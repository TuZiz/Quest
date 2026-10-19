package ym.quest.hud

/** Width budget for the server's configured 12px monospaced SC font, in half-em units. */
object HudText {
    /** HUD already owns its icon; menu-only prefixes are absent from the selected pixel font. */
    fun label(text: String) = text.trimStart().trimStart('✦','✧','★','☆','❖','▪').trimStart()
    fun fit(text: String, units: Int): String {
        fun width(c: Char) = if(c.code <= 255 && c !in "©®·¼½¾") 1 else 2
        if(text.sumOf(::width) <= units) return text
        var used=0
        return text.takeWhile { c -> (used+width(c) <= units-2).also { if(it) used+=width(c) } } + "…"
    }
}
