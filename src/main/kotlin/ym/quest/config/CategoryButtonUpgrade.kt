package ym.quest.config

import org.bukkit.configuration.file.YamlConfiguration
import java.nio.file.Files
import java.nio.file.Path

/** IO only. Collapse legacy category controls without changing chapter slots or unrelated components. */
object CategoryButtonUpgrade {
    fun migrate(root: Path,bundled: String) {
        val plan=ConfigMigrationPlan(root,"category-button-v1")
        if(plan.resume() || Files.exists(root.resolve(".migrations/category-button-v1/complete"))) return
        val path="gui/chapters.yml"
        val file=root.resolve(path)
        if(!Files.exists(file)) return
        val old=Files.readString(file)
        val y=YamlConfiguration().apply { loadFromString(old) }
        require(y.get("schema-version")==2) { "$path:schema-version=${y.get("schema-version")} unsupported for category-button-v1; original preserved" }
        val keys=y.getConfigurationSection("components")!!.getKeys(false).filter {
            y.getString("components.$it.action") in setOf("category-main","category-side","category-special","category-cycle")
        }.toSet()
        val shape=y.getStringList("Shape")
        val slots=shape.joinToString("").indices.filter { shape.joinToString("")[it].toString() in keys }
        if(slots.isEmpty()) return
        val slot=slots.minWith(compareBy<Int> { it/9 }.thenBy { kotlin.math.abs(it%9-4) })
        val keep=shape[slot/9][slot%9].toString()
        if(keys.size==1 && slots.size==1 && y.getString("components.$keep.action")=="category-cycle") return
        val defaults=YamlConfiguration().apply { loadFromString(bundled) }
        val source=defaults.getConfigurationSection("components")!!.getKeys(false).single { defaults.getString("components.$it.action")=="category-cycle" }
        var next=old
        shape.forEachIndexed { row,text ->
            val changed=text.mapIndexed { column,ch ->
                if(ch.toString() in keys && row*9+column!=slot) y.getString("empty-slots"," ")!!.single() else ch
            }.joinToString("")
            next=next.replace("'$text'","'$changed'")
        }
        keys.forEach { key ->
            val pattern=Regex("(?ms)^  ['\"]?${Regex.escape(key)}['\"]?: *\\r?\\n.*?(?=^  \\S|^\\S|\\z)")
            val block=pattern.find(next) ?: error("$path:components.$key expected block mapping; original preserved")
            if(key!=keep) next=next.replaceRange(block.range,"")
            else {
                fun quote(value: String)="'${value.replace("'","''")}'"
                var updated=block.value.replace(Regex("(?m)^    action:.*$"),"    action: 'category-cycle'")
                val name="    name: ${quote(defaults.getString("components.$source.name")!!)}"
                updated=if(Regex("(?m)^    name:.*$").containsMatchIn(updated)) updated.replace(Regex("(?m)^    name:.*$"),name) else updated.trimEnd()+"\n$name\n"
                val lore="    lore:\n"+defaults.getStringList("components.$source.lore").joinToString("\n") { "      - ${quote(it)}" }+"\n"
                val lorePattern=Regex("(?ms)^    lore:.*?(?=^    \\S|^  \\S|\\z)")
                updated=if(lorePattern.containsMatchIn(updated)) updated.replace(lorePattern,lore) else updated.trimEnd()+"\n$lore"
                next=next.replaceRange(block.range,updated)
            }
        }
        QuestFiles.validateMenu(path,YamlConfiguration().apply { loadFromString(next) })
        plan.apply(listOf(ConfigMigrationPlan.Change(path,old,next)))
    }
}
