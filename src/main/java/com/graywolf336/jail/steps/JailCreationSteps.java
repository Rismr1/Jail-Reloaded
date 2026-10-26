package com.graywolf336.jail.steps;

import com.graywolf336.jail.enums.Lang;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.graywolf336.jail.JailManager;
import com.graywolf336.jail.Util;
import com.graywolf336.jail.beans.CreationPlayer;
import com.graywolf336.jail.beans.Jail;

/**
 * Class for stepping a player through the Jail creation process, instance is stored in {@link JailManager}.
 * 
 * @author graywolf336
 * @since 3.0.0
 * @version 1.1.1
 */
public class JailCreationSteps {

    /** Sends the Jail Creation message for starting out. */
    public void startStepping(Player player) {
        player.sendMessage(ChatColor.AQUA + Lang.text("creation.zone"));
        player.sendMessage(ChatColor.GREEN + Lang.text("creation.start"));
        player.sendMessage(ChatColor.AQUA + "--------------------------------------");

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
     * Applies the next step in the Jail Creation process that involves a location, null if no location is needed.
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
                firstStep(cp, player, location);
                break;
            case 2:
                secondStep(cp, player, location);
                break;
            case 3:
                thirdStep(cp, player);
                break;
            case 4:
                fourthStep(jm, cp, player);
                break;
            default:
                player.sendMessage(ChatColor.RED + Lang.text("creation.failed"));
                jm.removeJailCreationPlayer(player.getName());
                break;
        }
    }

    /** Applies the first step, which is setting the first corner. */
    private void firstStep(CreationPlayer cp, Player p, Location location) {
        p.sendMessage(ChatColor.AQUA + Lang.text("creation.zone"));
        p.sendMessage(ChatColor.GREEN + Lang.text("creation.second"));
        p.sendMessage(ChatColor.AQUA + "---------------------------------------");

        cp.setCornerOne(location);
        cp.nextStep();
    }

    /** Applies the second step, which is setting the second corner. */
    private void secondStep(CreationPlayer cp, Player p, Location location) {
        p.sendMessage(ChatColor.AQUA + Lang.text("creation.zone"));
        p.sendMessage(ChatColor.GREEN + Lang.text("creation.arrival"));
        p.sendMessage(ChatColor.AQUA + "----------------------------------------");

        cp.setCornerTwo(location);
        cp.nextStep();
    }

    /** Applies the third step, which is setting the teleport in location. */
    private void thirdStep(CreationPlayer cp, Player p) {

        int[] p1 = cp.getCornerOne();
        int[] p2 = cp.getCornerTwo();
        Vector v1 = new Vector(p1[0], p1[1], p1[2]);
        Vector v2 = new Vector(p2[0], p2[1], p2[2]);
        Vector point = p.getLocation().toVector().clone();

        if(Util.isInsideAB(point, v1, v2)) {
            p.sendMessage(ChatColor.AQUA + Lang.text("creation.zone"));
            p.sendMessage(ChatColor.GREEN + Lang.text("creation.release"));
            p.sendMessage(ChatColor.AQUA + "----------------------------------------");

            cp.setTeleportIn(p.getLocation());
            cp.nextStep();
        } else {
            p.sendMessage(ChatColor.RED + Lang.text("creation.zone"));
            p.sendMessage(ChatColor.RED + Lang.text("creation.inside"));
            p.sendMessage(ChatColor.RED + "----------------------------------------");
        }
    }

    private void fourthStep(JailManager jm, CreationPlayer cp, Player p) {
        int[] p1 = cp.getCornerOne();
        int[] p2 = cp.getCornerTwo();
        Vector v1 = new Vector(p1[0], p1[1], p1[2]);
        Vector v2 = new Vector(p2[0], p2[1], p2[2]);
        Vector point = p.getLocation().toVector().clone();

        if(Util.isInsideAB(point, v1, v2)) {
            p.sendMessage(ChatColor.RED + Lang.text("creation.zone"));
            p.sendMessage(ChatColor.RED + Lang.text("creation.outside"));
            p.sendMessage(ChatColor.RED + "----------------------------------------");
        }else {
            cp.setTeleportFree(p.getLocation());

            finalStep(jm, cp, p);
        }
    }

    private void finalStep(JailManager jm, CreationPlayer cp, Player p) {
        Jail jail = new Jail(jm.getPlugin(), cp.getJailName());

        jail.setMinPoint(cp.getCornerOne());
        jail.setMaxPoint(cp.getCornerTwo());
        jail.setTeleportIn(cp.getTeleportInSL().getLocation());
        jail.setTeleportFree(cp.getTeleportFreeSL().getLocation());

        jm.addJail(jail, true);

        p.sendMessage(ChatColor.GREEN + Lang.text("creation.created", jail.getName()));

        jm.removeJailCreationPlayer(p.getName());
    }
}
