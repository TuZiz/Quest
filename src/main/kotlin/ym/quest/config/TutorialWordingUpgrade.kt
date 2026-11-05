package ym.quest.config

import org.bukkit.configuration.file.YamlConfiguration
import java.nio.file.Files
import java.nio.file.Path

/** IO executor only. Correct stock teaching phrases without rewriting customized task data. */
object TutorialWordingUpgrade {
    private const val migration="tutorial-wording-v2"
    private val stockStoneTexts=setOf(
        "<#94A3B8>将背包中的原石生成器安放在你的领地之上。",
        "<#E2E8F0>在空岛放置 <#78FFD6>原石生成器</#78FFD6>",
        "<#94A3B8>打开原石生成器面板，向燃料仓内注入初始动力。",
        "<#94A3B8>升级原石生成器，迈出产能跃升的第一步。",
        " <#7F8C8D>❖ <#90A4AE>点亮第一台生成器，让空岛文明从第一块原石开始。"
    )
    fun migrate(root: Path) {
        val plan=ConfigMigrationPlan(root,migration)
        if(plan.resume() || Files.exists(root.resolve(".migrations/$migration/complete"))) return
        val files=QuestTaskFiles.files(root.resolve("quests"))+ChapterConfigUpgrade.files(root.resolve("chapters"))
        val changes=files.mapNotNull { file ->
            val before=Files.readString(file)
            val old=YamlConfiguration().apply { loadFromString(before) }
            val id=old.getString("id")
            if(id !in setOf("free_production","first_resources","first_harvest","sky_start","place_generator","first_fuel","upgrade_two","sky_awakening")) return@mapNotNull null
            require(old.get("schema-version")==2) { "$file:schema-version=${old.get("schema-version")} expected 2; $migration original preserved" }
            val texts=old.getStringList("description")+old.getStringList("story")+old.getMapList("objectives").mapNotNull { it["description"] as? String }
            val replacements=texts.mapNotNull { value ->
                val next=when(id) {
                    "free_production" -> if(value=="<#E2E8F0>在生成器面板中切换一次 <#78FFD6>产出模式</#78FFD6>")
                        "<#E2E8F0>打开生成器面板<newline>切换一次 <#78FFD6>产出模式</#78FFD6>" else value
                    "sky_start" -> if(value.startsWith("<#E2E8F0><#78FFD6>创建或加入</#78FFD6> 一座空岛<newline>"))
                        value.replace("右键 <#67E8F9>领航员 NPC","右键 <#67E8F9>引航员 NPC") else value
                    "first_harvest" -> if(value=="<#64748B>▪ 主线目标（普通挖掘与玩家丢弃的原石不计入进度）" ||
                        value.matches(Regex("<#E2E8F0>拾取生成器产出的(?:<newline>| )<#78FFD6>[0-9]+</#78FFD6> 个原石"))) value.replace("原石","圆石") else value
                    "upgrade_two" -> when {
                        value.matches(Regex("<#E2E8F0>将(?:原石|圆石)生成器升至 <#78FFD6>Lv\\.[0-9]+</#78FFD6>")) ->
                            value.replace("原石","圆石").replace("升至 <#78FFD6>","升至<newline><#78FFD6>")
                        else -> if(value in stockStoneTexts) value.replace("原石","圆石") else value
                    }
                    else -> if(value in stockStoneTexts || value.matches(Regex("<#E2E8F0>原石生成器累计生产(?:<newline>| )<#78FFD6>[0-9]+</#78FFD6> 个原石")) ||
                        value.matches(Regex("<#E2E8F0>将原石生成器升至 <#78FFD6>Lv\\.[0-9]+</#78FFD6>"))) value.replace("原石","圆石") else value
                }
                if(next==value) null else value to next
            }.toMap()
            val after=replacements.entries.fold(before) { result,(from,to) -> result.replace("'$from'","'$to'") }
            if(after==before) return@mapNotNull null
            val check=YamlConfiguration().apply { loadFromString(after) }
            for(key in listOf("description","story")) if(old.contains(key)) old.set(key,old.getStringList(key).map { replacements[it] ?: it })
            if(old.contains("objectives")) old.set("objectives",old.getMapList("objectives").map { row -> row.mapValues { (key,value) ->
                if(key=="description" && value is String) replacements[value] ?: value else value
            } })
            require(check.saveToString()==old.saveToString()) { "$file: $migration changed non-text data; original preserved" }
            ConfigMigrationPlan.Change(root.relativize(file).toString().replace('\\','/'),before,after)
        }
        plan.apply(changes)
    }
}
