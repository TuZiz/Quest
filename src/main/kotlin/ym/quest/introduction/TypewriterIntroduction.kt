package ym.quest.introduction

import com.typewritermc.core.entries.Ref
import com.typewritermc.core.interaction.InteractionContext
import com.typewritermc.engine.paper.entry.Criteria
import com.typewritermc.engine.paper.entry.Modifier
import com.typewritermc.engine.paper.entry.TriggerableEntry
import com.typewritermc.engine.paper.entry.dialogue.DialogueMessenger
import com.typewritermc.engine.paper.entry.dialogue.MessengerState
import com.typewritermc.engine.paper.entry.dialogue.TickContext
import com.typewritermc.engine.paper.entry.dialogue.currentDialogue
import com.typewritermc.engine.paper.entry.dialogue.isInDialogue
import com.typewritermc.engine.paper.entry.entries.DialogueEntry
import com.typewritermc.engine.paper.entry.entries.SpeakerEntry
import com.typewritermc.engine.paper.entry.entries.EntryTrigger
import com.typewritermc.engine.paper.entry.triggerFor
import com.typewritermc.engine.paper.interaction.Confirmation
import com.typewritermc.engine.paper.interaction.interruptInteraction
import com.typewritermc.engine.paper.entry.temporal.isPlayingTemporal
import com.typewritermc.engine.paper.content.isInContentInteraction
import com.typewritermc.engine.paper.interaction.chatHistory
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import ym.quest.platform.GameThread
import ym.quest.text.TextRenderer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Thin DialogueEntry adapter; Typewriter owns sessions, input and dialogue teardown. No separate story engine. */
class TypewriterIntroduction(private val thread: GameThread, private val text: TextRenderer) : IntroductionDialogue {
    private val entries = ConcurrentHashMap<UUID, IntroductionEntry>()
    @Volatile private var closed = false
    override fun start(player: Player, lines: List<String>, finished: (Boolean) -> Unit): Boolean {
        if(closed || player.isInDialogue || player.isPlayingTemporal() || player.isInContentInteraction || entries.containsKey(player.uniqueId)) return false
        val entry = IntroductionEntry(player.uniqueId,lines,thread,text) { success ->
            thread.run { if(!closed) { entries.remove(player.uniqueId); finished(success) } }
        }
        entries[player.uniqueId] = entry
        try { EntryTrigger(entry).triggerFor(player,InteractionContext(emptyMap())) }
        catch(e: Exception) { entries.remove(player.uniqueId,entry); entry.active.set(false); throw e }
        catch(e: LinkageError) { entries.remove(player.uniqueId,entry); entry.active.set(false); throw e }
        return true
    }
    override fun cancel(player: Player) {
        val entry = entries.remove(player.uniqueId) ?: return
        entry.active.set(false)
        // Never interrupt another plugin's subsequent conversation.
        if(player.currentDialogue === entry) player.interruptInteraction(InteractionContext(emptyMap()))
    }
    override fun close() {
        closed=true
        entries.values.toList().forEach { entry ->
            org.bukkit.Bukkit.getPlayer(entry.playerId)?.let(::cancel)
            entry.active.set(false)
        }
        entries.clear()
    }

    private class IntroductionEntry(val playerId: UUID, val lines: List<String>, val thread: GameThread,
        val text: TextRenderer, val finished: (Boolean) -> Unit) : DialogueEntry {
        val active = AtomicBoolean(true)
        override val id = "quest_introduction_$playerId"
        override val name = id
        override val criteria = emptyList<Criteria>()
        override val modifiers = emptyList<Modifier>()
        override val triggers = emptyList<Ref<TriggerableEntry>>()
        override val speaker = TypewriterKotlinBoundary.emptySpeaker()
        override fun messenger(player: Player, context: InteractionContext) = object : DialogueMessenger<IntroductionEntry>(player,context,this) {
            private var confirmation: Confirmation? = null
            private var reported = false
            override fun init() {
                if(!active.get()) { state=MessengerState.CANCELLED; return }
                super.init()
                confirmation = TypewriterKotlinBoundary.confirm(player) { if(active.get()) completeOrFinish() }
                thread.run {
                    if(active.get() && player.isOnline) {
                        val content=lines.fold(Component.empty()) { result,line -> result.append(text.render(line)).append(Component.newline()) }
                            .append(text.format("introduction.confirm"))
                        // Use Typewriter's permitted dialogue message, otherwise its chat blocker hides the prompt.
                        player.sendMessage(player.chatHistory.composeDarkMessage(content))
                    }
                }
            }
            override fun tick(context: TickContext) {
                if(!active.get()) state=MessengerState.CANCELLED
            }
            override fun end() {
                // end() in beta-177 changes CANCELLED/RUNNING to FINISHED itself. Read BEFORE super.end().
                val completed = state == MessengerState.FINISHED
                super.end()
                if(!reported) { reported=true; if(active.getAndSet(false)) finished(completed) }
            }
            override fun dispose() { confirmation?.dispose(); confirmation=null; super.dispose() }
        }
    }
}
