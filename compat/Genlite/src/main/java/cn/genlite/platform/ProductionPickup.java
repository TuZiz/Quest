package cn.genlite.platform;

import cn.genlite.domain.MachineEngine;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.logging.Level;

/** Player/main thread: only entity provenance survives; ordinary dropped stacks cannot impersonate output. */
public final class ProductionPickup implements Listener {
    private final JavaPlugin plugin;
    private final MachineEngine engine;
    private final BiPredicate<UUID,UUID> member;
    public ProductionPickup(JavaPlugin plugin,MachineEngine engine,BiPredicate<UUID,UUID> member) {
        this.plugin=plugin;this.engine=engine;this.member=member;
    }
    public static void tag(org.bukkit.entity.Item item,JavaPlugin plugin,UUID receipt,UUID home) {
        var p=item.getPersistentDataContainer();
        p.set(new NamespacedKey(plugin,"quest-output"),PersistentDataType.STRING,receipt.toString());
        p.set(new NamespacedKey(plugin,"quest-home"),PersistentDataType.STRING,home.toString());
        p.set(new NamespacedKey(plugin,"quest-picked"),PersistentDataType.INTEGER,0);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void merge(ItemMergeEvent e) {
        var key=new NamespacedKey(plugin,"quest-output");
        if(e.getEntity().getPersistentDataContainer().has(key) || e.getTarget().getPersistentDataContainer().has(key)) e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void pickup(EntityPickupItemEvent e) {
        if(!(e.getEntity() instanceof Player player)) return;
        var entity=e.getItem();var data=entity.getPersistentDataContainer();
        String receipt=data.get(new NamespacedKey(plugin,"quest-output"),PersistentDataType.STRING);
        if(receipt==null) return;
        // Explicit synchronous delivery allows count-after-success, including partial inventory capacity.
        e.setCancelled(true);
        if(!engine.ready()) return;
        UUID home=UUID.fromString(data.get(new NamespacedKey(plugin,"quest-home"),PersistentDataType.STRING));
        if(!member.test(home,player.getUniqueId())) return;
        var stack=entity.getItemStack();int before=stack.getAmount();
        int rest=player.getInventory().addItem(stack.clone()).values().stream().mapToInt(org.bukkit.inventory.ItemStack::getAmount).sum();
        int taken=before-rest;if(taken<=0) return;
        int total=data.getOrDefault(new NamespacedKey(plugin,"quest-picked"),PersistentDataType.INTEGER,0)+taken;
        data.set(new NamespacedKey(plugin,"quest-picked"),PersistentDataType.INTEGER,total);
        String material="minecraft:"+stack.getType().getKey().getKey();
        if(rest==0) entity.remove();else {stack.setAmount(rest);entity.setItemStack(stack);}
        engine.transact(d -> {d.count(home,"genlite.pickup:"+material,taken);return null;})
            .exceptionally(error -> {plugin.getLogger().log(Level.SEVERE,"Production pickup receipt save failed; home="+home+", receipt="+receipt,error);return null;});
    }
}
