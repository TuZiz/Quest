package ym.quest.config

import org.bukkit.configuration.file.YamlConfiguration
import java.nio.file.Files
import java.nio.file.Path

/** IO-only journalled rollout. IDs, rewards, unknown fields and SQLite progress remain intact. */
object LogisticsTutorialUpgrade {
    private val resources=mapOf(
        "first_collector" to "quests/第一章/收集的力量.yml",
        "first_binding" to "quests/第一章/机械的联系.yml",
        "first_transfer" to "quests/第一章/自动化初体验.yml",
        "first_extract" to "quests/第一章/资源的归宿.yml",
        "free_production" to "quests/第一章/支线/自由的生产.yml",
        "logistics_supply" to "quests/第一章/支线/能源物流.yml"
    )
    const val supplyResource="quests/第一章/支线/能源物流.yml"
    fun migrate(root: Path,resource: (String)->String) {
        migrateRevision(root,resource,"logistics-core-v1")
        migrateRevision(root,resource,"manual-logistics-binding-v1")
        migrateRevision(root,resource,"shared-core-energy-v1")
    }
    private fun migrateRevision(root: Path,resource: (String)->String,revision: String) {
        val plan=ConfigMigrationPlan(root,revision)
        if(plan.resume() || Files.exists(root.resolve(".migrations/$revision/complete"))) return
        val changes=mutableListOf<ConfigMigrationPlan.Change>()
        QuestTaskFiles.files(root.resolve("quests")).forEach { path ->
            val before=Files.readString(path)
            val y=YamlConfiguration().apply {loadFromString(before)}
            val source=resources[y.getString("id")] ?: return@forEach
            val bundled=resource(source)
            var next=before
            for(key in listOf("name","icon","description","objectives")) {
                val replacement=block(bundled,key) ?: error("$source:$key missing")
                val original=block(next,key)
                if(original==null) next+="\n$replacement"
                else {
                    val comments=original.lineSequence().filter {it.trimStart().startsWith("#")}.joinToString("\n")
                    next=next.replaceRange(next.indexOf(original),next.indexOf(original)+original.length,
                        (if(comments.isEmpty()) "" else "$comments\n")+replacement)
                }
            }
            if(next!=before) changes+=ConfigMigrationPlan.Change(root.relativize(path).toString().replace('\\','/'),before,next)
        }
        val existingSupply=QuestTaskFiles.files(root.resolve("quests")).any { path ->
            YamlConfiguration().apply {load(path.toFile())}.getString("id")=="logistics_supply"
        }
        if(!existingSupply) {
            require(!Files.exists(root.resolve(supplyResource))) {"$supplyResource already exists with another ID; original preserved"}
            changes+=ConfigMigrationPlan.Change(supplyResource,null,resource(supplyResource))
        }
        ChapterConfigUpgrade.files(root.resolve("chapters")).forEach { path ->
            val before=Files.readString(path);val y=YamlConfiguration().apply {loadFromString(before)}
            if(y.getString("id")!="sky_awakening") return@forEach
            var next=before.replace("生成器与收集器","生成器与物流核心")
            if(y.getMapList("tasks").none {it["quest"]=="logistics_supply"}) {
                val original=block(next,"tasks") ?: error("$path:tasks missing")
                val addition="  - quest: 'logistics_supply'\n    requires:\n      - 'first_extract'\n      - 'stone_site_materials'\n    optional: true\n\n"
                val index=next.indexOf(original)
                next=next.replaceRange(index,index+original.length,original.trimEnd()+"\n"+addition)
            }
            ChapterFiles.parse(YamlConfiguration().apply {loadFromString(next)},path.toString())
            if(next!=before) changes+=ConfigMigrationPlan.Change(root.relativize(path).toString().replace('\\','/'),before,next)
        }
        plan.apply(changes)
    }
    private fun block(raw: String,key: String): String? {
        val start=Regex("(?m)^${Regex.escape(key)}:").find(raw)?.range?.first ?: return null
        val end=Regex("(?m)^[^\\s#][^\\r\\n]*:").find(raw,start+key.length+1)?.range?.first ?: raw.length
        return raw.substring(start,end)
    }
}
