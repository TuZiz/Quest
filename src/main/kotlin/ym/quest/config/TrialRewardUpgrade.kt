package ym.quest.config

import org.bukkit.configuration.file.YamlConfiguration
import java.nio.file.Files
import java.nio.file.Path

/** IO only. Replace the stock trial reward, retaining historical claims and immutable parcels. */
object TrialRewardUpgrade {
    fun migrate(root: Path) {
        val name="trial-sword-reward-v1"
        val plan=ConfigMigrationPlan(root,name)
        if(plan.resume() || Files.exists(root.resolve(".migrations/$name/complete"))) return
        val changes=QuestTaskFiles.files(root.resolve("quests")).mapNotNull { file ->
            val before=Files.readString(file)
            val y=YamlConfiguration().apply { loadFromString(before) }
            if(y.getString("id")!="first_trial") return@mapNotNull null
            require(y.getInt("schema-version")==2) { "$file:schema-version=${y.get("schema-version")} expected 2; original preserved" }
            if(y.getMapList("rewards")!=listOf(mapOf("item" to "genlite:fuel","amount" to 8))) return@mapNotNull null
            val after=before.replace(Regex("(?m)^(  - item: )'genlite:fuel'([^\\r\\n]*)(\\r?\\n    amount: )8(?=\\s|$)")) {
                it.groupValues[1]+"'itemhub:星芽:短剑'"+it.groupValues[2].replace("普通燃料","ItemHub 星芽短剑")+it.groupValues[3]+"1"
            }
            y.set("rewards",listOf(mapOf("item" to "itemhub:星芽:短剑","amount" to 1)))
            require(YamlConfiguration().apply { loadFromString(after) }.saveToString()==y.saveToString()) { "$file:rewards unsupported layout; original preserved" }
            ConfigMigrationPlan.Change(root.relativize(file).toString().replace('\\','/'),before,after)
        }
        plan.apply(changes)
    }
}
