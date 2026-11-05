package ym.quest.config

import org.bukkit.configuration.file.YamlConfiguration
import java.nio.file.Files
import java.nio.file.Path

/** IO only. Add entry instructions to the stock membership goal without changing amounts/rewards. */
object IslandEntryHintUpgrade {
    private const val before="<#E2E8F0><#78FFD6>创建或加入</#78FFD6> 一座空岛"
    private const val after="<#E2E8F0><#78FFD6>创建或加入</#78FFD6> 一座空岛<newline><#94A3B8>右键 <#67E8F9>领航员 NPC<newline><#94A3B8>或打开 <#FDE68A>服务器菜单"
    fun migrate(root: Path) {
        val plan=ConfigMigrationPlan(root,"island-entry-hint-v1")
        if(plan.resume() || Files.exists(root.resolve(".migrations/island-entry-hint-v1/complete"))) return
        val changes=QuestTaskFiles.files(root.resolve("quests")).mapNotNull { file ->
            val old=Files.readString(file)
            val y=YamlConfiguration().apply { loadFromString(old) }
            if(y.getString("id")!="sky_start") return@mapNotNull null
            require(y.get("schema-version")==2) { "$file:schema-version=${y.get("schema-version")} expected 2; original preserved" }
            if(y.getMapList("objectives").none { it["type"]=="island.member" && it["description"]==before }) return@mapNotNull null
            val next=old.replace("description: '$before'","description: '$after'")
            if(next==old) return@mapNotNull null
            val check=YamlConfiguration().apply { loadFromString(next) }
            require(check.getMapList("rewards")==y.getMapList("rewards")) { "$file: hint migration changed rewards; original preserved" }
            ConfigMigrationPlan.Change(root.relativize(file).toString().replace('\\','/'),old,next)
        }
        plan.apply(changes)
    }
}
