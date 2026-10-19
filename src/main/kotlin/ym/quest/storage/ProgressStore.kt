package ym.quest.storage

import ym.quest.domain.*
import java.sql.Connection

/** Called only within a Database transaction. Facts are monotonic; objectives start at unlock time. */
class ProgressStore {
    fun facts(c: Connection, home: String, values: Map<String, Long>) {
        values.forEach { (metric, value) ->
            require(value >= 0)
            c.update("INSERT INTO facts VALUES(?,?,?) ON CONFLICT(home,metric) DO UPDATE SET value=MAX(value,excluded.value)",home,metric,value)
        }
    }
    private fun fact(c: Connection, home: String, metric: String) = c.number("SELECT value FROM facts WHERE home=? AND metric=?",home,metric)
    fun claimed(c: Connection, home: String, quest: String) = c.number("SELECT COUNT(*) FROM claims WHERE home=? AND quest=?",home,quest) != 0L
    private fun completed(c: Connection, home: String, quest: String) = c.number("SELECT COUNT(*) FROM completions WHERE home=? AND quest=?",home,quest) != 0L
    fun views(c: Connection, home: String, definitions: List<QuestDefinition>): List<QuestView> = definitions.map { q ->
        val claimed = claimed(c,home,q.id)
        val unlocked = q.prerequisites.all { claimed(c,home,it) }
        val progress = q.objectives.map { o ->
            ObjectiveProgress(o, c.number("SELECT progress FROM objectives WHERE home=? AND quest=? AND objective=?",home,q.id,o.id).coerceAtMost(o.target))
        }
        val status = when { claimed -> QuestStatus.CLAIMED; !unlocked -> QuestStatus.LOCKED
            completed(c,home,q.id) || progress.all { it.progress >= it.objective.target } -> QuestStatus.COMPLETE
            else -> QuestStatus.ACTIVE }
        QuestView(q,status,progress)
    }
    fun refresh(c: Connection, home: String, definitions: List<QuestDefinition>): List<ProgressChange> {
        val changes = mutableListOf<ProgressChange>()
        definitions.forEach { q ->
            if (claimed(c,home,q.id) || completed(c,home,q.id) || q.prerequisites.any { !claimed(c,home,it) }) return@forEach
            var changed = false
            val before = q.objectives.all { o -> c.number("SELECT progress FROM objectives WHERE home=? AND quest=? AND objective=?",home,q.id,o.id) >= o.target }
            q.objectives.forEach { o ->
                val value = fact(c,home,o.metric)
                // Membership is a state condition; cumulative event conditions only count after unlock.
                val baseline = if(o.type == "island.member") 0 else value
                c.update("INSERT OR IGNORE INTO objectives(home,quest,objective,baseline,progress) VALUES(?,?,?,?,0)",home,q.id,o.id,baseline)
                val row = c.rows("SELECT baseline,progress FROM objectives WHERE home=? AND quest=? AND objective=?",home,q.id,o.id) { it.getLong(1) to it.getLong(2) }.single()
                val next = (value - row.first).coerceIn(0,o.target).coerceAtLeast(row.second)
                if(next > row.second) {
                    changed = true
                    c.update("UPDATE objectives SET progress=? WHERE home=? AND quest=? AND objective=?",next,home,q.id,o.id)
                }
            }
            val after = q.objectives.all { o -> c.number("SELECT progress FROM objectives WHERE home=? AND quest=? AND objective=?",home,q.id,o.id) >= o.target }
            if(after) c.update("INSERT OR IGNORE INTO completions VALUES(?,?,?)",home,q.id,System.currentTimeMillis())
            if(changed) {
                changes += ProgressChange(home,q.id,!before && after)
            }
        }
        return changes
    }
    fun reset(c: Connection, home: String, quest: String, actor: String) {
        // Never delete claims or parcels: resetting progress must not create a second reward.
        c.update("DELETE FROM objectives WHERE home=? AND quest=?",home,quest)
        c.update("DELETE FROM completions WHERE home=? AND quest=?",home,quest)
        c.update("INSERT INTO audit VALUES(?,?,?,?)",System.currentTimeMillis(),actor,"reset","$home/$quest; claims retained")
    }
}
