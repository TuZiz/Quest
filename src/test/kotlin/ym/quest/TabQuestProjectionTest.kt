package ym.quest

import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ym.quest.config.ResourceUpgrade
import ym.quest.config.ChapterFiles
import ym.quest.domain.*
import ym.quest.storage.Database
import ym.quest.tab.TabQuestProjection
import ym.quest.tab.TabSettings
import ym.quest.text.TextRenderer
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

class TabQuestProjectionTest {
    @TempDir lateinit var directory: Path
    private val language = YamlConfiguration().apply { loadFromString(Files.readString(Path.of("src/main/resources/lang/zh_cn.yml"))) }
    private val projection = TabQuestProjection(TextRenderer(language), 28)
    private fun plain(lines: List<String>) = lines.joinToString("\n") {
        PlainTextComponentSerializer.plainText().serialize(MiniMessage.miniMessage().deserialize(it))
    }
    private fun quest(id: String, target: Long = 32, requires: List<String> = emptyList()) = QuestDefinition(id,
        "<#78FFD6>利用原石生成器获取32个原石",listOf("<#E0F7FA>让生成器成功生产32份原石"),"minecraft:cobblestone",requires,
        listOf(Objective("produce","genlite.produce",target,"minecraft:cobblestone","<#E0F7FA>生成器实际生产原石")),emptyList())

    @Test fun `fuel task retains compact objective above the independent side section`() {
        val fuel = YamlConfiguration().apply { loadFromString(Files.readString(Path.of("src/main/resources/quests/第一章/能源启动.yml"))) }
        val objective = fuel.getMapList("objectives").single()
        val q = QuestDefinition(fuel.getString("id")!!, fuel.getString("name")!!, fuel.getStringList("description"),
            fuel.getString("icon")!!, emptyList(), listOf(Objective(objective["id"].toString(), objective["type"].toString(),
                (objective["target"] as Number).toLong(), null, objective["description"].toString())), emptyList())
        val chapter = ChapterFiles.parse(YamlConfiguration().apply {
            loadFromString(Files.readString(Path.of("src/main/resources/chapters/第一章·星海初醒.yml")))
        })
        val lines = projection.project(JournalView(listOf(QuestView(q, QuestStatus.ACTIVE,
            listOf(ObjectiveProgress(q.objectives.single(), 0)))), listOf(ChapterView(chapter, ChapterStatus.ACTIVE, 0, false))))
        assertEquals(listOf("✦ 主线任务","  第一章·星海初醒","▪ 能源启动 0/1"),plain(lines.take(3)).split('\n'))
        val main=plain(lines.take(8))
        assertTrue(main.contains("0/1"))
        assertTrue(main.replace("\n","").replace(" ","").contains(PlainTextComponentSerializer.plainText().serialize(MiniMessage.miniMessage().deserialize(objective["description"].toString())).replace(" ","")))
        assertTrue(plain(listOf(lines[3])).startsWith("  目标："))
        assertTrue(plain(listOf(lines[4])).startsWith("  "))
        assertEquals("",lines[8])
        assertEquals("✦ 支线任务",plain(listOf(lines[9])))
        assertEquals("▪ 暂无进行中的支线任务。",plain(listOf(lines[10])))
        assertTrue(lines.drop(11).all { it.isEmpty() })
        val mini = MiniMessage.miniMessage()
        val title = mini.deserialize(lines[2])
        val gson = net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson()
        val json = lines.take(12).map { gson.serialize(mini.deserialize(it)) }
        val output = Path.of("target/verification")
        Files.createDirectories(output)
        Files.writeString(output.resolve("tab-compact-preview.txt"), plain(lines.take(12)))
        Files.writeString(output.resolve("tab-emphasis-components.json"), json.joinToString(",", "[", "]"))
        assertTrue(gson.serialize(title).contains("#C2E9FB", ignoreCase=true))
    }

    @Test fun `active objective progress and long Chinese names wrap without losing text`() {
        val q = quest("resources")
        val chapter = ChapterDefinition("chapter","<#FFD700>第一章·星海初醒","minecraft:book",emptyList(),1,emptyList(),listOf(q.id),emptyList())
        val lines = projection.project(JournalView(listOf(QuestView(q,QuestStatus.ACTIVE,listOf(ObjectiveProgress(q.objectives.single(),8)))),
            listOf(ChapterView(chapter,ChapterStatus.ACTIVE,0,false))))
        assertEquals(18,lines.size)
        val visible = plain(lines)
        assertTrue(visible.contains("8/32"),visible)
        assertTrue(visible.replace("\n","").replace(" ","").contains("利用原石生成器获取32个原石"),visible)
        assertTrue(visible.contains("第一章·星海初醒"),visible)
        assertTrue(visible.contains("生成器实际生产原石"),visible)
        assertFalse(visible.contains("空岛成员共享任务进度"),visible)
        assertFalse(visible.contains("<#"))
    }

