package ym.quest.tutorial

import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.inventory.ItemStack
import java.util.logging.Level

/** Optional MythicMobs 5 event adapter. No Mythic classes enter Quest's mandatory linkage graph. */
class MythicCombat(private val plugin: JavaPlugin, private val kill: (Player,LivingEntity,String,List<ItemStack>) -> Unit) : Listener, AutoCloseable {
    init {
        plugin.server.pluginManager.getPlugin("MythicMobs")?.takeIf { it.isEnabled }?.let { mythic ->
            try {
                val eventType=mythic.javaClass.classLoader.loadClass("io.lumine.mythic.bukkit.events.MythicMobDeathEvent").asSubclass(Event::class.java)
                val getKiller=eventType.getMethod("getKiller")
                val getEntity=eventType.getMethod("getEntity")
                val getType=eventType.getMethod("getMobType")
                val getDrops=eventType.getMethod("getDrops")
                val typeClass=mythic.javaClass.classLoader.loadClass("io.lumine.mythic.api.mobs.MythicMob")
                val getName=typeClass.getMethod("getInternalName")
                plugin.server.pluginManager.registerEvent(eventType,this,EventPriority.MONITOR,{ _,event ->
                    try {
                        val player=getKiller.invoke(event) as? Player ?: return@registerEvent
                        val entity=getEntity.invoke(event) as? LivingEntity ?: return@registerEvent
                        val name=getName.invoke(getType.invoke(event)) as String
                        val drops=(getDrops.invoke(event) as List<*>).filterIsInstance<ItemStack>()
                        kill(player,entity,"mythic:$name",drops)
                    } catch(error: ReflectiveOperationException) {plugin.logger.log(Level.SEVERE,"MythicMobs combat event API failed; no progress credited",error)}
                },plugin)
            } catch(error: ReflectiveOperationException) {
                plugin.logger.log(Level.WARNING,"MythicMobs event API incompatible; custom combat objectives unavailable",error)
            }
        }
    }
    override fun close() {HandlerList.unregisterAll(this)}
}
