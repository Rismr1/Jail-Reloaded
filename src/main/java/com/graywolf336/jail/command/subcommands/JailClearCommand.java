package com.graywolf336.jail.command.subcommands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.util.StringUtil;

import com.graywolf336.jail.JailManager;
import com.graywolf336.jail.beans.ConfirmPlayer;
import com.graywolf336.jail.beans.Jail;
import com.graywolf336.jail.command.Command;
import com.graywolf336.jail.command.CommandInfo;
import com.graywolf336.jail.enums.Confirmation;
import com.graywolf336.jail.enums.Lang;

@CommandInfo(
        maxArgs = 2,
        minimumArgs = 0,
        needsPlayer = false,
        pattern = "clear|clearforce",
        permission = "jail.command.jailclear",
        usage = "/jail clear (-f) (jail)"
        )
public class JailClearCommand implements Command {
    public boolean execute(JailManager jm, CommandSender sender, String... args) {
        boolean force = args[0].equalsIgnoreCase("clearforce");
        String target = null;

        //Check if we need to forcefully clear something
        for(int i = 1; i < args.length; i++) {
            String s = args[i];
            if(s.equalsIgnoreCase("-f") || s.equalsIgnoreCase("-force")) force = true;
            else if(target == null && !s.startsWith("-")) target = s;
            else return false;
        }
        if(target != null && !jm.isValidJail(target)) { sender.sendMessage(Lang.NOJAIL.get(target)); return true; }
        if(force && !sender.hasPermission("jail.command.jailclearforce")) { sender.sendMessage(Lang.NOPERMISSION.get()); return true; }
        // Store the resolved target rather than reparsing flags during confirmation.
        String[] resolved = target == null ? new String[] {"clear"} : new String[] {"clear", target};

        if(jm.isConfirming(sender.getName())) {
            sender.sendMessage(Lang.ALREADY.get());
        }else if(force && sender.hasPermission("jail.command.jailclearforce")) {
            jm.addConfirming(sender.getName(), new ConfirmPlayer(sender.getName(), resolved, Confirmation.CLEARFORCE));
            sender.sendMessage(Lang.START.get());
        }else {
            jm.addConfirming(sender.getName(), new ConfirmPlayer(sender.getName(), resolved, Confirmation.CLEAR));
            sender.sendMessage(Lang.START.get());
        }

        return true;
    }

    public List<String> provideTabCompletions(JailManager jm, CommandSender sender, String... args) throws Exception {
        List<String> results = new ArrayList<String>();
        
        for(Jail j : jm.getJails()) {
            if((args.length == 2 && StringUtil.startsWithIgnoreCase(j.getName(), args[1]))
                    || (args.length == 3 && StringUtil.startsWithIgnoreCase(j.getName(), args[2]))) {
                results.add(j.getName());
            }else {
                results.add(j.getName());
            }
        }
        
        Collections.sort(results);
        
        return results;
    }
}
