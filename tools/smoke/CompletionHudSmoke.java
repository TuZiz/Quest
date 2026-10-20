package ym.quest.smoke;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import ym.quest.hud.*;
import ym.quest.text.TextRenderer;
import com.hiusers.xerr.api.builder.RefreshableComponent;

/** Isolated server evidence. Proxy player; never claims actual client positioning. */
public final class CompletionHudSmoke {
    public static void run(Player player,Map<String,RefreshableComponent> components) throws Exception {
        var layout=YamlConfiguration.loadConfiguration(new java.io.File("plugins/Xerr/core/layout/quest_notifications.yml"));
        for(String event:List.of("task-complete","task-start","chapter-complete","chapter-start")) {
            String key="quest_notice_"+event;
            for(String row:List.of("panel","icon","heading","detail"))
                if(layout.getInt(key+"."+row+".anchor.x")!=100 || layout.getInt(key+"."+row+".anchor.y")!=0)
                    throw new AssertionError("Notification row is not top-right: "+key+"."+row);
            if(layout.getInt(key+".panel.x")!=-220 || layout.getInt(key+".panel.y")!=-2)
                throw new AssertionError("Notification right/top inset changed");
        }
        var bitmap=javax.imageio.ImageIO.read(new java.io.File("plugins/Xerr/core/assets/image/quest/completion/textures/panel.png"));
        if(bitmap.getWidth()!=220 || bitmap.getHeight()!=44)throw new AssertionError("Completion panel dimensions");
        for(int y=0;y<bitmap.getHeight();y++)for(int x=0;x<bitmap.getWidth();x++)
            if((bitmap.getRGB(x,y)>>>24)!=255)throw new AssertionError("Completion panel must be opaque");
        var type=Class.forName("ym.quest.hud.QuestNotificationHud");
        var function=type.getConstructors()[0].getParameterTypes()[1];
        Object plain=Proxy.newProxyInstance(function.getClassLoader(),new Class<?>[]{function},(p,m,a)->a[0]);
        var language=YamlConfiguration.loadConfiguration(new java.io.File("plugins/Quest/lang/zh_cn.yml"));
        Object hud=type.getConstructors()[0].newInstance(new TextRenderer(language),plain);
        var settings=HudSettings.Companion.parse(YamlConfiguration.loadConfiguration(new java.io.File("plugins/Quest/显示.yml")));
        var display=new XerrDisplay();
        var offer=type.getMethod("offer",Player.class,List.class,HudSettings.class);
        var tick=type.getMethod("tick",Collection.class,XerrDisplay.class,boolean.class,function,function,long.class);
        Object eligible=Proxy.newProxyInstance(function.getClassLoader(),new Class<?>[]{function},(p,m,a)->true);
        Object home=Proxy.newProxyInstance(function.getClassLoader(),new Class<?>[]{function},(p,m,a)->"completion-probe");
        var notice=new HudNotice("completion-probe","first_fuel","task-complete","能源启动","下一任务",Set.of());
        offer.invoke(hud,player,List.of(notice,notice),settings);
        tick.invoke(hud,List.of(player),display,true,eligible,home,6L);
        tick.invoke(hud,List.of(player),display,true,eligible,home,14L);
        var stored=components.get("quest_notice");
        if(stored==null || components.size()!=2)throw new AssertionError("Single notice and existing tracker required");
        Object component=stored.getClass().getMethod("getComponent").invoke(stored);
        String json=(String)component.getClass().getMethod("toRawMessage").invoke(component);
        if(!json.contains("任务完成 · 能源启动") || !json.contains("奖励自动发放") || json.contains("<#") || json.contains("(color="))
            throw new AssertionError("Dynamic completion text/formatting incorrect: "+json);
        if(java.util.regex.Pattern.compile("ui_position_[0-9]+").matcher(json).find())throw new AssertionError("Unresolved font carrier");
        Files.writeString(Path.of("../verification/xerr-notice-final.json"),json);
        tick.invoke(hud,List.of(player),display,true,eligible,home,30L);
        if(stored!=components.get("quest_notice"))throw new AssertionError("Stable notification redrawn");
        tick.invoke(hud,List.of(player),display,true,eligible,home,6L+settings.getDuration());
        if(components.containsKey("quest_notice") || components.size()!=1)throw new AssertionError("Expired/duplicate notification left residue");
        type.getMethod("remove",Player.class,XerrDisplay.class).invoke(hud,player,display);
    }
}
