package ym.quest.config

import org.bukkit.configuration.file.YamlConfiguration
import ym.quest.domain.ChapterGraph
import ym.quest.domain.QuestDefinition
import java.nio.file.Files
import java.nio.file.Path

/** Converts relationships only. Does not read or write player databases. IO executor only. */
object ChapterConfigUpgrade {
    const val defaultFile = "chapters/第一章·星海初醒.yml"
    fun migrate(root: Path, bundledChapter: String) {
        val plan = ConfigMigrationPlan(root)
        plan.resume()
        val taskFiles = files(root.resolve("quests")).associate { root.relativize(it).toString().replace('\\','/') to Files.readString(it) }
        val tasks = taskFiles.mapValues { (path,text) -> yaml(path,text).also {
            require(it.get("schema-version") in listOf(1,2)) { "$path:schema-version=${it.get("schema-version")} unsupported; original preserved" }
            require(it.get("schema-version") != 2 || !it.contains("prerequisites")) { "$path: schema 2 forbids prerequisites; move them to chapters/*.yml tasks.requires" }
        } }
        val oldPath = "chapters/章节.yml"
        val old = root.resolve(oldPath).takeIf { Files.exists(it) }?.let { Files.readString(it) }
        val chapterTexts = files(root.resolve("chapters")).filter { root.relativize(it).toString().replace('\\','/') != oldPath }
            .associate { root.relativize(it).toString().replace('\\','/') to Files.readString(it) }.toMutableMap()
        val oldChapters = if(old == null) emptyList() else splitLegacy(oldPath,old)
        require(oldChapters.isEmpty() || chapterTexts.isEmpty()) { "$oldPath: schema 1 -> 2 conflicts with existing chapter files; all originals preserved" }
        val legacy = tasks.values.any { it.get("schema-version") == 1 } || old != null
        if(!legacy) return
        val ids = tasks.map { (path,y) ->
            val id = y.getString("id")
            require(id?.matches(Regex("[a-z0-9_-]+")) == true) { "$path:id=$id invalid" }
            id
        }
        require(ids.distinct().size == ids.size) { "quests/: duplicate task ID; migration cancelled" }
        val oldDeps = tasks.values.associate { y ->
            require(!y.contains("prerequisites") || y.getList("prerequisites")?.all { it is String } == true) { "quests/${y.getString("id")}:prerequisites expected string list" }
            require(y.getStringList("prerequisites").distinct().size == y.getStringList("prerequisites").size) { "quests/${y.getString("id")}:prerequisites duplicate" }
            y.getString("id")!! to y.getStringList("prerequisites")
        }
        if(oldChapters.isNotEmpty()) oldChapters.forEach { raw ->
            val y = yaml(oldPath,raw)
            require(y.getList("quests")?.all { it is String } == true) { "$oldPath:${y.get("id")}.quests invalid" }
            require(!y.contains("sequential") || y.get("sequential") is Boolean) { "$oldPath:${y.get("id")}.sequential invalid" }
            val quests = y.getStringList("quests")
            val deps = quests.mapIndexed { i,id -> id to (oldDeps[id].orEmpty() + if(y.getBoolean("sequential",true) && i > 0) listOf(quests[i-1]) else emptyList()).distinct() }.toMap()
            val filename = y.getString("name").orEmpty().replace(Regex("<[^>]*>"),"").replace(Regex("[\\s<>:\"/\\\\|?*]"),"").trimEnd('.')
            require(filename.isNotBlank() && !filename.matches(Regex("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?"))) { "$oldPath:name invalid filename $filename" }
            val path = "chapters/${if(filename == "章节") "$filename-${y.getString("id")}" else filename}.yml"
            require(chapterTexts.keys.none { it.equals(path,true) }) { "$oldPath: duplicate chapter filename $path" }
            chapterTexts[path] = replaceTasks(raw,quests,deps)
        }
        if(chapterTexts.isEmpty()) {
            val ch = ChapterFiles.parse(yaml(defaultFile,bundledChapter),defaultFile)
            // Original Quest had the same three-task chain, with dependencies in quests/.
            chapterTexts[defaultFile] = replaceTasks(bundledChapter,ch.quests,ch.quests.associateWith { oldDeps[it].orEmpty() })
        }
        val chapters = chapterTexts.map { (path,text) -> ChapterFiles.parse(yaml(path,text),path) }
        if(oldChapters.isEmpty()) chapters.forEach { ch -> ch.quests.forEach { id ->
            val declared = ch.taskRequirements[id].orEmpty()
            require(oldDeps[id].orEmpty().all { it in declared }) { "chapters/:${ch.id}.tasks.$id.requires conflicts with legacy task prerequisites ${oldDeps[id]}; originals preserved" }
        } }
        // Validate references, membership, duplicate IDs and cycles before publishing the journal.
        ChapterGraph.prepare(ids.map { QuestDefinition(it,it,emptyList(),"minecraft:book",emptyList(),emptyList(),emptyList()) },chapters)
        val changes = chapterTexts.mapNotNull { (path,text) ->
            val before = root.resolve(path).takeIf { Files.exists(it) }?.let { Files.readString(it) }
            if(before == text) null else ConfigMigrationPlan.Change(path,before,text)
        }.toMutableList()
        taskFiles.forEach { (path,text) -> if(tasks.getValue(path).get("schema-version") == 1) {
            val next = removeBlock(text,"prerequisites").replace(Regex("(?m)^schema-version: *1(?= *(?:#|\\r?$))"),"schema-version: 2")
            require(yaml(path,next).get("schema-version") == 2) { "$path:schema-version could not migrate; original preserved" }
            changes += ConfigMigrationPlan.Change(path,text,next)
        } }
        if(old != null) changes += ConfigMigrationPlan.Change(oldPath,old,null)
        try { plan.apply(changes) }
        catch(e: Exception) { throw IllegalStateException("chapters/: configuration migration schema 1 -> 2 failed; originals/recovery plan in .migrations/chapter-v2",e) }
    }

