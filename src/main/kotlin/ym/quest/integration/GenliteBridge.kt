package ym.quest.integration

import cn.genlite.api.GenliteQuestApi
import cn.genlite.api.GenliteCountersCommittedEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class GenliteBridge(private val api: GenliteQuestApi, private val committed: (Map<String,Long>) -> Unit) : ProductionGateway, Listener {
    override fun ready() = api.ready()
    override fun counters() = api.counters()
    override fun hasGenerator(island: Island) = api.hasGenerator(island.id,island.members)
    // Local API checks committed machine history OR parcels owned by the supplied members.
    // Empty members exclude unplaced parcel items. Removed machines retain placement history.
    override fun hasPlacedGenerator(island: Island) = api.hasGenerator(island.id,emptySet())
    @EventHandler fun counters(event: GenliteCountersCommittedEvent) = committed(event.counters())
}
