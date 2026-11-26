package ym.quest.storage

import java.sql.Connection

/** Quest DB executor only. One source baseline per island, without changing existing objectives or reward rows. */
object WarehouseProgressFacts {
    const val source = "integration.playervault.withdrawn"
    private const val baseline = "$source.baseline"
    private const val observed = "$source.observed"
    fun merge(c: Connection, home: String, snapshot: Map<String,Long>): Map<String,Long> {
        val total=snapshot[source] ?: return snapshot
        require(total>=0) { "PlayerVault negative withdrawal total: home=$home, total=$total" }
        val inserted=c.update("INSERT OR IGNORE INTO facts VALUES(?,?,?)",home,baseline,total)
        if(inserted>0) c.update("INSERT INTO audit VALUES(?,?,?,?)",System.currentTimeMillis(),"Quest","playervault-withdrawal-baseline","$home/$total; historical actions excluded; objectives and rewards retained")
        val start=c.number("SELECT value FROM facts WHERE home=? AND metric=?",home,baseline)
        val last=c.number("SELECT value FROM facts WHERE home=? AND metric=?",home,observed)
        check(total>=start && total>=last) { "PlayerVault withdrawal counter rollback: home=$home, total=$total, baseline=$start, previous=$last; restore consistent backups" }
        c.update("INSERT INTO facts VALUES(?,?,?) ON CONFLICT(home,metric) DO UPDATE SET value=MAX(value,excluded.value)",home,observed,total)
        val result=snapshot-source
        val legacy=result["genlite.extract"] ?: return result
        val delta=total-start
        return result+("genlite.extract" to if(legacy>Long.MAX_VALUE-delta) Long.MAX_VALUE else legacy+delta)
    }
}