    fun files(directory: Path): List<Path> = if(!Files.isDirectory(directory)) emptyList() else Files.list(directory).use { paths ->
        paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".yml") }.sorted().toList()
    }
    private fun yaml(path: String, text: String) = YamlConfiguration().apply {
        try { loadFromString(text) } catch(e: Exception) { throw IllegalArgumentException("$path: YAML migration parse failed; original preserved",e) }
    }
    private fun splitLegacy(path: String, text: String): List<String> {
        val y = yaml(path,text)
        require(y.get("schema-version") == 1 && y.getList("chapters")?.all { it is Map<*,*> } == true) { "$path: expected schema 1 chapters list; original preserved" }
        val lines = text.lines()
        val start = lines.indexOfFirst { it.startsWith("chapters:") }
        val end = (start+1 until lines.size).firstOrNull { lines[it].matches(Regex("^[^ #].*:.*")) } ?: lines.size
        val entries = (start+1 until end).filter { lines[it].startsWith("  - ") || lines[it] == "  -" }
        require(entries.size == y.getMapList("chapters").size && entries.isNotEmpty()) { "$path: migration requires block list indentation of two spaces; original preserved" }
        val extra = removeBlock(removeBlock(text,"chapters"),"schema-version")
        return entries.mapIndexed { i,n ->
            val body = lines.subList(n,entries.getOrNull(i+1) ?: end).mapIndexed { j,line ->
                if(j == 0) line.removePrefix("  -").removePrefix(" ") else line.removePrefix("    ")
            }.joinToString("\n")
            "schema-version: 2\n$extra\n$body\n"
        }
    }
    private fun replaceTasks(text: String, quests: List<String>, dependencies: Map<String,List<String>>): String {
        var next = text
        for(key in listOf("quests","sequential","tasks")) next = removeBlock(next,key)
        val block = quests.joinToString("\n") { id ->
            "  - quest: $id" + dependencies[id].orEmpty().takeIf { it.isNotEmpty() }?.joinToString("\n      - ","\n    requires:\n      - ").orEmpty()
        }
        return next.trimEnd()+"\n# 前置关系由章节统一管理；列表顺序只影响展示。\ntasks:\n$block\n"
    }
    private fun removeBlock(text: String, key: String): String {
        val lines = text.lines().toMutableList()
        val start = lines.indexOfFirst { it.startsWith("$key:") }
        if(start < 0) return text
        var end = start+1
        while(end < lines.size && (lines[end].isBlank() || lines[end].startsWith(" ") || lines[end].startsWith("#"))) end++
        // Keep every comment from the removed block, including nested comments.
        val comments = lines.subList(start,end).mapNotNull { line -> Regex("(?:^|\\s)(#.*)$").find(line)?.groupValues?.get(1) }
        repeat(end-start) { lines.removeAt(start) }; lines.addAll(start,comments)
        return lines.joinToString("\n")
    }
}
