package ym.quest.preferences

import ym.quest.storage.*
import java.util.UUID

/** All JDBC work uses Quest's database executor; the existing legacy HUD rows remain readable. */
class DisplayPreferenceStore(private val db: Database) {
    fun load(player: UUID)=db.submit { db.transaction { c ->
        c.rows("SELECT h.mode,h.notifications,h.tracked_quest,o.auto_tracking,o.sound FROM hud_preferences h LEFT JOIN journal_options o ON h.player=o.player WHERE h.player=?",player.toString()) { r ->
            DisplayPreferences(if(r.getString(1)=="OFF") HudMode.OFF else HudMode.ALWAYS,
                r.getObject(4)?.let { r.getInt(4)!=0 } ?: true,r.getInt(2)!=0,
                r.getObject(5)?.let { r.getInt(5)!=0 } ?: true,r.getString(3))
        }.firstOrNull() ?: DisplayPreferences()
    } }
    fun save(player: UUID,value: DisplayPreferences)=db.submit { db.transaction { c ->
        c.update("INSERT INTO hud_preferences(player,mode,notifications,tracked_quest) VALUES(?,?,?,?) ON CONFLICT(player) DO UPDATE SET mode=excluded.mode,notifications=excluded.notifications,tracked_quest=excluded.tracked_quest",
            player.toString(),value.mode.name,if(value.notifications) 1 else 0,value.trackedQuest)
        c.update("INSERT INTO journal_options(player,auto_tracking,sound) VALUES(?,?,?) ON CONFLICT(player) DO UPDATE SET auto_tracking=excluded.auto_tracking,sound=excluded.sound",
            player.toString(),if(value.autoTracking) 1 else 0,if(value.sound) 1 else 0)
        value
    } }
}
