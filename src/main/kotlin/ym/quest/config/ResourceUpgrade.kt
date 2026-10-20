package ym.quest.config

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.*
import org.bukkit.configuration.file.YamlConfiguration

/** Idempotent, narrow upgrade: preserves all unknown fields and comments without YAML reserialization. */
object ResourceUpgrade {
    /** Before default extraction, move existing user files byte-for-byte. Never merge or replace. */
    fun relocateLayout(root: Path) {
        val paths = mapOf("gui/章节.yml" to "gui/chapters.yml",
            "gui/章节详情.yml" to "gui/chapter-detail.yml", "章节.yml" to "chapters/章节.yml")
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
        listOf("gui/detail.yml" to ("前置任务领取后自动解锁下一任务。" to "前置目标完成即解锁，无需立即领奖。"),
            "quests/第一批资源.yml" to ("领取上一任务后开始计数；" to "上一任务完成即开始计数；")).forEach { (name,wording) ->
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
        val old = Files.readString(file)
        val existing = YamlConfiguration().apply { loadFromString(old) }
        require(existing.getInt("schema-version",-1) == 1) { "$file:schema-version=${existing.get("schema-version")} unsupported; original preserved" }
        val defaults = YamlConfiguration().apply { loadFromString(bundled) }
        val missing = listOf("chapter-status","chapter-progress","chapter-reward-status","notices","automatic-rewards","hud-completed","hud-controls","hud-notices","smart-hud","sidebar-hud","completion-hud","hud-test").filter { !existing.contains(it) }
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
