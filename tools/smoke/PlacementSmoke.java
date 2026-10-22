package ym.quest.smoke;

import cn.genlite.api.GenliteQuestApi;
import cn.genlite.domain.*;
import org.bukkit.plugin.java.JavaPlugin;
import ym.quest.integration.GenliteBridge;
import ym.quest.integration.Island;
import java.util.*;

/** Writes artificial machine/parcel records ONLY in target/smoke; no real client placement claim. */
public final class PlacementSmoke extends JavaPlugin {
    @Override public void onEnable() { getServer().getScheduler().runTaskLater(this,this::check,160); }
    private void check() {
        try {
            var plugin=getServer().getPluginManager().getPlugin("Genlite");
            var field=plugin.getClass().getDeclaredField("engine");field.setAccessible(true);
            var engine=(MachineEngine)field.get(plugin);
            var api=getServer().getServicesManager().load(GenliteQuestApi.class);
            var quest=getServer().getPluginManager().getPlugin("Quest");
            var runtimeField=quest.getClass().getDeclaredField("runtime");runtimeField.setAccessible(true);
            var runtime=runtimeField.get(quest);
            var serviceField=runtime.getClass().getDeclaredField("service");serviceField.setAccessible(true);
            var service=(ym.quest.domain.QuestService)serviceField.get(runtime);
            var integrationsField=runtime.getClass().getDeclaredField("integrations");integrationsField.setAccessible(true);
            var bridge=((ym.quest.integration.Integrations)integrationsField.get(runtime)).getProduction();
            var home=UUID.randomUUID();var owner=UUID.randomUUID();
            var island=new Island(home,Set.of(owner),0,0L);
            var machine=Machine.create(new Position(UUID.randomUUID(),"probe",0,64,0),home,owner,"cobblestone_generator",1);
            engine.transact(draft->{draft.parcel(owner,"genlite:cobblestone_generator",1,1);return null;})
                .thenCompose(v->{
                    if(!bridge.hasGenerator(island) || bridge.hasPlacedGenerator(island)) throw new AssertionError("Parcel incorrectly counted as placement");
                    return engine.transact(draft->{draft.machines.put(machine.id(),machine);return null;});
                }).thenCompose(v->{
                    if(!bridge.hasPlacedGenerator(island)) throw new AssertionError("Committed placement not recognized");
                    return service.sync(home.toString(),Map.of("island.member",1L,"genlite.placed",1L,"genlite.fuel",0L,"genlite.produce:minecraft:cobblestone",0L));
                }).thenCompose(v->service.journal(home.toString())).thenCompose(journal->{
                    var task=journal.getQuests().stream().filter(q->q.getDefinition().getId().equals("first_fuel")).findFirst().orElseThrow();
                    if(task.getStatus()!=ym.quest.domain.QuestStatus.ACTIVE || task.getProgress().getFirst().getProgress()!=0L) throw new AssertionError("Opening/unfilled chest completed fuel task");
                    return engine.transact(draft->{new ContainerStock(engine.rules(),(h,p)->true).deposit(draft,owner,new ContainerStock.Source(ContainerStock.Kind.FUEL,machine.id(),engine.rules().fuelItem(machine)),1);return null;});
                }).thenCompose(v->{
                    long count=api.counters().getOrDefault(home+"|genlite.fuel",0L);
                    if(count!=1L) throw new AssertionError("Fuel chest successful deposit missing from API: "+count);
                    return service.sync(home.toString(),Map.of("island.member",1L,"genlite.placed",1L,"genlite.fuel",count,"genlite.produce:minecraft:cobblestone",0L));
                }).thenCompose(v->service.journal(home.toString())).thenAccept(journal->{
                    var task=journal.getQuests().stream().filter(q->q.getDefinition().getId().equals("first_fuel")).findFirst().orElseThrow();
                    if(task.getStatus()!=ym.quest.domain.QuestStatus.COMPLETE) throw new AssertionError("Committed chest fuel did not complete Quest task");
                    getLogger().info("QUEST_FUEL_CHEST_PASSED: actual ContainerStock FUEL commit -> published counter -> Quest first_fuel COMPLETE; no deposit stayed ACTIVE");
                }).whenComplete((v,error)->{
                    if(error!=null) getLogger().log(java.util.logging.Level.SEVERE,"QUEST_PLACEMENT_FAILED",error);
                    else if(!bridge.hasPlacedGenerator(island)) getLogger().severe("QUEST_PLACEMENT_FAILED: committed machine not recognized");
                    else getLogger().info("QUEST_PLACEMENT_PASSED: actual Genlite API excludes unplaced parcel and recognizes committed machine history; simulated state in isolated server");
                });
        } catch(Throwable error) { getLogger().log(java.util.logging.Level.SEVERE,"QUEST_PLACEMENT_FAILED",error); }
    }
}
