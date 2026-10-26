package com.graywolf336.jail;

import java.util.List;
import java.util.Locale;
import org.bukkit.Material;

/** Matches names as text, so materials from newer servers remain harmless on older ones. */
public final class InteractionPolicy {
    private InteractionPolicy() { }

    public static boolean blocks(Material material, List<String> configured) {
        if(material == null || material.isAir()) return false;
        String name = material.name().toLowerCase(Locale.ROOT);
        for(String entry : configured) {
            String candidate = entry.trim().toLowerCase(Locale.ROOT);
            if(candidate.startsWith("minecraft:")) candidate = candidate.substring("minecraft:".length());
            if(candidate.equals("*") || candidate.equals(name)) return true;
        }
        return false;
    }
}
