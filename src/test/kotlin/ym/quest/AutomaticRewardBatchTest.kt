package ym.quest

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ym.quest.domain.*
import ym.quest.preferences.*
import ym.quest.reward.RewardDeliveryBatch
import ym.quest.storage.Database
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

class AutomaticRewardBatchTest {
    @TempDir lateinit var root: Path
    private fun <T> CompletableFuture<T>.await(): T=get(10,TimeUnit.SECONDS)
    @Test fun `automatic claims reserve one complete task with all its rewards and never duplicate on recovery`() {
        val db=Database(root.resolve("quest.db"),Logger.getAnonymousLogger());db.open().await()
        try {
            val service=QuestService(db)
            val tasks=(1..3).map { n -> QuestDefinition("q$n","任务$n",emptyList(),"minecraft:book",emptyList(),
                listOf(Objective("member","island.member",1,null,"入岛")),
                listOf(Reward("genlite:fuel",8),Reward("currency:copper_coin",1))) }
            service.configure(tasks).await();service.sync("home",mapOf("island.member" to 1)).await()
            val eligible=tasks.map { it.id }.toSet()
            assertEquals(listOf("q1"),service.claimCompleted("home","one",emptyMap(),false,eligible,1).await())
            assertEquals(listOf("q1","q1"),service.mailbox("home").await().map { it.quest })
            val calls=(1..20).map { service.claimCompleted("home","member$it",emptyMap(),false,eligible,1) }
            val ids=calls.flatMap { it.await() }
            assertEquals(setOf("q2","q3"),ids.toSet());assertEquals(2,ids.size)
            assertEquals(6,service.mailbox("home").await().size)
            assertTrue(service.claimCompleted("home","reconnect",emptyMap(),false,eligible,1).await().isEmpty())
        } finally { db.closeAsync().await() }
    }
    @Test fun `delivery recovery selects exactly one task and preferences default to notices without tracking`() {
        fun parcel(id: String,quest: String)=Parcel(id,"home",quest,"genlite:fuel",8,"WAITING",null)
        val waiting=listOf(parcel("1","a"),parcel("2","b"),parcel("3","a"))
        assertEquals(listOf("1","3"),RewardDeliveryBatch.firstTask(waiting).map { it.id })
        assertTrue(RewardDeliveryBatch.firstTask(emptyList()).isEmpty())
        assertTrue(DisplayPreferences().notifications)
        assertEquals(HudMode.OFF,DisplayPreferences().mode)
        assertFalse(DisplayPreferences().autoTracking)
    }
}
