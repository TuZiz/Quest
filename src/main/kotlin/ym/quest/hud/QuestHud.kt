package ym.quest.hud

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.NamespacedKey
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerResourcePackStatusEvent
import org.bukkit.plugin.java.JavaPlugin
import ym.quest.domain.*
import ym.quest.integration.Integrations
import ym.quest.platform.GameThread
import ym.quest.storage.HudPreferenceStore
import ym.quest.text.TextRenderer
import java.util.UUID

/** Paper owner coordinator. SQLite reads are async, grouped by island, at most one in flight. */
class QuestHud(private val plugin: JavaPlugin, private val service: QuestService,
               private val islands: Integrations, private val thread: GameThread,
               private val failure: (Throwable) -> Unit, initial: YamlConfiguration,
               private val text: TextRenderer, store: HudPreferenceStore) : Listener, AutoCloseable {
    private var settings=initial
    private var options=HudSettings.parse(initial)
    private val preferences=HudPreferenceSessions(store,thread,NamespacedKey(plugin,"hud-hidden"),text,failure)
    private val tracker=QuestTrackerHud(::plain)
    private val notifications=QuestNotificationHud(text,::plain)
    private val controls=HudControls(preferences,tracker,text,::refreshPlayer)
    private val journals=mutableMapOf<UUID,Pair<String,JournalView>>()
    private val loaded=mutableSetOf<UUID>()
    private val pending=mutableSetOf<UUID>()
    private var generation=0
    private var closed=false
    private var counter=0
    private var clock=0L
    private var warned=false
    private val timer=plugin.server.scheduler.runTaskTimer(plugin,Runnable { tick() },20,20)
    private val displayTimer=plugin.server.scheduler.runTaskTimer(plugin,Runnable { safe {
        if(!closed) { clock+=2; render() }
    } },2,2)
    init { plugin.server.pluginManager.registerEvents(this,plugin) }
    private fun display(): XerrDisplay? = if(settings.getBoolean("enabled") && plugin.server.pluginManager.isPluginEnabled("Xerr")) XerrDisplay() else null
    private fun safe(action: () -> Unit) {
        try { action() } catch(e: Exception) { if(!warned) { warned=true; failure(e) } }
        catch(e: LinkageError) { if(!warned) { warned=true; failure(e) } }
    }
    private fun home(player: Player) = islands.island(player.uniqueId)?.id?.toString()
    private fun eligible(player: Player) = player.hasPermission("quest.use") && packReady(player)
    private fun render() {
        val players=plugin.server.onlinePlayers
        val api=display()
        tracker.tick(players,api,options,settings.getBoolean("tracker"),::eligible,::home,preferences::get)
        notifications.tick(players,api,settings.getBoolean("notifications"),{
            eligible(it) && preferences.get(it)?.notifications == true
        },::home,clock)
    }
    private fun tick() = safe {
        if(closed) return@safe
        plugin.server.onlinePlayers.forEach { player -> preferences.ensure(player) { counter=settings.getInt("refresh-ticks") }; packReady(player) }
        val api=display() ?: return@safe
        if(!api.ready(options.sidebarLayout)) {
            if(!warned) { warned=true; plugin.logger.warning("Xerr sidebar layout missing: ${options.sidebarLayout}; rebuild server Xerr layouts and resource pack") }
            return@safe
        }
        counter+=20
        if(counter < settings.getInt("refresh-ticks")) return@safe
        counter=0
        val epoch=generation
        plugin.server.onlinePlayers.filter { eligible(it) && preferences.get(it) != null }
            .mapNotNull { p -> islands.island(p.uniqueId)?.id?.let { it to p.uniqueId } }.groupBy({ it.first },{ it.second })
            .forEach { (home,members) ->
                if(!pending.add(home)) return@forEach
                service.journal(home.toString()).whenComplete { journal,error -> thread.run {
                    if(closed || epoch != generation) return@run
                    pending.remove(home)
                    if(error != null) { failure(error); return@run }
                    members.forEach { id ->
                        val player=plugin.server.getPlayer(id)?.takeIf { it.isOnline && eligible(it) && this.home(it) == home.toString() } ?: return@forEach
                        journals[id]=home.toString() to journal
                        project(player,home.toString(),journal)
                    }
                    safe { render() }
                } }
            }
    }
    private fun project(player: Player, home: String, journal: JournalView) {
        val preference=preferences.get(player) ?: return
        tracker.accept(player,TrackerSnapshot.target(home,journal,preference.trackedQuest),
            journal.quests.filter { it.status == QuestStatus.ACTIVE }.map { it.definition.id }.toSet())
    }
    private fun refreshPlayer(player: Player) {
        if(closed || !player.isOnline) return
        journals[player.uniqueId]?.takeIf { it.first == home(player) }?.let { project(player,it.first,it.second) }
        counter=settings.getInt("refresh-ticks")
        safe { render() }
    }
    fun control(player: Player, args: List<String>) { if(!closed) controls.execute(player,args) }
    fun changes(player: Player, changes: List<ProgressChange>, quests: List<QuestDefinition>, chapters: List<ChapterDefinition>) = safe {
        if(closed) return@safe
        if(settings.getBoolean("notifications") && eligible(player) && preferences.get(player)?.notifications == true && display() != null)
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
        generation++; pending.clear(); journals.clear(); preferences.clear()
        safe { tracker.clear(plugin.server.onlinePlayers,display()); notifications.clear(plugin.server.onlinePlayers,display()) }
    }
    @EventHandler fun pack(e: PlayerResourcePackStatusEvent) {
        if(e.status == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
            loaded.add(e.player.uniqueId); tracker.invalidate(e.player); counter=settings.getInt("refresh-ticks")
        }
    }
    @EventHandler fun quit(e: PlayerQuitEvent) {
        loaded.remove(e.player.uniqueId); journals.remove(e.player.uniqueId); preferences.remove(e.player.uniqueId)
        tracker.remove(e.player,display()); notifications.remove(e.player,display())
    }
    override fun close() { if(closed) return; closed=true; timer.cancel(); displayTimer.cancel(); clear(); loaded.clear(); HandlerList.unregisterAll(this) }
}
