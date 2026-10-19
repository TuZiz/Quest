package ym.quest.platform

import org.bukkit.plugin.java.JavaPlugin

/** Quest targets Paper/Purpur/Leaf's main-thread ownership model. Folia is explicitly unsupported. */
class GameThread(private val plugin: JavaPlugin) {
    @Volatile var closed = false
    fun run(action: () -> Unit) {
        if(closed || !plugin.isEnabled) return
        try { plugin.server.scheduler.runTask(plugin,Runnable { if(!closed && plugin.isEnabled) action() }) }
        catch(e: org.bukkit.plugin.IllegalPluginAccessException) { if(!closed) throw e }
    }
}
