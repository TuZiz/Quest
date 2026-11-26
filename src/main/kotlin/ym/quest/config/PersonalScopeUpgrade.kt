package ym.quest.config

import org.bukkit.configuration.file.YamlConfiguration
import java.nio.file.Files
import java.nio.file.Path

/** One transactional text migration; preserve comments and unrelated custom settings. */
internal object PersonalScopeUpgrade {
    fun migrate(root: Path) {
        val name="personal-combat-scope-v1"
        val plan=ConfigMigrationPlan(root,name)
        if(plan.resume() || Files.exists(root.resolve(".migrations/$name/complete"))) return
        val changes=mutableListOf<ConfigMigrationPlan.Change>()
        QuestTaskFiles.files(root.resolve("quests")).forEach { path ->
            val before=Files.readString(path)
            val y=YamlConfiguration().apply { loadFromString(before) }
            if(y.getString("id") !in setOf("first_trial","stone_site_materials")) return@forEach
            require(y.getInt("schema-version")==2 && y.getString("scope") in setOf("island","personal")) { "$path:scope=${y.get("scope")}; schema-version=${y.get("schema-version")} unsupported; original preserved" }
            val after=Regex("(?m)^scope:[^\r\n]*").replace(before,"scope: 'personal'")
            require(YamlConfiguration().apply { loadFromString(after) }.getString("scope")=="personal") { "$path:scope expected block scalar" }
            if(before!=after) changes+=ConfigMigrationPlan.Change(root.relativize(path).toString().replace('\\','/'),before,after)
        }
        ChapterConfigUpgrade.files(root.resolve("chapters")).forEach { path ->
            val before=Files.readString(path)
            // Shared logistics follows extraction; personal loot no longer gates island business.
            val block=Regex("(?ms)^  - quest: ['\"]?logistics_supply['\"]?[^\r\n]*\r?\n.*?(?=^  - quest:|^[^\\s#]|\\z)")
            val after=block.replace(before) { m -> Regex("(?m)^      - ['\"]?stone_site_materials['\"]?[ \t]*\r?\n").replace(m.value,"") }
            if(before!=after) {
                ChapterFiles.parse(YamlConfiguration().apply { loadFromString(after) },path.toString())
                changes+=ConfigMigrationPlan.Change(root.relativize(path).toString().replace('\\','/'),before,after)
            }
        }
        plan.apply(changes)
    }
}
