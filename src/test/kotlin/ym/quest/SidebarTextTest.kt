package ym.quest

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ym.quest.hud.SidebarText
import ym.quest.hud.TrackerTarget

class SidebarTextTest {
    private fun target(n: Long, total: Long=32) = TrackerTarget("island","first_resources","produce",n,total,
        mapOf("chapter" to "✦ 第一章 · 星海初醒", "task" to "利用原石生成器获取32个原石",
            "objective" to "用生成器产出 32 个原石", "current" to "$n", "target" to "$total"))
    @Test fun `real objective and chapter preserve text and quarter progress uses two full blocks`() {
        val values=SidebarText.values(target(8),true,true) { it }
        assertEquals("第一章 · 星海初醒",values["chapter"])
        assertEquals("用生成器产出 32 个原石",values["goal"])
        assertEquals("",values["goal_more"])
        assertEquals("8",values["current"]);assertEquals("32",values["target"])
        assertEquals("25",values["percent"]);assertEquals("██",values["bar_full"])
        assertEquals("░".repeat(8),values["bar_empty"])
    }
    @Test fun `text wraps without splitting code points or losing Chinese goal`() {
        val text="利用原石生成器获取32个原石"
        val lines=SidebarText.lines(text,18)
        assertEquals(2,lines.size);assertEquals(text,lines.joinToString(""))
    }
    @Test fun `zero middle full and malformed counts remain safe`() {
        for((count,percent,filled) in listOf(Triple(0L,"0",0),Triple(18L,"56",5),Triple(32L,"100",10),Triple(60L,"100",10),Triple(-1L,"0",0))) {
            val values=SidebarText.values(target(count),false,false) { it }
            assertEquals(percent,values["percent"]);assertEquals(filled,values.getValue("bar_full").length)
            assertEquals(10,values.getValue("bar_full").length+values.getValue("bar_empty").length)
            assertEquals("false",values["show_background"]);assertEquals("false",values["show_bar"])
        }
        assertEquals("0",SidebarText.values(target(2,0),true,true) { it }["percent"])
    }
}
