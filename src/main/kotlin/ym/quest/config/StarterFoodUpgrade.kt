package ym.quest.config

import org.bukkit.configuration.file.YamlConfiguration
import java.nio.file.Files
import java.nio.file.Path

/** IO executor only. Add food without replaying claims or changing existing reward entries. */
object StarterFoodUpgrade {
    fun migrate(root: Path) {
        val name="starter-food-v1"
        val plan=ConfigMigrationPlan(root,name)
        if(plan.resume() || Files.exists(root.resolve(".migrations/$name/complete"))) return
        val additions=mapOf("sky_start" to "minecraft:bread","first_trial" to "minecraft:baked_potato")
        val changes=QuestTaskFiles.files(root.resolve("quests")).mapNotNull { file ->
            val before=Files.readString(file)
            val y=YamlConfiguration().apply { loadFromString(before) }
            val item=additions[y.getString("id")] ?: return@mapNotNull null
            require(y.getInt("schema-version")==2) { "$file:schema-version=${y.get("schema-version")} expected 2; original preserved" }
            val rewards=y.getMapList("rewards")
            if(rewards.any { it["item"]==item }) return@mapNotNull null
            val section=Regex("(?ms)^rewards:[^\\n]*\\n.*?(?=^[^\\s#][^\\n]*:|\\z)").find(before)
            require(section!=null) { "$file:rewards expected block list; original preserved" }
            val newline=if("\r\n" in before) "\r\n" else "\n"
            val block="  - item: '$item'  # 新手食物补给${newline}    amount: 8$newline"
            val after=before.replaceRange(section.range,section.value.trimEnd()+newline+block+newline)
            val parsed=YamlConfiguration().apply { loadFromString(after) }
            require(parsed.getMapList("rewards")==rewards+listOf(mapOf("item" to item,"amount" to 8))) {
                "$file:rewards unsupported layout; original preserved"
            }
            parsed.set("rewards",null);y.set("rewards",null)
            fun values(config: YamlConfiguration)=config.getValues(true).filterValues { it !is org.bukkit.configuration.ConfigurationSection }
            require(values(parsed)==values(y)) { "$file:food migration changed unrelated fields; original preserved" }
            ConfigMigrationPlan.Change(root.relativize(file).toString().replace('\\','/'),before,after)
        }
        plan.apply(changes)
    }
}
