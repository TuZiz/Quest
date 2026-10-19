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
                if(getServer().getPluginManager().isPluginEnabled("Xerr")) checkXerr();
                getLogger().info("QUEST_SMOKE_PASSED: API readiness, 5 CE identities, real inventory capacity planner");
            } catch(Throwable error) {getLogger().log(java.util.logging.Level.SEVERE,"QUEST_SMOKE_FAILED",error);}
        },100L);
    }
    private void checkText(Component text) {
        if(text.decoration(TextDecoration.ITALIC)!=TextDecoration.State.FALSE) throw new AssertionError("Italic item text");
        for(Component child:text.children()) checkText(child);
    }
    private void checkXerr() {
        var api=com.hiusers.xerr.api.XerrAPI.get();
        if(!api.layouts().exists("quest_tracker")) throw new AssertionError("Quest tracker layout missing");
        for(String event:List.of("chapter-start","chapter-complete","task-start","task-complete","next-task"))
            if(!api.popups().ids().contains("quest_"+event)) throw new AssertionError("Popup ID missing: "+api.popups().ids());
        var id=UUID.randomUUID();
        var player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(proxy,method,args)->{
            return switch(method.getName()) {
                case "getUniqueId" -> id;
                case "getName" -> "HudProbe";
                case "getWorld" -> getServer().getWorlds().getFirst();
                case "getLocale" -> "zh_cn";
                case "locale" -> java.util.Locale.SIMPLIFIED_CHINESE;
                default -> method.getReturnType()==boolean.class ? true : null;
            };
        });
        var vars=Map.of("chapter","章节探针","task","生产探针","objective","原石","current","7","target","32","progress","7 / 32","completed","2","total","3","pending","2");
        String raw=api.layouts().renderString(player,"quest_tracker",vars,false,"quest:smoke");
        if(raw==null || !raw.contains("章节探针") || !raw.contains("7")) throw new AssertionError("HUD variable render failed: "+raw);
        getLogger().info("XERR_API_PASSED: five popup IDs, tracker and real Xerr font/image/variable render; proxy player, no client packets");
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
        for(String mode:List.of("chapters","chapter","task")) {
            var session=new MenuSession(UUID.randomUUID(),UUID.randomUUID(),mode.equals("task")?"sky_start":null,0,
                mode.equals("chapters")?null:chapterId,true);
            var layout=renderer.layout(settings,session);
            session.setMenu(getServer().createInventory(session,layout.getStringList("Shape").size()*9,Component.empty()));
            renderer.render(player,session,journal,settings,true);
            var actions=session.getActions().values();
            if(mode.equals("chapters") && !actions.contains("chapter:"+chapterId)) throw new AssertionError("No chapter link");
            if(mode.equals("chapter") && (!actions.contains("chapter-claim") || actions.stream().filter(a->a.startsWith("quest:")).count()!=3 || !actions.contains("chapter-preview"))) throw new AssertionError("Chapter layout incomplete");
            if(mode.equals("task") && actions.stream().filter(a->a.equals("preview")).count()!=4) throw new AssertionError("Original task reward previews lost");
            for(var item:session.getMenu().getContents()) if(item!=null) {
                var meta=item.getItemMeta();
                if(meta.hasDisplayName()) checkText(meta.displayName());
                if(meta.hasLore()) for(var line:meta.lore()) checkText(line);
            }
        }
        var notices=new StoryNotices(text,settings.getNotices());
        calls.clear();
        notices.send(player,new ProgressChange("A",chapterId,true,false,true),settings.getQuests(),settings.getChapters());
        if(!calls.contains("showTitle") || !calls.contains("playSound") || !calls.contains("sendMessage")) throw new AssertionError("Completion channels missing: "+calls);
        var disabled=new HashMap<String,NoticeProfile>();
        settings.getNotices().forEach((event,p)->disabled.put(event,new NoticeProfile(false,false,false,null,0,1)));
        notices.setProfiles(disabled);calls.clear();
        notices.send(player,new ProgressChange("A",chapterId,true,false,true),settings.getQuests(),settings.getChapters());
        if(!calls.isEmpty()) throw new AssertionError("Disabled channels invoked: "+calls);
        getLogger().info("CHAPTER_GUI_PASSED: chapter list/detail/task rewards, actual ItemStack non-italic rendering, title/sound/chat dispatch and all-off switches; player is a proxy, no client click claim");
    }

}
