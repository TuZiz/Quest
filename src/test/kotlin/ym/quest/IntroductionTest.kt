package ym.quest

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ym.quest.config.ResourceUpgrade
import ym.quest.domain.*
import ym.quest.introduction.*
import ym.quest.storage.*
import ym.quest.tab.TabQuestProjection
import ym.quest.text.TextRenderer
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

class IntroductionTest {
    @TempDir lateinit var directory: Path
    private fun <T> CompletableFuture<T>.await(): T = get(10,TimeUnit.SECONDS)
    private fun language() = YamlConfiguration().apply { loadFromString(Files.readString(Path.of("src/main/resources/lang/zh_cn.yml"))) }

    @Test fun `personal progress is isolated idempotent survives restart and v3 upgrade preserves rewards`() {
        val file=directory.resolve("quest.db")
        var db=Database(file,Logger.getAnonymousLogger()); db.open().await()
        db.submit { db.transaction { c ->
            c.update("INSERT INTO objectives VALUES('home','first_fuel','fuel',17,1)")
            c.update("INSERT INTO claims VALUES('home','first_fuel','owner',1)")
            c.update("INSERT INTO parcels VALUES('parcel','home','first_fuel','minecraft:bread',8,'DELIVERED','owner')")
            c.createStatement().use { it.execute("DROP TABLE personal_introduction"); it.execute("PRAGMA user_version=3") }
        } }.await()
        db.closeAsync().await()
        db=Database(file,Logger.getAnonymousLogger()); db.open().await()
        val alice=UUID.randomUUID(); val bob=UUID.randomUUID()
        try {
            val store=IntroductionStore(db)
            assertNull(store.load(alice,"meet_guide").await())
            store.complete(alice,"meet_guide",IntroductionOutcome.DONE).await()
            store.complete(alice,"meet_guide",IntroductionOutcome.SKIPPED).await()
            assertEquals(IntroductionOutcome.DONE,store.load(alice,"meet_guide").await())
            assertNull(store.load(bob,"meet_guide").await())
            store.complete(bob,"meet_guide",IntroductionOutcome.SKIPPED).await()
            assertNull(store.load(alice,"different_task").await())
        } finally { db.closeAsync().await() }
        db=Database(file,Logger.getAnonymousLogger()); db.open().await()
        try {
            assertEquals(IntroductionOutcome.DONE,IntroductionStore(db).load(alice,"meet_guide").await())
            assertEquals(IntroductionOutcome.SKIPPED,IntroductionStore(db).load(bob,"meet_guide").await())
        } finally { db.closeAsync().await() }
        DriverManager.getConnection("jdbc:sqlite:$file").use { c ->
            assertEquals(5L,c.number("PRAGMA user_version"))
            assertEquals(17L,c.number("SELECT baseline FROM objectives"))
            assertEquals(1L,c.number("SELECT progress FROM objectives"))
            assertEquals(1L,c.number("SELECT COUNT(*) FROM claims"))
            assertEquals("DELIVERED",c.rows("SELECT phase FROM parcels") { it.getString(1) }.single())
            assertEquals(2L,c.number("SELECT COUNT(*) FROM personal_introduction"))
        }
    }

    @Test fun `personal first task only decorates journal without changing shared graph rewards or claim state`() {
        val q=QuestDefinition("sky_start","入岛",emptyList(),"minecraft:book",emptyList(),
            listOf(Objective("island","island.member",1,null,"入岛")),listOf(Reward("minecraft:bread",8)))
        val chapter=ChapterDefinition("sky_awakening","第一章","minecraft:book",emptyList(),1,emptyList(),listOf(q.id),listOf(Reward("minecraft:diamond",2)))
        val original=JournalView(listOf(QuestView(q,QuestStatus.ACTIVE,listOf(ObjectiveProgress(q.objectives.single(),0)))),
            listOf(ChapterView(chapter,ChapterStatus.ACTIVE,0,false)))
        val text=TextRenderer(language()); val overlay=IntroductionJournal(IntroductionSettings(),text)
        val pending=overlay.decorate(original,false)
        assertEquals("meet_guide",pending.quests.first().definition.id)
        assertEquals(emptyList<Reward>(),pending.quests.first().definition.rewards)
        assertEquals(listOf("meet_guide","sky_start"),pending.chapters.single().definition.quests)
        assertEquals(listOf("sky_start"),original.chapters.single().definition.quests)
        assertSame(original.quests.single(),pending.quests[1])
        assertEquals(chapter.rewards,pending.chapters.single().definition.rewards)
        assertFalse(pending.chapters.single().claimed)
        val finished=overlay.decorate(original,true)
        assertEquals(QuestStatus.COMPLETE,finished.quests.first().status)
        assertEquals(1,finished.chapters.single().completed)
        assertEquals(TabQuestProjection(text,28).project(original),TabQuestProjection(text,28).project(finished))
    }

    @Test fun `introduction configuration validates IDs types and timeout`() {
        val raw=Files.readString(Path.of("src/main/resources/引导.yml"))
        fun parse(value: String) = IntroductionSettings.parse(YamlConfiguration().apply { loadFromString(value) })
        assertEquals("新人",parse(raw).npcId)
        for(value in listOf(raw.replace("120","0"),raw.replace("120","'120'"),raw.replace("sky_awakening","bad/id"),raw.replace("enabled: true","enabled: 'true'"))) {
            assertThrows(IllegalArgumentException::class.java) { parse(value) }
        }
    }

    @Test fun `language upgrade is additive repeatable and keeps user comments and unknown keys`() {
        val file=directory.resolve("lang.yml")
        val original="schema-version: 1\n# 自定义注释\ncustom: '保留'\n"
        Files.writeString(file,original)
        val bundled=Files.readString(Path.of("src/main/resources/lang/zh_cn.yml"))
        ResourceUpgrade.appendLanguage(file,bundled)
        val once=Files.readString(file)
        assertTrue(once.startsWith(original))
        assertEquals(language().getStringList("introduction.dialogue"),YamlConfiguration().apply { loadFromString(once) }.getStringList("introduction.dialogue"))
        ResourceUpgrade.appendLanguage(file,bundled)
        assertEquals(once,Files.readString(file))
    }

    @Test fun `actionbar upgrade adds missing key while retaining customized introduction and comments`() {
        val file=directory.resolve("lang.yml")
        val bundled=Files.readString(Path.of("src/main/resources/lang/zh_cn.yml"))
        val old=bundled.lineSequence().filterNot { it.startsWith("  actionbar:") }.joinToString("\n")
            .replace("初见引航员", "我的自定义任务")
            .replace("下一步：创建或加入空岛，继续后续任务。","创建或加入空岛后，即可开启任务。") + "\n# 我的注释\ncustom: '保留'\n"
        Files.writeString(file,old)
        ResourceUpgrade.appendLanguage(file,bundled)
        val once=Files.readString(file)
        val y=YamlConfiguration().apply { loadFromString(once) }
        assertEquals(language().getString("introduction.actionbar"),y.getString("introduction.actionbar"))
        assertEquals("<#FFD700>我的自定义任务",y.getString("introduction.task-name"))
        assertEquals(language().getString("tab.no-island"),y.getString("tab.no-island"))
        assertTrue(once.contains("# 我的注释"))
        assertEquals("保留",y.getString("custom"))
        ResourceUpgrade.appendLanguage(file,bundled)
        assertEquals(once,Files.readString(file))
    }
}
