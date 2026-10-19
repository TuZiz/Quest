package ym.quest.hud

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerResourcePackStatusEvent
import org.bukkit.plugin.java.JavaPlugin
import ym.quest.domain.QuestService
import ym.quest.integration.Integrations
import ym.quest.platform.GameThread
import ym.quest.text.TextRenderer
import java.util.UUID

/** Paper owner thread; database reads are asynchronous and grouped by island, one in-flight read each. */
class QuestHud(private val plugin: JavaPlugin, private val service: QuestService,
               private val islands: Integrations, private val thread: GameThread,
               private val failure: (Throwable) -> Unit, initial: YamlConfiguration,
               private val text: TextRenderer) : Listener, AutoCloseable {
    private var settings = initial
    private val loaded = mutableSetOf<UUID>()
    private val shown = mutableSetOf<UUID>()
    private val pending = mutableSetOf<UUID>()
    private var generation = 0
    private var closed = false
    private var counter = 0
    private var warned = false
    private val timer = plugin.server.scheduler.runTaskTimer(plugin,Runnable { tick() },20,20)
    init { plugin.server.pluginManager.registerEvents(this,plugin) }
    private fun display(): XerrDisplay? = if(settings.getBoolean("enabled") && plugin.server.pluginManager.isPluginEnabled("Xerr")) XerrDisplay() else null
    private fun safe(action: () -> Unit) {
        try { action() } catch(e: Exception) { if(!warned) { warned=true; failure(e) } }
        catch(e: LinkageError) { if(!warned) { warned=true; failure(e) } }
    }
    private fun tick() = safe {
        if(closed) return@safe
        // Runtime is assembled asynchronously. A successful pack event may predate this listener.
        plugin.server.onlinePlayers.forEach { packReady(it) }
        val api = display() ?: return@safe
        val layout = settings.getString("tracker-layout")!!
        if(!api.ready(layout)) {
            if(!warned) { warned=true; plugin.logger.warning("Xerr layout=$layout missing; configure plugins/Xerr/core/layout; Quest chat/title fallback remains active") }
            return@safe
        }
        plugin.server.onlinePlayers.filter { it.uniqueId in shown && (!it.hasPermission("quest.use") || it.uniqueId !in loaded || islands.island(it.uniqueId) == null) }
            .forEach { api.remove(it,layout); shown.remove(it.uniqueId) }
        counter += 20
        if(counter < settings.getInt("refresh-ticks")) return@safe
        counter = 0
        if(!settings.getBoolean("tracker")) return@safe
        val epoch = generation
        plugin.server.onlinePlayers.filter { it.hasPermission("quest.use") && it.uniqueId in loaded }
            .mapNotNull { p -> islands.island(p.uniqueId)?.id?.let { it to p.uniqueId } }.groupBy({ it.first },{ it.second })
            .forEach { (home,members) ->
                if(!pending.add(home)) return@forEach
                service.journal(home.toString()).whenComplete { journal,error -> thread.run {
                    if(closed || epoch != generation) return@run
                    pending.remove(home)
                    if(error != null) { failure(error); return@run }
                    val complete = listOf("task","objective").associateWith {
                        PlainTextComponentSerializer.plainText().serialize(text.format("hud-completed.$it"))
                    }
                    val values = TrackerSnapshot.values(journal,complete)?.mapValues { plain(it.value) }
                    safe {
                        val current = display() ?: return@safe
                        members.forEach { id ->
                            val p = plugin.server.getPlayer(id)?.takeIf { it.isOnline && it.hasPermission("quest.use") && id in loaded && islands.island(id)?.id == home } ?: return@forEach
                            if(values == null) { if(shown.remove(id)) current.remove(p,layout) }
                            else { current.update(p,layout,values); shown.add(id) }
                        }
                    }
                } }
            }
    }
    fun popup(player: Player, event: String, title: Component, message: Component) = safe {
        if(closed || !settings.getBoolean("notifications") || !packReady(player)) return@safe
        display()?.popup(player,settings.getString("popup-prefix")!!+event,
            mapOf("title" to PlainTextComponentSerializer.plainText().serialize(title),
                "message" to PlainTextComponentSerializer.plainText().serialize(message)))?.whenComplete { _,error -> if(error != null) failure(error) }
    }
    private fun plain(value: String) = PlainTextComponentSerializer.plainText().serialize(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(value))
    private fun packReady(player: Player): Boolean {
        if(player.resourcePackStatus == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) loaded.add(player.uniqueId)
        return player.uniqueId in loaded
    }
    fun reload(next: YamlConfiguration) { clear(); settings=next; counter=0; warned=false }
    private fun clear() {
        generation++; pending.clear()
        safe { display()?.let { api -> shown.forEach { id -> plugin.server.getPlayer(id)?.let { api.remove(it,settings.getString("tracker-layout")!!) } } } }
        shown.clear()
    }
    @EventHandler fun pack(e: PlayerResourcePackStatusEvent) {
        if(e.status == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) loaded.add(e.player.uniqueId)
    }
    @EventHandler fun quit(e: PlayerQuitEvent) { loaded.remove(e.player.uniqueId); shown.remove(e.player.uniqueId) }
    override fun close() { if(closed) return; closed=true; timer.cancel(); clear(); loaded.clear(); HandlerList.unregisterAll(this) }
}
