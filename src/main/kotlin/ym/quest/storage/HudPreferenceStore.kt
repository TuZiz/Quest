package ym.quest.storage

import ym.quest.hud.HudMode
import ym.quest.hud.HudPreference
import java.util.UUID

/** Uses the existing JDBC owner executor; never called synchronously from the player thread. */
class HudPreferenceStore(private val db: Database) {
    fun load(player: UUID, legacy: HudMode = HudMode.SMART) = db.submit { db.transaction { c ->
        c.update("INSERT OR IGNORE INTO hud_preferences(player,mode,notifications,tracked_quest) VALUES(?,?,1,NULL)",player.toString(),legacy.name)
        c.rows("SELECT mode,notifications,tracked_quest FROM hud_preferences WHERE player=?",player.toString()) {
            HudPreference(HudMode.valueOf(it.getString(1)),it.getBoolean(2),it.getString(3))
        }.single()
    } }
    fun save(player: UUID, value: HudPreference) = db.submit { db.transaction { c ->
        c.update("INSERT INTO hud_preferences(player,mode,notifications,tracked_quest) VALUES(?,?,?,?) ON CONFLICT(player) DO UPDATE SET mode=excluded.mode,notifications=excluded.notifications,tracked_quest=excluded.tracked_quest",
            player.toString(),value.mode.name,if(value.notifications) 1 else 0,value.trackedQuest)
        value
    } }
}
