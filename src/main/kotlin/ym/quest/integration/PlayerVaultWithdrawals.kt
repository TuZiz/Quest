package ym.quest.integration

import org.bukkit.plugin.java.JavaPlugin
import java.math.BigInteger
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level

/** Optional API bridge. Discovery is global-thread only; snapshot readers use immutable ledger projections on any thread. */
class PlayerVaultWithdrawals(private val plugin: JavaPlugin) {
    data class Snapshot(val available: Boolean, val total: Long? = null)
    private data class Reader(val snapshot: (UUID) -> Snapshot)
    @Volatile private var reader: Reader? = null
    @Volatile private var required = false
    private val warned=AtomicBoolean()

    fun discover() {
        val vault=plugin.server.pluginManager.getPlugin("PlayerVault")
        if(vault==null || !vault.isEnabled) {reader=null;return}
        try {
            val teamEnabled=vault.javaClass.getMethod("teamWarehouseEnabled").invoke(vault)
            if(teamEnabled==false) {reader=null;return}
            required=true // Unknown startup status must not initialize objectives from a false zero.
            if(teamEnabled!=true) {reader=null;return}
            if(reader!=null) return
            val type=Class.forName("cn.playervault.api.PlayerVaultApi",false,vault.javaClass.classLoader)
            @Suppress("UNCHECKED_CAST")
            val api=plugin.server.servicesManager.load(type as Class<Any>) ?: return
            val target=Class.forName("cn.playervault.api.WarehouseTarget",false,type.classLoader)
            val team=target.getMethod("team",UUID::class.java)
            val ready=type.getMethod("ready")
            val teamReady=type.getMethod("teamReady")
            val total=type.getMethod("withdrawnToInventory",target)
            reader=Reader { home ->
                if(ready.invoke(api)!=true || teamReady.invoke(api)!=true) Snapshot(false)
                else Snapshot(true,(total.invoke(api,team.invoke(null,home)) as BigInteger)
                    .min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact())
            }
            warned.set(false)
            plugin.logger.info("PlayerVault committed team warehouse withdrawal progress connected; historical totals use per-island baselines")
        } catch(error: ReflectiveOperationException) {reader=null;report(error)}
        catch(error: LinkageError) {reader=null;report(error)}
    }
    fun snapshot(home: UUID): Snapshot {
        val current=reader ?: return Snapshot(!required)
        return try {current.snapshot(home)}
        catch(error: ReflectiveOperationException) {report(error);Snapshot(false)}
        catch(error: RuntimeException) {report(error);Snapshot(false)}
    }
    private fun report(error: Throwable) {
        if(warned.compareAndSet(false,true)) plugin.logger.log(Level.SEVERE,
            "PlayerVault withdrawal progress unavailable; update PlayerVault with withdrawnToInventory API; extraction objectives wait without changing progress",error)
    }
    fun close() {reader=null;required=false;warned.set(false)}
}
