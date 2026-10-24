package ym.quest.platform

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.event.server.PluginEnableEvent
import org.bukkit.plugin.java.JavaPlugin
import ym.quest.config.*
import ym.quest.domain.*
import ym.quest.gui.journal.TaskJournalMenus
import ym.quest.preferences.PlayerDisplayPreferences
import ym.quest.integration.*
import ym.quest.reward.*
import ym.quest.storage.Database
import ym.quest.text.TextRenderer
import ym.quest.text.StoryNotices
import ym.quest.tab.TabQuestBridge
import ym.quest.tab.TabSettings
import ym.quest.introduction.IntroductionRuntime
import ym.quest.hud.HudSettings
import ym.quest.hud.QuestHudBridge
import java.util.UUID
import java.util.logging.Level

class QuestRuntime(private val plugin: JavaPlugin, private val db: Database, private val service: QuestService,
                   private val files: QuestFiles, private val thread: GameThread, settings: Settings) : Listener, AutoCloseable {
    private val text = TextRenderer(settings.language)
    private val integrations = Integrations(plugin,service,::notify,::failure)
    private val notices = StoryNotices(text,settings.notices)
    private val custom = if(plugin.server.pluginManager.isPluginEnabled("CraftEngine")) CraftEngineItemProvider() else null
    private val items = ItemFactory(listOfNotNull(custom))
    private val delivery = MailboxDelivery(service,integrations,items,text,thread,::failure) { preferences.get(it)?.notifications != false }
    private var introduction: IntroductionRuntime? = null
    private val preferences=PlayerDisplayPreferences(plugin,db,thread,::failure)
    private var tutorialWorlds=settings.tutorialWorlds
    private val tutorial=ym.quest.tutorial.TutorialActions(plugin,service,integrations,::notify,::failure) { tutorialWorlds }
    private val journal=TaskJournalMenus(plugin,service,integrations,items,text,thread,preferences,settings,::failure) { player,view -> introduction?.decorate(player,view) ?: view }
    private val automatic = AutomaticRewards(plugin,service,integrations,items,{ custom?.id(it) },delivery,thread,::failure,settings.rewards) { !closed && !reloading }
    private var reloading = false
    private var closed = false
    private var tabSettings = settings.tab
    private var tab: TabQuestBridge? = null
    private var hudSettings=settings.hud
    private var hud: QuestHudBridge? = null
    private val heartbeat = plugin.server.scheduler.runTaskTimer(plugin,Runnable { if(!closed) integrations.discover() },1,20)
    init {
        service.claimChanges = ::notify
        validateItems(settings)
        plugin.server.pluginManager.registerEvents(this,plugin)
        configureIntroduction(settings)
        configureTab(settings.tab)
        configureHud(settings.hud)
        val commands = QuestCommands(text,service,integrations,thread,delivery,{
            if(closed) text.send(it,"unavailable")
            else journal.open(it)
        },::reload,::failure) { player,action ->
            if(closed || reloading) text.send(player,"busy")
            else introduction?.command(player,action) ?: text.send(player,"unavailable")
        }
        plugin.getCommand("quest")!!.apply { setExecutor(commands); tabCompleter=commands }
        plugin.logger.info("Quest ${plugin.pluginMeta.version}; Minecraft=${plugin.server.minecraftVersion}; Java=${System.getProperty("java.version")}; SQLite schema=5; task/chapter YAML schema=2; quests=${settings.quests.size}; chapters=${settings.chapters.size}; introduction=${settings.introduction.enabled}; Paper ownership; Folia=false; journal=enabled")
        listOf("DreamSkyblock","Genlite","CraftEngine","TAB","Adyeshach","Typewriter","Xerr").forEach { id ->
            plugin.logger.info("Integration $id=${plugin.server.pluginManager.getPlugin(id)?.pluginMeta?.version ?: "missing"}")
        }
    }
    private fun configureIntroduction(settings: Settings) {
        introduction?.close(); introduction=null
        if(settings.introduction.enabled) introduction=IntroductionRuntime(plugin,db,integrations,thread,text,settings.introduction,settings.chapters,settings.tab.lineWidth) { player,id,name -> hud?.personalComplete(player,id,name) }
    }
    private fun configureHud(settings: HudSettings) {
        hud?.close(); hud=null; hudSettings=settings
        if(!settings.enabled || !plugin.server.pluginManager.isPluginEnabled("Xerr")) return
        try { hud=QuestHudBridge(plugin,service,integrations,thread,text,settings,{ preferences.get(it) }) { introduction?.hudJournal(it) } }
        catch(e: LinkageError) { plugin.logger.log(Level.WARNING,"Xerr 2.2.0 API incompatible; quest HUD disabled",e) }
        plugin.logger.info("Quest HUD: enabled=${hud != null}; layouts=${settings.tracker}/${settings.notice}; assets=server Xerr only")
    }
    private fun configureTab(settings: TabSettings) {
        tab?.close(); tab=null; tabSettings=settings
        if(!settings.enabled || !plugin.server.pluginManager.isPluginEnabled("TAB")) return
        try { tab=TabQuestBridge(plugin,service,integrations,thread,text,settings) { introduction?.tabLines(it) } }
        catch(e: LinkageError) { plugin.logger.log(Level.WARNING,"TAB API incompatible; Quest task display disabled",e) }
    }
    private fun validateItems(settings: Settings) {
        if(custom == null) { plugin.logger.warning("CraftEngine missing; custom rewards remain unavailable"); return }
        (settings.quests.flatMap { listOf(it.icon)+it.rewards.map { r -> r.item } } + settings.chapters.flatMap { listOf(it.icon)+it.rewards.map { r -> r.item } }).distinct().forEach { id ->
            try { items.build(id) } catch(e: Exception) { plugin.logger.warning("Quest item $id not available: ${e.message}; claim/delivery guarded") }
        }
    }
    private fun notify(changes: List<ProgressChange>) {
        if(changes.isEmpty()) return
        thread.run {
            if(closed) return@run
            changes.groupBy { it.home }.forEach { (home,list) ->
                val id = UUID.fromString(home)
                if(list.any { it.completed }) automatic.completed(id)
                integrations.islands?.byId(id)?.members?.forEach { member ->
                    plugin.server.getPlayer(member)?.takeIf { it.hasPermission("quest.use") }?.let { player ->
                        preferences.get(player)?.takeIf { it.notifications }?.let { pref ->
                            list.forEach { notices.send(player,it,service.definitions,service.chapters,pref.sound) }
                        }
                        hud?.notify(player,id,list)
                    }
                }
                journal.refreshHome(id)
            }
        }
    }
    private fun reload(sender: CommandSender) {
        if(closed) { text.send(sender,"unavailable"); return }
        if(reloading) { text.send(sender,"busy"); return }
        reloading=true
        db.submit { files.load() }.whenComplete { settings,error -> thread.run {
            if(closed) return@run
            if(error != null) { reloading=false; failure(error); text.send(sender,"error"); return@run }
            try {
                (settings.quests.flatMap { listOf(it.icon)+it.rewards.map { r -> r.item } } + settings.chapters.flatMap { listOf(it.icon)+it.rewards.map { r -> r.item } }).distinct().forEach { items.build(it) }
                settings.journalMenus.values.forEach { y ->
                    y.getConfigurationSection("components")!!.getKeys(false).forEach { ch ->
                        listOf("item","item-on","item-off").mapNotNull { y.getString("components.$ch.$it") }.forEach { items.build(it) }
                    }
                }
            } catch(e: Exception) { reloading=false; failure(e); text.send(sender,"error"); return@run }
            service.configure(settings.quests,settings.chapters).whenComplete { _,configureError -> thread.run {
                if(closed) return@run
                reloading=false
                if(configureError != null) { failure(configureError); text.send(sender,"error"); return@run }
                text.language=settings.language; notices.profiles=settings.notices; tutorialWorlds=settings.tutorialWorlds
                journal.closeMenus(); journal.settings=settings
                automatic.reload(settings.rewards); integrations.refresh(); configureIntroduction(settings); configureTab(settings.tab); configureHud(settings.hud); text.send(sender,"reloaded")
            } }
        } }
    }
    private fun failure(error: Throwable) = plugin.logger.log(Level.SEVERE,"Quest operation failed; configuration/database retained; see cause",error)
    @EventHandler fun join(e: PlayerJoinEvent) { integrations.island(e.player.uniqueId)?.let { integrations.syncIsland(it.id) } }
    @EventHandler fun dependencyEnabled(e: PluginEnableEvent) {
        if(!closed && e.plugin.name == "TAB") configureTab(tabSettings)
        if(!closed && e.plugin.name == "Xerr") configureHud(hudSettings)
    }
    @EventHandler fun dependencyDisabled(e: PluginDisableEvent) {
        if(e.plugin.name == "TAB") { tab?.close(); tab=null }
        if(e.plugin.name == "Xerr") { hud?.close(); hud=null }
        if(e.plugin.name in setOf("DreamSkyblock","Genlite","CraftEngine")) { plugin.logger.warning("Dependency ${e.plugin.name} disabled; Quest paused until restart"); close() }
    }
    override fun close() {
        if(closed) return
        closed=true; service.claimChanges = {}; heartbeat.cancel(); tutorial.close(); journal.close(); preferences.close(); hud?.close(); hud=null; tab?.close(); tab=null; introduction?.close(); introduction=null; automatic.close(); delivery.close(); integrations.close()
    }
}
