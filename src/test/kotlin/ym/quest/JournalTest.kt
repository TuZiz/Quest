package ym.quest

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ym.quest.domain.*
import ym.quest.gui.journal.*
import ym.quest.preferences.*
import ym.quest.storage.*
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

class JournalTest {
    @TempDir lateinit var root: Path
    private fun <T> CompletableFuture<T>.await(): T=get(10,TimeUnit.SECONDS)
    @Test fun `schema four preferences are preserved and new options survive restart independently`() {
        val file=root.resolve("quest.db");val first=UUID.randomUUID();val second=UUID.randomUUID()
        var db=Database(file,Logger.getAnonymousLogger());db.open().await()
        db.submit { db.transaction { c ->
            c.update("INSERT INTO hud_preferences VALUES(?,?,?,?)",first.toString(),"SMART",0,"active")
            c.update("DROP TABLE journal_options");c.update("PRAGMA user_version=4")
            c.update("DELETE FROM audit WHERE action='journal-options-migration'")
            c.update("INSERT INTO objectives VALUES('home','task','fuel',9,3)")
            c.update("INSERT INTO claims VALUES('home','task','owner',1)")
        } }.await();db.closeAsync().await()
        db=Database(file,Logger.getAnonymousLogger());db.open().await()
        var store=DisplayPreferenceStore(db)
        assertEquals(DisplayPreferences(HudMode.ALWAYS,true,notifications=false,trackedQuest="active"),store.load(first).await())
        assertEquals(DisplayPreferences(),store.load(second).await())
        assertEquals(HudMode.OFF,store.load(second).await().mode)
        assertFalse(store.load(second).await().autoTracking)
        val changed=DisplayPreferences(HudMode.OFF,false,false,false,"active")
        store.save(first,changed).await();store.save(second,DisplayPreferences(trackedQuest="other")).await()
        db.closeAsync().await();db=Database(file,Logger.getAnonymousLogger());db.open().await();store=DisplayPreferenceStore(db)
        try {
            assertEquals(changed,store.load(first).await())
            assertEquals(DisplayPreferences(trackedQuest="other"),store.load(second).await())
            db.submit { db.transaction { c ->
                assertEquals(5L,c.number("PRAGMA user_version"))
                assertEquals(3L,c.number("SELECT progress FROM objectives"))
                assertEquals(1L,c.number("SELECT COUNT(*) FROM claims"))
                assertEquals(1L,c.number("SELECT COUNT(*) FROM audit WHERE action='journal-options-migration'"))
            } }.await()
        } finally { db.closeAsync().await() }
    }
    private fun task(id: String,status: QuestStatus,category: QuestCategory=QuestCategory.MAIN)=QuestView(
        QuestDefinition(id,id,emptyList(),"minecraft:book",emptyList(),listOf(Objective("fuel","genlite.fuel",1,null,"燃料")),emptyList(),category),status,emptyList())
    private fun view()=JournalView(listOf(task("done",QuestStatus.COMPLETE),task("active",QuestStatus.ACTIVE),
        task("locked",QuestStatus.LOCKED),task("other",QuestStatus.ACTIVE,QuestCategory.SIDE)),listOf(
        ChapterView(ChapterDefinition("main","main","minecraft:book",emptyList(),0,emptyList(),listOf("done","active","locked"),emptyList()),ChapterStatus.ACTIVE,1,false),
        ChapterView(ChapterDefinition("side","side","minecraft:book",emptyList(),1,emptyList(),listOf("other"),emptyList()),ChapterStatus.ACTIVE,0,false)))
    @Test fun `chapter and category filtering exposes only selected chapter without changing tracking`() {
        val view=view();val prefs=DisplayPreferences(trackedQuest="active")
        val s=JournalSession(UUID.randomUUID(),null,JournalPage.TASKS,chapter="main")
        assertEquals(listOf("done","active","locked"),JournalNavigation.tasks(view,s).map { it.definition.id })
        assertEquals("active",TrackingSelection.select(view,prefs)?.definition?.id)
        s.category=QuestCategory.SPECIAL
        assertTrue(JournalNavigation.tasks(view,s).isEmpty())
        assertTrue(JournalNavigation.chapters(view,s).isEmpty())
        s.category=QuestCategory.SIDE;s.chapter="side"
        assertEquals(listOf("other"),JournalNavigation.tasks(view,s).map { it.definition.id })
        s.chapter=null;assertTrue(JournalNavigation.tasks(view,s).isEmpty())
        assertEquals(3,JournalPage.entries.size)
    }
    @Test fun `manual tracking never follows completed locked or removed tasks and auto advances`() {
        val view=view()
        for(id in listOf("done","locked","removed")) {
            assertNull(TrackingSelection.select(view,DisplayPreferences(autoTracking=false,trackedQuest=id)))
            assertEquals("active",TrackingSelection.select(view,DisplayPreferences(autoTracking=true,trackedQuest=id))?.definition?.id)
        }
        assertEquals("other",TrackingSelection.select(view,DisplayPreferences(trackedQuest="other"))?.definition?.id)
    }
}
