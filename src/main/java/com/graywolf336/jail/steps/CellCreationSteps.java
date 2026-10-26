package com.graywolf336.jail.steps;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.graywolf336.jail.JailManager;
import com.graywolf336.jail.Util;
import com.graywolf336.jail.beans.Cell;
import com.graywolf336.jail.beans.CreationPlayer;
import com.graywolf336.jail.beans.Jail;
import com.graywolf336.jail.beans.SimpleLocation;
import com.graywolf336.jail.enums.Lang;

/**
 * Class for stepping a player through the Cell creation process, instance is stored in {@link JailManager}.
 * 
 * @author graywolf336
 * @since 3.0.0
 * @version 1.0.1
 */
public class CellCreationSteps {
    /** Sends the Cell Creation message for starting out. */
    public void startStepping(Player player){
        player.sendMessage(ChatColor.AQUA + Lang.text("creation.cell"));
        player.sendMessage(ChatColor.GREEN + Lang.text("creation.cellarrival"));
        player.sendMessage(ChatColor.AQUA + "----------------------------------------");

        ItemStack wand = Util.getWand();
        if(player.getInventory().contains(wand)) {
            int i = player.getInventory().first(wand);
            if(i != -1) {
                player.getInventory().setItem(i, player.getInventory().getItemInMainHand());
                player.getInventory().setItemInMainHand(wand);
            }
        }else {
            player.getInventory().addItem(wand);
        }
    }

    /**
     * Applies the next step in the Cell Creation process that involves a location, null if no location is needed.
     * 
     * @param jm The {@link JailManager} instance.
     * @param player The player who is doing the creating.
     * @param cp The {@link CreationPlayer} instance
     * @param location The location, null if none, being set.
     */
    public void step(JailManager jm, Player player, CreationPlayer cp, Location location) {
        jm.getPlugin().debug("Stepping into step #" + cp.getStep());
        switch(cp.getStep()) {
            case 1:
                firstStep(jm, cp, player);
                break;
            case 2:
                secondStep(jm, cp, player, location.getBlock());
                break;
            case 3:
                thirdStep(jm, cp, player, location.getBlock());
                break;
            default:
                player.sendMessage(ChatColor.RED + Lang.text("creation.failed"));
                jm.removeJailCreationPlayer(player.getName());
                break;
        }
    }

    /** Applies the first step, which is setting the teleport in location. */
    private void firstStep(JailManager jm, CreationPlayer cp, Player player) {
        Vector v1 = jm.getJail(cp.getJailName()).getMinPoint().toVector().clone();
        Vector v2 = jm.getJail(cp.getJailName()).getMaxPoint().toVector().clone();
        Vector point = player.getLocation().toVector().clone();

        if(Util.isInsideAB(point, v1, v2)) {
            player.sendMessage(ChatColor.AQUA + Lang.text("creation.cell"));
            player.sendMessage(ChatColor.GREEN + Lang.text("creation.signs"));
            player.sendMessage(ChatColor.AQUA + "----------------------------------------");

            cp.setTeleportIn(player.getLocation());
            cp.nextStep();
        }else {
            player.sendMessage(ChatColor.RED + Lang.text("creation.cell"));
            player.sendMessage(ChatColor.RED + Lang.text("creation.inside"));
            player.sendMessage(ChatColor.RED + "----------------------------------------");
        }
    }

    /** Applies the second step, which is adding signs to the cell. */
    private void secondStep(JailManager jm, CreationPlayer cp, Player player, Block block) {
        if (block.getState() instanceof Sign) {
            if(com.graywolf336.jail.CellResources.signRegistered(jm, block.getLocation())) {
                player.sendMessage(ChatColor.RED + Lang.text("creation.signused")); return;
            }
            cp.addSign(new SimpleLocation(block.getLocation()));

            //Set the sign's first text
            Sign sign = (Sign) block.getState();
            com.graywolf336.jail.SignProtection.protect(sign);
            sign.setLine(0, "");
            sign.setLine(1, Lang.CELLEMPTYSIGN.get());
            sign.setLine(2, "");
            sign.setLine(3, "");
            sign.update();

            player.sendMessage(ChatColor.GREEN + Lang.text("creation.signadded"));
        }else {
            player.sendMessage(ChatColor.AQUA + Lang.text("creation.cell"));
            player.sendMessage(ChatColor.GREEN + Lang.text("creation.chest"));
            player.sendMessage(ChatColor.AQUA + "----------------------------------------");
            cp.nextStep();
        }
    }

    /** Applies the third step, which is adding a chest or select not to have a chest. */
    private void thirdStep(JailManager jm, CreationPlayer cp, Player player, Block block) {
        if (block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST) {
            if(((Chest) block.getState()).getInventory().getSize() >= 40) {
                if(new com.graywolf336.jail.CellChestProtection(jm.getPlugin()).findCell(((Chest) block.getState()).getInventory()) != null) {
                    player.sendMessage(ChatColor.RED + Lang.text("creation.chestused")); return;
                }
                cp.setChestLocation(new SimpleLocation(block.getLocation()));
                player.sendMessage(ChatColor.AQUA + Lang.text("creation.cell"));
                player.sendMessage(ChatColor.GREEN + Lang.text("creation.chestselected"));
                player.sendMessage(ChatColor.AQUA + "----------------------------------------");
            }else {
                player.sendMessage(ChatColor.RED + Lang.text("creation.cell"));
                player.sendMessage(ChatColor.RED + Lang.text("creation.doublechest"));
                player.sendMessage(ChatColor.RED + "----------------------------------------");
                return;
            }
        }else {
            player.sendMessage(ChatColor.AQUA + Lang.text("creation.cell"));
            player.sendMessage(ChatColor.RED + Lang.text("creation.nochest"));
            player.sendMessage(ChatColor.AQUA + "----------------------------------------");
        }

        finalStep(jm, cp, player);
    }

    private void finalStep(JailManager jm, CreationPlayer cp, Player player) {
        Jail j = jm.getJail(cp.getJailName());
        Cell c = new Cell(cp.getCellName());

        c.addAllSigns(cp.getSigns());
        c.setTeleport(cp.getTeleportInSL());

        if(cp.getChestLocation() != null)
            c.setChestLocation(cp.getChestLocation());

        try { j.addCell(c, true); }
        catch(IllegalArgumentException conflict) { player.sendMessage(ChatColor.RED + Lang.text("creation.conflict")); return; }

        jm.removeCellCreationPlayer(player.getName());
        jm.addCreatingCell(player.getName(), j.getName(), "cell_n" + (j.getCellCount() + 1));

        player.sendMessage(ChatColor.AQUA + Lang.text("creation.cell"));
        player.sendMessage(ChatColor.GREEN + Lang.text("creation.nextcell", jm.getCellCreationPlayer(player.getName()).getCellName()));
        player.sendMessage(ChatColor.AQUA + "----------------------------------------");
    }
}
