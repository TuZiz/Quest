package ym.quest.config

import java.nio.file.Files
import java.nio.file.Path

/** Only replaces known legacy instructions, leaving custom fields, colors and comments intact. */
object RewardTextUpgrade {
    private val wording = listOf(
        "腾出空间后点击待领取。" to "腾出空间后会自动发放。",
        "腾出空位后点击 <#FFF9C4>[待领取] <#E0F7FA>即可提取。" to "腾出空位后会自动发放。",
        "任务奖励已存入全岛待领取列表。" to "任务奖励正在自动发放。",
        "任务奖励已成功存入全岛 <#FFD700>待领取列表<#E0F7FA>。" to "任务奖励正在自动发放。",
        "，使用 /quest 领取奖励。" to "，奖励会自动发放。",
        "！输入 <#FFF9C4>/quest <#E0F7FA>即可领取奖励。" to "！奖励会自动发放。",
        "奖励可随时前往任务中心领取。" to "奖励会自动发放。",
        "，奖励可随时领取。" to "，奖励会自动发放。",
        "！请前往章节页面领取丰厚奖励。" to "！章节奖励会自动发放。",
        "，请在章节页面领取奖励。" to "，章节奖励会自动发放。",
        "无需领取前置奖励即可继续推进" to "新的目标已解锁，继续前进吧",
        "无需领取前置奖励即可继续" to "新的目标已解锁，继续前进吧",
        "已完成 · 可领取" to "已完成"
    )
    fun migrate(file: Path) {
        val before = Files.readString(file)
        val after = wording.fold(before) { text, (old, next) -> text.replace(old,next) }
        if(after != before) ResourceUpgrade.replace(file,after)
    }
}
