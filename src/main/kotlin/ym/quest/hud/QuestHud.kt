package ym.quest.hud

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
import ym.quest.domain.ProgressChange
import ym.quest.domain.QuestDefinition
import ym.quest.domain.ChapterDefinition
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataType
import java.util.UUID

/** Paper owner thread; database reads are asynchronous and grouped by island, one in-flight read each. */
class QuestHud(private val plugin: JavaPlugin, private val service: QuestService,
               private val islands: Integrations, private val thread: GameThread,
               private val failure: (Throwable) -> Unit, initial: YamlConfiguration,
               private val text: TextRenderer) : Listener, AutoCloseable {
    private var settings = initial
    private var options = HudSettings.parse(initial)
    private val preference = NamespacedKey(plugin,"hud-hidden")
    private val notifications = QuestNotificationHud(text,::plain)
    private val lastValues = mutableMapOf<UUID,Map<String,String>>()
    private val loaded = mutableSetOf<UUID>()
    private val shown = mutableSetOf<UUID>()
    private val pending = mutableSetOf<UUID>()
    private var generation = 0
    private var closed = false
    private var counter = 0
    private var warned = false
    private val timer = plugin.server.scheduler.runTaskTimer(plugin,Runnable { tick() },20,20)
    private val notificationTimer = plugin.server.scheduler.runTaskTimer(plugin,Runnable {
        safe { if(!closed) notifications.tick(plugin.server.onlinePlayers,display(),settings.getBoolean("notifications"),::eligible) { islands.island(it.uniqueId)?.id?.toString() } }
    },2,2)
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
        plugin.server.onlinePlayers.filter { it.uniqueId in shown && (!eligible(it) || !settings.getBoolean("tracker") || islands.island(it.uniqueId) == null) }
            .forEach { api.remove(it,layout); shown.remove(it.uniqueId); lastValues.remove(it.uniqueId) }
        counter += 20
        if(counter < settings.getInt("refresh-ticks")) return@safe
        counter = 0
        if(!settings.getBoolean("tracker")) return@safe
        val epoch = generation
        plugin.server.onlinePlayers.filter(::eligible)
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
                    val values = TrackerSnapshot.values(journal,complete,options.hideCompleted)?.mapValues {
                        val value=plain(it.value)
                        if(it.key in setOf("chapter","task")) HudText.fit(value,options.textUnits)
                        else if(it.key == "objective") HudText.fit(value,(options.textUnits-8).coerceAtLeast(8)) else value
                    }?.plus("show_bar" to options.bar.toString())
                    safe {
                        val current = display() ?: return@safe
                        members.forEach { id ->
                            val p = plugin.server.getPlayer(id)?.takeIf { it.isOnline && eligible(it) && islands.island(id)?.id == home } ?: return@forEach
                            if(values == null) { if(shown.remove(id)) current.remove(p,layout); lastValues.remove(id) }
                            else if(lastValues[id] != values) { current.update(p,layout,values); shown.add(id); lastValues[id]=values }
                        }
                    }
                } }
            }
    }
    private fun eligible(player: Player) = player.hasPermission("quest.use") && packReady(player) &&
        player.persistentDataContainer.get(preference,PersistentDataType.BYTE) != 1.toByte()
    fun visibility(player: Player, value: String?) {
        val hidden=player.persistentDataContainer.get(preference,PersistentDataType.BYTE) == 1.toByte()
        val show=when(value) { "on" -> true; "off" -> false; else -> hidden }
        if(show) player.persistentDataContainer.remove(preference)
        else player.persistentDataContainer.set(preference,PersistentDataType.BYTE,1.toByte())
        if(!show) {
            display()?.let { it.remove(player,settings.getString("tracker-layout")!!) }
            shown.remove(player.uniqueId); lastValues.remove(player.uniqueId); notifications.remove(player,display())
        }
        text.send(player,if(show) "hud-controls.shown" else "hud-controls.hidden")
        counter=settings.getInt("refresh-ticks")
    }
    fun changes(player: Player, changes: List<ProgressChange>, quests: List<QuestDefinition>, chapters: List<ChapterDefinition>) = safe {
        if(closed || !settings.getBoolean("notifications") || !eligible(player) || display() == null) return@safe
        notifications.offer(player,HudNoticePlanner.plan(changes,quests,chapters,options.merge),options)
        counter=settings.getInt("refresh-ticks")
    }
    private fun plain(value: String) = PlainTextComponentSerializer.plainText().serialize(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(value))
    private fun packReady(player: Player): Boolean {
        if(player.resourcePackStatus == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) loaded.add(player.uniqueId)
        return player.uniqueId in loaded
    }
    fun reload(next: YamlConfiguration) { clear(); settings=next; options=HudSettings.parse(next); counter=0; warned=false }
    private fun clear() {
        generation++; pending.clear()
        safe { display()?.let { api -> shown.forEach { id -> plugin.server.getPlayer(id)?.let { api.remove(it,settings.getString("tracker-layout")!!) } } } }
        shown.clear()
        lastValues.clear(); notifications.clear(plugin.server.onlinePlayers,display())
    }
    @EventHandler fun pack(e: PlayerResourcePackStatusEvent) {
        if(e.status == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) loaded.add(e.player.uniqueId)
    }
    @EventHandler fun quit(e: PlayerQuitEvent) { loaded.remove(e.player.uniqueId); shown.remove(e.player.uniqueId); lastValues.remove(e.player.uniqueId); notifications.remove(e.player,display()) }
    override fun close() { if(closed) return; closed=true; timer.cancel(); notificationTimer.cancel(); clear(); loaded.clear(); HandlerList.unregisterAll(this) }
}
