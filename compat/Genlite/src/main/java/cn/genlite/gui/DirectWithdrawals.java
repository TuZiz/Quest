package cn.genlite.gui;

import cn.genlite.domain.*;
import cn.genlite.platform.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;

/** Disk reservation -> player delivery -> ACK. No owner thread waits for disk; uncertain sends are never replayed. */
final class DirectWithdrawals {
    private final JavaPlugin plugin;
    private final GameTasks tasks;
    private final MachineEngine engine;
    private final CraftItems items;
    private final Messages messages;
    private final ContainerStock stock;
    private final Set<UUID> busy;
    DirectWithdrawals(JavaPlugin p,GameTasks t,MachineEngine e,CraftItems i,Messages m,ContainerStock s,Set<UUID> busy) {
        plugin=p;tasks=t;engine=e;items=i;messages=m;stock=s;this.busy=busy;
    }
    void take(Player player,ContainerStock.Source source,int requested,BooleanSupplier allowed,Runnable refresh) {
        take(player,source,requested,allowed,refresh,false);
    }
    void take(Player player,ContainerStock.Source source,int requested,BooleanSupplier allowed,Runnable refresh,boolean cursor) {
        UUID user=player.getUniqueId();
        if(!engine.ready() || !allowed.getAsBoolean()) {messages.send(player,"permission");return;}
        if(!busy.add(user)) {messages.send(player,"busy");return;}
        ItemStack unit;
        try {unit=items.build(player,source.item(),1,0);} catch(Exception error) {busy.remove(user);messages.send(player,"unavailable");plugin.getLogger().log(Level.WARNING,"Build withdrawal item="+source.item(),error);return;}
        int amount=Math.min(requested,cursor?ItemDelivery.cursorCapacity(player,unit):ItemDelivery.capacity(player,unit));
        if(amount<=0) {busy.remove(user);messages.send(player,"inventory-full");return;}
        var reserved=engine.transact(d -> stock.reserve(d,user,source,amount));
        apply(player,unit,reserved,source,allowed,refresh,false,cursor);
    }
    void recover(Player player) {
        UUID user=player.getUniqueId();
        if(!engine.ready() || !player.hasPermission("genlite.use") || !busy.add(user)) return;
        Parcel pending=engine.snapshot().parcels().values().stream().filter(p -> p.player().equals(user)&&p.phase().equals("WAITING")&&p.dropPosition()==null).findFirst().orElse(null);
        if(pending==null) {busy.remove(user);return;}
        ItemStack unit;
        try {unit=items.parcel(player,pending.amount(1));} catch(Exception error) {busy.remove(user);plugin.getLogger().log(Level.WARNING,"Build recovery parcel="+pending.id(),error);return;}
        int amount=Math.min(pending.amount(),ItemDelivery.capacity(player,unit));
        if(amount<=0) {busy.remove(user);return;}
        apply(player,unit,engine.transact(d -> PendingParcels.reserve(d,pending.id(),amount)),null,() -> player.hasPermission("genlite.use"),() -> {},true,false);
    }
    private void apply(Player player,ItemStack unit,CompletableFuture<Parcel> reservation,ContainerStock.Source source,BooleanSupplier allowed,Runnable refresh,boolean quiet,boolean cursor) {
        UUID user=player.getUniqueId();
        reservation.whenComplete((parcel,error) -> tasks.player(player,() -> {
            if(error!=null) {busy.remove(user);if(!quiet) messages.send(player,InventoryTransfers.errorKey(error));return;}
            if(!allowed.getAsBoolean() || (cursor?ItemDelivery.cursorCapacity(player,unit):ItemDelivery.capacity(player,unit))<parcel.amount()) {
                rollback(user,source,parcel);if(!quiet) messages.send(player,"inventory-full");return;
            }
            try {if(cursor) ItemDelivery.cursorGive(player,unit,parcel.amount());else ItemDelivery.give(player,unit,parcel.amount());}
            catch(Exception deliveryError) {
                busy.remove(user);plugin.getLogger().log(Level.SEVERE,"Delivery uncertain; quarantined parcel="+parcel.id()+", player="+user,deliveryError);return;
            }
            engine.transact(d -> {
                if(d.parcels.remove(parcel.id())!=null && source!=null && source.kind()==ContainerStock.Kind.COLLECTOR) {
                    Machine machine=d.machines.get(source.id());
                    if(machine!=null) d.count(machine.home(),"genlite.extract",parcel.amount());
                }
                return null;
            }).whenComplete((v,ackError) -> {
                busy.remove(user);
                if(ackError!=null) plugin.getLogger().log(Level.SEVERE,"Delivered parcel ACK failed; do not replay="+parcel.id(),ackError);
                else tasks.player(player,() -> {refresh.run();recover(player);},() -> {});
            });
        },() -> {if(error==null) rollback(user,source,parcel);else busy.remove(user);}));
    }
    private void rollback(UUID user,ContainerStock.Source source,Parcel parcel) {
        engine.transact(d -> {
            if(source!=null) stock.restore(d,source,parcel);
            else if(d.parcels.containsKey(parcel.id())) d.parcels.put(parcel.id(),parcel.waiting());
            return null;
        }).whenComplete((v,error) -> {
            busy.remove(user);
            if(error!=null) plugin.getLogger().log(Level.SEVERE,"Rollback/recovery failed; parcel="+parcel.id()+", player="+user,error);
        });
    }
}
