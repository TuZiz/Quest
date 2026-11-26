package ym.quest

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ym.quest.gui.journal.JournalCardLayout
import ym.quest.gui.journal.JournalCardVisuals
import ym.quest.domain.*

class JournalCardLayoutTest {
    private fun menu() = YamlConfiguration().apply {
        set("cards.schema-version",1)
        set("cards.blank-item","example:blank")
        set("cards.base-item","minecraft:paper")
        set("cards.fallback-model","example:unknown")
        set("Shape",listOf("#######S#","AAABBBCCC","AAABBBCCC","DDDEEEFFF","DDDEEEFFF","RR#PL#N#X"))
        for (symbol in "ABCDEF") set("components.$symbol.action","journal-quest")
    }
    @Test fun `six cards map all 36 slots without multiplying page capacity`() {
        val layout=JournalCardLayout.parse(menu())!!
        assertEquals(6,layout.capacity)
        assertEquals(listOf(10,13,16,28,31,34),layout.groups.map { it[1] })
        for ((index,slots) in layout.groups.withIndex()) for (slot in slots) {
            assertEquals(index,layout.entry(slot))
            assertEquals(slot==slots[1],layout.anchor(slot))
        }
        assertNull(layout.entry(0));assertNull(layout.entry(53))
    }
    @Test fun `reject irregular rectangles and retain legacy menus`() {
        val y=menu();y.set("Shape",listOf("#######S#","AAABBBCCC","AABABBCCC","DDDEEEFFF","DDDEEEFFF","RR#PL#N#X"))
        assertThrows(IllegalArgumentException::class.java) { JournalCardLayout.parse(y) }
        y.set("cards",null);assertNull(JournalCardLayout.parse(y))
    }
    @Test fun `actual progress includes all objectives and caps each at its target`() {
        val a=Objective("a","genlite.transfer",16,null,"a")
        val b=Objective("b","genlite.extract",8,null,"b")
        val def=QuestDefinition("q","q",emptyList(),"minecraft:book",emptyList(),listOf(a,b),emptyList())
        val view=QuestView(def,QuestStatus.ACTIVE,listOf(ObjectiveProgress(a,22),ObjectiveProgress(b,3)))
        assertEquals(19L to 24L,JournalCardVisuals.progress(view,null))
    }
}
