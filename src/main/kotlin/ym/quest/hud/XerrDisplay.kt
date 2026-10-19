package ym.quest.hud

import com.hiusers.xerr.api.XerrAPI
import com.hiusers.xerr.api.container.BossbarHudRenderer
import com.hiusers.xerr.api.builder.ComponentBuilder.buildRaw
import com.hiusers.xerr.api.font.PositionedText
import com.google.gson.JsonParser
import org.bukkit.entity.Player

/** Version-bound Xerr 2.1 API. Never clears another plugin's HUD or popup group. Paper thread only. */
internal class XerrDisplay {
    fun ready(layout: String) = XerrAPI.get().layouts().exists(layout)
    fun present(player: Player, layout: String) = BossbarHudRenderer.getBossbarContext(player).components.containsKey(layout)
    @JvmOverloads fun update(player: Player, layout: String, values: Map<String,String>, offset: Int = 0, slot: String = layout) {
        // Xerr 2.1 appendLayout leaves intermediate _position_N fonts unresolved.
        // Resolve through its own positioned-text pipeline before the raw HUD API.
        val source = XerrAPI.get().layouts().renderString(player,layout,values,true,"bossbar:$layout") ?: return
        val raw = source.buildRaw()
        val positioned = PositionedText.render(JsonParser.parseString(raw),emptyMap(),255,offset)
        BossbarHudRenderer.appendLayoutRaw(player,slot,positioned)
    }
    fun remove(player: Player, layout: String) = BossbarHudRenderer.removeLayout(player,layout)
}
