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
    private val renderer = JournalMenuRenderer(items,text)
    private val sessions = mutableMapOf<UUID,MenuSession>()
    fun open(player: Player, quest: String? = null, page: Int = 0, chapter: String? = null, chapters: Boolean = true) {
        if(!player.hasPermission("quest.use")) { text.send(player,"no-permission"); return }
        val island = integrations.island(player.uniqueId)
        if(island == null) { text.send(player,if(integrations.islands?.ready() == true) "no-island" else "unavailable"); return }
        val session = MenuSession(player.uniqueId,island.id,quest,page,chapter,chapters)
        val y = renderer.layout(settings,session)
        session.menu = plugin.server.createInventory(session,y.getStringList("Shape").size*9,text.render(y.getString("title")!!))
        player.openInventory(session.menu); sessions[player.uniqueId]=session
        integrations.syncIsland(island.id)
        refresh(player,session)
    }
    fun refreshHome(home: UUID) { sessions.values.filter { it.home == home }.toList().forEach { s ->
        plugin.server.getPlayer(s.player)?.let { refresh(it,s) }
    } }
    fun refreshPlayer(player: Player) { sessions[player.uniqueId]?.let { refresh(player,it) } }
    private fun refresh(player: Player, s: MenuSession) {
        val request = ++s.request
        service.journal(s.home.toString()).whenComplete { views,error -> thread.run {
            if(sessions[player.uniqueId] !== s || request != s.request || !player.isOnline) return@run
            if(integrations.island(player.uniqueId)?.id != s.home || !player.hasPermission("quest.use")) { player.closeInventory(); return@run }
            if(error != null) { failure(error); text.send(player,"error"); return@run }
            try { renderer.render(player,s,views,settings,integrations.production?.ready() == true) } catch(e: Exception) { failure(e); text.send(player,"unavailable"); player.closeInventory() }
        } }
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
                action.startsWith("quest:") -> open(player,action.substringAfter(':'),chapter=s.chapter)
                action.startsWith("chapter:") -> open(player,chapter=action.substringAfter(':'),chapters=true)
                action == "chapters" -> open(player,chapters=true)
                action == "back" -> when {
                    s.quest != null && s.chapter != null -> open(player,chapter=s.chapter,chapters=true)
                    s.chapters && s.chapter != null -> open(player,chapters=true)
                    else -> open(player,chapters=false)
                }
                action == "previous" -> open(player,page=s.page-1,chapter=s.chapter,chapters=s.chapters)
                action == "next" -> open(player,page=s.page+1,chapter=s.chapter,chapters=s.chapters)
                action == "refresh" -> { integrations.syncIsland(s.home); refresh(player,s) }
                action == "mailbox" -> delivery.collect(player,s.home) { refreshPlayer(player) }
                action == "chapter-claim" && s.chapter != null -> claim(player,s.home,"chapter:${s.chapter}")
                action == "claim" && s.quest != null -> claim(player,s.home,s.quest)
            }
        }
    }
    @EventHandler fun drag(e: InventoryDragEvent) { if(e.view.topInventory.holder is MenuSession) e.isCancelled=true }
    @EventHandler fun close(e: InventoryCloseEvent) { val s=e.inventory.holder as? MenuSession ?: return; sessions.remove(e.player.uniqueId,s) }
    @EventHandler fun quit(e: PlayerQuitEvent) { sessions.remove(e.player.uniqueId) }
    override fun close() { val players=sessions.keys.toList(); sessions.clear(); players.forEach { plugin.server.getPlayer(it)?.closeInventory() } }
}
