package ym.quest.integration

import net.momirealms.craftengine.bukkit.api.CraftEngineItems
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import ym.quest.reward.RewardItemProvider

class CraftEngineItemProvider : RewardItemProvider {
    override fun build(id: String, player: Player?): ItemStack? {
        val definition = CraftEngineItems.byId(id) ?: return null
        return if(player == null) definition.buildBukkitItem() else definition.buildBukkitItem(player)
    }
    fun id(item: ItemStack) = CraftEngineItems.getCustomItemId(item)?.toString()
}
