package ym.quest.config

import org.bukkit.configuration.file.YamlConfiguration
import java.nio.file.Files
import java.nio.file.Path

/** RPG reward adjustment only; archived definitions and immutable historical parcels remain available. */
object TutorialRpgRewardsUpgrade {
    fun migrate(root: Path) {
        val plan=ConfigMigrationPlan(root,"chapter-one-rpg-rewards-v1")
        if(plan.resume() || Files.exists(root.resolve(".migrations/chapter-one-rpg-rewards-v1/complete"))) return
        fun yaml(raw: String)=YamlConfiguration().apply {loadFromString(raw)}
        val chapter=ChapterConfigUpgrade.files(root.resolve("chapters")).singleOrNull {yaml(Files.readString(it)).getString("id")=="sky_awakening"} ?: return
        val ids=yaml(Files.readString(chapter)).getMapList("tasks").map {it["quest"]}.toSet()
        val changes=mutableListOf<ConfigMigrationPlan.Change>()
        (QuestTaskFiles.files(root.resolve("quests"))+listOf(chapter)).forEach { path ->
            val before=Files.readString(path);val y=yaml(before);val id=y.getString("id")
            if(path!=chapter && id !in ids) return@forEach
            if(y.getMapList("rewards").none {(it["item"] as String).startsWith("minecraft:")}) return@forEach
            var after=before.replace(Regex("(?m)^  - ['\"]?item['\"]?: ['\"]?minecraft:[a-z0-9_/]+['\"]?[^\\n]*\\n(?:^ {4}[^\\n]*(?:\\n|\\z))*"),"")
            val rewards=yaml(after).getMapList("rewards")
            if(rewards.isEmpty()) {
                val block=if(path==chapter) "  - item: 'genlite:mechanical_parts'  # 铁片\n    amount: 4\n  - item: 'genlite:copper_coil'  # 铜丝\n    amount: 4\n"
                    else "  - item: 'genlite:fuel'  # 普通燃料\n    amount: 8\n"
                val section=Regex("(?ms)^rewards:[^\\n]*\\n.*?(?=^[^\\s#][^\\n]*:|\\z)").find(after)
                    ?: error("$path:rewards expected block list; original preserved")
                after=after.replaceRange(section.range,"rewards:\n$block\n")
            }
            require(yaml(after).getMapList("rewards").none {(it["item"] as String).startsWith("minecraft:")}) { "$path: unsupported vanilla reward layout; original preserved" }
            changes+=ConfigMigrationPlan.Change(root.relativize(path).toString().replace('\\','/'),before,after)
        }
        plan.apply(changes)
    }
}
