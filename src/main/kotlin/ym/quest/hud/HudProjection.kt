package ym.quest.hud

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.kyori.adventure.text.Component
import ym.quest.domain.*
import ym.quest.text.TextRenderer

/** Immutable read-only projection; never counts progress or distributes rewards. */
class HudProjection(private val text: TextRenderer, private val width: Int) {
    private val plain=PlainTextComponentSerializer.plainText()
    fun label(value: String): String = plain.serialize(text.render(value)).trim().trimStart('✦','◆','▪',' ')
    fun clip(value: String, limit: Int = width): String {
        val points=label(value).replace('\n',' ').codePoints().toArray()
        fun size(point: Int)=if(point > 255 || point == 183) 2 else 1
        if(points.sumOf(::size) <= limit) return String(points,0,points.size)
        var used=0
        return buildString {
            for(point in points) {
                if(used+size(point)>limit-2) break
                appendCodePoint(point); used+=size(point)
            }
            append("..")
        }
    }
    @JvmOverloads fun project(view: JournalView, tracked: String? = null, autoTracking: Boolean = true, personal: JournalView? = null): Map<String,String>? {
        val journal=if(personal==null) view else JournalView((personal.quests+view.quests).distinctBy { it.definition.id },personal.chapters+view.chapters)
        val quest=ym.quest.preferences.TrackingSelection.select(journal,ym.quest.preferences.DisplayPreferences(autoTracking=autoTracking,trackedQuest=tracked)) ?: return null
        val chapter=journal.chapters.firstOrNull { quest.definition.id in it.definition.quests }
        val objective=quest.progress.firstOrNull { it.progress < it.objective.target } ?: quest.progress.firstOrNull() ?: return null
        val current=objective.progress.coerceIn(0,objective.objective.target)
        val values=mutableMapOf("category" to label(text.language.getString("journal.category-${quest.definition.category}")!!),"chapter" to clip(chapter?.definition?.name ?: ""),"task" to clip(quest.definition.name),
            "objective" to clip(objective.objective.description),"current" to current.toString(),
            "target" to objective.objective.target.toString(),"progress-text" to "$current / ${objective.objective.target}")
        // Reserve six half-width units for the server-configured objective label.
        val objectives=HudTextLines(width-6).lines(text.render(objective.objective.description),2)
        repeat(2) { values["objective-${it+1}"]=objectives.getOrElse(it) { "" } }
        val hints=quest.definition.description.filter { label(it).isNotBlank() }
        val source=hints.mapIndexed { index,hint ->
            Component.text("${index+1}. ").append(text.render(hint))
        }.fold(Component.empty()) { all,line -> if(all==Component.empty()) line else all.append(Component.newline()).append(line) }
        val lines=HudTextLines(width).lines(source,6)
        repeat(6) { values["hint-${it+1}"]=lines.getOrElse(it) { "" } }
        return values
    }
    fun completion(changes: List<ProgressChange>, quests: List<QuestDefinition>, chapters: List<ChapterDefinition>): List<HudNotice> {
        val completedChapters=changes.filter { it.chapter && it.completed }.map { it.quest }.toSet()
        val mergedQuests=chapters.filter { it.id in completedChapters }.flatMap { it.quests }.toSet()
        return changes.filter { it.completed && (it.chapter || it.quest !in mergedQuests) }.mapNotNull { change ->
            val name=if(change.chapter) chapters.firstOrNull { it.id == change.quest }?.name else quests.firstOrNull { it.id == change.quest }?.name
            name ?: return@mapNotNull null
            val next=changes.firstOrNull { it.started && !it.chapter && !it.completed }?.let { start -> quests.firstOrNull { it.id == start.quest }?.name }
            notice((if(change.chapter) "chapter:" else "task:")+change.quest,name,change.chapter,next)
        }.distinctBy { it.key }
    }
    fun notice(key: String, name: String, chapter: Boolean, next: String? = null): HudNotice {
        val message=if(next == null) text.language.getString(if(chapter) "hud.chapter-detail" else "hud.task-detail")!!
            else text.language.getString("hud.next")!!.replace("{next}",clip(next,28))
        return HudNotice(key,chapter,mapOf("heading" to text.language.getString(if(chapter) "hud.chapter-complete" else "hud.task-complete")!!,
            "name" to clip(name,28),"detail" to message))
    }
}
