package ym.quest.hud

import com.google.gson.JsonParser
import com.hiusers.xerr.api.XerrAPI
import com.hiusers.xerr.api.builder.ComponentBuilder.buildRaw
import com.hiusers.xerr.api.container.BossbarHudRenderer
import com.hiusers.xerr.api.font.PositionedText
import org.bukkit.entity.Player
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

/** Xerr 2.2.0 native TabooLib Components. All calls require the Paper ownership thread. */
class XerrHudRenderer(private val settings: HudSettings) {
    private val nativeColors=Regex("(?<=\\(color=)#[0-9a-fA-F]{6}(?=\\))")
    private val transport=LegacyComponentSerializer.builder().hexColors().useUnusualXRepeatedCharacterHexFormat().build()
    private val cache=object : LinkedHashMap<String,String>(128,0.75f,true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String,String>?)=size>256
    }
    fun styled(key: String, value: String): String {
        if(value.isBlank()) return ""
        val field=key.removePrefix("side-")
        val native=HudTextLines.nativeKey(field)
        val alias=when {
            field.startsWith("hint-") -> "hint"
            field=="objective-1" -> "objective"
            field=="objective-2" -> "objective-continuation"
            else -> field
        }
        val template=settings.templates[key] ?: settings.templates[alias] ?: "{value}"
        // Xerr's font wrapper escapes Components brackets. Parse natively BEFORE Xerr measures text.
        // Legacy is an internal RGB transport here, never the author-facing configuration syntax.
        val escaped=if(native) nativeColors.replace(value) {
            settings.colorMap[it.value.uppercase()] ?: it.value
        } else HudTextLines.escape(value)
        val source=template.replace("{value}",escaped)
        return cache.getOrPut(source) {
            transport.serialize(GsonComponentSerializer.gson().deserialize(source.buildRaw()))
        }
    }
    fun ready(settings: HudSettings): Boolean = XerrAPI.get().layouts().let { it.exists(settings.tracker) && it.exists(settings.notice) }
    fun present(player: Player, slot: String) = BossbarHudRenderer.getBossbarContext(player).components.containsKey(slot)
    fun render(player: Player, slot: String, layout: String, values: Map<String,String>, alpha: Int = 255) {
        val source=XerrAPI.get().layouts().renderString(player,layout,values.mapValues { (key,value) -> styled(key,value) },true,"bossbar:$slot")
            ?: error("Xerr layout=$layout returned no component; player=${player.uniqueId}")
        // Resolve virtual ui_position_N fonts to the real resource-pack fonts before submission.
        val raw=source.buildRaw()
        val positioned=PositionedText.render(JsonParser.parseString(raw),emptyMap(),alpha,0)
        BossbarHudRenderer.appendLayoutRaw(player,slot,positioned)
    }
    fun remove(player: Player, slot: String) = BossbarHudRenderer.removeLayout(player,slot)
}
