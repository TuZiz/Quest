package ym.quest.config

import org.bukkit.configuration.file.YamlConfiguration
import java.nio.file.Files
import java.nio.file.Path

/** Add one placement stage and move the existing fuel reward; durable journal preserves user files. IO only. */
object GeneratorPlacementUpgrade {
    const val taskFile = "quests/放置生成器.yml"
    fun migrate(root: Path, bundled: String) {
        val plan = ConfigMigrationPlan(root,"generator-placement-v1")
        plan.resume()
        val chapters = ChapterConfigUpgrade.files(root.resolve("chapters")).filter {
            yaml(Files.readString(it)).getString("id") == "sky_awakening"
        }
        if(chapters.isEmpty()) return
        require(chapters.size == 1) { "chapters/: duplicate sky_awakening; placement migration cancelled" }
        val chapter = chapters.single(); val raw = Files.readString(chapter)
        val parsed = ChapterFiles.parse(yaml(raw),chapter.toString())
        if("sky_start" !in parsed.quests || "first_fuel" !in parsed.quests) return
        val changes = mutableListOf<ConfigMigrationPlan.Change>()
        val taskFiles=QuestTaskFiles.files(root.resolve("quests"))
        val starter = taskFiles.single { yaml(Files.readString(it)).getString("id") == "sky_start" }
        var task = bundled
        if("place_generator" !in parsed.quests) {
            require(!Files.exists(root.resolve(taskFile))) { "$taskFile: placement migration conflict; originals preserved" }
            val lines = raw.lines().toMutableList()
            val entries = lines.indices.filter { lines[it].matches(Regex("  - quest:.*")) }
            require(entries.size == parsed.quests.size) { "$chapter:tasks expected two-space block list; original preserved" }
            val at = entries[parsed.quests.indexOf("first_fuel")]
            val end = entries.firstOrNull { it > at } ?: (at+1 until lines.size).firstOrNull {
                lines[it].matches(Regex("^[^ #].*:.*"))
            } ?: lines.size
            val section = lines.subList(at,end).toMutableList()
            val requires = section.indexOfFirst { it.startsWith("    requires:") }
            if(requires >= 0) section.add(requires+1,"      - place_generator")
            else section.addAll(1,listOf("    requires:","      - place_generator"))
            lines.subList(at,end).clear(); lines.addAll(at,section)
            lines.addAll(at,listOf("  - quest: place_generator","    requires:","      - sky_start"))
            val next = lines.joinToString("\n")
            ChapterFiles.parse(yaml(next),chapter.toString())
            changes += ConfigMigrationPlan.Change(relative(root,chapter),raw,next)

            val old = Files.readString(starter)
            val fuel = yaml(old).getMapList("rewards").filter { it["item"] == "genlite:fuel" }
            require(fuel.size <= 1) { "$starter:rewards duplicate fuel entries; original preserved" }
            if(fuel.isNotEmpty()) {
                // Transfer the whole reward block, including custom amount/fields/comments.
                val reward = Regex("(?m)^  - item: ['\"]?genlite:fuel['\"]?[ \\t]*(?:#.*)?\\r?\\n(?:(?!^  - |^[^ #\\r\\n]).*(?:\\r?\\n|$))*").find(old)
                    ?: error("$starter:rewards expected block fuel entry; original preserved")
                val oldBlock = reward.value.trimEnd()
                require(!fuel.single().containsKey("unless-rewarded-by")) { "$starter:rewards fuel already has unless-rewarded-by; original preserved" }
                task = bundled.substringBefore("rewards:")+"rewards:\n"+oldBlock+"\n    unless-rewarded-by: sky_start\n"
                task = task.replace("16份燃料","${fuel.single()["amount"]}份燃料")
                val comments = reward.value.lines().filter { it.trimStart().startsWith("#") }.joinToString("\n")
                changes += ConfigMigrationPlan.Change(relative(root,starter),old,old.replaceRange(reward.range,if(comments.isBlank()) "" else "$comments\n"))
            }
        } else if(taskFiles.any { yaml(Files.readString(it)).getString("id")=="place_generator" }) return
        changes += ConfigMigrationPlan.Change(taskFile,null,task)
        try { plan.apply(changes) }
        catch(e: Exception) { throw IllegalStateException("$taskFile: placement migration v1 failed; originals in .migrations/generator-placement-v1",e) }
    }
    private fun yaml(raw: String) = YamlConfiguration().apply { loadFromString(raw) }
    private fun relative(root: Path,file: Path) = root.relativize(file).toString().replace('\\','/')
}
