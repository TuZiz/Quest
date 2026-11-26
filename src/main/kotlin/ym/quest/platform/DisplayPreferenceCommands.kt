package ym.quest.platform

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import ym.quest.preferences.DisplayPreferences
import ym.quest.preferences.HudMode
import ym.quest.text.TextRenderer

/** Paper ownership thread. Storage callbacks retain the existing player-session checks. */
class DisplayPreferenceCommands(private val text: TextRenderer,
    private val read: (Player) -> DisplayPreferences?,
    private val update: (Player, (DisplayPreferences) -> DisplayPreferences, (Boolean) -> Unit) -> Unit) {
    fun handle(sender: CommandSender, args: Array<out String>): Boolean {
        if(args.firstOrNull()!="hud") return false
        if(!sender.hasPermission("quest.use")) { text.send(sender,"no-permission");return true }
        if(sender !is Player) { text.send(sender,"player-only");return true }
        if(args.size!=2 || args[1] !in operations) { text.send(sender,"preference-command.usage");return true }
        if(read(sender)==null) { text.send(sender,"preference-command.loading");return true }
        update(sender,{ value ->
            val current=value.mode!=HudMode.OFF
            val next=when(args[1]) { "on" -> true; "off" -> false; else -> !current }
            value.copy(mode=if(next) HudMode.ALWAYS else HudMode.OFF,autoTracking=next)
        }) { saved ->
            if(!saved) text.send(sender,"error")
            else read(sender)?.let { value ->
                text.send(sender,"preference-command.saved",mapOf(
                    "setting" to text.format("preference-command.hud"),
                    "state" to text.format(if(value.mode!=HudMode.OFF) "journal.enabled" else "journal.disabled")
                ))
            }
        }
        return true
    }
    fun complete(sender: CommandSender,args: Array<out String>): List<String> {
        if(!sender.hasPermission("quest.use")) return emptyList()
        val choices=when {
            args.size==1 -> settings
            args.size==2 && args[0] in settings -> operations
            else -> emptyList()
        }
        return choices.filter { it.startsWith(args.lastOrNull() ?: "") }
    }
    companion object {
        private val settings=listOf("hud")
        private val operations=listOf("on","off","toggle")
    }
}
