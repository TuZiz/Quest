from pathlib import Path
import sys
root = Path(__file__).resolve().parents[1]
base = Path(sys.argv[1]) if len(sys.argv) > 1 else root.parent

# Current dependency repositories can include the compatibility hooks natively.
# In that case build their current files without replaying older overlays.
native_projects = set()
genlite_api = base / 'Genlite/src/main/java/cn/genlite/api/GenliteQuestApi.java'
genlite_engine = base / 'Genlite/src/main/java/cn/genlite/domain/MachineEngine.java'
if (genlite_api.exists() and 'TeachingMachine' in genlite_api.read_text(encoding='utf-8-sig')
        and 'questListener' in genlite_engine.read_text(encoding='utf-8-sig')):
    native_projects.add('Genlite')
dream_service = base / 'DreamSkyblock/src/main/kotlin/ym/dreamSkyblock/service/HomeManager.kt'
dream_gui = base / 'DreamSkyblock/src/main/kotlin/ym/dreamSkyblock/gui/HomeGuiManager.kt'
if ('HomeMembershipCommittedEvent' in dream_service.read_text(encoding='utf-8-sig')
        and 'HomeMenuOpenedEvent' in dream_gui.read_text(encoding='utf-8-sig')):
    native_projects.add('DreamSkyblock')
def read(project, path):
    return (base / project / path).read_text(encoding='utf-8-sig')
def write(project, path, text):
    if project in native_projects:
        return
    p = root / 'compat' / project / path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding='utf-8')
def change(project, path, replacements):
    if project in native_projects:
        return
    text = read(project, path)
    for a,b in replacements:
        assert a in text, (path,a)
        text = text.replace(a,b)
    write(project,path,text)
