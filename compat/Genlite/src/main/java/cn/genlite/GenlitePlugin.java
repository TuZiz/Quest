package cn.genlite;

import cn.genlite.config.PluginFiles;
import cn.genlite.display.*;
import cn.genlite.domain.*;
import cn.genlite.gui.*;
import cn.genlite.integration.DreamBridge;
import cn.genlite.integration.IslandAccess;
import cn.genlite.platform.*;
import cn.genlite.storage.AtomicStateFile;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.concurrent.*;
import java.util.logging.Level;

/** Composition and lifecycle only. */
public final class GenlitePlugin extends JavaPlugin {
    private GameTasks tasks;
    private MachineEngine engine;
    private IslandAccess dream;
    private MachineMenus menus;
    private MachineListener listener;
    private MachineInteractions interactions;
    private InventoryTransfers transfers;
    private MachinePresence presence;
    private MachineDismantling dismantling;
    private ShellDrops drops;
    private CraftItems items;
    private MachineHolograms holograms;
    private CollectorParticles collectorParticles;
    private ContainerMenus warehouse;
    private HomeHologramControl homeHolograms;
    /** Invero node reads only immutable cached state; no world or file access. */
    public boolean hologramsEnabled(java.util.UUID player) {return homeHolograms!=null && homeHolograms.enabled(player);}
    public boolean hologramsReady() {return engine!=null && engine.ready() && homeHolograms!=null;}
    private final ExecutorService startup=Executors.newSingleThreadExecutor(r -> new Thread(r,"Genlite-config-io"));
    @Override public void onEnable() {
        tasks=new GameTasks(this);
        CompletableFuture.supplyAsync(() -> {
            try {return PluginFiles.load(this);} catch(Exception e) {throw new CompletionException(e);}
        },startup).whenComplete((files,error) -> {
            startup.shutdown();
            if(error!=null) {getLogger().log(Level.SEVERE,"Genlite configuration startup failed; original files preserved",error);tasks.global(() -> getServer().getPluginManager().disablePlugin(this));return;}
            tasks.global(() -> assemble(files));
        });
    }
    private void assemble(PluginFiles files) {
        dream=getServer().getPluginManager().isPluginEnabled("DreamSkyblock")?new DreamBridge(this):IslandAccess.unavailable();
        engine=new MachineEngine(new AtomicStateFile(files.storage()),files.rules(),getLogger());
        var actions=new MachineActions(files.rules(),dream::member);
        var messages=new Messages(files.language());
        var text=new MachineText(files.rules(),files.displays(),messages);
        items=new CraftItems(this,text,engine::snapshot);
        transfers=new InventoryTransfers(this,tasks,engine,items,messages,dream::member);
        warehouse=new ContainerMenus(files.menus(),engine,dream,items,messages,transfers,tasks,text);
        menus=new MachineMenus(files.menus(),engine,actions,dream,items,messages,transfers,text,warehouse);
        holograms=new MachineHolograms(this,engine,text);
        collectorParticles=new CollectorParticles(this,tasks,engine,files.collectorParticles());
        drops=new ShellDrops(this,tasks,engine,items);
        presence=new MachinePresence(this,tasks,engine,actions,dream,holograms,drops);
        dismantling=new MachineDismantling(this,tasks,engine,actions,presence,messages);
        listener=new MachineListener(this,tasks,engine,actions,dream,items,messages,menus,dismantling);
        interactions=new MachineInteractions(engine,actions,dream,items,messages,menus,transfers,dismantling);
        getServer().getPluginManager().registerEvents(listener,this);
        getServer().getPluginManager().registerEvents(interactions,this);
        getServer().getPluginManager().registerEvents(menus,this);
        getServer().getPluginManager().registerEvents(holograms,this);
        getServer().getPluginManager().registerEvents(collectorParticles,this);
        getServer().getPluginManager().registerEvents(warehouse,this);
        getServer().getPluginManager().registerEvents(new AutomaticDeliveryListener(tasks,transfers),this);
        getServer().getPluginManager().registerEvents(new MachineItemDisplayListener(items),this);
        homeHolograms=new HomeHologramControl(this,tasks,engine,dream,presence,messages);
        var command=new GenliteCommand(items,messages,transfers,engine,warehouse,homeHolograms);
        getCommand("genlite").setExecutor(command);getCommand("genlite").setTabCompleter(command);
        getServer().getServicesManager().register(cn.genlite.api.GenliteQuestApi.class,new cn.genlite.api.GenliteQuestApi() {
            public boolean ready() {return engine.ready();}
            public java.util.Map<String,Long> counters() {return engine.snapshot().questCounters();}
            public boolean hasGenerator(java.util.UUID home,java.util.Set<java.util.UUID> members) {
                var s=engine.snapshot();
                return s.machines().values().stream().anyMatch(m -> m.home().equals(home) && !m.collectorType())
                    || s.parcels().values().stream().anyMatch(p -> members.contains(p.player()) && engine.rules().types().containsKey(p.item().replace("genlite:","")) && !p.item().equals("genlite:resource_collector"));
            }
        },this,org.bukkit.plugin.ServicePriority.Normal);
        engine.questListener(counters -> getServer().getPluginManager().callEvent(new cn.genlite.api.GenliteCountersCommittedEvent(counters)));
        engine.start().whenComplete((v,error) -> {
            if(error!=null) {getLogger().log(Level.SEVERE,"Genlite state load failed; refusing to overwrite saved data",error);tasks.global(() -> getServer().getPluginManager().disablePlugin(this));return;}
            tasks.global(() -> tasks.repeat(() -> {dream.refresh(engine);presence.refresh();drops.refresh();warehouse.refresh();collectorParticles.refresh();}));
        });
        getLogger().info("Genlite 1.0.0; Minecraft="+getServer().getMinecraftVersion()+"; Java="+System.getProperty("java.version")+
                "; CraftEngine="+getServer().getPluginManager().getPlugin("CraftEngine").getPluginMeta().getVersion()+"; config schema=1; output text/menu schema=3; genlite/ files=4; warehouseSlots="+files.rules().warehouseSlots()+"; modes=DROP/COLLECTOR/WAREHOUSE; async durable storage");
        getLogger().info("DreamSkyblock membership/value API required for island operation. This plugin uses region/entity schedulers; installed DreamSkyblock currently declares Folia unsupported.");
        getLogger().info("Collector particles: enabled="+files.collectorParticles().enabled()+"; collector-particles.yml schema=1");
    }
    @Override public void onDisable() {
        if(collectorParticles!=null) collectorParticles.close();
        if(holograms!=null) holograms.close();
        if(warehouse!=null) warehouse.clear();
        if(tasks!=null) tasks.close();
        if(interactions!=null) interactions.clear();if(menus!=null) menus.clear();if(transfers!=null) transfers.clear();
        if(presence!=null) presence.clear();if(dismantling!=null) dismantling.clear();if(drops!=null) drops.clear();if(dream!=null) dream.clear();
        getServer().getServicesManager().unregisterAll(this);
        if(engine!=null) engine.close();startup.shutdown();
    }
}
