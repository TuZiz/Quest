package ym.quest.hud

/** Pure bounded timeline. One frame only; dedup covers pending, visible and recent notices. */
class HudNoticeQueue(private val capacity: Int, private val duration: Int, private val merge: Boolean,
                     private val settle: Int, private val slide: Int) {
    data class Frame(val notice: HudNotice, val offset: Int)
    private data class Pending(var notice: HudNotice, val at: Long)
    private val pending = ArrayDeque<Pending>()
    private val recent = linkedMapOf<String,Long>()
    private var active: Pending? = null
    val size get() = pending.size + if(active == null) 0 else 1
    fun offer(notice: HudNotice, now: Long) {
        recent.entries.removeIf { now-it.value > 600 }
        if(notice.key in recent) return
        recent[notice.key] = now
        while(recent.size > 128) recent.remove(recent.keys.first())
        if(merge && notice.event == "task-start") {
            pending.lastOrNull { it.notice.home == notice.home && it.notice.event == "task-complete" &&
                it.notice.id in notice.prerequisites && it.notice.next == null }?.let {
                it.notice = it.notice.copy(next=notice.name); return
            }
        }
        if(pending.size >= capacity) pending.removeFirst()
        pending.addLast(Pending(notice,now))
    }
    fun frame(now: Long): Frame? {
        active?.let { if(now-it.at >= duration) active=null }
        if(active == null && pending.firstOrNull()?.let { now-it.at >= settle } == true)
            active=Pending(pending.removeFirst().notice,now)
        val item=active ?: return null
        val age=(now-item.at).toInt()
        val distance=if(slide == 0) 0 else when {
            age < slide -> 12 * (slide-age) / slide
            age > duration-slide -> 12 * (age-(duration-slide)) / slide
            else -> 0
        }
        return Frame(item.notice,distance)
    }
}
