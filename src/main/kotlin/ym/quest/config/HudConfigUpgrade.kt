package ym.quest.config

import org.bukkit.configuration.file.YamlConfiguration
import java.nio.file.Files
import java.nio.file.Path

/** IO thread only; append new defaults, retain all existing choices/comments and a durable original. */
object HudConfigUpgrade {
    fun migrate(root: Path, bundled: String) {
        ConfigMigrationPlan(root,"hud-v2").resume()
        ConfigMigrationPlan(root,"hud-v3").resume()
        val plan=ConfigMigrationPlan(root,"hud-v4")
        if(plan.resume()) return
        val path=root.resolve("显示.yml")
        if(!Files.exists(path)) return
        val old=Files.readString(path)
        val y=YamlConfiguration().apply { loadFromString(old) }
        require(y.getInt("schema-version") in 1..4) { "显示.yml:schema-version=${y.get("schema-version")} unsupported; original preserved" }
        if(y.getInt("schema-version") == 4) return
        val defaults=YamlConfiguration().apply { loadFromString(bundled) }
        val missing=defaults.getKeys(false).filter { !y.contains(it) }
        val lines=bundled.lines()
        val appended=missing.joinToString("\n") { key ->
            val start=lines.indexOfFirst { it.startsWith("$key:") }
            lines.drop(start).take(1).plus(lines.drop(start+1).takeWhile { it.startsWith(" ") }).joinToString("\n")
        }
        val next=old.replace(Regex("(?m)^schema-version: [123]\\s*$"),"schema-version: 4")+
            "\n# 常驻文字任务栏新增选项；材质和坐标在服务端 Xerr 配置。\n$appended\n"
        ym.quest.hud.HudSettings.parse(YamlConfiguration().apply { loadFromString(next) })
        plan.apply(listOf(ConfigMigrationPlan.Change("显示.yml",old,next)))
    }
}
