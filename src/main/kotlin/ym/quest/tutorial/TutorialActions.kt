package ym.quest.tutorial

import cn.genlite.api.GenliteQuestApi
import cn.genlite.gui.MenuSession
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.plugin.java.JavaPlugin
import ym.dreamSkyblock.api.HomeMenuOpenedEvent
import ym.quest.domain.QuestService
import ym.quest.domain.ProgressChange
import ym.quest.integration.Integrations
import java.util.UUID

/** Paper main thread captures successful interactions; database mutations use the Quest IO owner. */
class TutorialActions(private val plugin: JavaPlugin, private val service: QuestService,
    private val integrations: Integrations, private val changed: (List<ProgressChange>) -> Unit,
    private val failure: (Throwable) -> Unit, private val worlds: () -> Set<String>) : Listener, AutoCloseable {
    private var closed=false
    private val mythic=MythicCombat(plugin) { player,entity,identity ->
        if(entity.world.name in worlds() && player.hasPermission("quest.use")) record(player.uniqueId,"combat.kill:$identity")
    }
    init { plugin.server.pluginManager.registerEvents(this,plugin) }
    private fun record(player: UUID,metric: String) {
        if(closed) return
        val island=integrations.island(player) ?: return
        service.record(island.id.toString(),metric,1,integrations.facts(island.id)).whenComplete { events,error ->
            if(error!=null) failure(error) else if(!closed) changed(events)
        }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    fun machine(e: InventoryOpenEvent) {
        val player=e.player as? Player ?: return
        val session=e.inventory.holder as? MenuSession ?: return
        val api=plugin.server.servicesManager.load(GenliteQuestApi::class.java) ?: return
        val machine=api.teachingMachine(session.machine) ?: return
        if(integrations.island(player.uniqueId)?.id!=machine.home()) return
        // Verify after InventoryOpen dispatch, including cancellations by other plugins.
        plugin.server.scheduler.runTask(plugin,Runnable {
            if(closed || !player.isOnline || player.openInventory.topInventory !== e.inventory) return@Runnable
            record(player.uniqueId,"genlite.inspect")
            service.definitions.flatMap { it.objectives }.filter { it.type=="genlite.inspect" && machine.type() in it.resource.orEmpty().split('|') }.map { it.metric }.distinct().forEach { record(player.uniqueId,it) }
        })
    }
    @EventHandler fun home(e: HomeMenuOpenedEvent) {
        if(integrations.island(e.playerId)?.id==e.homeId && e.menu=="ISLAND") record(e.playerId,"island.inspect")
    }
    @EventHandler(priority=EventPriority.MONITOR)
    fun kill(e: EntityDeathEvent) {
        val player=e.entity.killer ?: return
        if(e.entity.world.name !in worlds() || !player.hasPermission("quest.use")) return
        // Bukkit's actual killer attribution includes player projectiles; uncredited/environment-only deaths are excluded.
        record(player.uniqueId,"combat.kill:minecraft:${e.entity.type.key.key}")
    }
    override fun close() { closed=true;mythic.close();HandlerList.unregisterAll(this) }
}
