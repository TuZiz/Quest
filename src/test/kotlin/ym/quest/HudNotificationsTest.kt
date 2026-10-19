package ym.quest

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ym.quest.hud.*
import ym.quest.domain.*

class HudNotificationsTest {
    private val first=QuestDefinition("first","能源启动",emptyList(),"minecraft:stone",emptyList(),emptyList(),emptyList())
    private val next=first.copy(id="next",name="第一份收获",prerequisites=listOf("first"))
    private val chapter=ChapterDefinition("chapter","第一章","minecraft:stone",emptyList(),1,emptyList(),listOf("first","next"),emptyList())
    private fun change(id: String, complete: Boolean=false, start: Boolean=false, chapter: Boolean=false)=ProgressChange("island",id,complete,start,chapter)
    @Test fun `completion and unlock combine without duplicated start or stage popup`() {
        val changes=listOf(change("first",true),change("next",start=true),change("next",start=true))
        val planned=HudNoticePlanner.plan(changes,listOf(first,next),listOf(chapter),true)
        assertEquals(1,planned.size); assertEquals("能源启动",planned.single().name); assertEquals("第一份收获",planned.single().next)
        assertEquals(2,HudNoticePlanner.plan(changes,listOf(first,next),listOf(chapter),false).size)
    }
    @Test fun `chapter completion supersedes final task but no other chapter`() {
        val planned=HudNoticePlanner.plan(listOf(change("next",true),change("chapter",true,chapter=true)),listOf(first,next),listOf(chapter),true)
        assertEquals(listOf("chapter-complete"),planned.map { it.event })
    }
    @Test fun `separate nearby callbacks merge before display and one timeline advances`() {
        val queue=HudNoticeQueue(4,80,true,6,8)
        queue.offer(HudNotice("island","first","task-complete","能源启动"),0)
        queue.offer(HudNotice("island","next","task-start","第一份收获",prerequisites=setOf("first")),2)
        assertNull(queue.frame(4))
        assertEquals("第一份收获",queue.frame(6)!!.notice.next)
        assertEquals(12,queue.frame(6)!!.offset); assertEquals(0,queue.frame(14)!!.offset)
        assertNull(queue.frame(86))
    }
    @Test fun `pending visible and recently closed notices deduplicate and bursts are bounded`() {
        val queue=HudNoticeQueue(2,80,true,0,0)
        val notice=HudNotice("island","first","task-complete","能源启动")
        queue.offer(notice,0); assertNotNull(queue.frame(0));queue.offer(notice,2)
        assertEquals(1,queue.size)
        assertNull(queue.frame(80));queue.offer(notice,82);assertNull(queue.frame(82))
        repeat(100) { queue.offer(notice.copy(id=it.toString()),100) }
        assertEquals(2,queue.size)
        assertEquals("98",queue.frame(100)!!.notice.id)
        assertEquals("99",queue.frame(180)!!.notice.id)
        assertNull(queue.frame(260))
    }
    @Test fun `fullwidth symbol and long mixed text fit actual monospaced budget`() {
        assertEquals("第一章 · 星海初醒",HudText.fit("第一章 · 星海初醒",20))
        assertEquals("生产圆石…",HudText.fit("生产圆石并等待资源收集",10))
        assertEquals("18/32",HudText.fit("18/32",6))
    }
}
