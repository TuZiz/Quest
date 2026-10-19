package ym.quest.config

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import ym.quest.domain.*
import java.nio.file.Files

data class Settings(val quests: List<QuestDefinition>, val language: YamlConfiguration,
                    val center: YamlConfiguration,
                    val chapters: List<ChapterDefinition>, val chapterMenu: YamlConfiguration,
                    val notices: Map<String,NoticeProfile>, val hud: YamlConfiguration, val rewards: RewardSettings)

/** IO executor only. No save of existing YAML: comments/unknown fields remain byte-for-byte intact. */
class QuestFiles(private val plugin: JavaPlugin, private val conditions: ConditionRegistry) {
    private val defaults = listOf("config.yml","lang/zh_cn.yml","gui/main.yml",
        "quests/星海启航.yml","quests/能源启动.yml","quests/第一批资源.yml",
        "提示.yml","显示.yml","奖励.yml","gui/chapters.yml")
    fun load(): Settings {
        HudConfigUpgrade.migrate(plugin.dataFolder.toPath(),checkNotNull(plugin.getResource("显示.yml")).bufferedReader(Charsets.UTF_8).use { it.readText() })
        ResourceUpgrade.relocateLayout(plugin.dataFolder.toPath())
        GuiConfigUpgrade.migrate(plugin.dataFolder.toPath(),checkNotNull(plugin.getResource("gui/main.yml")).bufferedReader(Charsets.UTF_8).use { it.readText() })
        ResourceUpgrade.renameQuests(plugin.dataFolder.toPath())
        ResourceUpgrade.updateProgressionText(plugin.dataFolder.toPath())
        val chapterDefault = checkNotNull(plugin.getResource(ChapterConfigUpgrade.defaultFile)).bufferedReader(Charsets.UTF_8).use { it.readText() }
        ChapterConfigUpgrade.migrate(plugin.dataFolder.toPath(),chapterDefault)
        defaults.forEach { name ->
            val file = plugin.dataFolder.toPath().resolve(name)
            if(!Files.exists(file)) {
                Files.createDirectories(file.parent)
                checkNotNull(plugin.getResource(name)) { "Missing bundled $name" }.use { Files.copy(it,file) }
            }
        }
        if(ChapterConfigUpgrade.files(plugin.dataFolder.toPath().resolve("chapters")).isEmpty()) {
            val file = plugin.dataFolder.toPath().resolve(ChapterConfigUpgrade.defaultFile)
            Files.createDirectories(file.parent); Files.writeString(file,chapterDefault,java.nio.file.StandardOpenOption.CREATE_NEW)
        }
        ResourceUpgrade.appendLanguage(plugin.dataFolder.toPath().resolve("lang/zh_cn.yml"),
            checkNotNull(plugin.getResource("lang/zh_cn.yml")).bufferedReader(Charsets.UTF_8).use { it.readText() })
        fun yaml(name: String): YamlConfiguration {
            val text = Files.readString(plugin.dataFolder.toPath().resolve(name))
            return YamlConfiguration().apply {
                try { loadFromString(text) } catch(e: Exception) { throw IllegalArgumentException("$name: YAML解析失败",e) }
                val expected = if(name.startsWith("quests/") || name.startsWith("chapters/") || name in setOf("gui/main.yml","显示.yml")) 2 else 1
                require(get("schema-version") == expected) { "$name:schema-version=${get("schema-version")} expected $expected; original preserved" }
            }
        }
        val config = yaml("config.yml")
        val rewards = RewardSettings.parse(yaml("奖励.yml"))
        if(rewards.automatic) RewardTextUpgrade.migrate(plugin.dataFolder.toPath().resolve("lang/zh_cn.yml"))
        require(config.getString("storage") == "data/quest.db") { "config.yml:storage=${config.get("storage")} unsupported" }
        val quests = Files.list(plugin.dataFolder.toPath().resolve("quests")).use { paths ->
            paths.filter { it.fileName.toString().endsWith(".yml") }.sorted().map { path ->
                val name = "quests/${path.fileName}"
                parse(name,yaml(name))
            }.toList()
        }
        validateGraph(quests)
        val language = yaml("lang/zh_cn.yml")
        listOf("task","objective").forEach { require(language.isString("hud-completed.$it")) { "lang/zh_cn.yml:hud-completed.$it missing" } }
        requiredMessages.forEach { require(language.isString(it)) { "lang/zh_cn.yml:$it missing" } }
        val center = yaml("gui/main.yml")
        validateMenu("gui/main.yml",center)
        require(center.isString("reward-format")) { "gui/main.yml:reward-format missing" }
        val chapters = ChapterConfigUpgrade.files(plugin.dataFolder.toPath().resolve("chapters")).map { path ->
            val name = "chapters/${path.fileName}"
            ChapterFiles.parse(yaml(name),name)
        }
        val ordered = ChapterGraph.prepare(quests,chapters)
        val chapterMenu = yaml("gui/chapters.yml")
        validateMenu("gui/chapters.yml",chapterMenu)
        val notices = NoticeFiles.parse(yaml("提示.yml"))
        NoticeFiles.events.forEach { event -> listOf("message","title","subtitle").forEach { field ->
            require(language.isString("notices.$event.$field")) { "lang/zh_cn.yml:notices.$event.$field missing" }
        } }
        ChapterStatus.entries.forEach { require(language.isString("chapter-status.$it")) { "lang/zh_cn.yml:chapter-status.$it missing" } }
        val hud = yaml("显示.yml")
        ym.quest.hud.HudSettings.parse(hud)
        listOf("enabled","tracker","notifications").forEach { require(hud.isBoolean(it)) { "显示.yml:$it=${hud.get(it)} expected boolean" } }
        require(hud.isInt("refresh-ticks") && hud.getInt("refresh-ticks") in 20..1200) { "显示.yml:refresh-ticks=${hud.get("refresh-ticks")} expected 20..1200" }
        require(!hud.getString("tracker-layout").isNullOrBlank()) { "显示.yml:tracker-layout missing" }
        return Settings(ordered,language,center,chapters,chapterMenu,notices,hud,rewards)
    }
    private fun parse(file: String, y: YamlConfiguration): QuestDefinition {
        fun text(s: ConfigurationSection, key: String) = s.getString(key)?.takeIf { it.isNotBlank() }
            ?: error("$file:$key=${s.get(key)} expected nonblank string")
        val id = text(y,"id")
        require(id.matches(Regex("[a-z0-9_-]+"))) { "$file:id=$id invalid" }
        require(y.getString("scope") == "island") { "$file:scope=${y.get("scope")} only island is implemented" }
        require(!y.contains("prerequisites")) { "$file:prerequisites forbidden in schema 2; configure chapters/*.yml tasks.requires" }
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
        return QuestDefinition(id,text(y,"name"),y.getStringList("description"),text(y,"icon"),emptyList(),objectives,rewards)
    }
    companion object {
        val requiredMessages = listOf("no-permission","unavailable","no-island","loading","error","busy","claimed","not-complete","inventory-full","uncertain","delivered","progress","complete","reloaded","reset","usage","debug","resolved","player-only","reward-line","objective-line","status.LOCKED","status.ACTIVE","status.COMPLETE","status.CLAIMED","chapter-progress","chapter-reward-status")
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
            require(shape.size in 1..6 && shape.all { it.length == 9 }) { "$file:Shape expected 1..6 rows of 9 chars" }
            shape.joinToString("").filter { it != ' ' }.toSet().forEach { ch ->
                require(y.isConfigurationSection("components.$ch")) { "$file:components.$ch missing mapping" }
                val action=y.getString("components.$ch.action","")
                require(action in setOf("","quest","back","previous","next","refresh","close","chapter","chapter-summary","chapter-claim")) { "$file:components.$ch.action=$action invalid" }
                require(y.isString("components.$ch.item")) { "$file:components.$ch.item missing" }
            }
            require(y.isString("title")) { "$file:title missing" }
            require(y.isString("unavailable-item")) { "$file:unavailable-item missing" }
        }
    }
}
