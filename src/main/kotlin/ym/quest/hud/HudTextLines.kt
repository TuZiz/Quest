package ym.quest.hud

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.Style

/** Wrap visible glyphs and encode their RGB runs as native TabooLib Components, never raw task markup. */
internal class HudTextLines(private val width: Int) {
    private data class Glyph(val value: String, val color: String?)
    fun lines(source: Component, limit: Int): List<String> {
        val rows=mutableListOf<MutableList<Glyph>>()
        var row=mutableListOf<Glyph>()
        var used=0
        fun flush() { rows+=row; row=mutableListOf(); used=0 }
        fun visit(node: Component, parent: Style) {
            val style=node.style().merge(parent,Style.Merge.Strategy.IF_ABSENT_ON_TARGET)
            require(node is TextComponent) { "HUD requires rendered literal task text" }
            node.content().codePoints().forEach { point ->
                if(point==10) flush() else {
                    val size=if(point>255 || point==183) 2 else 1
                    if(used+size>width) flush()
                    row+=Glyph(String(Character.toChars(point)),style.color()?.asHexString())
                    used+=size
                }
            }
            node.children().forEach { visit(it,style) }
        }
        visit(source,Style.empty())
        if(row.isNotEmpty()) flush()
        if(rows.size>limit) {
            val last=rows[limit-1]
            fun units(g: Glyph)=if(g.value.codePointAt(0)>255 || g.value=="·") 2 else 1
            while(last.sumOf(::units)>width-2) last.removeLast()
            last+=Glyph("..",last.lastOrNull()?.color)
        }
        return rows.take(limit).map { glyphs ->
            buildString {
                var index=0
                while(index<glyphs.size) {
                    val color=glyphs[index].color
                    val run=StringBuilder()
                    while(index<glyphs.size && glyphs[index].color==color) run.append(glyphs[index++].value)
                    val escaped=escape(run.toString())
                    if(color==null) append(escaped) else append("[$escaped](color=$color)")
                }
            }
        }
    }
    companion object {
        fun escape(value: String)=buildString { value.forEach { if(it in "\\[]();=") append('\\'); append(it) } }
        fun nativeKey(key: String)=key.matches(Regex("(?:hint-[1-6]|objective-[12])"))
    }
}
