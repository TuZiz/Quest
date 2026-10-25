package ym.quest.config

import org.bukkit.configuration.file.YamlConfiguration
import java.nio.file.Files
import java.nio.file.Path

/** IO only. Replace exact former defaults; preserve customized text and comments. */
object TabTextUpgrade {
    fun migrate(root: Path, bundled: String) {
        val plan = ConfigMigrationPlan(root, "tab-main-style-v1")
        if(plan.resume() || Files.exists(root.resolve(".migrations/tab-main-style-v1/complete"))) return
        val file = root.resolve("lang/zh_cn.yml")
        if(!Files.exists(file)) return
        val old = Files.readString(file)
        val yaml = YamlConfiguration().apply { loadFromString(old) }
        require(yaml.get("schema-version") == 1) { "$file:schema-version=${yaml.get("schema-version")} unsupported; TAB text preserved" }
        val defaults = YamlConfiguration().apply { loadFromString(bundled) }
        val previous = mapOf(
            "chapter-line" to "<#94A3B8>▪ <chapter>",
            "quest-line" to "<#FFD700>✦ <bold><quest></bold>",
            "objective-line" to "<#94A3B8>目标  <#E2E8F0><objective>"
        )
        var section = ""
        val newline = if("\r\n" in old) "\r\n" else "\n"
        val next = old.lineSequence().joinToString(newline) { line ->
            if(line.isNotEmpty() && !line.startsWith(" ") && !line.startsWith("#")) section = line.substringBefore(':')
            val key = line.trimStart().substringBefore(':')
            if(section == "tab" && line.startsWith("  ") && previous[key] == yaml.getString("tab.$key")) {
                val value = checkNotNull(defaults.getString("tab.$key"))
                line.replace("'${previous[key]}'", "'${value.replace("'", "''")}'")
            } else line
        }
        if(next == old) return
        YamlConfiguration().apply { loadFromString(next) }
        plan.apply(listOf(ConfigMigrationPlan.Change("lang/zh_cn.yml", old, next)))
    }
}
