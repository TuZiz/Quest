package ym.quest.config

import org.bukkit.configuration.file.YamlConfiguration
import java.nio.file.Files
import java.nio.file.Path

/** Archive retired definitions through the migration journal; never delete SQLite progress or claims. */
object TutorialLevelTwoUpgrade {
    private val retired=setOf("upgrade_three","upgrade_four","upgrade_five","island_scale")
    fun migrate(root: Path) {
        val plan=ConfigMigrationPlan(root,"chapter-one-level-two-v1")
        if(plan.resume() || Files.exists(root.resolve(".migrations/chapter-one-level-two-v1/complete"))) return
        val changes=mutableListOf<ConfigMigrationPlan.Change>()
        fun yaml(raw: String)=YamlConfiguration().apply {loadFromString(raw)}
        ChapterConfigUpgrade.files(root.resolve("chapters")).forEach { path ->
            val before=Files.readString(path);val y=yaml(before)
            if(y.getString("id")!="sky_awakening") return@forEach
            val next=before.replace(Regex("(?m)^  - quest: ['\"]?(?:${retired.joinToString("|")})['\"]?[ \\t]*\\r?\\n(?:^ {4}[^\\n]*\\n)*"),"")
            val chapter=ChapterFiles.parse(yaml(next),path.toString())
            require(chapter.taskRequirements.values.flatten().none {it in retired}) { "$path: requires references a retired Lv.3–5 task; original preserved" }
            if(next!=before) changes+=ConfigMigrationPlan.Change(root.relativize(path).toString().replace('\\','/'),before,next)
        }
        QuestTaskFiles.files(root.resolve("quests")).forEach { path ->
            val before=Files.readString(path);val y=yaml(before)
            val relative=root.relativize(path).toString().replace('\\','/')
            if(y.getString("id") in retired) changes+=ConfigMigrationPlan.Change(relative,before,null)
            if(y.getString("id")=="first_extract") {
                val rewards=y.getMapList("rewards")
                if(rewards.size==1 && rewards.single()["item"]=="genlite:basic_upgrade_unit" && rewards.single()["amount"]==6) {
                    val next=before.replace(Regex("(?ms)^rewards:[^\\n]*\\n.*?(?=^[^\\s#][^\\n]*:|\\z)"),
                        "rewards:\n  - item: 'genlite:fuel'  # 燃料\n    amount: 8\n  - item: 'minecraft:bread'  # 面包\n    amount: 4\n")
                    if(next!=before) changes+=ConfigMigrationPlan.Change(relative,before,next)
                }
            }
        }
        plan.apply(changes)
    }
}
