package ym.quest.config

import org.bukkit.configuration.file.YamlConfiguration
import java.nio.file.Files
import java.nio.file.Path

/** IO only. Move stock quantity phrases to their own line, without changing gameplay data. */
object ObjectiveLineBreakUpgrade {
    private val wording=mapOf(
        "first_resources" to ("原石生成器累计生产 " to "原石生成器累计生产<newline>"),
        "first_harvest" to ("拾取生成器产出的 " to "拾取生成器产出的<newline>"),
        "first_transfer" to ("生成器向收集器自动传输 " to "向收集器自动传输<newline>"),
        "first_extract" to ("从收集器中成功取出 " to "从收集器中成功取出<newline>")
    )
    fun migrate(root: Path) {
        val plan=ConfigMigrationPlan(root,"objective-quantity-lines-v1")
        if(plan.resume() || Files.exists(root.resolve(".migrations/objective-quantity-lines-v1/complete"))) return
        val changes=QuestTaskFiles.files(root.resolve("quests")).mapNotNull { file ->
            val before=Files.readString(file)
            val y=YamlConfiguration().apply { loadFromString(before) }
            val pair=wording[y.getString("id")] ?: return@mapNotNull null
            require(y.get("schema-version") in listOf(1,2)) { "$file:schema-version=${y.get("schema-version")} unsupported; objective wording preserved" }
            val descriptions=y.getMapList("objectives").mapNotNull { it["description"] as? String }
            val variants=if(y.getString("id")=="first_resources") listOf(pair,pair.first.replace("原石","圆石") to pair.second.replace("原石","圆石")) else listOf(pair)
            val replacements=descriptions.mapNotNull { value ->
                val match=variants.firstOrNull { value.startsWith("<#E2E8F0>${it.first}<#78FFD6>") && "<newline>" !in value }
                match?.let { value to value.replace(it.first,it.second) }
            }.toMap()
            if(replacements.isEmpty()) return@mapNotNull null
            val after=replacements.entries.fold(before) { value,(old,next) ->
                value.replace("description: '$old'","description: '$next'")
            }
            if(after==before) return@mapNotNull null
            val check=YamlConfiguration().apply { loadFromString(after) }
            val expected=y.getMapList("objectives").map { row -> row.mapValues { (key,value) ->
                if(key=="description" && value is String) replacements[value] ?: value else value
            } }
            require(check.getMapList("objectives")==expected && check.getMapList("rewards")==y.getMapList("rewards")) {
                "$file: objective-quantity-lines-v1 changed task data; original preserved"
            }
            ConfigMigrationPlan.Change(root.relativize(file).toString().replace('\\','/'),before,after)
        }
        plan.apply(changes)
    }
}
