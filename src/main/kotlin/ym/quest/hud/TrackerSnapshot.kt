package ym.quest.hud

import ym.quest.domain.*

/** Read-only projection: the HUD cannot change objective baselines, completions or rewards. */
object TrackerSnapshot {
    @JvmOverloads fun values(journal: JournalView, completedText: Map<String,String>, hideCompleted: Boolean = true): Map<String,String>? {
        val pending = journal.quests.count { it.status == QuestStatus.COMPLETE } +
            journal.chapters.count { it.status == ChapterStatus.COMPLETE && !it.claimed }
        val task = journal.quests.firstOrNull { it.status == QuestStatus.ACTIVE }
        if(task == null && hideCompleted) return null
        val chapter = journal.chapters.firstOrNull { task?.definition?.id in it.definition.quests }
            ?: journal.chapters.lastOrNull { it.status == ChapterStatus.COMPLETE }
        if(task == null && chapter == null) return null
        val objective = task?.progress?.firstOrNull { it.progress < it.objective.target } ?: task?.progress?.firstOrNull()
        return mapOf("chapter" to (chapter?.definition?.name ?: ""),
            "task" to (task?.definition?.name ?: completedText.getValue("task")),
            "objective" to (objective?.objective?.description ?: if(task == null) completedText.getValue("objective") else ""),
            "current" to (objective?.progress ?: if(task == null) chapter!!.completed else 0).toString(),
            "target" to (objective?.objective?.target ?: if(task == null) chapter!!.definition.quests.size else 0).toString(),
            "progress" to (objective?.let { "${it.progress} / ${it.objective.target}" } ?: if(task == null) "${chapter!!.completed} / ${chapter.definition.quests.size}" else ""),
            "completed" to (chapter?.completed ?: 0).toString(),
            "total" to (chapter?.definition?.quests?.size ?: 0).toString(),
            "pending" to pending.toString())
    }
}
