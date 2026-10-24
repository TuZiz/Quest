package ym.quest.reward

import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import ym.quest.config.RewardSettings
import ym.quest.domain.QuestService
import ym.quest.integration.Integrations
import ym.quest.platform.GameThread
import java.util.UUID

/** Paper owner thread: one recipient per island; all durable claims execute on the database executor. */
class AutomaticRewards(private val plugin: JavaPlugin, private val service: QuestService,
                       private val integrations: Integrations, private val items: ItemFactory,
                       private val customId: (org.bukkit.inventory.ItemStack) -> String?,
                       private val delivery: MailboxDelivery, private val thread: GameThread,
                       private val failure: (Throwable) -> Unit, initial: RewardSettings,
                       private val active: () -> Boolean = { true }) : AutoCloseable {
    private var settings = initial
    private var closed = false
    private var generation = 0
    private var elapsed = 0
    private val pending = mutableSetOf<UUID>()
    private val requested = mutableSetOf<UUID>()
    fun completed(home: UUID) { if(!closed && settings.automatic) requested += home }
    private val unavailable = mutableSetOf<String>()
    private val timer = plugin.server.scheduler.runTaskTimer(plugin,Runnable { tick() },1,1)
    private fun valid(player: Player, home: UUID) = !closed && active() && settings.automatic && player.isOnline &&
        player.hasPermission("quest.use") && integrations.island(player.uniqueId)?.id == home && integrations.allowed(home,player.uniqueId)
    private fun tick() {
        if(closed || !active() || !settings.automatic) return
        elapsed++
        val recovery=elapsed >= settings.retryTicks
        if(recovery) elapsed=0
        if(!recovery && requested.isEmpty()) return
        if(integrations.production?.ready() != true) return
        plugin.server.onlinePlayers.sortedBy { it.uniqueId.toString() }.mapNotNull { player ->
            integrations.island(player.uniqueId)?.let { it.id to player }
        }.groupBy({ it.first },{ it.second }).forEach { (home,players) ->
            val player = players.firstOrNull { valid(it,home) } ?: return@forEach
            if(!recovery && home !in requested) return@forEach
            if(!pending.add(home)) return@forEach
            requested.remove(home)
            val epoch = generation
            try {
                val candidates = service.definitions.map { it.id to it.rewards } + service.chapters.map { "chapter:${it.id}" to it.rewards }
                val eligible = candidates.filter { (_,rewards) -> rewards.all { reward ->
                    try { items.build(reward.item,player); true }
                    catch(e: Exception) { if(unavailable.add(reward.item)) failure(e); false }
                } }.map { it.first }.toSet()
                val island = integrations.island(player.uniqueId)!!
                val starters = candidates.flatMap { it.second }.filter { it.starter }.map { it.item }.toSet()
                val hasGenerator = integrations.production?.hasGenerator(island) == true || island.members.any { id ->
                    plugin.server.getPlayer(id)?.inventory?.storageContents?.any { it != null && customId(it) in starters } == true
                }
                service.claimCompleted(home.toString(),player.uniqueId.toString(),integrations.facts(home),hasGenerator,eligible,limit=1)
                    .whenComplete { _,error -> thread.run {
                        if(closed || epoch != generation) return@run
                        if(error != null) { pending.remove(home); failure(error); return@run }
                        if(!valid(player,home)) { pending.remove(home); return@run }
                        delivery.collect(player,home,quiet=true) { if(epoch == generation) pending.remove(home) }
                    } }
            } catch(e: Exception) { pending.remove(home); failure(e) }
        }
    }
    fun reload(next: RewardSettings) { generation++; pending.clear(); requested.clear(); unavailable.clear(); elapsed=0; settings=next }
    override fun close() { closed=true; generation++; timer.cancel(); pending.clear(); requested.clear(); unavailable.clear() }
}
