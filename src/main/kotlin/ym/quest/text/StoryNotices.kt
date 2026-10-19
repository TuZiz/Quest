package ym.quest.text

import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.title.Title
import org.bukkit.entity.Player
import ym.quest.config.NoticeProfile
import ym.quest.domain.*
import java.time.Duration

/** Called only on the Paper ownership thread. Each channel can be disabled independently. */
class StoryNotices @JvmOverloads constructor(private val text: TextRenderer, var profiles: Map<String,NoticeProfile>,
                   private val popup: (Player,String,net.kyori.adventure.text.Component,net.kyori.adventure.text.Component) -> Unit = { _,_,_,_ -> }) {
    fun send(player: Player, change: ProgressChange, quests: List<QuestDefinition>, chapters: List<ChapterDefinition>) {
        val chapter = chapters.firstOrNull { it.id == change.quest && change.chapter }
        val quest = quests.firstOrNull { it.id == change.quest && !change.chapter }
        if(chapter == null && quest == null) return
        val events = when {
            change.chapter -> listOf(if(change.completed) "chapter-complete" else "chapter-start")
            change.completed -> listOf("task-complete")
            change.started -> listOf("task-start") + if(quest!!.prerequisites.isEmpty()) emptyList() else listOf("next-task")
            else -> return
        }
        events.forEach { event ->
            val p = profiles.getValue(event)
            val values = mapOf("quest" to text.render(quest?.name ?: ""),"chapter" to text.render(chapter?.name ?: ""))
            popup(player,event,text.format("notices.$event.title",values),text.format("notices.$event.message",values))
            if(p.chat) {
                text.send(player,"notices.$event.message",values)
                if(event == "chapter-start") chapter!!.story.forEach { player.sendMessage(text.render(it,values)) }
            }
            if(p.actionBar) player.sendActionBar(text.format("notices.$event.message",values))
            if(p.title) player.showTitle(Title.title(text.format("notices.$event.title",values),
                text.format("notices.$event.subtitle",values),Title.Times.times(Duration.ofMillis(300),Duration.ofSeconds(2),Duration.ofMillis(500))))
            p.sound?.let { player.playSound(Sound.sound(Key.key(it),Sound.Source.MASTER,p.volume,p.pitch)) }
        }
    }
}
