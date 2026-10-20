package ym.quest.tab

import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import ym.quest.domain.JournalView
import ym.quest.domain.QuestStatus
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
        val quest = journal.quests.firstOrNull { it.status == QuestStatus.ACTIVE }
            ?: return state(if(journal.quests.isNotEmpty() && journal.quests.all {
                it.status == QuestStatus.COMPLETE || it.status == QuestStatus.CLAIMED
            }) "done" else "waiting")
        val chapter = journal.chapters.firstOrNull { quest.definition.id in it.definition.quests }
        val objective = quest.progress.firstOrNull { it.progress < it.objective.target } ?: quest.progress.firstOrNull()
        val lines = mutableListOf<String>()
        if(chapter != null) wrap(label(chapter.definition.name).replace(Regex("\\s*·\\s*"), "·")).take(2).forEach { lines += line("chapter-line", "chapter" to it) }
        if(chapter != null) lines += ""
        wrap(label(quest.definition.name)).take(2).forEach { lines += line("quest-line", "quest" to it) }
        if(objective != null) {
            lines += styledLines("objective-line", mapOf("objective" to text.render(objective.objective.description))).take(3)
            lines += line("progress-line", "progress" to objective.progress.coerceAtMost(objective.objective.target), "target" to objective.objective.target)
            if(quest.progress.size > 1) lines += line("objectives-line", "completed" to quest.progress.count { it.progress >= it.objective.target }, "total" to quest.progress.size)
        }
        val hints = quest.definition.description.filter { raw(it).isNotBlank() }
        if(hints.isNotEmpty()) lines += ""
        hints.flatMapIndexed { index, hint ->
            styledLines("hint-line", mapOf("step" to Component.text(index + 1), "hint" to text.render(hint)))
        }.take(6).forEach { lines += it }
        return pad(lines.take(LINE_COUNT))
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
        val requiredMessages = listOf("loading", "no-island", "no-permission", "unavailable", "error", "done", "waiting",
            "state-line", "chapter-line", "quest-line", "objective-line", "progress-line", "objectives-line", "hint-line", "shared-line")
            .map { "tab.$it" }
    }
}
