package ym.quest

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ym.quest.storage.*
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

class HudPreferenceStoreTest {
    @TempDir lateinit var root: Path
    private fun <T> CompletableFuture<T>.await(): T = get(10,TimeUnit.SECONDS)
    @Test fun `legacy disabled notices and sound are ignored while tracking remains independently persisted`() {
        val db=Database(root.resolve("display.db"),Logger.getAnonymousLogger())
        db.open().await()
        try {
            val player=UUID.randomUUID()
            db.submit { db.transaction { c ->
                c.update("INSERT INTO hud_preferences VALUES(?,?,?,?)",player.toString(),"OFF",0,"task")
                c.update("INSERT INTO journal_options VALUES(?,?,?)",player.toString(),0,0)
            } }.await()
            val store=ym.quest.preferences.DisplayPreferenceStore(db)
            val loaded=store.load(player).await()
            assertTrue(loaded.notifications);assertTrue(loaded.sound)
            assertEquals(ym.quest.preferences.HudMode.OFF,loaded.mode)
            assertEquals("task",loaded.trackedQuest)
            val saved=store.save(player,loaded.copy(mode=ym.quest.preferences.HudMode.ALWAYS,autoTracking=true,notifications=false,sound=false)).await()
            assertTrue(saved.notifications);assertTrue(saved.sound)
            assertEquals(saved,store.load(player).await())
            db.submit { db.transaction { c ->
                assertEquals(1,c.number("SELECT notifications FROM hud_preferences WHERE player=?",player.toString()))
                assertEquals(1,c.number("SELECT sound FROM journal_options WHERE player=?",player.toString()))
            } }.await()
        } finally { db.closeAsync().await() }
    }
    @Test fun `v2 upgrade is additive preferences persist by UUID and migration is idempotent`() {
        val file=root.resolve("quest.db");val first=UUID.randomUUID();val second=UUID.randomUUID()
        var db=Database(file,Logger.getAnonymousLogger());db.open().await()
        db.submit { db.transaction { c ->
            c.update("INSERT INTO objectives VALUES('home','task','produce',100,18)")
            c.update("INSERT INTO claims VALUES('home','task','owner',1)")
            c.update("INSERT INTO parcels VALUES('parcel','home','task','minecraft:stone',1,'DELIVERED','owner')")
            c.update("DROP TABLE hud_preferences"); c.update("PRAGMA user_version=2")
        } }.await();db.closeAsync().await()
        db=Database(file,Logger.getAnonymousLogger());db.open().await()
        db.submit { db.transaction { c ->
            c.update("INSERT INTO hud_preferences VALUES(?,?,?,?)",first.toString(),"ALWAYS",0,"task")
            c.update("INSERT INTO hud_preferences VALUES(?,?,?,?)",second.toString(),"OFF",1,null)
        } }.await();db.closeAsync().await()
        db=Database(file,Logger.getAnonymousLogger());db.open().await()
        try {
            db.submit { db.transaction { c ->
                assertEquals(5,c.number("PRAGMA user_version"))
                assertEquals(100,c.number("SELECT baseline FROM objectives"));assertEquals(18,c.number("SELECT progress FROM objectives"))
                assertEquals(1,c.number("SELECT COUNT(*) FROM claims"));assertEquals(1,c.number("SELECT COUNT(*) FROM parcels WHERE phase='DELIVERED'"))
                assertEquals(2,c.number("SELECT COUNT(*) FROM hud_preferences"))
                assertEquals(1,c.number("SELECT COUNT(*) FROM hud_preferences WHERE player=? AND mode='ALWAYS' AND notifications=0 AND tracked_quest='task'",first.toString()))
            } }.await()
        } finally { db.closeAsync().await() }
    }
}
