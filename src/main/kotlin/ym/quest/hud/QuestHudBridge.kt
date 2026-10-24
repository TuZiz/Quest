package ym.quest.hud

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
import ym.quest.text.TextRenderer
import ym.quest.preferences.DisplayPreferences
import ym.quest.preferences.HudMode
import java.util.UUID
import java.util.logging.Level

/** Paper main-thread sessions and rendering; journal IO stays on Quest's existing database executor. */
class QuestHudBridge(private val plugin: JavaPlugin, private val service: QuestService,
    private val integrations: Integrations, private val thread: GameThread, text: TextRenderer,
    private val settings: HudSettings, private val preferences: (Player) -> DisplayPreferences?,
    private val personal: (Player) -> JournalView?) : Listener, AutoCloseable {
    private class Session(val player: Player, settings: HudSettings) {
        var home: UUID? = null
        var tracker: Map<String,String>? = null
        var rendered: Map<String,String>? = null
        var notice: HudNoticeFrame? = null
        val queue=HudNoticeQueue(settings)
    }
    private val renderer=XerrHudRenderer(settings)
    private val projection=HudProjection(text,settings.textWidth)
    private val sessions=mutableMapOf<UUID,Session>()
    private val pending=mutableMapOf<UUID,Any>()
    private val failures=mutableSetOf<String>()
    private var closed=false
    private var tick=0L
    private val timer=plugin.server.scheduler.runTaskTimer(plugin,Runnable { update() },2,2)
    init { plugin.server.pluginManager.registerEvents(this,plugin) }
    private fun allowed(player: Player)=!closed && player.isOnline && player.hasPermission("quest.use") && player.hasResourcePack()
    private fun session(player: Player): Session {
        val old=sessions[player.uniqueId]
        if(old?.player === player) return old
        old?.let(::clear)
        return Session(player,settings).also { sessions[player.uniqueId]=it }
    }
    private fun valid(s: Session)=allowed(s.player) && sessions[s.player.uniqueId] === s
    private fun update() {
        if(closed) return
        tick+=2
        if(!plugin.server.pluginManager.isPluginEnabled("Xerr")) return
        try {
            if(!renderer.ready(settings)) {
                if(failures.add("layouts")) plugin.logger.warning("Xerr quest layouts missing: ${settings.tracker}, ${settings.notice}; waiting for xerr reload")
                return
            }
            failures.remove("layouts")
            sessions.values.toList().filter { !allowed(it.player) }.forEach { clear(it); sessions.remove(it.player.uniqueId) }
            if(tick == 2L || tick % settings.refreshTicks < 2) refresh()
            sessions.values.toList().forEach { s ->
                try { draw(s); failures.remove(s.player.uniqueId.toString()) }
                catch(e: Exception) { failed(s.player.uniqueId.toString(),e) }
            }
        } catch(e: Exception) { failed("renderer",e) }
    }
    private fun refresh() {
        val groups=mutableMapOf<UUID,MutableList<Session>>()
        plugin.server.onlinePlayers.filter(::allowed).forEach { player ->
            val s=session(player)
            val prefs=preferences(player)
            if(prefs==null || prefs.mode==HudMode.OFF) { s.tracker=null;return@forEach }
            val home=integrations.island(player.uniqueId)?.id
            if(home != s.home) { clear(s); s.home=home }
            val personalView=personal(player)
            if(personalView!=null && personalView.quests.isEmpty()) { s.tracker=null;return@forEach }
            if(home == null || integrations.islands?.ready() != true) {
                s.tracker=personalView?.let { projection.projectGrouped(it,prefs.trackedQuest,prefs.autoTracking) };return@forEach
            }
            groups.getOrPut(home) { mutableListOf() }.add(s)
        }
        groups.forEach { (home,targets) ->
            if(home in pending) return@forEach
            val ticket=Any(); pending[home]=ticket
            service.journal(home.toString()).whenComplete { view,error -> thread.run {
                if(closed || pending[home] !== ticket) return@run
                pending.remove(home)
                if(error != null) { failed("journal:$home",error); targets.filter(::valid).forEach { it.tracker=null }; return@run }
                failures.remove("journal:$home")
                targets.filter { valid(it) && it.home == home && integrations.island(it.player.uniqueId)?.id == home }
                    .forEach { s -> val prefs=preferences(s.player)
                        val intro=personal(s.player)
                        s.tracker=if(prefs==null || prefs.mode==HudMode.OFF || (intro!=null && intro.quests.isEmpty())) null
                            else projection.projectGrouped(view,prefs.trackedQuest,prefs.autoTracking,intro)
                    }
            } }
        }
    }
    private fun draw(s: Session) {
        val home=integrations.island(s.player.uniqueId)?.id
        if(s.home != home) { clear(s); s.home=home; return }
        val prefs=preferences(s.player)
        val values=if(prefs?.mode==HudMode.ALWAYS) s.tracker else null
        if(prefs?.notifications!=true) s.queue.clear()
        if(values == null) {
            if(s.rendered != null || renderer.present(s.player,TRACKER)) renderer.remove(s.player,TRACKER)
            s.rendered=null
        } else if(s.rendered != values || !renderer.present(s.player,TRACKER)) {
            renderer.render(s.player,TRACKER,settings.tracker,values); s.rendered=values
        }
        val frame=s.queue.frame(tick)
        if(frame == null) {
            if(s.notice != null || renderer.present(s.player,NOTICE)) renderer.remove(s.player,NOTICE)
        } else if(frame != s.notice || !renderer.present(s.player,NOTICE)) {
            renderer.render(s.player,NOTICE,settings.notice,frame.notice.values,frame.alpha)
        }
        s.notice=frame
    }
    fun notify(player: Player, home: UUID, changes: List<ProgressChange>) {
        if(!allowed(player) || preferences(player)?.notifications!=true || integrations.island(player.uniqueId)?.id != home) return
        val s=session(player)
        if(s.home != home) { clear(s); s.home=home }
        projection.completion(changes,service.definitions,service.chapters).forEach(s.queue::offer)
    }
    fun personalComplete(player: Player, id: String, name: String) {
        if(allowed(player) && preferences(player)?.notifications==true) session(player).queue.offer(projection.notice("personal:$id",name,false))
    }
    private fun clear(s: Session) {
        s.queue.clear(); s.tracker=null; s.rendered=null; s.notice=null
        if(plugin.server.pluginManager.isPluginEnabled("Xerr")) {
            renderer.remove(s.player,TRACKER); renderer.remove(s.player,NOTICE)
        }
    }
    private fun failed(key: String, error: Throwable) {
        if(failures.add(key)) plugin.logger.log(Level.WARNING,"Quest HUD failed; context=$key; layouts=${settings.tracker}/${settings.notice}; progress retained",error)
    }
    @EventHandler fun quit(e: PlayerQuitEvent) { sessions.remove(e.player.uniqueId)?.let(::clear); failures.remove(e.player.uniqueId.toString()) }
    @EventHandler fun pack(e: PlayerResourcePackStatusEvent) {
        // New pack generation invalidates cached glyphs, including when Xerr emptied its native slots.
        sessions.remove(e.player.uniqueId)?.let(::clear)
    }
    override fun close() {
        if(closed) return
        closed=true; timer.cancel(); sessions.values.toList().forEach(::clear); sessions.clear(); pending.clear(); failures.clear()
        HandlerList.unregisterAll(this)
    }
    companion object {
        const val TRACKER="quest:tracker"
        const val NOTICE="quest:completion"
    }
}
