package ym.quest.hud

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.Style

/** Wrap visible glyphs and encode their RGB runs as native TabooLib Components, never raw task markup. */
internal class HudTextLines(
    private val width: Int,
    private val firstLineWidth: Int = width,
    private val keepWords: Boolean = false
) {
    private data class Glyph(val value: String, val color: String?) {
        val units: Int get() = if(value.codePointAt(0)>255 || value=="·") 2 else 1
    }
    fun lines(source: Component, limit: Int): List<String> {
        require(width >= 2 && firstLineWidth in 2..width && limit > 0)
        val glyphs=mutableListOf<Glyph>()
        val rows=mutableListOf<MutableList<Glyph>>()
        var row=mutableListOf<Glyph>()
        var used=0
        fun budget()=if(rows.isEmpty()) firstLineWidth else width
        fun flush() { rows+=row; row=mutableListOf(); used=0 }
        fun visit(node: Component, parent: Style) {
            val style=node.style().merge(parent,Style.Merge.Strategy.IF_ABSENT_ON_TARGET)
            require(node is TextComponent) { "HUD requires rendered literal task text" }
            node.content().codePoints().forEach { point ->
                glyphs+=Glyph(String(Character.toChars(point)),style.color()?.asHexString())
            }
            node.children().forEach { visit(it,style) }
        }
        visit(source,Style.empty())
        var cursor=0
        while(cursor<glyphs.size) {
            val start=cursor
            val glyph=glyphs[cursor++]
            if(glyph.value=="\n") { flush(); continue }
            if(keepWords && !glyph.value.isBlank()) {
                // Colored semantic phrases and ASCII words are indivisible when they fit a row.
                while(cursor<glyphs.size && !glyphs[cursor].value.isBlank() && glyphs[cursor].color==glyph.color) cursor++
                if(glyphs.subList(start,cursor).sumOf { it.units }>width) {
                    cursor=start+1
                    if(glyph.value.singleOrNull()?.let { it.isLetterOrDigit() && it.code<128 }==true) {
                        while(cursor<glyphs.size && glyphs[cursor].value.singleOrNull()?.let {
                            it.code<128 && (it.isLetterOrDigit() || it in "._/-")
                        }==true) cursor++
                    }
                }
            }
            val token=glyphs.subList(start,cursor)
            if(used+token.sumOf { it.units }>budget() && row.isNotEmpty()) flush()
            if(keepWords && row.isEmpty() && token.all { it.value.isBlank() }) continue
            for(part in token) {
                if(used+part.units>budget()) flush()
                row+=part
                used+=part.units
            }
        }
        if(row.isNotEmpty()) flush()
        if(rows.size>limit) {
            val last=rows[limit-1]
            val lastBudget=if(limit==1) firstLineWidth else width
            while(last.sumOf { it.units }>lastBudget-2) last.removeLast()
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
        fun nativeKey(key: String)=key.matches(Regex("(?:hint-[1-6]|objective-[1-3])"))
    }
}
