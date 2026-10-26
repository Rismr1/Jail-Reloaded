package com.graywolf336.jail.command.subcommands;

import com.graywolf336.jail.enums.Lang;
import java.util.Collections;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.graywolf336.jail.JailManager;
import com.graywolf336.jail.command.Command;
import com.graywolf336.jail.command.CommandInfo;

@CommandInfo(
        maxArgs = 1,
        minimumArgs = 1,
        needsPlayer = true,
        pattern = "create",
        permission = "jail.command.jailcreate",
        usage = "/jail create [name]"
        )
public class JailCreateCommand implements Command {

    public boolean execute(JailManager jm, CommandSender sender, String... args) {
        Player player = (Player) sender;
        String name = player.getName();
        String jail = args[1];

        //Check if the player is currently creating something else
        if(jm.isCreatingSomething(name)) {
            String message = jm.getStepMessage(name);
            if(!message.isEmpty()) {
                player.sendMessage(ChatColor.RED + message);
            }else {
                player.sendMessage(ChatColor.RED + Lang.text("creation.busy"));
            }
        }else {
            if(jm.isValidJail(jail)) {
                player.sendMessage(ChatColor.RED + Lang.text("creation.jailexists", jail));
            }else {
                if(jm.addCreatingJail(name, jail)) {
                    jm.getJailCreationSteps().startStepping(player);
                }else {
                    player.sendMessage(ChatColor.RED + Lang.text("creation.failed"));
                }
            }
        }

        return true;
    }

    public List<String> provideTabCompletions(JailManager jm, CommandSender sender, String... args) throws Exception {
        //Creating a jail shouldn't provide tab completion
        return Collections.emptyList();
    }
}
