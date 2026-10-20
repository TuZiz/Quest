package ym.quest.tab

import me.neznamy.tab.api.TabAPI
import me.neznamy.tab.api.placeholder.PlaceholderManager
import me.neznamy.tab.api.placeholder.PlayerPlaceholder
import org.bukkit.plugin.java.JavaPlugin
import ym.quest.domain.QuestService
import ym.quest.integration.Integrations
import ym.quest.platform.GameThread
import ym.quest.text.TextRenderer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level

/** Paper main-thread ownership for players/islands/TAB updates; DB stays on Quest's IO executor. */
class TabQuestBridge(private val plugin: JavaPlugin, private val service: QuestService,
    private val integrations: Integrations, private val thread: GameThread, text: TextRenderer, settings: TabSettings) : AutoCloseable {
    private val projection = TabQuestProjection(text, settings.lineWidth)
    private val states = listOf("loading", "no-island", "no-permission", "unavailable", "error").associateWith(projection::state)
    private val cache = ConcurrentHashMap<UUID, List<String>>()
    private val bindings = mutableMapOf<UUID, UUID>()
    private val pending = mutableMapOf<UUID, Any>()
    private val failed = mutableSetOf<UUID>()
    private var manager: PlaceholderManager? = null
    private val placeholders = mutableListOf<PlayerPlaceholder>()
    private var closed = false
    private var waitingForApi = false
    private val task = plugin.server.scheduler.runTaskTimer(plugin, Runnable { refresh() }, 1L, settings.refreshTicks)

    init { plugin.logger.info("TAB quest layout enabled; placeholders=%quest_line_1%..%quest_line_18%; read-only island journal; refresh=${settings.refreshTicks} ticks") }

    private fun activeApi(): TabAPI? = try {
        TabAPI.getInstance().also { waitingForApi=false }
    } catch(e: IllegalStateException) {
        if(!waitingForApi) {
            waitingForApi=true
            plugin.logger.warning("TAB API not ready; quest display waits for TAB startup/reload; ${e.message}")
        }
        null
    }

    private fun connect(api: TabAPI): TabAPI {
        val next = api.placeholderManager
        if(manager !== next || placeholders.size != TabQuestProjection.LINE_COUNT || placeholders.indices.any {
            next.getPlaceholder(id(it)) !== placeholders[it]
        }) {
            if(manager === next) unregister() else { placeholders.clear(); manager=null }
            manager = next
            repeat(TabQuestProjection.LINE_COUNT) { index ->
                placeholders += next.registerPlayerPlaceholder(id(index), 1000) { player ->
                    (cache[player.uniqueId] ?: states.getValue("loading"))[index]
                }
            }
        }
        return api
    }

    private fun refresh() {
        if(closed || !plugin.isEnabled || !plugin.server.pluginManager.isPluginEnabled("TAB")) return
        val api = try { connect(activeApi() ?: return) } catch(e: Exception) {
            plugin.logger.log(Level.WARNING, "TAB quest placeholder registration failed; stopping display bridge", e)
            close(); return
        }
        val online = plugin.server.onlinePlayers.map { it.uniqueId }.toSet()
        cache.keys.retainAll(online); bindings.keys.retainAll(online)
        val homes = mutableSetOf<UUID>()
        plugin.server.onlinePlayers.forEach { player ->
            val id = player.uniqueId
            val state = when {
                !player.hasPermission("quest.use") -> "no-permission"
                integrations.islands?.ready() != true -> "unavailable"
                integrations.island(id) == null -> "no-island"
                else -> null
            }
            if(state != null) { bindings.remove(id); publish(api, id, states.getValue(state)); return@forEach }
            val home = integrations.island(id)!!.id
            if(bindings.put(id, home) != home) publish(api, id, states.getValue("loading"))
            homes += home
        }
        homes.forEach { home ->
            if(home in pending) return@forEach
            val ticket = Any(); pending[home] = ticket
            service.journal(home.toString()).whenComplete { journal, error -> thread.run {
                if(closed || pending[home] !== ticket) return@run
                pending.remove(home)
                if(!plugin.server.pluginManager.isPluginEnabled("TAB")) return@run
                val currentApi = connect(activeApi() ?: return@run)
                val lines = if(error != null) {
                    if(failed.add(home)) plugin.logger.log(Level.WARNING, "TAB quest journal failed; island=$home; progress retained", error)
                    states.getValue("error")
                } else { failed.remove(home); projection.project(journal) }
                // Membership, permission, player connection and ownership are rechecked after IO.
                bindings.filterValues { it == home }.keys.toList().forEach { id ->
                    val player = plugin.server.getPlayer(id)
                    if(player?.isOnline == true && player.hasPermission("quest.use") && integrations.island(id)?.id == home)
                        publish(currentApi, id, lines)
                }
            } }
        }
        failed.retainAll(homes)
    }

    private fun publish(api: TabAPI, id: UUID, lines: List<String>) {
        val old = cache.put(id, lines)
        val player = api.getPlayer(id)?.takeIf { it.isLoaded } ?: return
        lines.forEachIndexed { index, value -> if(old?.get(index) != value) placeholders.getOrNull(index)?.updateValue(player, value) }
    }

    private fun id(index: Int) = "%quest_line_${index + 1}%"
    private fun unregister() {
        manager?.let { previous -> placeholders.forEachIndexed { index, placeholder ->
            if(previous.getPlaceholder(id(index)) === placeholder) previous.unregisterPlaceholder(placeholder)
        } }
        placeholders.clear(); manager = null
    }

    override fun close() {
        if(closed) return
        closed = true; task.cancel()
        val api = if(plugin.server.pluginManager.isPluginEnabled("TAB")) activeApi() else null
        if(api != null && api.placeholderManager === manager) {
            api.onlinePlayers.filter { it.isLoaded }.forEach { player ->
                placeholders.forEach { it.updateValue(player, "") }
            }
            unregister()
        } else { placeholders.clear(); manager=null }
        cache.clear(); bindings.clear(); pending.clear(); failed.clear()
    }
}
