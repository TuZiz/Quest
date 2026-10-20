package ym.quest.config

import org.bukkit.configuration.file.YamlConfiguration
import java.nio.file.Files
import java.nio.file.Path

/** IO executor only. One-time channel change; custom messages and unrelated options survive. */
object NoticeConfigUpgrade {
    fun migrate(root: Path) {
        val file=root.resolve("提示.yml")
        if(!Files.exists(file)) return
        try {
            val old=Files.readString(file)
            val y=YamlConfiguration().apply { loadFromString(old) }
            val version=y.getInt("schema-version",-1)
            require(version in 1..2) { "unsupported schema-version=$version" }
            NoticeFiles.parse(y)
            if(version == 2) return
            var section=""
            val keys=setOf("chat","action-bar","title","sound-enabled")
            val lines=old.lines().map { line ->
                if(line.isNotBlank() && !line.startsWith(" ") && !line.startsWith("#")) section=line.substringBefore(':')
                if(line.startsWith("schema-version:")) line.replace(Regex(":\\s*1(?=\\s*(?:#|$))"),": 2")
                else if(section in setOf("task-complete","chapter-complete")) {
                    val key=line.trimStart().substringBefore(':')
                    if(key in keys) line.replace(Regex(":\\s*(?:true|false)(?=\\s*(?:#|$))"),": ${key == "chat"}") else line
                } else line
            }
            val next=lines.joinToString("\n")
            val parsed=YamlConfiguration().apply { loadFromString(next) }
            require(parsed.getInt("schema-version") == 2)
            NoticeFiles.parse(parsed)
            val backup=root.resolve(".migrations/notices-v2/0.before")
            Files.createDirectories(backup.parent)
            if(!Files.exists(backup)) Files.copy(file,backup)
            ResourceUpgrade.replace(file,next)
        } catch(e: Exception) {
            throw IllegalStateException("提示.yml: schema 1 -> 2 chat completion migration failed; original retained",e)
        }
    }
}
