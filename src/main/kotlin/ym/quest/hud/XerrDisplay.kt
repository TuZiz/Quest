package ym.quest.hud

import com.hiusers.xerr.api.XerrAPI
import com.hiusers.xerr.api.container.BossbarHudRenderer
import com.hiusers.xerr.api.builder.ComponentBuilder.buildRaw
import com.hiusers.xerr.api.font.PositionedText
import com.google.gson.JsonParser
import org.bukkit.entity.Player
import java.util.concurrent.CompletableFuture

/** Version-bound Xerr 2.1 API. Never clears another plugin's HUD or popup group. Paper thread only. */
internal class XerrDisplay {
    fun ready(layout: String) = XerrAPI.get().layouts().exists(layout)
    fun update(player: Player, layout: String, values: Map<String,String>) {
        // Xerr 2.1 appendLayout leaves intermediate _position_N fonts unresolved.
        // Resolve through its own positioned-text pipeline before the raw HUD API.
        val source = XerrAPI.get().layouts().renderString(player,layout,values,true,"bossbar:$layout") ?: return
        val raw = source.buildRaw()
        val positioned = PositionedText.render(JsonParser.parseString(raw),emptyMap(),255,0)
        BossbarHudRenderer.appendLayoutRaw(player,layout,positioned)
    }
    fun remove(player: Player, layout: String) = BossbarHudRenderer.removeLayout(player,layout)
    fun popup(player: Player, id: String, values: Map<String,String>): CompletableFuture<*>? {
        val api = XerrAPI.get().popups()
        return if(id in api.ids()) api.show(player,id,values) else null
    }
}
