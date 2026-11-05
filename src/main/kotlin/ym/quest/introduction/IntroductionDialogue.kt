package ym.quest.introduction

import org.bukkit.entity.Player
import java.util.UUID

interface IntroductionDialogue : AutoCloseable {
    fun start(player: Player, finished: (Boolean) -> Unit): Boolean
    fun complete(playerId: UUID, token: UUID): Boolean
    fun cancel(player: Player)
}
