package ym.quest.gui.journal

import ym.quest.domain.*

/** Read-only chapter/category filtering. Only the selected chapter contributes task rows. */
object JournalNavigation {
    fun tasks(view: JournalView,s: JournalSession)=view.quests.filter { q ->
        q.definition.category==s.category && view.chapters.any { it.definition.id==s.chapter && q.definition.id in it.definition.quests }
    }
    fun chapters(view: JournalView,s: JournalSession)=view.chapters.filter { ch -> view.quests.any { it.definition.category==s.category && it.definition.id in ch.definition.quests } }
}
