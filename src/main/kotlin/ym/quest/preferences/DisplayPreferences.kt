package ym.quest.preferences

import ym.quest.domain.JournalView
import ym.quest.domain.QuestStatus
import ym.quest.domain.QuestView

enum class HudMode { ALWAYS, OFF }
data class DisplayPreferences(val mode: HudMode = HudMode.OFF, val autoTracking: Boolean = false,
    val notifications: Boolean = true, val sound: Boolean = true, val trackedQuest: String? = null)

/** A player's viewing selection never changes task eligibility, progress or rewards. */
object TrackingSelection {
    fun select(view: JournalView, settings: DisplayPreferences): QuestView? {
        val selected=view.quests.firstOrNull { it.definition.id==settings.trackedQuest && it.status==QuestStatus.ACTIVE }
        return selected ?: if(settings.autoTracking) view.quests.firstOrNull { it.status==QuestStatus.ACTIVE } else null
    }
}
