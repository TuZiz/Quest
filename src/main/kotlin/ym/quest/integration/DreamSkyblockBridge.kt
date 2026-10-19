package ym.quest.integration

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import ym.dreamSkyblock.api.DreamSkyblockApi
import ym.dreamSkyblock.api.HomeView
import ym.dreamSkyblock.api.HomeMembershipCommittedEvent
import java.util.UUID

class DreamSkyblockBridge(private val api: DreamSkyblockApi, private val changed: (UUID) -> Unit) : IslandGateway, Listener {
    private fun island(home: HomeView?) = home?.takeIf { it.status == "ACTIVE" }?.let { Island(it.homeId,it.members.toSet(),it.level,it.experience) }
    override fun ready() = api.isReady()
    override fun byMember(player: UUID) = island(api.findHomeByMember(player))
    override fun byId(home: UUID) = island(api.findHome(home))
    override fun all() = api.allHomes().mapNotNull(::island)
    override fun canClaim(home: UUID, player: UUID) = api.isMember(home,player) && api.permission(home,player,"stranger_container") && byId(home) != null
    @EventHandler fun membership(event: HomeMembershipCommittedEvent) = changed(event.homeId)
}
