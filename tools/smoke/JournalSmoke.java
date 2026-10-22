package ym.quest.smoke;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.event.inventory.*;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import ym.quest.gui.journal.*;
import ym.quest.preferences.*;
import ym.quest.reward.ItemFactory;
import ym.quest.domain.*;
import ym.quest.integration.*;
import ym.quest.text.TextRenderer;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Native Paper inventory/click probe; only isolated fixture rows, no live player or reward data. */
public final class JournalSmoke extends JavaPlugin {
    private final UUID id=UUID.randomUUID(),home=UUID.randomUUID();
    private final AtomicReference<InventoryView> open=new AtomicReference<>();
    private final PlainTextComponentSerializer plain=PlainTextComponentSerializer.plainText();
    private Player player;private TaskJournalMenus menus;private PlayerDisplayPreferences preferences;
    private Integrations integrations;private IslandGateway original;private Field gateway;private int itemsChecked;
    private String tracked;private int opens;
    private Object field(Object target,String name) throws Exception { var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target); }
    private void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
    private void guard(Runnable action) { try { action.run(); } catch(Throwable e) { restore();getLogger().log(java.util.logging.Level.SEVERE,"QUEST_JOURNAL_FAILED",e); } }
    private void later(Runnable action) { getServer().getScheduler().runTaskLater(this,()->guard(action),15); }
    @Override public void onEnable() { getServer().getScheduler().runTaskLater(this,()->guard(this::begin),140); }
    @Override public void onDisable() { restore(); }
    private void restore() { if(gateway!=null) { try { gateway.set(integrations,original);gateway=null; } catch(Exception e) { throw new RuntimeException(e); } } }
    private InventoryView view(Inventory top) {
        return (InventoryView)Proxy.newProxyInstance(InventoryView.class.getClassLoader(),new Class<?>[]{InventoryView.class},(p,m,a)->switch(m.getName()) {
            case "getTopInventory" -> top;
            case "getPlayer" -> player;
            case "getType" -> InventoryType.CHEST;
            case "convertSlot" -> a[0];
            case "hashCode" -> System.identityHashCode(p);
            case "equals" -> p==a[0];
            default -> m.getReturnType()==boolean.class ? false : null;
        });
    }
    private void begin() {
        try {
            var quest=getServer().getPluginManager().getPlugin("Quest");require(quest!=null && quest.isEnabled(),"Quest disabled");
            var runtime=field(quest,"runtime");menus=(TaskJournalMenus)field(runtime,"journal");preferences=(PlayerDisplayPreferences)field(runtime,"preferences");
            var names=new HashSet<String>();try(var paths=Files.list(quest.getDataFolder().toPath().resolve("gui"))) { paths.forEach(p->names.add(p.getFileName().toString())); }
            require(names.equals(Set.of("chapters.yml","tasks.yml","settings.yml")),"More than three live GUI files: "+names);
            require(JournalPage.values().length==3 && menus.getSettings().getJournalMenus().keySet().equals(Set.of("chapters","tasks","settings")),"Retired screen registered");
            player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->switch(m.getName()) {
                case "getUniqueId" -> id;
                case "getName" -> "ThreeMenuProbe";
                case "isOnline" -> true;
                case "hasPermission" -> a[0].equals("quest.use");
                case "getOpenInventory" -> open.get();
                case "openInventory" -> { opens++;open.set(view((Inventory)a[0]));yield open.get(); }
                case "closeInventory" -> { open.set(view(getServer().createInventory(null,9)));yield null; }
                case "hashCode" -> id.hashCode();
                case "equals" -> p==a[0];
                default -> m.getReturnType()==boolean.class ? false : null;
            });
            open.set(view(getServer().createInventory(null,9)));
            var refresh=TaskJournalMenus.class.getDeclaredMethod("refresh",Player.class,JournalSession.class);refresh.setAccessible(true);
            getServer().getScheduler().runTaskTimer(this,()->guard(()->{
                if(open.get().getTopInventory().getHolder() instanceof JournalSession s) try { refresh.invoke(menus,player,s); } catch(Exception e) { throw new RuntimeException(e); }
            }),1,5);
            render(runtime);
            integrations=(Integrations)field(runtime,"integrations");original=integrations.getIslands();gateway=Integrations.class.getDeclaredField("islands");gateway.setAccessible(true);
            var island=new Island(home,Set.of(id),0,0);
            gateway.set(integrations,new IslandGateway() {
                public boolean ready() { return true; }
                public Island byMember(UUID member) { return member.equals(id)?island:original.byMember(member); }
                public Island byId(UUID value) { return value.equals(home)?island:original.byId(value); }
                public List<Island> all() { return original.all(); }
                public boolean canClaim(UUID value,UUID member) { return false; }
            });
            var service=(QuestService)field(runtime,"service");
            // An isolated, uncompleted first objective provides an ACTIVE task without granting rewards.
            service.sync(home.toString(),Map.of("island.member",0L)).whenComplete((changes,error)->getServer().getScheduler().runTask(this,()->guard(()->{
                if(error!=null) throw new RuntimeException(error);
                routes();
            })));
        } catch(Exception e) { throw new RuntimeException(e); }
    }
    private JournalSession session() { return (JournalSession)open.get().getTopInventory().getHolder(); }
    private void click(String action) {
        int slot=session().getActions().entrySet().stream().filter(e->e.getValue().equals(action)).map(Map.Entry::getKey).findFirst().orElseThrow();
        var event=new InventoryClickEvent(open.get(),InventoryType.SlotType.CONTAINER,slot,ClickType.LEFT,InventoryAction.PICKUP_ALL);
        menus.click(event);require(event.isCancelled(),"Inventory movement not cancelled");
    }
    private void routes() {
        getServer().getPluginCommand("quest").execute(player,"quest",new String[0]);
        later(()->{
            require(session().getScreen()==JournalPage.CHAPTERS,"Entry must be chapters");
            require(preferences.get(player).getMode()==HudMode.OFF && !preferences.get(player).getAutoTracking(),"New player tracking must default OFF");
            cycleCategories(0);
        });
    }
    private void cycleCategories(int index) {
        if(index==3) { trackRoute();return; }
        var same=session();var inventory=same.getMenu();int opened=opens;
        require(same.getActions().values().stream().filter(a->a.startsWith("category-")).toList().equals(List.of("category-cycle")),"Expected exactly one category button");
        if(index==0) same.setPage(6);
        click("category-cycle");later(()->{
            require(session()==same && session().getMenu()==inventory && opens==opened,"Category reopened the menu");
            require(same.getCategory()==QuestCategory.values()[(index+1)%3] && same.getPage()==0,"Category cycle/page reset");
            cycleCategories(index+1);
        });
    }
    private void trackRoute() {
            var chaptersInventory=session().getMenu();
            click("chapter:"+menus.getSettings().getChapters().getFirst().getId());later(()->{
                require(session().getMenu()!=chaptersInventory,"Previous independent task screen not restored");
                require(session().getScreen()==JournalPage.TASKS,"Chapter task route");
                String action=session().getActions().values().stream().filter(a->a.startsWith("track:")).findFirst().orElseThrow();
                tracked=action.substring(6);
                click(action);later(()->{
                    require(session().getScreen()==JournalPage.TASKS,"Click opened a detail screen");
                    require(preferences.get(player).getTrackedQuest().equals(tracked),"Task click did not save tracking");
                    require(preferences.get(player).getMode()==HudMode.ALWAYS && preferences.get(player).getAutoTracking(),"Tracking must also enable HUD and automatic tracking");
                    var tasksInventory=session().getMenu();click("back");later(()->{
                        require(session().getMenu()!=tasksInventory,"Previous independent chapter screen not restored");
                        require(session().getScreen()==JournalPage.CHAPTERS,"Task back route");
                        click("settings");later(()->{ require(session().getScreen()==JournalPage.SETTINGS,"Settings route");require(!session().getActions().containsValue("toggle-auto"),"Duplicate tracking button");toggle(0); });
                    });
                });
            });
    }
    private void toggle(int index) {
        var actions=List.of("toggle-hud","toggle-notices","toggle-sound");
        if(index<actions.size()) { click(actions.get(index));later(()->toggle(index+1));return; }
        var pref=preferences.get(player);require(pref.getMode()==HudMode.OFF && !pref.getAutoTracking() && !pref.getNotifications() && !pref.getSound(),"Setting toggle persistence");
        require(Objects.equals(pref.getTrackedQuest(),tracked),"Closing HUD cleared tracked task");
        require(opens==4,"Previous chapter/task/back/settings navigation should open four configured screens: "+opens);
        menus.closeMenus();try { require(((Map<?,?>)field(menus,"sessions")).isEmpty(),"Session leak"); } catch(Exception e) { throw new RuntimeException(e); }
        player.closeInventory();restore();
        getDataFolder().mkdirs();try { Files.writeString(getDataFolder().toPath().resolve("report.txt"),"Exactly3files/3screens; nativeItems="+itemsChecked+"; configured chapter slots+glass;13chapterpaging; full chapter/task lore; onlyACTIVEtracks; nativeclicks enable OFF HUD while tracking; closing HUD preserves selection;3settings; category cycle same inventory; unchanged render zero writes; diff slots only; default tracking OFF; realclient pending.\n"); } catch(Exception e) { throw new RuntimeException(e); }
        getLogger().info("QUEST_JOURNAL_PASSED: exactly3files/screens; nativeItems="+itemsChecked+";13chapterpaging; taskclick tracks without detail screen; settings/back; client pending");
    }
    private Set<Integer> chapterSlots(ym.quest.config.Settings settings) {
        var y=settings.getJournalMenus().get("chapters");var slots=new HashSet<Integer>();var rows=y.getStringList("Shape");
        for(int row=0;row<rows.size();row++) for(int col=0;col<9;col++) if("journal-chapter".equals(y.getString("components."+rows.get(row).charAt(col)+".action"))) slots.add(row*9+col);
        return slots;
    }
    private void noItalic(Component c) {
        require(c.decoration(TextDecoration.ITALIC)==TextDecoration.State.FALSE,"Italic item text");c.children().forEach(this::noItalic);
        if(c instanceof TranslatableComponent t) t.arguments().forEach(a->{ if(a.value() instanceof Component child) noItalic(child); });
    }
    private void verifyDiff(JournalRenderer renderer,ym.quest.config.Settings settings,JournalView data,DisplayPreferences prefs) {
        var state=new JournalSession(id,null,JournalPage.CHAPTERS,QuestCategory.MAIN,null,0);
        var real=getServer().createInventory(state,54,Component.empty());var writes=new HashSet<Integer>();int[] clears={0};
        var inventory=(Inventory)Proxy.newProxyInstance(Inventory.class.getClassLoader(),new Class<?>[]{Inventory.class},(p,m,a)->{
            if(m.getName().equals("setItem")) writes.add((Integer)a[0]);
            if(m.getName().equals("clear") || m.getName().equals("setContents")) clears[0]++;
            try { return m.invoke(real,a); } catch(InvocationTargetException e) { throw e.getCause(); }
        });
        state.setMenu(inventory);renderer.render(player,state,data,prefs,settings);writes.clear();
        renderer.render(player,state,data,prefs,settings);require(writes.isEmpty() && clears[0]==0,"Unchanged render wrote inventory slots");
        state.setCategory(QuestCategory.SIDE);renderer.render(player,state,data,prefs,settings);
        var allowed=chapterSlots(settings);state.getActions().forEach((slot,action)->{if(action.equals("category-cycle") || action.equals("previous") || action.equals("next")) allowed.add(slot);});
        require(!writes.isEmpty() && allowed.containsAll(writes) && clears[0]==0,"Category rewrote static slots or cleared inventory: "+writes);
        require(state.getActions().values().stream().noneMatch(a->a.startsWith("chapter:")),"Empty category retained stale chapter actions");
    }
    private void render(Object runtime) throws Exception {
        var settings=menus.getSettings();var service=(QuestService)field(runtime,"service");var originalItems=(ItemFactory)field(runtime,"items");
        var items=new ItemFactory(List.of((key,p)->originalItems.build(key,null)));var renderer=new JournalRenderer(items,new TextRenderer(settings.getLanguage()));
        var tasks=new ArrayList<QuestView>();
        for(int n=0;n<service.getDefinitions().size();n++) {
            var q=service.getDefinitions().get(n);tasks.add(new QuestView(q,n==0?QuestStatus.COMPLETE:n==1?QuestStatus.ACTIVE:QuestStatus.LOCKED,q.getObjectives().stream().map(o->new ObjectiveProgress(o,0)).toList()));
        }
        var chapter=service.getChapters().getFirst();var data=new JournalView(tasks,List.of(new ChapterView(chapter,ChapterStatus.ACTIVE,1,false)));
        var prefs=new DisplayPreferences(HudMode.ALWAYS,true,true,true,tasks.get(1).getDefinition().getId());
        for(var page:JournalPage.values()) {
            var s=new JournalSession(id,null,page,QuestCategory.MAIN,page==JournalPage.TASKS?chapter.getId():null,0);
            s.setMenu(getServer().createInventory(s,54,Component.empty()));renderer.render(player,s,data,prefs,settings);
            if(page==JournalPage.CHAPTERS) {
                require(s.getActions().get(10).equals("chapter:"+chapter.getId()),"Chapter column2");
                String lore=s.getMenu().getItem(10).getItemMeta().lore().stream().map(plain::serialize).collect(java.util.stream.Collectors.joining("\n"));
                require(lore.contains("1 / "+chapter.getQuests().size()),"Chapter progress/reward missing");
                for(var r:chapter.getRewards()) require(lore.contains(plain.serialize(items.build(r.getItem(),null).effectiveName())) && lore.contains("×"+r.getAmount()),"Actual chapter reward missing");
            }
            if(page==JournalPage.TASKS) {
                require(s.getActions().values().stream().filter(a->a.startsWith("track:")).toList().equals(List.of("track:"+tasks.get(1).getDefinition().getId())),"Only ACTIVE tasks may be tracked");
                for(int n=0;n<tasks.size();n++) {
                    var q=tasks.get(n);String lore=s.getMenu().getItem(10+n).getItemMeta().lore().stream().map(plain::serialize).collect(java.util.stream.Collectors.joining("\n"));
                    for(var p:q.getProgress()) require(lore.contains(plain.serialize(new TextRenderer(settings.getLanguage()).render(p.getObjective().getDescription(),Map.of()))),"Task objective missing");
                    for(var r:q.getDefinition().getRewards()) require(lore.contains(plain.serialize(items.build(r.getItem(),null).effectiveName())) && lore.contains("×"+r.getAmount()),"Task reward missing");
                }
            }
            for(var item:s.getMenu().getContents()) if(item!=null) {
                itemsChecked++;noItalic(item.getItemMeta().displayName());
                if(item.getItemMeta().lore()!=null) for(var line:item.getItemMeta().lore()) { noItalic(line);require(!plain.serialize(line).contains("\n") && !plain.serialize(line).contains("<objectives>"),"Unexpanded lore"); }
            }
        }
        verifyDiff(renderer,settings,data,prefs);
        var chapters=new ArrayList<ChapterView>();var quests=new ArrayList<QuestView>();
        for(int n=0;n<13;n++) {
            var q=new QuestDefinition("q"+n,"<#89F7FE>任务"+n,List.of(),"minecraft:book",List.of(),List.of(),List.of());quests.add(new QuestView(q,QuestStatus.COMPLETE,List.of()));
            chapters.add(new ChapterView(new ChapterDefinition("c"+n,"<#89F7FE>章节"+n,"minecraft:book",List.of("<#C2E9FB>章节介绍"),n,List.of(),List.of(q.getId()),List.of(),List.of(),Map.of(),"test"),ChapterStatus.COMPLETE,1,false));
        }
        var all=new JournalView(quests,chapters);var s=new JournalSession(id,null,JournalPage.CHAPTERS,QuestCategory.MAIN,null,0);s.setMenu(getServer().createInventory(s,54,Component.empty()));
        renderer.render(player,s,all,prefs,settings);
        require(s.getActions().entrySet().stream().filter(e->e.getValue().startsWith("chapter:")).map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet()).equals(chapterSlots(settings)),"Chapter layout");
        require(s.getActions().containsValue("next") && !s.getActions().containsValue("previous"),"First page boundary");
        s.setPage(1);renderer.render(player,s,all,prefs,settings);require(s.getActions().get(10).equals("chapter:c12") && !s.getActions().containsValue("next") && s.getActions().containsValue("previous"),"Chapter page2");
        for(int slot=0;slot<54;slot++) require(s.getMenu().getItem(slot)!=null,"Empty slot not glass filled");
        s.setCategory(QuestCategory.SPECIAL);renderer.render(player,s,all,prefs,settings);require(s.getPage()==0 && s.getActions().values().stream().noneMatch(a->a.startsWith("chapter:")),"Empty category");
    }
}
