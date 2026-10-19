package ym.quest.hud

import ym.quest.domain.*

/** Read-only projection: the HUD cannot change objective baselines, completions or rewards. */
object TrackerSnapshot {
    fun values(journal: JournalView): Map<String,String>? {
        val pending = journal.quests.count { it.status == QuestStatus.COMPLETE } +
            journal.chapters.count { it.status == ChapterStatus.COMPLETE && !it.claimed }
        val task = journal.quests.firstOrNull { it.status == QuestStatus.ACTIVE }
        val chapter = journal.chapters.firstOrNull { task?.definition?.id in it.definition.quests }
            ?: journal.chapters.firstOrNull { it.status == ChapterStatus.COMPLETE && !it.claimed }
        if(task == null && pending == 0) return null
        val objective = task?.progress?.firstOrNull { it.progress < it.objective.target } ?: task?.progress?.firstOrNull()
        return mapOf("chapter" to (chapter?.definition?.name ?: ""),
            "task" to (task?.definition?.name ?: ""),
            "objective" to (objective?.objective?.description ?: ""),
            "current" to (objective?.progress ?: 0L).toString(),
            "target" to (objective?.objective?.target ?: 0L).toString(),
            "progress" to (objective?.let { "${it.progress} / ${it.objective.target}" } ?: ""),
            "completed" to (chapter?.completed ?: 0).toString(),
            "total" to (chapter?.definition?.quests?.size ?: 0).toString(),
            "pending" to pending.toString())
    }
}
