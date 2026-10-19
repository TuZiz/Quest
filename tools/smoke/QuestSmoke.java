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
                checkHudStartup(runtime);
                if(getServer().getPluginManager().isPluginEnabled("Xerr")) checkXerr();
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
    private void checkHudStartup(Object runtime) throws Exception {
        var hud=(ym.quest.hud.QuestHud)field(runtime,"hud");
        var status=new java.util.concurrent.atomic.AtomicReference<org.bukkit.event.player.PlayerResourcePackStatusEvent.Status>();
        var id=UUID.randomUUID();
        var player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(proxy,method,args)->switch(method.getName()) {
            case "getUniqueId" -> id;
            case "getResourcePackStatus" -> status.get();
            default -> null;
        });
        var ready=hud.getClass().getDeclaredMethod("packReady",Player.class);ready.setAccessible(true);
        if((boolean)ready.invoke(hud,player)) throw new AssertionError("HUD allowed before pack acknowledgement");
        status.set(org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.ACCEPTED);
        if((boolean)ready.invoke(hud,player)) throw new AssertionError("Accepted pack is not loaded yet");
        status.set(org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED);
        if(!(boolean)ready.invoke(hud,player)) throw new AssertionError("Missed startup pack event not recovered");
        hud.reload(((QuestMenus)field(runtime,"menus")).getSettings().getHud());
        if(!(boolean)ready.invoke(hud,player)) throw new AssertionError("HUD reload lost loaded player");
        hud.quit(new org.bukkit.event.player.PlayerQuitEvent(player,Component.empty(),org.bukkit.event.player.PlayerQuitEvent.QuitReason.DISCONNECTED));
        status.set(null);
        if((boolean)ready.invoke(hud,player)) throw new AssertionError("Quit leaked resource pack readiness");
        getLogger().info("HUD_STARTUP_PASSED: current pack success recovers missed event, accepted gated, reload and quit lifecycle; proxy player");
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
        String raw=api.layouts().renderString(player,"quest_tracker",vars,true,"quest:smoke");
        try { java.nio.file.Files.writeString(java.nio.file.Path.of("../verification/xerr-tracker-render.txt"),raw); }
        catch(java.io.IOException error) { throw new IllegalStateException("Cannot export Xerr render evidence",error); }
        if(raw==null || !raw.contains("章节探针") || !raw.contains("7")) throw new AssertionError("HUD variable render failed: "+raw);
        if(!raw.contains("font=xerr_img:quest/panels/gui") || !raw.contains("font=xerr_position:space"))
            throw new AssertionError("HUD missing anchored bitmap font or positioning markers: "+raw);
        try {
            var layouts=api.layouts();
            var method=com.hiusers.xerr.api.layout.XerrLayoutAPI.class.getMethod("render",Player.class,String.class,Map.class,boolean.class,String.class);
            var rendered=method.invoke(layouts,player,"quest_tracker",vars,true,"quest:smoke");
            String json=(String)rendered.getClass().getMethod("toRawMessage").invoke(rendered);
            java.nio.file.Files.writeString(java.nio.file.Path.of("../verification/xerr-tracker-components.json"),json);
            checkHudComponent(json);
            checkFinalHud(player,vars);
            var asyncMethod=com.hiusers.xerr.api.layout.XerrLayoutAPI.class.getMethod("renderAsync",Player.class,String.class,Map.class,String.class,Integer.class);
            var future=(java.util.concurrent.CompletableFuture<?>)asyncMethod.invoke(api.layouts(),player,"quest_tracker",vars,"bossbar:quest_tracker",null);
            future.whenComplete((frame,error)->
                getServer().getScheduler().runTask(this,()->{
                    try {
                        if(error!=null) throw new IllegalStateException("HUD async rendering failed",error);
                        String async=(String)frame.getClass().getMethod("toRawMessage").invoke(frame);checkHudComponent(async);
                        java.nio.file.Files.writeString(java.nio.file.Path.of("../verification/xerr-tracker-async.json"),async);
                        getLogger().info("XERR_ASYNC_PASSED: actual color properties, no literal formatting, positioned font and plain-text alignment on refresh path");
                    } catch(Throwable failure) {getLogger().log(java.util.logging.Level.SEVERE,"QUEST_SMOKE_FAILED",failure);}
                }));
            if(raw.contains("/quest") || raw.contains("待领奖励")) throw new AssertionError("Legacy manual reward HUD");
            var config=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File("plugins/Xerr/core/layout/quest_tracker.yml"));
            if(config.getInt("quest_tracker.panel.x")!=-151 || config.getInt("quest_tracker.panel.y")!=-44) throw new AssertionError("Tracker not right-flush below notice");
            for(String row:List.of("chapter","count","task","objective","progress"))
                if(!"center".equals(config.getString("quest_tracker."+row+".align"))) throw new AssertionError("Tracker row not centered: "+row);
            var popup=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File("plugins/Xerr/core/display/popups/quest.yml"));
            if(popup.getMapList("quest.slots").size()!=1 || !popup.getMapList("quest.slots").getFirst().get("y").equals(0))
                throw new AssertionError("Notifications must queue in one top slot");
        } catch(ReflectiveOperationException | java.io.IOException error) { throw new IllegalStateException("Xerr component verification failed",error); }
        getLogger().info("XERR_API_PASSED: five popup IDs, anchored tracker bitmap/markers and variables; proxy player, no client packets");
    }
    private void checkHudComponent(String json) {
        var tree=com.google.gson.JsonParser.parseString(json);
        var colors=new HashSet<String>();var texts=new ArrayList<String>();
        var pending=new ArrayDeque<com.google.gson.JsonElement>();pending.add(tree);
        while(!pending.isEmpty()) {
            var next=pending.remove();
            if(next.isJsonArray()) next.getAsJsonArray().forEach(pending::add);
            else if(next.isJsonObject()) {
                var object=next.getAsJsonObject();
                if(object.has("color")) colors.add(object.get("color").getAsString().toLowerCase(Locale.ROOT));
                if(object.has("text")) {
                    String value=object.get("text").getAsString();texts.add(value);
                    if(value.contains("(color=") || value.contains("<#")) throw new AssertionError("HUD formatting leaked into visible text: "+value);
                }
                object.entrySet().forEach(e->{if(e.getValue().isJsonObject() || e.getValue().isJsonArray()) pending.add(e.getValue());});
            }
        }
        if(!colors.containsAll(List.of("#ffcc80","#89f7fe","#a8ff78","#ffd700"))) throw new AssertionError("HUD lacks actual RGB style properties: "+colors);
        for(String expected:List.of("章节探针","章节进度 2 / 3","生产探针","原石","7 / 32","完成后奖励自动发放"))
            if(!texts.contains(expected)) throw new AssertionError("HUD visible row differs from plain text: "+expected+" / "+texts);
    }
    private void checkFinalHud(Player player,Map<String,String> vars) throws ReflectiveOperationException,java.io.IOException {
        // Capture exactly what Quest submits to the production Xerr HUD API.
        var renderer=com.hiusers.xerr.api.container.BossbarHudRenderer.INSTANCE;
        var contextsField=renderer.getClass().getDeclaredField("playerBossbar");contextsField.setAccessible(true);
        @SuppressWarnings("unchecked") var contexts=(Map<UUID,Object>)contextsField.get(null);
        var bar=(org.bukkit.boss.BossBar)Proxy.newProxyInstance(org.bukkit.boss.BossBar.class.getClassLoader(),new Class<?>[]{org.bukkit.boss.BossBar.class},(proxy,method,args)->null);
        var components=new HashMap<String,com.hiusers.xerr.api.builder.RefreshableComponent>();
        contexts.put(player.getUniqueId(),new com.hiusers.xerr.api.bossbar.BossbarHudContext(bar,components));
        try {
            var display=Class.forName("ym.quest.hud.XerrDisplay");
            display.getMethod("update",Player.class,String.class,Map.class).invoke(display.getConstructor().newInstance(),player,"quest_tracker",vars);
            var stored=components.get("quest_tracker");
            var frame=stored.getClass().getMethod("getComponent").invoke(stored);
            var json=(String)frame.getClass().getMethod("toRawMessage").invoke(frame);
            if(java.util.regex.Pattern.compile("ui_position_[0-9]+").matcher(json).find()) throw new AssertionError("Intermediate font leaked to player HUD");
            if(!json.contains("ui_position_dark") && !json.contains("ui_position_light")) throw new AssertionError("Final HUD lacks carrier font");
            if(json.contains("(color=") || json.contains("<#")) throw new AssertionError("Final HUD leaked formatting");
            var tree=com.google.gson.JsonParser.parseString(json);
            var pending=new ArrayDeque<com.google.gson.JsonElement>();pending.add(tree);var rgb=new HashSet<Integer>();
            while(!pending.isEmpty()) {
                var next=pending.remove();
                if(next.isJsonArray()) next.getAsJsonArray().forEach(pending::add);
                else if(next.isJsonObject()) {
                    var object=next.getAsJsonObject();
                    if(object.has("shadow_color")) rgb.add(object.get("shadow_color").getAsInt() & 0xffffff);
                    object.entrySet().forEach(e->{if(e.getValue().isJsonObject() || e.getValue().isJsonArray())pending.add(e.getValue());});
                }
            }
            if(!rgb.containsAll(List.of(0xffcc80,0x89f7fe,0xa8ff78,0xffd700))) throw new AssertionError("Final HUD shader RGB missing: "+rgb);
            java.nio.file.Files.writeString(java.nio.file.Path.of("../verification/xerr-tracker-final.json"),json);
            getLogger().info("XERR_FINAL_HUD_PASSED: actual Quest update captured; carrier fonts, RGB shader payload and positioning markers; no client packets");
        } finally {contexts.remove(player.getUniqueId());}
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
        if(!calls.contains("showTitle") || !calls.contains("playSound") || !calls.contains("sendMessage")) throw new AssertionError("Completion channels missing: "+calls);
        var disabled=new HashMap<String,NoticeProfile>();
        settings.getNotices().forEach((event,p)->disabled.put(event,new NoticeProfile(false,false,false,null,0,1)));
        notices.setProfiles(disabled);calls.clear();
        notices.send(player,new ProgressChange("A",chapterId,true,false,true),settings.getQuests(),settings.getChapters());
        if(!calls.isEmpty()) throw new AssertionError("Disabled channels invoked: "+calls);
        getLogger().info("CHAPTER_GUI_PASSED: two screens, inline task objectives and actual CE/vanilla reward names/counts, no task detail/mailbox/preview buttons, ItemStack non-italic rendering; proxy player, no client click claim");
    }

}
