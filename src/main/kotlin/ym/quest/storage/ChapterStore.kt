package ym.quest.storage

import ym.quest.domain.*
import java.sql.Connection

/** Same transaction and reward ledger as quests; chapter keys cannot collide with valid quest IDs. */
class ChapterStore {
    fun complete(c: Connection, home: String, chapter: String) = c.number(
        "SELECT COUNT(*) FROM chapter_progress WHERE home=? AND chapter=? AND completed IS NOT NULL",home,chapter) > 0
    fun eligible(c: Connection, home: String, chapter: ChapterDefinition) = chapter.prerequisites.all { complete(c,home,it) }
    fun refresh(c: Connection, home: String, chapters: List<ChapterDefinition>, progress: ProgressStore): List<ProgressChange> = buildList {
        chapters.forEach { ch ->
            if(!eligible(c,home,ch)) return@forEach
            if(c.update("INSERT OR IGNORE INTO chapter_progress(home,chapter,started) VALUES(?,?,?)",home,ch.id,System.currentTimeMillis()) == 1)
                add(ProgressChange(home,ch.id,false,started=true,chapter=true))
            if(ch.requiredQuests.all { progress.satisfied(c,home,it) } &&
                c.update("UPDATE chapter_progress SET completed=? WHERE home=? AND chapter=? AND completed IS NULL",System.currentTimeMillis(),home,ch.id) == 1)
                add(ProgressChange(home,ch.id,true,chapter=true))
        }
    }
    fun views(c: Connection, home: String, chapters: List<ChapterDefinition>, progress: ProgressStore) = chapters.sortedBy { it.order }.map { ch ->
        ChapterView(ch,when { complete(c,home,ch.id) -> ChapterStatus.COMPLETE
            !eligible(c,home,ch) -> ChapterStatus.LOCKED; else -> ChapterStatus.ACTIVE },
            ch.requiredQuests.count { progress.satisfied(c,home,it) },progress.claimed(c,home,"chapter:${ch.id}"))
    }
}
