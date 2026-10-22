package ym.quest.introduction

import ym.quest.domain.*
import ym.quest.text.TextRenderer

/** Personal display overlay only: shared graph, completions, chapter claims and rewards remain unchanged. */
class IntroductionJournal(private val settings: IntroductionSettings, private val text: TextRenderer) {
    fun quest(done: Boolean): QuestView {
        val objective = Objective("talk","personal.npc",1,null,text.language.getString("introduction.objective")!!)
        val definition = QuestDefinition(settings.taskId,text.language.getString("introduction.task-name")!!,
            text.language.getStringList("introduction.hints"),"minecraft:book",emptyList(),listOf(objective),emptyList())
        return QuestView(definition,if(done) QuestStatus.COMPLETE else QuestStatus.ACTIVE,listOf(ObjectiveProgress(objective,if(done) 1 else 0)))
    }
    fun decorate(journal: JournalView, done: Boolean): JournalView {
        val personal = quest(done)
        return JournalView(listOf(personal)+journal.quests,journal.chapters.map { chapter ->
            if(chapter.definition.id != settings.chapterId) chapter else chapter.copy(
                definition=chapter.definition.copy(quests=listOf(settings.taskId)+chapter.definition.quests),
                completed=chapter.completed + if(done) 1 else 0)
        })
    }
}
