package ym.quest.smoke;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import ym.quest.reward.ItemFactory;
import ym.quest.reward.MailboxDelivery;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import java.util.*;

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
                getLogger().info("QUEST_SMOKE_PASSED: API readiness, 5 CE identities, real inventory capacity planner");
            } catch(Throwable error) {getLogger().log(java.util.logging.Level.SEVERE,"QUEST_SMOKE_FAILED",error);}
        },100L);
    }
}
