package ym.quest.platform

import org.bukkit.command.*
import org.bukkit.entity.Player
import ym.quest.domain.QuestService
import ym.quest.integration.Integrations
import ym.quest.reward.MailboxDelivery
import ym.quest.text.TextRenderer
import java.util.UUID

class QuestCommands(private val text: TextRenderer, private val service: QuestService,
                    private val integrations: Integrations, private val thread: GameThread,
                    private val delivery: MailboxDelivery, private val open: (Player) -> Unit,
                    private val reload: (CommandSender) -> Unit, private val failure: (Throwable) -> Unit) : CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if(args.isEmpty()) {
            if(!sender.hasPermission("quest.use")) text.send(sender,"no-permission")
            else if(sender is Player) open(sender) else text.send(sender,"player-only")
            return true
        }
        if(args[0] in setOf("help","reload")) {
            if(args[0] == "reload") {
                if(!sender.hasPermission("quest.admin")) text.send(sender,"no-permission")
                else if(args.size != 1) text.send(sender,"command-help.admin") else reload(sender)
            } else {
                if(!sender.hasPermission("quest.use")) text.send(sender,"no-permission")
                else { text.send(sender,"command-help.player"); if(sender.hasPermission("quest.admin")) text.send(sender,"command-help.admin") }
            }
            return true
        }
        if(args[0] != "admin") {
            if(sender.hasPermission("quest.use")) text.send(sender,"command-help.player") else text.send(sender,"no-permission")
            return true
        }
        if(!sender.hasPermission("quest.admin")) { text.send(sender,"no-permission"); return true }
        try {
            when(args.getOrNull(1)) {
                "reload" -> reload(sender)
                "reset" -> {
                    if(args.size != 4) { text.send(sender,"usage"); return true }
                    val home = UUID.fromString(args[2]); val quest=args[3]
                    service.reset(home.toString(),quest,sender.name,integrations.facts(home)).whenComplete { _,e -> thread.run {
                        if(e != null) { failure(e); text.send(sender,"error") } else { integrations.syncIsland(home); text.send(sender,"reset") }
                    } }
                }
                "debug" -> {
                    val home = args.getOrNull(2)?.let(UUID::fromString) ?: (sender as? Player)?.let { integrations.island(it.uniqueId)?.id }
                    if(home == null) { text.send(sender,"usage"); return true }
                    val facts=integrations.facts(home)
                    val adapters="Dream=${integrations.islands?.ready()}; Genlite=${integrations.production?.ready()}"
                    service.views(home.toString()).thenCombine(service.mailbox(home.toString())) { views,parcels ->
                        "home=$home; $adapters; facts=$facts; quests=${views.map { "${it.definition.id}:${it.status}:${it.progress.map { p -> p.progress }}" }}; pending=${parcels.filter { it.phase != "DELIVERED" }}"
                    }.whenComplete { info,e -> thread.run {
                        if(e != null) { failure(e); text.send(sender,"error") } else text.send(sender,"debug",TextRenderer.values("info" to info))
                    } }
                }
                "resolve" -> {
                    if(args.size != 4 || args[3] !in setOf("delivered","retry")) { text.send(sender,"usage"); return true }
                    UUID.fromString(args[2])
                    if(delivery.isSending(args[2])) { text.send(sender,"busy"); return true }
                    service.resolve(args[2],sender.name,args[3] == "delivered").whenComplete { resolved,e -> thread.run {
                        if(e != null) { failure(e); text.send(sender,"error") }
                        else text.send(sender,if(resolved) "resolved" else "not-complete")
                    } }
                }
                else -> text.send(sender,"usage")
            }
        } catch(e: IllegalArgumentException) { text.send(sender,"usage") }
        return true
    }
    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if(!sender.hasPermission("quest.use")) return emptyList()
        val choices = when {
            args.size == 1 -> listOf("help") + if(sender.hasPermission("quest.admin")) listOf("reload","admin") else emptyList()
            !sender.hasPermission("quest.admin") -> emptyList()
            args[0] != "admin" -> emptyList()
            else -> when(args.size) { 2 -> listOf("reload","reset","debug","resolve")
            4 -> if(args[1] == "reset") service.definitions.map { it.id } else if(args[1] == "resolve") listOf("delivered","retry") else emptyList()
            else -> emptyList() } }
        return choices.filter { it.startsWith(args.lastOrNull() ?: "") }
    }
}
