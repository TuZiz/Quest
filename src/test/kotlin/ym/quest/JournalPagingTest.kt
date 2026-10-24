package ym.quest

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ym.quest.gui.journal.*
import java.util.UUID

class JournalPagingTest {
    @Test fun `page dependencies include content navigation and page labels but exclude fixed controls`() {
        fun component(action: String?, name: String="", lore: List<String> = emptyList()) =
            YamlConfiguration().apply { set("action",action);set("name",name);set("lore",lore) }
        for(action in listOf("journal-chapter","journal-quest","previous","next"))
            assertTrue(JournalPageSlots.dependsOnPage(component(action)))
        assertTrue(JournalPageSlots.dependsOnPage(component(null,name="第<page>页")))
        assertTrue(JournalPageSlots.dependsOnPage(component(null,lore=listOf("<page>/<pages>"))))
        for(action in listOf(null,"home","settings","close","category-cycle"))
            assertFalse(JournalPageSlots.dependsOnPage(component(action,name="固定按钮")))
    }
    @Test fun `rapid page changes stop at both boundaries and can reverse immediately`() {
        val s=JournalSession(UUID.randomUUID(),null,JournalPage.TASKS).apply { pages=3 }
        assertFalse(s.turnPage(-1))
        assertTrue(s.turnPage(1));assertEquals(1,s.page)
        assertTrue(s.turnPage(1));assertEquals(2,s.page)
        repeat(20) { assertFalse(s.turnPage(1)) }
        assertTrue(s.turnPage(-1));assertEquals(1,s.page)
        assertTrue(s.turnPage(-1));assertEquals(0,s.page)
        repeat(20) { assertFalse(s.turnPage(-1)) }
    }
}
