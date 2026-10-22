package ym.quest.introduction

import org.bukkit.entity.Player

interface IntroductionDialogue : AutoCloseable {
    fun start(player: Player, lines: List<String>, finished: (Boolean) -> Unit): Boolean
    fun cancel(player: Player)
}
