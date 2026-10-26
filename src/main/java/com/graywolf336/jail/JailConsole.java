package com.graywolf336.jail;

import java.util.*;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import com.graywolf336.jail.beans.*;
import com.graywolf336.jail.enums.Settings;

/** Concise console output; all prefixes/timestamps are supplied by Bukkit's logger. */
public final class JailConsole {
    private final JailMain plugin;
    private final Map<UUID, String[]> releases = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Long> denials = new java.util.concurrent.ConcurrentHashMap<>();
    public JailConsole(JailMain plugin) { this.plugin = plugin; }

    public static String clean(String text) {
        if(text == null) return "unknown";
        String stripped = ChatColor.stripColor(text).replaceAll("\\x1B\\[[0-9;]*[A-Za-z]", "").replaceAll("[\\p{Cntrl}]", " ");
        return stripped.length() > 256 ? stripped.substring(0, 256) + "..." : stripped;
    }

    public static String format(String action, String... fields) {
        StringBuilder line = new StringBuilder(clean(action));
        for(int i = 0; i + 1 < fields.length; i += 2) {
            if(fields[i + 1] == null) continue;
            String value = clean(fields[i + 1]);
            if(fields[i].equals("reason") || value.matches(".*[\\s|=\"\\\\].*"))
                value = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
            line.append(" | ").append(fields[i]).append('=').append(value);
        }
        return line.toString();
    }

    public static String actor(CommandSender sender) {
        return sender == null ? null : sender instanceof ConsoleCommandSender ? "CONSOLE" : sender.getName();
    }

    public static String duration(long millis) {
        if(millis < 0) return "permanent";
        long seconds = millis / 1000;
        if(seconds == 0) return "0s";
        StringBuilder result = new StringBuilder();
        long[] units = {86400, 3600, 60, 1}; String[] names = {"d", "h", "m", "s"};
        for(int i = 0; i < units.length; i++) {
            long amount = seconds / units[i]; seconds %= units[i];
            if(amount > 0) result.append(amount).append(names[i]);
        }
        return result.toString();
    }

    public static String cell(Jail jail, com.graywolf336.jail.interfaces.ICell cell) {
        return jail.getName() + "/" + (cell == null ? "none" : cell.getName());
    }

    public void banner() {
        for(String line : new String[] {
                "       .-----------------------.",
                "       |  |  |  |  |  |  |  | |",
                "       |  |  |  | [o] |  |  | |",
                "       |  |  |  |  |  |  |  | |",
                "       '-----------------------'",
                "          J A I L - R E L O A D E D",
                "                     v" + plugin.getDescription().getVersion(),
                "          Originally By: graywolf336",
                "       Fork By: Rismr1 | ImagineCraft Plugins"}) plugin.getLogger().info(line);
    }

    public void ready(long elapsed) {
        plugin.getLogger().info("Server: " + clean(plugin.getServer().getName()) + " "
                + clean(plugin.getServer().getBukkitVersion()).split("-", 2)[0] + " | API baseline: 1.19");
        String storage = plugin.getConfig().getString("storage.type", "sqlite");
        plugin.getLogger().info("Storage: " + (storage.equalsIgnoreCase("sqlite") ? "SQLite | jail3.sqlite"
                : storage.equalsIgnoreCase("mysql") ? "MySQL" : "Flatfile | data.yml / records.yml"));
        plugin.getLogger().info("Loaded: " + plugin.getJailManager().getJails().size() + " jails | "
                + plugin.getJailManager().getAllCells().size() + " cells | " + plugin.getJailManager().getAllPrisoners().size() + " prisoners");
        plugin.getLogger().info("Ready in " + elapsed + " ms.");
    }

    public void player(String action, Prisoner prisoner, String... fields) {
        if(!plugin.getConfig().getBoolean(Settings.LOGJAILINGTOCONSOLE.getPath(), true)) return;
        String[] all = new String[fields.length + 2]; all[0] = "player"; all[1] = prisoner.getLastKnownName();
        System.arraycopy(fields, 0, all, 2, fields.length);
        plugin.getLogger().info(format(action, all));
        plugin.debug(format("PLAYER", "event", action, "player", prisoner.getLastKnownName(), "uuid", prisoner.getUUID().toString()));
    }

    public void rememberRelease(Prisoner prisoner, CommandSender sender, String cause) {
        releases.put(prisoner.getUUID(), new String[] {actor(sender), cause});
    }

    public void paid(Prisoner prisoner, CommandSender payer, com.graywolf336.jail.interfaces.IJailPayManager manager, double amount) {
        if(amount <= 0) return;
        boolean items = manager.usingItemsForPayment();
        player("PAID", prisoner, "actor", actor(payer), "method", items ? "items" : "currency",
                "amount", items ? Long.toString((long) Math.ceil(amount)) : Double.toString(amount),
                "item", items ? manager.getItemItCost().name() : null);
    }

    public void released(Prisoner prisoner, Jail jail, com.graywolf336.jail.interfaces.ICell cell, CommandSender sender) {
        String[] context = releases.remove(prisoner.getUUID());
        String by = sender != null ? actor(sender) : context == null ? null : context[0];
        String cause = context != null ? context[1] : sender != null ? "admin_release" : "scheduled_release";
        player("RELEASED", prisoner, "actor", by, "cause", cause, "jail", jail.getName(), "cell", cell == null ? "none" : cell.getName());
    }

    public void denied(UUID player, String name, String action, String... fields) {
        if(!plugin.inDebug()) return;
        String key = player + ":" + action;
        long now = System.nanoTime(); Long previous = denials.get(key);
        if(previous != null && now - previous < 5_000_000_000L) return;
        denials.put(key, now);
        plugin.debug(format(action, "player", name, "uuid", player.toString()) + (fields.length == 0 ? "" : format("", fields)));
    }

    public void forget(UUID player) { denials.keySet().removeIf(key -> key.startsWith(player.toString() + ":")); }
}
