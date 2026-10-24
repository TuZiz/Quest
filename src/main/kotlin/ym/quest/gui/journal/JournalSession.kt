package ym.quest.gui.journal

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import ym.quest.domain.*
import ym.quest.preferences.DisplayPreferences
import java.util.UUID

enum class JournalPage(val file: String) { CHAPTERS("chapters"),TASKS("tasks"),SETTINGS("settings") }
class JournalSession(val player: UUID,val home: UUID?,val screen: JournalPage,
    var category: QuestCategory = QuestCategory.MAIN,var chapter: String? = null,var page: Int = 0) : InventoryHolder {
    lateinit var menu: Inventory
    val actions=mutableMapOf<Int,String>()
    var pages=1
    var revision=0L
    fun turnPage(delta: Int): Boolean {
        val next=(page+delta).coerceIn(0,pages-1)
        if(next==page) return false
        page=next
        return true
    }
    var request=0L
    var pending=false
    var dirty=true
    var view: JournalView?=null
    var preferences: DisplayPreferences?=null
    override fun getInventory()=menu
}
