package com.graywolf336.jail.listeners;

import java.util.List;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import com.graywolf336.jail.JailMain;
import com.graywolf336.jail.Util;
import com.graywolf336.jail.beans.Cell;
import com.graywolf336.jail.beans.Jail;
import com.graywolf336.jail.beans.SimpleLocation;
import org.bukkit.block.Block;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import com.graywolf336.jail.enums.Settings;
import com.graywolf336.jail.events.JailPluginReloadedEvent;
import com.graywolf336.jail.events.OfflinePrisonerJailedEvent;
import com.graywolf336.jail.events.PrisonerJailedEvent;
import com.graywolf336.jail.events.PrisonerReleasedEvent;
import com.graywolf336.jail.events.PrisonerTimeChangeEvent;
import com.graywolf336.jail.events.PrisonerTransferredEvent;

public class CellSignListener implements Listener {
    private String lineOne = "", lineTwo = "", lineThree = "", lineFour = "";
    private JailMain pl;

    public static void refreshLoadedSigns(JailMain plugin) {
        for(Jail jail : plugin.getJailManager().getJails()) {
            for(Cell cell : jail.getCells()) cell.updateSigns();
        }
    }

    private boolean isRegistered(Block block) {
        for(Jail jail : pl.getJailManager().getJails()) {
            for(Cell cell : jail.getCells()) {
                for(SimpleLocation sign : cell.getSigns()) {
                    org.bukkit.Location location = sign.getLocation();
                    if(sign.getWorldName().equals(block.getWorld().getName())
                            && location.getBlockX() == block.getX() && location.getBlockY() == block.getY()
                            && location.getBlockZ() == block.getZ()) return true;
                }
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void preventRegisteredSignInteraction(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if(event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null
                || !(block.getState() instanceof org.bukkit.block.Sign) || !isRegistered(block)) return;
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void preventRegisteredSignEditing(SignChangeEvent event) {
        if(isRegistered(event.getBlock())) event.setCancelled(true);
    }

    @EventHandler
    public void protectSignsOnChunkLoad(ChunkLoadEvent event) {
        for(Jail jail : pl.getJailManager().getJails()) {
            for(Cell cell : jail.getCells()) {
                boolean inChunk = cell.getSigns().stream().anyMatch(sign ->
                        sign.getWorldName().equals(event.getWorld().getName())
                        && (sign.getLocation().getBlockX() >> 4) == event.getChunk().getX()
                        && (sign.getLocation().getBlockZ() >> 4) == event.getChunk().getZ());
                if(inChunk) cell.updateSigns();
            }
        }
    }

    public CellSignListener(JailMain plugin) {
        pl = plugin;
        List<String> lines = pl.getConfig().getStringList(Settings.CELLSIGNLINES.getPath());

        if(lines.size() >= 1) lineOne = lines.get(0);
        if(lines.size() >= 2) lineTwo = lines.get(1);
        if(lines.size() >= 3) lineThree = lines.get(2);
        if(lines.size() >= 4) lineFour = lines.get(3);

        try {
            Util.updateSignLinesCache(new String[] { lineOne, lineTwo, lineThree, lineFour });
        }catch(Exception e) {
            //this should never happen
            pl.debug("Error on Cell Sign Listener constructor:" + e.getMessage());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void changeTheCellSigns(final PrisonerTimeChangeEvent event) {
        pl.getServer().getScheduler().scheduleSyncDelayedTask(pl, new Runnable() {
            public void run() {
                if (event.hasCell() && event.getCell().hasSigns()) {
                    event.getCell().updateSigns();
                }
            }
        });
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void changeSignsOnOfflineJailing(OfflinePrisonerJailedEvent event) {
        if (event.hasCell() && event.getCell().hasSigns()) {
            event.getCell().updateSigns();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void changeCellSignsOnJail(PrisonerJailedEvent event) {
        if (event.hasCell() && event.getCell().hasSigns()) {
            event.getCell().updateSigns();
        }
    }

    @EventHandler
    public void clearTheCellSigns(PrisonerReleasedEvent event) {
        if (event.hasCell() && event.getCell().hasSigns()) {
            event.getCell().updateSigns();
        }
    }

    @EventHandler
    public void handleSignsOnTransfer(PrisonerTransferredEvent event) {
        if (event.hasOriginalCell() && event.getOriginalCell().hasSigns()) {
            event.getOriginalCell().updateSigns();
        }

        if (event.hasTargetCell() && event.getTargetCell().hasSigns()) {
            event.getTargetCell().updateSigns();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void handleSignLineUpdates(JailPluginReloadedEvent event) throws Exception {
        List<String> lines = pl.getConfig().getStringList(Settings.CELLSIGNLINES.getPath());

        //Reset the lines to nothing
        lineOne = "";
        lineTwo = "";
        lineThree = "";
        lineFour = "";

        if(lines.size() >= 1) lineOne = lines.get(0);
        if(lines.size() >= 2) lineTwo = lines.get(1);
        if(lines.size() >= 3) lineThree = lines.get(2);
        if(lines.size() >= 4) lineFour = lines.get(3);

        Util.updateSignLinesCache(new String[] { lineOne, lineTwo, lineThree, lineFour });
        refreshLoadedSigns(pl);
    }
}
