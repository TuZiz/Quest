package ym.quest

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ym.quest.domain.*
import ym.quest.storage.*
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

class PersonalQuestTest {
    @TempDir lateinit var root: Path
    private val alice=UUID.randomUUID().toString()
    private val bob=UUID.randomUUID().toString()
    private fun <T> CompletableFuture<T>.await()=get(5,TimeUnit.SECONDS)
    private val shared=QuestDefinition("business","经营",emptyList(),"minecraft:stone",emptyList(),listOf(Objective("fuel","genlite.fuel",1,null,"燃料")),listOf(Reward("minecraft:bread",1)))
    private val trial=QuestDefinition("first_trial","石址试炼",emptyList(),"minecraft:stone",listOf("business"),listOf(Objective("kill","combat.kill",3,"hostile","击杀")),listOf(Reward("minecraft:bread",8)),QuestCategory.SIDE,scope=ProgressScope.PERSONAL)
    private val loot=QuestDefinition("stone_site_materials","寻材",emptyList(),"minecraft:iron_ingot",listOf("first_trial"),listOf(Objective("iron","combat.stone-loot",4,"iron","铁片")),emptyList(),QuestCategory.SIDE,scope=ProgressScope.PERSONAL)
    private fun withDb(block: (Database,QuestService)->Unit) {
        val db=Database(root.resolve("quest.db"),Logger.getAnonymousLogger());db.open().await()
        try { block(db,QuestService(db)) } finally { db.closeAsync().await() }
    }
    private fun prepare(s: QuestService) {
        s.configure(listOf(shared,trial,loot)).await()
        s.record("island","genlite.fuel",1,emptyMap()).await()
    }
    private fun quest(s: QuestService, player: String, id: String="first_trial",home: String="island")=s.journal(home,player).await().quests.single { it.definition.id==id }

