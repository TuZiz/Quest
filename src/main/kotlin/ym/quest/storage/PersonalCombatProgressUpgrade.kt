package ym.quest.storage

import ym.quest.domain.*
import java.sql.Connection
import java.util.UUID

/** Preserve island history, credit only known recipients; never clone an island's rewards to its members. */
internal object PersonalCombatProgressUpgrade {
    fun migrate(c: Connection, definitions: List<QuestDefinition>) {
        definitions.filter { it.scope==ProgressScope.PERSONAL }.forEach { q ->
            val marker="personal-scope-v1:${q.id}"
            if(c.number("SELECT COUNT(*) FROM audit WHERE action=?",marker)>0) return@forEach
            val claims=c.rows("SELECT home,player,created FROM claims WHERE quest=? AND home NOT LIKE 'player:%'",q.id) {
                Triple(it.getString(1),it.getString(2),it.getLong(3))
            }
            claims.forEach { (home,claimant,created) ->
                val parcels=c.rows("SELECT id,player FROM parcels WHERE home=? AND quest=?",home,q.id) { it.getString(1) to it.getString(2) }
                // A partial old delivery can have distinct recipients. Credit each actual recipient conservatively.
                val recipients=(parcels.map { it.second ?: claimant }+claimant).filterNotNull().distinct()
                recipients.forEach { player ->
                    require(runCatching { UUID.fromString(player) }.isSuccess) { "Personal scope migration: invalid recipient $player for $home/${q.id}" }
                    val owner=PersonalQuests.owner(player)
                    c.update("INSERT OR IGNORE INTO claims VALUES(?,?,?,?)",owner,q.id,player,created)
                    c.update("INSERT OR IGNORE INTO completions VALUES(?,?,?)",owner,q.id,created)
                    c.update("INSERT OR IGNORE INTO objectives SELECT ?,quest,objective,baseline,progress FROM objectives WHERE home=? AND quest=?",owner,home,q.id)
                }
                parcels.forEach { (id,recipient) -> c.update("UPDATE parcels SET home=? WHERE id=?",PersonalQuests.owner(recipient ?: claimant),id) }
            }
            c.update("INSERT INTO audit VALUES(?,?,?,?)",System.currentTimeMillis(),"Quest",marker,
                "Legacy island progress retained; personal claims credited to known recipients; original parcel IDs and phases retained")
        }
    }
}
