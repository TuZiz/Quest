package ym.quest.gui.journal

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.inventory.*
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import ym.quest.config.Settings
import ym.quest.domain.*
import ym.quest.integration.Integrations
import ym.quest.platform.GameThread
import ym.quest.preferences.*
import ym.quest.reward.ItemFactory
import ym.quest.text.TextRenderer
import java.util.UUID
import java.util.concurrent.CompletableFuture

/** Routes player actions; all persistence is asynchronous and rechecks session, connection and island. */
class TaskJournalMenus(private val plugin: JavaPlugin,private val service: QuestService,
    private val integrations: Integrations,items: ItemFactory,private val text: TextRenderer,
    private val thread: GameThread,private val preferences: PlayerDisplayPreferences,
    var settings: Settings,private val failure: (Throwable)->Unit,
    private val decorate: (Player,JournalView)->JournalView) : Listener,AutoCloseable {
    private val renderer=JournalRenderer(items,text)
    private val sessions=mutableMapOf<UUID,JournalSession>()
    private var closed=false
    private val timer=plugin.server.scheduler.runTaskTimer(plugin,Runnable {
        sessions.values.toList().forEach { s -> plugin.server.getPlayer(s.player)?.let { refresh(it,s) } }
    },20,40)
    init { plugin.server.pluginManager.registerEvents(this,plugin) }
    fun open(player: Player,screen: JournalPage=JournalPage.CHAPTERS,category: QuestCategory=QuestCategory.MAIN,
        chapter: String?=null,page: Int=0) {
        if(closed) return
        if(!player.hasPermission("quest.use")) { text.send(player,"no-permission");return }
        val s=JournalSession(player.uniqueId,integrations.island(player.uniqueId)?.id,screen,category,chapter,page)
        val y=settings.journalMenus.getValue(screen.file)
        val title=if(player.hasResourcePack()) y.getString("title")!! else y.getString("fallback-title",y.getString("title"))!!
        s.menu=plugin.server.createInventory(s,y.getStringList("Shape").size*9,text.render(title))
        player.openInventory(s.menu);sessions[player.uniqueId]=s
        renderer.loading(s,settings);preferences.load(player);refresh(player,s)
    }
    private fun valid(player: Player,s: JournalSession)=!closed && player.isOnline && sessions[player.uniqueId]===s &&
        player.openInventory.topInventory.holder===s && player.hasPermission("quest.use") && integrations.island(player.uniqueId)?.id==s.home
    private fun journal(s: JournalSession): CompletableFuture<JournalView> = s.home?.let { service.journal(it.toString()) }
        ?: CompletableFuture.completedFuture(JournalView(service.definitions.map { q ->
            QuestView(q,QuestStatus.LOCKED,q.objectives.map { ObjectiveProgress(it,0) })
        },service.chapters.map { ChapterView(it,ChapterStatus.LOCKED,0,false) }))
    private fun refresh(player: Player,s: JournalSession) {
        if(!valid(player,s)) { if(sessions[player.uniqueId]===s) player.closeInventory();return }
        val prefs=preferences.get(player)
        if(prefs==null) { if(preferences.failed(player)) renderer.loading(s,settings,"error");return }
        if(s.pending) return
        s.pending=true;val request=++s.request
        journal(s).whenComplete { view,error -> thread.run {
            if(!valid(player,s) || request!=s.request) return@run
            s.pending=false
            if(error!=null) { failure(error);renderer.loading(s,settings,"error");return@run }
            try {
                val decorated=decorate(player,view)
                val current=preferences.get(player) ?: return@run
                if(s.dirty || s.view!=decorated || s.preferences!=current) {
                    renderer.render(player,s,decorated,current,settings);s.dirty=false;s.view=decorated;s.preferences=current
                }
            } catch(e: Exception) {
                failure(IllegalStateException("Journal render; player=${player.uniqueId}; screen=${s.screen.file}; chapter=${s.chapter}",e))
                renderer.loading(s,settings,"error");s.dirty=true
            }
        } }
    }
    fun refreshHome(home: UUID) { sessions.values.filter { it.home==home }.toList().forEach { s -> plugin.server.getPlayer(s.player)?.let { refresh(it,s) } } }
    private fun action(player: Player,s: JournalSession,action: String) {
        if(!valid(player,s)) return
        if(s.view==null) return
        when {
            action.startsWith("category-") -> {
                s.category=if(action=="category-cycle") QuestCategory.entries[(s.category.ordinal+1)%QuestCategory.entries.size]
                    else QuestCategory.valueOf(action.substringAfter('-').uppercase())
                s.page=0;s.dirty=true
                val current=preferences.get(player)
                if(current!=null) {
                    renderer.render(player,s,s.view!!,current,settings)
                    s.dirty=false;s.preferences=current
                } else refresh(player,s)
            }
            action=="settings" -> open(player,JournalPage.SETTINGS,s.category)
            action=="home" -> open(player,category=s.category)
            action=="back" -> open(player,category=s.category)
            action=="close" -> player.closeInventory()
            action.startsWith("chapter:") -> open(player,JournalPage.TASKS,s.category,action.substringAfter(':'))
            action=="previous" || action=="next" -> {
                val current=preferences.get(player) ?: return
                if(s.turnPage(if(action=="next") 1 else -1))
                    renderer.render(player,s,s.view!!,current,settings,pageOnly=true)
            }
            action=="refresh" -> { s.dirty=true;refresh(player,s) }
            action.startsWith("track:") -> track(player,s,action.substringAfter(':'))
            action.startsWith("toggle-") -> preferences.update(player,{ value -> when(action) {
                "toggle-hud", "toggle-auto" -> value.copy(mode=if(value.mode==HudMode.OFF) HudMode.ALWAYS else HudMode.OFF,autoTracking=value.mode==HudMode.OFF)
                "toggle-notices" -> value.copy(notifications=!value.notifications)
                "toggle-sound" -> value.copy(sound=!value.sound)
                else -> value
            } }) { success -> if(valid(player,s)) { text.send(player,if(success) "journal.saved" else "error");s.dirty=true;refresh(player,s) } }
        }
    }
    private fun track(player: Player,s: JournalSession,id: String) {
        journal(s).whenComplete { view,error -> thread.run {
            if(!valid(player,s)) return@run
            if(error!=null) { failure(error);text.send(player,"error");return@run }
            val q=decorate(player,view).quests.firstOrNull { it.definition.id==id && it.status==QuestStatus.ACTIVE }
            if(q==null) { text.send(player,"journal.track-unavailable");return@run }
            preferences.update(player,{ it.copy(trackedQuest=id,mode=HudMode.ALWAYS,autoTracking=true) }) { saved ->
                if(valid(player,s)) { text.send(player,if(saved) "journal.tracked" else "error");s.dirty=true;refresh(player,s) }
            }
        } }
    }
    @EventHandler fun click(e: InventoryClickEvent) {
        val s=e.view.topInventory.holder as? JournalSession ?: return
        e.isCancelled=true
        val player=e.whoClicked as? Player ?: return
        if(!valid(player,s) || e.rawSlot !in 0 until s.menu.size) return
        val selectedAction=s.actions[e.rawSlot] ?: return
        val slot=e.rawSlot
        val revision=s.revision
        thread.run {
            if(s.revision==revision && s.actions[slot]==selectedAction) action(player,s,selectedAction)
        }
    }
    @EventHandler fun drag(e: InventoryDragEvent) { if(e.view.topInventory.holder is JournalSession) e.isCancelled=true }
    @EventHandler fun close(e: InventoryCloseEvent) { val s=e.inventory.holder as? JournalSession ?: return;sessions.remove(e.player.uniqueId,s) }
    @EventHandler fun quit(e: PlayerQuitEvent) { sessions.remove(e.player.uniqueId) }
    fun closeMenus() { val players=sessions.keys.toList();sessions.clear();players.forEach { plugin.server.getPlayer(it)?.closeInventory() } }
    override fun close() { if(closed) return;closed=true;timer.cancel();closeMenus();HandlerList.unregisterAll(this) }
}
