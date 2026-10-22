package ym.quest.introduction

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.event.server.PluginEnableEvent
import org.bukkit.plugin.java.JavaPlugin
import ym.quest.domain.JournalView
import ym.quest.domain.ChapterDefinition
import ym.quest.integration.Integrations
import ym.quest.platform.GameThread
import ym.quest.storage.Database
import ym.quest.tab.TabQuestProjection
import ym.quest.text.TextRenderer
import java.util.UUID
import java.util.logging.Level

/** Paper ownership thread only. SQL callbacks recheck player connection and session identity. */
class IntroductionRuntime(private val plugin: JavaPlugin, db: Database, private val integrations: Integrations,
    private val thread: GameThread, private val text: TextRenderer, private val settings: IntroductionSettings,
    private val chapters: List<ChapterDefinition>, width: Int,
    private val completed: (Player,String,String) -> Unit = { _,_,_ -> }) : Listener, AutoCloseable {
    private class State(val player: Player) {
        var loaded=false; var failed=false; var done=false; var saving=false
        var legacyCandidate=player.hasPlayedBefore()
        var session: Any? = null; var fallback=false; var replay=false; var deadline=0L; var cooldown=0L
    }
    private val store = IntroductionStore(db)
    private val states = mutableMapOf<UUID,State>()
    private val journal = IntroductionJournal(settings,text)
    private val projection = TabQuestProjection(text,width)
    private val actionBar = IntroductionActionBar(text)
    private var ady: AdyeshachInteraction? = null
    private var dialogue: IntroductionDialogue? = null
    private var closed = false
    private val timer = plugin.server.scheduler.runTaskTimer(plugin,Runnable {
        if(!closed) states.values.toList().forEach {
            resolveLegacy(it)
            if(it.session != null && System.currentTimeMillis() > it.deadline) {
                cancel(it); if(it.player.isOnline) text.send(it.player,"introduction.interrupted")
            }
            actionBar.refresh(it.player, valid(it) && it.player.hasPermission("quest.use") &&
                it.loaded && !it.failed && !it.done && !it.saving && it.session == null)
        }
    },20,20)
    init {
        plugin.server.pluginManager.registerEvents(this,plugin)
        connect()
        plugin.server.onlinePlayers.forEach(::load)
    }
    private fun connect() {
        if(closed) return
        if(ady == null && plugin.server.pluginManager.isPluginEnabled("Adyeshach")) try {
            ady=AdyeshachInteraction(plugin,thread,settings.npcId,::interact)
        } catch(e: Exception) { plugin.logger.log(Level.SEVERE,"Introduction NPC binding failed; 引导.yml:npc-id=${settings.npcId}",e) }
        if(dialogue == null && plugin.server.pluginManager.isPluginEnabled("Typewriter")) try {
            dialogue=TypewriterIntroduction(thread,text)
        } catch(e: LinkageError) { plugin.logger.log(Level.WARNING,"Typewriter beta-177 API unavailable; introduction uses chat confirmation",e) }
    }
    private fun valid(s: State) = !closed && s.player.isOnline && states[s.player.uniqueId] === s
    private fun load(player: Player) {
        if(closed || states[player.uniqueId]?.player === player) return
        val s=State(player); states[player.uniqueId]=s
        store.load(player.uniqueId,settings.taskId).whenComplete { outcome,error -> thread.run {
            if(!valid(s)) return@run
            if(error != null) { s.failed=true; log(s,"load",error); return@run }
            s.loaded=true; s.done=outcome != null
            // Existing island members are grandfathered; no forced restart of an established chapter.
            resolveLegacy(s)
        } }
    }
    private fun resolveLegacy(s: State) {
        if(!s.loaded || s.failed || s.saving || s.done || !s.legacyCandidate || integrations.islands?.ready() != true) return
        s.legacyCandidate=false
        if(integrations.island(s.player.uniqueId) != null) { cancel(s); save(s,IntroductionOutcome.LEGACY) }
    }
    fun decorate(player: Player, view: JournalView): JournalView {
        val s=states[player.uniqueId] ?: return view
        return if(s.loaded && !s.failed) journal.decorate(view,s.done) else view
    }
    fun tabLines(player: Player): List<String>? {
        load(player)
        val s=states[player.uniqueId] ?: return null
        if(s.failed) return projection.state("error")
        resolveLegacy(s)
        if(!s.loaded || s.saving || (s.legacyCandidate && integrations.islands?.ready() != true)) return projection.state("loading")
        if(s.done) return null
        if(ady == null) return introState("introduction.npc-unavailable")
        val chapter=chapters.firstOrNull { it.id == settings.chapterId }
        val view=JournalView(listOf(journal.quest(false)),chapter?.let {
            listOf(ym.quest.domain.ChapterView(it.copy(quests=listOf(settings.taskId)),ym.quest.domain.ChapterStatus.ACTIVE,0,false))
        } ?: emptyList())
        return projection.project(view)
    }
    private fun introState(key: String): List<String> = List(TabQuestProjection.LINE_COUNT) { if(it == 0) text.language.getString(key)!! else "" }
    fun hudJournal(player: Player): JournalView? {
        load(player)
        val s=states[player.uniqueId] ?: return null
        resolveLegacy(s)
        if(s.done) return null
        if(!s.loaded || s.failed || s.saving || (s.legacyCandidate && integrations.islands?.ready() != true)) return JournalView(emptyList(),emptyList())
        val chapter=chapters.firstOrNull { it.id == settings.chapterId }
        return JournalView(listOf(journal.quest(false)),chapter?.let {
            listOf(ym.quest.domain.ChapterView(it.copy(quests=listOf(settings.taskId)),ym.quest.domain.ChapterStatus.ACTIVE,0,false))
        } ?: emptyList())
    }
    fun interact(player: Player) {
        if(!player.hasPermission("quest.use")) { text.send(player,"no-permission"); return }
        load(player)
        val s=states[player.uniqueId] ?: return
        if(s.failed) { text.send(player,"error"); return }
        resolveLegacy(s)
        if(!s.loaded || s.saving || (s.legacyCandidate && integrations.islands?.ready() != true)) { text.send(player,"busy"); return }
        if(s.session != null || System.currentTimeMillis() < s.cooldown) return
        s.cooldown=System.currentTimeMillis()+750
        if(s.done) { openIsland(player); return }
        start(s,false)
    }
    private fun start(s: State, replay: Boolean) {
        if(!valid(s) || s.session != null || s.saving) return
        val ticket=Any(); s.session=ticket; s.replay=replay; s.fallback=false
        actionBar.clear(s.player)
        s.deadline=System.currentTimeMillis()+settings.timeoutSeconds*1000L
        val lines=text.language.getStringList("introduction.dialogue")
        if(dialogue != null) try {
            if(dialogue!!.start(s.player,lines) { complete ->
                if(valid(s) && s.session === ticket) {
                    if(complete) finish(s) else { cancel(s); text.send(s.player,"introduction.interrupted") }
                }
            }) return
            cancel(s); text.send(s.player,"busy"); return
        } catch(e: Exception) { log(s,"start-dialogue; falling back to chat",e) }
          catch(e: LinkageError) { log(s,"Typewriter API; falling back to chat",e) }
        s.fallback=true
        lines.forEach { s.player.sendMessage(text.render(it)) }
        text.send(s.player,"introduction.fallback-confirm")
    }
    private fun finish(s: State) {
        val replay=s.replay; cancel(s)
        if(replay) text.send(s.player,"introduction.complete") else save(s,IntroductionOutcome.DONE)
    }
    private fun save(s: State, outcome: IntroductionOutcome) {
        if(s.saving) return
        s.saving=true
        actionBar.clear(s.player)
        store.complete(s.player.uniqueId,settings.taskId,outcome).whenComplete { _,error -> thread.run {
            if(!valid(s)) return@run
            s.saving=false
            if(error != null) { log(s,"save outcome=$outcome",error); text.send(s.player,"error"); return@run }
            s.done=true
            if(outcome != IntroductionOutcome.LEGACY) text.send(s.player,"introduction.complete")
            if(outcome == IntroductionOutcome.DONE) completed(s.player,settings.taskId,text.language.getString("introduction.task-name")!!)
        } }
    }
    fun command(player: Player, action: String) {
        if(!player.hasPermission("quest.use")) { text.send(player,"no-permission"); return }
        load(player)
        val s=states[player.uniqueId] ?: return
        if(s.failed) { text.send(player,"error"); return }
        resolveLegacy(s)
        if(!s.loaded || s.saving || (s.legacyCandidate && integrations.islands?.ready() != true)) { text.send(player,"busy"); return }
        when(action) {
            "finish" -> if(s.session != null && s.fallback) finish(s) else text.send(player,"introduction.no-dialogue")
            "skip" -> {
                cancel(s)
                if(!s.done) save(s,IntroductionOutcome.SKIPPED) else text.send(player,"introduction.complete")
            }
            "replay" -> if(s.done) start(s,true) else text.send(player,"introduction.npc-unavailable")
            else -> text.send(player,"introduction.help")
        }
    }
    private fun openIsland(player: Player) {
        if(integrations.islands?.ready() != true) { text.send(player,"unavailable"); return }
        // Exact namespaced public player command, verified against the local DreamSkyblock HomeCommand.
        // DreamSkyblock owns permission checks and chooses create/join versus island management GUI.
        if(!plugin.server.dispatchCommand(player,"dreamskyblock:home gui")) text.send(player,"unavailable")
    }
    private fun cancel(s: State) {
        s.session=null; s.fallback=false; s.replay=false
        dialogue?.cancel(s.player)
    }
    private fun log(s: State, operation: String, error: Throwable) = plugin.logger.log(Level.SEVERE,
        "Introduction $operation; player=${s.player.uniqueId}; task=${settings.taskId}; NPC=${settings.npcId}; progress retained",error)
    @EventHandler fun join(e: PlayerJoinEvent) = load(e.player)
    @EventHandler fun quit(e: PlayerQuitEvent) { actionBar.clear(e.player); states.remove(e.player.uniqueId)?.let(::cancel) }
    @EventHandler fun enabled(e: PluginEnableEvent) { if(e.plugin.name in setOf("Adyeshach","Typewriter")) connect() }
    @EventHandler fun disabled(e: PluginDisableEvent) {
        if(e.plugin.name == "Adyeshach") { ady?.close(); ady=null }
        if(e.plugin.name == "Typewriter") { states.values.forEach(::cancel); dialogue?.close(); dialogue=null }
    }
    override fun close() {
        if(closed) return
        closed=true; timer.cancel(); actionBar.close(); ady?.close(); ady=null
        states.values.forEach(::cancel); dialogue?.close(); dialogue=null; states.clear(); HandlerList.unregisterAll(this)
    }
}
