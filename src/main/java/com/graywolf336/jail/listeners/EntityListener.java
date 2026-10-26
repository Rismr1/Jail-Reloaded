package com.graywolf336.jail.listeners;

import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import com.graywolf336.jail.JailMain;
import com.graywolf336.jail.enums.Settings;

public class EntityListener implements Listener {
    private JailMain pl;

    public EntityListener(JailMain plugin) {
        this.pl = plugin;
    }

    @EventHandler(ignoreCancelled=true)
    public void onBlockExplode(org.bukkit.event.block.BlockExplodeEvent event) {
        protectExplosion(event.blockList());
    }

    private void protectExplosion(java.util.List<Block> blocks) {
        if(pl.getConfig().getBoolean(Settings.EXPLOSIONPROTECTION.getPath()))
            blocks.removeIf(block -> pl.getJailManager().isLocationAJail(block.getLocation()));
    }

    @EventHandler(ignoreCancelled=true)
    public void onEntityExplode(EntityExplodeEvent event) {
        protectExplosion(event.blockList());
    }

    @EventHandler(ignoreCancelled=true)
    public void protectFromEndermen(EntityChangeBlockEvent event) {
        //If we are protecting the jails from endermen protection
        if(pl.getConfig().getBoolean(Settings.ENDERMENPROTECTION.getPath())) {
            //Check if there are any jails where the block's location is
            if(pl.getJailManager().isLocationAJail(event.getBlock().getLocation())) {
                //Let's cancel the event so it doesn't happen
                event.setCancelled(true);
            }
        }
    }
}
