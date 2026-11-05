package ym.quest.introduction

import com.typewritermc.core.interaction.InteractionContext
import com.typewritermc.engine.paper.entry.TriggerableEntry
import com.typewritermc.engine.paper.entry.dialogue.currentDialogue
import com.typewritermc.engine.paper.entry.dialogue.isInDialogue
import com.typewritermc.engine.paper.entry.entries.DialogueEntry
import com.typewritermc.engine.paper.entry.entries.EntryTrigger
import com.typewritermc.engine.paper.entry.triggerFor
import com.typewritermc.engine.paper.entry.temporal.isPlayingTemporal
import com.typewritermc.engine.paper.content.isInContentInteraction
import com.typewritermc.engine.paper.events.AsyncDialogueEndEvent
import com.typewritermc.engine.paper.interaction.interruptInteraction
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import ym.quest.platform.GameThread
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Main-thread bridge to published Typewriter entries; no text, messenger or confirmation implementation. */
class TypewriterIntroduction(private val plugin: JavaPlugin, private val thread: GameThread,
    private val entryId: String) : IntroductionDialogue, Listener {
    private class Session(val player: Player,val completion: DialogueCompletion,val entries: Set<String>) {
        var endTask: BukkitTask?=null
    }
    private val sessions=ConcurrentHashMap<UUID,Session>()
    private var closed=false
    init { plugin.server.pluginManager.registerEvents(this,plugin) }

    override fun start(player: Player,finished: (Boolean) -> Unit): Boolean {
        if(closed || player.isInDialogue || player.isPlayingTemporal() || player.isInContentInteraction || sessions.containsKey(player.uniqueId)) return false
        val entry=TypewriterKotlinBoundary.entry(entryId) as? DialogueEntry
            ?: error("Typewriter dialogue entry '$entryId' missing; check Typewriter/pages and matching BasicExtension")
        val owned=reachable(entry)
        val completion=DialogueCompletion(finished)
        val s=Session(player,completion,owned)
        sessions[player.uniqueId]=s
        // Official context-string variable supplies the terminal console action's per-session command.
        val context=TypewriterKotlinBoundary.completionContext(
            "quest:quest _typewriter-complete ${player.uniqueId} ${completion.token}")
        try { EntryTrigger(entry).triggerFor(player,context) }
        catch(e: Exception) { sessions.remove(player.uniqueId,s); completion.cancel(); throw e }
        catch(e: LinkageError) { sessions.remove(player.uniqueId,s); completion.cancel(); throw e }
        return true
    }
    private fun reachable(first: TriggerableEntry): Set<String> {
        val found=linkedSetOf<String>()
        val pending=ArrayDeque<TriggerableEntry>().apply { add(first) }
        while(pending.isNotEmpty()) {
            val entry=pending.removeFirst()
            if(!found.add(entry.id)) continue
            require(found.size<=256) { "Typewriter guide '$entryId' exceeds 256 reachable entries" }
            entry.triggers.mapNotNull { it.get() }.forEach(pending::addLast)
        }
        return found
    }
    override fun complete(playerId: UUID,token: UUID): Boolean {
        val s=sessions[playerId] ?: return false
        if(!s.player.isOnline || s.completion.token!=token) return false
        sessions.remove(playerId,s);s.endTask?.cancel()
        return s.completion.complete(token)
    }
    @EventHandler fun ended(event: AsyncDialogueEndEvent) {
        val s=sessions[event.player.uniqueId] ?: return
        thread.run {
            if(closed || sessions[s.player.uniqueId]!==s) return@run
            s.endTask?.cancel()
            // Native final actions are queued on Paper's sync dispatcher. Let their nonce callback win.
            s.endTask=plugin.server.scheduler.runTaskLater(plugin,Runnable {
                if(s.player.currentDialogue?.id !in s.entries && sessions.remove(s.player.uniqueId,s)) s.completion.interrupt()
            },2)
        }
    }
    override fun cancel(player: Player) {
        val s=sessions.remove(player.uniqueId) ?: return
        s.endTask?.cancel();s.completion.cancel()
        if(plugin.server.pluginManager.isPluginEnabled("Typewriter") && player.currentDialogue?.id in s.entries)
            player.interruptInteraction(InteractionContext(emptyMap()))
    }
    override fun close() {
        closed=true
        HandlerList.unregisterAll(this)
        sessions.values.toList().forEach { cancel(it.player) }
        sessions.clear()
    }
}