    @Test fun `first unfinished objective wins and next active task replaces completed task`() {
        val first=quest("one")
        val second=quest("two").copy(name="下一任务",objectives=listOf(Objective("a","genlite.fuel",1,null,"第一个目标"),Objective("b","genlite.fuel",2,null,"第二个目标")))
        val lines=projection.project(JournalView(listOf(QuestView(first,QuestStatus.COMPLETE,emptyList()),QuestView(second,QuestStatus.ACTIVE,
            listOf(ObjectiveProgress(second.objectives[0],1),ObjectiveProgress(second.objectives[1],0)))),emptyList()))
        val visible=plain(lines)
        assertTrue(visible.contains("下一任务") && visible.contains("第二个目标"),visible)
        assertTrue(visible.contains("0/2") && visible.contains("已达成  1 / 2"),visible)
        assertFalse(visible.contains("利用原石生成器"))
    }

    @Test fun `custom task name without counts retains separate progress`() {
        val custom=YamlConfiguration().apply {
            loadFromString(Files.readString(Path.of("src/main/resources/lang/zh_cn.yml")))
            set("tab.quest-line", "<#C2E9FB>▪ <quest>")
        }
        val q=quest("custom").copy(name="自定义任务")
        val lines=TabQuestProjection(TextRenderer(custom),28).project(JournalView(
            listOf(QuestView(q,QuestStatus.ACTIVE,listOf(ObjectiveProgress(q.objectives.single(),8)))),emptyList()))
        assertTrue(plain(lines).contains("▪ 自定义任务"))
        assertTrue(plain(lines).contains("进度  8 / 32"))
    }

    @Test fun `done locked no island and unavailable states contain no placeholder leaks`() {
        val q=quest("one")
        assertTrue(plain(projection.project(JournalView(listOf(QuestView(q,QuestStatus.CLAIMED,emptyList())),emptyList()))).contains("当前主线章节已全部完成"))
        assertTrue(plain(projection.project(JournalView(listOf(QuestView(q,QuestStatus.LOCKED,emptyList())),emptyList()))).contains("等待"))
        for(state in listOf("loading","no-island","no-permission","unavailable","error")) {
            val lines=projection.state(state)
            assertEquals(18,lines.size)
            assertFalse(plain(lines).contains("<value>"))
        }
    }

    @Test fun `side tasks never replace main area and all active branches have their own lower section`() {
        fun entry(id: String,category: QuestCategory,status: QuestStatus)=quest(id,3).copy(name=id,category=category).let {
            QuestView(it,status,listOf(ObjectiveProgress(it.objectives.single(),1)))
        }
        val side=(1..4).map { entry("支线$it",QuestCategory.SIDE,QuestStatus.ACTIVE) }
        val main=entry("主线目标",QuestCategory.MAIN,QuestStatus.ACTIVE)
        val view=JournalView(side+main+entry("未解锁支线",QuestCategory.SIDE,QuestStatus.LOCKED),emptyList())
        val lines=projection.project(view)
        assertEquals(18,lines.size)
        assertTrue(plain(lines.take(8)).contains("主线目标"))
        val heading=lines.indexOfFirst { plain(listOf(it))=="✦ 支线任务" }
        assertTrue(heading>0)
        assertFalse(plain(lines.take(heading)).contains("支线"))
        side.forEach { assertTrue(plain(lines.drop(heading)).contains(it.definition.name+" 1/3")) }
        assertTrue(plain(lines.drop(heading)).contains("目标："))
        assertFalse(plain(lines).contains("未解锁支线"))
        val onlySide=projection.project(JournalView(side+main.copy(status=QuestStatus.CLAIMED),emptyList()))
        assertTrue(plain(onlySide.take(8)).contains("当前主线章节已全部完成"))
        val onlySideHeading=onlySide.indexOfFirst { plain(listOf(it))=="✦ 支线任务" }
        assertFalse(plain(onlySide.take(onlySideHeading)).contains("支线1"))
    }

