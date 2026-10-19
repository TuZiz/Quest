package ym.quest

import org.bukkit.command.CommandExecutor
import org.bukkit.plugin.java.JavaPlugin
import ym.quest.config.QuestFiles
import ym.quest.domain.ConditionRegistry
import ym.quest.domain.QuestService
import ym.quest.platform.GameThread
import ym.quest.platform.QuestRuntime
import ym.quest.storage.Database
import java.util.logging.Level

class Quest : JavaPlugin() {
    private lateinit var database: Database
    private lateinit var thread: GameThread
    private var runtime: QuestRuntime? = null
    override fun onEnable() {
        thread = GameThread(this)
        database = Database(dataFolder.toPath().resolve("data/quest.db"),logger)
        val files = QuestFiles(this,ConditionRegistry())
        val service = QuestService(database)
        getCommand("quest")!!.setExecutor(CommandExecutor { _,_,_,_ -> true })
        database.open().thenCompose { database.submit { files.load() } }
            .thenCompose { settings -> service.configure(settings.quests).thenApply { settings } }
            .whenComplete { settings,error -> thread.run {
                if(error != null) {
                    logger.log(Level.SEVERE,"Quest initialization failed: YAML/schema/SQLite; original files preserved",error)
                    server.pluginManager.disablePlugin(this)
                } else try { runtime=QuestRuntime(this,database,service,files,thread,settings) }
                catch(e: Exception) { logger.log(Level.SEVERE,"Quest runtime assembly failed",e); server.pluginManager.disablePlugin(this) }
            } }
    }
    override fun onDisable() {
        runtime?.close()
        if(::thread.isInitialized) thread.closed=true
        server.scheduler.cancelTasks(this)
        if(::database.isInitialized) database.close()
    }
}
