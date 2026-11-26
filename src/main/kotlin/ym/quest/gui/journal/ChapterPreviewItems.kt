package ym.quest.gui.journal

import org.bukkit.NamespacedKey
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import ym.quest.reward.ItemFactory
import ym.quest.text.TextRenderer

/** Paper owner thread only. Covers have hover text but deliberately have no click action. */
internal class ChapterPreviewItems(private val items: ItemFactory, private val text: TextRenderer) {
    fun build(player: Player, preview: ChapterCardPreview, y: YamlConfiguration, anchor: Boolean): ItemStack {
        val packed = player.hasResourcePack()
        val id = if (packed) y.getString(if (anchor) "cards.base-item" else "cards.blank-item")!! else preview.icon
        return items.build(id,player).apply {
            editMeta { meta ->
                meta.displayName(text.item(preview.name))
                meta.lore(preview.lore.map { text.item(it) })
                if (packed && anchor) meta.setItemModel(requireNotNull(NamespacedKey.fromString(preview.model)))
            }
        }
    }
}
