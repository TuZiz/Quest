package ym.quest.hud

import org.bukkit.entity.Player
import ym.quest.text.TextRenderer
import java.util.UUID

/** Paper ownership thread, driven and cleared by QuestHud's lifecycle. */
internal class QuestNotificationHud(private val text: TextRenderer, private val plain: (String) -> String) {
    private val queues=mutableMapOf<UUID,HudNoticeQueue>()
    private val frames=mutableMapOf<UUID,HudNoticeQueue.Frame>()
    private var clock=0L
    fun offer(player: Player, notices: List<HudNotice>, settings: HudSettings) {
        val queue=queues.getOrPut(player.uniqueId) { HudNoticeQueue(settings.capacity,settings.duration,settings.merge,settings.settle,settings.slide) }
        notices.forEach { queue.offer(it,clock) }
    }
    fun tick(players: Collection<Player>, api: XerrDisplay?, enabled: Boolean, eligible: (Player) -> Boolean, home: (Player) -> String?, now: Long) {
        clock=now
        val online=players.associateBy { it.uniqueId }
        queues.keys.toList().forEach { id ->
            val player=online[id]
            if(player == null || !enabled || api == null || !eligible(player)) {
                if(player != null && api != null && id in frames) api.remove(player,LAYOUT)
                queues.remove(id); frames.remove(id); return@forEach
            }
            val queue=queues.getValue(id)
            val frame=queue.frame(clock)
            if(frame != null && frame.notice.home != home(player)) {
                remove(player,api); return@forEach
            }
            if(frame == null) {
                if(frames.remove(id) != null) api.remove(player,LAYOUT)
            } else if(frames[id] != frame || !api.present(player,LAYOUT)) {
                val notice=frame.notice
                api.update(player,"${LAYOUT}_${notice.event}",CompletionNoticeText.values(notice,text,plain),frame.offset,LAYOUT)
                frames[id]=frame
            }
        }
    }
    fun remove(player: Player, api: XerrDisplay?) {
        queues.remove(player.uniqueId)
        if(frames.remove(player.uniqueId) != null) api?.remove(player,LAYOUT)
    }
    fun clear(players: Collection<Player>, api: XerrDisplay?) {
        players.forEach { remove(it,api) }; queues.clear(); frames.clear()
    }
    companion object { const val LAYOUT="quest_notice" }
}
