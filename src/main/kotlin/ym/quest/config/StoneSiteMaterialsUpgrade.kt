package ym.quest.config

import org.bukkit.configuration.file.YamlConfiguration
import java.nio.file.Files
import java.nio.file.Path

/** Add the optional follow-up to existing first chapters, keeping their comments and rewards. */
internal object StoneSiteMaterialsUpgrade {
    const val resourcePath = "quests/第一章/支线/石址寻材.yml"
    const val taskId = "stone_site_materials"
    private const val migration = "stone-site-materials-v1"

    fun migrate(root: Path, resource: () -> String) {
        val plan = ConfigMigrationPlan(root, migration)
        if(plan.resume() || Files.exists(root.resolve(".migrations/$migration/complete"))) return
        fun yaml(raw: String) = YamlConfiguration().apply { loadFromString(raw) }
        val chapter = ChapterConfigUpgrade.files(root.resolve("chapters")).singleOrNull {
            yaml(Files.readString(it)).getString("id") == "sky_awakening"
        } ?: return
        val before = Files.readString(chapter)
        val parsed = ChapterFiles.parse(yaml(before), chapter.toString())
        if("first_trial" !in parsed.quests) return
        val changes = mutableListOf<ConfigMigrationPlan.Change>()
        val taskExists = QuestTaskFiles.files(root.resolve("quests")).any { yaml(Files.readString(it)).getString("id") == taskId }
        if(!taskExists) {
            require(!Files.exists(root.resolve(resourcePath))) { "$resourcePath already occupied; original preserved" }
            changes += ConfigMigrationPlan.Change(resourcePath, null, resource())
        }
        if(taskId !in parsed.quests) {
            val tasks = Regex("(?ms)^tasks:[^\\n]*\\n.*?(?=^[^\\s#][^\\n]*:|\\z)").find(before)
                ?: error("$chapter:tasks missing; original preserved")
            val lastEntry = Regex("(?m)^ +(?:- |requires:|optional:)[^\\r\\n]*").findAll(tasks.value).last()
            val offset = tasks.range.first + lastEntry.range.last + 1
            val newline = if("\r\n" in before) "\r\n" else "\n"
            val entry = listOf("", "  - quest: '$taskId'", "    requires:", "      - 'first_trial'", "    optional: true").joinToString(newline)
            val after = before.substring(0,offset) + entry + before.substring(offset)
            val upgraded = ChapterFiles.parse(yaml(after), chapter.toString())
            require(upgraded == parsed.copy(quests = parsed.quests + taskId,
                taskRequirements = parsed.taskRequirements + (taskId to listOf("first_trial")),
                optionalQuests = parsed.optionalQuests + taskId)) { "$chapter:tasks migration changed unrelated settings; original preserved" }
            changes += ConfigMigrationPlan.Change(root.relativize(chapter).toString().replace('\\','/'), before, after)
        }
        plan.apply(changes)
    }
}
