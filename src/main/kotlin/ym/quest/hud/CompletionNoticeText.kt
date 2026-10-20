package ym.quest.hud

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import ym.quest.text.TextRenderer

/** Presentation only: completion is not proof that an asynchronous reward has arrived. */
internal object CompletionNoticeText {
    fun values(notice: HudNotice, text: TextRenderer, plain: (String) -> String): Map<String,String> {
        val name=HudText.label(plain(notice.name))
        val placeholders=TextRenderer.values("name" to name,"next" to HudText.label(plain(notice.next ?: "")))
        fun line(field: String) = HudText.fit(PlainTextComponentSerializer.plainText().serialize(
            text.format("completion-hud.${notice.event}.$field",placeholders)),27)
        return mapOf("heading" to line("heading"),"detail" to line("detail"),"name" to HudText.fit(name,27))
    }
}
