package com.graywolf336.jail;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.text.SimpleDateFormat;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.*;
import com.graywolf336.jail.command.*;
import com.graywolf336.jail.enums.Lang;

public class LocaleSystemTest {
    @After public void reset() { Lang.setFile(LocaleManager.bundled("en")); }

    @Test public void allSeventeenFilesHaveCompleteUtf8MessagesAndValidDatePatterns() {
        assertEquals(17, LocaleManager.CODES.size());
        YamlConfiguration english = LocaleManager.bundled("en");
        for (String code : LocaleManager.CODES) {
            YamlConfiguration locale = LocaleManager.bundled(code);
            assertEquals(code, english.getKeys(true), locale.getKeys(true));
            for (String key : english.getKeys(true)) if (english.isString(key)) {
                assertTrue(code + ":" + key, locale.isString(key));
                String text = locale.getString(key);
                assertFalse(code + ":" + key, text.isBlank());
                assertFalse(text.contains("\ufffd"));
                assertEquals(code + ":" + key, LocaleManager.placeholders(english.getString(key)), LocaleManager.placeholders(text));
            }
            new SimpleDateFormat(locale.getString("language.general.timeformat"), Locale.ROOT).format(new Date(0));
        }
    }

    @Test public void subcommandAliasesAreUniqueAndRoundTripInEveryLanguage() {
        for (String code : LocaleManager.CODES) {
            Lang.setFile(LocaleManager.bundled(code));
            Set<String> names = new HashSet<>();
            for (String command : CommandLocale.COMMANDS) {
                String alias = CommandLocale.word(command);
                assertTrue(code + ":" + alias, names.add(alias.toLowerCase(Locale.ROOT)));
                assertArrayEquals(new String[] {command}, CommandLocale.canonicalize(new String[] {alias}));
                assertArrayEquals(new String[] {command}, CommandLocale.canonicalize(new String[] {command}));
            }
            assertArrayEquals(new String[] {"time", "add", "admin", "5m"}, CommandLocale.canonicalize(new String[] {CommandLocale.word("time"), CommandLocale.word("add"), "admin", "5m"}));
            assertArrayEquals(new String[] {"vote", "players", "yes"}, CommandLocale.canonicalize(new String[] {CommandLocale.word("vote"), "players", CommandLocale.word("yes")}));
            assertArrayEquals(new String[] {"help", "players", "2"}, CommandLocale.canonicalize(new String[] {CommandLocale.word("help"), CommandLocale.word("players"), "2"}));
        }
    }

    @Test public void syntaxKeepsRootsFlagsAndDelimitersButTranslatesArguments() {
        Lang.setFile(LocaleManager.bundled("de"));
        assertEquals("/jail hilfe [spieler|admin] [Seite]", CommandLocale.syntax("/jail help [players|admin] [page]"));
        assertEquals("/jail {Spieler} [-t Zeit] [-j Gefängnis] [-r Grund]", CommandLocale.syntax("/jail {player} [-t time] [-j jail] [-r reason]"));
        assertEquals("/unjail {Spieler}", CommandLocale.syntax("/unjail {player}"));
        assertEquals("/jail zeit {hinzufügen|entfernen|setzen|anzeigen} {Spieler} [Zeit]", CommandLocale.syntax("/jail time {add|remove|set|show} {player} [time]"));
    }

    @Test public void completionsAcceptLocalizedPrefixesAndNeverRenamePlayerCandidates() throws Exception {
        JailMain plugin = mock(JailMain.class);
        JailHandler handler = new JailHandler(plugin);
        Player sender = mock(Player.class);
        when(sender.hasPermission(anyString())).thenReturn(true);
        JailManager manager = mock(JailManager.class);
        for (String code : LocaleManager.CODES) {
            Lang.setFile(LocaleManager.bundled(code));
            String help = CommandLocale.word("help");
            assertTrue(code, handler.parseTabComplete(manager, sender, new String[] {help.substring(0, 1)}).contains(help));
            String create = CommandLocale.word("createcell");
            assertTrue(code, handler.parseTabComplete(manager, sender, new String[] {create.substring(0, 1)}).contains(create));
            String enter = CommandLocale.word("telein");
            assertTrue(code, handler.parseTabComplete(manager, sender, new String[] {enter.substring(0, 1)}).contains(enter));
            String add = CommandLocale.word("add");
            assertTrue(code, handler.parseTabComplete(manager, sender, new String[] {CommandLocale.word("time"), add.substring(0, 1)}).contains(add));
        }
        assertEquals(List.of("help"), CommandLocale.completions(new String[] {"check", "he"}, List.of("help")));
    }

    @Test public void helpStillFiltersPermissionsAndBuildsExecutableLocalizedNavigation() {
        Player player = mock(Player.class);
        when(player.hasPermission("jail.command.jailhelp")).thenReturn(true);
        when(player.hasPermission("jail.usercmd.jailstatus")).thenReturn(true);
        Lang.setFile(LocaleManager.bundled("ja"));
        String help = JailHelpMenu.pages(player, JailHelpMenu.View.ALL).toString();
        assertTrue(help.contains("プレイヤー用コマンド"));
        assertFalse(help.contains("管理者用コマンド"));
        assertTrue(help.contains("/jail 状態"));
        assertFalse(help.contains("/jail 作成"));
        String command = CommandLocale.syntax("/jail help admin");
        var nav = JailHelpMenu.navigation(1, 3, command);
        assertEquals("/jail ヘルプ 管理者 2", nav.getExtra().get(0).getClickEvent().getValue());
    }

