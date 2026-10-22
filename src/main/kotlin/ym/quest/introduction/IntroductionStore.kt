package ym.quest.introduction

import ym.quest.storage.Database
import ym.quest.storage.rows
import ym.quest.storage.update
import java.util.UUID

enum class IntroductionOutcome { DONE, SKIPPED, LEGACY }

/** All JDBC work is serialized on Quest's existing database executor. No team/reward writes. */
class IntroductionStore(private val db: Database) {
    fun load(player: UUID, task: String) = db.submit { db.transaction { c ->
        c.rows("SELECT outcome FROM personal_introduction WHERE player=? AND task=?",player.toString(),task) {
            IntroductionOutcome.valueOf(it.getString(1))
        }.firstOrNull()
    } }
    fun complete(player: UUID, task: String, outcome: IntroductionOutcome) = db.submit { db.transaction { c ->
        c.update("INSERT OR IGNORE INTO personal_introduction(player,task,outcome,completed) VALUES(?,?,?,?)",
            player.toString(),task,outcome.name,System.currentTimeMillis())
        Unit
    } }
}
