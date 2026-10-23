package ym.quest.config

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import ym.quest.domain.*
import ym.quest.tab.TabSettings
import ym.quest.tab.TabQuestProjection
import ym.quest.introduction.IntroductionSettings
import ym.quest.hud.HudSettings
import java.nio.file.Files

data class Settings(val quests: List<QuestDefinition>, val language: YamlConfiguration,
                    val chapters: List<ChapterDefinition>,
                    val notices: Map<String,NoticeProfile>, val rewards: RewardSettings, val tab: TabSettings,
                    val introduction: IntroductionSettings = IntroductionSettings(), val hud: HudSettings = HudSettings(),
                    val journalMenus: Map<String,YamlConfiguration> = emptyMap(),
                    val tutorialWorlds: Set<String> = setOf("world"))

/** IO executor only. No save of existing YAML: comments/unknown fields remain byte-for-byte intact. */
class QuestFiles(private val plugin: JavaPlugin, private val conditions: ConditionRegistry) {
    private val defaults = listOf("config.yml","lang/zh_cn.yml",
        "quests/星海启航.yml","quests/能源启动.yml","quests/第一批资源.yml",
        "提示.yml","奖励.yml","gui/chapters.yml","tab.yml","引导.yml",
        "gui/tasks.yml","gui/settings.yml")
    fun load(): Settings {
        QuestTaskFiles.recover(plugin.dataFolder.toPath())
        NoticeConfigUpgrade.migrate(plugin.dataFolder.toPath())
        ResourceUpgrade.relocateLayout(plugin.dataFolder.toPath())
        MenuConfigUpgrade.migrate(plugin.dataFolder.toPath(),listOf("chapters","tasks","settings").associateWith { name ->
            checkNotNull(plugin.getResource("gui/$name.yml")).bufferedReader(Charsets.UTF_8).use { it.readText() }
        })
        ResourceUpgrade.renameQuests(plugin.dataFolder.toPath())
        ResourceUpgrade.updateProgressionText(plugin.dataFolder.toPath())
        val chapterDefault = checkNotNull(plugin.getResource(ChapterConfigUpgrade.defaultFile)).bufferedReader(Charsets.UTF_8).use { it.readText() }
        ChapterConfigUpgrade.migrate(plugin.dataFolder.toPath(),chapterDefault)
        defaults.forEach { name ->
            val file = plugin.dataFolder.toPath().resolve(name)
            val existingTask=name.startsWith("quests/") && QuestTaskFiles.files(plugin.dataFolder.toPath().resolve("quests")).any { it.fileName==file.fileName }
            if(!Files.exists(file) && !existingTask) {
                Files.createDirectories(file.parent)
                checkNotNull(plugin.getResource(QuestTaskFiles.bundled(name))) { "Missing bundled $name" }.use { Files.copy(it,file) }
            }
        }
        if(ChapterConfigUpgrade.files(plugin.dataFolder.toPath().resolve("chapters")).isEmpty()) {
            val file = plugin.dataFolder.toPath().resolve(ChapterConfigUpgrade.defaultFile)
            Files.createDirectories(file.parent); Files.writeString(file,chapterDefault,java.nio.file.StandardOpenOption.CREATE_NEW)
        }
        GeneratorPlacementUpgrade.migrate(plugin.dataFolder.toPath(),
            checkNotNull(plugin.getResource(QuestTaskFiles.bundled("quests/放置生成器.yml"))).bufferedReader(Charsets.UTF_8).use { it.readText() })
        FuelTaskTextUpgrade.migrate(plugin.dataFolder.toPath())
        TrackingHudTextUpgrade.migrate(plugin.dataFolder.toPath())
        CategoryButtonUpgrade.migrate(plugin.dataFolder.toPath(),checkNotNull(plugin.getResource("gui/chapters.yml")).bufferedReader(Charsets.UTF_8).use { it.readText() })
        QuestTaskFiles.groupFirstChapter(plugin.dataFolder.toPath())
        TutorialUpgrade.migrate(plugin.dataFolder.toPath()) { path ->
            checkNotNull(plugin.getResource(path)) { "Missing tutorial resource $path" }.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
        ResourceUpgrade.appendLanguage(plugin.dataFolder.toPath().resolve("lang/zh_cn.yml"),
            checkNotNull(plugin.getResource("lang/zh_cn.yml")).bufferedReader(Charsets.UTF_8).use { it.readText() })
        SidebarTextUpgrade.migrate(plugin.dataFolder.toPath())
        fun yaml(name: String): YamlConfiguration {
            val text = Files.readString(plugin.dataFolder.toPath().resolve(name))
            return YamlConfiguration().apply {
                try { loadFromString(text) } catch(e: Exception) { throw IllegalArgumentException("$name: YAML解析失败",e) }
                val expected = if(name.startsWith("quests/") || name.startsWith("chapters/") || name in setOf("gui/chapters.yml","提示.yml")) 2 else 1
                require(get("schema-version") == expected) { "$name:schema-version=${get("schema-version")} expected $expected; original preserved" }
            }
        }
        val config = yaml("config.yml")
        val rewards = RewardSettings.parse(yaml("奖励.yml"))
        val tab = TabSettings.parse(yaml("tab.yml"))
        val introduction = IntroductionSettings.parse(yaml("引导.yml"))
        if(rewards.automatic) RewardTextUpgrade.migrate(plugin.dataFolder.toPath().resolve("lang/zh_cn.yml"))
        require(config.getString("storage") == "data/quest.db") { "config.yml:storage=${config.get("storage")} unsupported" }
        val quests = QuestTaskFiles.files(plugin.dataFolder.toPath().resolve("quests")).map { path ->
                val name = plugin.dataFolder.toPath().relativize(path).toString().replace('\\','/')
                parse(name,yaml(name))
        }
        validateGraph(quests)
        val language = yaml("lang/zh_cn.yml")
        requiredMessages.forEach { require(language.isString(it)) { "lang/zh_cn.yml:$it missing" } }
        TabQuestProjection.requiredMessages.forEach { require(language.isString(it)) { "lang/zh_cn.yml:$it missing" } }
        val chapters = ChapterConfigUpgrade.files(plugin.dataFolder.toPath().resolve("chapters")).map { path ->
            val name = "chapters/${path.fileName}"
            ChapterFiles.parse(yaml(name),name)
        }
        val ordered = ChapterGraph.prepare(quests,chapters)
        if(introduction.enabled) {
            require(chapters.any { it.id == introduction.chapterId }) { "引导.yml:chapter-id=${introduction.chapterId} missing chapter" }
            require(quests.none { it.id == introduction.taskId }) { "引导.yml:task-id=${introduction.taskId} conflicts with shared quest" }
        }
        listOf("task-name","objective","confirm","complete","fallback-confirm","interrupted","no-dialogue","help","npc-unavailable","actionbar").forEach {
            require(language.isString("introduction.$it")) { "lang/zh_cn.yml:introduction.$it missing" }
        }
        listOf("dialogue","hints").forEach { key ->
            val value = language.get("introduction.$key")
            require(value is List<*> && value.isNotEmpty() && value.all { it is String && it.isNotBlank() }) { "lang/zh_cn.yml:introduction.$key expected nonempty text list" }
        }
        val notices = NoticeFiles.parse(yaml("提示.yml"))
        NoticeFiles.events.forEach { event -> listOf("message","title","subtitle").forEach { field ->
            require(language.isString("notices.$event.$field")) { "lang/zh_cn.yml:notices.$event.$field missing" }
        } }
        ChapterStatus.entries.forEach { require(language.isString("chapter-status.$it")) { "lang/zh_cn.yml:chapter-status.$it missing" } }
        val hud=HudSettings.load(plugin.dataFolder.toPath().parent.resolve("Xerr/quest.yml"))
        listOf("personal","shared","task-complete","chapter-complete","task-detail","chapter-detail","next","help").forEach {
            require(language.isString("hud.$it")) { "lang/zh_cn.yml:hud.$it missing" }
        }
        val journalMenus=listOf("chapters","tasks","settings").associateWith { id ->
            yaml("gui/$id.yml").also {
                validateMenu("gui/$id.yml",it)
                if(id!="settings") require(it.isString("reward-format")) { "gui/$id.yml:reward-format missing" }
            }
        }
        listOf("loading","empty","browse","no-selection","tracked","not-tracked","track-unavailable","saved","enabled","disabled","mode-ALWAYS","mode-OFF","category-MAIN","category-SIDE","category-SPECIAL").forEach {
            require(language.isString("journal.$it")) { "lang/zh_cn.yml:journal.$it missing" }
        }
        val tutorial=yaml("教学.yml")
        require(tutorial.getInt("schema-version")==1 && tutorial.getList("combat.allowed-worlds")?.all { it is String && it.isNotBlank() }==true) { "教学.yml:combat.allowed-worlds expected explicit world list; schema-version must be 1" }
        return Settings(ordered,language,chapters,notices,rewards,tab,introduction,hud,journalMenus,tutorial.getStringList("combat.allowed-worlds").toSet())
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
            val previous = s.getString("unless-rewarded-by")
            require(previous == null || previous.matches(Regex("[a-z0-9_-]+"))) { "$file:rewards.unless-rewarded-by=$previous invalid task ID" }
            Reward(text(s,"item"),amount,s.getBoolean("unless-generator-exists"),previous)
        }
        val category=y.getString("category","main")!!
        require(category in setOf("main","side","special")) { "$file:category=$category expected main/side/special" }
        require(!y.contains("unlock-when") || y.isConfigurationSection("unlock-when")) { "$file:unlock-when expected metric mapping" }
        val unlock=y.getConfigurationSection("unlock-when")?.getValues(false).orEmpty().mapValues { (metric,value) ->
            require((value is Int || value is Long) && (value as Number).toLong()>0) { "$file:unlock-when.$metric=$value expected positive integer" }
            (value as Number).toLong()
        }
        return QuestDefinition(id,text(y,"name"),y.getStringList("description"),text(y,"icon"),emptyList(),objectives,rewards,QuestCategory.valueOf(category.uppercase()),unlock)
    }
    companion object {
        val requiredMessages = listOf("no-permission","unavailable","no-island","loading","error","busy","claimed","not-complete","inventory-full","uncertain","delivered","progress","complete","reloaded","reset","usage","debug","resolved","player-only","reward-line","objective-line","status.LOCKED","status.ACTIVE","status.COMPLETE","status.CLAIMED","chapter-progress","chapter-reward-status") +
            listOf("command-help.player","command-help.admin")
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
            if(y.contains("empty-slots")) {
                val filler=y.getString("empty-slots")
                require(filler?.length==1 && y.isString("components.$filler.item") &&
                    y.getString("components.$filler.action","").isNullOrBlank()) { "$file:empty-slots=$filler expected a mapped decoration without an action" }
            }
            val shape = y.getStringList("Shape")
            require(shape.size in 1..6 && shape.all { it.length == 9 }) { "$file:Shape expected 1..6 rows of 9 chars" }
            shape.joinToString("").filter { it != ' ' }.toSet().forEach { ch ->
                require(y.isConfigurationSection("components.$ch")) { "$file:components.$ch missing mapping" }
                val action=y.getString("components.$ch.action","")
                require(action in setOf("","back","previous","next","refresh","close",
                    "category-cycle","category-main","category-side","category-special","settings",
                    "toggle-hud","toggle-auto","toggle-notices","toggle-sound","home","journal-quest","journal-chapter")) { "$file:components.$ch.action=$action invalid" }
                require(y.isString("components.$ch.item")) { "$file:components.$ch.item missing" }
                if(y.contains("components.$ch.pack-item")) require(y.isString("components.$ch.pack-item") &&
                    !y.getString("components.$ch.pack-item").isNullOrBlank()) { "$file:components.$ch.pack-item expected a nonempty item ID" }
                if(y.contains("components.$ch.pack-only")) require(y.get("components.$ch.pack-only") is Boolean) { "$file:components.$ch.pack-only expected boolean" }
                if(y.contains("components.$ch.item-on") || y.contains("components.$ch.item-off")) {
                    require(action in setOf("toggle-hud","toggle-notices","toggle-sound") &&
                        listOf("item-on","item-off").all { !y.getString("components.$ch.$it").isNullOrBlank() }) { "$file:components.$ch.item-on/item-off expected a pair on a supported toggle" }
                }
            }
            require(y.isString("title")) { "$file:title missing" }
            require(y.isString("unavailable-item")) { "$file:unavailable-item missing" }
        }
    }
}
