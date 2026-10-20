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
                checkChapterMenus(runtime,items);
                checkCommands();
                getLogger().info("QUEST_SMOKE_PASSED: API readiness, 5 CE identities, real inventory capacity planner");
            } catch(Throwable error) {getLogger().log(java.util.logging.Level.SEVERE,"QUEST_SMOKE_FAILED",error);}
        },100L);
    }
    private void checkText(Component text) {
        if(text.decoration(TextDecoration.ITALIC)!=TextDecoration.State.FALSE) throw new AssertionError("Italic item text");
        for(Component child:text.children()) checkText(child);
        if(text instanceof net.kyori.adventure.text.TranslatableComponent translated)
            for(var argument:translated.arguments()) if(argument.value() instanceof Component child) checkText(child);
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
    private void checkChapterMenus(Object runtime,ItemFactory original) throws Exception {
        var menus=(QuestMenus)field(runtime,"menus");
        var settings=menus.getSettings();
        if(settings.getChapters().size()!=1 || settings.getQuests().size()!=3) throw new AssertionError("Wrong defaults");
        var chapter=settings.getChapters().getFirst();
        var text=new TextRenderer(settings.getLanguage());
        // Render actual server ItemStacks without requiring a connected player's CE context.
        var items=new ItemFactory(List.of((id,player)->original.build(id,null)));
        var renderer=new JournalMenuRenderer(items,text);
        var calls=new ArrayList<String>();
        var player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(proxy,method,args)->{
            calls.add(method.getName());
            if(method.getReturnType()==boolean.class) return true;
            if(method.getReturnType()==int.class) return 0;
            if(method.getReturnType()==long.class) return 0L;
            if(method.getReturnType()==double.class) return 0D;
            if(method.getReturnType()==float.class) return 0F;
            return null;
        });
        var questViews=settings.getQuests().stream().map(q->new QuestView(q,QuestStatus.COMPLETE,
            q.getObjectives().stream().map(o->new ObjectiveProgress(o,o.getTarget())).toList())).toList();
        var journal=new JournalView(questViews,List.of(new ChapterView(chapter,ChapterStatus.COMPLETE,3,false)));
        String chapterId=chapter.getId();
        for(String mode:List.of("chapters","chapter")) {
            var session=new MenuSession(UUID.randomUUID(),UUID.randomUUID(),0,mode.equals("chapters")?null:chapterId);
            var layout=renderer.layout(settings,session);
            session.setMenu(getServer().createInventory(session,layout.getStringList("Shape").size()*9,Component.empty()));
            renderer.render(player,session,journal,settings,true);
            var actions=session.getActions().values();
            if(mode.equals("chapters") && !actions.contains("chapter:"+chapterId)) throw new AssertionError("No chapter link");
            if(mode.equals("chapter")) {
                if(!actions.contains("chapter-claim") || actions.stream().filter(a->a.equals("quest")).count()!=3)
                    throw new AssertionError("Chapter task layout incomplete");
                var taskSlots=session.getActions().entrySet().stream().filter(e->e.getValue().equals("quest")).map(Map.Entry::getKey).toList();
                for(int index=0;index<taskSlots.size();index++) {
                    String taskId=chapter.getQuests().get(index);
                    var quest=questViews.stream().filter(q->q.getDefinition().getId().equals(taskId)).findFirst().orElseThrow();
                    var lines=session.getMenu().getItem(taskSlots.get(index)).getItemMeta().lore();
                    String plain=lines.stream().map(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()::serialize).collect(java.util.stream.Collectors.joining("\n"));
                    for(var objective:quest.getProgress()) {
                        String description=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(text.render(objective.getObjective().getDescription(),Map.of()));
                        if(!plain.contains(description) || !plain.contains(Long.toString(objective.getObjective().getTarget()))) throw new AssertionError("Task objective missing from lore: "+plain);
                    }
                    for(var reward:quest.getDefinition().getRewards()) {
                        String name=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(items.build(reward.getItem(),null).effectiveName());
                        if(!plain.contains(name) || !plain.contains("×"+reward.getAmount())) throw new AssertionError("Reward name/count missing from lore: "+plain);
                    }
                    if(plain.contains("<rewards>") || plain.contains("<objectives>") || plain.contains("点击查看条件")) throw new AssertionError("Unexpanded task lore: "+plain);
                }
                if(actions.contains("mailbox") || actions.contains("chapter-preview") || actions.stream().anyMatch(a->a.startsWith("quest:")))
                    throw new AssertionError("Retired button/detail route restored");
            }
            for(var item:session.getMenu().getContents()) if(item!=null) {
                var meta=item.getItemMeta();
                if(meta.hasDisplayName()) checkText(meta.displayName());
                if(meta.hasLore()) for(var line:meta.lore()) checkText(line);
            }
        }
        var notices=new StoryNotices(text,settings.getNotices());
        calls.clear();
        notices.send(player,new ProgressChange("A",chapterId,true,false,true),settings.getQuests(),settings.getChapters());
        if(!calls.contains("sendMessage") || calls.contains("showTitle") || calls.contains("playSound") || calls.contains("sendActionBar")) throw new AssertionError("Completion must use chat only: "+calls);
        var disabled=new HashMap<String,NoticeProfile>();
        settings.getNotices().forEach((event,p)->disabled.put(event,new NoticeProfile(false,false,false,null,0,1)));
        notices.setProfiles(disabled);calls.clear();
        notices.send(player,new ProgressChange("A",chapterId,true,false,true),settings.getQuests(),settings.getChapters());
        if(!calls.isEmpty()) throw new AssertionError("Disabled channels invoked: "+calls);
        getLogger().info("CHAPTER_GUI_PASSED: two screens, inline task objectives and actual CE/vanilla reward names/counts, no task detail/mailbox/preview buttons, ItemStack non-italic rendering; proxy player, no client click claim");
    }

}
