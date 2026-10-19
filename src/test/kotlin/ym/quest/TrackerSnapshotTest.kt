package ym.quest

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ym.quest.domain.*
import ym.quest.hud.TrackerSnapshot

class TrackerSnapshotTest {
    private val completedText = mapOf("task" to "完成状态", "objective" to "完成说明")
    private val objective = Objective("produce","genlite.produce",32,"minecraft:cobblestone","生产原石")
    private val q = QuestDefinition("first_resources","第一批资源",emptyList(),"minecraft:stone",emptyList(),listOf(objective),emptyList())
    private val ch = ChapterDefinition("sky_awakening","第一章","minecraft:stone",emptyList(),1,emptyList(),listOf(q.id),emptyList())
    @Test fun `active progress and unpaid rewards share a read-only projection`() {
        val journal = JournalView(listOf(QuestView(q,QuestStatus.ACTIVE,listOf(ObjectiveProgress(objective,7)))),listOf(ChapterView(ch,ChapterStatus.ACTIVE,0,false)))
        val values = TrackerSnapshot.values(journal,completedText)!!
        assertEquals("7",values["current"]); assertEquals("32",values["target"])
        assertEquals(q.name,values["task"]); assertEquals("0",values["pending"])
        assertEquals(7,journal.quests.single().progress.single().progress)
    }
    @Test fun `completed chapter stays visible by default regardless of rewards and hides only when configured`() {
        val unpaid = JournalView(listOf(QuestView(q,QuestStatus.COMPLETE,emptyList())),listOf(ChapterView(ch,ChapterStatus.COMPLETE,1,false)))
        assertNotNull(TrackerSnapshot.values(unpaid,completedText))
        assertNull(TrackerSnapshot.values(unpaid,completedText,true))
        assertEquals("完成状态",TrackerSnapshot.values(unpaid,completedText,false)!!["task"])
        val paid = JournalView(listOf(QuestView(q,QuestStatus.CLAIMED,emptyList())),listOf(ChapterView(ch,ChapterStatus.COMPLETE,1,true)))
        assertNotNull(TrackerSnapshot.values(paid,completedText))
        assertNull(TrackerSnapshot.values(paid,completedText,true))
        val values = TrackerSnapshot.values(paid,completedText,false)!!
        assertEquals("完成状态",values["task"])
        assertEquals("完成说明",values["objective"])
        assertEquals("1 / 1",values["progress"])
        assertEquals("0",values["pending"])
    }
    @Test fun `a later active task is tracked while prior reward remains unclaimed`() {
        val later=q.copy(id="later",name="后续任务")
        val journal=JournalView(listOf(QuestView(q,QuestStatus.COMPLETE,emptyList()),QuestView(later,QuestStatus.ACTIVE,listOf(ObjectiveProgress(objective,0)))),emptyList())
        assertEquals(later.name,TrackerSnapshot.values(journal,completedText)!!["task"])
        assertEquals("1",TrackerSnapshot.values(journal,completedText)!!["pending"])
    }
    @Test fun `locked future chapter does not replace completed chapter`() {
        val future = ch.copy(id="future",name="后续章节")
        val journal = JournalView(listOf(QuestView(q,QuestStatus.CLAIMED,emptyList())),listOf(
            ChapterView(ch,ChapterStatus.COMPLETE,1,true), ChapterView(future,ChapterStatus.LOCKED,0,false)))
        assertEquals(ch.name,TrackerSnapshot.values(journal,completedText)!!["chapter"])
        assertNull(TrackerSnapshot.values(journal,completedText,true))
        assertEquals(ch.name,TrackerSnapshot.values(journal,completedText,false)!!["chapter"])
    }
    @Test fun `empty or entirely locked journal has no completed fallback`() {
        assertNull(TrackerSnapshot.values(JournalView(emptyList(),emptyList()),completedText))
        assertNull(TrackerSnapshot.values(JournalView(listOf(QuestView(q,QuestStatus.LOCKED,emptyList())),
            listOf(ChapterView(ch,ChapterStatus.LOCKED,0,false))),completedText))
    }
}
