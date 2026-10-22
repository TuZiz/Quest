package ym.quest.config

import org.bukkit.configuration.file.YamlConfiguration
import java.nio.file.Files
import java.nio.file.Path

/** Fuel-chest interaction uses the same committed fuel metric. Migrate only known stock wording. IO only. */
object FuelTaskTextUpgrade {
    fun migrate(root: Path) {
        val file=QuestTaskFiles.files(root.resolve("quests")).singleOrNull {
            YamlConfiguration().apply { loadFromString(Files.readString(it)) }.getString("id")=="first_fuel"
        } ?: return
        val raw=Files.readString(file)
        val y=YamlConfiguration().apply { loadFromString(raw) }
        require(y.get("schema-version") in listOf(1,2)) { "$file:schema-version=${y.get("schema-version")} unsupported; fuel wording preserved" }
        if(y.getString("id") != "first_fuel") return
        var next=raw
        val old="点击<#FDE68A><bold>「燃料」</bold><#E2E8F0><newline>   放入燃料即可"
        if(next.contains(old)) {
            next=next.replace(old,"点击<#FDE68A><bold>「燃料」</bold><#E2E8F0><newline>   打开燃料仓")
            val lines=next.lines().toMutableList()
            val at=lines.indexOfFirst { it.contains("点击<#FDE68A><bold>「燃料」</bold><#E2E8F0><newline>   打开燃料仓") }
            lines.add(at+1,"  - '<#E2E8F0>Shift点击背包燃料<newline>   或放入上方格子'")
            next=lines.joinToString("\n")
        }
        next=next.replace("为生成器<#FDE68A><bold>添加燃料</bold>","向燃料仓<#FDE68A><bold>存入燃料</bold>")
        if(next != raw) {
            val check=YamlConfiguration().apply { loadFromString(next) }
            require(check.getMapList("objectives") == y.getMapList("objectives").map { row -> row.mapValues { (key,value) ->
                if(key == "description" && value is String) value.replace("为生成器<#FDE68A><bold>添加燃料</bold>","向燃料仓<#FDE68A><bold>存入燃料</bold>") else value
            } } && check.getMapList("rewards") == y.getMapList("rewards")) { "$file: fuel wording migration changed task data; original preserved" }
            ResourceUpgrade.replace(file,next)
        }
    }
}
