package ym.quest.config

import java.nio.file.Files
import java.nio.file.Path

/** Task definitions may be grouped in subdirectories; IDs remain independent of file paths. */
object QuestTaskFiles {
    const val firstChapter="quests/第一章"
    fun recover(root: Path) { ConfigMigrationPlan(root,"quest-folders-v1").resume() }
    fun files(directory: Path): List<Path> = if(!Files.isDirectory(directory)) emptyList() else Files.walk(directory).use { paths ->
        paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".yml") }.sorted().toList()
    }
    fun bundled(path: String)=if(path.startsWith("quests/")) "$firstChapter/${path.substringAfterLast('/')}" else path
    fun groupFirstChapter(root: Path) {
        val plan=ConfigMigrationPlan(root,"quest-folders-v1")
        if(plan.resume() || Files.exists(root.resolve(".migrations/quest-folders-v1/complete"))) return
        val changes=mutableListOf<ConfigMigrationPlan.Change>()
        listOf("星海启航","放置生成器","能源启动","第一批资源").forEach { name ->
            val old="quests/$name.yml"
            if(Files.exists(root.resolve(old))) {
                val next="$firstChapter/$name.yml"
                require(!Files.exists(root.resolve(next))) { "$old -> $next: quest-folders-v1 conflict; both originals preserved" }
                val raw=Files.readString(root.resolve(old))
                changes+=ConfigMigrationPlan.Change(next,null,raw)
                changes+=ConfigMigrationPlan.Change(old,raw,null)
            }
        }
        plan.apply(changes)
    }
}
