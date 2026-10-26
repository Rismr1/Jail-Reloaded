package com.graywolf336.jail.command.subcommands;

import com.graywolf336.jail.enums.Lang;
import java.util.Collections;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.graywolf336.jail.JailManager;
import com.graywolf336.jail.beans.Jail;
import com.graywolf336.jail.command.Command;
import com.graywolf336.jail.command.CommandInfo;

@CommandInfo(
        maxArgs = 2,
        minimumArgs = 1,
        needsPlayer = true,
        pattern = "createcells|createcell|cellcreate|cellscreate|cc",
        permission = "jail.command.jailcreatecells",
        usage = "/jail createcell [jail] (cellname)"
        )
public class JailCreateCellCommand implements Command {

    public boolean execute(JailManager jm, CommandSender sender, String... args) {
        Player player = (Player) sender;
        String name = player.getName();
        String jail = args[1];
        String cell = "";

        //Only get the cell name they provide if they provide it
        if(args.length >= 3) {
            cell = args[2];
        }

        //Check if the player is currently creating something else
        if(jm.isCreatingSomething(name)) {
            String message = jm.getStepMessage(name);
            if(!message.isEmpty()) {
                player.sendMessage(ChatColor.RED + message);
            }else {
                player.sendMessage(ChatColor.RED + Lang.text("creation.busy"));
            }
        }else {
            //Not creating anything, so let them create some cells.
            if(jm.isValidJail(jail)) {
                Jail j = jm.getJail(jail);

                //If they didn't provide a cell name, let's provide one ourself.
                if(cell.isEmpty()) cell = "cell_n" + (j.getCellCount() + 1);

                if(j.getCell(cell) == null) {
                    if(jm.addCreatingCell(name, jail, cell)) {
                        jm.getCellCreationSteps().startStepping(player);
                    }else {
                        player.sendMessage(ChatColor.RED + Lang.text("creation.failed"));
                    }
                }else {
                    player.sendMessage(ChatColor.RED + Lang.text("creation.cellexists", cell));
                }
            }else {
                player.sendMessage(ChatColor.RED + Lang.NOJAIL.get(jail));
            }
        }

        return true;
    }

    public List<String> provideTabCompletions(JailManager jm, CommandSender sender, String... args) throws Exception {
        //We shouldn't provide when they want a cell name
        if(args.length >= 3) return Collections.emptyList();
        
        return jm.getJailsByPrefix(args.length == 2 ? args[1] : "");
    }
}
