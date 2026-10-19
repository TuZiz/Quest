package ym.quest.hud

import org.bukkit.entity.Player
import java.util.UUID

/** Paper owner presentation cache; timers inspect deadlines, emit only changed frames. */
internal class QuestTrackerHud(private val plain: (String) -> String) {
    private data class Frame(val layout: String, val values: Map<String,String>)
    private class Session {
        val state=SmartHudState()
        var target: TrackerTarget?=null
        var available=emptySet<String>()
        var frame: Frame?=null
    }
    private val sessions=mutableMapOf<UUID,Session>()
    fun accept(player: Player, target: TrackerTarget?, available: Set<String>, now: Long, settings: HudSettings) {
        val session=sessions.getOrPut(player.uniqueId) { Session() }
        session.target=target; session.available=available; session.state.observe(target,now,settings)
    }
    fun request(player: Player, now: Long, settings: HudSettings) {
        val session=sessions.getOrPut(player.uniqueId) { Session() }
        session.state.request()
        if(session.target != null) session.state.observe(session.target,now,settings)
    }
    fun canTrack(player: Player, quest: String) = quest in (sessions[player.uniqueId]?.available ?: emptySet())
    fun invalidate(player: Player) { sessions[player.uniqueId]?.frame=null }
    fun tick(players: Collection<Player>, api: XerrDisplay?, settings: HudSettings, enabled: Boolean, now: Long,
             eligible: (Player) -> Boolean, home: (Player) -> String?, preference: (Player) -> HudPreference?) {
        players.forEach { player ->
            val session=sessions[player.uniqueId] ?: return@forEach
            val target=session.target
            val pref=preference(player)
            val valid=enabled && eligible(player) && target != null && target.home == home(player) && pref != null
            val state=session.state.state(pref?.mode ?: HudMode.OFF,valid,now)
            if(api == null) { session.frame=null; return@forEach }
            if(state == HudState.HIDDEN) {
                if(session.frame != null || api.present(player,SLOT)) api.remove(player,SLOT)
                session.frame=null
                if(target != null && target.home != home(player)) { session.target=null; session.state.observe(null,now,settings) }
                return@forEach
            }
            val values=if(state == HudState.COMPACT) mapOf("compact_task" to HudText.fit(plain(target!!.values.getValue("task")),settings.compactUnits),
                "current" to target.current.toString(),"target" to target.target.toString())
            else target!!.values.mapValues { (key,value) ->
                val raw=plain(value)
                when(key) { "chapter","task" -> HudText.fit(raw,settings.textUnits)
                    "objective" -> HudText.fit(raw,(settings.textUnits-8).coerceAtLeast(8)); else -> raw }
            } + ("show_bar" to settings.bar.toString())
            val frame=Frame(if(state == HudState.COMPACT) settings.compactLayout else settings.expandedLayout,values)
            if(!api.ready(frame.layout)) return@forEach
            if(frame != session.frame || !api.present(player,SLOT)) {
                api.update(player,frame.layout,frame.values,slot=SLOT); session.frame=frame
            }
        }
    }
    fun remove(player: Player, api: XerrDisplay?) {
        if(sessions.remove(player.uniqueId) != null) api?.remove(player,SLOT)
    }
    fun clear(players: Collection<Player>, api: XerrDisplay?) { players.forEach { remove(it,api) }; sessions.clear() }
    companion object { const val SLOT="quest_tracker" }
}
