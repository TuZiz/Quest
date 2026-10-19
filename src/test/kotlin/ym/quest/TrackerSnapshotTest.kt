package ym.quest

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ym.quest.domain.*
import ym.quest.hud.TrackerSnapshot

class TrackerSnapshotTest {
    private val objective = Objective("produce","genlite.produce",32,"minecraft:cobblestone","生产原石")
    private val q = QuestDefinition("first_resources","第一批资源",emptyList(),"minecraft:stone",emptyList(),listOf(objective),emptyList())
    private val ch = ChapterDefinition("sky_awakening","第一章","minecraft:stone",emptyList(),1,emptyList(),listOf(q.id),emptyList())
    @Test fun `active progress and unpaid rewards share a read-only projection`() {
        val journal = JournalView(listOf(QuestView(q,QuestStatus.ACTIVE,listOf(ObjectiveProgress(objective,7)))),listOf(ChapterView(ch,ChapterStatus.ACTIVE,0,false)))
        val values = TrackerSnapshot.values(journal)!!
        assertEquals("7",values["current"]); assertEquals("32",values["target"])
        assertEquals(q.name,values["task"]); assertEquals("0",values["pending"])
        assertEquals(7,journal.quests.single().progress.single().progress)
    }
    @Test fun `completed chapter hides tracker without asking for manual claims`() {
        val unpaid = JournalView(listOf(QuestView(q,QuestStatus.COMPLETE,emptyList())),listOf(ChapterView(ch,ChapterStatus.COMPLETE,1,false)))
        assertNull(TrackerSnapshot.values(unpaid))
        val paid = JournalView(listOf(QuestView(q,QuestStatus.CLAIMED,emptyList())),listOf(ChapterView(ch,ChapterStatus.COMPLETE,1,true)))
        assertNull(TrackerSnapshot.values(paid))
    }
    @Test fun `a later active task is tracked while prior reward remains unclaimed`() {
        val later=q.copy(id="later",name="后续任务")
        val journal=JournalView(listOf(QuestView(q,QuestStatus.COMPLETE,emptyList()),QuestView(later,QuestStatus.ACTIVE,listOf(ObjectiveProgress(objective,0)))),emptyList())
        assertEquals(later.name,TrackerSnapshot.values(journal)!!["task"])
        assertEquals("1",TrackerSnapshot.values(journal)!!["pending"])
    }
}
