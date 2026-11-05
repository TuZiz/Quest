package ym.quest.tutorial

import java.util.UUID

/** Bounded death-event deduplication across Bukkit and Mythic callbacks; no Bukkit references retained. */
internal class CombatKillWindow(private val clock: () -> Long = System::nanoTime) {
    private val deaths = LinkedHashMap<UUID,Long>()
    @Synchronized fun accept(entity: UUID, hostile: Boolean, mythic: Boolean): Boolean {
        if(!hostile && !mythic) return false
        val now=clock()
        val iterator=deaths.entries.iterator()
        while(iterator.hasNext()) {
            if(now-iterator.next().value < 120_000_000_000L) break
            iterator.remove()
        }
        if(entity in deaths) return false
        if(deaths.size>=8192) deaths.remove(deaths.keys.first())
        deaths[entity]=now
        return true
    }
    @Synchronized fun clear() { deaths.clear() }
}
