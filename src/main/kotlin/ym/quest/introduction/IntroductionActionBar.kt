package ym.quest.introduction

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import ym.quest.text.TextRenderer
import java.util.UUID

/** Owned by IntroductionRuntime and called only on the Paper player ownership thread. */
class IntroductionActionBar(private val text: TextRenderer) {
    private val shown = mutableMapOf<UUID, Player>()

    fun refresh(player: Player, visible: Boolean) {
        if(visible) {
            player.sendActionBar(text.format("introduction.actionbar"))
            shown[player.uniqueId] = player
        } else clear(player)
    }

    fun clear(player: Player) {
        if(shown.remove(player.uniqueId) != null && player.isOnline) player.sendActionBar(Component.empty())
    }

    fun close() { shown.values.toList().forEach(::clear); shown.clear() }
}
