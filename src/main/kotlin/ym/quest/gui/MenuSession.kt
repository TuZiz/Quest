package ym.quest.gui

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import java.util.UUID

class MenuSession(val player: UUID, val home: UUID, val quest: String?, var page: Int) : InventoryHolder {
    lateinit var menu: Inventory
    val actions = mutableMapOf<Int,String>()
    var request = 0L
    override fun getInventory(): Inventory = menu
}
