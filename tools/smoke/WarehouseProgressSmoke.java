package ym.quest.smoke;

import cn.playervault.api.*;
import cn.playervault.access.*;
import cn.playervault.config.SharingSettings;
import cn.playervault.inventory.*;
import cn.playervault.storage.VaultLedger;
import cn.playervault.text.Messages;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import ym.quest.domain.*;
import ym.quest.integration.*;
import ym.quest.storage.Database;
import java.lang.reflect.*;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Isolated server fixture only. No real player, world, production DB or desktop is touched. */
public final class WarehouseProgressSmoke extends JavaPlugin {
    private final UUID home=UUID.randomUUID(),actor=UUID.randomUUID();
    private final WarehouseTarget target=WarehouseTarget.team(home);
    private final ItemStack[] contents=new ItemStack[36];
    private Database db;
    private Integrations integrations;
    private QuestService service;
    private VaultLedger ledger;
    private cn.playervault.platform.GameTasks tasks;
    private final AtomicBoolean finished=new AtomicBoolean();
    private final AtomicReference<String> message=new AtomicReference<>();
    private Object field(Object object,String name) throws Exception {var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private void field(Object object,String name,Object value) throws Exception {var f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
    private void global(Runnable action) {getServer().getGlobalRegionScheduler().execute(this,() -> {try {action.run();} catch(Throwable error) {fail(error);}});}
    private void fail(Throwable error) {if(finished.compareAndSet(false,true)) getLogger().log(java.util.logging.Level.SEVERE,"WAREHOUSE_PROGRESS_FAILED",error);}
    private void require(boolean value,String detail) {if(!value) throw new AssertionError(detail);}
    @Override public void onEnable() {
        getServer().getScheduler().runTaskLater(this,() -> {
            try {
                var quest=getServer().getPluginManager().getPlugin("Quest");
                var pv=getServer().getPluginManager().getPlugin("PlayerVault");
                require(quest!=null && quest.isEnabled() && pv!=null && pv.isEnabled(),"plugin startup");
                ledger=(VaultLedger)field(pv,"ledger");require(ledger.ready(),"ledger readiness");
                db=new Database(getDataFolder().toPath().resolve("quest-test.db"),getLogger());
                service=new QuestService(db);
                var objective=new Objective("experience","genlite.extract",8,null,"fixture","genlite.extract",false);
                var definition=new QuestDefinition("first_extract","fixture",List.of(),"minecraft:chest",List.of(),List.of(objective),List.of(new Reward("genlite:fuel",8,false,null)));
                db.open().thenCompose((Object v) -> service.configure(List.of(definition))).whenComplete((Object v,Throwable error) -> {
                    if(error!=null) fail(error);else global(() -> prepare((JavaPlugin)quest));
                });
            } catch(Throwable error) {fail(error);}
        },120L);
    }
    private void prepare(JavaPlugin quest) {
        try {
            // Respect the owner's shaded Kotlin loader; the probe must not resolve Function1 through another plugin.
            var constructor=Arrays.stream(Integrations.class.getConstructors()).filter(c -> c.getParameterCount()==4).findFirst().orElseThrow();
            var callback=constructor.getParameterTypes()[2];
            var changes=Proxy.newProxyInstance(callback.getClassLoader(),new Class<?>[]{callback},(p,m,a) -> null);
            var failure=Proxy.newProxyInstance(callback.getClassLoader(),new Class<?>[]{callback},(p,m,a) -> {if(a!=null && a.length>0 && a[0] instanceof Throwable error) fail(error);return null;});
            integrations=(Integrations)constructor.newInstance(quest,service,changes,failure);
            var island=new Island(home,Set.of(actor),1,0);
            field(integrations,"islands",new IslandGateway() {
                public boolean ready(){return true;}
                public Island byMember(UUID id){return actor.equals(id)?island:null;}
                public Island byId(UUID id){return home.equals(id)?island:null;}
                public List<Island> all(){return List.of(island);}
                public boolean canClaim(UUID h,UUID p){return home.equals(h)&&actor.equals(p);}
            });
            field(integrations,"production",new ProductionGateway() {
                public boolean ready(){return true;}
                public Map<String,Long> counters(){return Map.of(home+"|genlite.extract",0L);}
                public boolean hasGenerator(Island i){return true;}
                public boolean hasPlacedGenerator(Island i){return true;}
            });
            integrations.discover();
            var facts=integrations.facts(home);
            require(facts.containsKey("integration.playervault.withdrawn"),"optional API/classloader discovery");
            service.sync(home.toString(),facts).whenComplete((v,error) -> {
                if(error!=null) fail(error);else global(this::withdraw);
            });
        } catch(Throwable error) {fail(error);}
    }
    private void withdraw() {
        try {
            tasks=new cn.playervault.platform.GameTasks(this);
            var language=new YamlConfiguration();
            for(String key:List.of("done","busy","inventory-full","unavailable","recovery-required","permission","stale")) language.set(key,"<#FFFFFF>"+key);
            var messages=new Messages(language);
            var teams=new TeamDirectory() {
                public boolean ready(){return true;}
                public UUID team(UUID id){return actor.equals(id)?home:null;}
                public boolean member(UUID h,UUID p){return h.equals(home)&&p.equals(actor);}
                public boolean exists(UUID h){return h.equals(home);}
                public String name(UUID h){return "fixture";}
            };
            var access=new WarehouseAccess(new SharingSettings(true,true,true),ledger,teams);
            var checkpoint=new InventoryCheckpoint(ledger,tasks,messages,getLogger(),p -> true);
            var runner=new TransactionRunner(ledger,tasks,messages,getLogger());
            var codec=new ResourceCodec(null);
            var transfers=new cn.playervault.inventory.InventoryTransfers(codec,ledger,runner,messages,getLogger(),checkpoint,access);
            var player=player();
            var item=codec.capture(player,new ItemStack(Material.COBBLESTONE),ledger.snapshot(target.storageId()));
            ledger.exchange(target.storageId(),UUID.randomUUID(),Map.of(item.id(),new ResourceAmount(item,BigInteger.valueOf(32))),Map.of()).whenComplete((view,error) -> {
                if(error!=null) {fail(error);return;}
                global(() -> {
                    Arrays.setAll(contents,i -> new ItemStack(Material.BEDROCK,64));
                    transfers.withdraw(player,target,item.id(),8,() -> true,() -> {throw new AssertionError("full inventory refreshed");});
                    require(message.get()!=null && message.get().contains("inventory-full"),"full inventory rejected");
                    require(ledger.withdrawnToInventory(target.storageId()).signum()==0,"failed withdrawal counted");
                    Arrays.fill(contents,null);
                    transfers.withdraw(player,target,item.id(),8,() -> true,() -> {
                        require(contents[0]!=null && contents[0].getType()==Material.COBBLESTONE && contents[0].getAmount()==8,"actual inventory delivery");
                        require(ledger.withdrawnToInventory(target.storageId()).equals(BigInteger.valueOf(8)),"committed withdrawal API");
                        integrations.discover();
                        // discover() must detect the changed external source and queue the sync itself.
                        service.views(home.toString()).whenComplete((views,e) -> {
                            if(e!=null) {fail(e);return;}
                            try {
                                require(views.getFirst().getStatus()==QuestStatus.COMPLETE,"Quest did not complete at 8/8");
                                require(ledger.withdrawnToInventory(WarehouseTarget.personal(home).storageId()).signum()==0,"personal/team identity leak");
                                if(finished.compareAndSet(false,true)) getLogger().info("WAREHOUSE_PROGRESS_PASSED: real plugin API/classloader, inventory-full rejection, actual ItemStack delivery+PDC+durable ACK, integration discovery, Quest 8/8");
                            } catch(Throwable failed) {fail(failed);}
                        });
                    });
                });
            });
        } catch(Throwable error) {fail(error);}
    }
    private Player player() {
        var inventory=(PlayerInventory)Proxy.newProxyInstance(PlayerInventory.class.getClassLoader(),new Class<?>[]{PlayerInventory.class},(p,m,a) -> switch(m.getName()) {
            case "getStorageContents" -> contents.clone();
            case "getMaxStackSize" -> 64;
            case "setItem" -> {contents[(int)a[0]]=(ItemStack)a[1];yield null;}
            default -> null;
        });
        var scheduler=(EntityScheduler)Proxy.newProxyInstance(EntityScheduler.class.getClassLoader(),new Class<?>[]{EntityScheduler.class},(p,m,a) -> {
            if(m.getName().equals("execute")) {global((Runnable)a[1]);return true;}
            return null;
        });
        var pdc=new ItemStack(Material.PAPER).getItemMeta().getPersistentDataContainer();
        return (Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a) -> switch(m.getName()) {
            case "getUniqueId" -> actor;
            case "getInventory" -> inventory;
            case "getScheduler" -> scheduler;
            case "getPersistentDataContainer" -> pdc;
            case "isOnline","hasPermission" -> true;
            case "getName" -> "WarehouseFixture";
            case "sendMessage" -> {message.set(Arrays.toString(a));yield null;}
            default -> m.getReturnType()==boolean.class?false:null;
        });
    }
    @Override public void onDisable() {if(integrations!=null) integrations.close();if(db!=null) db.close();if(tasks!=null) tasks.close();}
}
