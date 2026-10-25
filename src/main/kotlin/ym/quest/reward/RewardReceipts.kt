package ym.quest.reward

import net.kyori.adventure.text.Component
import ym.quest.domain.Parcel
import ym.quest.text.TextRenderer

/** Per-collection receipt: only acknowledged deliveries enter the player-visible summary. */
class RewardReceipts(private val text: TextRenderer) {
    private data class Entry(val name: Component, val amount: Long)
    private val received = linkedMapOf<String, LinkedHashMap<String, Entry>>()
    private val parcels = mutableSetOf<String>()
    fun record(parcel: Parcel, name: Component) {
        require(parcel.amount > 0)
        if(!parcels.add(parcel.id)) return
        val task = received.getOrPut(parcel.quest) { linkedMapOf() }
        val previous = task[parcel.item]
        task[parcel.item] = Entry(name, Math.addExact(previous?.amount ?: 0L, parcel.amount.toLong()))
    }
    fun tasks(): List<String> = received.keys.toList()
    fun take(task: String): Component? {
        val entries = received.remove(task)?.values ?: return null
        var result: Component = Component.empty()
        entries.forEachIndexed { index, entry ->
            if(index > 0) result = result.append(text.format("reward-delivered-separator"))
            result = result.append(text.format("reward-delivered-item", mapOf(
                "item" to entry.name, "amount" to Component.text(entry.amount))))
        }
        return TextRenderer.noItalic(result)
    }
}
