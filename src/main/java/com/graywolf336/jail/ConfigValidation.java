package com.graywolf336.jail;

import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import com.graywolf336.jail.enums.Settings;

/** Validate a complete candidate before replacing working runtime managers. */
public final class ConfigValidation {
    private ConfigValidation() { }

    public static void validate(FileConfiguration config) throws Exception {
        String storage = config.getString("storage.type", "sqlite").toLowerCase(Locale.ROOT);
        if(!java.util.Set.of("sqlite", "mysql", "flatfile").contains(storage)) throw new IllegalArgumentException("Unknown storage.type: " + storage);
        if(!config.getString("storage.mysql.prefix", "jail_").matches("[A-Za-z0-9_]+")) throw new IllegalArgumentException("Storage prefix must contain only letters, numbers, and underscores");
        for(Settings setting : new Settings[] {Settings.MAXAFKTIME, Settings.BLOCKBREAKPENALTY, Settings.BLOCKPLACEPENALTY,
                Settings.COMMANDPENALTY, Settings.CROPTRAMPLINGPENALTY, Settings.MOVEPENALTY,
                Settings.PREVENTINTERACTIONBLOCKSPENALTY, Settings.PREVENTINTERACTIONITEMSPENALTY}) {
            if(Util.getTime(config.getString(setting.getPath())) < 0) throw new IllegalArgumentException(setting.getPath() + " must not be negative");
        }
        if(Util.getTime(config.getString(Settings.JAILVOTETIMER.getPath())) < 1000) throw new IllegalArgumentException("Vote timer must be at least one second");
        Util.getTime(config.getString(Settings.JAILVOTETIME.getPath()));
        Util.getTime(config.getString(Settings.DEFAULTTIME.getPath()));
        org.bukkit.GameMode.valueOf(config.getString(Settings.JAILEDGAMEMODE.getPath()).toUpperCase(Locale.ROOT));
        for(Settings setting : new Settings[] {Settings.JAILPAYPRICEPERMINUTE, Settings.JAILPAYPRICEINFINITE}) {
            double price = config.getDouble(setting.getPath());
            if(!Double.isFinite(price) || price < 0) throw new IllegalArgumentException(setting.getPath() + " must be finite and nonnegative");
        }
        material(config.getString(Settings.JAILPAYITEM.getPath()));
        for(Settings setting : new Settings[] {Settings.CLOTHINGBOOTS, Settings.CLOTHINGLEGS, Settings.CLOTHINGCHEST, Settings.CLOTHINGHELMET}) {
            String[] parts = config.getString(setting.getPath(), "air").split("~", -1);
            material(parts[0]);
            if(parts.length > 2) throw new IllegalArgumentException("Invalid clothing format: " + setting.getPath());
            if(parts.length == 2) {
                String[] rgb = parts[1].split(",", -1);
                if(rgb.length != 3) throw new IllegalArgumentException("Clothing colors require red,green,blue");
                org.bukkit.Color.fromRGB(Integer.parseInt(rgb[0]), Integer.parseInt(rgb[1]), Integer.parseInt(rgb[2]));
            }
        }
        int min = config.getInt(Settings.FOODCONTROLMIN.getPath()), max = config.getInt(Settings.FOODCONTROLMAX.getPath());
        if(min < 0 || max > 20 || min > max) throw new IllegalArgumentException("Food limits require 0 <= min <= max <= 20");
        for(String entry : config.getStringList(Settings.JAILSTICKSTICKS.getPath())) validateStick(entry);
    }

    public static String[] validateStick(String entry) throws Exception {
        String[] parts = entry.split(",", -1);
        if(parts.length < 4 || parts.length > 5) throw new IllegalArgumentException("Jail stick requires material,time,jail,reason[,health]: " + entry);
        material(parts[0]);
        try { Util.getTime(parts[1]); }
        catch(Exception invalid) { throw new IllegalArgumentException("Invalid jail stick duration: " + entry, invalid); }
        if(parts.length == 5) {
            double health = Double.parseDouble(parts[4]);
            if(!Double.isFinite(health) || health < -1) throw new IllegalArgumentException("Invalid jail stick health");
        }
        return parts;
    }

    private static void material(String name) {
        Material material = name == null ? null : Material.matchMaterial(name);
        if(material == null || (!material.isAir() && !material.isItem())) throw new IllegalArgumentException("Unknown/non-item material: " + name);
    }
}