    @Test public void badOverridesFallBackPerKeyWhileCustomUnicodeAndEmptyMessagesSurvive() {
        YamlConfiguration base = LocaleManager.bundled("fr"), custom = new YamlConfiguration();
        custom.set("language.jailing.status", "Incorrect %0%");
        custom.set("language.help.title", List.of("wrong type"));
        custom.set("language.general.pluginreloaded", "Bienvenue à la maison !");
        custom.set("language.general.nopermission", "");
        YamlConfiguration merged = LocaleManager.merge(base, custom);
        assertEquals(base.getString("language.jailing.status"), merged.getString("language.jailing.status"));
        assertEquals(base.getString("language.help.title"), merged.getString("language.help.title"));
        assertEquals("Bienvenue à la maison !", merged.getString("language.general.pluginreloaded"));
        assertEquals("", merged.getString("language.general.nopermission"));
    }

    @Test public void placeholderValuesAreLiteralAndNeverExpandedTwice() {
        Lang.setFile(LocaleManager.bundled("en"));
        String message = ChatColor.stripColor(Lang.text("general.prisonerentry", "$name\\x %1%", "reason", "jailer", "5m"));
        assertEquals("$name\\x %1%: reason; jailer (5m)", message);
        Lang.setFile(new YamlConfiguration());
        assertEquals("Jail Commands", Lang.text("help.title"));
    }

    @Test public void commandWhitelistUnderstandsAliasesWithoutTranslatingOtherPluginsOrNames() {
        Lang.setFile(LocaleManager.bundled("fr"));
        assertEquals("/jail pay aide", CommandLocale.canonicalLine("/jail payer aide"));
        assertEquals("/jailreloaded:jail status", CommandLocale.canonicalLine("/jailreloaded:jail statut"));
        assertEquals("/other payer aide", CommandLocale.canonicalLine("/other payer aide"));
        assertEquals("/jail transfer -p aide -j créer", CommandLocale.canonicalLine("/jail transférer -p aide -j créer"));
    }

    @Test public void localeCodesAreNormalizedWithoutAllowingPaths() {
        assertEquals("pt_br", LocaleManager.normalize(" PT-BR "));
        assertEquals("zh_tw", LocaleManager.normalize("ZH-TW"));
        assertEquals("en", LocaleManager.normalize("../../config"));
        assertEquals("en", LocaleManager.normalize(null));
    }

    @Test public void signDefaultsFollowLanguageWhileCustomLinesStayIntact() throws Exception {
        Lang.setFile(LocaleManager.bundled("de"));
        Util.updateSignLinesCache(new String[] {"%player%", "%prettytime%", "with a reason", "%reason%"});
        assertEquals("Grund:", Util.getSignLines()[2]);
        Util.updateSignLinesCache(new String[] {"custom", "custom", "@locale:sign.reason", ""});
        assertEquals("custom", Util.getSignLines()[0]);
        assertEquals("Grund:", Util.getSignLines()[2]);
    }

    @Test public void customAliasesCannotShadowEnglishCommandsOrTurnHelpIntoDestructiveActions() {
        YamlConfiguration custom = LocaleManager.bundled("de");
        custom.set("language.syntax.words.help", "dc");
        custom.set("language.syntax.words.delete", "clearforce");
        custom.set("language.syntax.words.create", "same");
        custom.set("language.syntax.words.clear", "same");
        Lang.setFile(custom);
        assertEquals("help", CommandLocale.word("help"));
        assertEquals("delete", CommandLocale.word("delete"));
        assertEquals("create", CommandLocale.word("create"));
        assertEquals("clear", CommandLocale.word("clear"));
        assertArrayEquals(new String[] {"deletecell", "jail", "cell"}, CommandLocale.canonicalize(new String[] {"dc", "jail", "cell"}));
        assertArrayEquals(new String[] {"clearforce", "jail"}, CommandLocale.canonicalize(new String[] {"clearforce", "jail"}));
        assertArrayEquals(new String[] {"time", "add", "help", "1m"}, CommandLocale.canonicalize(new String[] {"t", "hinzufügen", "help", "1m"}));
    }

    @Test public void commandsInsideInstructionsAreLocalizedBeforeInsertingUserText() {
        Lang.setFile(LocaleManager.bundled("fr"));
        assertTrue(Lang.START.get().contains("/jail confirmer"));
        assertTrue(Lang.VOTEBROADCASTLINE2.get("Alex").contains("/jail voter Alex oui"));
        assertTrue(Lang.text("creation.nextcell", "cell").contains("/jail arrêter"));
        assertTrue(Lang.JAILEDWITHREASON.get("/jail stop").contains("/jail stop"));
    }
}
