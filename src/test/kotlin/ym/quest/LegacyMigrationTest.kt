package ym.quest

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ym.quest.domain.*
import ym.quest.storage.*
import java.nio.file.Path
import java.sql.DriverManager
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

class LegacyMigrationTest {
    @TempDir lateinit var root: Path
    private fun <T> CompletableFuture<T>.await(): T = get(10,TimeUnit.SECONDS)
    private val quests=listOf(
        QuestDefinition("sky_start","星海启航",emptyList(),"minecraft:book",emptyList(),listOf(Objective("island","island.member",1,null,"入岛")),listOf(Reward("minecraft:bread",8))),
        QuestDefinition("first_fuel","能源启动",emptyList(),"minecraft:book",listOf("sky_start"),listOf(Objective("fuel","genlite.fuel",1,null,"燃料")),emptyList()),
        QuestDefinition("first_resources","第一批资源",emptyList(),"minecraft:book",listOf("first_fuel"),listOf(Objective("cobblestone","genlite.produce",32,"minecraft:cobblestone","圆石")),listOf(Reward("genlite:basic_upgrade_unit",2))))
    private val chapter=ChapterDefinition("sky_awakening","第一章 · 星海初醒","minecraft:book",emptyList(),1,emptyList(),quests.map { it.id },listOf(Reward("minecraft:diamond",2)),taskRequirements=mapOf("first_fuel" to listOf("sky_start"),"first_resources" to listOf("first_fuel")))
    private fun legacy() {
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite:${root.resolve("quest.db")}").use { c ->
            c.createStatement().use { s -> listOf(
                "CREATE TABLE facts(home TEXT,metric TEXT,value INTEGER NOT NULL CHECK(value>=0),PRIMARY KEY(home,metric))",
                "CREATE TABLE objectives(home TEXT,quest TEXT,objective TEXT,baseline INTEGER NOT NULL,progress INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(home,quest,objective))",
                "CREATE TABLE completions(home TEXT,quest TEXT,completed INTEGER NOT NULL,PRIMARY KEY(home,quest))",
                "CREATE TABLE claims(home TEXT,quest TEXT,player TEXT,created INTEGER,PRIMARY KEY(home,quest))",
                "CREATE TABLE parcels(id TEXT PRIMARY KEY,home TEXT NOT NULL,quest TEXT NOT NULL,item TEXT NOT NULL,amount INTEGER NOT NULL,phase TEXT NOT NULL,player TEXT)",
                "CREATE TABLE audit(created INTEGER NOT NULL,actor TEXT NOT NULL,action TEXT NOT NULL,details TEXT NOT NULL)",
                "PRAGMA user_version=1").forEach { s.execute(it) } }
            // Existing active production baseline must remain 100; 12 already credited.
            c.update("INSERT INTO objectives VALUES('partial','first_resources','cobblestone',100,12)")
            c.update("INSERT INTO facts VALUES('partial','genlite.produce:minecraft:cobblestone',112)")
            listOf("partial","done","waiting").forEach { h ->
                c.update("INSERT INTO completions VALUES(?,'sky_start',1)",h)
                c.update("INSERT INTO facts VALUES(?,'island.member',1)",h)
            }
            listOf("partial","done").forEach { c.update("INSERT INTO claims VALUES(?,'first_fuel','owner',1)",it) }
            c.update("INSERT INTO completions VALUES('done','first_resources',1)")
            c.update("INSERT INTO facts VALUES('waiting','genlite.fuel',5)") // v1 cached before upgrade; no unlocked fuel row.
            listOf("WAITING","SENDING","DELIVERED").forEach { phase ->
                c.update("INSERT INTO parcels VALUES(?,'done','sky_start','minecraft:bread',8,?,'owner')",phase,phase)
            }
            c.update("INSERT INTO claims VALUES('done','sky_start','owner',1)")
        }
    }
    private fun facts(fuel: Long, produced: Long) = mapOf("island.member" to 1L,"genlite.fuel" to fuel,"genlite.produce:minecraft:cobblestone" to produced)

    @Test fun `v1 upgrade preserves progress baselines completions all reward phases and defers newly unlocked counters`() {
        legacy()
        var db=Database(root.resolve("quest.db"),Logger.getAnonymousLogger()); db.open().await()
        var s=QuestService(db);s.configure(quests,listOf(chapter)).await()
        assertEquals(12L,s.views("partial").await().last().progress.single().progress)
        assertEquals(ChapterStatus.COMPLETE,s.journal("done").await().chapters.single().status)
        assertFalse(s.journal("done").await().chapters.single().claimed)
        assertEquals(listOf("WAITING","SENDING","DELIVERED"),s.mailbox("done").await().map { it.phase })
        assertEquals(5L,db.submit { db.transaction { it.number("PRAGMA user_version") } }.await())
        assertEquals(100L,db.submit { db.transaction { it.number("SELECT baseline FROM objectives WHERE home='partial'") } }.await())
        s.sync("partial",facts(50,120)).await()
        assertEquals(20L,s.views("partial").await().last().progress.single().progress)
        s.sync("waiting",mapOf("island.member" to 1L)).await()
        assertEquals(0L,db.submit { db.transaction { it.number("SELECT COUNT(*) FROM objectives WHERE home='waiting' AND quest='first_fuel'") } }.await())
        s.sync("waiting",facts(50,1000)).await()
        assertEquals(0L,s.views("waiting").await()[1].progress.single().progress)
        s.sync("waiting",facts(51,2000)).await()
        assertEquals(0L,s.views("waiting").await().last().progress.single().progress)
        assertTrue(s.claimChapter("done","owner",chapter.id,facts(100,3000),false).await())
        assertFalse(s.claim("done","owner","sky_start",facts(100,3000),false).await())
        db.closeAsync().await()
        db=Database(root.resolve("quest.db"),Logger.getAnonymousLogger());db.open().await()
        s=QuestService(db);s.configure(quests,listOf(chapter)).await()
        try {
            assertEquals(20L,s.views("partial").await().last().progress.single().progress)
            assertEquals(4,s.mailbox("done").await().size)
            assertTrue(s.journal("done").await().chapters.single().claimed)
            assertFalse(s.claimChapter("done","member",chapter.id,facts(100,3000),false).await())
            assertEquals(1L,db.submit { db.transaction { it.number("SELECT COUNT(*) FROM audit WHERE action='schema-migration'") } }.await())
        } finally { db.closeAsync().await() }
    }

    @Test fun `unknown future database version is rejected without changing original data`() {
        legacy()
        DriverManager.getConnection("jdbc:sqlite:${root.resolve("quest.db")}").use { c -> c.createStatement().use { it.execute("PRAGMA user_version=99") } }
        val db=Database(root.resolve("quest.db"),Logger.getAnonymousLogger())
        try { assertThrows(Exception::class.java) { db.open().await() } }
        finally { db.closeAsync().await() }
        DriverManager.getConnection("jdbc:sqlite:${root.resolve("quest.db")}").use { c ->
            assertEquals(99L,c.number("PRAGMA user_version"))
            assertEquals(3L,c.number("SELECT COUNT(*) FROM parcels"))
            assertEquals(100L,c.number("SELECT baseline FROM objectives WHERE home='partial'"))
        }
    }
}
