package ym.quest

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ym.quest.domain.*
import ym.quest.storage.Database
import ym.quest.tutorial.CombatKillWindow
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

class TrialCombatTest {
    @TempDir lateinit var root: Path
    private fun <T> java.util.concurrent.CompletableFuture<T>.await()=get(10,TimeUnit.SECONDS)
    @Test fun `hostile and MM callbacks count once in either order and animals do not count`() {
        val window=CombatKillWindow()
        val zombie=UUID.randomUUID()
        assertTrue(window.accept(zombie,true,false))
        assertFalse(window.accept(zombie,true,true))
        val mm=UUID.randomUUID()
        assertTrue(window.accept(mm,true,true))
        assertFalse(window.accept(mm,true,false))
        val animal=UUID.randomUUID()
        assertFalse(window.accept(animal,false,false))
        assertTrue(window.accept(animal,false,true)) // MM may use a non-Enemy base entity.
        assertFalse(window.accept(animal,false,true))
        window.clear()
        assertTrue(window.accept(mm,true,false))
    }
    @Test fun `death deduplication expires and remains bounded`() {
        var now=0L
        val window=CombatKillWindow { now }
        val id=UUID.randomUUID()
        assertTrue(window.accept(id,true,false))
        now=120_000_000_000L
        assertTrue(window.accept(id,true,false))
        repeat(8192) { assertTrue(window.accept(UUID.randomUUID(),true,false)) }
        assertTrue(window.accept(id,true,false))
    }
    @Test fun `old two of three with nonzero baseline continues after migration restart and claims once`() {
        fun database()=Database(root.resolve("quest.db"),Logger.getAnonymousLogger()).also { it.open().await() }
        val old=QuestDefinition("first_trial","试炼",emptyList(),"minecraft:iron_sword",emptyList(),
            listOf(Objective("experience","combat.kill",3,"minecraft:zombie","击杀")),listOf(Reward("minecraft:coal",8)))
        val next=old.copy(objectives=listOf(old.objectives.single().copy(resource="hostile",metric="combat.kill:hostile")))
        var db=database();var service=QuestService(db)
        service.configure(listOf(old)).await()
        service.sync("home",mapOf("combat.kill:minecraft:zombie" to 10L)).await()
        repeat(2) { service.record("home","combat.kill:minecraft:zombie",1,emptyMap()).await() }
        assertEquals(2,service.views("home").await().single().progress.single().progress)
        service.configure(listOf(next)).await()
        service.sync("home",mapOf("combat.kill:hostile" to 0L)).await()
        assertEquals(2,service.views("home").await().single().progress.single().progress)
        db.closeAsync().await();db=database();service=QuestService(db)
        service.configure(listOf(next)).await()
        service.record("home","combat.kill:hostile",1,emptyMap()).await()
        assertEquals(QuestStatus.COMPLETE,service.views("home").await().single().status)
        assertTrue(service.claim("home","player","first_trial",emptyMap(),false).await())
        service.configure(listOf(next)).await()
        assertFalse(service.claim("home","player","first_trial",emptyMap(),false).await())
        assertEquals(1,service.mailbox("home").await().size)
        db.closeAsync().await()
    }
}
