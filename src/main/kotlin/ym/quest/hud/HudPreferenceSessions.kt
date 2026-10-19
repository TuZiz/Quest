package ym.quest.hud

import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import ym.quest.platform.GameThread
import ym.quest.storage.HudPreferenceStore
import ym.quest.text.TextRenderer
import java.util.UUID

/** Paper owner cache; identity checks reject results from quit/reload/old sessions. */
internal class HudPreferenceSessions(private val store: HudPreferenceStore, private val thread: GameThread,
    private val legacyKey: NamespacedKey, private val text: TextRenderer, private val failure: (Throwable) -> Unit) {
    private class Session(var value: HudPreference? = null, var saving: Boolean = false)
    private val sessions=mutableMapOf<UUID,Session>()
    fun get(player: Player) = sessions[player.uniqueId]?.value
    fun ensure(player: Player, ready: () -> Unit) {
        if(player.uniqueId in sessions) return
        val session=Session(); sessions[player.uniqueId]=session
        val legacy=if(player.persistentDataContainer.get(legacyKey,PersistentDataType.BYTE) == 1.toByte()) HudMode.OFF else HudMode.SMART
        store.load(player.uniqueId,legacy).whenComplete { result,error -> thread.run {
            if(sessions[player.uniqueId] !== session || !player.isOnline) return@run
            if(error != null) { failure(IllegalStateException("HUD preference load player=${player.uniqueId}",error)); return@run }
            session.value=result
            player.persistentDataContainer.remove(legacyKey)
            ready()
        } }
    }
    fun update(player: Player, value: HudPreference, ready: () -> Unit) {
        val session=sessions[player.uniqueId]
        if(session?.value == null) { text.send(player,"loading"); return }
        if(session.saving) { text.send(player,"busy"); return }
        session.saving=true
        store.save(player.uniqueId,value).whenComplete { result,error -> thread.run {
            if(sessions[player.uniqueId] !== session || !player.isOnline) return@run
            session.saving=false
            if(error != null) { failure(IllegalStateException("HUD preference save player=${player.uniqueId}",error)); text.send(player,"error"); return@run }
            session.value=result; ready()
        } }
    }
    fun remove(id: UUID) { sessions.remove(id) }
    fun clear() { sessions.clear() }
}
