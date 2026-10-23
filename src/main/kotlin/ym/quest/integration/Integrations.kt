package ym.quest.integration

import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import ym.quest.domain.QuestService
import ym.quest.domain.ProgressChange
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Only discovery/recovery runs once per second. Normal progress is commit-event driven. */
class Integrations(private val plugin: JavaPlugin, private val service: QuestService,
                   private val changes: (List<ProgressChange>) -> Unit, private val failure: (Throwable) -> Unit) : AutoCloseable {
    @Volatile var islands: IslandGateway? = null; private set
    @Volatile var production: ProductionGateway? = null; private set
    private val listeners = mutableListOf<Listener>()
    private var initial = false
    private var warnedDream = false; private var warnedGen = false
    private val seen = ConcurrentHashMap<String,Long>()
    private val placed = ConcurrentHashMap.newKeySet<UUID>()
    @Volatile private var closed = false
    fun discover() {
        if(closed) return
        val pm = plugin.server.pluginManager
        if(islands == null && pm.isPluginEnabled("DreamSkyblock")) try {
            plugin.server.servicesManager.load(ym.dreamSkyblock.api.DreamSkyblockApi::class.java)?.let { api ->
                val bridge = DreamSkyblockBridge(api,::syncIsland); islands = bridge
                pm.registerEvents(bridge,plugin); listeners += bridge
            }
        } catch(e: LinkageError) { if(!warnedDream) { plugin.logger.warning("DreamSkyblock compatibility patch missing: $e"); warnedDream=true } }
        if(production == null && pm.isPluginEnabled("Genlite")) try {
            plugin.server.servicesManager.load(cn.genlite.api.GenliteQuestApi::class.java)?.let { api ->
                val bridge = GenliteBridge(api,::acceptCounters); production = bridge
                pm.registerEvents(bridge,plugin); listeners += bridge
            }
        } catch(e: LinkageError) { if(!warnedGen) { plugin.logger.warning("Genlite compatibility patch missing: $e"); warnedGen=true } }
        val homes = islands?.takeIf { it.ready() } ?: return
        if(!initial) { initial=true; homes.all().forEach { syncIsland(it.id) } }
        production?.takeIf { it.ready() }?.let { acceptCounters(it.counters()) }
        if(production?.ready() == true && service.definitions.any { q -> q.objectives.any { it.type == "genlite.placed" } }) {
            plugin.server.onlinePlayers.mapNotNull { homes.byMember(it.uniqueId) }.distinctBy { it.id }.forEach { island ->
                if(island.id !in placed && production!!.hasPlacedGenerator(island) && placed.add(island.id)) {
                    // Reserve while syncing; failures retry on discovery.
                    service.sync(island.id.toString(),facts(island.id)).whenComplete { list,error ->
                        if(error != null) { placed.remove(island.id); failure(error) } else if(!closed) changes(list)
                    }
                }
            }
        }
    }
    fun island(player: UUID) = islands?.takeIf { it.ready() }?.byMember(player)
    fun allowed(home: UUID, player: UUID) = islands?.canClaim(home,player) == true
    fun facts(home: UUID): Map<String,Long> = buildMap {
        islands?.byId(home)?.let { put("island.member",1);put("island.value",it.value) }
        service.definitions.flatMap { it.objectives }.filter { it.type.startsWith("combat.") || it.type=="island.inspect" || it.type=="genlite.inspect" || production?.ready()==true && it.type.startsWith("genlite.") }.forEach { put(it.metric,0L) }
        if(production?.ready() == true) islands?.byId(home)?.let { island ->
            put("genlite.placed",if(production!!.hasPlacedGenerator(island)) 1L else 0L)
        }
        production?.takeIf { it.ready() }?.counters()?.forEach { (key,n) -> if(key.startsWith("$home|")) put(key.substringAfter('|'),n) }
    }
    fun syncIsland(home: UUID) {
        if(closed || islands?.byId(home) == null) return
        service.sync(home.toString(),facts(home)).whenComplete { list,error -> if(error != null) failure(error) else if(!closed) changes(list) }
    }
    @Synchronized private fun acceptCounters(counters: Map<String,Long>) {
        if(closed || islands?.ready() != true) return
        val homes = mutableSetOf<UUID>()
        counters.forEach { (key,n) ->
            val old = seen[key]
            if(old == null || n > old) { homes += UUID.fromString(key.substringBefore('|')); seen[key]=n }
            else if(n < old) plugin.logger.severe("Genlite counter rollback: $key=$n < $old; progress frozen until counter recovers; restore Quest and Genlite from same backup")
        }
        homes.forEach { home ->
            if(islands?.byId(home) == null) return@forEach
            val values = facts(home)
            service.sync(home.toString(),values).whenComplete { list,error ->
                if(error != null) { values.keys.forEach { seen.remove("$home|$it") }; failure(error) }
                else if(!closed) changes(list)
            }
        }
    }
    fun refresh() { initial=false; seen.clear(); placed.clear(); discover() }
    override fun close() { closed=true; listeners.forEach { HandlerList.unregisterAll(it) }; listeners.clear(); islands=null; production=null; seen.clear(); placed.clear() }
}
