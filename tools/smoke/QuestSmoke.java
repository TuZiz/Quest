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
        if(messages.stream().noneMatch(s->s.contains("/quest hud")) || messages.stream().anyMatch(s->s.contains("/quest reload"))) throw new AssertionError("Player help leaked admin commands or omitted HUD");
        messages.clear();command.execute(sender,"quest",new String[]{"reload"});
        if(messages.isEmpty() || messages.stream().anyMatch(s->s.contains("/quest hud"))) throw new AssertionError("Unprivileged reload was not rejected");
        if(command.tabComplete(sender,"quest",new String[]{""}).contains("reload")) throw new AssertionError("Unprivileged reload completion");
        admin.set(true);messages.clear();command.execute(sender,"quest",new String[]{"help"});
        if(messages.stream().noneMatch(s->s.contains("/quest reload")) || !command.tabComplete(sender,"quest",new String[]{""}).contains("reload")) throw new AssertionError("Admin direct reload help missing");
        getLogger().info("QUEST_COMMANDS_PASSED: player/admin help, direct reload permission and completion");
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
        for(String event:List.of("chapter-start","chapter-complete","task-start","task-complete"))
            if(!api.layouts().exists("quest_notice_"+event)) throw new AssertionError("Notice layout missing: "+event);
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
        var vars=Map.of("chapter","章节探针","task","生产探针","objective","原石","current","7","target","32","progress","7 / 32","completed","2","total","3","pending","2","show_bar","true");
        String raw=api.layouts().renderString(player,"quest_tracker",vars,true,"quest:smoke");
        try { java.nio.file.Files.writeString(java.nio.file.Path.of("../verification/xerr-tracker-render.txt"),raw); }
        catch(java.io.IOException error) { throw new IllegalStateException("Cannot export Xerr render evidence",error); }
        if(raw==null || !raw.contains("章节探针") || !raw.contains("7")) throw new AssertionError("HUD variable render failed: "+raw);
        if(!raw.contains("font=xerr_img:quest/panels/gui") || !raw.contains("font=xerr_position:space"))
            throw new AssertionError("HUD missing anchored bitmap font or positioning markers: "+raw);
        if(!raw.contains("font=xerr_progress:")) throw new AssertionError("Native progress image did not render");
        var barVars=new HashMap<String,String>(vars);
        barVars.put("show_bar","false");
        if(api.layouts().renderString(player,"quest_tracker",barVars,true,"quest:smoke").contains("font=xerr_progress:"))
            throw new AssertionError("Disabled progress bar still rendered");
        var progressFrames=new HashSet<String>();
        barVars.put("show_bar","true");
        for(String count:List.of("0","18","32")) {
            barVars.put("current",count);
            String value=api.layouts().renderString(player,"quest_tracker",barVars,true,"quest:smoke");
            // Ignore the numeric label: compare the native bar suffix only.
            int at=value.indexOf("font=xerr_progress:");
            if(at<0) throw new AssertionError("Missing native bar at "+count);
            progressFrames.add(value.substring(at));
        }
        if(progressFrames.size()!=3) throw new AssertionError("Progress fill does not change with cumulative count");
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
            if(config.getInt("quest_tracker.panel.x")!=-150 || config.getInt("quest_tracker.panel.y")!=-2) throw new AssertionError("Tracker anchor mismatch");
            for(String row:List.of("chapter","task","objective"))
                if(!"left".equals(config.getString("quest_tracker."+row+".align"))) throw new AssertionError("Tracker left alignment: "+row);
            if(!"right".equals(config.getString("quest_tracker.progress.align"))) throw new AssertionError("Progress must align right");
            var notices=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File("plugins/Xerr/core/layout/quest_notifications.yml"));
            if(notices.getInt("quest_notice_task-complete.panel.y")!=-2) throw new AssertionError("Notice must use top-right inset");
            if(api.popups().ids().stream().anyMatch(key->key.startsWith("quest_"))) throw new AssertionError("Retired Quest popup group still active");
        } catch(Exception error) { throw new IllegalStateException("Xerr component verification failed",error); }
        getLogger().info("XERR_API_PASSED: four notice layouts, compact nonoverlapping bounds, native progress and variables; proxy player, no client packets");
    }
    private void checkSidebar(Player player,Map<String,com.hiusers.xerr.api.builder.RefreshableComponent> components) throws ReflectiveOperationException,java.io.IOException {
        var api=com.hiusers.xerr.api.XerrAPI.get();
        if(!api.layouts().exists("quest_sidebar")) throw new AssertionError("Text sidebar missing");
        var values=new HashMap<String,String>();
        values.put("chapter","第一章 · 星海初醒");values.put("goal","用生成器产出 32 个原石");values.put("goal_more","");
        values.put("current","8");values.put("target","32");values.put("percent","25");
        values.put("bar_full","██");values.put("bar_empty","░░░░░░░░");values.put("show_bar","true");values.put("show_background","true");
        String raw=api.layouts().renderString(player,"quest_sidebar",values,true,"quest:sidebar");
        if(raw==null || !raw.contains("██") || !raw.contains("░░░░░░░░") || !raw.contains("25%") || raw.contains("xerr_progress:"))
            throw new AssertionError("Sidebar is not text progress: "+raw);
        if(!raw.contains("xerr_img:quest/sidebar")) throw new AssertionError("Optional dark background missing");
        values.put("show_background","false");values.put("show_bar","false");
        String bare=api.layouts().renderString(player,"quest_sidebar",values,true,"quest:sidebar");
        if(bare.contains("xerr_img:") || bare.contains("██") || !bare.contains("获取进度"))
            throw new AssertionError("Background/bar switches did not render independently: "+bare);
        var settings=ym.quest.hud.HudSettings.Companion.parse(org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File("plugins/Quest/显示.yml")));
        var trackerType=Class.forName("ym.quest.hud.QuestTrackerHud");
        var function=trackerType.getConstructors()[0].getParameterTypes()[0];
        Object identity=Proxy.newProxyInstance(function.getClassLoader(),new Class<?>[]{function},(p,m,a)->a[0]);
        Object tracker=trackerType.getConstructors()[0].newInstance(identity);
        var accept=trackerType.getMethod("accept",Player.class,ym.quest.hud.TrackerTarget.class,Set.class);
        var tick=trackerType.getMethod("tick",Collection.class,ym.quest.hud.XerrDisplay.class,ym.quest.hud.HudSettings.class,boolean.class,function,function,function);
        var display=new ym.quest.hud.XerrDisplay();
        var target=new ym.quest.hud.TrackerTarget("sidebar-island","first_resources","produce",8,32,
            Map.of("chapter","第一章 · 星海初醒","task","利用原石生成器获取32个原石","objective","用生成器产出 32 个原石","current","8","target","32"));
        var preference=new java.util.concurrent.atomic.AtomicReference<>(new ym.quest.hud.HudPreference(ym.quest.hud.HudMode.SMART,true,null));
        Object eligible=Proxy.newProxyInstance(function.getClassLoader(),new Class<?>[]{function},(p,m,a)->true);
        Object home=Proxy.newProxyInstance(function.getClassLoader(),new Class<?>[]{function},(p,m,a)->"sidebar-island");
        Object pref=Proxy.newProxyInstance(function.getClassLoader(),new Class<?>[]{function},(p,m,a)->preference.get());
        accept.invoke(tracker,player,target,Set.of("first_resources"));
        tick.invoke(tracker,List.of(player),display,settings,true,eligible,home,pref);
        var first=components.get("quest_tracker");
        if(first==null) throw new AssertionError("Default smart preference did not show permanent sidebar");
        var frame=first.getClass().getMethod("getComponent").invoke(first);
        String json=(String)frame.getClass().getMethod("toRawMessage").invoke(frame);
        if(!json.contains("主线任务") || !json.contains("25%") || json.contains("ui_position_0")) throw new AssertionError("Final sidebar payload invalid: "+json);
        java.nio.file.Files.writeString(java.nio.file.Path.of("../verification/xerr-sidebar-final.json"),json);
        for(int i=0;i<500;i++) tick.invoke(tracker,List.of(player),display,settings,true,eligible,home,pref);
        if(first!=components.get("quest_tracker")) throw new AssertionError("Unchanged sidebar redrawn");
        components.remove("quest_tracker");tick.invoke(tracker,List.of(player),display,settings,true,eligible,home,pref);
        if(!components.containsKey("quest_tracker")) throw new AssertionError("Xerr reload did not recover sidebar");
        preference.set(new ym.quest.hud.HudPreference(ym.quest.hud.HudMode.OFF,true,null));
        tick.invoke(tracker,List.of(player),display,settings,true,eligible,home,pref);
        if(components.containsKey("quest_tracker")) throw new AssertionError("Off left sidebar behind");
        preference.set(new ym.quest.hud.HudPreference(ym.quest.hud.HudMode.ALWAYS,true,null));
        tick.invoke(tracker,List.of(player),display,settings,true,eligible,home,pref);
        if(!components.containsKey("quest_tracker")) throw new AssertionError("Always did not show sidebar");
        accept.invoke(tracker,player,null,Set.of());tick.invoke(tracker,List.of(player),display,settings,true,eligible,home,pref);
        if(components.containsKey("quest_tracker")) throw new AssertionError("All completed left permanent fallback");
        accept.invoke(tracker,player,target,Set.of("first_resources"));tick.invoke(tracker,List.of(player),display,settings,true,eligible,home,pref);
        if(!components.containsKey("quest_tracker")) throw new AssertionError("New task did not restore sidebar");
        var layout=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File("plugins/Xerr/core/layout/quest_sidebar.yml"));
        if(layout.getInt("quest_sidebar.background.anchor.x")!=100 || layout.getInt("quest_sidebar.background.anchor.y")!=50)
            throw new AssertionError("Sidebar must anchor on right center");
        var notice=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File("plugins/Xerr/core/layout/quest_notifications.yml"));
        if(notice.getInt("quest_notice_task-complete.panel.anchor.y")!=0)
            throw new AssertionError("Notice must keep its independent top-right anchor");
        getLogger().info("XERR_SIDEBAR_PASSED: text bar, optional background, permanent smart/always, cached frame, reload restore, off/completed hidden, next task restored; proxy only");
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
        if(!colors.containsAll(List.of("#ddb66d","#eef3f5","#a6b2c0","#75d6bb"))) throw new AssertionError("HUD lacks actual RGB style properties: "+colors);
        for(String expected:List.of("章节探针","生产探针","原石","7/32"))
            if(!texts.contains(expected)) throw new AssertionError("HUD visible row differs from plain text: "+expected+" / "+texts);
    }
    private void checkFinalHud(Player player,Map<String,String> vars) throws Exception {
        var fontConfig=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File("plugins/Xerr/core/assets/font/text.yml"));
        if("quest_pixel_12".equals(fontConfig.getString("ui.ttf"))) {
            var font=com.hiusers.xerr.api.container.FontContainer.INSTANCE.get("ui");
            if(font.getSize()!=12 || font.getOversample()!=1.0) throw new AssertionError("Quest pixel font must render at native 12px");
            var widths=com.hiusers.xerr.api.container.RegexContainer.INSTANCE;
            for(char c:new char[]{'章','·','©','®','¼','½','¾'})
                if(widths.getWidth(c,"ui")!=2) throw new AssertionError("Fullwidth Quest glyph measured as halfwidth: "+c);
            if(widths.getWidth('3',"ui")!=1 || widths.getWidth(' ',"ui")!=1 || widths.getWidth('·',"pixel")!=1)
                throw new AssertionError("Quest width correction changed unrelated glyphs/fonts");
            getLogger().info("XERR_QUEST_FONT_METRICS_PASSED: native 12px SC font and font-specific fullwidth symbols");
        }
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
            if(!Boolean.TRUE.equals(display.getMethod("present",Player.class,String.class).invoke(display.getConstructor().newInstance(),player,"quest_tracker")))
                throw new AssertionError("Quest layout presence check missed installed tracker");
            components.remove("quest_tracker");
            if(!Boolean.FALSE.equals(display.getMethod("present",Player.class,String.class).invoke(display.getConstructor().newInstance(),player,"quest_tracker")))
                throw new AssertionError("Quest cached a tracker removed by Xerr reload");
            display.getMethod("update",Player.class,String.class,Map.class).invoke(display.getConstructor().newInstance(),player,"quest_tracker",vars);
            if(!components.containsKey("quest_tracker")) throw new AssertionError("Unchanged tracker cannot be restored after Xerr removal");
            stored=components.get("quest_tracker");
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
            if(!rgb.containsAll(List.of(0xddb66d,0xeef3f5,0xa6b2c0,0x75d6bb))) throw new AssertionError("Final HUD shader RGB missing: "+rgb);
            java.nio.file.Files.writeString(java.nio.file.Path.of("../verification/xerr-tracker-final.json"),json);
            var compactVars=new HashMap<String,String>(vars);compactVars.put("compact_task","精简探针");
            String compactRaw=com.hiusers.xerr.api.XerrAPI.get().layouts().renderString(player,"quest_tracker_compact",compactVars,true,"quest:smoke");
            if(!compactRaw.contains("精简探针") || compactRaw.contains("xerr_img:quest/panels") || compactRaw.contains("xerr_progress:"))
                throw new AssertionError("Compact tracker must contain task text without a panel or progress bar");
            display.getMethod("update",Player.class,String.class,Map.class,int.class,String.class)
                .invoke(display.getConstructor().newInstance(),player,"quest_tracker_compact",compactVars,0,"quest_tracker");
            if(components.size()!=1) throw new AssertionError("Compact and expanded tracker stacked");
            var compactFrame=components.get("quest_tracker").getClass().getMethod("getComponent").invoke(components.get("quest_tracker"));
            java.nio.file.Files.writeString(java.nio.file.Path.of("../verification/xerr-compact-final.json"),(String)compactFrame.getClass().getMethod("toRawMessage").invoke(compactFrame));
            checkSidebar(player,components);
            CompletionHudSmoke.run(player,components);
            getLogger().info("XERR_COMPLETION_PASSED: approved opaque card, all rows top-right, dynamic title/detail, single deduplicated notice, unchanged hold frame and expiry cleanup; proxy only");
            for(String event:List.of("task-start","task-complete","chapter-start","chapter-complete")) {
                display.getMethod("update",Player.class,String.class,Map.class,int.class,String.class)
                    .invoke(display.getConstructor().newInstance(),player,"quest_notice_"+event,Map.of("heading","通知探针","name","任务探针","detail","下一任务"),6,"quest_notice");
                if(components.size()!=2 || !components.containsKey("quest_notice")) throw new AssertionError("Notifications stacked instead of replacing the shared slot");
            }
            display.getMethod("remove",Player.class,String.class).invoke(display.getConstructor().newInstance(),player,"quest_notice");
            if(components.size()!=1 || !components.containsKey("quest_tracker")) throw new AssertionError("Closing notice changed the tracker");
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
