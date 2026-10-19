package ym.quest.hud

import org.bukkit.entity.Player
import ym.quest.text.TextRenderer

/** Paper owner command adapter. Preferences commit before applying or acknowledging a change. */
internal class HudControls(private val preferences: HudPreferenceSessions, private val tracker: QuestTrackerHud,
    private val text: TextRenderer, private val now: () -> Long, private val settings: () -> HudSettings,
    private val refresh: (Player) -> Unit) {
    fun execute(player: Player, args: List<String>) {
        val current=preferences.get(player) ?: run { text.send(player,"loading"); return }
        val choice=args.firstOrNull()
        if(choice == "view" && args.size == 1) {
            tracker.request(player,now(),settings()); refresh(player); text.send(player,"smart-hud.view"); return
        }
        val next=when {
            args.isEmpty() -> current.copy(mode=current.mode.next())
            args.size == 1 && choice in setOf("smart","always","off","on") -> current.copy(mode=when(choice) {
                "always" -> HudMode.ALWAYS; "off" -> HudMode.OFF; else -> HudMode.SMART })
            args.size == 2 && choice == "notifications" && args[1] in setOf("on","off") -> current.copy(notifications=args[1] == "on")
            args.size == 2 && choice == "track" -> {
                if(!tracker.canTrack(player,args[1])) { text.send(player,"smart-hud.invalid-task"); return }
                current.copy(trackedQuest=args[1])
            }
            else -> { text.send(player,"smart-hud.usage"); return }
        }
        preferences.update(player,next) {
            if(next.mode != HudMode.OFF) tracker.request(player,now(),settings())
            refresh(player)
            text.send(player,"smart-hud.saved",TextRenderer.values("mode" to next.mode.name.lowercase(),"notifications" to next.notifications.toString()))
        }
    }
}
