package ym.quest.hud

/** Pure per-player timeline, measured in server ticks. No IO, scheduler or quest mutation. */
class SmartHudState {
    private var key: String? = null
    private var lastProgress = 0L
    private var seen = emptySet<Int>()
    private var until = 0L
    private var burst = 0L
    private var blockedUntil = 0L
    private var requested = false
    fun request() { requested = true }
    fun compact() { until=0; requested=false }
    fun observe(target: TrackerTarget?, now: Long, settings: HudSettings) {
        if(target == null) { key=null; lastProgress=0; seen=emptySet(); until=0; requested=false; return }
        val crossed=settings.milestones.filter { target.current.toDouble() / target.target.coerceAtLeast(1) * 100 >= it }.toSet()
        if(key != target.key) {
            val switched=key != null
            key=target.key; seen=crossed; lastProgress=target.current
            if(switched || requested) expand(now,settings,switched)
        } else {
            val important=lastProgress == 0L && target.current > 0 || (crossed-seen).isNotEmpty()
            seen=seen+crossed; lastProgress=target.current
            if(important || requested) expand(now,settings)
        }
        requested=false
    }
    private fun expand(now: Long, settings: HudSettings, newTarget: Boolean = false) {
        if(now < until) { until=minOf(now+settings.expandTicks,burst+160); return }
        if(!newTarget && now < blockedUntil) return
        burst=now; until=now+settings.expandTicks; blockedUntil=now+settings.cooldownTicks
    }
    fun state(mode: HudMode, valid: Boolean, now: Long): HudState = when {
        !valid || mode == HudMode.OFF -> HudState.HIDDEN
        mode == HudMode.ALWAYS || now < until -> HudState.EXPANDED
        else -> HudState.COMPACT
    }
}
