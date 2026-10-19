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

class ChapterServiceTest {
    @TempDir lateinit var directory: Path
    private fun <T> CompletableFuture<T>.await(): T = get(10,TimeUnit.SECONDS)
    private fun database() = Database(directory.resolve("quest.db"),Logger.getAnonymousLogger()).apply { open().await() }
    private val quests = listOf(
        QuestDefinition("sky_start","星海启航",emptyList(),"minecraft:book",emptyList(),listOf(Objective("island","island.member",1,null,"入岛")),listOf(Reward("minecraft:bread",8))),
        QuestDefinition("first_fuel","能源启动",emptyList(),"genlite:fuel",listOf("sky_start"),listOf(Objective("fuel","genlite.fuel",1,null,"燃料")),listOf(Reward("genlite:mechanical_parts",2))),
        QuestDefinition("first_resources","第一批资源",emptyList(),"minecraft:cobblestone",listOf("first_fuel"),listOf(Objective("cobblestone","genlite.produce",32,"minecraft:cobblestone","生产")),listOf(Reward("genlite:basic_upgrade_unit",2))))
    private val chapter = ChapterDefinition("sky_awakening","第一章 · 星海初醒","minecraft:book",emptyList(),1,emptyList(),quests.map { it.id },listOf(Reward("minecraft:diamond",2)),taskRequirements=mapOf("first_fuel" to listOf("sky_start"),"first_resources" to listOf("first_fuel")))
    private fun facts(fuel: Long = 0, production: Long = 0) = mapOf("island.member" to 1L,"genlite.fuel" to fuel,"genlite.produce:minecraft:cobblestone" to production)

    @Test fun `complete entire chapter without claiming tasks and claim each reward exactly once after restart`() {
        var db=database(); var s=QuestService(db); s.configure(quests,listOf(chapter)).await()
        val initial=s.sync("A",facts(100,500)).await()
        assertEquals(1,initial.count { it.chapter && it.started })
        assertEquals(listOf(QuestStatus.COMPLETE,QuestStatus.ACTIVE,QuestStatus.LOCKED),s.views("A").await().map { it.status })
        val unlock=s.sync("A",facts(101,999)).await()
        assertTrue(unlock.any { it.quest == "first_resources" && it.started })
        assertEquals(0L,s.views("A").await().last().progress.single().progress) // Same event's 499 production cannot count twice.
        repeat(5) { assertTrue(s.sync("A",facts(101,999)).await().isEmpty()) }
        s.sync("A",facts(101,1030)).await()
        assertEquals(31L,s.views("A").await().last().progress.single().progress)
        s.sync("A",facts(1,2)).await()
        assertEquals(31L,s.views("A").await().last().progress.single().progress)
        val finished=s.sync("A",facts(101,1031)).await()
        assertEquals(1,finished.count { it.chapter && it.completed })
        assertEquals(ChapterStatus.COMPLETE,s.journal("A").await().chapters.single().status)
        assertTrue(s.mailbox("A").await().isEmpty())
        val claims=(1..40).map { s.claimChapter("A","member-$it",chapter.id,facts(101,1031),false) }
        assertEquals(1,claims.count { it.await() })
        assertEquals("chapter:sky_awakening",s.mailbox("A").await().single().quest)
        db.closeAsync().await(); db=database(); s=QuestService(db); s.configure(quests,listOf(chapter)).await()
        try {
            assertTrue(s.journal("A").await().chapters.single().claimed)
            assertFalse(s.claimChapter("A","owner",chapter.id,facts(101,1031),false).await())
            assertEquals(listOf(QuestStatus.COMPLETE,QuestStatus.COMPLETE,QuestStatus.COMPLETE),s.views("A").await().map { it.status })
            quests.forEach { q -> assertEquals(1,(1..20).map { s.claim("A","member-$it",q.id,facts(101,1031),false) }.count { it.await() }) }
            assertEquals(4,s.mailbox("A").await().size)
            val parcel=s.mailbox("A").await().first()
            assertEquals(1,(1..20).map { s.transition(parcel.id,"member-$it","WAITING","SENDING") }.count { it.await() })
            assertTrue(s.resolve(parcel.id,"admin",false).await())
            assertTrue(s.transition(parcel.id,"owner","WAITING","SENDING").await())
            assertTrue(s.resolve(parcel.id,"admin",true).await())
            s.configure(quests,listOf(chapter.copy(rewards=listOf(Reward("minecraft:emerald",64))))).await()
            assertEquals("minecraft:diamond",s.mailbox("A").await().first().item)
            assertFalse(s.claimChapter("B","owner",chapter.id,facts(),false).await())
        } finally { db.closeAsync().await() }
    }

