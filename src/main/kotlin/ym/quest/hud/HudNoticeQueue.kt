package ym.quest.hud

data class HudNotice(val key: String, val chapter: Boolean, val values: Map<String,String>)
data class HudNoticeFrame(val notice: HudNotice, val alpha: Int)

/** One slot, bounded waiting queue, duplicate suppression, tick-based fade. Owned by one player session. */
class HudNoticeQueue(private val settings: HudSettings) {
    private val waiting=ArrayDeque<HudNotice>()
    private var active: HudNotice? = null
    private var started=0L
    fun offer(notice: HudNotice) {
        if(active?.key == notice.key || waiting.any { it.key == notice.key }) return
        if(waiting.size >= settings.queueLimit) waiting.removeFirst()
        waiting.addLast(notice)
    }
    fun frame(tick: Long): HudNoticeFrame? {
        active?.let { if(tick-started >= duration(it)) active=null }
        if(active == null) { active=waiting.removeFirstOrNull(); started=tick }
        val notice=active ?: return null
        val age=tick-started
        val opacity=minOf(1.0,(age+1).toDouble()/settings.fadeTicks,(duration(notice)-age).toDouble()/settings.fadeTicks)
        return HudNoticeFrame(notice,(opacity*255).toInt().coerceIn(0,255))
    }
    private fun duration(notice: HudNotice)=if(notice.chapter) settings.chapterTicks else settings.taskTicks
    fun clear() { waiting.clear(); active=null }
}
