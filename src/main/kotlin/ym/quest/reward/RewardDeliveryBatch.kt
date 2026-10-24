package ym.quest.reward

import ym.quest.domain.Parcel

/** Keep one task's immutable reward entries together during automatic recovery. */
object RewardDeliveryBatch {
    fun firstTask(waiting: List<Parcel>): List<Parcel> {
        val first=waiting.firstOrNull() ?: return emptyList()
        return waiting.filter { it.quest==first.quest }
    }
}
