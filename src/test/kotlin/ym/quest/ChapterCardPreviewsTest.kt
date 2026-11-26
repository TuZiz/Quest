package ym.quest

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ym.quest.domain.*
import ym.quest.gui.journal.*
import java.util.UUID

class ChapterCardPreviewsTest {
    private fun menu() = YamlConfiguration().apply {
        set("cards.schema-version",1)
        set("cards.blank-item","quest_journal:blank")
        set("cards.base-item","minecraft:paper")
        set("cards.fallback-model","quest_journal:cards/unknown")
        set("Shape",listOf("111222333","111222333","444555666","444555666","HR#PLNSUX"))
        for(symbol in "123456") set("components.$symbol.action","journal-chapter")
        set("cards.chapter-preview-category","MAIN")
        set("cards.chapter-preview-lore",listOf("<#9CAEB1>尚未开放"))
        for(order in 2..6) preview(order)
    }
    private fun YamlConfiguration.preview(order: Int) {
        val p="cards.chapter-previews.chapter_$order"
        set("$p.order",order);set("$p.name","<#B3C0BD>第${order}章")
        set("$p.item","minecraft:book");set("$p.visual-model","quest_journal:cards/preview_$order")
    }
    private fun view(order: Int = 1, category: QuestCategory = QuestCategory.MAIN): JournalView {
        val q=QuestDefinition("q","q",emptyList(),"minecraft:book",emptyList(),emptyList(),emptyList(),category)
        val ch=ChapterDefinition("real","real","minecraft:book",emptyList(),order,emptyList(),listOf("q"),emptyList())
        return JournalView(listOf(QuestView(q,QuestStatus.ACTIVE,emptyList())),listOf(ChapterView(ch,ChapterStatus.ACTIVE,0,false)))
    }
    private fun session()=JournalSession(UUID.randomUUID(),null,JournalPage.CHAPTERS)
    @Test fun `six chapter covers are display only and do not modify the journal`() {
        val y=menu();val view=view();val entries=ChapterCardPreviews.entries(y,view,session())
        assertEquals((1..6).toList(),entries.map { it.order })
        assertSame(view.chapters.single(),entries.first().chapter)
        assertTrue(entries.drop(1).all { it.chapter==null && it.preview!=null })
        assertEquals(1,view.chapters.size);assertEquals(1,view.quests.size)
    }
    @Test fun `real chapter replaces matching cover even when its category differs`() {
        val y=menu();val view=view(2)
        val entries=ChapterCardPreviews.entries(y,view,session())
        assertEquals(1,entries.count { it.order==2 });assertNotNull(entries.first().chapter)
        val hidden=ChapterCardPreviews.entries(y,view(2,QuestCategory.SIDE),session())
        assertFalse(hidden.any { it.order==2 })
    }
    @Test fun `previews follow category and can paginate beyond six entries`() {
        val y=menu();y.preview(7)
        val s=session();val entries=ChapterCardPreviews.entries(y,view(),s)
        assertEquals(7,entries.size);assertEquals(7,entries.drop(6).single().order)
        s.category=QuestCategory.SPECIAL
        assertTrue(ChapterCardPreviews.entries(y,view(),s).isEmpty())
        y.set("cards.chapter-previews",null);s.category=QuestCategory.MAIN
        assertEquals(1,ChapterCardPreviews.entries(y,view(),s).size)
    }
    @Test fun `invalid preview values fail with their configuration path`() {
        val y=menu();y.set("cards.chapter-previews.chapter_3.order",2)
        assertTrue(assertThrows(IllegalArgumentException::class.java) { ChapterCardPreviews.validate(y) }.message!!.contains("duplicate order"))
        y.set("cards.chapter-previews.chapter_3.order",3);y.set("cards.chapter-previews.chapter_2.visual-model","bad model")
        assertTrue(assertThrows(IllegalArgumentException::class.java) { ChapterCardPreviews.validate(y) }.message!!.contains("chapter_2.visual-model"))
    }
    @Test fun `ALL shares covers across categories but tasks still use their selected category`() {
        val y=menu();y.set("cards.chapter-preview-category","ALL")
        ChapterCardPreviews.validate(y)
        val view=view();val s=session();s.chapter="real"
        for(category in QuestCategory.entries) {
            s.category=category
            val entries=ChapterCardPreviews.entries(y,view,s)
            assertEquals((1..6).toList(),entries.map { it.order })
            assertSame(view.chapters.single(),entries.first().chapter)
            assertTrue(entries.drop(1).all { it.preview!=null })
            assertEquals(if(category==QuestCategory.MAIN) 1 else 0,JournalNavigation.tasks(view,s).size)
        }
        y.preview(7)
        for(category in QuestCategory.entries) {
            s.category=category
            assertEquals(7,ChapterCardPreviews.entries(y,view,s).size)
        }
    }
}
