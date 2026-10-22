package ym.quest.integration

import java.util.UUID

data class Island(val id: UUID, val members: Set<UUID>, val level: Int, val value: Long)
interface IslandGateway {
    fun ready(): Boolean
    fun byMember(player: UUID): Island?
    fun byId(home: UUID): Island?
    fun all(): List<Island>
    fun canClaim(home: UUID, player: UUID): Boolean
}
interface ProductionGateway {
    fun ready(): Boolean
    fun counters(): Map<String,Long>
    fun hasGenerator(island: Island): Boolean
    fun hasPlacedGenerator(island: Island): Boolean
}
