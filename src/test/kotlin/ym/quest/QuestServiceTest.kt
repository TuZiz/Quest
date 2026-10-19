package ym.quest

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ym.quest.domain.*
import ym.quest.storage.Database
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

class QuestServiceTest {
    @TempDir lateinit var directory: Path
    private fun <T> CompletableFuture<T>.await(): T = get(10,TimeUnit.SECONDS)
    private fun definitions() = listOf(
        QuestDefinition("start","启航",emptyList(),"minecraft:book",emptyList(),listOf(Objective("home","island.member",1,null,"入岛")),listOf(Reward("minecraft:bread",8),Reward("genlite:cobblestone_generator",1,true))),
        QuestDefinition("fuel","能源",emptyList(),"minecraft:book",listOf("start"),listOf(Objective("fuel","genlite.fuel",1,null,"加燃料")),listOf(Reward("minecraft:iron_ingot",2))),
        QuestDefinition("produce","生产",emptyList(),"minecraft:book",listOf("fuel"),listOf(Objective("cobble","genlite.produce",32,"minecraft:cobblestone","产出")),listOf(Reward("genlite:basic_upgrade_unit",1)))
    )
    private fun database() = Database(directory.resolve("quest.db"),Logger.getAnonymousLogger()).apply { open().await() }
    private val home = "island-A"
    private fun facts(fuel: Long = 0, produce: Long = 0) = mapOf("island.member" to 1L,"genlite.fuel" to fuel,"genlite.produce:minecraft:cobblestone" to produce)
    @Test fun `full shared flow counts only successful output after unlock and rejects duplicate claims`() {
        val db=database()
        try {
            val service=QuestService(db); service.configure(definitions()).await()
            service.sync(home,facts(10,100)).await()
            assertEquals(listOf(QuestStatus.COMPLETE,QuestStatus.ACTIVE,QuestStatus.LOCKED),service.views(home).await().map { it.status })
            val attempts=(1..40).map { service.claim(home,"member-$it","start",facts(10,100),false) }
            assertEquals(1,attempts.count { it.await() })
            assertEquals(2,service.mailbox(home).await().size)
            assertEquals(0L,service.views(home).await()[1].progress[0].progress)
            service.sync(home,facts(11,120)).await()
            assertTrue(service.claim(home,"member-B","fuel",facts(11,120),false).await())
            service.sync(home,facts(11,151)).await()
            repeat(20) { service.sync(home,facts(11,151)).await() }
            assertEquals(31L,service.views(home).await()[2].progress[0].progress)
            service.sync(home,facts(11,1)).await() // delayed/rolled-back source snapshot cannot decrement/replay.
            assertEquals(31L,service.views(home).await()[2].progress[0].progress)
            service.sync(home,facts(11,152)).await()
            assertEquals(QuestStatus.COMPLETE,service.views(home).await()[2].status)
            val finish=(1..20).map { service.claim(home,"member-$it","produce",facts(11,152),false) }
            assertEquals(1,finish.count { it.await() })
            assertEquals(4,service.mailbox(home).await().size)
            assertEquals(QuestStatus.ACTIVE,service.views("island-B").await()[0].status)
            service.reset(home,"produce","admin",facts(11,152)).await()
            assertEquals(QuestStatus.CLAIMED,service.views(home).await()[2].status)
            assertFalse(service.claim(home,"member-B","produce",facts(11,1000),false).await())
        } finally { db.closeAsync().await() }
    }
    @Test fun `restart preserves progress mailbox reservations and explicit recovery audit`() {
        var db=database()
        var service=QuestService(db); service.configure(definitions()).await()
        service.sync(home,facts()).await()
        assertTrue(service.claim(home,"owner","start",facts(),true).await())
        assertEquals(1,service.mailbox(home).await().size) // Existing Gen suppresses starter machine.
        val parcel=service.mailbox(home).await().single()
        val reservations=(1..30).map { service.transition(parcel.id,"member-$it","WAITING","SENDING") }
        assertEquals(1,reservations.count { it.await() })
        service.sync(home,facts(2)).await()
        db.closeAsync().await()
        db=database(); service=QuestService(db); service.configure(definitions()).await()
        try {
            assertEquals(QuestStatus.COMPLETE,service.views(home).await()[1].status)
            assertEquals("SENDING",service.mailbox(home).await().single().phase)
            assertFalse(service.transition(parcel.id,"owner","WAITING","SENDING").await())
            assertTrue(service.resolve(parcel.id,"admin",false).await())
            assertEquals("WAITING",service.mailbox(home).await().single().phase)
            assertTrue(service.transition(parcel.id,"owner","WAITING","SENDING").await())
            assertTrue(service.resolve(parcel.id,"admin",true).await())
            assertFalse(service.resolve(parcel.id,"admin",false).await())
            assertEquals("DELIVERED",service.mailbox(home).await().single().phase)
            assertFalse(service.claim(home,"owner","start",facts(),false).await())
        } finally { db.closeAsync().await() }
    }
    @Test fun `multiple objectives all required and reset establishes new counter baseline`() {
        val db=database()
        try {
            val q=definitions()[0].copy(objectives=listOf(Objective("a","genlite.fuel",2,null,"fuel"),Objective("b","genlite.produce",3,"minecraft:cobblestone","produce")))
            val s=QuestService(db); s.configure(listOf(q)).await(); s.sync(home,facts()).await()
            s.sync(home,facts(2,2)).await(); assertEquals(QuestStatus.ACTIVE,s.views(home).await().single().status)
            s.reset(home,q.id,"admin",facts(2,2)).await()
            s.sync(home,facts(3,100)).await(); assertEquals(QuestStatus.ACTIVE,s.views(home).await().single().status)
            s.sync(home,facts(4,100)).await(); assertEquals(QuestStatus.COMPLETE,s.views(home).await().single().status)
        } finally { db.closeAsync().await() }
    }
    @Test fun `claim freezes reward payload across config reload`() {
        val db=database()
        try {
            val s=QuestService(db); s.configure(definitions()).await(); s.sync(home,facts()).await()
            assertTrue(s.claim(home,"owner","start",facts(),true).await())
            s.configure(definitions().map { it.copy(rewards=listOf(Reward("minecraft:diamond",64))) }).await()
            assertEquals("minecraft:bread",s.mailbox(home).await().single().item)
        } finally { db.closeAsync().await() }
    }
    @Test fun `completed state survives objective edits and restart until explicitly reset`() {
        var db=database()
        val original=definitions().first().copy(objectives=listOf(Objective("fuel","genlite.fuel",1,null,"fuel")))
        var s=QuestService(db);s.configure(listOf(original)).await();s.sync(home,facts()).await();s.sync(home,facts(1)).await()
        db.closeAsync().await()
        db=database();s=QuestService(db)
        try {
            val changed=original.copy(objectives=listOf(original.objectives.single().copy(target=100)))
            s.configure(listOf(changed)).await()
            assertEquals(QuestStatus.COMPLETE,s.views(home).await().single().status)
            s.reset(home,original.id,"admin",facts(1)).await()
            assertEquals(QuestStatus.ACTIVE,s.views(home).await().single().status)
        } finally { db.closeAsync().await() }
    }
}
