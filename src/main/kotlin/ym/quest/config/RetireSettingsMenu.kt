package ym.quest.config

import org.bukkit.configuration.file.YamlConfiguration
import java.nio.file.Files
import java.nio.file.Path

/** IO executor only. Existing migration journal backs up originals before removing this screen. */
object RetireSettingsMenu {
    fun migrate(root: Path) {
        val plan=ConfigMigrationPlan(root,"retire-settings-v1")
        if(plan.resume()) return
        if(Files.exists(root.resolve(".migrations/retire-settings-v1/complete"))) return
        val changes=mutableListOf<ConfigMigrationPlan.Change>()
        for(path in listOf("gui/chapters.yml","gui/tasks.yml")) {
            val file=root.resolve(path)
            if(!Files.exists(file)) continue
            val raw=Files.readString(file)
            val y=YamlConfiguration().apply { loadFromString(raw) }
            val removed=y.getConfigurationSection("components")?.getKeys(false).orEmpty().filter {
                y.getString("components.$it.action") in setOf("settings","toggle-hud","toggle-auto","toggle-notices","toggle-sound")
            }.toSet()
            if(removed.isEmpty()) continue
            var next=raw
            for(row in y.getStringList("Shape")) {
                val updated=row.map { if(it.toString() in removed) ' ' else it }.joinToString("")
                next=next.replace("'$row'","'$updated'").replace("\"$row\"","\"$updated\"")
            }
            val lines=next.lines()
            val kept=mutableListOf<String>()
            var skipping=false
            for(line in lines) {
                if(line.matches(Regex("^  \\S.*: *(#.*)?$"))) {
                    skipping=line.trim().substringBeforeLast(':').trim().trim('\'', '"') in removed
                } else if(line.isNotBlank() && !line.startsWith(" ") && !line.startsWith("#")) skipping=false
                if(!skipping) kept+=line
            }
            next=kept.joinToString(if(raw.contains("\r\n")) "\r\n" else "\n")
            QuestFiles.validateMenu(path,YamlConfiguration().apply { loadFromString(next) })
            changes+=ConfigMigrationPlan.Change(path,raw,next)
        }
        val settings="gui/settings.yml"
        root.resolve(settings).takeIf(Files::exists)?.let {
            changes+=ConfigMigrationPlan.Change(settings,Files.readString(it),null)
        }
        plan.apply(changes)
    }
}
