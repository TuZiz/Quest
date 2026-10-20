package ym.quest.smoke;

import me.neznamy.tab.api.TabAPI;
import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.api.placeholder.PlayerPlaceholder;
import me.neznamy.tab.shared.features.layout.LayoutManagerImpl;
import me.neznamy.tab.shared.hook.MiniMessageHook;
import org.bukkit.plugin.java.JavaPlugin;
import ym.quest.tab.TabQuestProjection;
import ym.quest.text.TextRenderer;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Runs only in the isolated server against the verified TAB 6.2.1 binary. */
public final class TabSmoke extends JavaPlugin {
    private Object field(Object target, String name) throws Exception {
        var f=target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target);
    }
    @Override public void onEnable() {
        getServer().getScheduler().runTaskLater(this, () -> check("INITIAL"), 120L);
        getServer().getScheduler().runTaskTimer(this, () -> {
            if(Boolean.getBoolean("quest.tab.verify")) { System.clearProperty("quest.tab.verify"); check("RELOADED"); }
        }, 140L, 20L);
        getCommand("tabsmoke").setExecutor((sender,command,label,args) -> { check("RELOADED"); return true; });
    }
    @SuppressWarnings("unchecked")
    private void check(String phase) {
        try {
            var quest=getServer().getPluginManager().getPlugin("Quest");
            var runtime=field(quest,"runtime");
            var bridge=field(runtime,"tab");
            if(bridge==null) throw new AssertionError("Quest TAB bridge missing");
            var api=TabAPI.getInstance();
            if(api.getScoreboardManager()!=null || api.getBossBarManager()!=null) throw new AssertionError("Unexpected scoreboard or BossBar feature");
            var layout=(LayoutManagerImpl)api.getLayoutManager();
            if(layout==null) throw new AssertionError("Layout disabled");
            var config=layout.getConfiguration();
            if(!config.getDirection().name().equals("COLUMNS") || !config.isRemainingPlayersTextEnabled()) throw new AssertionError("Wrong layout direction/overflow");
            var definition=config.getLayouts().get("quest-and-players");
            if(definition==null || definition.getSlotCount()!=60 || definition.getFixedSlots().size()!=22) throw new AssertionError("Wrong fixed-slot layout");
            var slots=definition.getGroups().get("players").getSlots();
            if(slots.length!=38 || slots[0]!=22 || slots[18]!=40 || slots[19]!=42 || slots[37]!=60) throw new AssertionError("Wrong player slots: "+Arrays.toString(slots));
            if(!MiniMessageHook.isAvailable()) throw new AssertionError("MiniMessage unavailable");
            UUID a=UUID.randomUUID(), b=UUID.randomUUID();
            var cache=(ConcurrentHashMap<UUID,List<String>>)field(bridge,"cache");
            var projection=new TabQuestProjection((TextRenderer)field(runtime,"text"),28);
            cache.put(a,projection.state("no-island")); cache.put(b,projection.state("done"));
            try {
                for(int i=1;i<=18;i++) {
                    var placeholder=api.getPlaceholderManager().getPlaceholder("%quest_line_"+i+"%");
                    if(!(placeholder instanceof PlayerPlaceholder)) throw new AssertionError("Native player placeholder missing: "+i);
                    var supplier=(Function<TabPlayer,String>)field(placeholder,"function");
                    String value=supplier.apply(player(a));
                    if(!value.equals(cache.get(a).get(i-1))) throw new AssertionError("Per-player cache mismatch");
                    if(!value.isEmpty() && MiniMessageHook.parseText(value)==null) throw new AssertionError("TAB failed to parse Quest text");
                    if(!supplier.apply(player(b)).equals(cache.get(b).get(i-1))) throw new AssertionError("Cross-player cache leak");
                }
                getLogger().info("QUEST_TAB_"+phase+"_PASSED: native 60 slots, 38 player slots across two columns, 18 placeholders, per-player suppliers, MiniMessage, overflow, no scoreboard/BossBar");
            } finally { cache.remove(a); cache.remove(b); }
        } catch(Throwable error) { getLogger().log(java.util.logging.Level.SEVERE,"QUEST_TAB_FAILED "+phase,error); }
    }
    private TabPlayer player(UUID uuid) {
        return (TabPlayer)Proxy.newProxyInstance(TabPlayer.class.getClassLoader(),new Class<?>[]{TabPlayer.class},(proxy,method,args) -> {
            if(method.getName().equals("getUniqueId")) return uuid;
            throw new AssertionError("Placeholder supplier touched live API: "+method.getName());
        });
    }
}
