package ym.quest.introduction

import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import ym.quest.platform.GameThread

/** Reflection is confined here: Adyeshach relocates Kotlin and supplies TabooLib at runtime. */
class AdyeshachInteraction(plugin: JavaPlugin, thread: GameThread, npcId: String, interact: (Player) -> Unit) : AutoCloseable {
    private val listener = object : Listener {}
    private var closed = false
    init {
        val ady = checkNotNull(plugin.server.pluginManager.getPlugin("Adyeshach"))
        // 2.2 contains the core event and a legacy API event; both inherit a shared proxy HandlerList.
        val event = ady.javaClass.classLoader.loadClass("ink.ptms.adyeshach.core.event.AdyeshachEntityInteractEvent").asSubclass(Event::class.java)
        val entity = event.getMethod("getEntity")
        val player = event.getMethod("getPlayer")
        val hand = event.getMethod("isMainHand")
        val id = entity.returnType.getMethod("getId")
        plugin.server.pluginManager.registerEvent(event,listener,EventPriority.HIGHEST,{ _, e ->
            if(!closed && event.isInstance(e) && hand.invoke(e) == true && id.invoke(entity.invoke(e)) == npcId) {
                if((e as? Cancellable)?.isCancelled == true) return@registerEvent
                (e as? Cancellable)?.isCancelled = true
                val target = player.invoke(e) as Player
                thread.run { if(!closed && target.isOnline) interact(target) }
            }
        },plugin,true)
        plugin.logger.info("Introduction bound to Adyeshach NPC id=$npcId; existing name/model/location retained")
    }
    override fun close() { closed=true; HandlerList.unregisterAll(listener) }
}
