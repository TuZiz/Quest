package cn.genlite.domain;

import cn.genlite.storage.AtomicStateFile;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/** One owner for domain state and disk IO; publishes only durable immutable snapshots. */
public final class MachineEngine implements AutoCloseable {
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r,"Genlite-state-io"); t.setDaemon(false); return t;
    });
    private volatile java.util.function.Consumer<Map<String,Long>> questListener = ignored -> {};
    public void questListener(java.util.function.Consumer<Map<String,Long>> listener) { questListener=listener; }
    private final AtomicStateFile storage;
    private final Logger log;
    private final Rules rules;
    private final Map<UUID,Long> leases = new ConcurrentHashMap<>();
    private final Map<UUID,Long> due = new HashMap<>();
    private volatile Set<UUID> active = Set.of();
    private volatile WorldState state = WorldState.empty();
    private volatile boolean ready, closed, faulted;
    // Cosmetic receipts are bounded and never persisted or replayed after restart.
    private final ArrayBlockingQueue<CollectorTransfer> collectorTransfers=new ArrayBlockingQueue<>(256);

    public MachineEngine(AtomicStateFile storage, Rules rules, Logger log) {
        this.storage=storage; this.rules=rules; this.log=log;
    }
    public CompletableFuture<Void> start() {
        return CompletableFuture.runAsync(() -> {
            try {
                WorldState previous=storage.read();state=RetiredSmelters.migrate(previous);
                StateValidation.check(state,rules);
                state.machines().values().stream().filter(m -> m.collectorType() && rules.usedSlots(m.inventory())>27)
                        .forEach(m -> log.warning("Legacy collector overflow preserved; machine="+m.id()+", slots="+rules.usedSlots(m.inventory())+"/27; withdraw visible stacks to reveal the remainder; incoming transfers paused"));
                if(!state.equals(previous)) {storage.backupBeforeRetirement();storage.write(state);}
                state.parcels().values().stream().filter(p -> p.phase().equals("SENDING"))
                        .forEach(p -> log.warning("Uncertain delivery quarantined: parcel="+p.id()+", player="+p.player()+", item="+p.item()+", count="+p.amount()));
                if(closed) return;
                ready=true;
                worker.scheduleWithFixedDelay(this::tick,1,1,TimeUnit.SECONDS);
            } catch (Exception e) { throw new CompletionException(e); }
        },worker);
    }
    public WorldState snapshot() { return state; }
    public boolean ready() { return ready && !closed && !faulted; }
    public Rules rules() { return rules; }
    public Set<UUID> active() { return active; }
    public List<CollectorTransfer> drainCollectorTransfers() {
        List<CollectorTransfer> result=new ArrayList<>();collectorTransfers.drainTo(result);return List.copyOf(result);
    }
    public void lease(UUID id, boolean valid) { if (valid) leases.put(id,System.nanoTime()); else leases.remove(id); }
    public void invalidateWorld(UUID world) {
        state.machines().values().stream().filter(m -> m.position().world().equals(world)).forEach(m -> leases.remove(m.id()));
    }
    public Machine at(Position p) {
        return state.machines().values().stream().filter(m -> (!m.removed() || !m.cleared()) && m.position().equals(p)).findFirst().orElse(null);
    }
    public <T> CompletableFuture<T> transact(Function<WorldState.Draft,T> change) {
        if (!ready()) return CompletableFuture.failedFuture(new IllegalStateException("unavailable"));
        try { return CompletableFuture.supplyAsync(() -> {
            // Already accepted requests drain during disable; they perform no platform work.
            if (faulted) throw new IllegalStateException("unavailable");
            WorldState.Draft draft=state.draft();
            T result=change.apply(draft);
            commit(draft.freeze());
            return result;
        },worker); }
        catch(RejectedExecutionException e) {return CompletableFuture.failedFuture(new IllegalStateException("unavailable",e));}
    }
    private void commit(WorldState next) {
        if (next.equals(state)) return;
        try {
            boolean changed=!next.questCounters().equals(state.questCounters());
            storage.write(next); state=next;
            if(changed && !closed) { try {questListener.accept(next.questCounters());} catch(Exception eventError) {log.log(Level.WARNING,"Quest counter notification failed; durable snapshot available for replay",eventError);} }
        }
        catch (Exception e) {
            faulted=true; ready=false;
            log.log(Level.SEVERE,"Genlite state save failed; original preserved; production frozen",e);
            throw new CompletionException(e);
        }
    }
    private void tick() {
        if (!ready()) return;
        try {
            long now=System.nanoTime();
            Set<UUID> valid=new HashSet<>();
            leases.forEach((id,time) -> { if (now-time<TimeUnit.SECONDS.toNanos(4)) valid.add(id); });
            active=Set.copyOf(valid);
            due.keySet().retainAll(valid);
            WorldState.Draft draft=state.draft();
            // Drain existing buffers first. Production and transfer are persisted together.
            List<CollectorTransfer> transfers=new ArrayList<>(Production.transfer(draft,rules,valid));
            for (Machine m : List.copyOf(draft.machines.values())) {
                if (!valid.contains(m.id()) || m.removed() || m.collectorType()) continue;
                long interval=TimeUnit.SECONDS.toNanos(rules.level(m).seconds());
                long deadline=due.computeIfAbsent(m.id(), ignored -> now+interval);
                if (!Production.status(m,rules,draft.machines,draft.parcels,draft.warehouses,valid).equals("running")) {
                    due.put(m.id(),now+interval); continue;
                }
                if (now>=deadline) {
                    Machine generated=Production.generate(m,rules);
                    draft.machines.put(m.id(),generated);
                    int produced=generated.buffer()-m.buffer();
                    if(produced>0) draft.count(m.home(),"genlite.produce:"+rules.types().get(m.type()).output(),produced);
                    due.put(m.id(),now+interval);
                }
            }
            transfers.addAll(Production.transfer(draft,rules,valid));
            commit(draft.freeze());
            if(!closed) transfers.forEach(collectorTransfers::offer);
            if(closed) collectorTransfers.clear();
        } catch (Exception e) { log.log(Level.SEVERE,"Genlite production transaction failed; no partial publish",e); }
    }
    @Override public void close() {
        closed=true; ready=false; leases.clear(); active=Set.of();
        collectorTransfers.clear();
        // All published changes are already durable. Do not block an ownership thread on join/get.
        worker.shutdown();
    }
}
