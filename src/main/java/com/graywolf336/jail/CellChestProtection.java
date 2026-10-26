package com.graywolf336.jail;

import java.util.ArrayList;
import org.bukkit.Location;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import com.graywolf336.jail.beans.*;
import com.graywolf336.jail.enums.Settings;

/** Registered property chests are permanently protected, including vacant cells. */
public final class CellChestProtection implements Listener {
    private final JailMain plugin;
    public CellChestProtection(JailMain plugin) { this.plugin = plugin; }

    private static boolean sameBlock(Location a, Location b) {
        return a != null && b != null && a.getWorld() != null && a.getWorld().equals(b.getWorld())
                && a.getBlockX() == b.getBlockX() && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ();
    }

    static boolean contains(Inventory inventory, Location registered) {
        InventoryHolder holder = inventory.getHolder();
        if(holder instanceof Chest) {
            Chest chest = (Chest) holder;
            if(sameBlock(chest.getLocation(), registered)) return true;
            // Hopper events can expose only the other half's block inventory.
            Inventory connected = chest.getInventory();
            if(connected == null || !(connected.getHolder() instanceof DoubleChest)) return false;
            holder = connected.getHolder();
        }
        if(holder instanceof DoubleChest) {
            DoubleChest chest = (DoubleChest) holder;
            return (chest.getLeftSide() instanceof Chest && sameBlock(((Chest) chest.getLeftSide()).getLocation(), registered))
                    || (chest.getRightSide() instanceof Chest && sameBlock(((Chest) chest.getRightSide()).getLocation(), registered));
        }
        return sameBlock(inventory.getLocation(), registered);
    }

    public Cell findCell(Inventory inventory) {
        if(inventory == null) return null;
        for(Jail jail : plugin.getJailManager().getJails()) {
            for(Cell cell : jail.getCells()) {
                if(cell.useChest() && contains(inventory, cell.getChestLocation())) return cell;
            }
        }
        return null;
    }

    public boolean mayView(Player player, Cell cell) {
        if(cell.hasResourceConflict() || !cell.hasPrisoner() || !cell.getPrisoner().getUUID().equals(player.getUniqueId())
                || !plugin.getJailManager().isPlayerJailed(player.getUniqueId())
                || !plugin.getConfig().getBoolean(Settings.PRISONEROPENCHEST.getPath())) return false;
        try { CellResources.requireAvailable(plugin.getJailManager(), plugin.getJailManager().getJailPlayerIsIn(player.getUniqueId()), cell); }
        catch(IllegalArgumentException conflict) {
            cell.setResourceConflict(true);
            plugin.getLogger().warning("Cell " + cell.getName() + " has shared resources; access disabled. " + conflict.getMessage());
            return false;
        }
        return true;
    }

    public boolean mayReclaim(Player player, Cell cell) {
        return mayView(player, cell) && !plugin.getConfig().getBoolean(Settings.MOVEPROTECTION.getPath(), true);
    }

    private void denied(org.bukkit.entity.HumanEntity player, Cell cell, String reason) {
        plugin.console().denied(player.getUniqueId(), player.getName(), "CHEST_DENIED", "cell", cell.getName(), "reason", reason);
    }

    private boolean registered(org.bukkit.block.Block block) {
        return block.getState() instanceof Chest && findCell(((Chest) block.getState()).getInventory()) != null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void interact(PlayerInteractEvent event) {
        if(event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || !(event.getClickedBlock().getState() instanceof Chest)) return;
        Cell cell = findCell(((Chest) event.getClickedBlock().getState()).getInventory());
        if(cell != null && !mayView(event.getPlayer(), cell)) {
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
            denied(event.getPlayer(), cell, cell.hasPrisoner() ? "not_assigned_or_view_disabled" : "vacant_cell");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void open(InventoryOpenEvent event) {
        Cell cell = findCell(event.getInventory());
        if(cell != null && (!(event.getPlayer() instanceof Player) || !mayView((Player) event.getPlayer(), cell))) {
            event.setCancelled(true);
            denied(event.getPlayer(), cell, cell.hasPrisoner() ? "not_assigned_or_view_disabled" : "vacant_cell");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void click(InventoryClickEvent event) {
        // Cancel the whole view: this includes shift, hotbar, offhand, clone,
        // double-click collection, cursor drops, and clicks in the lower inventory.
        Cell cell = findCell(event.getView().getTopInventory());
        if(cell == null) return;
        event.setCancelled(true);
        Inventory chest = event.getView().getTopInventory();
        if(event.getWhoClicked() instanceof Player && mayReclaim((Player) event.getWhoClicked(), cell)
                && event.getRawSlot() >= 0 && event.getRawSlot() < chest.getSize()
                && (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT
                    || event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT)) {
            Player player = (Player) event.getWhoClicked();
            Prisoner owner = cell.getPrisoner();
            int slot = event.getRawSlot();
            int amount = event.getClick() == ClickType.RIGHT ? 1 : Integer.MAX_VALUE;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if(!player.isOnline() || cell.getPrisoner() != owner || !mayReclaim(player, cell)
                        || player.getOpenInventory().getTopInventory() != chest || findCell(chest) != cell) return;
                try {
                    PropertyInventory.captureLegacy(owner, cell);
                    if(PropertyInventory.reclaim(player, owner, chest, slot, amount,
                            () -> plugin.getJailIO().saveCellOrPrisoner(plugin.getJailManager().getJailPlayerIsIn(player.getUniqueId()), cell, owner))) {
                        player.updateInventory();
                    }
                } catch(IllegalStateException failure) {
                    plugin.getLogger().warning("Cannot reclaim stored property for " + player.getName() + "; saved data retained.");
                }
            });
        } else denied(event.getWhoClicked(), cell, "read_only");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void drag(InventoryDragEvent event) {
        Cell cell = findCell(event.getView().getTopInventory());
        if(cell != null) { event.setCancelled(true); denied(event.getWhoClicked(), cell, "read_only"); }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void move(InventoryMoveItemEvent event) {
        if(findCell(event.getSource()) != null || findCell(event.getDestination()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void pickup(InventoryPickupItemEvent event) {
        if(findCell(event.getInventory()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void breakChest(BlockBreakEvent event) { if(registered(event.getBlock())) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void explode(EntityExplodeEvent event) { event.blockList().removeIf(this::registered); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void explode(BlockExplodeEvent event) { event.blockList().removeIf(this::registered); }

    public static void closeViewers(Inventory inventory) {
        for(org.bukkit.entity.HumanEntity viewer : new ArrayList<>(inventory.getViewers())) viewer.closeInventory();
    }
}
