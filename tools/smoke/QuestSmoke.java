package ym.quest.smoke;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import ym.quest.reward.ItemFactory;
import ym.quest.reward.MailboxDelivery;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import java.util.*;
import java.lang.reflect.Proxy;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import ym.quest.gui.*;
import ym.quest.domain.*;
import ym.quest.config.*;
import ym.quest.text.*;

/** Isolated test plugin, deliberately excluded from the distributable Quest JAR. */
public final class QuestSmoke extends JavaPlugin {
    private Object field(Object target,String name) throws Exception {
        var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);
    }
    @Override public void onEnable() {
        getServer().getScheduler().runTaskLater(this,()->{
            try {
                var quest=getServer().getPluginManager().getPlugin("Quest");
                if(quest==null || !quest.isEnabled()) throw new AssertionError("Quest disabled");
                var runtime=field(quest,"runtime");
                var items=(ItemFactory)field(runtime,"items");
                for(String id:List.of("genlite:fuel","genlite:mechanical_parts","genlite:copper_coil","genlite:cobblestone_generator","genlite:basic_upgrade_unit")) {
                    ItemStack item=items.build(id,null);
                    if(!id.equals(String.valueOf(CraftEngineItems.getCustomItemId(item)))) throw new AssertionError("Wrong identity: "+id);
                    if(item.isEmpty()) throw new AssertionError("Empty custom item "+id);
                    getLogger().info("ITEM_OK "+id);
                }
                var integrations=(ym.quest.integration.Integrations)field(runtime,"integrations");
                if(integrations.getIslands()==null || !integrations.getIslands().ready()) throw new AssertionError("Dream API unready");
                if(integrations.getProduction()==null || !integrations.getProduction().ready()) throw new AssertionError("Genlite API unready");
                ItemStack[] full=new ItemStack[36];Arrays.setAll(full,i->new ItemStack(Material.STONE,64));
                var addition=List.of(items.build("genlite:basic_upgrade_unit",null));
                if(MailboxDelivery.Companion.plan(full,addition)!=null) throw new AssertionError("Overflow accepted");
                if(full[0].getAmount()!=64) throw new AssertionError("Input mutated");
                full[5]=null;
                var plan=MailboxDelivery.Companion.plan(full,addition);
                if(plan==null || plan[5]==null || full[5]!=null) throw new AssertionError("Delivery plan invalid");
                checkCommands();
                getLogger().info("QUEST_SMOKE_PASSED: API readiness, 5 CE identities, real inventory capacity planner");
            } catch(Throwable error) {getLogger().log(java.util.logging.Level.SEVERE,"QUEST_SMOKE_FAILED",error);}
        },100L);
    }
    private void checkCommands() {
        var messages=new ArrayList<String>();var admin=new java.util.concurrent.atomic.AtomicBoolean(false);
        var plain=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        var sender=(org.bukkit.command.CommandSender)Proxy.newProxyInstance(org.bukkit.command.CommandSender.class.getClassLoader(),new Class<?>[]{org.bukkit.command.CommandSender.class},(proxy,method,args)->{
            if(method.getName().equals("hasPermission")) return args[0].equals("quest.use") || admin.get();
            if(method.getName().equals("sendMessage") && args!=null) for(Object arg:args) if(arg instanceof Component component) messages.add(plain.serialize(component));
            if(method.getName().equals("getName")) return "CommandProbe";
            return method.getReturnType()==boolean.class ? false : null;
        });
        var command=getServer().getPluginCommand("quest");
        command.execute(sender,"quest",new String[]{"help"});
        if(messages.stream().noneMatch(s->s.contains("/quest help")) || messages.stream().anyMatch(s->s.contains("/quest reload") || s.contains("/quest hud"))) throw new AssertionError("Player help leaked admin or retired HUD commands");
        messages.clear();command.execute(sender,"quest",new String[]{"reload"});
        if(messages.isEmpty() || messages.stream().anyMatch(s->s.contains("/quest hud"))) throw new AssertionError("Unprivileged reload was not rejected");
        if(command.tabComplete(sender,"quest",new String[]{""}).contains("reload")) throw new AssertionError("Unprivileged reload completion");
        if(command.tabComplete(sender,"quest",new String[]{""}).contains("hud") || !command.tabComplete(sender,"quest",new String[]{"hud",""}).isEmpty()) throw new AssertionError("Retired HUD command completion");
        admin.set(true);messages.clear();command.execute(sender,"quest",new String[]{"help"});
        if(messages.stream().noneMatch(s->s.contains("/quest reload")) || !command.tabComplete(sender,"quest",new String[]{""}).contains("reload")) throw new AssertionError("Admin direct reload help missing");
        getLogger().info("QUEST_COMMANDS_PASSED: player/admin help, direct reload permission and completion");
    }
}