    @Test fun `automatic claims share unique delivery records with manual claims and recover after restart`() {
        var db=database(); var s=QuestService(db); s.configure(quests,listOf(chapter)).await()
        val eligible=quests.map { it.id }.toSet()+"chapter:${chapter.id}"
        assertEquals(listOf("sky_start"),s.claimCompleted("A","member",facts(100,500),false,eligible).await())
        s.sync("A",facts(101,999)).await(); s.sync("A",facts(101,1031)).await()
        val concurrent=(1..30).map { s.claimCompleted("A","member-$it",facts(101,1031),false,eligible) }
        assertEquals(3,concurrent.sumOf { it.await().size })
        assertFalse(s.claim("A","manual","first_resources",facts(101,1031),false).await())
        assertEquals(4,s.mailbox("A").await().size)
        val parcel=s.mailbox("A").await().first()
        assertTrue(s.transition(parcel.id,"member","WAITING","SENDING").await())
        db.closeAsync().await(); db=database(); s=QuestService(db); s.configure(quests,listOf(chapter)).await()
        try {
            assertTrue(s.claimCompleted("A","after-restart",facts(101,1031),false,eligible).await().isEmpty())
            assertEquals(4,s.mailbox("A").await().size)
            assertEquals("SENDING",s.mailbox("A").await().first().phase)
            assertEquals(3,s.mailbox("A").await().count { it.phase == "WAITING" })
            // Unavailable items are excluded by the owner-thread item check and retried later.
            assertTrue(s.claimCompleted("B","member",facts(),false,emptySet()).await().isEmpty())
            assertEquals(listOf("sky_start"),s.claimCompleted("B","member",facts(),false,eligible).await())
        } finally { db.closeAsync().await() }
    }

    @Test fun `chapter gates wait for completion not claims and branches use independent baselines`() {
        val db=database()
        try {
            val q=quests[1].copy(id="branch",prerequisites=emptyList())
            val ch1=chapter.copy(quests=listOf("sky_start"),taskRequirements=emptyMap())
            val ch2=chapter.copy(id="second",order=2,prerequisites=listOf(ch1.id),quests=listOf("first_fuel","branch"),taskRequirements=emptyMap())
            val s=QuestService(db);s.configure(listOf(quests[0],quests[1],q),listOf(ch2,ch1)).await()
            assertEquals(ChapterStatus.LOCKED,s.journal("A").await().chapters.last().status)
            s.sync("A",facts(20,400)).await()
            assertEquals(listOf(ChapterStatus.COMPLETE,ChapterStatus.ACTIVE),s.journal("A").await().chapters.map { it.status })
            assertEquals(listOf(0L,0L),s.views("A").await().drop(1).map { it.progress.single().progress })
            s.sync("A",facts(21,400)).await()
            assertTrue(s.journal("A").await().chapters.all { it.status == ChapterStatus.COMPLETE })
            assertTrue(s.mailbox("A").await().isEmpty())
        } finally { db.closeAsync().await() }
    }

    @Test fun `missing live metric never establishes a baseline from a stale view or incomplete snapshot`() {
        val db=database()
        try {
            val s=QuestService(db);s.configure(quests,listOf(chapter)).await()
            s.sync("A",mapOf("island.member" to 1L)).await()
            repeat(3) { s.views("A").await(); s.journal("A").await() }
            s.sync("A",facts(100,500)).await()
            assertEquals(0L,s.views("A").await()[1].progress.single().progress)
            s.sync("A",facts(101,1000)).await()
            assertEquals(0L,s.views("A").await()[2].progress.single().progress)
            assertTrue(s.sync("A",facts(101,1000)).await().isEmpty())
        } finally { db.closeAsync().await() }
    }

    @Test fun `chapter graph forks and joins on all predecessors independent of display order`() {
        val db=database()
        try {
            val branch=quests[1].copy(id="branch",objectives=listOf(quests[1].objectives.single().copy(target=2)))
            val ch=chapter.copy(quests=listOf("first_resources","branch","first_fuel","sky_start"),taskRequirements=mapOf(
                "first_fuel" to listOf("sky_start"),"branch" to listOf("sky_start"),"first_resources" to listOf("first_fuel","branch")))
            val service=QuestService(db);service.configure(quests+branch,listOf(ch)).await()
            fun views() = service.views("A").await().associateBy { it.definition.id }
            service.sync("A",facts(10,500)).await()
            service.sync("A",facts(11,900)).await()
            assertEquals(QuestStatus.COMPLETE,views().getValue("first_fuel").status)
            assertEquals(QuestStatus.ACTIVE,views().getValue("branch").status)
            assertEquals(QuestStatus.LOCKED,views().getValue("first_resources").status)
            service.sync("A",facts(12,1000)).await()
            assertEquals(0L,views().getValue("first_resources").progress.single().progress)
            service.sync("A",facts(12,1032)).await()
            assertEquals(ChapterStatus.COMPLETE,service.journal("A").await().chapters.single().status)
            assertTrue(service.mailbox("A").await().isEmpty())
        } finally { db.closeAsync().await() }
    }

    @Test fun `configuration rejects duplicate missing and cyclic dependencies including cross chapter gates`() {
        fun invalid(q: List<QuestDefinition> = quests, ch: List<ChapterDefinition>) = assertThrows(IllegalArgumentException::class.java) { ChapterGraph.prepare(q,ch) }
        invalid(ch=listOf(chapter,chapter))
        invalid(ch=listOf(chapter.copy(quests=listOf("missing"))))
        invalid(ch=listOf(chapter.copy(quests=listOf("sky_start","sky_start"))))
        invalid(ch=listOf(chapter.copy(prerequisites=listOf("missing"))))
        invalid(ch=listOf(chapter.copy(prerequisites=listOf(chapter.id))))
        invalid(q=quests+quests[0],ch=listOf(chapter))
        invalid(ch=listOf(chapter.copy(taskRequirements=chapter.taskRequirements + ("sky_start" to listOf("first_resources")))))
        val a=chapter.copy(quests=listOf("sky_start"),taskRequirements=emptyMap())
        val b=chapter.copy(id="second",quests=listOf("first_fuel","first_resources"),prerequisites=listOf(a.id))
        invalid(q=quests.map { if(it.id == "sky_start") it.copy(prerequisites=listOf("first_resources")) else it },ch=listOf(a,b))
    }
}
