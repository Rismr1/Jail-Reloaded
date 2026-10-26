package com.graywolf336.jail;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.*;
import org.bukkit.configuration.file.YamlConfiguration;
import com.graywolf336.jail.enums.Lang;

/** Server-wide UTF-8 locales. Administrator overrides never replace bundled defaults. */
public final class LocaleManager {
    public static final List<String> CODES = List.of("en", "fi", "de", "es", "fr", "it", "ja", "ko", "nl", "pl", "pt_br", "ru", "sv", "uk", "zh_cn", "zh_hk", "zh_tw");
    private static final Pattern TOKEN = Pattern.compile("%[a-zA-Z0-9_]+%");
    private LocaleManager() { }

    public static String normalize(String code) {
        String value = code == null ? "en" : code.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (!value.matches("[a-z]{2}(?:_[a-z]{2})?")) return "en";
        return value;
    }

    public static YamlConfiguration bundled(String code) {
        try (InputStream stream = LocaleManager.class.getResourceAsStream("/locales/" + code + ".yml")) {
            if (stream == null) throw new IllegalArgumentException("Unknown bundled locale: " + code);
            YamlConfiguration result = new YamlConfiguration();
            result.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return result;
        } catch (Exception failure) { throw new IllegalStateException("Cannot read bundled locale " + code, failure); }
    }

    public static Set<String> placeholders(String text) {
        Set<String> result = new TreeSet<>();
        Matcher matcher = TOKEN.matcher(text);
        while (matcher.find()) result.add(matcher.group());
        return result;
    }

    /** A missing, mistyped, or damaged entry falls back individually. Empty strings intentionally hide messages. */
    public static YamlConfiguration merge(YamlConfiguration defaults, YamlConfiguration custom) {
        YamlConfiguration result = new YamlConfiguration();
        for (String key : defaults.getKeys(true)) {
            if (!defaults.isString(key)) continue;
            String base = defaults.getString(key);
            Object override = custom.get(key);
            result.set(key, override instanceof String && (((String) override).isEmpty()
                    || placeholders(base).equals(placeholders((String) override))) ? override : base);
        }
        return result;
    }

    public static void load(JailMain plugin) {
        File directory = new File(plugin.getDataFolder(), "locales");
        for (String code : CODES) {
            File file = new File(directory, code + ".yml");
            if (file.exists()) continue;
            try (InputStream input = LocaleManager.class.getResourceAsStream("/locales/" + code + ".yml")) {
                Files.createDirectories(directory.toPath());
                Files.copy(input, file.toPath());
            } catch (IOException failure) { plugin.getLogger().warning("Could not install locale " + code + ": " + failure.getMessage()); }
        }
        String code = normalize(plugin.getConfig().getString("system.language", "en"));
        YamlConfiguration base = CODES.contains(code) ? bundled(code) : bundled("en");
        File file = new File(directory, code + ".yml");
        YamlConfiguration custom = new YamlConfiguration();
        if (file.isFile()) {
            try {
                custom.options().parseComments(true);
                try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) { custom.load(reader); }
                boolean missing = false;
                for (String key : base.getKeys(true)) {
                    if (base.isString(key) && !custom.contains(key)) {
                        custom.set(key, base.getString(key));
                        missing = true;
                    } else if (base.isString(key) && (!custom.isString(key)
                            || (!custom.getString(key).isEmpty() && !placeholders(base.getString(key)).equals(placeholders(custom.getString(key)))))) {
                        plugin.getLogger().warning("Invalid locale entry " + code + ": " + key + "; using bundled wording for this entry.");
                    }
                }
                if (missing) {
                    java.nio.file.Path temporary = Files.createTempFile(directory.toPath(), ".locale-", ".tmp");
                    try {
                        Files.writeString(temporary, custom.saveToString(), StandardCharsets.UTF_8);
                        try { Files.move(temporary, file.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
                        catch (java.nio.file.AtomicMoveNotSupportedException unsupported) { Files.move(temporary, file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
                    } finally { Files.deleteIfExists(temporary); }
                }
            }
            catch (Exception failure) {
                plugin.getLogger().warning("Cannot read locale " + code + "; using bundled defaults. File preserved: " + failure.getMessage());
                custom = new YamlConfiguration();
            }
        } else if (!CODES.contains(code)) plugin.getLogger().warning("Locale " + code + " not found; using English.");
        Lang.setFile(merge(base, custom));
        plugin.debug("Loaded locale: " + code);
    }

    /** The old English defaults follow the locale; explicit administrator wording stays custom. */
    public static String configuredLabel(JailMain plugin, String path, String key, String legacy) {
        String configured = plugin.getConfig().getString(path, "@locale");
        return configured.equals("@locale") || configured.equals(legacy) ? Lang.text(key)
                : org.bukkit.ChatColor.translateAlternateColorCodes('&', configured);
    }
}
