package ym.quest.config

import java.nio.file.Files
import java.nio.file.Path

/** Comment-only correction; never changes reward amounts, item IDs or existing claims. */
internal object SilverCoinCommentUpgrade {
    fun migrate(root: Path) {
        val name="silver-coin-comment-v1"
        val plan=ConfigMigrationPlan(root,name)
        if(plan.resume() || Files.exists(root.resolve(".migrations/$name/complete"))) return
        val files=QuestTaskFiles.files(root.resolve("quests"))+ChapterConfigUpgrade.files(root.resolve("chapters"))
        val changes=files.mapNotNull { path ->
            val before=Files.readString(path)
            val after=Regex("(?m)^(\\s*- item: ['\"]?currency:silver_coin['\"]?[^#\\r\\n]*#)[^\\r\\n]*").replace(before) { m ->
                m.value.replace("银币，每枚右键兑换10金币","银币，每枚右键兑换5金币")
            }
            if(before==after) null else ConfigMigrationPlan.Change(root.relativize(path).toString().replace('\\','/'),before,after)
        }
        plan.apply(changes)
    }
}
