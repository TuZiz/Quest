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
    fun satisfied(c: Connection, home: String, quest: String) = completed(c,home,quest) || claimed(c,home,quest)
    fun views(c: Connection, home: String, definitions: List<QuestDefinition>, allowed: (QuestDefinition) -> Boolean = { true }): List<QuestView> = definitions.map { q ->
        val claimed = claimed(c,home,q.id)
        val unlocked = allowed(q) && q.prerequisites.all { satisfied(c,home,it) }
        val progress = q.objectives.map { o ->
            ObjectiveProgress(o, c.number("SELECT progress FROM objectives WHERE home=? AND quest=? AND objective=?",home,q.id,o.id).coerceAtMost(o.target))
        }
        val status = when { claimed -> QuestStatus.CLAIMED; completed(c,home,q.id) -> QuestStatus.COMPLETE; !unlocked -> QuestStatus.LOCKED
            else -> QuestStatus.ACTIVE }
        QuestView(q,status,progress)
    }
    fun refresh(c: Connection, home: String, definitions: List<QuestDefinition>, snapshot: Set<String>, allowed: (QuestDefinition) -> Boolean = { true }): List<ProgressChange> {
        val changes = mutableListOf<ProgressChange>()
        definitions.forEach { q ->
            if (claimed(c,home,q.id) || completed(c,home,q.id) || !allowed(q) || q.prerequisites.any { !satisfied(c,home,it) }) return@forEach
            // Do not initialize from cached v1 facts when a live integration snapshot is absent.
            val missing = q.objectives.filter { o -> c.number("SELECT COUNT(*) FROM objectives WHERE home=? AND quest=? AND objective=?",home,q.id,o.id) == 0L }
            if(missing.any { it.metric !in snapshot }) return@forEach
            val started = missing.size == q.objectives.size
            var changed = false
            val before = q.objectives.all { o -> c.number("SELECT progress FROM objectives WHERE home=? AND quest=? AND objective=?",home,q.id,o.id) >= o.target }
            q.objectives.forEach { o ->
                val value = fact(c,home,o.metric)
                // Membership is a state condition; cumulative event conditions only count after unlock.
                val baseline = if(o.state) 0 else value
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
            if(started) changes += ProgressChange(home,q.id,false,started=true)
            if(changed || (!before && after)) changes += ProgressChange(home,q.id,!before && after)
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
