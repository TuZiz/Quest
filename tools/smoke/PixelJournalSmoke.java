package ym.quest.smoke;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import ym.quest.config.Settings;
import ym.quest.domain.*;
import ym.quest.gui.journal.*;
import ym.quest.reward.ItemFactory;
import ym.quest.text.TextRenderer;
import ym.quest.preferences.DisplayPreferences;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Isolated Paper probe only. No real players, quest operations, rewards, or production database. */
public final class PixelJournalSmoke extends JavaPlugin {
    private Object field(Object o,String key) throws Exception { var f=o.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(o); }
    private void require(boolean ok,String why) { if(!ok)throw new AssertionError(why); }
    @Override public void onEnable() { getServer().getScheduler().runTaskLater(this,()->{
        try { verify();getLogger().info("PIXEL_JOURNAL_PASSED");getServer().dispatchCommand(getServer().getConsoleSender(),"ce reload pack"); }
        catch(Throwable e) { getLogger().log(java.util.logging.Level.SEVERE,"PIXEL_JOURNAL_FAILED",e); }
    },800); }
    private void verify() throws Exception {
        var quest=getServer().getPluginManager().getPlugin("Quest");require(quest!=null&&quest.isEnabled(),"Quest disabled");
        var runtime=field(quest,"runtime");require(runtime!=null,"Quest runtime unavailable");
        var menus=(TaskJournalMenus)field(runtime,"journal");Settings settings=menus.getSettings();
        var factory=(ItemFactory)field(runtime,"items");var renderer=new JournalRenderer(factory,new TextRenderer(settings.getLanguage()));
        boolean[] packed={true};var uuid=UUID.randomUUID();
        var player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class[]{Player.class},(p,m,a)->switch(m.getName()) {
            case "hasResourcePack" -> packed[0]; case "getUniqueId" -> uuid;case "getName" -> "PixelJournalProbe";
            case "hasPermission" -> true;case "hashCode" -> uuid.hashCode();case "equals" -> p==a[0];
            default -> { if(m.getReturnType()==boolean.class)yield false;if(m.getReturnType()==int.class)yield 0;yield null; }
        });
        var qs=new ArrayList<QuestView>();int ordinal=0;
        for(var def:settings.getQuests()) {
            var status=QuestStatus.values()[ordinal++%4];
            qs.add(new QuestView(def,status,def.getObjectives().stream().map(o->new ObjectiveProgress(o,o.getTarget()/2)).toList()));
        }
        var chapters=settings.getChapters().stream().map(c->new ChapterView(c,ChapterStatus.ACTIVE,4,false)).toList();
        var view=new JournalView(qs,chapters);var prefs=new DisplayPreferences();
        for(var page:JournalPage.values()) {
            var session=new JournalSession(uuid,null,page,QuestCategory.MAIN,chapters.getFirst().getDefinition().getId(),0);
            var menu=settings.getJournalMenus().get(page.getFile());
            var shape=String.join("",menu.getStringList("Shape"));
            require(shape.length()==45,"Expected selected five-row layout");
            var real=getServer().createInventory(session,shape.length(),Component.empty());var writes=new HashSet<Integer>();
            int[] resets={0};
            var inv=(Inventory)Proxy.newProxyInstance(Inventory.class.getClassLoader(),new Class[]{Inventory.class},(p,m,a)->{
                if(m.getName().equals("setItem"))writes.add((Integer)a[0]);
                if(m.getName().equals("clear")||m.getName().equals("setContents"))resets[0]++;
                try{return m.invoke(real,a);}catch(InvocationTargetException e){throw e.getCause();}
            });
            session.setMenu(inv);renderer.render(player,session,view,prefs,settings,false);
            var layout=JournalCardLayout.Companion.parse(settings.getJournalMenus().get(page.getFile()));
            require(layout.getCapacity()==6,"Page capacity is not six");
            var chapterEntries=ChapterCardPreviews.INSTANCE.entries(menu,view,session);
            var visible=page==JournalPage.CHAPTERS?chapterEntries.size():JournalNavigation.INSTANCE.tasks(view,session).size();
            require(session.getPages()==Math.max(1,(visible+5)/6),"Incorrect six-card pagination");
            int index=0;
            for(var group:layout.getGroups()) {
                if(index++>=visible)break;
                var anchor=inv.getItem(group.get(1));require(anchor!=null,"Missing anchor");
                require(anchor.getType()==org.bukkit.Material.PAPER,"Card carrier must remain a vanilla item");
                var model=anchor.getItemMeta().getItemModel();require(model!=null&&model.getNamespace().equals("quest_journal"),"Anchor lacks configured model");
                for(int slot:group) {
                    var item=inv.getItem(slot);require(item!=null,"Missing card hitbox");
                    require(Objects.equals(item.getItemMeta().displayName(),anchor.getItemMeta().displayName()),"Hitbox lore/name diverged");
                    require(Objects.equals(session.getActions().get(slot),session.getActions().get(group.get(1))),"Whole card does not share one action");
                    require(item.getItemMeta().displayName().decoration(TextDecoration.ITALIC)==TextDecoration.State.FALSE,"Italic item name");
                    if(page==JournalPage.CHAPTERS && chapterEntries.get(index-1).getPreview()!=null) {
                        require(!session.getActions().containsKey(slot),"Preview cover must never open a task/chapter");
                        require(item.getItemMeta().getLore()!=null,"Preview lacks locked information");
                    }
                }
            }
            writes.clear();renderer.render(player,session,view,prefs,settings,false);
            require(writes.isEmpty()&&resets[0]==0,"Unchanged render rewrites inventory");
            var allowed=new HashSet<Integer>();
            for(int slot=0;slot<shape.length();slot++) {
                var component=menu.getConfigurationSection("components."+shape.charAt(slot));
                if(component!=null && (List.of("previous","next").contains(component.getString("action","")) || component.getBoolean("page-visual")))allowed.add(slot);
            }
            layout.getGroups().forEach(allowed::addAll);
            if(session.turnPage(1)) {
                renderer.render(player,session,view,prefs,settings,true);
                require(allowed.containsAll(writes)&&resets[0]==0,"Paging rewrote fixed slots or reset menu");
            }
            if(page==JournalPage.CHAPTERS) {
                require(visible==6,"Expected one real chapter plus five display covers");
                String prefix="cards.chapter-previews.smoke_seventh";
                menu.set(prefix+".order",7);menu.set(prefix+".name","<#B3C0BD>Seventh preview");
                menu.set(prefix+".item","minecraft:book");menu.set(prefix+".visual-model","quest_journal:cards/chapter_preview_06");
                try {
                    renderer.render(player,session,view,prefs,settings,false);
                    require(session.getPages()==2 && session.turnPage(1),"Display covers must participate in paging");
                    writes.clear();renderer.render(player,session,view,prefs,settings,true);
                    require(allowed.containsAll(writes)&&resets[0]==0,"Preview paging reset fixed UI");
                    for(var group:layout.getGroups()) for(int slot:group)
                        require(!session.getActions().containsKey(slot),"Preview/empty page retained an old chapter action");
                    require(session.turnPage(-1),"Cannot return from preview page");
                    renderer.render(player,session,view,prefs,settings,true);
                    require(session.getActions().containsKey(layout.getGroups().getFirst().getFirst()),"Real chapter did not return after paging");
                } finally {menu.set(prefix,null);session.setPage(0);}
            }
            session.setCategory(QuestCategory.SPECIAL);renderer.render(player,session,view,prefs,settings,false);
            if(page==JournalPage.CHAPTERS && "ALL".equals(menu.getString("cards.chapter-preview-category"))) {
                for(var category:QuestCategory.values()) {
                    session.setCategory(category);renderer.render(player,session,view,prefs,settings,false);
                    var entries=ChapterCardPreviews.INSTANCE.entries(menu,view,session);
                    require(entries.size()==6,"Every category must share six chapter covers");
                    require(session.getActions().get(layout.getGroups().getFirst().getFirst()).startsWith("chapter:"),"Real chapter entrance missing");
                    for(var group:layout.getGroups().subList(1,6)) for(int slot:group)
                        require(inv.getItem(slot)!=null && !session.getActions().containsKey(slot),"Future cover missing or actionable in "+category);
                    require(session.getActions().values().stream().noneMatch(a->a.startsWith("track:")),"Chapter list contains task tracking action");
                }
            } else require(session.getActions().values().stream().noneMatch(a->a.startsWith("track:")||a.startsWith("chapter:")),"Empty category retained stale actions");
            packed[0]=false;session.setCategory(QuestCategory.MAIN);session.setPage(0);
            renderer.render(player,session,view,prefs,settings,false);
            require(inv.getItem(layout.getGroups().getFirst().get(1))!=null,"No resource-pack fallback");
            if(page==JournalPage.CHAPTERS) {
                for(var group:layout.getGroups().subList(1,6)) for(int slot:group) {
                    require(inv.getItem(slot).getType()==org.bukkit.Material.BOOK,"Preview fallback icon missing");
                    require(!session.getActions().containsKey(slot),"Unpacked preview retained a click action");
                }
            }
            packed[0]=true;
        }
        getDataFolder().mkdirs();Files.writeString(getDataFolder().toPath().resolve("report.txt"),"PASS: 45 slots; six cards; one real chapter plus five locked preview covers; preview covers have no actions; synthetic seventh cover uses existing page controls, no clear/setContents, back paging restores real action; whole-card lore; real ItemMeta; unchanged render zero writes; empty category; book fallback with no actions. No real client tested.\n");
    }
}
