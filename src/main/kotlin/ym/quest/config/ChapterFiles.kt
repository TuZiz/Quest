package ym.quest.config

import org.bukkit.configuration.file.YamlConfiguration
import ym.quest.domain.*

object ChapterFiles {
    fun parse(s: YamlConfiguration, file: String = "chapters/<chapter>.yml"): ChapterDefinition {
        val path = file
        require(s.get("schema-version") == 2) { "$path:schema-version=${s.get("schema-version")} expected 2" }
        require(!s.contains("chapters") && !s.contains("quests") && !s.contains("sequential")) { "$path: use tasks.quest/requires; legacy chapters/quests/sequential not allowed" }
        fun text(key: String) = s.getString(key)?.takeIf { it.isNotBlank() } ?: error("$path.$key=${s.get(key)} expected text")
        fun strings(key: String): List<String> {
            require(!s.contains(key) || s.getList(key)?.all { it is String } == true) { "$path.$key=${s.get(key)} expected list" }
            return s.getStringList(key)
        }
        val id = text("id")
        require(id.matches(Regex("[a-z0-9_-]+"))) { "$path.id=$id invalid" }
        require(s.get("order") is Int) { "$path.order=${s.get("order")} expected integer" }
        val rawTasks = s.getList("tasks")
        require(!rawTasks.isNullOrEmpty() && rawTasks.all { it is Map<*,*> }) { "$path.tasks=${s.get("tasks")} expected nonempty task list" }
        val tasks = rawTasks.mapIndexed { index, raw ->
            val task = raw as Map<*,*>
            require(task.keys.all { it in setOf("quest","requires","optional") }) { "$path.tasks.$index=$task: only quest/requires/optional allowed" }
            require(!task.containsKey("optional") || task["optional"] is Boolean) { "$path.tasks.$index.optional must be boolean" }
            val quest = task["quest"] as? String
            val requires = task["requires"]
            require(!quest.isNullOrBlank()) { "$path.tasks.$index.quest=$quest expected task ID" }
            require(!task.containsKey("requires") || requires is List<*> && requires.all { it is String }) { "$path.tasks.$index.requires=$requires expected string list" }
            quest to ((requires as? List<*>)?.map { it as String } ?: emptyList())
        }
        require(tasks.map { it.first }.distinct().size == tasks.size) { "$path.tasks duplicate quest" }
        require(!s.contains("rewards") || s.getList("rewards")?.all { it is Map<*,*> } == true) { "$path.rewards=${s.get("rewards")} expected list" }
        val rewards = s.getMapList("rewards").mapIndexed { n, r ->
            val item = r["item"] as? String
            val amount = r["amount"] as? Int
            require(!item.isNullOrBlank() && amount != null && amount in 1..4096) { "$path.rewards.$n=$r invalid item/amount" }
            Reward(item,amount)
        }
        return ChapterDefinition(id,text("name"),text("icon"),strings("description"),s.getInt("order"),
            strings("prerequisites"),tasks.map { it.first },rewards,strings("story"),tasks.toMap(),file,
            rawTasks.map { it as Map<*,*> }.filter { it["optional"] == true }.map { it["quest"] as String }.toSet())
    }
}
