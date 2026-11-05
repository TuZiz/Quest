package ym.quest.storage

import java.sql.Connection
import ym.quest.domain.QuestDefinition

/** Database owner transaction only. Retain earned progress when the stock trial changes counter. */
internal object TrialCombatProgressUpgrade {
    fun migrate(c: Connection, definitions: List<QuestDefinition>) {
        if(definitions.none { q -> q.id=="first_trial" && q.objectives.any { it.id=="experience" && it.metric=="combat.kill:hostile" } }) return
        val action="trial-hostile-v1"
        if(c.number("SELECT COUNT(*) FROM audit WHERE actor=? AND action=?","Quest",action)>0) return
        // A negative baseline is valid: it carries old earned progress into the new absolute counter.
        c.update("UPDATE objectives SET baseline=COALESCE((SELECT value FROM facts WHERE facts.home=objectives.home AND metric='combat.kill:hostile'),0)-progress WHERE quest='first_trial' AND objective='experience'")
        c.update("INSERT INTO audit VALUES(?,?,?,?)",System.currentTimeMillis(),"Quest",action,
            "first_trial/experience counter changed to hostile; progress, completions, claims and parcels retained")
    }
}
