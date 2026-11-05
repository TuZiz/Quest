package ym.quest.smoke;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ym.quest.integration.*;
import ym.quest.introduction.IntroductionRuntime;
import java.lang.reflect.*;
import java.util.*;

/** Isolated Leaf probe: actual runtime/store and player command boundary, no live player or island creation. */
public final class IntroductionMenuSmoke extends JavaPlugin {
    private IntroductionRuntime intro;
    private Player player;
    private final UUID id=UUID.randomUUID();
    private int opens;
    private boolean allowed=true;
    private Integrations integrations;
    private IslandGateway original;
    private Field gateway;
    private Object field(Object object,String name) throws Exception {
        var field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);
    }
    private void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
    private void guard(Runnable action) { try { action.run(); } catch(Throwable e) { restore();getLogger().log(java.util.logging.Level.SEVERE,"INTRODUCTION_MENU_FAILED",e); } }
    private void later(Runnable action) { getServer().getScheduler().runTaskLater(this,()->guard(action),25); }
    @Override public void onEnable() { getServer().getScheduler().runTaskLater(this,()->guard(this::begin),140); }
    @Override public void onDisable() { restore(); }
    private void restore() {
        if(gateway!=null) try { gateway.set(integrations,original);gateway=null; } catch(Exception e) { throw new RuntimeException(e); }
    }
    private Map<?,?> states() { try { return (Map<?,?>)field(intro,"states"); } catch(Exception e) { throw new RuntimeException(e); } }
    private boolean done() { try { return (boolean)field(states().get(id),"done"); } catch(Exception e) { throw new RuntimeException(e); } }
    private void begin() {
        try {
            var quest=getServer().getPluginManager().getPlugin("Quest");require(quest!=null && quest.isEnabled(),"Quest disabled");
            var runtime=field(quest,"runtime");intro=(IntroductionRuntime)field(runtime,"introduction");require(intro!=null,"Introduction disabled");
            require(Arrays.stream(intro.getClass().getDeclaredFields()).noneMatch(f->f.getName().equals("dialogue")),"Still initializes dialogue engine");
            player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->switch(m.getName()) {
                case "getUniqueId" -> id;
                case "getName" -> "IntroductionMenuProbe";
                case "getServer" -> getServer();
                case "performCommand" -> { require(a[0].equals("dreamskyblock:home gui"),"Unexpected player command");opens++;yield true; }
                case "isOnline" -> true;
                case "hasPermission" -> allowed && (a[0].equals("quest.use") || a[0].equals("dreamskyblock.use"));
                case "hashCode" -> id.hashCode();
                case "equals" -> p==a[0];
                case "toString" -> "IntroductionMenuProbe";
                default -> { if(m.getReturnType()==boolean.class) yield false; if(m.getReturnType()==int.class) yield 0; yield null; }
            });
            integrations=(Integrations)field(runtime,"integrations");original=integrations.getIslands();
            gateway=Integrations.class.getDeclaredField("islands");gateway.setAccessible(true);
            gateway.set(integrations,new IslandGateway() {
                public boolean ready() { return true; }
                public Island byMember(UUID member) { return null; }
                public Island byId(UUID home) { return null; }
                public List<Island> all() { return List.of(); }
                public boolean canClaim(UUID home,UUID member) { return false; }
            });
            intro.interact(player); // Async personal progress load; no task completion before load.
            later(this::first);
        } catch(Exception e) { throw new RuntimeException(e); }
    }
    private void first() {
        require(!done(),"Fresh player already done");intro.interact(player);
        require(opens==1,"First interaction did not dispatch GUI immediately");
        intro.interact(player);require(opens==1,"Repeated click reopened while saving/cooldown");
        later(this::saved);
    }
    private void saved() {
        require(done(),"Personal completion not saved");
        intro.dialogueComplete(id,UUID.randomUUID());require(done(),"Retired callback changed progress");
        allowed=false;intro.interact(player);require(opens==1,"Permission bypass");allowed=true;
        // Evict the connection cache and re-read SQLite exactly as reconnect does.
        states().remove(id);intro.interact(player);later(this::reconnected);
    }
    private void reconnected() {
        require(done(),"Reconnect lost personal progress");
        intro.interact(player);require(opens==2,"Completed player's interaction did not open GUI");
        require(integrations.island(id)==null,"Opening GUI created membership");
        later(()->{
            intro.command(player,"replay");require(opens==3,"Legacy replay command still plays dialogue");
            restore();getLogger().info("INTRODUCTION_MENU_PASSED: immediate GUI dispatch, duplicate click guard, permission, actual SQLite reconnect, retired callback ignored, replay compatibility; no island membership created");
        });
    }
}