    @Test fun `all bundled branch goals remain complete alongside every first chapter main task`() {
        val chapter=ChapterFiles.parse(YamlConfiguration().apply {
            loadFromString(Files.readString(Path.of("src/main/resources/chapters/第一章·星海初醒.yml")))
        })
        val tasks=ym.quest.config.QuestTaskFiles.files(Path.of("src/main/resources/quests/第一章")).map { file ->
            val y=YamlConfiguration().apply { loadFromString(Files.readString(file)) }
            val o=y.getMapList("objectives").first()
            val q=QuestDefinition(y.getString("id")!!,y.getString("name")!!,emptyList(),y.getString("icon")!!,emptyList(),
                listOf(Objective(o["id"].toString(),o["type"].toString(),(o["target"] as Number).toLong(),null,o["description"].toString())),emptyList(),
                category=if(y.getString("category")=="side") QuestCategory.SIDE else QuestCategory.MAIN)
            QuestView(q,QuestStatus.ACTIVE,listOf(ObjectiveProgress(q.objectives.single(),0)))
        }
        val branches=tasks.filter { it.definition.category==QuestCategory.SIDE }
        assertEquals(3,branches.size)
        fun compact(value: String)=value.filterNot(Char::isWhitespace)
        tasks.filter { it.definition.category==QuestCategory.MAIN }.forEach { main ->
            val lines=projection.project(JournalView(listOf(main)+branches,listOf(ChapterView(chapter,ChapterStatus.ACTIVE,0,false))))
            val visible=compact(plain(lines))
            (listOf(main)+branches).forEach { q ->
                val description=compact(plain(listOf(q.definition.objectives.single().description)))
                assertTrue(visible.contains(description),"${main.definition.id}: missing ${q.definition.id}: $visible")
            }
            val heading=lines.indexOfFirst { plain(listOf(it))=="✦ 支线任务" }
            assertTrue(heading>0)
            lines.drop(heading+1).map { plain(listOf(it)) }.filter { it.isNotBlank() && !it.startsWith("▪") }.forEach {
                assertTrue(it.startsWith("  "),it)
            }
        }
    }

    @Test fun `team journal read projection neither completes nor claims rewards`() {
        val db=Database(directory.resolve("quest.db"),Logger.getAnonymousLogger())
        fun <T> java.util.concurrent.CompletableFuture<T>.await(): T = get(10,TimeUnit.SECONDS)
        db.open().await()
        try {
            val service=QuestService(db)
            service.configure(listOf(quest("one"))).await()
            service.sync("island",mapOf("genlite.produce:minecraft:cobblestone" to 100L)).await()
            service.sync("island",mapOf("genlite.produce:minecraft:cobblestone" to 108L)).await()
            val before=service.journal("island").await()
            val memberA=projection.project(before)
            val memberB=projection.project(service.journal("island").await())
            assertEquals(memberA,memberB)
            assertTrue(plain(memberA).contains("8/32"))
            assertEquals(before,service.journal("island").await())
            assertTrue(service.mailbox("island").await().isEmpty())
        } finally { db.closeAsync().await() }
    }

    @Test fun `language append is idempotent and preserves comments and unknown keys`() {
        val file=directory.resolve("zh_cn.yml")
        val original="schema-version: 1\n# 用户注释\ncustom: '保留'\ntab-groups:\n  main-heading: '自定义主线'\n"
        Files.writeString(file,original)
        val bundled=Files.readString(Path.of("src/main/resources/lang/zh_cn.yml"))
        ResourceUpgrade.appendLanguage(file,bundled)
        val once=Files.readString(file)
        ResourceUpgrade.appendLanguage(file,bundled)
        assertEquals(once,Files.readString(file))
        assertTrue(once.contains("# 用户注释"))
        assertTrue(once.contains("custom: '保留'"))
        val upgraded=YamlConfiguration().apply { loadFromString(once) }
        assertEquals("自定义主线",upgraded.getString("tab-groups.main-heading"))
        assertTrue(upgraded.isString("tab-groups.side-objective"))
        assertTrue(YamlConfiguration().apply { loadFromString(once) }.isString("tab.shared-line"))
    }

    @Test fun `tab settings validate ranges and layout separates fixed and player slots`() {
        val settings=YamlConfiguration().apply { loadFromString(Files.readString(Path.of("src/main/resources/tab.yml"))) }
        assertTrue(TabSettings.parse(settings).enabled)
        settings.set("refresh-ticks",1)
        assertThrows(IllegalArgumentException::class.java) { TabSettings.parse(settings) }
        val layout=YamlConfiguration().apply { loadFromString(Files.readString(Path.of("integrations/tab/config.yml"))) }
        assertTrue(layout.getBoolean("layout.enabled"))
        assertEquals(60,layout.getInt("layout.layouts.quest-and-players.slot-count"))
        val fixed=layout.getStringList("layout.layouts.quest-and-players.fixed-slots").map { it.substringBefore('|').toInt() }.toSet()
        assertEquals((1..21).toSet() + 41,fixed)
        assertEquals(listOf("22-40", "42-60"),layout.getStringList("layout.layouts.quest-and-players.groups.players.slots"))
        assertFalse(layout.getBoolean("scoreboard.enabled"))
        assertFalse(layout.getBoolean("bossbar.enabled"))
        assertTrue(layout.getBoolean("components.minimessage-support"))
        assertEquals("1|%quest_line_1%",layout.getStringList("layout.layouts.quest-and-players.fixed-slots").first())
    }
}
