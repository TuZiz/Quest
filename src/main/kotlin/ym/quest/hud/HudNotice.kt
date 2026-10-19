package ym.quest.hud

import ym.quest.domain.*

/** Read-only presentation events; never changes quest/reward state. */
data class HudNotice(val home: String, val id: String, val event: String, val name: String,
                     val next: String? = null, val prerequisites: Set<String> = emptySet()) {
    val key get() = "$home:$event:$id"
}

object HudNoticePlanner {
    fun plan(changes: List<ProgressChange>, quests: List<QuestDefinition>, chapters: List<ChapterDefinition>, merge: Boolean): List<HudNotice> {
        val unique = changes.distinctBy { listOf(it.home,it.quest,it.chapter,it.completed,it.started) }
        val result = mutableListOf<HudNotice>()
        unique.groupBy { it.home }.forEach { (home, batch) ->
            val finished = batch.filter { it.chapter && it.completed }.map { it.quest }.toSet()
            val covered = if(merge) chapters.filter { it.id in finished }.flatMap { it.quests }.toSet() else emptySet()
            val starts = batch.filter { !it.chapter && it.started && !it.completed }.mapNotNull { c -> quests.find { it.id == c.quest } }
            val used = mutableSetOf<String>()
            batch.filter { !it.chapter && it.completed && it.quest !in covered }.forEach { c ->
                val q = quests.find { it.id == c.quest } ?: return@forEach
                val next = if(merge) starts.firstOrNull { q.id in it.prerequisites && it.id !in used } else null
                next?.let { used.add(it.id) }
                result += HudNotice(home,q.id,"task-complete",q.name,next?.name)
            }
            starts.filter { it.id !in used && it.id !in covered }.forEach { q ->
                result += HudNotice(home,q.id,"task-start",q.name,prerequisites=q.prerequisites.toSet())
            }
            batch.filter { it.chapter && (it.completed || it.started) }.forEach { c ->
                val chapter = chapters.find { it.id == c.quest } ?: return@forEach
                // The first active task already announces a new chapter's actionable objective.
                if(c.completed || starts.isEmpty()) result += HudNotice(home,c.quest,if(c.completed) "chapter-complete" else "chapter-start",chapter.name)
            }
        }
        return result
    }
}
