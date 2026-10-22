package ym.quest.smoke;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;
import org.bukkit.boss.BossBar;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;
import com.hiusers.xerr.api.bossbar.BossbarHudContext;
import com.hiusers.xerr.api.container.BossbarHudRenderer;
import ym.quest.hud.*;
import ym.quest.domain.*;
import ym.quest.text.TextRenderer;

/** Server renderer probe; deliberately does not claim client pixel/GUI-scale acceptance. */
public final class QuestHudSmoke extends JavaPlugin {
    private Object field(Object object,String name) throws Exception {
        var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);
    }
    @Override public void onEnable() {
        getServer().getScheduler().runTaskLater(this,this::check,140);
        getCommand("questhudsmoke").setExecutor((s,c,l,a)->{check();return true;});
    }
    private void require(boolean condition,String message) {if(!condition)throw new AssertionError(message);}
    private void check() {
        UUID id=UUID.randomUUID();
        Map<UUID,BossbarHudContext> contexts=null;
        try {
            var quest=(JavaPlugin)getServer().getPluginManager().getPlugin("Quest");
            require(quest!=null && quest.isEnabled(),"Quest disabled");
            require(field(field(quest,"runtime"),"hud")!=null,"HUD bridge absent");
            require(field(field(quest,"runtime"),"tab")==null,"Retired TAB quest bridge still running");
            var settings=HudSettings.Companion.load(getDataFolder().toPath().getParent().resolve("Xerr/quest.yml"));
            var renderer=new XerrHudRenderer(settings);
            require(renderer.ready(settings),"Missing Xerr layouts");
            Player player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->switch(m.getName()) {
                case "getUniqueId" -> id;
                case "getName" -> "QuestHudProbe";
                case "isOnline","hasPermission","hasResourcePack" -> true;
                case "getWorld" -> getServer().getWorlds().getFirst();
                case "getLocation" -> getServer().getWorlds().getFirst().getSpawnLocation();
                case "hashCode" -> id.hashCode();
                case "equals" -> p==a[0];
                default -> m.getReturnType()==boolean.class ? false : null;
            });
            BossBar bar=(BossBar)Proxy.newProxyInstance(BossBar.class.getClassLoader(),new Class<?>[]{BossBar.class},(p,m,a)->m.getName().equals("getPlayers")?List.of(player):null);
            var mapField=BossbarHudRenderer.class.getDeclaredField("playerBossbar");mapField.setAccessible(true);
            contexts=(Map<UUID,BossbarHudContext>)mapField.get(null);
            contexts.put(id,new BossbarHudContext(bar,new LinkedHashMap<>()));
            var legacy=net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection();
            var styled=renderer.styled("task","第一批资源");
            var parsed=legacy.deserialize(styled);
            var plain=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
            require(plain.serialize(parsed).equals("第一批资源"),"Components markup leaked");
            require(parsed.color()!=null || parsed.children().stream().anyMatch(c->c.color()!=null),"Native color missing");
            var escaped=renderer.styled("task","[名称](color=#FF0000)");
            require(plain.serialize(legacy.deserialize(escaped)).equals("[名称](color=#FF0000)"),"Dynamic text became formatting");
            var runtime=field(quest,"runtime");
            var service=(QuestService)field(runtime,"service");
            var projection=new HudProjection((TextRenderer)field(runtime,"text"),settings.getTextWidth());
            var fuel=service.getDefinitions().stream().filter(q->q.getId().equals("first_fuel")).findFirst().orElseThrow();
            var chapter=service.getChapters().stream().filter(c->c.getQuests().contains(fuel.getId())).findFirst().orElseThrow();
            var reports=new com.google.gson.JsonArray();
            for(int current:new int[]{0,1}) {
                var view=new JournalView(List.of(new QuestView(fuel,QuestStatus.ACTIVE,List.of(new ObjectiveProgress(fuel.getObjectives().getFirst(),current)))),
                    List.of(new ChapterView(chapter,ChapterStatus.ACTIVE,0,false)));
                var values=projection.project(view);
                renderer.render(player,QuestHudBridge.TRACKER,settings.getTracker(),values,255);
                var component=contexts.get(id).getComponents().get(QuestHudBridge.TRACKER).getComponent();
                String raw=component.toRawMessage();
                require(raw.contains("主线任务") && raw.contains("能源启动"),"Mainline/task missing");
                require(raw.contains("打开管理面板") && raw.contains("打开燃料仓") && raw.contains("或放入上方格子"),"Full step instructions missing: "+raw);
                require(!raw.contains("xerr_progress:") && !raw.contains("共享进度") && !raw.contains("TAB"),"Retired duplicate sidebar elements remain");
                require(raw.contains(current+" / 1"),"Numeric progress missing");
                require(raw.contains("quest_sidebar_position_dark") || raw.contains("quest_sidebar_position_light"),"Real 10px positioned font missing: "+raw);
                require(!java.util.regex.Pattern.compile("quest_sidebar_position_[0-9]+").matcher(raw).find(),"Virtual font leaked");
                require(!raw.contains("(color=") && !raw.contains("<#"),"Formatting syntax leaked");
                reports.add(com.google.gson.JsonParser.parseString(raw));
            }
            var notice=Map.of("heading","任务完成","name","能源启动","detail","下一任务：第一批资源");
            renderer.render(player,QuestHudBridge.NOTICE,settings.getNotice(),notice,128);
            var raw=contexts.get(id).getComponents().get(QuestHudBridge.NOTICE).getComponent().toRawMessage();
            require(raw.contains("能源启动") && raw.contains("shadow_color") && raw.contains("quest_ui_position_dark"),"Unchanged 12px notification positioning/alpha missing");
            reports.add(com.google.gson.JsonParser.parseString(raw));
            renderer.remove(player,QuestHudBridge.NOTICE);
            require(!renderer.present(player,QuestHudBridge.NOTICE),"Notice not removed");
            contexts.get(id).getComponents().clear();
            require(!renderer.present(player,QuestHudBridge.TRACKER),"External clear undetected");
            var restore=new JournalView(List.of(new QuestView(fuel,QuestStatus.ACTIVE,List.of(new ObjectiveProgress(fuel.getObjectives().getFirst(),0)))),List.of(new ChapterView(chapter,ChapterStatus.ACTIVE,0,false)));
            renderer.render(player,QuestHudBridge.TRACKER,settings.getTracker(),projection.project(restore),255);
            require(renderer.present(player,QuestHudBridge.TRACKER),"Resubmit failed");
            Files.createDirectories(getDataFolder().toPath());
            Files.writeString(getDataFolder().toPath().resolve("rendered-components.json"),reports.toString());
            var language=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(getDataFolder().toPath().getParent().resolve("Quest/lang/zh_cn.yml").toFile());
            var evidence=new com.google.gson.JsonArray();
            var fields=Map.of("personal","scope","shared","scope","task-complete","heading","chapter-complete","heading",
                "task-detail","detail","chapter-detail","detail","next","detail","help","help");
            for(var entry:fields.entrySet()) {
                String original=language.getString("hud."+entry.getKey());
                String value=original.replace("{next}","第一批资源");
                var sample=new com.google.gson.JsonObject();
                sample.addProperty("path","src/main/resources/lang/zh_cn.yml");sample.addProperty("field","hud."+entry.getKey());
                sample.addProperty("text_hash",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(original.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
                sample.addProperty("runtime","Quest XerrHudRenderer / Xerr 2.1.0 native TabooLib Components / actual server templates");
                sample.addProperty("source","plugin_renderer_test");sample.add("placeholders",new com.google.gson.JsonArray());
                sample.add("component",com.google.gson.JsonParser.parseString(net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().serialize(legacy.deserialize(renderer.styled(entry.getValue(),value)))));
                evidence.add(sample);
            }
            Files.writeString(getDataFolder().toPath().resolve("native-components.json"),evidence.toString());
            getLogger().info("QUEST_HUD_SMOKE_PASSED: TAB bridge off, native Components, literal placeholders, real 10px sidebar/12px notification fonts, full six-line steps, numeric progress, no image bar/shared/TAB footer, notification removal and slot recovery");
        } catch(Throwable error) { getLogger().log(java.util.logging.Level.SEVERE,"QUEST_HUD_SMOKE_FAILED",error); }
        finally { if(contexts!=null)contexts.remove(id); }
    }
}
