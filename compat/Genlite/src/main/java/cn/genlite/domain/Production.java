package cn.genlite.domain;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

/** Pure production + collector transaction. Runtime owns due times, so no offline catch-up. */
public final class Production {
    private Production() {}
    public static Machine generate(Machine m, Rules rules) {
        Rules.Type type = rules.types().get(m.type());
        Rules.Level level = rules.level(m);
        int count = level.count();
        if (m.removed() || m.collectorType() || m.paused() || m.buffer()+count > level.bufferItems()) return m;
        int fuel = Math.multiplyExact(count, type.fuelPerItem());
        if (m.fuel()<fuel || (type.input()!=null && m.input()<count)) return m;
        return m.contents(m.fuel()-fuel, m.input()-(type.input()==null ? 0 : count), m.buffer()+count, m.inventory());
    }
    public static List<CollectorTransfer> transfer(WorldState.Draft draft, Rules rules, Set<UUID> active) {
        List<CollectorTransfer> transfers=new ArrayList<>();
        for (Machine original : Map.copyOf(draft.machines).values()) {
            Machine m = draft.machines.get(original.id());
            if (!active.contains(m.id()) || m.removed() || m.collectorType() || m.paused() || m.buffer()==0) continue;
            if(m.mode()==OutputMode.DROP) {
                if(pendingDrops(draft.parcels,m)>=8) continue;
                int amount=Math.min(64,m.buffer());
                Parcel drop=Parcel.production(m,rules.types().get(m.type()).output(),amount);
                draft.parcels.put(drop.id(),drop);
                draft.machines.put(m.id(),m.contents(m.fuel(),m.input(),m.buffer()-amount,m.inventory()));
                continue;
            }
            if(m.mode()==OutputMode.WAREHOUSE) {
                String item=rules.types().get(m.type()).output();
                var stock=new HashMap<>(draft.warehouses.getOrDefault(m.home(),Map.of()));
                int count=(stock.containsKey(item) || stock.size()<rules.warehouseSlots())?m.buffer():0;
                if(count<=0) continue;
                stock.merge(item,BigInteger.valueOf(count),BigInteger::add);draft.warehouses.put(m.home(),Map.copyOf(stock));
                draft.machines.put(m.id(),m.contents(m.fuel(),m.input(),m.buffer()-count,m.inventory()));
                continue;
            }
            Machine collector = draft.machines.get(m.collector());
            if (collector == null || !active.contains(collector.id()) || collector.removed() || collector.paused()
                    || !collector.collectorType() || !rules.inRange(m,collector)) continue;
            String item = rules.types().get(m.type()).output();
            int count = Math.min(m.buffer(),rules.capacityFor(collector.inventory(), item,rules.level(collector).collectorSlots()));
            if (count<=0) continue;
            Map<String,Integer> items = new HashMap<>(collector.inventory());
            items.merge(item, count, Math::addExact);
            draft.machines.put(collector.id(), collector.contents(collector.fuel(),collector.input(),collector.buffer(),items));
            draft.machines.put(m.id(),m.contents(m.fuel(),m.input(),m.buffer()-count,m.inventory()));
            draft.count(m.home(),"genlite.transfer",count);
            transfers.add(new CollectorTransfer(m.id(),collector.id(),m.position(),collector.position(),count));
        }
        return List.copyOf(transfers);
    }
    public static String status(Machine m, Rules rules, Map<UUID, Machine> machines, Set<UUID> active) {
        if (m.removed() || m.paused() || !active.contains(m.id())) return "paused";
        if (m.collectorType()) return "running";
        Rules.Type type = rules.types().get(m.type());
        int count = rules.level(m).count();
        if (m.buffer()+count>rules.level(m).bufferItems() || (type.input()!=null && m.input()<count)) return "paused";
        if (m.mode()==OutputMode.COLLECTOR) {
            Machine c = m.collector()==null?null:machines.get(m.collector());
            if (c==null || c.removed() || c.paused() || !active.contains(c.id()) || !rules.inRange(m,c)
                    || rules.capacityFor(c.inventory(),type.output(),rules.level(c).collectorSlots())<count) return "paused";
        }
        return m.fuel()<count*type.fuelPerItem() ? "no_fuel" : "running";
    }
    public static String status(Machine m,Rules rules,WorldState state,Set<UUID> active) {
        return status(m,rules,state.machines(),state.parcels(),state.warehouses(),active);
    }
    public static String status(Machine m,Rules rules,Map<UUID,Machine> machines,Map<UUID,Parcel> parcels,
                                Map<UUID,Map<String,BigInteger>> warehouses,Set<UUID> active) {
        String status=status(m,rules,machines,active);
        if(!status.equals("running") || m.collectorType()) return status;
        if(m.mode()==OutputMode.DROP && pendingDrops(parcels,m)>=8) return "paused";
        if(m.mode()==OutputMode.WAREHOUSE) {
            var stock=warehouses.getOrDefault(m.home(),Map.of());
            if(!stock.containsKey(rules.types().get(m.type()).output()) && stock.size()>=rules.warehouseSlots()) return "paused";
        }
        return status;
    }
    private static long pendingDrops(Map<UUID,Parcel> parcels,Machine m) {
        return parcels.values().stream().filter(p -> p.shellSource()==null && m.position().equals(p.dropPosition())).count();
    }
}
