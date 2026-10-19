package ym.quest.reward

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import ym.quest.domain.Parcel
import ym.quest.domain.QuestService
import ym.quest.integration.Integrations
import ym.quest.platform.GameThread
import ym.quest.text.TextRenderer
import java.util.UUID

/** Main-thread inventory work + durable asynchronous state transitions. Never drop overflow on the ground. */
class MailboxDelivery(private val service: QuestService, private val integrations: Integrations,
                      private val items: ItemFactory, private val text: TextRenderer, private val thread: GameThread,
                      private val failure: (Throwable) -> Unit) : AutoCloseable {
    private val busy = mutableSetOf<UUID>()
    private val sending = mutableSetOf<String>()
    fun isSending(id: String) = id in sending
    private var closed = false
    fun collect(player: Player, home: UUID, quiet: Boolean = false, done: () -> Unit = {}) {
        if(closed || !busy.add(player.uniqueId)) { if(!quiet) text.send(player,"busy"); done(); return }
        service.mailbox(home.toString()).whenComplete { parcels,error -> thread.run {
            if(error != null) { busy.remove(player.uniqueId); failure(error); if(!quiet) text.send(player,"error"); done(); return@run }
            if(!valid(player,home)) { busy.remove(player.uniqueId); done(); return@run }
            if(!quiet && parcels.any { it.phase == "SENDING" }) text.send(player,"uncertain")
            next(player,home,parcels.filter { it.phase == "WAITING" },quiet,done)
        } }
    }
    private fun valid(p: Player,home: UUID) = !closed && p.isOnline && p.hasPermission("quest.use") &&
        integrations.island(p.uniqueId)?.id == home && integrations.allowed(home,p.uniqueId)
    private fun next(player: Player,home: UUID,parcels: List<Parcel>,quiet: Boolean,done: () -> Unit) {
        val parcel = parcels.firstOrNull()
        if(parcel == null || !valid(player,home)) { busy.remove(player.uniqueId); done(); return }
        val stacks = try { split(items.build(parcel.item,player),parcel.amount) }
        catch(e: Exception) { busy.remove(player.uniqueId); failure(e); if(!quiet) text.send(player,"unavailable"); done(); return }
        val planned = plan(player.inventory.storageContents,stacks)
        if(planned == null) { busy.remove(player.uniqueId); if(!quiet) text.send(player,"inventory-full"); done(); return }
        sending += parcel.id
        service.transition(parcel.id,player.uniqueId.toString(),"WAITING","SENDING").whenComplete { reserved,error -> thread.run {
            if(error != null || !reserved) {
                sending -= parcel.id; busy.remove(player.uniqueId)
                if(error != null) { failure(error); if(!quiet) text.send(player,"error") }
                done(); return@run
            }
            // Re-plan after async persistence: player inventory/membership may have changed.
            val now = if(valid(player,home)) plan(player.inventory.storageContents,stacks) else null
            if(now == null) {
                service.transition(parcel.id,player.uniqueId.toString(),"SENDING","WAITING").whenComplete { _,undoError -> thread.run {
                    sending -= parcel.id; busy.remove(player.uniqueId)
                    if(undoError != null) failure(undoError)
                    if(player.isOnline && !quiet) text.send(player,"inventory-full")
                    done()
                } }
                return@run
            }
            // One synchronous inventory replacement, no partial addItem/overflow and no intervening player action.
            try { player.inventory.storageContents = now }
            catch(e: Exception) { sending -= parcel.id; busy.remove(player.uniqueId); failure(e); if(!quiet) text.send(player,"uncertain"); done(); return@run }
            service.transition(parcel.id,player.uniqueId.toString(),"SENDING","DELIVERED").whenComplete { saved,saveError -> thread.run {
                sending -= parcel.id
                if(saveError != null || !saved) {
                    busy.remove(player.uniqueId); if(saveError != null) failure(saveError)
                    if(!quiet) text.send(player,"uncertain"); done()
                } else {
                    if(player.isOnline) text.send(player,"delivered")
                    next(player,home,parcels.drop(1),quiet,done)
                }
            } }
        } }
    }
    override fun close() { closed=true; busy.clear(); sending.clear() }
    companion object {
        fun split(unit: ItemStack,amount: Int): List<ItemStack> {
            require(amount > 0)
            var remaining = amount
            return buildList { while(remaining > 0) {
                val count = minOf(unit.maxStackSize,remaining)
                add(unit.clone().apply { this.amount=count }); remaining -= count
            } }
        }
        fun plan(current: Array<ItemStack?>, additions: List<ItemStack>): Array<ItemStack?>? {
            val result = current.map { it?.clone() }.toTypedArray()
            for(stack in additions) {
                var remaining = stack.amount
                for(existing in result) if(existing != null && existing.isSimilar(stack)) {
                    val n = minOf(remaining,(existing.maxStackSize-existing.amount).coerceAtLeast(0))
                    existing.amount += n; remaining -= n
                }
                for(slot in result.indices) if(remaining > 0 && (result[slot] == null || result[slot]!!.isEmpty)) {
                    val n = minOf(remaining,stack.maxStackSize)
                    result[slot] = stack.clone().apply { amount=n }; remaining -= n
                }
                if(remaining > 0) return null
            }
            return result
        }
    }
}
