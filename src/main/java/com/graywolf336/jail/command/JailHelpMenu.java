package com.graywolf336.jail.command;

import com.graywolf336.jail.enums.Lang;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;

/** MailboxGUI-style chat help, filtered against the sender's actual permissions. */
public final class JailHelpMenu {
    public enum View { ALL, PLAYERS, ADMIN }
    private static final int LINES_PER_PAGE = 8;
    private static final String BORDER = ChatColor.DARK_GRAY + "" + ChatColor.STRIKETHROUGH
            + "------------------------------------------------";
    private static final List<Entry> ENTRIES = new ArrayList<>();
    static {
        player("/jail help [players] [page]", "help.entry0", "jail.command.jailhelp");
        player("/jail status", "help.entry1", "jail.usercmd.jailstatus");
        player("/jail pay [amount] [player]", "help.entry2", "jail.usercmd.jailpay");
        player("/jail vote {player} [yes|no]", "help.entry3", "jail.usercmd.jailvote");
        player("/jail stick", "help.entry4", "jail.usercmd.jailstick");
        admin("/jail help admin [page]", "help.entry5", "jail.command.jailhelp");
        admin("/jail {player} [-t time] [-j jail] [-c cell] [-r reason]", "help.entry6", "jail.command.jail");
        admin("/unjail {player}", "help.entry7", "jail.command.unjail");
        admin("/unjailforce {player}", "help.entry8", "jail.command.unjailforce");
        admin("/jail create {name}", "help.entry9", "jail.command.jailcreate");
        admin("/jail createcell {jail} [cell]", "help.entry10", "jail.command.jailcreatecells");
        admin("/jail stop", "help.entry11", "jail.command.jailstop");
        admin("/jail list [jail]", "help.entry12", "jail.command.jaillist");
        admin("/jail listcells {jail}", "help.entry13", "jail.command.jaillistcells");
        admin("/jail check {player}", "help.entry14", "jail.command.jailcheck");
        admin("/jail time {add|remove|set|show} {player} [time]", "help.entry15", "jail.command.jailtime");
        admin("/jail mute {player}", "help.entry16", "jail.command.jailmute");
        admin("/jail transfer -p {player} [-j jail] [-c cell]", "help.entry17", "jail.command.jailtransfer");
        admin("/jail transferall {current} {target}", "help.entry18", "jail.command.jailtransferall");
        admin("/jail telein {jail} [player]", "help.entry19", "jail.command.jailtelein");
        admin("/jail teleout {jail} [player]", "help.entry20", "jail.command.jailteleout");
        admin("/handcuff {player}", "help.entry21", "jail.command.handcuff");
        admin("/unhandcuff {player}", "help.entry22", "jail.command.handcuff");
        admin("/jail record {player} [display]", "help.entry23", "jail.command.jailrecord");
        admin("/jail signs {clean|refresh|update|verify} {jail} [cell]", "help.entry24", "jail.command.jailsigns");
        admin("/jail clear [-f] [jail]", "help.entry25", "jail.command.jailclear");
        admin("/jail deletecell {jail} {cell}", "help.entry26", "jail.command.jaildeletecell");
        admin("/jail deletecells {jail}", "help.entry27", "jail.command.jaildeletecells");
        admin("/jail delete {jail}", "help.entry28", "jail.command.jaildelete");
        admin("/jail confirm", "help.entry29", "");
        admin("/jail reload", "help.entry30", "jail.command.jailreload");
        admin("/jail version", "help.entry31", "jail.command.jailversion");
        admin("/togglejaildebug", "help.entry32", "jail.command.toggledebug");
    }

    private JailHelpMenu() { }
    private static void player(String syntax, String description, String permission) {
        ENTRIES.add(new Entry(syntax, description, permission, false));
    }
    private static void admin(String syntax, String description, String permission) {
        ENTRIES.add(new Entry(syntax, description, permission, true));
    }

    public static boolean isAdmin(CommandSender sender) {
        // Public help and the permission-free confirmation command do not make someone staff.
        return sender.isOp() || ENTRIES.stream().anyMatch(entry -> entry.admin
                && !entry.permission.isEmpty() && !entry.permission.equals("jail.command.jailhelp")
                && sender.hasPermission(entry.permission));
    }

