package ym.quest.smoke;

import com.typewritermc.core.interaction.InteractionContext;
import com.typewritermc.engine.paper.entry.TriggerableEntry;
import com.typewritermc.engine.paper.entry.entries.DialogueEntry;
import com.typewritermc.engine.paper.entry.entries.Var;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.Proxy;
import java.util.*;

/** Real Typewriter Library/BasicExtension parse and native command-variable probe, isolated server only. */
public final class TypewriterNativeSmoke extends JavaPlugin {
    @Override public void onEnable() {
        getServer().getScheduler().runTaskLater(this,this::check,140);
    }
    private void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
    private void check() {
        try {
            var quest=getServer().getPluginManager().getPlugin("Quest");
            require(quest!=null && quest.isEnabled(),"Quest not enabled");
            var loader=quest.getClass().getClassLoader();
            var bridge=Class.forName("ym.quest.introduction.TypewriterKotlinBoundary",true,loader);
            var instance=bridge.getField("INSTANCE").get(null);
            var resolve=bridge.getMethod("entry",String.class);
            var first=(DialogueEntry)resolve.invoke(instance,"quest_guide_01");
            require(first!=null,"Published first entry missing");
            require(first.getClass().getName().equals("com.typewritermc.basic.entries.dialogue.SpokenDialogueEntry"),"Not official spoken entry");
            var playerId=UUID.randomUUID();
            boolean[] denied={false};
            Player player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)-> switch(m.getName()) {
                case "getUniqueId" -> playerId;
                case "getName" -> "NativeTypewriterProbe";
                case "isOnline", "hasPermission", "isOp" -> true;
                case "sendMessage" -> { if(a!=null && Arrays.toString(a).contains("权限")) denied[0]=true; yield null; }
                case "hashCode" -> playerId.hashCode();
                case "equals" -> p==a[0];
                case "toString" -> "NativeTypewriterProbe";
                default -> { if(m.getReturnType()==boolean.class) yield false; if(m.getReturnType()==int.class) yield 0; yield null; }
            });
            String command="quest:quest _typewriter-complete "+playerId+" "+UUID.randomUUID();
            var context=(InteractionContext)bridge.getMethod("completionContext",String.class).invoke(instance,command);
            var messenger=first.messenger(player,context);
            require(messenger.getClass().getName().equals("com.typewritermc.basic.entries.dialogue.messengers.spoken.JavaSpokenDialogueDialogueMessenger"),"Not official Java messenger");
            Set<String> visited=new LinkedHashSet<>();
            TriggerableEntry cursor=first;
            while(cursor instanceof DialogueEntry && visited.size()<32) {
                require(cursor instanceof DialogueEntry,"Spoken chain broken");
                require(visited.add(cursor.getId()),"Dialogue cycle");
                cursor=cursor.getTriggers().getFirst().get();
            }
            require(cursor.getId().equals("quest_guide_complete"),"Wrong terminal entry");
            require(cursor.getClass().getName().equals("com.typewritermc.basic.entries.action.ConsoleCommandActionEntry"),"Not official console action");
            var commandField=cursor.getClass().getDeclaredField("command");
            commandField.setAccessible(true);
            Var<?> value=(Var<?>)commandField.get(cursor);
            require(command.equals(value.get(player,context)),"Native context variable lost per-session completion token");
            // Even an OP player cannot impersonate the terminal console callback.
            var questCommand=((JavaPlugin)quest).getCommand("quest");
            questCommand.getExecutor().onCommand(player,questCommand,"quest",
                new String[]{"_typewriter-complete",playerId.toString(),UUID.randomUUID().toString()});
            require(denied[0],"OP player was not denied the internal completion endpoint");
            getLogger().info("TYPEWRITER_NATIVE_PASSED: published pages, "+visited.size()+" official spoken entries, official speaker/messenger, native context variable resolves guarded command, player callback denied");
        } catch(Throwable error) { getLogger().log(java.util.logging.Level.SEVERE,"TYPEWRITER_NATIVE_FAILED",error); }
    }
}
