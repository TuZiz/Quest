package ym.quest.config

import org.bukkit.configuration.file.YamlConfiguration
import java.nio.file.Files
import java.nio.file.Path

/** IO owner only; journal originals and preserve comments, custom quantities and reward data. */
object TrialCombatUpgrade {
    private const val migration="trial-hostile-v1"
    fun migrate(root: Path) {
        val plan=ConfigMigrationPlan(root,migration)
        if(plan.resume() || Files.exists(root.resolve(".migrations/$migration/complete"))) return
        val replacements=mapOf(
            "<gradient:#78FFD6:#A8FF78>✦ 初次试炼</gradient>" to "<gradient:#78FFD6:#A8FF78>前往石址击杀三只生物</gradient>",
            "<#94A3B8>星海之外，并不只有宁静的家园。" to "<#94A3B8>前往石址击杀三只生物",
            "<#94A3B8>探索未知之前，请先握紧手中的剑。" to "<#94A3B8>任意敌对生物或 MythicMobs 怪物均计入进度。",
            "<#E2E8F0>亲自击杀 <#78FFD6>3</#78FFD6> 只僵尸" to "<#E2E8F0>前往石址击杀 <#78FFD6>3</#78FFD6> 只生物"
        )
        val changes=QuestTaskFiles.files(root.resolve("quests")).mapNotNull { file ->
            val before=Files.readString(file)
            val old=YamlConfiguration().apply { loadFromString(before) }
            if(old.getString("id")!="first_trial") return@mapNotNull null
            require(old.get("schema-version")==2) { "$file:schema-version=${old.get("schema-version")} expected 2; $migration original preserved" }
            val rows=old.getMapList("objectives")
            if(rows.none { it["id"]=="experience" && it["type"]=="combat.kill" && it["resource"]=="minecraft:zombie" }) return@mapNotNull null
            var after=replacements.entries.fold(before) { text,(from,to) -> text.replace("'$from'","'$to'") }
            after=after.replace(Regex("(?m)^(\\s+resource: *)'minecraft:zombie'"),"$1'hostile'")
            old.set("name",replacements[old.getString("name")] ?: old.getString("name"))
            old.set("description",old.getStringList("description").map { replacements[it] ?: it })
            old.set("objectives",rows.map { row -> row.mapValues { (key,value) ->
                when {
                    key=="resource" && row["id"]=="experience" && row["type"]=="combat.kill" && value=="minecraft:zombie" -> "hostile"
                    key=="description" && value is String -> replacements[value] ?: value
                    else -> value
                }
            } })
            val check=YamlConfiguration().apply { loadFromString(after) }
            require(check.saveToString()==old.saveToString()) { "$file: $migration unsupported YAML shape; original preserved" }
            ConfigMigrationPlan.Change(root.relativize(file).toString().replace('\\','/'),before,after)
        }
        plan.apply(changes)
    }
}
