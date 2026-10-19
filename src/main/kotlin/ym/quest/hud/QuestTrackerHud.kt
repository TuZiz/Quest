package ym.quest.hud

import org.bukkit.entity.Player
import java.util.UUID

/** Paper owner presentation cache; effective task is persistent, emit only changed frames. */
internal class QuestTrackerHud(private val plain: (String) -> String) {
    private data class Frame(val layout: String, val values: Map<String,String>)
    private class Session {
        var target: TrackerTarget?=null
        var available=emptySet<String>()
        var frame: Frame?=null
    }
    private val sessions=mutableMapOf<UUID,Session>()
    fun accept(player: Player, target: TrackerTarget?, available: Set<String>) {
        val session=sessions.getOrPut(player.uniqueId) { Session() }
        session.target=target; session.available=available
    }
    fun canTrack(player: Player, quest: String) = quest in (sessions[player.uniqueId]?.available ?: emptySet())
    fun invalidate(player: Player) { sessions[player.uniqueId]?.frame=null }
    fun tick(players: Collection<Player>, api: XerrDisplay?, settings: HudSettings, enabled: Boolean,
             eligible: (Player) -> Boolean, home: (Player) -> String?, preference: (Player) -> HudPreference?) {
        players.forEach { player ->
            val session=sessions[player.uniqueId] ?: return@forEach
            val target=session.target
            val pref=preference(player)
            val valid=enabled && eligible(player) && target != null && target.home == home(player) && pref != null
            val state=if(valid && pref?.mode != HudMode.OFF) HudState.EXPANDED else HudState.HIDDEN
            if(api == null) { session.frame=null; return@forEach }
            if(state == HudState.HIDDEN) {
                if(session.frame != null || api.present(player,SLOT)) api.remove(player,SLOT)
                session.frame=null
                if(target != null && target.home != home(player)) { session.target=null }
                return@forEach
            }
            val values=SidebarText.values(target!!,settings.sidebarBackground,settings.bar,plain)
            val frame=Frame(settings.sidebarLayout,values)
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
