package ym.quest.config

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import ym.quest.domain.*
import java.nio.file.Files

data class Settings(val quests: List<QuestDefinition>, val language: YamlConfiguration,
                    val center: YamlConfiguration, val detail: YamlConfiguration)

/** IO executor only. No save of existing YAML: comments/unknown fields remain byte-for-byte intact. */
class QuestFiles(private val plugin: JavaPlugin, private val conditions: ConditionRegistry) {
    private val defaults = listOf("config.yml","lang/zh_cn.yml","gui/main.yml","gui/detail.yml",
        "quests/sky_start.yml","quests/first_fuel.yml","quests/first_resources.yml")
    fun load(): Settings {
        defaults.forEach { name ->
            val file = plugin.dataFolder.toPath().resolve(name)
            if(!Files.exists(file)) {
                Files.createDirectories(file.parent)
                checkNotNull(plugin.getResource(name)) { "Missing bundled $name" }.use { Files.copy(it,file) }
            }
        }
        fun yaml(name: String): YamlConfiguration {
            val text = Files.readString(plugin.dataFolder.toPath().resolve(name))
            return YamlConfiguration().apply {
                try { loadFromString(text) } catch(e: Exception) { throw IllegalArgumentException("$name: YAML解析失败",e) }
                require(getInt("schema-version",-1) == 1) { "$name:schema-version=${get("schema-version")} unsupported; original preserved" }
            }
        }
        val config = yaml("config.yml")
        require(config.getString("storage") == "data/quest.db") { "config.yml:storage=${config.get("storage")} unsupported" }
        val quests = Files.list(plugin.dataFolder.toPath().resolve("quests")).use { paths ->
            paths.filter { it.fileName.toString().endsWith(".yml") }.sorted().map { path ->
                val name = "quests/${path.fileName}"
                parse(name,yaml(name))
            }.toList()
        }
        validateGraph(quests)
        val language = yaml("lang/zh_cn.yml")
        requiredMessages.forEach { require(language.isString(it)) { "lang/zh_cn.yml:$it missing" } }
        val center = yaml("gui/main.yml"); val detail = yaml("gui/detail.yml")
        validateMenu("gui/main.yml",center); validateMenu("gui/detail.yml",detail)
        fun depth(q: QuestDefinition): Int = 1 + (q.prerequisites.maxOfOrNull { id -> depth(quests.single { it.id == id }) } ?: 0)
        return Settings(quests.sortedBy(::depth),language,center,detail)
    }
    private fun parse(file: String, y: YamlConfiguration): QuestDefinition {
        fun text(s: ConfigurationSection, key: String) = s.getString(key)?.takeIf { it.isNotBlank() }
            ?: error("$file:$key=${s.get(key)} expected nonblank string")
        val id = text(y,"id")
        require(id.matches(Regex("[a-z0-9_-]+"))) { "$file:id=$id invalid" }
        require(y.getString("scope") == "island") { "$file:scope=${y.get("scope")} only island is implemented" }
        if(y.contains("prerequisites")) require(y.get("prerequisites") is List<*> && y.getList("prerequisites")!!.all { it is String }) { "$file:prerequisites=${y.get("prerequisites")} expected string list" }
        if(y.contains("rewards")) require(y.get("rewards") is List<*>) { "$file:rewards=${y.get("rewards")} expected list" }
        val objectives = y.getMapList("objectives").map { raw ->
            val s = YamlConfiguration().apply { raw.forEach { (k,v) -> set(k.toString(),v) } }
            val type = text(s,"type"); val target = s.getLong("target",0)
            require(s.get("target") is Int || s.get("target") is Long) { "$file:objectives.target=${s.get("target")} expected integer" }
            require(conditions.contains(type) && target > 0) { "$file:objectives type=$type target=$target invalid" }
            val resource = s.getString("resource")
            require(type != "genlite.produce" || resource?.matches(Regex("[a-z0-9_.-]+:[a-z0-9_/.-]+")) == true) { "$file:objectives.resource=$resource invalid" }
            Objective(text(s,"id"),type,target,resource,text(s,"description"),conditions.metric(type,resource))
        }
        require(objectives.isNotEmpty() && objectives.map { it.id }.distinct().size == objectives.size) { "$file:objectives empty or duplicate id" }
        val rewards = y.getMapList("rewards").map { raw ->
            val s = YamlConfiguration().apply { raw.forEach { (k,v) -> set(k.toString(),v) } }
            val amount = s.getInt("amount",0)
            require(s.get("amount") is Int) { "$file:rewards.amount=${s.get("amount")} expected integer" }
            require(amount in 1..4096) { "$file:rewards.amount=$amount expected 1..4096" }
            Reward(text(s,"item"),amount,s.getBoolean("unless-generator-exists"))
        }
        return QuestDefinition(id,text(y,"name"),y.getStringList("description"),text(y,"icon"),y.getStringList("prerequisites"),objectives,rewards)
    }
    companion object {
        val requiredMessages = listOf("no-permission","unavailable","no-island","loading","error","busy","claimed","not-complete","inventory-full","uncertain","delivered","progress","complete","reloaded","reset","usage","debug","resolved","player-only","reward-line","objective-line","status.LOCKED","status.ACTIVE","status.COMPLETE","status.CLAIMED")
        fun validateGraph(quests: List<QuestDefinition>) {
            val byId = quests.associateBy { it.id }
            require(quests.isNotEmpty() && byId.size == quests.size) { "quests/: empty or duplicate quest IDs" }
            val visited = mutableSetOf<String>(); val visiting = mutableSetOf<String>()
            fun visit(id: String) {
                if(id in visited) return
                require(visiting.add(id)) { "quests/$id: cyclic prerequisites" }
                val q = byId[id] ?: error("quests/: prerequisite $id missing")
                q.prerequisites.forEach(::visit); visiting.remove(id); visited.add(id)
            }
            byId.keys.forEach(::visit)
        }
        fun validateMenu(file: String, y: YamlConfiguration) {
            val shape = y.getStringList("Shape")
            require(shape.size in setOf(3,6) && shape.all { it.length == 9 }) { "$file:Shape expected 3 or 6 rows of 9 chars" }
            shape.joinToString("").filter { it != ' ' }.toSet().forEach { ch ->
                require(y.isConfigurationSection("components.$ch")) { "$file:components.$ch missing mapping" }
                val action=y.getString("components.$ch.action","")
                require(action in setOf("","quest","summary","objectives","preview","claim","mailbox","back","previous","next","refresh")) { "$file:components.$ch.action=$action invalid" }
                require(y.isString("components.$ch.item")) { "$file:components.$ch.item missing" }
            }
            require(y.isString("title")) { "$file:title missing" }
            require(y.isString("unavailable-item")) { "$file:unavailable-item missing" }
        }
    }
}
