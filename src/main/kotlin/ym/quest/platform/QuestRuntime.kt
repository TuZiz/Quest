package ym.quest.platform

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.plugin.java.JavaPlugin
import ym.quest.config.*
import ym.quest.domain.*
import ym.quest.gui.QuestMenus
import ym.quest.integration.*
import ym.quest.reward.*
import ym.quest.storage.Database
import ym.quest.text.TextRenderer
import ym.quest.text.StoryNotices
import java.util.UUID
import java.util.logging.Level

class QuestRuntime(private val plugin: JavaPlugin, private val db: Database, private val service: QuestService,
                   private val files: QuestFiles, private val thread: GameThread, settings: Settings) : Listener, AutoCloseable {
    private val text = TextRenderer(settings.language)
    private val notices = StoryNotices(text,settings.notices)
    private val integrations = Integrations(plugin,service,::notify,::failure)
    private val custom = if(plugin.server.pluginManager.isPluginEnabled("CraftEngine")) CraftEngineItemProvider() else null
    private val items = ItemFactory(listOfNotNull(custom))
    private val delivery = MailboxDelivery(service,integrations,items,text,thread,::failure)
    private val menus = QuestMenus(plugin,service,integrations,items,text,thread,delivery,::claim,::failure,settings)
    private val claiming = mutableSetOf<UUID>()
    private var reloading = false
    private var closed = false
    private val heartbeat = plugin.server.scheduler.runTaskTimer(plugin,Runnable { if(!closed) integrations.discover() },1,20)
    init {
        service.claimChanges = ::notify
        validateItems(settings)
        plugin.server.pluginManager.registerEvents(menus,plugin)
        plugin.server.pluginManager.registerEvents(this,plugin)
        val commands = QuestCommands(text,service,integrations,thread,delivery,{ if(!closed) menus.open(it) else text.send(it,"unavailable") },::reload,::failure)
        plugin.getCommand("quest")!!.apply { setExecutor(commands); tabCompleter=commands }
        plugin.logger.info("Quest ${plugin.pluginMeta.version}; Minecraft=${plugin.server.minecraftVersion}; Java=${System.getProperty("java.version")}; SQLite schema=2; YAML schema=1; quests=${settings.quests.size}; chapters=${settings.chapters.size}; Paper ownership; Folia=false")
        listOf("DreamSkyblock","Genlite","CraftEngine").forEach { id ->
            plugin.logger.info("Integration $id=${plugin.server.pluginManager.getPlugin(id)?.pluginMeta?.version ?: "missing"}")
        }
    }
    private fun validateItems(settings: Settings) {
        if(custom == null) { plugin.logger.warning("CraftEngine missing; custom rewards remain unavailable"); return }
        (settings.quests.flatMap { listOf(it.icon)+it.rewards.map { r -> r.item } } + settings.chapters.flatMap { listOf(it.icon)+it.rewards.map { r -> r.item } }).distinct().forEach { id ->
            try { items.build(id) } catch(e: Exception) { plugin.logger.warning("Quest item $id not available: ${e.message}; claim/delivery guarded") }
        }
    }
    private fun claim(player: Player, home: UUID, quest: String) {
        if(closed || reloading || !claiming.add(player.uniqueId)) { text.send(player,"busy"); return }
        fun reject(key: String) { claiming.remove(player.uniqueId); text.send(player,key) }
        if(!player.hasPermission("quest.use") || !integrations.allowed(home,player.uniqueId) || integrations.island(player.uniqueId)?.id != home) { reject("no-permission"); return }
        val chapter = if(quest.startsWith("chapter:")) service.chapters.firstOrNull { it.id == quest.substringAfter(':') } else null
        val definition = if(chapter != null) QuestDefinition(quest,chapter.name,chapter.description,chapter.icon,emptyList(),emptyList(),chapter.rewards)
            else service.definitions.firstOrNull { it.id == quest } ?: run { reject("unavailable"); return }
        if((definition.objectives.any { it.type.startsWith("genlite.") } || definition.rewards.any { it.starter }) && integrations.production?.ready() != true) { reject("unavailable"); return }
        try { definition.rewards.forEach { items.build(it.item,player) } }
        catch(e: Exception) { failure(e); reject("unavailable"); return }
        val island = integrations.island(player.uniqueId)!!
        val starterIds = definition.rewards.filter { it.starter }.map { it.item }.toSet()
        val inInventory = island.members.any { member -> plugin.server.getPlayer(member)?.inventory?.storageContents?.any { stack ->
            stack != null && custom?.id(stack) in starterIds
        } == true }
        val hasGenerator = integrations.production?.hasGenerator(island) == true || inInventory
        val result = if(chapter != null) service.claimChapter(home.toString(),player.uniqueId.toString(),chapter.id,integrations.facts(home),hasGenerator)
            else service.claim(home.toString(),player.uniqueId.toString(),quest,integrations.facts(home),hasGenerator)
        result.whenComplete { claimed,error -> thread.run {
            claiming.remove(player.uniqueId)
            if(closed) return@run
            if(error != null) { failure(error); if(player.isOnline) text.send(player,"error"); return@run }
            if(!player.isOnline || integrations.island(player.uniqueId)?.id != home || !player.hasPermission("quest.use")) return@run
            text.send(player,if(claimed) "claimed" else "not-complete")
            menus.refreshHome(home)
            if(claimed) delivery.collect(player,home) { menus.refreshHome(home) }
        } }
    }
    private fun notify(changes: List<ProgressChange>) {
        if(changes.isEmpty()) return
        thread.run {
            if(closed) return@run
            changes.groupBy { it.home }.forEach { (home,list) ->
                val id = UUID.fromString(home)
                integrations.islands?.byId(id)?.members?.forEach { member ->
                    plugin.server.getPlayer(member)?.takeIf { it.hasPermission("quest.use") }?.let { player ->
                        list.forEach { notices.send(player,it,service.definitions,service.chapters) }
                    }
                }
                menus.refreshHome(id)
            }
        }
    }
    private fun reload(sender: CommandSender) {
        if(closed) { text.send(sender,"unavailable"); return }
        if(reloading) { text.send(sender,"busy"); return }
        reloading=true
        db.submit { files.load() }.whenComplete { settings,error -> thread.run {
            if(error != null) { reloading=false; failure(error); text.send(sender,"error"); return@run }
            try {
                (settings.quests.flatMap { listOf(it.icon)+it.rewards.map { r -> r.item } } + settings.chapters.flatMap { listOf(it.icon)+it.rewards.map { r -> r.item } }).distinct().forEach { items.build(it) }
                listOf(settings.center,settings.detail,settings.chapterMenu,settings.chapterDetail).forEach { y ->
                    y.getConfigurationSection("components")!!.getKeys(false).forEach { ch -> items.build(y.getString("components.$ch.item")!!) }
                }
            } catch(e: Exception) { reloading=false; failure(e); text.send(sender,"error"); return@run }
            service.configure(settings.quests,settings.chapters).whenComplete { _,configureError -> thread.run {
                reloading=false
                if(configureError != null) { failure(configureError); text.send(sender,"error"); return@run }
                menus.close(); menus.settings=settings; text.language=settings.language; notices.profiles=settings.notices
                integrations.refresh(); text.send(sender,"reloaded")
            } }
        } }
    }
    private fun failure(error: Throwable) = plugin.logger.log(Level.SEVERE,"Quest operation failed; configuration/database retained; see cause",error)
    @EventHandler fun join(e: PlayerJoinEvent) { integrations.island(e.player.uniqueId)?.let { integrations.syncIsland(it.id) } }
    @EventHandler fun dependencyDisabled(e: PluginDisableEvent) {
        if(e.plugin.name in setOf("DreamSkyblock","Genlite","CraftEngine")) { plugin.logger.warning("Dependency ${e.plugin.name} disabled; Quest paused until restart"); close() }
    }
    override fun close() {
        if(closed) return
        closed=true; service.claimChanges = {}; heartbeat.cancel(); menus.close(); delivery.close(); integrations.close(); claiming.clear()
    }
}
