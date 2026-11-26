package ym.quest.config

import org.bukkit.configuration.file.YamlConfiguration
import java.nio.file.Files
import java.nio.file.Path

/** First chapter built-in filenames. Task identity and order still come from the task and chapter YAML. */
internal object QuestFileNumbering {
    private const val migration = "quest-file-numbering-v1"
    private const val chapter = QuestTaskFiles.firstChapter

    private data class Rename(val id: String, val oldName: String, val numberedName: String)

    private val entries = listOf(
        Rename("sky_start", "星海启航.yml", "主1-01 星海启航.yml"),
        Rename("place_generator", "放置生成器.yml", "主1-02 安放生成器.yml"),
        Rename("first_fuel", "能源启动.yml", "主1-03 能源启动.yml"),
        Rename("first_resources", "第一批资源.yml", "主1-04 第一批资源.yml"),
        Rename("first_harvest", "收获的喜悦.yml", "主1-05 收获的喜悦.yml"),
        Rename("upgrade_two", "机械革新.yml", "主1-06 机械革新.yml"),
        Rename("first_collector", "收集的力量.yml", "主1-07 物流的起点.yml"),
        Rename("first_binding", "机械的联系.yml", "主1-08 机械的联系.yml"),
        Rename("first_transfer", "自动化初体验.yml", "主1-09 自动化初体验.yml"),
        Rename("first_extract", "资源的归宿.yml", "主1-10 资源的归宿.yml"),
        Rename("logistics_supply", "支线/能源物流.yml", "支线/支1-01 能源物流.yml"),
        Rename("more_resources", "支线/更多的资源.yml", "支线/支1-02 更多的资源.yml"),
        Rename("free_production", "支线/自由的生产.yml", "支线/支1-03 自由的生产.yml"),
        Rename("first_trial", "支线/初次试炼.yml", "支线/支1-04 石址试炼.yml"),
        Rename("stone_site_materials", "支线/石址寻材.yml", "支线/支1-05 石址寻材.yml")
    )
    private val numberedPaths = entries.associate { "$chapter/${it.oldName}" to "$chapter/${it.numberedName}" }

    /** Resolves the original resource paths used by older migrations against the current JAR. */
    fun bundled(path: String): String = numberedPaths[path] ?: path

    fun recover(root: Path) {
        ConfigMigrationPlan(root, migration).resume()
    }

    fun migrate(root: Path) {
        val plan = ConfigMigrationPlan(root, migration)
        if (plan.resume() || Files.exists(root.resolve(".migrations/$migration/complete"))) return

        val changes = mutableListOf<ConfigMigrationPlan.Change>()
        for ((id, oldName, numberedName) in entries) {
            val old = "$chapter/$oldName"
            val source = root.resolve(old)
            if (!Files.isRegularFile(source)) continue
            val before = Files.readString(source)
            val task = YamlConfiguration().apply {
                try {
                    loadFromString(before)
                } catch (cause: Exception) {
                    throw IllegalArgumentException("$old: $migration could not parse YAML; original preserved", cause)
                }
            }
            if (task.getString("id") != id) continue
            require(task.get("schema-version") == 2) {
                "$old:schema-version=${task.get("schema-version")} unsupported for $migration; original preserved"
            }

            val next = "$chapter/$numberedName"
            require(!Files.exists(root.resolve(next))) {
                "$old -> $next: $migration conflict; both originals preserved"
            }
            changes += ConfigMigrationPlan.Change(next, null, before)
            changes += ConfigMigrationPlan.Change(old, before, null)
        }
        plan.apply(changes)
    }
}
