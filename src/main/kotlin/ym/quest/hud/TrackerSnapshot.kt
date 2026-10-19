package ym.quest.hud

import ym.quest.domain.*

data class TrackerTarget(val home: String, val quest: String, val objective: String,
                         val current: Long, val target: Long, val values: Map<String,String>) {
    val key get() = "$home:$quest:$objective"
}

/** Read-only projection. Completion/rewards never produce a permanent fallback panel. */
object TrackerSnapshot {
    fun target(home: String, journal: JournalView, preferredQuest: String? = null): TrackerTarget? {
        val active=journal.quests.filter { it.status == QuestStatus.ACTIVE }
        val task=active.find { it.definition.id == preferredQuest } ?: active.firstOrNull() ?: return null
        val objective=task.progress.firstOrNull { it.progress < it.objective.target } ?: task.progress.firstOrNull() ?: return null
        val chapter=journal.chapters.firstOrNull { task.definition.id in it.definition.quests }
        return TrackerTarget(home,task.definition.id,objective.objective.id,objective.progress,objective.objective.target,
            mapOf("chapter" to (chapter?.definition?.name ?: ""),"task" to task.definition.name,
                "objective" to objective.objective.description,"current" to objective.progress.toString(),"target" to objective.objective.target.toString()))
    }
}
