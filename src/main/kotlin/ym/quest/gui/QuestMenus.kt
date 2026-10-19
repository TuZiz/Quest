package ym.quest.gui

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import ym.quest.config.Settings
import ym.quest.domain.*
import ym.quest.integration.Integrations
import ym.quest.platform.GameThread
import ym.quest.reward.ItemFactory
import ym.quest.reward.MailboxDelivery
import ym.quest.text.TextRenderer
import java.util.UUID

/** Inventory events do validation and delegate; SQL never runs on this thread. */
class QuestMenus(private val plugin: JavaPlugin, private val service: QuestService,
                 private val integrations: Integrations, private val items: ItemFactory,
                 private val text: TextRenderer, private val thread: GameThread,
                 private val delivery: MailboxDelivery, private val claim: (Player,UUID,String) -> Unit,
                 private val failure: (Throwable) -> Unit, var settings: Settings) : Listener, AutoCloseable {
    private val sessions = mutableMapOf<UUID,MenuSession>()
    fun open(player: Player, quest: String? = null, page: Int = 0) {
        if(!player.hasPermission("quest.use")) { text.send(player,"no-permission"); return }
        val island = integrations.island(player.uniqueId)
        if(island == null) { text.send(player,if(integrations.islands?.ready() == true) "no-island" else "unavailable"); return }
        val y = if(quest == null) settings.center else settings.detail
        val session = MenuSession(player.uniqueId,island.id,quest,page)
        session.menu = plugin.server.createInventory(session,y.getStringList("Shape").size*9,text.render(y.getString("title")!!))
        player.openInventory(session.menu); sessions[player.uniqueId]=session
        refresh(player,session)
    }
    fun refreshHome(home: UUID) { sessions.values.filter { it.home == home }.toList().forEach { s ->
        plugin.server.getPlayer(s.player)?.let { refresh(it,s) }
    } }
    fun refreshPlayer(player: Player) { sessions[player.uniqueId]?.let { refresh(player,it) } }
    private fun refresh(player: Player, s: MenuSession) {
        val request = ++s.request
        service.views(s.home.toString()).whenComplete { views,error -> thread.run {
            if(sessions[player.uniqueId] !== s || request != s.request || !player.isOnline) return@run
            if(integrations.island(player.uniqueId)?.id != s.home || !player.hasPermission("quest.use")) { player.closeInventory(); return@run }
            if(error != null) { failure(error); text.send(player,"error"); return@run }
            try { render(player,s,views) } catch(e: Exception) { failure(e); text.send(player,"unavailable"); player.closeInventory() }
        } }
    }
    private fun render(player: Player, s: MenuSession, views: List<QuestView>) {
        val y = if(s.quest == null) settings.center else settings.detail
        val shape = y.getStringList("Shape").joinToString("")
        val current = views.firstOrNull { it.definition.id == s.quest }
        val capacity = shape.count { ch -> y.getString("components.$ch.action") == "quest" }
        val pages = if(capacity == 0) 1 else ((views.size+capacity-1)/capacity).coerceAtLeast(1)
        s.page = s.page.coerceIn(0,pages-1)
        val list = if(s.quest == null) views.drop(s.page*capacity).iterator() else emptyList<QuestView>().iterator()
        val rewardList = current?.definition?.rewards?.iterator()
        s.menu.clear(); s.actions.clear()
        shape.forEachIndexed { slot,ch ->
            if(ch == ' ') return@forEachIndexed
            val section = checkNotNull(y.getConfigurationSection("components.$ch"))
            val action = section.getString("action","")!!
            val q = if(action == "quest") { if(list.hasNext()) list.next() else return@forEachIndexed } else current
            val reward = if(action == "preview") { if(rewardList?.hasNext() == true) rewardList.next() else return@forEachIndexed } else null
            val id = when { reward != null -> reward.item; action in setOf("quest","summary") -> q?.definition?.icon ?: section.getString("item")!!; else -> section.getString("item")!! }
            var unavailable=false
            val item = try { items.build(id,player) } catch(e: IllegalStateException) {
                unavailable=true; items.build(y.getString("unavailable-item")!!,player)
            }
            val values = mutableMapOf<String,Component>("page" to Component.text(s.page+1),"pages" to Component.text(pages))
            if(q != null) {
                values["quest"] = text.render(q.definition.name)
                values["status"] = text.format("status.${q.status}")
            }
            val lore = section.getStringList("lore").map { text.item(it,values) }.toMutableList()
            if(unavailable || (q?.definition?.objectives?.any { it.type.startsWith("genlite.") } == true && integrations.production?.ready() != true)) {
                lore += TextRenderer.noItalic(text.format("unavailable"))
            }
            if(q != null && action in setOf("quest","summary","objectives")) {
                if(action != "objectives") lore += q.definition.description.map { text.item(it) }
                lore += q.progress.map { p -> TextRenderer.noItalic(text.format("objective-line",mapOf(
                    "description" to text.render(p.objective.description),"progress" to Component.text(p.progress),"target" to Component.text(p.objective.target)))) }
            }
            if(reward != null) {
                item.amount = reward.amount.coerceAtMost(item.maxStackSize)
                lore += TextRenderer.noItalic(text.format("reward-line",TextRenderer.values("amount" to reward.amount)))
            }
            item.editMeta { meta ->
                section.getString("name")?.let { meta.displayName(text.item(it,values)) }
                meta.lore(lore)
            }
            s.menu.setItem(slot,item)
            s.actions[slot] = if(action == "quest") "quest:${q!!.definition.id}" else action
        }
    }
    @EventHandler fun click(e: InventoryClickEvent) {
        val s = e.view.topInventory.holder as? MenuSession ?: return
        e.isCancelled = true
        val player = e.whoClicked as? Player ?: return
        if(sessions[player.uniqueId] !== s || e.rawSlot !in 0 until s.menu.size) return
        if(!player.hasPermission("quest.use") || integrations.island(player.uniqueId)?.id != s.home) { player.closeInventory(); return }
        val action = s.actions[e.rawSlot] ?: return
        thread.run {
            if(sessions[player.uniqueId] !== s) return@run
            when {
                action.startsWith("quest:") -> open(player,action.substringAfter(':'))
                action == "back" -> open(player)
                action == "previous" -> open(player,page=s.page-1)
                action == "next" -> open(player,page=s.page+1)
                action == "refresh" -> { integrations.syncIsland(s.home); refresh(player,s) }
                action == "mailbox" -> delivery.collect(player,s.home) { refreshPlayer(player) }
                action == "claim" && s.quest != null -> claim(player,s.home,s.quest)
            }
        }
    }
    @EventHandler fun drag(e: InventoryDragEvent) { if(e.view.topInventory.holder is MenuSession) e.isCancelled=true }
    @EventHandler fun close(e: InventoryCloseEvent) { val s=e.inventory.holder as? MenuSession ?: return; sessions.remove(e.player.uniqueId,s) }
    @EventHandler fun quit(e: PlayerQuitEvent) { sessions.remove(e.player.uniqueId) }
    override fun close() { val players=sessions.keys.toList(); sessions.clear(); players.forEach { plugin.server.getPlayer(it)?.closeInventory() } }
}
