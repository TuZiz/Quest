package cn.genlite.domain;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record WorldState(int schemaVersion, Map<UUID, Machine> machines, Map<UUID, Parcel> parcels,
                         Map<UUID,Map<String,BigInteger>> warehouses, Map<UUID,Boolean> hologramVisibility, Map<String,Long> questCounters) {
    public WorldState {
        questCounters=questCounters==null?Map.of():Map.copyOf(questCounters);
        machines = Map.copyOf(machines); parcels = Map.copyOf(parcels);
        warehouses=warehouses==null?Map.of():warehouses.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey,e -> Map.copyOf(e.getValue())));
        hologramVisibility=hologramVisibility==null?Map.of():Map.copyOf(hologramVisibility);
    }
    public WorldState(int version,Map<UUID,Machine> machines,Map<UUID,Parcel> parcels,Map<UUID,Map<String,BigInteger>> warehouses,Map<UUID,Boolean> holograms) {this(version,machines,parcels,warehouses,holograms,Map.of());}
    public WorldState(int version,Map<UUID,Machine> machines,Map<UUID,Parcel> parcels,Map<UUID,Map<String,BigInteger>> warehouses) {this(version,machines,parcels,warehouses,Map.of());}
    public WorldState(int version,Map<UUID,Machine> machines,Map<UUID,Parcel> parcels) {this(version,machines,parcels,Map.of());}
    public boolean hologramsEnabled(UUID home) {return hologramVisibility.getOrDefault(home,true);}
    public static WorldState empty() { return new WorldState(1, Map.of(), Map.of()); }
    public Draft draft() { return new Draft(this); }
    public static final class Draft {
        public final Map<String,Long> questCounters;
        public void count(UUID home,String metric,long amount) { if(amount<=0) throw new IllegalArgumentException("Invalid counter delta");questCounters.merge(home+"|"+metric,amount,Math::addExact); }
        public final Map<UUID, Machine> machines;
        public final Map<UUID, Parcel> parcels;
        public final Map<UUID,Map<String,BigInteger>> warehouses;
        public final Map<UUID,Boolean> hologramVisibility;
        Draft(WorldState source) { questCounters=new HashMap<>(source.questCounters); machines = new HashMap<>(source.machines); parcels = new HashMap<>(source.parcels);warehouses=new HashMap<>(source.warehouses);hologramVisibility=new HashMap<>(source.hologramVisibility); }
        public WorldState freeze() { return new WorldState(1, machines, parcels,warehouses,hologramVisibility,questCounters); }
        public void parcel(UUID player, String item, int amount, int level) {
            if (amount <= 0) return;
            Parcel p = Parcel.waiting(player, item, amount, level);
            parcels.put(p.id(), p);
        }
    }
}
