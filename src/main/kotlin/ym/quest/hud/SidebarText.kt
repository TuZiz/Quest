package ym.quest.hud

/** Pure projection using the deployed ui font: ASCII is one half-em, Chinese two. */
internal object SidebarText {
    fun lines(text: String, units: Int): List<String> {
        require(units >= 2)
        val result=mutableListOf<String>()
        val line=StringBuilder()
        var used=0
        text.codePoints().forEach { point ->
            val width=if(point in 32..126) 1 else 2
            if(point == 10 || used+width > units) {
                result.add(line.toString().trim()); line.setLength(0); used=0
            }
            if(point != 10) { line.appendCodePoint(point); used+=width }
        }
        if(line.isNotEmpty() || result.isEmpty()) result.add(line.toString().trim())
        return result
    }

    fun values(target: TrackerTarget, background: Boolean, bar: Boolean, plain: (String) -> String): Map<String,String> {
        val count=target.current.coerceAtLeast(0).coerceAtMost(target.target.coerceAtLeast(0))
        val ratio=if(target.target > 0) count.toDouble()/target.target else 0.0
        val filled=(ratio*10).toInt().coerceIn(0,10)
        val objective=plain(target.values.getValue("objective")).ifBlank { plain(target.values.getValue("task")) }
        val goal=lines(HudText.label(objective),26)
        val chapter=lines(HudText.label(plain(target.values.getValue("chapter"))),26)
        return target.values.mapValues { plain(it.value) } + mapOf(
            "chapter" to HudText.fit(chapter.joinToString(" "),26),
            "goal" to goal.first(), "goal_more" to HudText.fit(goal.drop(1).joinToString(" "),26),
            "current" to count.toString(), "percent" to (ratio*100).toInt().toString(),
            "bar_full" to "█".repeat(filled), "bar_empty" to "░".repeat(10-filled),
            "show_bar" to bar.toString(), "show_background" to background.toString())
    }
}
