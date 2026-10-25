package ym.quest.config

import org.bukkit.configuration.file.YamlConfiguration
import java.nio.file.Files
import java.nio.file.Path

/** IO only. Archive the retired side quest and chapter entry; leave all SQLite history intact. */
object RetiredHomeGrowthUpgrade {
    fun migrate(root: Path) {
        val name = "retire-home-growth-v1"
        val plan = ConfigMigrationPlan(root, name)
        if(plan.resume() || Files.exists(root.resolve(".migrations/$name/complete"))) return
        val changes = mutableListOf<ConfigMigrationPlan.Change>()
        fun yaml(raw: String) = YamlConfiguration().apply { loadFromString(raw) }
        ChapterConfigUpgrade.files(root.resolve("chapters")).forEach { file ->
            val before = Files.readString(file)
            if(yaml(before).getString("id") != "sky_awakening") return@forEach
            val after = before.replace(Regex("(?m)^  - quest: ['\"]?home_growth['\"]?[ \\t]*\\r?\\n(?:^ {4}[^\\n]*(?:\\n|\\z))*"), "")
            val chapter = ChapterFiles.parse(yaml(after), file.toString())
            require("home_growth" !in chapter.quests && chapter.taskRequirements.values.flatten().none { it == "home_growth" }) {
                "$file:tasks still references retired home_growth; original preserved"
            }
            if(after != before) changes += ConfigMigrationPlan.Change(root.relativize(file).toString().replace('\\', '/'), before, after)
        }
        QuestTaskFiles.files(root.resolve("quests")).forEach { file ->
            val before = Files.readString(file)
            if(yaml(before).getString("id") == "home_growth")
                changes += ConfigMigrationPlan.Change(root.relativize(file).toString().replace('\\', '/'), before, null)
        }
        plan.apply(changes)
    }
}
