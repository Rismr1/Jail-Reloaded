package com.graywolf336.jail;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.junit.*;
import com.graywolf336.jail.beans.Prisoner;
import com.graywolf336.jail.command.CommandLocale;
import com.graywolf336.jail.enums.Lang;
import com.graywolf336.jail.enums.Settings;
import com.graywolf336.jail.listeners.ProtectionListener;
import test.java.com.graywolf336.jail.util.TestInstanceCreator;

public class LocaleReloadTest {
    private TestInstanceCreator fixture;
    private JailMain plugin;
    @Before public void setup() {
        fixture = new TestInstanceCreator(); assertTrue(fixture.setup()); plugin = fixture.getMain();
    }
    @After public void cleanup() { fixture.tearDown(); Lang.setFile(LocaleManager.bundled("en")); }

    @Test public void loaderInstallsEveryLocaleAndUpgradesTheSelectedFileWithoutOverwritingCustomText() throws Exception {
        for (String code : LocaleManager.CODES) assertTrue(new File(plugin.getDataFolder(), "locales/" + code + ".yml").isFile());
        File selected = new File(plugin.getDataFolder(), "locales/fr.yml");
        String englishBefore = Files.readString(new File(plugin.getDataFolder(), "locales/en.yml").toPath());
        Files.writeString(selected.toPath(), "# Administrateur : texte personnalisé\nlanguage:\n  help:\n    title: 'Bienvenue, équipe !'\n", StandardCharsets.UTF_8);
        plugin.getConfig().set("system.language", "fr");
        LocaleManager.load(plugin);
        assertEquals("Bienvenue, équipe !", Lang.text("help.title"));
        String after = Files.readString(selected.toPath());
        assertTrue(after.contains("Administrateur : texte personnalisé"));
        assertTrue(after.contains("Bienvenue, équipe !"));
        assertTrue(YamlConfiguration.loadConfiguration(selected).isString("language.creation.start"));
        assertEquals(englishBefore, Files.readString(new File(plugin.getDataFolder(), "locales/en.yml").toPath()));
        assertEquals("aide", CommandLocale.word("help"));
    }

    @Test public void malformedYamlIsPreservedAndLanguageReloadChangesSyntaxAndLabels() throws Exception {
        File selected = new File(plugin.getDataFolder(), "locales/ja.yml");
        String damaged = "language: [\n";
        Files.writeString(selected.toPath(), damaged, StandardCharsets.UTF_8);
        plugin.getConfig().set("system.language", "ja");
        plugin.getConfig().set(Settings.SCOREBOARDENABLED.getPath(), false);
        plugin.saveConfig();
        plugin.reloadEverything();
        assertEquals(damaged, Files.readString(selected.toPath()));
        assertEquals("/jail ヘルプ", CommandLocale.syntax("/jail help"));
        assertEquals("服役情報", LocaleManager.configuredLabel(plugin, Settings.SCOREBOARDTITLE.getPath(), "scoreboard.title", "Jail Info"));
        plugin.getConfig().set(Settings.SCOREBOARDTITLE.getPath(), "&bMy Jail");
        assertEquals("§bMy Jail", LocaleManager.configuredLabel(plugin, Settings.SCOREBOARDTITLE.getPath(), "scoreboard.title", "Jail Info"));
    }

    @Test public void prisonerCanUseTranslatedWhitelistedCommandsButNotTranslatedAdminCommands() {
        fixture.addJail("secure");
        var jail = plugin.getJailManager().getJail("secure");
        var player = fixture.getPlayer();
        Prisoner prisoner = new Prisoner(player.getUniqueId().toString(), player.getName(), true, 60000, "staff", "test");
        jail.addPrisoner(prisoner);
        Lang.setFile(LocaleManager.bundled("fr"));
        ProtectionListener listener = new ProtectionListener(plugin);
        PlayerCommandPreprocessEvent allowed = new PlayerCommandPreprocessEvent(player, "/jail statut");
        listener.commandProtection(allowed);
        assertFalse(allowed.isCancelled());
        PlayerCommandPreprocessEvent denied = new PlayerCommandPreprocessEvent(player, "/jail créer other");
        listener.commandProtection(denied);
        assertTrue(denied.isCancelled());
    }
}
