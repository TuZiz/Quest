package ym.quest.hud

import com.hiusers.xerr.api.XerrAPI
import com.hiusers.xerr.api.container.BossbarHudRenderer
import org.bukkit.entity.Player
import java.util.concurrent.CompletableFuture

/** Version-bound Xerr 2.1 API. Never clears another plugin's HUD or popup group. Paper thread only. */
internal class XerrDisplay {
    fun ready(layout: String) = XerrAPI.get().layouts().exists(layout)
    fun update(player: Player, layout: String, values: Map<String,String>) =
        BossbarHudRenderer.appendLayout(player,layout,values)
    fun remove(player: Player, layout: String) = BossbarHudRenderer.removeLayout(player,layout)
    fun popup(player: Player, id: String, values: Map<String,String>): CompletableFuture<*>? {
        val api = XerrAPI.get().popups()
        return if(id in api.ids()) api.show(player,id,values) else null
    }
}
