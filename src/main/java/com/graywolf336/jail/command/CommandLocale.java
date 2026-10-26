package com.graywolf336.jail.command;

import java.util.*;
import java.util.regex.*;
import com.graywolf336.jail.enums.Lang;

/** Localizes only command grammar, never player names, reasons, flags, or root commands. */
public final class CommandLocale {
    public static final List<String> COMMANDS = List.of("help", "status", "pay", "vote", "stick", "create", "createcell", "stop", "list", "listcells", "check", "time", "mute", "transfer", "transferall", "telein", "teleout", "record", "signs", "clear", "deletecell", "deletecells", "delete", "confirm", "reload", "version");
    public static final List<String> OPTIONS = List.of("players", "admin", "add", "remove", "set", "show", "clean", "refresh", "update", "verify", "yes", "no");
    private static final Map<String, String> ENGLISH_ALIASES = Map.ofEntries(
            Map.entry("h", "help"), Map.entry("s", "status"), Map.entry("v", "vote"),
            Map.entry("createcells", "createcell"), Map.entry("cellcreate", "createcell"), Map.entry("cellscreate", "createcell"), Map.entry("cc", "createcell"),
            Map.entry("l", "list"), Map.entry("lc", "listcells"), Map.entry("t", "time"), Map.entry("m", "mute"),
            Map.entry("trans", "transfer"), Map.entry("transall", "transferall"), Map.entry("ta", "transferall"),
            Map.entry("teleportin", "telein"), Map.entry("teleportout", "teleout"), Map.entry("r", "record"),
            Map.entry("dc", "deletecell"), Map.entry("dcs", "deletecells"), Map.entry("d", "delete"),
            Map.entry("con", "confirm"), Map.entry("ver", "version"), Map.entry("clearforce", "clearforce"));
    private CommandLocale() { }

    private static String rawWord(String canonical) {
        return Lang.getFile() == null ? canonical : Lang.getFile().getString("language.syntax.words." + canonical, canonical);
    }

    public static String word(String canonical) {
        String value = rawWord(canonical);
        if (!value.matches("[\\p{L}\\p{N}_-]+")) return canonical;
        List<String> domain = COMMANDS.contains(canonical) ? COMMANDS : OPTIONS;
        String lower = value.toLowerCase(Locale.ROOT);
        if (COMMANDS.contains(canonical) && ENGLISH_ALIASES.containsKey(lower)
                && !ENGLISH_ALIASES.get(lower).equals(canonical)) return canonical;
        for (String token : domain) {
            if (token.equals(canonical)) continue;
            if (token.equalsIgnoreCase(value) || rawWord(token).equalsIgnoreCase(value)) return canonical;
        }
        return value;
    }

    private static String resolve(String value, List<String> allowed) {
        // Canonical English always wins if an administrator introduces an alias collision.
        for (String token : allowed) if (token.equalsIgnoreCase(value)) return token;
        for (String token : allowed) if (word(token).equalsIgnoreCase(value)) return token;
        return value;
    }

    private static String command(String value) {
        String english = ENGLISH_ALIASES.get(value.toLowerCase(Locale.ROOT));
        if (english != null) return english;
        String canonical = resolve(value, COMMANDS);
        if (canonical.equalsIgnoreCase("createcells")) return "createcell";
        return canonical;
    }

    private static List<String> options(String command, int position) {
        if (position == 1 && command.equals("help")) return List.of("players", "admin");
        if (position == 1 && command.equals("time")) return List.of("add", "remove", "set", "show");
        if (position == 1 && command.equals("signs")) return List.of("clean", "refresh", "update", "verify");
        if (position == 2 && command.equals("vote")) return List.of("yes", "no");
        return List.of();
    }

    public static String[] canonicalize(String[] original) {
        String[] args = original.clone();
        if (args.length == 0) return args;
        args[0] = command(args[0]);
        String first = args[0].toLowerCase(Locale.ROOT);
        // Resolve option aliases only in their grammatical position.
        // A player called "yes" or a jail called "admin" must keep its name.
        if (first.matches("h")) first = "help";
        if (first.matches("t")) first = "time";
        if (first.matches("v")) first = "vote";
        for (int i = 1; i < args.length; i++) args[i] = resolve(args[i], options(first, i));
        return args;
    }

    public static List<String> completions(String[] original, List<String> canonical) {
        String first = original.length == 0 ? "" : command(original[0]);
        List<String> allowed = original.length <= 1 ? COMMANDS : options(first, original.length - 1);
        String prefix = original.length == 0 ? "" : original[original.length - 1].toLowerCase(Locale.ROOT);
        Set<String> result = new TreeSet<>();
        for (String candidate : canonical) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(prefix)) result.add(candidate);
            String primary = original.length <= 1 ? command(candidate) : candidate;
            if (allowed.contains(primary) && primary.toLowerCase(Locale.ROOT).startsWith(prefix)) result.add(primary);
            if (allowed.contains(primary) && word(primary).toLowerCase(Locale.ROOT).startsWith(prefix)) result.add(word(primary));
        }
        return new ArrayList<>(result);
    }

    public static String[] completionInput(String[] original) {
        String[] args = canonicalize(original);
        if (args.length == 1 || (args.length > 1 && !options(args[0], args.length - 1).isEmpty())) args[args.length - 1] = "";
        return args;
    }

    public static String canonicalLine(String line) {
        String[] words = line.trim().split("\\s+");
        if (words.length < 2 || !(words[0].equalsIgnoreCase("/jail") || words[0].equalsIgnoreCase("/jailreloaded:jail"))) return line;
        return words[0] + " " + String.join(" ", canonicalize(Arrays.copyOfRange(words, 1, words.length)));
    }

    public static String embeddedCommands(String template) {
        Matcher commands = Pattern.compile("/jail [a-z]+(?: %[0-9]+% (?:yes|no))?").matcher(template);
        StringBuffer result = new StringBuffer();
        while (commands.find()) commands.appendReplacement(result, Matcher.quoteReplacement(syntax(commands.group())));
        commands.appendTail(result);
        return result.toString();
    }

    public static String syntax(String syntax) {
        // Keep every root, including /jail, exactly as registered in plugin.yml.
        String value = syntax.replace("A reason for jailing", "reason");
        Matcher command = Pattern.compile("^(/jail) ([a-z]+)(?= |$)").matcher(value);
        boolean subcommand = command.find() && COMMANDS.contains(command.group(2));
        if (subcommand) {
            value = command.replaceFirst(Matcher.quoteReplacement("/jail " + word(command.group(2))));
        }
        Matcher labels = Pattern.compile("[a-zA-Z]+(?:[A-Z][a-z]+)*").matcher(value);
        StringBuffer result = new StringBuffer();
        int grammarStart = subcommand ? value.indexOf(' ', value.indexOf(' ') + 1) : value.indexOf(' ');
        while (labels.find()) {
            String token = labels.group();
            String translated = token;
            if (labels.start() > grammarStart && grammarStart >= 0) {
                if (OPTIONS.contains(token)) translated = word(token);
                else {
                    String key = "syntax.arguments." + token.toLowerCase(Locale.ROOT);
                    if (Lang.getFile() != null && Lang.getFile().isString("language." + key)) translated = Lang.text(key);
                }
            }
            labels.appendReplacement(result, Matcher.quoteReplacement(translated));
        }
        labels.appendTail(result);
        return result.toString();
    }
}
