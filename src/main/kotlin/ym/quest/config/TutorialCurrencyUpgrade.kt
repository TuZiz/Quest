package ym.quest.config

import org.bukkit.configuration.file.YamlConfiguration
import java.nio.file.Files
import java.nio.file.Path

/** Append missing copper/silver reward entries; completed claims and existing parcels are never replayed. */
object TutorialCurrencyUpgrade {
    fun migrate(root: Path, resource: (String) -> String) {
        val plan=ConfigMigrationPlan(root,"chapter-one-reward-choice-v1")
        if(plan.resume() || Files.exists(root.resolve(".migrations/chapter-one-reward-choice-v1/complete"))) return
        fun yaml(raw: String)=YamlConfiguration().apply {loadFromString(raw)}
        val chapterFiles=ChapterConfigUpgrade.files(root.resolve("chapters"))
        val chapter=chapterFiles.singleOrNull {yaml(Files.readString(it)).getString("id")=="sky_awakening"} ?: return
        val bundled=yaml(resource(ChapterConfigUpgrade.defaultFile))
        val side=setOf("更多的资源","自由的生产","家园的成长","初次试炼")
        val names=TutorialUpgrade.files+listOf("星海启航","放置生成器","能源启动","第一批资源")
        val defaults=names.associate { name ->
            val path="quests/第一章/"+(if(name in side) "支线/" else "")+"$name.yml"
            val y=yaml(resource(path));y.getString("id")!! to y
        }
        val tasks=yaml(Files.readString(chapter)).getMapList("tasks").map {it["quest"]}.toSet()
        val changes=mutableListOf<ConfigMigrationPlan.Change>()
        (QuestTaskFiles.files(root.resolve("quests"))+listOf(chapter)).forEach { path ->
            val before=Files.readString(path);val current=yaml(before);val id=current.getString("id")
            val template=if(path==chapter) bundled else defaults[id]?.takeIf {id in tasks} ?: return@forEach
            val hasItems=current.getMapList("rewards").any {!(it["item"] as String).startsWith("currency:")}
            val cleaned=if(hasItems) before.replace(Regex("(?m)^  - ['\"]?item['\"]?: ['\"]?currency:[a-z_]+['\"]?[^\\n]*\\n(?:^ {4}[^\\n]*(?:\\n|\\z))*"),"") else before
            val existing=yaml(cleaned).getMapList("rewards").map {it["item"]}.toSet()
            val additions=template.getMapList("rewards").filter {!hasItems && it["item"] in setOf("currency:copper_coin","currency:silver_coin") && it["item"] !in existing}
            if(additions.isEmpty()) {
                if(cleaned!=before) changes+=ConfigMigrationPlan.Change(root.relativize(path).toString().replace('\\','/'),before,cleaned)
                return@forEach
            }
            val block=additions.joinToString("") {
                val label=if(it["item"]=="currency:copper_coin") "铜币，每枚右键兑换1金币" else "银币，每枚右键兑换10金币"
                "  - item: '${it["item"]}'  # $label\n    amount: ${it["amount"]}\n"
            }
            val section=Regex("(?ms)^rewards:[^\\n]*\\n.*?(?=^[^\\s#][^\\n]*:|\\z)").find(before)
            require(!current.contains("rewards") || section!=null) { "$path:rewards expected block list; original preserved" }
            val after=if(section==null) before.trimEnd()+"\n\nrewards:\n"+block
                else before.replaceRange(section.range,section.value.trimEnd()+"\n"+block+"\n")
            yaml(after)
            changes+=ConfigMigrationPlan.Change(root.relativize(path).toString().replace('\\','/'),before,after)
        }
        plan.apply(changes)
    }
}
