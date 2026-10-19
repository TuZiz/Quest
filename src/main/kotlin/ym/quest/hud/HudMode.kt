package ym.quest.hud

enum class HudMode { SMART, ALWAYS, OFF;
    fun next() = entries[(ordinal + 1) % entries.size]
}

data class HudPreference(val mode: HudMode = HudMode.SMART, val notifications: Boolean = true, val trackedQuest: String? = null)
enum class HudState { COMPACT, EXPANDED, HIDDEN }
