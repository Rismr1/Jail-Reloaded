package com.graywolf336.jail;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.PersistentDataType;
import com.graywolf336.jail.enums.Settings;
import com.graywolf336.jail.events.*;

/** Required, unbreakable jail equipment; original equipment is stored separately. */
public final class JailUniform implements Listener {
    private static final NamespacedKey KEY = new NamespacedKey("jailreloaded", "uniform");
    private final JailMain plugin;
    private static final java.lang.reflect.Method EQUIPPABLE = equippableMethod();
    public JailUniform(JailMain plugin) { this.plugin = plugin; }

    private static java.lang.reflect.Method equippableMethod() {
        try { return ItemMeta.class.getMethod("getEquippable"); }
        catch(NoSuchMethodException olderServer) { return null; }
    }

    private static boolean armorItem(ItemStack item) {
        EquipmentSlot slot = item.getType().getEquipmentSlot();
        if(EQUIPPABLE != null && item.hasItemMeta()) {
            try {
                if((Boolean) ItemMeta.class.getMethod("hasEquippable").invoke(item.getItemMeta())) {
                    Object component = EQUIPPABLE.invoke(item.getItemMeta());
                    slot = (EquipmentSlot) EQUIPPABLE.getReturnType().getMethod("getSlot").invoke(component);
                }
            } catch(ReflectiveOperationException unavailableComponent) { return true; }
        }
        return slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET;
    }

    public boolean required(Player player) {
        return plugin.getConfig().getBoolean(Settings.CLOTHINGENABLED.getPath())
                && plugin.getConfig().getBoolean(Settings.MOVEPROTECTION.getPath(), true)
                && plugin.getJailManager().isPlayerJailed(player.getUniqueId());
    }

    private void denied(Player player, String action) {
        plugin.console().denied(player.getUniqueId(), player.getName(), "GEAR_CHANGE_DENIED", "action", action);
    }

    public static boolean isUniform(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
    }

    private ItemStack piece(Settings setting) {
        String[] parts = plugin.getConfig().getString(setting.getPath(), "air").split("~", 2);
        Material material = Material.matchMaterial(parts[0]);
        if(material == null) throw new IllegalArgumentException("Unknown uniform material: " + parts[0]);
        if(material.isAir()) return null;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if(parts.length == 2 && meta instanceof LeatherArmorMeta) {
            String[] color = parts[1].split(",");
            ((LeatherArmorMeta) meta).setColor(Color.fromRGB(Integer.parseInt(color[0]), Integer.parseInt(color[1]), Integer.parseInt(color[2])));
        }
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public void equip(Player player) {
        if(!plugin.getConfig().getBoolean(Settings.CLOTHINGENABLED.getPath())
                || !plugin.getJailManager().isPlayerJailed(player.getUniqueId())) return;
        player.getInventory().setArmorContents(new ItemStack[] {piece(Settings.CLOTHINGBOOTS), piece(Settings.CLOTHINGLEGS),
                piece(Settings.CLOTHINGCHEST), piece(Settings.CLOTHINGHELMET)});
    }

    public static void remove(Player player) {
        ItemStack[] armor = player.getInventory().getArmorContents();
        for(int slot = 0; slot < armor.length; slot++) if(isUniform(armor[slot])) armor[slot] = null;
        player.getInventory().setArmorContents(armor);
        ItemStack[] contents = player.getInventory().getContents();
        for(int slot = 0; slot < contents.length; slot++)
            if(isUniform(contents[slot])) player.getInventory().setItem(slot, null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(InventoryClickEvent event) {
        if(!(event.getWhoClicked() instanceof Player) || !required((Player) event.getWhoClicked())) return;
        boolean shiftEquipment = event.isShiftClick() && event.getCurrentItem() != null && armorItem(event.getCurrentItem());
        if(event.getSlotType() == InventoryType.SlotType.ARMOR || isUniform(event.getCurrentItem()) || isUniform(event.getCursor())
                || shiftEquipment) {
            event.setCancelled(true);
            denied((Player) event.getWhoClicked(), event.getClick().name());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void drag(InventoryDragEvent event) {
        if(!(event.getWhoClicked() instanceof Player) || !required((Player) event.getWhoClicked())) return;
        for(int raw : event.getRawSlots()) {
            if(event.getView().getSlotType(raw) == InventoryType.SlotType.ARMOR) {
                event.setCancelled(true); denied((Player) event.getWhoClicked(), "drag"); return;
            }
        }
        if(isUniform(event.getOldCursor())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void equipByUse(PlayerInteractEvent event) {
        if(!required(event.getPlayer()) || event.getItem() == null
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        if(armorItem(event.getItem())) { event.setUseItemInHand(Event.Result.DENY); denied(event.getPlayer(), "right_click_swap"); }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void drop(PlayerDropItemEvent event) {
        if(required(event.getPlayer()) && isUniform(event.getItemDrop().getItemStack())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void damage(PlayerItemDamageEvent event) {
        if(required(event.getPlayer()) && isUniform(event.getItem())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void armorStand(PlayerArmorStandManipulateEvent event) {
        if(required(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void death(PlayerDeathEvent event) {
        if(required(event.getEntity())) event.getDrops().removeIf(JailUniform::isUniform);
    }

    private void later(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> { if(player.isOnline()) enforce(player); });
    }

    private void enforce(Player player) {
        if(!required(player)) return;
        // Re-enabling protection must not destroy replacement armor worn while unlocked.
        ItemStack[] worn = player.getInventory().getArmorContents();
        player.getInventory().setArmorContents(null);
        for(ItemStack item : worn) {
            if(item != null && !item.getType().isAir() && !isUniform(item))
                for(ItemStack overflow : player.getInventory().addItem(item.clone()).values())
                    player.getWorld().dropItemNaturally(player.getLocation(), overflow);
        }
        equip(player);
    }

    @EventHandler public void join(PlayerJoinEvent event) { later(event.getPlayer()); }
    @EventHandler public void respawn(PlayerRespawnEvent event) { later(event.getPlayer()); }
    @EventHandler public void jailed(PrisonerJailedEvent event) { equip(event.getPlayer()); }
    @EventHandler public void transferred(PrisonerTransferredEvent event) { if(event.getPlayer() != null) enforce(event.getPlayer()); }
    @EventHandler public void reloaded(JailPluginReloadedEvent event) {
        for(com.graywolf336.jail.beans.Jail jail : plugin.getJailManager().getJails()) {
            for(com.graywolf336.jail.beans.Prisoner prisoner : jail.getAllPrisoners().values()) {
                Player player = plugin.getServer().getPlayer(prisoner.getUUID());
                if(player != null) {
                    if(required(player)) enforce(player);
                    else if(!plugin.getConfig().getBoolean(Settings.CLOTHINGENABLED.getPath())) remove(player);
                }
            }
        }
    }
}
