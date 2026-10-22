package ym.quest.config

import org.bukkit.configuration.file.YamlConfiguration
import java.nio.file.Files
import java.nio.file.Path

/** IO only. Idempotently remove retired TAB instructions, preserving custom text, comments and progress. */
object SidebarTextUpgrade {
    val replacements=linkedMapOf(
        "按住 TAB 查看后续任务" to "查看右侧任务栏，继续旅程",
        "按住 TAB 查看任务" to "查看右侧任务栏",
        "按住Tab查看任务与操作提示" to "查看右侧任务栏与操作提示",
        "Java版按住<#FFD700>Tab<#E0F7FA>，即可查看当前任务、进度和操作提示。" to "右侧任务栏会显示当前目标、进度和操作步骤。",
        "Java版按住Tab查看任务；" to "查看右侧任务栏；",
        "按住Tab查看任务" to "查看右侧任务栏"
    )
    fun migrate(root: Path) {
        val file=root.resolve("lang/zh_cn.yml")
        if(!Files.exists(file)) return
        val old=Files.readString(file)
        val y=YamlConfiguration().apply { loadFromString(old) }
        require(y.get("schema-version")==1) { "$file:schema-version=${y.get("schema-version")} unsupported; sidebar wording preserved" }
        val next=replacements.entries.fold(old) { value,(before,after) -> value.replace(before,after) }
        if(next==old) return
        try {
            YamlConfiguration().apply { loadFromString(next) }
            val backup=root.resolve(".migrations/sidebar-text-v1/lang.before")
            if(!Files.exists(backup)) { Files.createDirectories(backup.parent); Files.copy(file,backup) }
            ResourceUpgrade.replace(file,next)
        } catch(e: Exception) {
            throw IllegalStateException("$file:sidebar wording migration schema=1 failed; original/backup retained",e)
        }
    }
}
