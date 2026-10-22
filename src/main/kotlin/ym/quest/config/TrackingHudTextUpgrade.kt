package ym.quest.config

import org.bukkit.configuration.file.YamlConfiguration
import java.nio.file.Files
import java.nio.file.Path

/** IO only. Update exact bundled wording, retaining custom lore, comments and unknown fields. */
object TrackingHudTextUpgrade {
    fun migrate(root: Path) {
        val name="tracking-hud-v1"
        val plan=ConfigMigrationPlan(root,name)
        if(plan.resume() || Files.exists(root.resolve(".migrations/$name/complete"))) return
        val changes=listOf("tasks","settings").mapNotNull { menu ->
            val path="gui/$menu.yml"
            val file=root.resolve(path)
            if(!Files.exists(file)) return@mapNotNull null
            val old=Files.readString(file)
            val yaml=YamlConfiguration().apply { loadFromString(old) }
            require(yaml.get("schema-version")==1) { "$path:schema-version=${yaml.get("schema-version")} unsupported in $name; original preserved" }
            var next=old.replace("点击追踪进行中的任务；未解锁任务也可阅读。","点击追踪并开启 HUD；未解锁任务也可阅读。")
                .replace("侧边栏 HUD：<hud-mode>","任务追踪：<hud-mode>")
                .replace("常驻或关闭任务侧边栏。","开启时自动追踪任务并显示侧边栏 HUD。")
                .replace("关闭时保留已选任务；追踪任务会自动开启。","关闭时停止自动追踪并隐藏 HUD，保留已选任务。")
            if(menu=="settings") {
                yaml.getConfigurationSection("components")?.getKeys(false)?.filter {
                    yaml.getString("components.$it.action")=="toggle-auto"
                }?.forEach { key ->
                    require(key.length==1) { "$path:components.$key invalid Shape symbol; original preserved" }
                    yaml.getStringList("Shape").forEach { row ->
                        next=next.replace("'$row'","'${row.replace(key," ")}'")
                    }
                    next=next.replace(Regex("(?ms)^  ${Regex.escape(key)}: *\\r?\\n.*?(?=^  \\S|^\\S|\\z)"),"")
                }
            }
            if(next==old) null else {
                QuestFiles.validateMenu(path,YamlConfiguration().apply { loadFromString(next) })
                ConfigMigrationPlan.Change(path,old,next)
            }
        }
        try { plan.apply(changes) }
        catch(e: Exception) { throw IllegalStateException("gui/: $name schema=1 failed; originals and recovery journal preserved",e) }
    }
}
