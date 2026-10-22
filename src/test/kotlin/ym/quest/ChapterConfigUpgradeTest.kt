package ym.quest

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ym.quest.config.*
import ym.quest.domain.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

class ChapterConfigUpgradeTest {
    @TempDir lateinit var root: Path
    // Historical v1/v2 fixtures deliberately retain the pre-placement graph.
    private val bundled get() = Files.readString(Path.of("src/main/resources/${ChapterConfigUpgrade.defaultFile}"))
        .replace("  - quest: place_generator\n    requires:\n      - sky_start\n", "")
        .replace("      - place_generator", "      - sky_start")
    private fun put(path: String, text: String) { val f=root.resolve(path); Files.createDirectories(f.parent); Files.writeString(f,text) }
    private fun read(path: String) = Files.readString(root.resolve(path))
    private fun yaml(text: String) = YamlConfiguration().apply { loadFromString(text) }
    private fun migrate() = ChapterConfigUpgrade.migrate(root,bundled)
    private fun tasks() {
        for((id,required) in listOf("sky_start" to null,"first_fuel" to "sky_start","first_resources" to "first_fuel")) {
            val original=Files.readString(Path.of("src/main/resources/quests/第一章/${ResourceUpgrade.questNames.getValue(id)}.yml"))
            put("quests/$id.yml",original.replace("schema-version: 2","schema-version: 1")+"\n# 自定义注释\nunknown: 保留\n"+
                (required?.let { "prerequisites: # 前置行尾注释\n  # 前置注释\n  - $it # 条目注释\n" } ?: ""))
        }
    }
    private val aggregate = """
        # 旧章节注释
        schema-version: 1
        unknown-root: 保留根字段
        chapters:
          - id: sky_awakening
            name: '<#123456>第一章 · 星海初醒'
            icon: minecraft:book
            order: 7
            sequential: true
            # 章节自定义注释
            unknown-chapter: 保留章节字段
            quests:
              - sky_start
              - first_fuel
              - first_resources
            rewards:
              - item: minecraft:emerald
                amount: 9
    """.trimIndent()+"\n"

    @Test fun `aggregate migration preserves content comments IDs and rewards and is idempotent`() {
        tasks();put("chapters/章节.yml",aggregate)
        val db=byteArrayOf(1,3,5,7);Files.createDirectories(root.resolve("data"));Files.write(root.resolve("data/quest.db"),db)
        migrate()
        val text=read(ChapterConfigUpgrade.defaultFile); val chapter=ChapterFiles.parse(yaml(text))
        assertEquals("sky_awakening",chapter.id);assertEquals(7,chapter.order)
        assertEquals(listOf(Reward("minecraft:emerald",9)),chapter.rewards)
        assertEquals(listOf("first_fuel"),chapter.taskRequirements["first_resources"])
        assertTrue(text.contains("unknown-root: 保留根字段"));assertTrue(text.contains("unknown-chapter: 保留章节字段"))
        assertTrue(text.contains("# 章节自定义注释"));assertTrue(text.contains("# 旧章节注释"))
        assertFalse(Files.exists(root.resolve("chapters/章节.yml")))
        val task=read("quests/first_resources.yml")
        assertFalse(yaml(task).contains("prerequisites"));assertEquals(2,yaml(task).getInt("schema-version"))
        assertTrue(task.contains("# 前置注释"));assertTrue(task.contains("unknown: 保留"))
        assertTrue(task.contains("# 前置行尾注释"));assertTrue(task.contains("# 条目注释"))
        assertEquals("genlite:basic_upgrade_unit",yaml(task).getMapList("rewards").single()["item"])
        migrate();assertEquals(text,read(ChapterConfigUpgrade.defaultFile));assertEquals(task,read("quests/first_resources.yml"))
        assertArrayEquals(db,Files.readAllBytes(root.resolve("data/quest.db")))
        val backups=Files.list(root.resolve(".migrations/chapter-v2")).use { it.toList() }
        assertTrue(backups.any { it.fileName.toString().endsWith(".before") && Files.readString(it) == aggregate })
    }

    @Test fun `original task only installation migrates prerequisites without requiring legacy chapter file`() {
        tasks()
        put("quests/sky_start.yml",read("quests/sky_start.yml").replace("\n","\r\n"))
        migrate()
        val ch=ChapterFiles.parse(yaml(read(ChapterConfigUpgrade.defaultFile)))
        assertEquals(listOf("sky_start"),ch.taskRequirements["first_fuel"])
        assertEquals(listOf("first_fuel"),ch.taskRequirements["first_resources"])
        assertEquals(3,ch.quests.size)
    }

    @Test fun `legacy aggregate filename cannot collide with a chapter display name`() {
        tasks();put("chapters/章节.yml",aggregate.replace("第一章 · 星海初醒","章节"));migrate()
        val chapter=read("chapters/章节-sky_awakening.yml")
        assertEquals("sky_awakening",ChapterFiles.parse(yaml(chapter)).id)
        assertFalse(Files.exists(root.resolve("chapters/章节.yml")))
    }

