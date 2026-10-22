package ym.quest.storage

import ym.quest.domain.*
import java.sql.Connection
import java.util.UUID

/** Claim and the immutable mailbox payload commit atomically. Sending is deliberately not replayed blindly. */
class RewardStore {
    fun claim(c: Connection, home: String, player: String, view: QuestView, hasGenerator: Boolean): Boolean {
        if(view.status != QuestStatus.COMPLETE) return false
        if(c.update("INSERT OR IGNORE INTO claims VALUES(?,?,?,?)",home,view.definition.id,player,System.currentTimeMillis()) == 0) return false
        view.definition.rewards.filter { !it.starter || !hasGenerator }.forEach { reward ->
            // Moving a reward between tasks never creates a second parcel, including WAITING/SENDING history.
            if(reward.previouslyRewardedBy != null && c.number(
                "SELECT COUNT(*) FROM parcels WHERE home=? AND quest=? AND item=?",home,reward.previouslyRewardedBy,reward.item) > 0) return@forEach
            c.update("INSERT INTO parcels VALUES(?,?,?,?,?,'WAITING',NULL)",UUID.randomUUID().toString(),home,view.definition.id,reward.item,reward.amount)
        }
        return true
    }
    fun parcels(c: Connection, home: String): List<Parcel> = c.rows("SELECT * FROM parcels WHERE home=? ORDER BY rowid",home) {
        Parcel(it.getString("id"),home,it.getString("quest"),it.getString("item"),it.getInt("amount"),it.getString("phase"),it.getString("player"))
    }
    fun transition(c: Connection, id: String, player: String, from: String, to: String): Boolean =
        c.update("UPDATE parcels SET phase=?,player=? WHERE id=? AND phase=?",to,player,id,from) == 1
    fun resolve(c: Connection, id: String, actor: String, delivered: Boolean): Boolean {
        val changed = c.update("UPDATE parcels SET phase=? WHERE id=? AND phase='SENDING'",if(delivered) "DELIVERED" else "WAITING",id) == 1
        if(changed) c.update("INSERT INTO audit VALUES(?,?,?,?)",System.currentTimeMillis(),actor,"reconcile","$id delivered=$delivered")
        return changed
    }
}
