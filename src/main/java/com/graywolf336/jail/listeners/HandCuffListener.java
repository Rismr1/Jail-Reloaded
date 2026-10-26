package com.graywolf336.jail.listeners;

import com.graywolf336.jail.enums.Lang;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import com.graywolf336.jail.JailMain;

public class HandCuffListener implements Listener {
    private JailMain pl;

    public HandCuffListener(JailMain plugin) {
        this.pl = plugin;
    }

    @EventHandler(ignoreCancelled=true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if(event.isCancelled() || event.getTo() == null) return;

        if(pl.getHandCuffManager().isHandCuffed(event.getPlayer().getUniqueId())) {
            Location to = pl.getHandCuffManager().getLocation(event.getPlayer().getUniqueId()).clone();
            to.setPitch(event.getTo().getPitch());
            to.setYaw(event.getTo().getYaw());
            // Redirect the current move instead of recursively firing teleport events.
            event.setTo(to);
            if(System.currentTimeMillis() >= pl.getHandCuffManager().getNextMessageTime(event.getPlayer().getUniqueId())) {
                event.getPlayer().sendMessage(ChatColor.RED + Lang.text("handcuffing.nomove"));
                pl.getHandCuffManager().updateNextTime(event.getPlayer().getUniqueId());
            }
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        onPlayerMove(event);
    }

    @EventHandler(ignoreCancelled=true)
    public void playerChat(AsyncPlayerChatEvent event) {
        if(event.isCancelled()) return;

        if (pl.getHandCuffManager().isHandCuffed(event.getPlayer().getUniqueId()) && !event.getPlayer().hasPermission("jail.command.handcuff")) {
        	event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + Lang.text("handcuffing.nochat"));
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void blockBreak(BlockBreakEvent event) {
        if(event.isCancelled()) return;

        if (pl.getHandCuffManager().isHandCuffed(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + Lang.text("handcuffing.nobreak"));
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void blockPlace(BlockPlaceEvent event) {
        if(event.isCancelled()) return;

        if (pl.getHandCuffManager().isHandCuffed(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + Lang.text("handcuffing.noplace"));
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void preCommands(PlayerCommandPreprocessEvent event) {
        if(event.isCancelled()) return;

        if (pl.getHandCuffManager().isHandCuffed(event.getPlayer().getUniqueId()) && !event.getPlayer().hasPermission("jail.command.handcuff") && (!event.getMessage().startsWith("/r") || !event.getMessage().startsWith("/reply"))) {
        	event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + Lang.text("handcuffing.nocommand"));
        }
    }
}
