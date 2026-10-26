package com.graywolf336.jail;

import java.util.*;
import org.bukkit.Location;
import org.bukkit.block.Chest;
import com.graywolf336.jail.beans.*;

/** Exclusive cell resources; loaded conflicts are retained but closed to access. */
public final class CellResources {
    private CellResources() { }

    private static String key(String world, Location location) {
        return world + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    public static boolean signRegistered(JailManager manager, Location location) {
        String wanted = key(location.getWorld().getName(), location);
        for(Jail jail : manager.getJails()) for(Cell cell : jail.getCells())
            for(SimpleLocation sign : cell.getSigns()) if(key(sign.getWorldName(), sign.getLocation()).equals(wanted)) return true;
        return false;
    }

    public static void requireAvailable(JailManager manager, Jail owner, Cell candidate) {
        for(Jail jail : manager.getJails()) for(Cell cell : jail.getCells()) {
            if(cell == candidate) continue;
            if(sharedChest(owner, candidate, jail, cell)) throw new IllegalArgumentException("Chest is already registered to " + jail.getName() + "/" + cell.getName());
            for(SimpleLocation a : candidate.getSigns()) for(SimpleLocation b : cell.getSigns())
                if(key(a.getWorldName(), a.getLocation()).equals(key(b.getWorldName(), b.getLocation())))
                    throw new IllegalArgumentException("Sign is already registered to " + jail.getName() + "/" + cell.getName());
        }
    }

    private static boolean sharedChest(Jail a, Cell first, Jail b, Cell second) {
        if(!first.useChest() || !second.useChest() || !a.getWorldName().equals(b.getWorldName())) return false;
        if(key(a.getWorldName(), first.getChestLocation()).equals(key(b.getWorldName(), second.getChestLocation()))) return true;
        Location position = first.getChestLocation();
        if(position.getWorld() == null || !position.getWorld().isChunkLoaded(position.getBlockX() >> 4, position.getBlockZ() >> 4)) return false;
        Chest live = first.getChest();
        return live != null && CellChestProtection.contains(live.getInventory(), second.getChestLocation());
    }

    public static void validate(JailManager manager) {
        Set<Cell> previousConflicts = Collections.newSetFromMap(new IdentityHashMap<>());
        for(Jail jail : manager.getJails()) for(Cell cell : jail.getCells())
            if(cell.hasResourceConflict()) previousConflicts.add(cell);
        for(Jail jail : manager.getJails()) for(Cell cell : jail.getCells()) cell.setResourceConflict(false);
        for(Jail jail : manager.getJails()) for(Cell cell : jail.getCells()) {
            try { requireAvailable(manager, jail, cell); }
            catch(IllegalArgumentException conflict) {
                cell.setResourceConflict(true);
                if(!previousConflicts.contains(cell)) manager.getPlugin().getLogger().warning("Cell " + jail.getName() + "/" + cell.getName() + " has shared resources; chest/sign access disabled until corrected. " + conflict.getMessage());
            }
        }
    }
}
