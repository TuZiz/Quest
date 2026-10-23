package ym.quest.config

import org.bukkit.configuration.file.YamlConfiguration
import java.nio.file.Files
import java.nio.file.Path

/** Additive journaled migration: existing task amounts/rewards/unknown fields/comments remain untouched. */
object TutorialUpgrade {
    val files=listOf("收获的喜悦","机械革新","收集的力量","机械的联系","自动化初体验","资源的归宿",
        "产能突破","持续进化","机械巅峰","初具规模","更多的资源","自由的生产","家园的成长","初次试炼")
    fun migrate(root: Path, resource: (String) -> String) {
        val plan=ConfigMigrationPlan(root,"chapter-one-tutorial-v1")
        if(plan.resume() || Files.exists(root.resolve(".migrations/chapter-one-tutorial-v1/complete"))) return
        val changes=mutableListOf<ConfigMigrationPlan.Change>()
        val paths=QuestTaskFiles.files(root.resolve("quests"))
        val existing=paths.associate { p -> YamlConfiguration().apply { loadFromString(Files.readString(p)) }.getString("id") to p }
        existing["place_generator"]?.let { file ->
            val raw=Files.readString(file)
            val parsed=YamlConfiguration().apply { loadFromString(raw) }
            if(parsed.getMapList("objectives").singleOrNull()?.let { it["type"]=="genlite.placed" && !it.containsKey("resource") }==true) {
                val next=raw.replace(Regex("(?m)^(\\s+)type: ['\"]?genlite\\.placed['\"]?\\s*$")) {
                    it.value.trimEnd()+"\n"+it.groupValues[1]+"resource: 'cobblestone_generator'"
                }
                changes+=ConfigMigrationPlan.Change(root.relativize(file).toString().replace('\\','/'),raw,next)
            }
        }
        files.forEach { name ->
            val path="quests/第一章/$name.yml";val raw=resource(path)
            val id=YamlConfiguration().apply { loadFromString(raw) }.getString("id")!!
            if(id !in existing) {
                require(!Files.exists(root.resolve(path))) { "$path: tutorial task path occupied; original preserved" }
                changes+=ConfigMigrationPlan.Change(path,null,raw)
            }
        }
        val chapter=ChapterConfigUpgrade.files(root.resolve("chapters")).singleOrNull { p ->
            YamlConfiguration().apply { loadFromString(Files.readString(p)) }.getString("id")=="sky_awakening"
        } ?: error("chapters/: tutorial requires exactly one chapter id sky_awakening")
        val before=Files.readString(chapter)
        val old=YamlConfiguration().apply { loadFromString(before) }
        val template=YamlConfiguration().apply { loadFromString(resource(ChapterConfigUpgrade.defaultFile)) }
        val oldIds=old.getMapList("tasks").map { it["quest"] }.toSet()
        val additions=template.getMapList("tasks").filter { it["quest"] !in oldIds }
        val lines=buildString { additions.forEach { task ->
            append("  - quest: '").append(task["quest"]).append("'\n")
            (task["requires"] as? List<*>)?.takeIf { it.isNotEmpty() }?.let { required ->
                append("    requires:\n");required.forEach { append("      - '").append(it).append("'\n") }
            }
            if(task["optional"]==true) append("    optional: true\n")
        } }
        val section=Regex("(?ms)^tasks:[^\\n]*\\n.*?(?=^[^\\s#][^\\n]*:|\\z)").find(before)
            ?: error("$chapter:tasks missing block mapping; original preserved")
        var after=before.replaceRange(section.range,section.value.trimEnd()+"\n"+lines+"\n")
        after=after.replace("第一章 · 初入星辰","第一章 · 星海初醒")
        ChapterFiles.parse(YamlConfiguration().apply { loadFromString(after) },chapter.toString())
        if(after!=before) changes+=ConfigMigrationPlan.Change(root.relativize(chapter).toString().replace('\\','/'),before,after)
        val combat=root.resolve("教学.yml")
        if(!Files.exists(combat)) changes+=ConfigMigrationPlan.Change("教学.yml",null,resource("教学.yml"))
        plan.apply(changes)
    }
}
