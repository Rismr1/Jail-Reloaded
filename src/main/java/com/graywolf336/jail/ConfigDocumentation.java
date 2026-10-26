package com.graywolf336.jail;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

/** Refreshes shipped documentation without resetting the owner's configured values. */
public final class ConfigDocumentation {
    private ConfigDocumentation() { }

    public static void apply(FileConfiguration config, Reader templateReader, String version) {
        YamlConfiguration template = YamlConfiguration.loadConfiguration(templateReader);
        config.options().parseComments(true).copyDefaults(true);
        // Materialize defaults before assigning comments: comments on absent nodes
        // are otherwise lost when Bukkit later copies defaults during serialization.
        for(String path : template.getKeys(true)) {
            if(!template.isConfigurationSection(path) && !config.contains(path, true)) {
                config.set(path, template.get(path));
            }
        }
        config.set("system.updates", null);
        List<String> header = new ArrayList<>(template.options().getHeader());
        header.replaceAll(line -> line != null && line.startsWith("Jail-Reloaded v")
                ? "Jail-Reloaded v" + version + " | Configuration" : line);
        config.options().setHeader(header);
        for(String path : template.getKeys(true)) {
            List<String> comments = template.getComments(path);
            if(!comments.isEmpty()) config.setComments(path, comments);
        }
    }
}
