package ym.quest.config

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.*
import org.bukkit.configuration.file.YamlConfiguration

/** Idempotent, narrow upgrade: preserves all unknown fields and comments without YAML reserialization. */
object ResourceUpgrade {
    /** Before default extraction, move existing user files byte-for-byte. Never merge or replace. */
    fun relocateLayout(root: Path) {
        val paths = mapOf("章节.yml" to "chapters/章节.yml")
        paths.forEach { (old,next) ->
            require(!Files.exists(root.resolve(old)) || !Files.exists(root.resolve(next))) {
                "$old -> $next: resource layout migration (schema=1) conflict; both files preserved"
            }
        }
        paths.forEach { (old,next) ->
            val source = root.resolve(old); val target = root.resolve(next)
            if(Files.exists(source)) try {
                Files.createDirectories(target.parent)
                Files.move(source,target,ATOMIC_MOVE)
            } catch(e: Exception) {
                throw IllegalStateException("$old -> $next: resource layout migration (schema=1) failed; source preserved",e)
            }
        }
    }
    val questNames = mapOf("sky_start" to "星海启航", "first_fuel" to "能源启动", "first_resources" to "第一批资源")
    fun renameQuests(root: Path) {
        questNames.forEach { (id,name) ->
            require(!Files.exists(root.resolve("quests/$id.yml")) || !Files.exists(root.resolve("quests/$name.yml"))) {
                "quests/$id.yml -> quests/$name.yml: chapter resource upgrade conflict; originals preserved"
            }
        }
        questNames.forEach { (id,name) ->
            val old = root.resolve("quests/$id.yml"); val next = root.resolve("quests/$name.yml")
            if(Files.exists(old)) {
                require(!Files.exists(next)) { "$old -> $next: chapter resource upgrade (YAML schema=1) filename conflict; originals preserved" }
                try { Files.move(old,next,ATOMIC_MOVE) }
                catch(e: Exception) { throw IllegalStateException("$old -> $next: chapter resource upgrade (YAML schema=1) rename failed; original preserved",e) }
            }
        }
    }
    fun updateProgressionText(root: Path) {
        listOf("quests/第一批资源.yml" to ("领取上一任务后开始计数；" to "上一任务完成即开始计数；")).forEach { (name,wording) ->
            val file = root.resolve(name)
            if(Files.exists(file)) {
                val old = Files.readString(file)
                val y = YamlConfiguration().apply { loadFromString(old) }
                require(y.get("schema-version") in if(name.startsWith("quests/")) listOf(1,2) else listOf(1)) { "$name:schema-version=${y.get("schema-version")} unsupported; original preserved" }
                if(old.contains(wording.first)) replace(file,old.replace(wording.first,wording.second))
            }
        }
    }
    fun appendLanguage(file: Path, bundled: String) {
        var old = Files.readString(file)
        val existing = YamlConfiguration().apply { loadFromString(old) }
        require(existing.getInt("schema-version",-1) == 1) { "$file:schema-version=${existing.get("schema-version")} unsupported; original preserved" }
        val defaults = YamlConfiguration().apply { loadFromString(bundled) }
        val oldGuide = "入岛后，为生成器添加燃料即可继续任务。"
        if(existing.getStringList("introduction.dialogue").any { it.contains(oldGuide) }) {
            old = old.replace(oldGuide,"入岛领取生成器，放置后获得燃料，再为它添加燃料。")
            replace(file,old)
        }
        // Replace only the previous bundled wording, never a customized no-island message.
        val oldNoIsland = "<#FFD700>创建或加入空岛后，即可开启任务。"
        val oldPlayerHelp = "<#DDB66D>Quest 帮助<newline><#EEF3F5>/quest help <#A6B2C0>查看帮助；任务自动推进，完成后发送聊天消息，奖励自动发放。"
        if(existing.getString("command-help.player") == oldPlayerHelp) {
            old=old.replace(oldPlayerHelp,checkNotNull(defaults.getString("command-help.player")))
            replace(file,old)
        }
        if(existing.getString("tab.no-island") == oldNoIsland) {
            old = old.replace(oldNoIsland,checkNotNull(defaults.getString("tab.no-island")))
            replace(file,old)
        }
        // Add the new hint to pre-release introduction blocks without rewriting user text/comments.
        if(existing.isConfigurationSection("introduction") && !existing.contains("introduction.actionbar")) {
            val line = bundled.lines().single { it.startsWith("  actionbar:") }
            val root = Regex("(?m)^introduction:[ \\t]*(?:#.*)?$")
            require(root.containsMatchIn(old)) { "$file:introduction expected block mapping; original preserved" }
            old = root.replace(old) { it.value + "\n" + line }
            replace(file, old)
        }
        if(existing.isConfigurationSection("tab-groups") && !existing.contains("tab-groups.side-objective")) {
            val line=bundled.lines().single { it.startsWith("  side-objective:") }
            old=Regex("(?m)^tab-groups:[ \t]*(?:#.*)?$").replace(old) { it.value+"\n"+line }
            replace(file,old)
        }
        val missing = listOf("tab-groups","reward-delivered","chapter-status","chapter-progress","chapter-reward-status","notices","automatic-rewards","command-help","tab","introduction","hud","journal").filter { !existing.contains(it) }
        if(missing.isEmpty()) return
        // Append complete new root blocks only. Malformed existing keys are rejected by the loader.
        val lines = bundled.lines()
        val blocks = missing.joinToString("\n") { key ->
            require(defaults.contains(key))
            val start = lines.indexOfFirst { it.startsWith("$key:") }
            lines.drop(start).take(1).plus(lines.drop(start+1).takeWhile { it.isBlank() || it.startsWith(" ") || it.startsWith("#") }).joinToString("\n")
        }
        replace(file,old.trimEnd()+"\n\n# 章节升级新增文案，保留原有内容与注释。\n"+blocks+"\n")
    }
    fun replace(file: Path, text: String) {
        val temp = Files.createTempFile(file.parent,".quest-upgrade-",".tmp")
        try {
            Files.writeString(temp,text)
            Files.move(temp,file,ATOMIC_MOVE,REPLACE_EXISTING)
        } catch(e: Exception) { throw IllegalStateException("$file: chapter resource upgrade (YAML schema=1) migration failed; original preserved",e) }
        finally { Files.deleteIfExists(temp) }
    }
}