    public static List<List<String>> pages(CommandSender sender, View view) {
        List<List<String>> pages = new ArrayList<>();
        List<String> page = new ArrayList<>();
        pages.add(page);
        Boolean section = null;
        boolean administrator = isAdmin(sender);
        for(Entry entry : ENTRIES) {
            if(entry.admin && (!administrator || view == View.PLAYERS)) continue;
            if(!entry.admin && view == View.ADMIN) continue;
            if(!entry.permission.isEmpty() && !sender.hasPermission(entry.permission)) continue;
            boolean newSection = section == null || section != entry.admin;
            int required = newSection ? (page.isEmpty() ? 2 : 3) : 1;
            if(page.size() + required > LINES_PER_PAGE) {
                page = new ArrayList<>(); pages.add(page); newSection = true;
            }
            if(newSection) {
                if(!page.isEmpty()) page.add(" ");
                page.add((entry.admin ? ChatColor.RED : ChatColor.YELLOW)
                        + (entry.admin ? Lang.text("help.admin") : Lang.text("help.players")));
            }
            page.add(ChatColor.GRAY + "  " + syntax(entry.syntax) + ChatColor.DARK_GRAY + " - "
                    + ChatColor.GRAY + Lang.text(entry.description));
            section = entry.admin;
        }
        if(page.isEmpty()) page.add(ChatColor.GRAY + Lang.text("help.empty"));
        return pages;
    }

    public static void send(CommandSender sender, String version, View view, int requestedPage) {
        List<List<String>> pages = pages(sender, view);
        int page = Math.min(Math.max(1, requestedPage), pages.size());
        String command = CommandLocale.syntax("/jail help" + (view == View.PLAYERS ? " players" : view == View.ADMIN ? " admin" : ""));
        sender.sendMessage(BORDER);
        sender.sendMessage(" ");
        sender.sendMessage(center(ChatColor.GOLD + "" + ChatColor.BOLD + "Jail-Reloaded"));
        sender.sendMessage(center(ChatColor.GOLD + "v" + version));
        sender.sendMessage(center(ChatColor.GRAY + Lang.text("help.credit", ChatColor.WHITE + "graywolf336")));
        sender.sendMessage(center(ChatColor.YELLOW + "" + ChatColor.BOLD + Lang.text("help.title")));
        sender.sendMessage(" ");
        for(String line : pages.get(page - 1)) sender.sendMessage(line);
        sender.sendMessage(" ");
        sender.sendMessage(ChatColor.WHITE + Lang.text("help.page", String.valueOf(page), String.valueOf(pages.size()), command + " " + ChatColor.RED + "{" + Lang.text("syntax.arguments.page") + "}"));
        if(pages.size() > 1) {
            if(sender instanceof Player) {
                ((Player) sender).spigot().sendMessage(navigation(page, pages.size(), command));
            } else {
                if(page > 1) sender.sendMessage(ChatColor.AQUA + Lang.text("help.previous") + ": " + command + " " + (page - 1));
                if(page < pages.size()) sender.sendMessage(ChatColor.AQUA + Lang.text("help.next") + ": " + command + " " + (page + 1));
            }
        }
        sender.sendMessage(BORDER);
    }

    public static TextComponent navigation(int page, int pages, String command) {
        TextComponent result = new TextComponent("");
        if(page > 1) result.addExtra(link(Lang.text("help.previous"), command + " " + (page - 1)));
        if(page > 1 && page < pages) result.addExtra("   ");
        if(page < pages) result.addExtra(link(Lang.text("help.next"), command + " " + (page + 1)));
        return result;
    }

    private static TextComponent link(String label, String command) {
        TextComponent result = new TextComponent("");
        result.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        int[] stops = {0x409DBB, 0xD7F3F8, 0x409DBB};
        for(int i = 0; i < label.length(); i++) {
            double position = (double) i / Math.max(1, label.length() - 1) * 2;
            int segment = Math.min(1, (int) position);
            double fraction = position - segment;
            int color = 0;
            for(int shift : new int[] {16, 8, 0}) {
                int start = stops[segment] >> shift & 255, end = stops[segment + 1] >> shift & 255;
                color |= (int) Math.round(start + (end - start) * fraction) << shift;
            }
            TextComponent character = new TextComponent(String.valueOf(label.charAt(i)));
            character.setColor(net.md_5.bungee.api.ChatColor.of(String.format(Locale.ROOT, "#%06X", color)));
            character.setBold(true);
            result.addExtra(character);
        }
        return result;
    }

    private static String syntax(String text) {
        return ChatColor.WHITE + CommandLocale.syntax(text).replaceAll("(\\{[^}]+})", ChatColor.RED + "$1" + ChatColor.WHITE)
                .replaceAll("(\\[[^]]+])", ChatColor.LIGHT_PURPLE + "$1" + ChatColor.WHITE);
    }
    private static String center(String text) {
        return " ".repeat(Math.max(0, (38 - ChatColor.stripColor(text).length()) / 2)) + text;
    }
    private static final class Entry {
        final String syntax, description, permission;
        final boolean admin;
        Entry(String syntax, String description, String permission, boolean admin) {
            this.syntax = syntax; this.description = description; this.permission = permission; this.admin = admin;
        }
    }
}
