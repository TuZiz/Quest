package cn.genlite.domain;

import java.util.*;
import java.util.function.BiPredicate;

/** Mutations execute only inside the engine transaction; permissions checked again at execution. */
public final class MachineActions {
    private final Rules rules;
    private final BiPredicate<UUID,UUID> member;
    public MachineActions(Rules rules, BiPredicate<UUID,UUID> member) { this.rules=rules; this.member=member; }
    public Machine require(WorldState.Draft d, UUID id, UUID player) {
        Machine m=d.machines.get(id);
        if (m==null || m.removed()) throw new IllegalArgumentException("missing");
        if (!member.test(m.home(),player)) throw new IllegalArgumentException("permission");
        if (m.locked() && !m.owner().equals(player)) throw new IllegalArgumentException("locked");
        return m;
    }
    public Machine place(WorldState.Draft d, Machine m) {
        if (!member.test(m.home(),m.owner())) throw new IllegalArgumentException("permission");
        if (d.machines.values().stream().anyMatch(x -> (!x.removed() || !x.cleared()) && x.position().equals(m.position()))) throw new IllegalArgumentException("busy");
        d.machines.put(m.id(),m); return m;
    }
    public Machine unpack(WorldState.Draft d,Position p,UUID home,UUID player,String type,UUID source,UUID token) {
        Machine old=MachineShells.require(d.machines,type,source,token);
        Machine fresh=old.unpack(p,home,player);
        place(d,fresh);
        d.machines.put(source,old.consumeShell());
        return fresh;
    }
    public void toggle(WorldState.Draft d, UUID id, UUID player, String action) {
        Machine m=require(d,id,player);
        if (action.equals("lock") && !m.owner().equals(player)) throw new IllegalArgumentException("locked");
        if (action.equals("mode")) {
            Machine collector=d.machines.get(m.collector());
            if (collector==null || collector.removed() || !collector.collectorType() || !rules.inRange(m,collector))
                throw new IllegalArgumentException("collector-required");
        }
        if(action.equals("mode")) d.count(m.home(),"genlite.mode",1);
        d.machines.put(id,switch(action) {
            case "pause" -> m.settings(m.level(),!m.paused(),m.locked(),m.connected(),m.collector());
            case "lock" -> m.settings(m.level(),m.paused(),!m.locked(),m.connected(),m.collector());
            case "mode" -> m.output(m.mode().next());
            default -> throw new IllegalArgumentException("unavailable");
        });
    }
    public void bind(WorldState.Draft d, UUID generatorId, UUID collectorId, UUID player) {
        Machine m=require(d,generatorId,player), c=require(d,collectorId,player);
        if (m.collectorType() || !c.collectorType() || !rules.inRange(m,c)) throw new IllegalArgumentException("range");
        if(!c.id().equals(m.collector())) d.count(m.home(),"genlite.bind",1);
        d.machines.put(m.id(),m.settings(m.level(),m.paused(),m.locked(),m.connected(),c.id()).output(OutputMode.COLLECTOR));
    }
    public void deposit(WorldState.Draft d, UUID id, UUID player, String kind, int amount, int expectedLevel) {
        if(amount<=0 || amount>1000000) throw new IllegalArgumentException("empty");
        Machine m=require(d,id,player);
        if (m.level()!=expectedLevel) throw new IllegalArgumentException("busy");
        Rules.Level level=rules.level(m);
        if (kind.equals("fuel")) {
            if (m.collectorType() || m.fuel()+amount*rules.fuelUnits(m)>level.fuelItems()*rules.fuelUnits(m)) throw new IllegalArgumentException("full");
            d.machines.put(id,m.contents(m.fuel()+amount*rules.fuelUnits(m),m.input(),m.buffer(),m.inventory()));
            d.count(m.home(),"genlite.fuel",amount);
        } else if (kind.equals("input")) {
            if (rules.types().get(m.type()).input()==null || m.input()+amount>level.bufferItems()) throw new IllegalArgumentException("full");
            d.machines.put(id,m.contents(m.fuel(),m.input()+amount,m.buffer(),m.inventory()));
        } else throw new IllegalArgumentException("unavailable");
    }
    public void upgrade(WorldState.Draft d, UUID id, UUID player, int expectedLevel) {
        Machine m=require(d,id,player);
        if (m.level()!=expectedLevel || m.level()>=5) throw new IllegalArgumentException("max-level");
        d.machines.put(id,m.settings(m.level()+1,m.paused(),m.locked(),m.connected(),m.collector()));
    }
    public void withdraw(WorldState.Draft d, UUID id, UUID player, String item, String kind) {
        Machine m=require(d,id,player);
        int amount;
        if (kind.equals("stock") && m.collectorType()) {
            amount=Math.min(64,m.inventory().getOrDefault(item,0));
            Map<String,Integer> stock=new HashMap<>(m.inventory());
            stock.computeIfPresent(item,(k,n) -> n==amount ? null : n-amount);
            d.machines.put(id,m.contents(m.fuel(),m.input(),m.buffer(),stock));
        } else if (kind.equals("fuel")) {
            amount=Math.min(64,m.fuel()/rules.fuelUnits(m)); item=rules.fuelItem(m);
            d.machines.put(id,m.contents(m.fuel()-amount*rules.fuelUnits(m),m.input(),m.buffer(),m.inventory()));
        } else if (kind.equals("input")) {
            amount=Math.min(64,m.input()); item=rules.types().get(m.type()).input();
            d.machines.put(id,m.contents(m.fuel(),m.input()-amount,m.buffer(),m.inventory()));
        } else {
            amount=Math.min(64,m.buffer()); item=rules.types().get(m.type()).output();
            d.machines.put(id,m.contents(m.fuel(),m.input(),m.buffer()-amount,m.inventory()));
        }
        if (amount<=0 || item==null) throw new IllegalArgumentException("empty");
        d.parcel(player,item,amount,0);
    }
    public void dismantle(WorldState.Draft d, UUID id, UUID player) {
        Machine m=require(d,id,player);
        d.machines.put(id,m.retirement(false));
        // The shell is mailed only after the region confirms physical removal.
    }
    public Parcel cleared(WorldState.Draft d, UUID id) {
        Machine m=d.machines.get(id);
        if (m==null || !m.removed() || m.cleared()) return null;
        d.machines.put(id,m.retirement(true));
        if(RetiredSmelters.retired(m.type())) {RetiredSmelters.refund(d,m);return null;}
        Parcel parcel=Parcel.shell(m);
        d.parcels.put(parcel.id(),parcel);
        return parcel;
    }
}
