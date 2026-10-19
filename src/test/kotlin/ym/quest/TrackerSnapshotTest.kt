package ym.quest

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ym.quest.domain.*
import ym.quest.hud.TrackerSnapshot

class TrackerSnapshotTest {
    private val objective=Objective("produce","genlite.produce",32,"minecraft:cobblestone","生产圆石")
    private val q=QuestDefinition("first_resources","第一批资源",emptyList(),"minecraft:stone",emptyList(),listOf(objective),emptyList())
    private val ch=ChapterDefinition("sky_awakening","第一章","minecraft:stone",emptyList(),1,emptyList(),listOf(q.id),emptyList())
    @Test fun `active projection is read only and prefers the selected active task`() {
        val later=q.copy(id="later",name="后续任务")
        val journal=JournalView(listOf(QuestView(q,QuestStatus.ACTIVE,listOf(ObjectiveProgress(objective,7))),
            QuestView(later,QuestStatus.ACTIVE,listOf(ObjectiveProgress(objective,18)))),listOf(ChapterView(ch,ChapterStatus.ACTIVE,0,false)))
        val target=TrackerSnapshot.target("home",journal)!!
        assertEquals(7,target.current);assertEquals("7",target.values["current"]);assertEquals(ch.name,target.values["chapter"])
        assertEquals("later",TrackerSnapshot.target("home",journal,"later")!!.quest)
        assertEquals(q.id,TrackerSnapshot.target("home",journal,"missing")!!.quest)
        assertEquals(7,journal.quests.first().progress.single().progress)
    }
    @Test fun `finished claimed empty and locked contexts never produce a permanent panel`() {
        for(status in listOf(QuestStatus.COMPLETE,QuestStatus.CLAIMED,QuestStatus.LOCKED)) {
            val journal=JournalView(listOf(QuestView(q,status,emptyList())),listOf(ChapterView(ch,ChapterStatus.COMPLETE,1,status == QuestStatus.CLAIMED)))
            assertNull(TrackerSnapshot.target("home",journal))
        }
        assertNull(TrackerSnapshot.target("home",JournalView(emptyList(),emptyList())))
    }
    @Test fun `completed prior task does not block active next task or repeat its progress`() {
        val later=q.copy(id="later")
        val journal=JournalView(listOf(QuestView(q,QuestStatus.COMPLETE,emptyList()),
            QuestView(later,QuestStatus.ACTIVE,listOf(ObjectiveProgress(objective,0)))),emptyList())
        assertEquals("later",TrackerSnapshot.target("home",journal,q.id)!!.quest)
        assertEquals(0,TrackerSnapshot.target("home",journal)!!.current)
    }
}
