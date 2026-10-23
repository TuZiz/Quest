package cn.genlite.platform;

import cn.genlite.domain.*;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Durable drop outbox for packed machines and physical generator output. All spawning stays on region owners. */
public final class ShellDrops {
    private final JavaPlugin plugin;
    private final GameTasks tasks;
    private final MachineEngine engine;
    private final CraftItems items;
    private final Set<UUID> sending=ConcurrentHashMap.newKeySet();
    public ShellDrops(JavaPlugin p,GameTasks t,MachineEngine e,CraftItems i) {plugin=p;tasks=t;engine=e;items=i;}
    public void refresh() {
        for (Parcel parcel:engine.snapshot().parcels().values()) {
            if (parcel.dropPosition()==null || !parcel.phase().equals("WAITING")) continue;
            Position p=parcel.dropPosition(); World world=plugin.getServer().getWorld(p.world());
            if (world!=null) deliver(parcel,new Location(world,p.x(),p.y(),p.z()));
        }
    }
    /** Direct committed-outbox notification; periodic refresh remains recovery only. */
    public void deliver(Parcel parcel,Location blockLocation) {
        if (parcel==null || parcel.dropPosition()==null || !parcel.phase().equals("WAITING") || !sending.add(parcel.id())) return;
        Position p=parcel.dropPosition(); World world=blockLocation.getWorld();
        boolean output=parcel.shellSource()==null;
        Location location=blockLocation.clone().add(0.5,output?1.05:0.5,0.5);
        tasks.region(location,() -> {
            if (!world.isChunkLoaded(p.x()>>4,p.z()>>4)) {sending.remove(parcel.id());return;}
            if(output && !GeneratorDropSpace.available(location,parcel.item(),parcel.amount())) {sending.remove(parcel.id());return;}
            engine.transact(d -> {
                Parcel current=d.parcels.get(parcel.id());
                if(current==null || !current.phase().equals("WAITING")) throw new IllegalArgumentException("busy");
                d.parcels.put(parcel.id(),current.sending());return null;
            }).whenComplete((v,error) -> {
                if(error!=null) {sending.remove(parcel.id());return;}
                tasks.region(location,() -> {
                    try {
                        if(!world.isChunkLoaded(p.x()>>4,p.z()>>4) || (output && !GeneratorDropSpace.available(location,parcel.item(),parcel.amount()))) {
                            engine.transact(d -> {d.parcels.put(parcel.id(),parcel.waiting());return null;});return;
                        }
                        if(output) world.dropItem(location,items.parcel(null,parcel),entity -> {
                            entity.setVelocity(new org.bukkit.util.Vector(0,0.16,0));entity.setPickupDelay(10);
                            var machine=engine.snapshot().machines().values().stream().filter(m -> m.position().equals(parcel.dropPosition())).findFirst().orElse(null);
                            if(machine!=null) ProductionPickup.tag(entity,plugin,parcel.id(),machine.home());
                        });
                        else world.dropItemNaturally(location,items.parcel(null,parcel));
                        engine.transact(d -> {d.parcels.remove(parcel.id());return null;});
                    } catch(Exception e) {plugin.getLogger().log(Level.SEVERE,"Packed machine drop uncertain; do not replay parcel="+parcel.id(),e);}
                    finally {sending.remove(parcel.id());}
                });
            });
        });
    }
    public void clear() {sending.clear();}
}
