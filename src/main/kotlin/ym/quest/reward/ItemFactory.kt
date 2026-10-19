package ym.quest.reward

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import ym.quest.text.TextRenderer

interface RewardItemProvider { fun build(id: String, player: Player?): ItemStack? }

/** Main thread only. Custom providers resolve actual registered IDs; never infer them from display names. */
class ItemFactory(private val providers: List<RewardItemProvider>) {
    fun build(id: String, player: Player? = null): ItemStack {
        val item = if(id.startsWith("minecraft:")) {
            val material = Material.matchMaterial(id) ?: error("Unknown vanilla item $id")
            require(material.isItem && !material.isAir) { "Not an item $id" }
            ItemStack(material)
        } else providers.firstNotNullOfOrNull { it.build(id,player) } ?: error("Custom item unavailable: $id")
        item.editMeta { meta ->
            if(meta.hasDisplayName()) meta.displayName(meta.displayName()?.let(TextRenderer::noItalic))
            if(meta.hasItemName()) meta.itemName(TextRenderer.noItalic(meta.itemName()))
            meta.lore(meta.lore()?.map(TextRenderer::noItalic))
        }
        return item
    }
}