g='src/main/java/cn/genlite/'
change('Genlite',g+'domain/WorldState.java',[
('Map<UUID,Boolean> hologramVisibility) {','Map<UUID,Boolean> hologramVisibility, Map<String,Long> questCounters) {'),
('machines = Map.copyOf(machines);','questCounters=questCounters==null?Map.of():Map.copyOf(questCounters);\n        machines = Map.copyOf(machines);'),
('    public WorldState(int version,Map<UUID,Machine> machines,Map<UUID,Parcel> parcels,Map<UUID,Map<String,BigInteger>> warehouses)', '    public WorldState(int version,Map<UUID,Machine> machines,Map<UUID,Parcel> parcels,Map<UUID,Map<String,BigInteger>> warehouses,Map<UUID,Boolean> holograms) {this(version,machines,parcels,warehouses,holograms,Map.of());}\n    public WorldState(int version,Map<UUID,Machine> machines,Map<UUID,Parcel> parcels,Map<UUID,Map<String,BigInteger>> warehouses)'),
('public final Map<UUID, Machine> machines;', 'public final Map<String,Long> questCounters;\n        public void count(UUID home,String metric,long amount) { if(amount<=0) throw new IllegalArgumentException("Invalid counter delta");questCounters.merge(home+"|"+metric,amount,Math::addExact); }\n        public final Map<UUID, Machine> machines;'),
('Draft(WorldState source) {', 'Draft(WorldState source) { questCounters=new HashMap<>(source.questCounters);'),
('new WorldState(1, machines, parcels,warehouses,hologramVisibility)', 'new WorldState(1, machines, parcels,warehouses,hologramVisibility,questCounters)')])
change('Genlite',g+'domain/MachineActions.java',[
('public void deposit(WorldState.Draft d, UUID id, UUID player, String kind, int amount, int expectedLevel) {','public void deposit(WorldState.Draft d, UUID id, UUID player, String kind, int amount, int expectedLevel) {\n        if(amount<=0 || amount>1000000) throw new IllegalArgumentException("empty");'),
('d.machines.put(id,m.contents(m.fuel()+amount*rules.fuelUnits(m),m.input(),m.buffer(),m.inventory()));','d.machines.put(id,m.contents(m.fuel()+amount*rules.fuelUnits(m),m.input(),m.buffer(),m.inventory()));\n            d.count(m.home(),"genlite.fuel",amount);')])
change('Genlite',g+'domain/MachineEngine.java',[
('private final AtomicStateFile storage;', 'private volatile java.util.function.Consumer<Map<String,Long>> questListener = ignored -> {};\n    public void questListener(java.util.function.Consumer<Map<String,Long>> listener) { questListener=listener; }\n    private final AtomicStateFile storage;'),
('try { storage.write(next); state=next; }','try {\n            boolean changed=!next.questCounters().equals(state.questCounters());\n            storage.write(next); state=next;\n            if(changed && !closed) { try {questListener.accept(next.questCounters());} catch(Exception eventError) {log.log(Level.WARNING,"Quest counter notification failed; durable snapshot available for replay",eventError);} }\n        }'),
('draft.machines.put(m.id(),Production.generate(m,rules));','Machine generated=Production.generate(m,rules);\n                    draft.machines.put(m.id(),generated);\n                    int produced=generated.buffer()-m.buffer();\n                    if(produced>0) draft.count(m.home(),"genlite.produce:"+rules.types().get(m.type()).output(),produced);')])
write('Genlite',g+'api/GenliteQuestApi.java','''package cn.genlite.api;
import java.util.*;
/** Immutable, nonblocking snapshots; counters are durable, monotonic per home and metric. */
public interface GenliteQuestApi {
    boolean ready();
    Map<String,Long> counters();
    boolean hasGenerator(UUID home, Set<UUID> members);
}
''')
write('Genlite',g+'api/GenliteCountersCommittedEvent.java','''package cn.genlite.api;
import org.bukkit.event.*;
import java.util.Map;
/** Fired on the Genlite IO executor AFTER atomic persistence. Never access Bukkit state in handlers. */
public final class GenliteCountersCommittedEvent extends Event {
    private static final HandlerList HANDLERS=new HandlerList();
    private final Map<String,Long> counters;
    public GenliteCountersCommittedEvent(Map<String,Long> counters) {super(true);this.counters=Map.copyOf(counters);}
    public Map<String,Long> counters() {return counters;}
    @Override public HandlerList getHandlers() {return HANDLERS;}
    public static HandlerList getHandlerList() {return HANDLERS;}
}
''')
change('Genlite',g+'GenlitePlugin.java',[
('engine.start().whenComplete((v,error) -> {', '''getServer().getServicesManager().register(cn.genlite.api.GenliteQuestApi.class,new cn.genlite.api.GenliteQuestApi() {
            public boolean ready() {return engine.ready();}
            public java.util.Map<String,Long> counters() {return engine.snapshot().questCounters();}
            public boolean hasGenerator(java.util.UUID home,java.util.Set<java.util.UUID> members) {
                var s=engine.snapshot();
                return s.machines().values().stream().anyMatch(m -> m.home().equals(home) && !m.collectorType())
                    || s.parcels().values().stream().anyMatch(p -> members.contains(p.player()) && engine.rules().types().containsKey(p.item().replace("genlite:","")) && !p.item().equals("genlite:resource_collector"));
            }
        },this,org.bukkit.plugin.ServicePriority.Normal);
        engine.questListener(counters -> getServer().getPluginManager().callEvent(new cn.genlite.api.GenliteCountersCommittedEvent(counters)));
        engine.start().whenComplete((v,error) -> {'''),
('if(engine!=null) engine.close();startup.shutdown();','getServer().getServicesManager().unregisterAll(this);\n        if(engine!=null) engine.close();startup.shutdown();')])
d='src/main/kotlin/ym/dreamSkyblock/'
write('DreamSkyblock',d+'api/HomeMembershipCommittedEvent.kt','''package ym.dreamSkyblock.api

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.UUID

/** Paper main thread, after a successful durable creation or membership commit. */
class HomeMembershipCommittedEvent(val homeId: UUID, val playerId: UUID) : Event() {
    override fun getHandlers(): HandlerList = HANDLERS
    companion object {
        private val HANDLERS = HandlerList()
        @JvmStatic fun getHandlerList(): HandlerList = HANDLERS
    }
}
''')
change('DreamSkyblock',d+'service/HomeManager.kt',[
('if (player.isOnline) messages.send(player, if (ok && statusError == null)', 'if (ok && statusError == null) plugin.server.pluginManager.callEvent(ym.dreamSkyblock.api.HomeMembershipCommittedEvent(home.id, player.uniqueId))\n                        if (player.isOnline) messages.send(player, if (ok && statusError == null)'),
('            done(error == null)\n        } }\n    }\n\n    fun toggleFlag', '            if (error == null) plugin.server.pluginManager.callEvent(ym.dreamSkyblock.api.HomeMembershipCommittedEvent(home.id, player.uniqueId))\n            done(error == null)\n        } }\n    }\n\n    fun toggleFlag')])

# Additional teaching hooks are verified against the same current sources.
exec((root / "tools/make_tutorial_compat.py").read_text(encoding="utf-8"))
