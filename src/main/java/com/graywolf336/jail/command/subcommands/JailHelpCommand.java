package com.graywolf336.jail.command.subcommands;

import java.util.Collections;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import com.graywolf336.jail.JailManager;
import com.graywolf336.jail.command.Command;
import com.graywolf336.jail.command.CommandInfo;
import com.graywolf336.jail.command.JailHelpMenu;
import com.graywolf336.jail.enums.Lang;

@CommandInfo(
        maxArgs = 2,
        minimumArgs = 0,
        needsPlayer = false,
        pattern = "help|h",
        permission = "jail.command.jailhelp",
        usage = "/jail help [players|admin] [page]"
        )
public class JailHelpCommand implements Command {
    public boolean execute(JailManager jm, CommandSender sender, String... args) throws Exception {
        JailHelpMenu.View view = view(args);
        if(args.length > 2 && view == JailHelpMenu.View.ALL) return false;
        if(view == JailHelpMenu.View.ADMIN && !JailHelpMenu.isAdmin(sender)) {
            sender.sendMessage(Lang.NOPERMISSION.get());
            return true;
        }
        int page = 1;
        if(args.length > 1) {
            String last = args[args.length - 1];
            if(!last.equalsIgnoreCase("admin") && !last.equalsIgnoreCase("players") && !last.equalsIgnoreCase("player")) {
                try { page = Integer.parseInt(last); }
                catch(NumberFormatException invalidPage) { return false; }
            }
        }
        JailHelpMenu.send(sender, jm.getPlugin().getDescription().getVersion(), view, page);
        return true;
    }

    public List<String> provideTabCompletions(JailManager jm, CommandSender sender, String... args) throws Exception {
        if(args.length > 3) return Collections.emptyList();
        java.util.ArrayList<String> choices = new java.util.ArrayList<>();
        if(args.length == 2) {
            choices.add("players");
            if(JailHelpMenu.isAdmin(sender)) choices.add("admin");
        }
        for(int page = 1; page <= JailHelpMenu.pages(sender, view(args)).size(); page++) choices.add(String.valueOf(page));
        String prefix = args[args.length - 1].toLowerCase(java.util.Locale.ROOT);
        choices.removeIf(value -> !value.startsWith(prefix));
        return choices;
    }

    private JailHelpMenu.View view(String[] args) {
        if(args.length > 1 && args[1].equalsIgnoreCase("admin")) return JailHelpMenu.View.ADMIN;
        if(args.length > 1 && (args[1].equalsIgnoreCase("players") || args[1].equalsIgnoreCase("player"))) return JailHelpMenu.View.PLAYERS;
        return JailHelpMenu.View.ALL;
    }
}
