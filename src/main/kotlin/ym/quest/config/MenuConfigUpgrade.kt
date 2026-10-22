package ym.quest.config

import org.bukkit.configuration.file.YamlConfiguration
import java.nio.file.Files
import java.nio.file.Path

/** One durable transition to three menus. Originals and outputs are published before any retirement. */
object MenuConfigUpgrade {
    private const val migration="menus-v3"
    val retired=listOf("home","journal-chapters","journal-tasks","main","task","detail","chapter-detail","章节","章节详情")
        .map { "gui/$it.yml" }
    fun migrate(root: Path,bundled: Map<String,String>) {
        val plan=ConfigMigrationPlan(root,migration)
        if(plan.resume()) return
        if(Files.exists(root.resolve(".migrations/$migration/complete"))) {
            require(retired.none { Files.exists(root.resolve(it)) }) { "gui/: retired menu restored after $migration; originals preserved; move it outside gui/" }
            return
        }
        val paths=listOf("gui/chapters.yml","gui/tasks.yml","gui/settings.yml")+retired
        val originals=paths.associateWith { path -> root.resolve(path).takeIf(Files::exists)?.let(Files::readString) }
        originals.forEach { (path,raw) -> if(raw!=null) {
            val y=parse(path,raw)
            require(y.get("schema-version") in listOf(1,2)) { "$path:schema-version=${y.get("schema-version")} unsupported; original preserved" }
        } }
        val currentChapters=originals["gui/chapters.yml"]?.takeIf { parse("gui/chapters.yml",it).getInt("schema-version")==2 }
        val chapterSource=currentChapters ?: listOf("gui/home.yml","gui/journal-chapters.yml","gui/章节.yml","gui/chapters.yml")
            .firstNotNullOfOrNull { originals[it] } ?: bundled.getValue("chapters")
        val taskSource=originals["gui/tasks.yml"] ?: listOf("gui/journal-tasks.yml","gui/main.yml","gui/chapter-detail.yml","gui/章节详情.yml")
            .firstNotNullOfOrNull { originals[it] } ?: bundled.getValue("tasks")
        val next=mapOf(
            "gui/chapters.yml" to prepare(chapterSource,true,bundled.getValue("chapters")),
            "gui/tasks.yml" to prepare(taskSource,false,bundled.getValue("tasks")),
            "gui/settings.yml" to (originals["gui/settings.yml"] ?: bundled.getValue("settings")))
        next.forEach { (path,raw) ->
            val y=parse(path,raw)
            require(y.getInt("schema-version")==if(path=="gui/chapters.yml") 2 else 1) { "$path:schema-version=${y.get("schema-version")} invalid migration output; original preserved" }
            QuestFiles.validateMenu(path,y)
            if(path!="gui/settings.yml") require(y.isString("reward-format")) { "$path:reward-format missing; original preserved" }
        }
        val changes=next.mapNotNull { (path,raw) ->
            val old=originals[path]
            if(old==raw) null else ConfigMigrationPlan.Change(path,old,raw)
        }.toMutableList()
        retired.forEach { path -> originals[path]?.let { changes+=ConfigMigrationPlan.Change(path,it,null) } }
        // Fresh/current installs still get a completion record, so later loads cannot revive legacy screens.
        if(changes.isEmpty()) return
        try { plan.apply(changes) }
        catch(e: Exception) { throw IllegalStateException("gui/: $migration failed; original files and recovery journal preserved",e) }
    }
    private fun parse(path: String,raw: String)=YamlConfiguration().apply {
        try { loadFromString(raw) } catch(e: Exception) { throw IllegalArgumentException("$path: $migration YAML parse failed; original preserved",e) }
    }
    private fun prepare(raw: String,chapters: Boolean,default: String): String {
        val y=parse("gui/"+(if(chapters) "chapters" else "tasks")+".yml",raw)
        var result=raw.replace(Regex("(?m)^schema-version: *[12](?= *(?:#|\\r?$))"),"schema-version: ${if(chapters) 2 else 1}")
        result=result.replace(Regex("(?m)^player-access:.*\\r?\\n?"),"")
        val section=y.getConfigurationSection("components") ?: error("gui/: $migration components missing; original preserved")
        val removed=section.getKeys(false).filter { y.getString("components.$it.action") in
            setOf("chapter-claim","mailbox","preview","chapter-preview","claim","summary","objectives","chapters") }.toSet()
        y.getStringList("Shape").forEach { row ->
            val next=row.map { if(it.toString() in removed) ' ' else it }.joinToString("")
            result=result.replace("'$row'","'$next'").replace("\"$row\"","\"$next\"")
        }
        val lines=result.lines()
        val start=lines.indexOfFirst { it.startsWith("components:") }
        require(start>=0) { "gui/: $migration requires block components; original preserved" }
        val end=(start+1 until lines.size).firstOrNull { lines[it].matches(Regex("^[^ #].*:.*")) } ?: lines.size
        val starts=(start+1 until end).filter { lines[it].matches(Regex("^  \\S.*: *(#.*)?$")) }
        val output=lines.take(starts.firstOrNull() ?: end).toMutableList()
        starts.forEachIndexed { index,line ->
            val key=lines[line].trim().substringBeforeLast(':').trim().trim('\'', '"')
            if(key in removed) return@forEachIndexed
            var block=lines.subList(line,starts.getOrNull(index+1) ?: end).joinToString("\n")
            val action=y.getString("components.$key.action","")!!
            val changed=when(action) {
                "chapter" -> "journal-chapter"
                "quest" -> "journal-quest"
                "chapter-summary" -> ""
                else -> action
            }
            if(changed!=action) block=block.replace(Regex("(?m)^(\\s+action:) *[^\\r\\n]*")) { "${it.groupValues[1]} '$changed'" }
            if(!chapters && changed=="journal-quest") block=block.replace("点击查看；未解锁任务也可阅读。","点击追踪进行中的任务；未解锁任务也可阅读。")
            output+=block.lines()
        }
        output+=lines.drop(end)
        result=output.joinToString("\n")
        if(!y.isString("reward-format")) {
            val format=parse("bundled menu",default).getString("reward-format")!!
            result=result.trimEnd()+"\nreward-format: '${format.replace("'","''")}'\n"
        }
        return result
    }
}
