package ym.quest.reward

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/** The provider is loading and the caller can retry before creating or reserving a reward. */
internal class RewardProviderNotReady(message: String) : IllegalStateException(message)

interface RewardItemProvider {
    /** Deterministic availability/menu view. */
    fun build(id: String, player: Player?): ItemStack?
    /** Actual delivery only; providers may generate a new saved item roll here. */
    fun reward(id: String, player: Player?): ItemStack? = build(id,player)
}

/** Main thread only. Custom providers resolve actual registered IDs; never infer them from display names. */
class ItemFactory(private val providers: List<RewardItemProvider>) {
    /** Prepare all additions before reserving the parcel; each nonstackable item gets its own roll. */
    fun reward(id: String, amount: Int, player: Player? = null): List<ItemStack> {
        require(amount>0)
        fun create()=if(id.startsWith("minecraft:")) build(id,player)
            else providers.firstNotNullOfOrNull { it.reward(id,player) } ?: error("Custom item unavailable: $id")
        val first=create()
        if(first.maxStackSize!=1) return MailboxDelivery.split(first,amount)
        return List(amount) { if(it==0) first else create() }
    }
    fun build(id: String, player: Player? = null): ItemStack {
        val item = if(id.startsWith("minecraft:")) {
            val material = Material.matchMaterial(id) ?: error("Unknown vanilla item $id")
            require(material.isItem && !material.isAir) { "Not an item $id" }
            ItemStack(material)
        } else providers.firstNotNullOfOrNull { it.build(id,player) } ?: error("Custom item unavailable: $id")
        // Provider metadata is part of stack identity. GUI copies format their own text separately.
        return item
    }
}
