package ym.quest.preferences

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import ym.quest.platform.GameThread
import ym.quest.storage.Database
import java.util.UUID

/** Paper-owned session cache. SQL results are applied only to the original online player connection. */
class PlayerDisplayPreferences(private val plugin: JavaPlugin, db: Database, private val thread: GameThread,
    private val failure: (Throwable) -> Unit) : Listener,AutoCloseable {
    private class State(val player: Player) { var value: DisplayPreferences?=null; var writing=false; var failed=false }
    private val store=DisplayPreferenceStore(db)
    private val states=mutableMapOf<UUID,State>()
    private var closed=false
    init { plugin.server.pluginManager.registerEvents(this,plugin); plugin.server.onlinePlayers.forEach(::load) }
    private fun valid(s: State)=!closed && s.player.isOnline && states[s.player.uniqueId]===s
    fun load(player: Player) {
        if(closed || states[player.uniqueId]?.player===player) return
        val state=State(player);states[player.uniqueId]=state
        store.load(player.uniqueId).whenComplete { value,error -> thread.run {
            if(!valid(state)) return@run
            if(error!=null) { state.failed=true;failure(IllegalStateException("Display preferences load; player=${player.uniqueId}",error)) }
            else state.value=value
        } }
    }
    fun get(player: Player): DisplayPreferences? { load(player);return states[player.uniqueId]?.value }
    fun failed(player: Player)=states[player.uniqueId]?.failed==true
    fun update(player: Player,change: (DisplayPreferences)->DisplayPreferences,done: (Boolean)->Unit) {
        load(player)
        val s=states[player.uniqueId] ?: return
        val value=s.value
        if(value==null || s.writing || !valid(s)) { done(false);return }
        s.writing=true
        store.save(player.uniqueId,change(value)).whenComplete { next,error -> thread.run {
            if(!valid(s)) return@run
            s.writing=false
            if(error!=null) { failure(IllegalStateException("Display preferences save; player=${player.uniqueId}; previous settings retained",error));done(false) }
            else { s.value=next;done(true) }
        } }
    }
    @EventHandler fun join(e: PlayerJoinEvent)=load(e.player)
    @EventHandler fun quit(e: PlayerQuitEvent) { states.remove(e.player.uniqueId) }
    override fun close() { closed=true;states.clear();HandlerList.unregisterAll(this) }
}
