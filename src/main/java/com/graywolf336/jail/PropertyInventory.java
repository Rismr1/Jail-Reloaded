package com.graywolf336.jail;

import java.io.IOException;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import com.graywolf336.jail.beans.Prisoner;
import com.graywolf336.jail.interfaces.ICell;

/** The saved snapshot is authoritative; a chest contains protected display copies. */
public final class PropertyInventory {
    private PropertyInventory() { }

    public static void confiscate(Player player, Prisoner prisoner, List<String> blacklist, ICell cell) {
        player.closeInventory();
        ItemStack[] original = player.getInventory().getContents();
        ItemStack[] saved = new ItemStack[original.length];
        for(int slot = 0; slot < original.length; slot++) {
            ItemStack item = original[slot];
            if(item != null && !Util.isStringInsideList(item.getType().name(), blacklist)) saved[slot] = item.clone();
        }
        // Finish encoding before taking anything from the player.
        prisoner.setInventory(Util.itemStackArrayToBase64(saved));
        prisoner.setArmor("");
        populate(cell, saved);
        player.getInventory().setArmorContents(null);
        player.getInventory().clear();
    }

    public static ItemStack[] read(Prisoner prisoner) {
        try { return Util.itemStackArrayFromBase64(prisoner.getInventory()); }
        catch(IOException failure) { throw new IllegalStateException("Cannot decode stored property; prisoner and saved data retained", failure); }
    }

    public static void captureLegacy(Prisoner prisoner, ICell cell) {
        if(prisoner.getInventory().isEmpty() && cell != null && cell.hasChest())
            prisoner.setInventory(Util.toBase64(cell.getChest().getInventory()));
    }

    public static void populate(ICell cell, ItemStack[] items) {
        if(cell == null || !cell.hasChest()) return;
        Inventory chest = cell.getChest().getInventory();
        CellChestProtection.closeViewers(chest);
        chest.clear();
        for(int slot = 0; slot < Math.min(items.length, chest.getSize()); slot++)
            if(items[slot] != null) chest.setItem(slot, items[slot].clone());
        // Overflow remains in the authoritative snapshot and is restored on release.
    }

    /** Reclaim directly into storage slots; never put property on the cursor or equip it. */
    static boolean reclaim(Player player, Prisoner prisoner, Inventory chest, int slot, int requested) {
        return reclaim(player, prisoner, chest, slot, requested, () -> { });
    }

    static boolean reclaim(Player player, Prisoner prisoner, Inventory chest, int slot, int requested, Runnable persist) {
        ItemStack[] saved = read(prisoner);
        if(slot < 0 || slot >= saved.length || saved[slot] == null || requested <= 0) return false;
        ItemStack item = saved[slot];
        if(item.getType().isAir()) return false;
        if(!item.equals(chest.getItem(slot))) return false;
        ItemStack[] storage = player.getInventory().getStorageContents().clone();
        for(int i = 0; i < storage.length; i++) if(storage[i] != null) storage[i] = storage[i].clone();
        int wanted = Math.min(requested, item.getAmount());
        int remaining = wanted;
        int limit = Math.min(item.getMaxStackSize(), player.getInventory().getMaxStackSize());
        for(int pass = 0; pass < 2; pass++) {
            for(int i = 0; i < storage.length && remaining > 0; i++) {
                ItemStack existing = storage[i];
                boolean empty = existing == null || existing.getType().isAir();
                if(pass == 0 ? empty || !existing.isSimilar(item) : !empty) continue;
                int amount = Math.min(remaining, empty ? limit : Math.max(0, limit - existing.getAmount()));
                if(amount == 0) continue;
                if(empty) { storage[i] = item.clone(); storage[i].setAmount(amount); }
                else existing.setAmount(existing.getAmount() + amount);
                remaining -= amount;
            }
        }
        int taken = wanted - remaining;
        if(taken == 0) return false;
        saved[slot] = item.getAmount() == taken ? null : item.clone();
        if(saved[slot] != null) saved[slot].setAmount(item.getAmount() - taken);
        // Encode before changing either inventory; keep the merge flag across reload/transfer.
        String encoded = ItemBytes.RECLAIMED + Util.itemStackArrayToBase64(saved);
        String previous = prisoner.getInventory();
        prisoner.setInventory(encoded);
        try { persist.run(); }
        catch(RuntimeException failure) { prisoner.setInventory(previous); throw failure; }
        player.getInventory().setStorageContents(storage);
        chest.setItem(slot, saved[slot] == null ? null : saved[slot].clone());
        return true;
    }