    @Test fun `same island members share business but never combat progress or rewards`()=withDb { _,s ->
        prepare(s)
        assertEquals(QuestStatus.COMPLETE,quest(s,bob,"business").status)
        s.recordPersonal("island",alice,"combat.kill:hostile",3).await()
        assertEquals(QuestStatus.COMPLETE,quest(s,alice).status)
        assertEquals(0L,quest(s,bob).progress.single().progress)
        assertEquals(QuestStatus.LOCKED,quest(s,bob,"stone_site_materials").status)
        assertEquals(listOf("first_trial"),s.claimPersonalCompleted("island",alice,setOf("first_trial")).await())
        assertTrue(s.claimPersonalCompleted("island",alice,setOf("first_trial")).await().isEmpty())
        assertTrue(s.claimPersonalCompleted("island",bob,setOf("first_trial")).await().isEmpty())
        assertEquals(8,s.mailbox(PersonalQuests.owner(alice)).await().single().amount)
        assertTrue(s.mailbox(PersonalQuests.owner(bob)).await().isEmpty())
        assertTrue(s.mailbox("island").await().isEmpty())
        assertEquals(listOf("business"),s.claimCompleted("island",alice,emptyMap(),false,setOf("business","first_trial")).await())
        assertTrue(s.claimCompleted("island",bob,emptyMap(),false,setOf("business")).await().isEmpty())
    }
    @Test fun `personal gates and loot baseline use only that players actions`()=withDb { _,s ->
        s.configure(listOf(shared,trial,loot)).await()
        s.recordPersonal("island",alice,"combat.kill:hostile",2).await()
        assertEquals(QuestStatus.LOCKED,quest(s,alice).status)
        s.record("island","genlite.fuel",1,emptyMap()).await()
        s.recordPersonal("island",alice,"combat.kill:hostile",1).await()
        assertEquals(1L,quest(s,alice).progress.single().progress)
        s.recordPersonal("island",alice,"combat.stone-loot:iron",40).await()
        s.recordPersonal("island",alice,"combat.kill:hostile",2).await()
        assertEquals(0L,quest(s,alice,"stone_site_materials").progress.single().progress)
        s.recordPersonal("island",bob,"combat.stone-loot:iron",40).await()
        s.recordPersonal("island",alice,"combat.stone-loot:iron",4).await()
        assertEquals(QuestStatus.COMPLETE,quest(s,alice,"stone_site_materials").status)
        assertEquals(QuestStatus.LOCKED,quest(s,bob,"stone_site_materials").status)
    }
    @Test fun `UUID progress survives restart and moving islands without reward replay`() {
        withDb { _,s -> prepare(s);s.recordPersonal("island",alice,"combat.kill:hostile",2).await() }
        withDb { _,s ->
            s.configure(listOf(shared,trial,loot)).await()
            s.record("new-island","genlite.fuel",1,emptyMap()).await()
            assertEquals(2L,quest(s,alice,home="new-island").progress.single().progress)
            s.recordPersonal("new-island",alice,"combat.kill:hostile",1).await()
            s.claimPersonalCompleted("new-island",alice,setOf("first_trial")).await()
        }
        withDb { _,s ->
            s.configure(listOf(shared,trial,loot)).await()
            assertTrue(s.claimPersonalCompleted("island",alice,setOf("first_trial")).await().isEmpty())
            assertEquals(1,s.mailbox(PersonalQuests.owner(alice)).await().size)
        }
    }
    @Test fun `legacy rewards preserve recipient parcel IDs uncertain phases and shared history`()=withDb { db,s ->
        db.submit { db.transaction { c ->
            c.update("INSERT INTO claims VALUES(?,?,?,?)","island","first_trial",alice,1)
            c.update("INSERT INTO completions VALUES(?,?,?)","island","first_trial",1)
            listOf("DELIVERED","SENDING","WAITING").forEach { phase ->
                c.update("INSERT INTO parcels VALUES(?,?,?,?,?,?,?)",phase,"island","first_trial","minecraft:bread",1,phase,if(phase=="WAITING") null else alice)
            }
        } }.await()
        prepare(s);s.configure(listOf(shared,trial,loot)).await()
        java.sql.DriverManager.getConnection("jdbc:sqlite:${root.resolve("backups/before-personal-combat-v1.db")}").use { backup ->
            assertEquals(3L,backup.number("SELECT COUNT(*) FROM parcels WHERE home='island'"))
            assertEquals(0L,backup.number("SELECT COUNT(*) FROM claims WHERE home LIKE 'player:%'"))
        }
        assertEquals(QuestStatus.CLAIMED,quest(s,alice).status)
        assertEquals(QuestStatus.ACTIVE,quest(s,bob).status)
        assertTrue(s.mailbox("island").await().isEmpty())
        assertEquals(setOf("DELIVERED","SENDING","WAITING"),s.mailbox(PersonalQuests.owner(alice)).await().map { it.id }.toSet())
        assertTrue(s.claimPersonalCompleted("island",alice,setOf("first_trial")).await().isEmpty())
        db.submit { db.transaction { c ->
            assertEquals(1L,c.number("SELECT COUNT(*) FROM claims WHERE home='island' AND quest='first_trial'"))
            assertEquals(1L,c.number("SELECT COUNT(*) FROM audit WHERE action='personal-scope-v1:first_trial'"))
        } }.await()
    }
    @Test fun `shared tasks cannot depend on personal progression or personal chapter rewards`() {
        assertThrows(IllegalArgumentException::class.java) { ChapterGraph.prepare(listOf(shared.copy(prerequisites=listOf("first_trial")),trial.copy(prerequisites=emptyList())),emptyList()) }
        val chapter=ChapterDefinition("one","章节","minecraft:book",emptyList(),1,emptyList(),listOf("business","first_trial"),emptyList())
        assertThrows(IllegalArgumentException::class.java) { ChapterGraph.prepare(listOf(shared,trial),listOf(chapter)) }
    }
}
