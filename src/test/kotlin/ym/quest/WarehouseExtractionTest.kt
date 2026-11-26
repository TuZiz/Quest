package ym.quest

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ym.quest.domain.*
import ym.quest.storage.*
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

class WarehouseExtractionTest {
    @TempDir lateinit var root: Path
    private fun <T> CompletableFuture<T>.await(): T=get(10,TimeUnit.SECONDS)
    private fun task(id: String="first_extract", prerequisites: List<String> = emptyList()) = QuestDefinition(id,"资源的归宿",emptyList(),"minecraft:chest",prerequisites,
        listOf(Objective("experience","genlite.extract",8,null,"取出8个资源")),listOf(Reward("genlite:fuel",8)))
    private fun snapshot(vault: Long, legacy: Long=0)=mapOf("genlite.extract" to legacy,WarehouseProgressFacts.source to vault)

    @Test fun `existing active task preserves progress excludes historical withdrawals and resumes after restart`() {
        var db=Database(root.resolve("quest.db"),Logger.getAnonymousLogger());db.open().await()
        try {
            val service=QuestService(db);service.configure(listOf(task())).await()
            service.sync("home",mapOf("genlite.extract" to 10L)).await()
            service.sync("home",mapOf("genlite.extract" to 12L)).await()
            assertEquals(2L,service.views("home").await().single().progress.single().progress)
            service.sync("home",snapshot(200,12)).await()
            assertEquals(2L,service.views("home").await().single().progress.single().progress)
            service.sync("home",snapshot(203,12)).await()
            assertEquals(5L,service.views("home").await().single().progress.single().progress)
        } finally {db.closeAsync().await()}
        db=Database(root.resolve("quest.db"),Logger.getAnonymousLogger());db.open().await()
        try {
            val service=QuestService(db);service.configure(listOf(task())).await()
            service.sync("home",snapshot(206,12)).await()
            assertEquals(QuestStatus.COMPLETE,service.views("home").await().single().status)
            val claims=listOf(service.claim("home","alice","first_extract",snapshot(206,12),false),service.claim("home","bob","first_extract",snapshot(206,12),false))
            assertEquals(1,claims.count { it.await() })
            repeat(3) { service.sync("home",snapshot(214,12)).await() }
            assertFalse(service.claim("home","alice","first_extract",snapshot(214,12),false).await())
            assertEquals(1,service.mailbox("home").await().size)
            assertEquals(QuestStatus.CLAIMED,service.views("home").await().single().status)
        } finally {db.closeAsync().await()}
    }
    @Test fun `unlock and island boundaries retain experience baselines and reject source rollback`() {
        val db=Database(root.resolve("quest.db"),Logger.getAnonymousLogger());db.open().await()
        try {
            val service=QuestService(db);service.configure(listOf(task("before"),task("after",listOf("before")))).await()
            service.sync("a",snapshot(50)).await();service.sync("b",snapshot(400)).await()
            service.sync("a",snapshot(58)).await()
            val views=service.views("a").await()
            assertEquals(QuestStatus.COMPLETE,views[0].status)
            assertEquals(0L,views[1].progress.single().progress)
            assertEquals(0L,service.views("b").await()[0].progress.single().progress)
            service.sync("a",snapshot(66)).await()
            assertEquals(QuestStatus.COMPLETE,service.views("a").await()[1].status)
            assertThrows(ExecutionException::class.java) {service.sync("a",snapshot(65)).await()}
            service.sync("a",snapshot(66)).await()
            assertEquals(QuestStatus.COMPLETE,service.views("a").await()[1].status)
        } finally {db.closeAsync().await()}
    }
    @Test fun `missing warehouse API never fabricates a withdrawal event`() {
        val db=Database(root.resolve("quest.db"),Logger.getAnonymousLogger());db.open().await()
        try {
            val service=QuestService(db);service.configure(listOf(task())).await()
            repeat(3) {service.sync("a",emptyMap()).await()}
            service.sync("a",snapshot(900)).await()
            assertEquals(0L,service.views("a").await().single().progress.single().progress)
            service.sync("a",snapshot(908)).await()
            assertEquals(QuestStatus.COMPLETE,service.views("a").await().single().status)
        } finally {db.closeAsync().await()}
    }
}