    /** Roll back live inventories if durable release/commit fails after restoration. */
    static void withRollback(Player player, ICell cell, Runnable operation) {
        player.closeInventory();
        ItemStack[] carried = copy(player.getInventory().getContents());
        Inventory chest = cell != null && cell.hasChest() ? cell.getChest().getInventory() : null;
        ItemStack[] displayed = chest == null ? null : copy(chest.getContents());
        try { operation.run(); }
        catch(RuntimeException failure) {
            player.getInventory().setContents(carried);
            if(chest != null) chest.setContents(displayed);
            throw failure;
        }
    }

    private static ItemStack[] copy(ItemStack[] source) {
        ItemStack[] result = source.clone();
        for(int i = 0; i < result.length; i++) if(result[i] != null) result[i] = result[i].clone();
        return result;
    }

    public static void restore(Player player, Prisoner prisoner, ICell cell, boolean store) {
        if(store && prisoner.getArmor().isEmpty()) captureLegacy(prisoner, cell);
        // Honor the snapshot taken at jailing even if storeInventory changed on reload.
        store = !prisoner.getInventory().isEmpty();
        ItemStack[] items = store ? read(prisoner) : new ItemStack[0];
        final ItemStack[] armor;
        try { armor = Util.itemStackArrayFromBase64(prisoner.getArmor()); }
        catch(IOException failure) { throw new IllegalStateException("Cannot decode original armor; saved data retained", failure); }
        // All data has been decoded successfully before clearing any inventory.
        player.closeInventory();
        if(store && prisoner.getInventory().startsWith(ItemBytes.RECLAIMED)) {
            JailUniform.remove(player);
            // Keep reclaimed/earned items, restoring remaining property into free original
            // slots where possible. Overflow is dropped instead of discarded.
            boolean slotted = items.length == player.getInventory().getSize();
            for(int slot = 0; slot < items.length; slot++) {
                ItemStack item = items[slot];
                if(item == null || item.getType().isAir()) continue;
                ItemStack current = slotted ? player.getInventory().getItem(slot) : null;
                if(slotted && (current == null || current.getType().isAir()))
                    player.getInventory().setItem(slot, item.clone());
                else for(ItemStack overflow : player.getInventory().addItem(item.clone()).values())
                    player.getWorld().dropItemNaturally(player.getLocation(), overflow);
            }
        } else if(store) {
            player.getInventory().setArmorContents(null);
            player.getInventory().clear();
            if(items.length == player.getInventory().getSize()) {
                player.getInventory().setContents(items);
            } else {
                for(ItemStack item : items) {
                    if(item != null && !item.getType().isAir()) {
                        for(ItemStack overflow : player.getInventory().addItem(item).values())
                            player.getWorld().dropItemNaturally(player.getLocation(), overflow);
                    }
                }
            }
        } else JailUniform.remove(player);
        if(armor.length == 4) {
            for(ItemStack worn : player.getInventory().getArmorContents()) {
                if(worn != null && !worn.getType().isAir() && !JailUniform.isUniform(worn))
                    for(ItemStack overflow : player.getInventory().addItem(worn.clone()).values())
                        player.getWorld().dropItemNaturally(player.getLocation(), overflow);
            }
            player.getInventory().setArmorContents(armor);
        }
        if(store && cell != null && cell.hasChest()) {
            CellChestProtection.closeViewers(cell.getChest().getInventory());
            cell.getChest().getInventory().clear();
        }
    }
}
