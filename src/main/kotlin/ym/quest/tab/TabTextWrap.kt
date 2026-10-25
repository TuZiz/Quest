package ym.quest.tab

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.Style

/** Wrap rendered text, carrying inherited styles onto each visible glyph. */
internal class TabTextWrap(private val width: Int) {
    fun lines(source: Component, continuationIndent: Int = 0): List<Component> {
        require(continuationIndent in 0 until width)
        val result = mutableListOf<Component>()
        var current = Component.empty()
        var used = 0
        fun flush() {
            result += current.compact()
            current = Component.text(" ".repeat(continuationIndent))
            used = continuationIndent
        }
        fun visit(node: Component, parent: Style) {
            val style = node.style().merge(parent, Style.Merge.Strategy.IF_ABSENT_ON_TARGET)
            require(node is TextComponent) { "TAB wrapping requires rendered literal text" }
            node.content().codePoints().forEach { point ->
                if(point == 10) flush()
                else {
                    val size = if(point > 255) 2 else 1
                    if(used + size > width) flush()
                    current = current.append(Component.text(String(Character.toChars(point))).style(style))
                    used += size
                }
            }
            node.children().forEach { visit(it, style) }
        }
        visit(source, Style.empty())
        if(used > continuationIndent || result.isEmpty() && used > 0) result += current.compact()
        return result
    }
}
