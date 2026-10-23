package ym.quest

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ym.quest.domain.*
import ym.quest.storage.Database
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

class TutorialProgressTest {
    @TempDir lateinit var root: Path
    private fun <T> java.util.concurrent.CompletableFuture<T>.await()=get(10,TimeUnit.SECONDS)
    private fun database()=Database(root.resolve("quest.db"),Logger.getAnonymousLogger()).also { it.open().await() }
    private val start=QuestDefinition("start","开始",emptyList(),"minecraft:stone",emptyList(),listOf(Objective("member","island.member",1,null,"入岛")),listOf(Reward("minecraft:coal",4)))
    private fun chapter(ids: List<String>,optional: Set<String> = emptySet())=ChapterDefinition("sky_awakening","第一章","minecraft:book",emptyList(),1,emptyList(),ids,listOf(Reward("minecraft:diamond",2)),optionalQuests=optional)
    @Test fun `level reads committed state and does not regress after removal while personal operations start at unlock`() {
        val db=database();val s=QuestService(db)
        val level=start.copy(id="upgrade",objectives=listOf(Objective("level","genlite.level",5,"cobblestone_generator","真实等级")))
        val pick=start.copy(id="harvest",objectives=listOf(Objective("pick","genlite.pickup",16,"minecraft:cobblestone","真实拾取")))
        val ch=chapter(listOf("start","upgrade","harvest")).copy(taskRequirements=mapOf("harvest" to listOf("upgrade")))
        s.configure(listOf(start,level,pick),listOf(ch)).await()
        s.sync("island",mapOf("island.member" to 1,"genlite.level:cobblestone_generator" to 5,"genlite.pickup:minecraft:cobblestone" to 100)).await()
        assertEquals(QuestStatus.COMPLETE,s.views("island").await().first { it.definition.id=="upgrade" }.status)
        assertEquals(0,s.views("island").await().last().progress.single().progress)
        s.sync("island",mapOf("genlite.level:cobblestone_generator" to 0,"genlite.pickup:minecraft:cobblestone" to 116)).await()
        assertEquals(QuestStatus.COMPLETE,s.views("island").await().last().status)
        db.closeAsync().await()
    }
    @Test fun `optional side never gates chapter and old chapter claim survives expansion and concurrent grants`() {
        var db=database();var s=QuestService(db);s.configure(listOf(start),listOf(chapter(listOf("start")))).await()
        s.sync("home",mapOf("island.member" to 1)).await();assertTrue(s.claimChapter("home","old", "sky_awakening",emptyMap(),false).await())
        db.closeAsync().await();db=database();s=QuestService(db)
        val added=start.copy(id="new_main",objectives=listOf(Objective("level","genlite.level",2,"cobblestone_generator","升级")))
        val side=start.copy(id="side",category=QuestCategory.SIDE,objectives=listOf(Objective("kill","combat.kill",3,"minecraft:zombie","击杀")))
        s.configure(listOf(start,added,side),listOf(chapter(listOf("start","new_main","side"),setOf("side")))).await()
        val facts=mapOf("island.member" to 1L,"genlite.level:cobblestone_generator" to 2L,"combat.kill:minecraft:zombie" to 0L)
        s.sync("home",facts).await()
        assertEquals(0,(1..20).count { s.claimChapter("home","member$it","sky_awakening",facts,false).await() })
        assertEquals(1,(1..20).count { s.claim("home","member$it","new_main",facts,false).await() })
        s.sync("fresh",facts).await()
        assertEquals(ChapterStatus.COMPLETE,s.journal("fresh").await().chapters.single().status)
        assertEquals(QuestStatus.ACTIVE,s.views("fresh").await().first { it.definition.id=="side" }.status)
        assertEquals(1,s.mailbox("home").await().count { it.quest=="chapter:sky_awakening" })
        db.closeAsync().await()
    }
    @Test fun `local successful actions survive reconnect and exclude actions before prerequisite`() {
        val db=database();val s=QuestService(db)
        val side=start.copy(id="trial",objectives=listOf(Objective("kill","combat.kill",3,"minecraft:zombie","击杀")))
        val ch=chapter(listOf("start","trial")).copy(taskRequirements=mapOf("trial" to listOf("start")))
        s.configure(listOf(start,side),listOf(ch)).await()
        s.record("home","combat.kill:minecraft:zombie",3,emptyMap()).await()
        s.sync("home",mapOf("island.member" to 1L,"combat.kill:minecraft:zombie" to 0L)).await()
        assertEquals(0,s.views("home").await().last().progress.single().progress)
        repeat(3) {s.record("home","combat.kill:minecraft:zombie",1,emptyMap()).await()}
        assertEquals(QuestStatus.COMPLETE,s.views("home").await().last().status)
        db.closeAsync().await()
        val reopened=database();val service=QuestService(reopened);service.configure(listOf(start,side),listOf(ch)).await()
        assertEquals(QuestStatus.COMPLETE,service.views("home").await().last().status)
        reopened.closeAsync().await()
    }
}