    @Test fun `new chapter conflicts and forbidden schema two task dependencies never modify original files`() {
        tasks();put(ChapterConfigUpgrade.defaultFile,bundled.replace("      - first_fuel","      - sky_start"))
        val original=read("quests/first_resources.yml")
        assertThrows(IllegalArgumentException::class.java) { migrate() }
        assertEquals(original,read("quests/first_resources.yml"))
        put(ChapterConfigUpgrade.defaultFile,bundled)
        put("quests/first_resources.yml",original.replace("schema-version: 1","schema-version: 2"))
        assertThrows(IllegalArgumentException::class.java) { migrate() }
        assertFalse(Files.exists(root.resolve(".migrations/chapter-v2")))
    }

    @Test fun `invalid references cycles and mixed new legacy conflicts leave active files intact`() {
        tasks()
        put("chapters/章节.yml",aggregate.replace("- sky_start","- missing"))
        val original=read("quests/first_fuel.yml")
        assertThrows(IllegalArgumentException::class.java) { migrate() }
        assertEquals(original,read("quests/first_fuel.yml"));assertFalse(Files.exists(root.resolve(".migrations/chapter-v2")))
        put("chapters/章节.yml",aggregate)
        put("quests/sky_start.yml",read("quests/sky_start.yml")+"prerequisites:\n  - first_resources\n")
        assertThrows(IllegalArgumentException::class.java) { migrate() }
        put(ChapterConfigUpgrade.defaultFile,bundled)
        assertThrows(IllegalArgumentException::class.java) { migrate() }
        assertEquals(aggregate,read("chapters/章节.yml"));assertEquals(bundled,read(ChapterConfigUpgrade.defaultFile))
    }

    @Test fun `durable journal resumes after partial apply retains predecessors and rejects concurrent edits`() {
        tasks();put("chapters/章节.yml",aggregate);migrate()
        val plan=root.resolve(".migrations/chapter-v2")
        val manifest=Properties().apply { Files.newInputStream(plan.resolve("plan.properties")).use { load(it) } }
        // Simulate a crash after chapter writes, before task cleanup and aggregate retirement.
        repeat(manifest.getProperty("count").toInt()) { i ->
            val path=manifest.getProperty("$i.path")
            if(path.startsWith("quests/") || path == "chapters/章节.yml") put(path,Files.readString(plan.resolve("$i.before")))
        }
        Files.delete(plan.resolve("complete"))
        val task=read("quests/first_fuel.yml");put("quests/first_fuel.yml",task+"user-change: true\n")
        assertThrows(IllegalArgumentException::class.java) { migrate() }
        assertTrue(read("quests/first_fuel.yml").contains("user-change"));assertTrue(Files.exists(root.resolve("chapters/章节.yml")))
        put("quests/first_fuel.yml",task);migrate();migrate()
        val ch=ChapterFiles.parse(yaml(read(ChapterConfigUpgrade.defaultFile)))
        assertEquals(listOf("sky_start"),ch.taskRequirements["first_fuel"])
        assertFalse(yaml(read("quests/first_fuel.yml")).contains("prerequisites"))
        assertTrue(Files.exists(plan.resolve("complete")))
    }

    @Test fun `chapter parser rejects legacy ambiguous duplicate and malformed relations`() {
        for(edit in listOf<(YamlConfiguration)->Unit>(
            { it.set("sequential",true) }, { it.set("quests",listOf("sky_start")) },
            { it.set("tasks",listOf(mapOf("quest" to "sky_start"),mapOf("quest" to "sky_start"))) },
            { it.set("tasks",listOf(mapOf("quest" to "sky_start","requires" to "bad"))) },
            { it.set("tasks",listOf(mapOf("quest" to "sky_start","next" to "first_fuel"))) })) {
            val y=yaml(bundled);edit(y)
            assertThrows(IllegalArgumentException::class.java) { ChapterFiles.parse(y,"chapters/测试.yml") }
        }
    }

    @Test fun `multiple chapter files load independently and display order does not create prerequisites`() {
        val a=ChapterFiles.parse(yaml(bundled)).copy(quests=listOf("first_resources","first_fuel","sky_start"),
            taskRequirements=mapOf("first_fuel" to listOf("sky_start"),"first_resources" to listOf("sky_start","first_fuel")))
        val q=a.quests.map { QuestDefinition(it,it,emptyList(),"minecraft:book",emptyList(),emptyList(),emptyList()) }
        assertEquals(listOf("sky_start","first_fuel","first_resources"),ChapterGraph.prepare(q,listOf(a)).map { it.id })
        assertEquals(listOf("first_resources","first_fuel","sky_start"),a.quests)
        val second=bundled.replace("id: sky_awakening","id: second").substringBefore("tasks:")+"tasks:\n  - quest: other\nprerequisites:\n  - sky_awakening\n"
        put(ChapterConfigUpgrade.defaultFile,bundled);put("chapters/第二章.yml",second)
        val chapters=ChapterConfigUpgrade.files(root.resolve("chapters")).map { ChapterFiles.parse(yaml(Files.readString(it)),it.toString()) }
        assertEquals(2,chapters.size)
        ChapterGraph.prepare(q+q.first().copy(id="other"),chapters)
        assertThrows(IllegalArgumentException::class.java) { ChapterGraph.prepare(q+q.first().copy(id="other"),listOf(a,chapters.last().copy(taskRequirements=mapOf("other" to listOf("sky_start"))))) }
    }
}
