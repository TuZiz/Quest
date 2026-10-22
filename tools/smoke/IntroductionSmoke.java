package ym.quest.smoke;

import com.typewritermc.core.interaction.InteractionContext;
import com.typewritermc.engine.paper.entry.dialogue.*;
import com.typewritermc.engine.paper.entry.entries.*;
import com.typewritermc.engine.paper.interaction.TriggerContinuation;
import kotlin.Unit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ym.quest.platform.GameThread;
import ym.quest.text.TextRenderer;
import java.lang.reflect.Proxy;
import java.util.*;

/** API and lifecycle probe only; does not claim real client confirmation/packet rendering. */
public final class IntroductionSmoke extends JavaPlugin {
    private final Map<UUID,List<net.kyori.adventure.text.Component>> actionBars=new HashMap<>();
    private Object field(Object target,String key) throws Exception {
        var f=target.getClass().getDeclaredField(key); f.setAccessible(true); return f.get(target);
    }
    @Override public void onEnable() {
        getServer().getScheduler().runTaskLater(this,()->check("INITIAL"),160);
        getCommand("introsmoke").setExecutor((sender,command,label,args)->{ check("RELOADED");return true; });
    }
    private void check(String phase) {
        try {
            var quest=(JavaPlugin)getServer().getPluginManager().getPlugin("Quest");
            var runtime=field(quest,"runtime");
            var intro=field(runtime,"introduction");
            if(intro==null || field(intro,"ady")==null || field(intro,"dialogue")==null) throw new AssertionError("Optional integration failed to connect");
            var loader=quest.getClass().getClassLoader();
            var entryClass=Class.forName("ym.quest.introduction.TypewriterIntroduction$IntroductionEntry",true,loader);
            var ctor=Arrays.stream(entryClass.getDeclaredConstructors()).filter(c->c.getParameterCount()==5).findFirst().orElseThrow(); ctor.setAccessible(true);
            var text=(TextRenderer)field(runtime,"text");
            var thread=new GameThread(quest);
            var outcomes=new ArrayList<Boolean>();
            Class<?> functionType=Class.forName("kotlin.jvm.functions.Function1",true,loader);
            Object callback=Proxy.newProxyInstance(loader,new Class<?>[]{functionType},(p,m,a)->{
                if(m.getName().equals("invoke")) { outcomes.add((Boolean)a[0]); return Unit.INSTANCE; }
                if(m.getName().equals("toString")) return "IntroductionCompletionProbe";
                return null;
            });
            var context=new InteractionContext(Collections.emptyMap());
            var player=player(UUID.randomUUID());
            var entry=(DialogueEntry)ctor.newInstance(player.getUniqueId(),List.of("<#E0F7FA>验证"),thread,text,callback);
            var trigger=new EntryTrigger(entry);
            if(trigger.getRef().get()!=entry || !trigger.canTriggerFor(player,context)) throw new AssertionError("Runtime entry reference not resolved");
            var decision=new DialogueHandler().trigger(new Event(player,context,trigger),null,null);
            if(!(decision instanceof TriggerContinuation.StartInteraction start) || !(start.getInteraction() instanceof DialogueInteraction)) throw new AssertionError("Typewriter did not accept dialogue entry: "+decision);
            var normal=entry.messenger(player,context);
            normal.init();
            // Proxy has no network channel: suppress only Typewriter's packet-based chat replay.
            // Real client rendering/key input is a separate acceptance check.
            var history=com.typewritermc.engine.paper.interaction.ChatHistoryHandlerKt.getChatHistory(player);
            history.stopBlocking(); history.startBlocking();
            normal.completeOrFinish(); normal.end(); normal.end();
            normal.dispose();
            if(!outcomes.equals(List.of(true))) throw new AssertionError("Completion not exactly once: "+outcomes);
            outcomes.clear();
            var cancelled=(DialogueEntry)ctor.newInstance(player.getUniqueId(),List.of(),thread,text,callback);
            cancelled.messenger(player,context).end();
            if(!outcomes.equals(List.of(false))) throw new AssertionError("Interrupted RUNNING dialogue counted as completed");
            outcomes.clear();
            var late=(DialogueEntry)ctor.newInstance(player.getUniqueId(),List.of(),thread,text,callback);
            ((java.util.concurrent.atomic.AtomicBoolean)field(late,"active")).set(false);
            var messenger=late.messenger(player,context); messenger.init();
            if(messenger.getState()!=MessengerState.CANCELLED) throw new AssertionError("Late cancelled entry initialized");
            messenger.end(); messenger.dispose();
            history.stopBlocking();
            if(!outcomes.isEmpty()) throw new AssertionError("Late callback survived cancellation");
            var load=intro.getClass().getDeclaredMethod("load",Player.class); load.setAccessible(true); load.invoke(intro,player);
            getServer().getScheduler().runTaskLater(this,()->checkPersonal(phase,intro,player),30);
        } catch(Throwable error) { getLogger().log(java.util.logging.Level.SEVERE,"QUEST_INTRO_FAILED "+phase,error); }
    }
    private void checkPersonal(String phase,Object intro,Player player) {
        try {
            var lines=(List<?>)intro.getClass().getMethod("tabLines",Player.class).invoke(intro,player);
            if(lines==null || !lines.toString().contains("交谈") || lines.toString().contains("创建或加入空岛后"))
                throw new AssertionError("No-island player did not see personal NPC objective: "+lines);
            var prompts=field(intro,"actionBar");
            var shown=(Map<?,?>)field(prompts,"shown");
            if(!shown.containsKey(player.getUniqueId())) throw new AssertionError("Pending player ActionBar missing");
            var sent=actionBars.getOrDefault(player.getUniqueId(),List.of());
            var plain=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
            if(sent.stream().map(plain::serialize).noneMatch(line->line.contains("右键") && line.contains("交谈") && line.contains("右侧任务栏")))
                throw new AssertionError("ActionBar instruction not sent to player: "+sent);
            var states=(Map<?,?>)field(intro,"states"); var state=states.get(player.getUniqueId());
            var session=state.getClass().getDeclaredField("session"); session.setAccessible(true); session.set(state,new Object());
            var deadline=state.getClass().getDeclaredField("deadline"); deadline.setAccessible(true); deadline.set(state,System.currentTimeMillis()+60000L);
            getServer().getScheduler().runTaskLater(this,()->{
                try {
                    if(shown.containsKey(player.getUniqueId())) throw new AssertionError("ActionBar not paused in dialogue");
                    intro.getClass().getMethod("command",Player.class,String.class).invoke(intro,player,"skip");
                    getServer().getScheduler().runTaskLater(this,()->{
                        try {
                            if(intro.getClass().getMethod("tabLines",Player.class).invoke(intro,player)!=null) throw new AssertionError("Completed personal task still overrides shared task");
                            if(shown.containsKey(player.getUniqueId())) throw new AssertionError("Completed ActionBar still shown");
                            getLogger().info("QUEST_INTRO_"+phase+"_PASSED: Typewriter handler, messenger init/confirmation/dispose, exactly-once completion, no-island NPC objective, ActionBar show/pause/stop");
                        } catch(Throwable error) { getLogger().log(java.util.logging.Level.SEVERE,"QUEST_INTRO_FAILED "+phase,error); }
                    },30);
                } catch(Throwable error) { getLogger().log(java.util.logging.Level.SEVERE,"QUEST_INTRO_FAILED "+phase,error); }
            },30);
        } catch(Throwable error) { getLogger().log(java.util.logging.Level.SEVERE,"QUEST_INTRO_FAILED "+phase,error); }
    }
    private Player player(UUID id) {
        return (Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->switch(m.getName()) {
            case "getUniqueId" -> id;
            case "getName" -> "IntroductionProbe";
            case "isOnline" -> true;
            case "hasPermission" -> true;
            case "sendActionBar" -> { actionBars.computeIfAbsent(id,k->new ArrayList<>()).add((net.kyori.adventure.text.Component)a[0]); yield null; }
            case "hashCode" -> id.hashCode();
            case "equals" -> p==a[0];
            case "toString" -> "IntroductionProbe";
            default -> { if(m.getReturnType()==boolean.class) yield false; if(m.getReturnType()==int.class) yield 0; yield null; }
        });
    }
}
