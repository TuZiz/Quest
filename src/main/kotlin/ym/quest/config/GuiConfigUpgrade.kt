package ym.quest.config

import org.bukkit.configuration.file.YamlConfiguration
import java.nio.file.Files
import java.nio.file.Path

/** IO executor only. Retires two screens through the existing durable configuration journal. */
object GuiConfigUpgrade {
    fun migrate(root: Path, bundled: String) {
        val plan = ConfigMigrationPlan(root,"gui-v2")
        if(plan.resume() || Files.exists(root.resolve(".migrations/gui-v2/complete"))) return
        fun read(name: String) = root.resolve(name).takeIf(Files::exists)?.let(Files::readString)
        val main = read("gui/main.yml")
        val chapter = read("gui/chapter-detail.yml")
        val detail = read("gui/detail.yml")
        if(main == null && chapter == null && detail == null) return
        fun parse(name: String, raw: String) = YamlConfiguration().apply {
            loadFromString(raw)
            require(getInt("schema-version") in if(name == "gui/main.yml") 1..2 else 1..1) {
                "$name:schema-version=${get("schema-version")} cannot migrate GUI; originals preserved"
            }
        }
        val oldMain = main?.let { parse("gui/main.yml",it) }
        require(oldMain?.getInt("schema-version") != 2 || chapter == null) {
            "gui/main.yml + gui/chapter-detail.yml: GUI schema 2 migration conflict; originals preserved"
        }
        chapter?.let { parse("gui/chapter-detail.yml",it) }
        detail?.let { parse("gui/detail.yml",it) }
        val alreadyTasks = oldMain?.getConfigurationSection("components")?.getKeys(false)?.any {
            oldMain.getString("components.$it.action") == "chapter-summary"
        } == true
        val source = chapter ?: if(oldMain?.getInt("schema-version") == 2 || alreadyTasks) main!! else bundled
        var next = prepare(source,true)
        if(!YamlConfiguration().apply { loadFromString(next) }.contains("reward-format")) {
            val format = YamlConfiguration().apply { loadFromString(bundled) }.getString("reward-format")!!
            next += "\nreward-format: '${format.replace("'","''")}'\n"
        }
        if(!YamlConfiguration().apply { loadFromString(next) }.contains("player-access"))
            next += "\n# 普通玩家默认使用 HUD；管理员仍可检查菜单。\nplayer-access: ${oldMain?.getBoolean("player-access",false) ?: false}\n"
        QuestFiles.validateMenu("gui/main.yml",YamlConfiguration().apply { loadFromString(next) })
        val changes = mutableListOf(ConfigMigrationPlan.Change("gui/main.yml",main,next))
        val chapterList = read("gui/chapters.yml")
        if(chapterList != null) {
            parse("gui/chapters.yml",chapterList)
            val cleaned = prepare(chapterList,false)
            QuestFiles.validateMenu("gui/chapters.yml",YamlConfiguration().apply { loadFromString(cleaned) })
            changes += ConfigMigrationPlan.Change("gui/chapters.yml",chapterList,cleaned)
        }
        if(chapter != null) changes += ConfigMigrationPlan.Change("gui/chapter-detail.yml",chapter,null)
        if(detail != null) changes += ConfigMigrationPlan.Change("gui/detail.yml",detail,null)
        plan.apply(changes)
    }

    private fun prepare(raw: String, main: Boolean): String {
        val y = YamlConfiguration().apply { loadFromString(raw) }
        val retired = y.getConfigurationSection("components")!!.getKeys(false).filter {
            y.getString("components.$it.action") in setOf("mailbox","preview","chapter-preview","claim","summary","objectives","chapters")
        }.toSet()
        var source = raw
        // Remove retired controls without moving the user's surviving buttons.
        y.getStringList("Shape").forEach { row ->
            val cleaned = row.map { if(it.toString() in retired) ' ' else it }.joinToString("")
            source = source.replace("'$row'","'$cleaned'").replace("\"$row\"","\"$cleaned\"")
        }
        val active = y.getStringList("Shape").joinToString("").map { it.toString() }.toSet() - retired - " "
        val lines = source.lines()
        val start = lines.indexOfFirst { it.startsWith("components:") }
        val end = (start+1 until lines.size).firstOrNull { lines[it].matches(Regex("^[^ #].*:.*")) } ?: lines.size
        val starts = (start+1 until end).filter { lines[it].matches(Regex("^  \\S.*: *(#.*)?$")) }
        val output = lines.take(if(starts.isEmpty()) end else starts.first()).toMutableList()
        starts.forEachIndexed { i,index ->
            val key = lines[index].trim().substringBeforeLast(':').trim().trim('\'', '"')
            if(key !in active) return@forEachIndexed
            var block = lines.subList(index,starts.getOrNull(i+1) ?: end).joinToString("\n")
            val action = y.getString("components.$key.action","")
            if(!main && action == "back") block = block.replace("action: back","action: close").replace("返回任务中心","关闭")
            if(main && action in setOf("quest","chapter-summary","chapter-claim")) {
                block = block.lines().filterNot { it.contains("点击查看条件及任务奖励") || it.contains("点击查看条件与奖励") }.joinToString("\n")
                val markers = if(action == "quest") listOf("description","objectives","rewards") else listOf("rewards")
                val additions = markers.filter { !block.contains("<$it>") }
                if(additions.isNotEmpty()) {
                    val b = block.lines().toMutableList()
                    val lore = b.indexOfFirst { it.trim() == "lore:" }
                    if(lore < 0) { b += "    lore:"; b += additions.map { "      - '<$it>'" } }
                    else {
                        val after = (lore+1 until b.size).firstOrNull { b[it].matches(Regex("^    [^ #\\-].*:.*")) } ?: b.size
                        val indent = b.subList(lore+1,after).firstOrNull { it.trimStart().startsWith("- ") }?.takeWhile { it == ' ' } ?: "      "
                        b.addAll(after,additions.map { "$indent- '<$it>'" })
                    }
                    block = b.joinToString("\n")
                }
            }
            output += block.lines()
        }
        output += lines.drop(end)
        return output.joinToString("\n").let { if(main) it.replaceFirst(Regex("(?m)^schema-version: 1\\s*$"),"schema-version: 2") else it }.trimEnd()+"\n"
    }
}
