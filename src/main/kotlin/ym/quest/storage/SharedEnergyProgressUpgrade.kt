package ym.quest.storage

import java.sql.Connection
import ym.quest.domain.QuestDefinition

/** Database owner transaction only. Carry earned supply progress across the energy metric change. */
internal object SharedEnergyProgressUpgrade {
    fun migrate(c: Connection,definitions: List<QuestDefinition>) {
        if(definitions.none {q -> q.id=="logistics_supply" && q.objectives.any {it.id=="supply" && it.metric=="genlite.energy"}}) return
        val action="shared-core-energy-v1"
        if(c.number("SELECT COUNT(*) FROM audit WHERE actor=? AND action=?","Quest",action)>0) return
        c.update("UPDATE objectives SET baseline=COALESCE((SELECT value FROM facts WHERE facts.home=objectives.home AND metric='genlite.energy'),0)-progress WHERE quest='logistics_supply' AND objective='supply'")
        c.update("INSERT INTO audit VALUES(?,?,?,?)",System.currentTimeMillis(),"Quest",action,
            "logistics_supply/supply changed to core energy; earned progress, completions, claims and parcels retained")
    }
}
