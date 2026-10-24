package ym.quest.tab

import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import ym.quest.domain.JournalView
import ym.quest.domain.QuestStatus
import ym.quest.domain.QuestCategory
import ym.quest.domain.QuestView
import ym.quest.text.TextRenderer

/** Pure read-only projection. TAB receives serialized MiniMessage, never database or Bukkit objects. */
class TabQuestProjection(private val text: TextRenderer, private val width: Int) {
    private val mini = MiniMessage.miniMessage()
    private val plain = PlainTextComponentSerializer.plainText()
    private val styledWrap = TabTextWrap(width)

    fun state(key: String): List<String> {
        val message = text.format("tab.$key")
        return pad(wrap(plain.serialize(message)).map { value ->
            mini.serialize(text.format("tab.state-line", mapOf("value" to Component.text(value).style(message.style()))))
        })
    }

    fun project(journal: JournalView): List<String> {
        val main=journal.quests.filter { it.definition.category==QuestCategory.MAIN }
        val quest=main.firstOrNull { it.status==QuestStatus.ACTIVE }
        val lines=mutableListOf(mini.serialize(text.format("tab-groups.main-heading")))
        if(quest==null) {
            val key=if(main.isNotEmpty() && main.all { it.status==QuestStatus.COMPLETE || it.status==QuestStatus.CLAIMED })
                "tab-groups.main-done" else "tab.waiting"
            lines+=styledWrap.lines(text.format(key)).take(2).map(mini::serialize)
        } else {
            val chapter=journal.chapters.firstOrNull { quest.definition.id in it.definition.quests }
            if(chapter!=null) wrap(label(chapter.definition.name).replace(Regex("\\s*·\\s*"),"·")).take(1).forEach { lines+=line("chapter-line","chapter" to it) }
            wrap(label(quest.definition.name)).take(2).forEach { lines+=line("quest-line","quest" to it) }
            val objective=quest.progress.firstOrNull { it.progress<it.objective.target } ?: quest.progress.firstOrNull()
            if(objective!=null) {
                lines+=styledLines("objective-line",mapOf("objective" to text.render(objective.objective.description))).take(3)
                lines+=line("progress-line","progress" to objective.progress.coerceAtMost(objective.objective.target),"target" to objective.objective.target)
                if(quest.progress.size>1) lines+=line("objectives-line","completed" to quest.progress.count { it.progress>=it.objective.target },"total" to quest.progress.size)
            }

        }
        val result=MutableList(MAIN_LINES) { lines.getOrElse(it) { "" } }
        result+=""
        result+=mini.serialize(text.format("tab-groups.side-heading"))
        val side=journal.quests.filter { it.definition.category==QuestCategory.SIDE && it.status==QuestStatus.ACTIVE }
        if(side.isEmpty()) result+=mini.serialize(text.format("tab-groups.side-empty"))
        else side.forEach { q ->
            val objective=q.progress.firstOrNull { it.progress<it.objective.target } ?: q.progress.firstOrNull()
            val values=TextRenderer.values("quest" to label(q.definition.name),"progress" to (objective?.progress ?: 0),"target" to (objective?.objective?.target ?: 0))
            result+=mini.serialize(text.format("tab-groups.side-entry",values))
            if(objective!=null) result+=styledWrap.lines(text.format("tab-groups.side-objective",mapOf("objective" to text.render(objective.objective.description)))).take(1).map(mini::serialize)
            else result+=""
        }
        return pad(result.take(LINE_COUNT))
    }

    private fun raw(value: String) = plain.serialize(text.render(value)).trim()
    private fun label(value: String) = raw(value).trimStart('✦', '▪', ' ')
    private fun line(key: String, vararg values: Pair<String, Any>) = mini.serialize(text.format("tab.$key", TextRenderer.values(*values)))
    private fun styledLines(key: String, values: Map<String, Component>) =
        styledWrap.lines(text.format("tab.$key", values)).map(mini::serialize)
    private fun pad(lines: List<String>) = List(LINE_COUNT) { lines.getOrElse(it) { "" } }

    private fun wrap(value: String): List<String> {
        val lines = mutableListOf<String>()
        val current = StringBuilder()
        var used = 0
        value.codePoints().forEach { point ->
            val size = if(point > 255) 2 else 1
            if(point == 10 || used + size > width) {
                lines += current.toString().trimEnd(); current.clear(); used = 0
            }
            if(point != 10) { current.appendCodePoint(point); used += size }
        }
        if(current.isNotEmpty()) lines += current.toString().trimEnd()
        return lines
    }

    companion object {
        const val LINE_COUNT = 18
        private const val MAIN_LINES = 8
        val requiredMessages = listOf("loading", "no-island", "no-permission", "unavailable", "error", "done", "waiting",
            "state-line", "chapter-line", "quest-line", "objective-line", "progress-line", "objectives-line", "hint-line", "shared-line")
            .map { "tab.$it" } + listOf("main-heading","main-done","side-heading","side-empty","side-entry","side-objective").map { "tab-groups.$it" }
    }
}
