package com.graywolf336.jail;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.junit.*;
import org.mockito.ArgumentCaptor;
import com.graywolf336.jail.beans.*;
import com.graywolf336.jail.command.JailHelpMenu;
import com.graywolf336.jail.enums.Settings;
import com.graywolf336.jail.listeners.*;
import test.java.com.graywolf336.jail.util.TestInstanceCreator;

public class FeedbackTest {
    private TestInstanceCreator fixture;
    private JailMain plugin;
    @Before public void setup() { fixture = new TestInstanceCreator(); assertTrue(fixture.setup()); plugin = fixture.getMain(); }
    @After public void cleanup() { fixture.tearDown(); }

    private CommandSender sender(String... permissions) {
        CommandSender sender = mock(CommandSender.class);
        Set<String> allowed = Set.of(permissions);
        when(sender.hasPermission(anyString())).thenAnswer(call -> allowed.contains(call.getArgument(0)));
        return sender;
    }
    private String body(CommandSender sender, JailHelpMenu.View view) {
        return ChatColor.stripColor(JailHelpMenu.pages(sender, view).toString());
    }

    @Test public void publicHelpDoesNotMakePlayersAdministrators() {
        CommandSender player = sender("jail.command.jailhelp", "jail.usercmd.jailstatus", "jail.usercmd.jailpay");
        assertFalse(JailHelpMenu.isAdmin(player));
        String text = body(player, JailHelpMenu.View.ALL);
        assertTrue(text.contains("Player Commands")); assertTrue(text.contains("/jail status"));
        assertFalse(text.contains("Admin Commands")); assertFalse(text.contains("/jail create"));
    }

    @Test public void staffSeeOnlyTheirGrantedAdminCommandsAlongsidePlayerCommands() {
        CommandSender staff = sender("jail.command.jailhelp", "jail.usercmd.jailstatus", "jail.command.jailreload");
        String text = body(staff, JailHelpMenu.View.ALL);
        assertTrue(text.contains("Player Commands")); assertTrue(text.contains("Admin Commands"));
        assertTrue(text.contains("/jail reload")); assertFalse(text.contains("/jail create"));
        assertFalse(body(staff, JailHelpMenu.View.PLAYERS).contains("Admin Commands"));
        assertFalse(body(staff, JailHelpMenu.View.ADMIN).contains("/jail status"));
    }

    @Test public void helpPagesNeverLoseCommandsOrLeaveAnOrphanHeading() {
        CommandSender admin = mock(CommandSender.class);
        when(admin.isOp()).thenReturn(true); when(admin.hasPermission(anyString())).thenReturn(true);
        List<List<String>> pages = JailHelpMenu.pages(admin, JailHelpMenu.View.ALL);
        assertTrue(pages.size() > 1);
        for(List<String> page : pages) {
            assertTrue(page.size() <= 8);
            assertFalse(ChatColor.stripColor(page.get(page.size() - 1)).endsWith("Commands"));
        }
        long reloads = pages.stream().flatMap(Collection::stream).filter(line -> line.contains("/jail reload")).count();
        assertEquals(1, reloads);
    }

    @Test public void menuHeaderCreditsAndOutOfRangePagesAreCorrect() {
        CommandSender admin = mock(CommandSender.class);
        when(admin.isOp()).thenReturn(true); when(admin.hasPermission(anyString())).thenReturn(true);
        JailHelpMenu.send(admin, "test-version", JailHelpMenu.View.ALL, Integer.MAX_VALUE);
        ArgumentCaptor<String> lines = ArgumentCaptor.forClass(String.class);
        verify(admin, atLeastOnce()).sendMessage(lines.capture());
        String text = ChatColor.stripColor(String.join("\n", lines.getAllValues()));
        assertTrue(text.indexOf("Jail-Reloaded") < text.indexOf("vtest-version"));
        assertTrue(text.indexOf("vtest-version") < text.indexOf("Originally By: graywolf336"));
        assertTrue(text.indexOf("Originally By: graywolf336") < text.indexOf("Jail Commands"));
        int count = JailHelpMenu.pages(admin, JailHelpMenu.View.ALL).size();
        assertTrue(text.contains("Page " + count + "/" + count));
    }

    @Test public void paginationHasMailboxColorsAndCorrectClickableCommands() {
        net.md_5.bungee.api.chat.TextComponent links = JailHelpMenu.navigation(2, 3, "/jail help admin");
        assertEquals("/jail help admin 1", links.getExtra().get(0).getClickEvent().getValue());
        assertEquals("/jail help admin 3", links.getExtra().get(2).getClickEvent().getValue());
        assertEquals(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, links.getExtra().get(0).getClickEvent().getAction());
        assertEquals("#409DBB", links.getExtra().get(0).getExtra().get(0).getColor().getName().toUpperCase(Locale.ROOT));
        assertTrue(JailHelpMenu.navigation(1, 3, "/jail help").toPlainText().contains("Next Page"));
        assertFalse(JailHelpMenu.navigation(1, 3, "/jail help").toPlainText().contains("Previous Page"));
    }

    @Test public void bareJailShowsPlayerHelpAndAdminHelpRemainsProtected() {
        CommandSender player = sender("jail.command.jailhelp", "jail.usercmd.jailstatus");
        plugin.onCommand(player, null, "jail", new String[0]);
        verify(player, atLeastOnce()).sendMessage(contains("Jail Commands"));
        clearInvocations(player);
        plugin.onCommand(player, null, "jail", new String[] {"help", "admin"});
        verify(player, never()).sendMessage(contains("Admin Commands"));
    }

    @Test public void wandCancelsSignChestRapidClicksAndOffhandBeforeSelection() {
        JailMain isolated = mock(JailMain.class);
        JailManager manager = mock(JailManager.class);
        Player player = fixture.getPlayer();
        when(isolated.getJailManager()).thenReturn(manager);
        when(manager.isCreatingSomething(player.getName())).thenReturn(true);
        when(manager.isCreatingACell(player.getName())).thenReturn(true);
        com.graywolf336.jail.steps.CellCreationSteps steps = mock(com.graywolf336.jail.steps.CellCreationSteps.class);
        CreationPlayer state = new CreationPlayer("test", "cell");
        when(manager.getCellCreationSteps()).thenReturn(steps);
        when(manager.getCellCreationPlayer(player.getName())).thenReturn(state);
        ItemStack wand = Util.getWand();
        when(player.getInventory().getItemInMainHand()).thenReturn(wand);
        Block sign = mock(Block.class), chest = mock(Block.class);
        Location location = player.getLocation();
        when(sign.getLocation()).thenReturn(location); when(chest.getLocation()).thenReturn(location);
        PlayerListener listener = new PlayerListener(isolated);
        PlayerInteractEvent first = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, wand, sign, BlockFace.UP, EquipmentSlot.HAND);
        listener.jailOrCellCreation(first);
        assertEquals(Result.DENY, first.useInteractedBlock()); assertEquals(Result.DENY, first.useItemInHand());
        verify(steps).step(manager, player, state, location);
        PlayerInteractEvent rapid = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, wand, chest, BlockFace.UP, EquipmentSlot.HAND);
        listener.jailOrCellCreation(rapid);
        assertEquals(Result.DENY, rapid.useInteractedBlock());
        PlayerInteractEvent offhand = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, wand, chest, BlockFace.UP, EquipmentSlot.OFF_HAND);
        listener.jailOrCellCreation(offhand);
        assertEquals(Result.DENY, offhand.useInteractedBlock());
        verify(steps, times(1)).step(any(), any(), any(), any());
    }

    @Test public void wildcardCoversAllRuntimeMaterialsAndUnknownFutureNamesAreHarmless() {
        for(Material material : Material.values()) {
            if(material.isLegacy() || material.isAir()) continue;
            assertTrue(material.name(), InteractionPolicy.blocks(material, List.of("*")));
        }
        assertFalse(InteractionPolicy.blocks(Material.STONE, List.of("future_wood_door", "cherry_door")));
        assertTrue(InteractionPolicy.blocks(Material.OAK_DOOR, List.of("MINECRAFT:OAK_DOOR")));
        assertFalse(InteractionPolicy.blocks(Material.AIR, List.of("*")));
    }

    @Test public void itemUseAgainstAnAllowedBlockAndOffhandUseAreBothDenied() {
        fixture.addJail("test");
        Prisoner prisoner = new Prisoner(fixture.getPlayer().getUniqueId().toString(), "Test", 60000L);
        plugin.getJailManager().getJail("test").addPrisoner(prisoner);
        plugin.getConfig().set(Settings.PREVENTINTERACTIONBLOCKS.getPath(), List.of());
        plugin.getConfig().set(Settings.PREVENTINTERACTIONITEMS.getPath(), List.of("*"));
        Block block = mock(Block.class); when(block.getType()).thenReturn(Material.STONE);
        ProtectionListener listener = new ProtectionListener(plugin);
        for(EquipmentSlot hand : List.of(EquipmentSlot.HAND, EquipmentSlot.OFF_HAND)) {
            PlayerInteractEvent event = new PlayerInteractEvent(fixture.getPlayer(), Action.RIGHT_CLICK_BLOCK,
                    new ItemStack(Material.ENDER_PEARL), block, BlockFace.UP, hand);
            Result previousBlockUse = event.useInteractedBlock();
            listener.interactionProtection(event);
            assertEquals(Result.DENY, event.useItemInHand());
            assertEquals(previousBlockUse, event.useInteractedBlock());
        }
        assertEquals(60000L, prisoner.getRemainingTime());
    }

    @Test public void standaloneOffhandStillAppliesConfiguredPositivePenalty() {
        fixture.addJail("test");
        Prisoner prisoner = new Prisoner(fixture.getPlayer().getUniqueId().toString(), "Test", 60000L);
        plugin.getJailManager().getJail("test").addPrisoner(prisoner);
        plugin.getConfig().set(Settings.PREVENTINTERACTIONITEMSPENALTY.getPath(), "5m");
        PlayerInteractEvent event = new PlayerInteractEvent(fixture.getPlayer(), Action.RIGHT_CLICK_AIR,
                new ItemStack(Material.ENDER_PEARL), null, BlockFace.SELF, EquipmentSlot.OFF_HAND);
        new ProtectionListener(plugin).interactionProtection(event);
        assertEquals(360000L, prisoner.getRemainingTime());
    }

    @Test public void itemUseOnEntitiesIsProtectedInBothHands() {
        fixture.addJail("test");
        Prisoner prisoner = new Prisoner(fixture.getPlayer().getUniqueId().toString(), "Test", 60000L);
        plugin.getJailManager().getJail("test").addPrisoner(prisoner);
        ItemStack nameTag = new ItemStack(Material.NAME_TAG);
        when(fixture.getPlayer().getInventory().getItemInMainHand()).thenReturn(nameTag);
        when(fixture.getPlayer().getInventory().getItemInOffHand()).thenReturn(nameTag);
        org.bukkit.entity.Entity entity = mock(org.bukkit.entity.Entity.class);
        ProtectionListener listener = new ProtectionListener(plugin);
        for(EquipmentSlot hand : List.of(EquipmentSlot.HAND, EquipmentSlot.OFF_HAND)) {
            org.bukkit.event.player.PlayerInteractEntityEvent event = new org.bukkit.event.player.PlayerInteractEntityEvent(fixture.getPlayer(), entity, hand);
            listener.entityItemInteraction(event);
            assertTrue(event.isCancelled());
        }
        assertEquals(60000L, prisoner.getRemainingTime());
    }

    @Test public void zeroWorksWithoutAUnitAndExistingTimeFormatsRemainSupported() throws Exception {
        for(String value : List.of("0", "0s", "0m", "0h", "0d", " 0 ")) assertEquals(Long.valueOf(0), Util.getTime(value));
        assertEquals(Long.valueOf(300000), Util.getTime("5minutes"));
        assertEquals(Long.valueOf(3600000), Util.getTime("1H"));
        assertEquals(Long.valueOf(-1), Util.getTime("-1"));
    }

    @Test public void configDocumentationUpdatesWithoutResettingOwnerValues() throws Exception {
        YamlConfiguration owner = new YamlConfiguration();
        owner.loadFromString("storage:\n  type: flatfile\njailing:\n  during:\n    movePenalty: 7m\n");
        try(InputStreamReader template = new InputStreamReader(plugin.getResource("config.yml"), StandardCharsets.UTF_8)) {
            ConfigDocumentation.apply(owner, template, "test-build");
        }
        assertEquals("flatfile", owner.getString("storage.type"));
        assertEquals("7m", owner.getString(Settings.MOVEPENALTY.getPath()));
        String output = owner.saveToString();
        assertTrue(output.contains("Original Creator: graywolf336"));
        assertTrue(output.contains("Fork By: Rismr1"));
        assertTrue(output.contains("Jail-Reloaded vtest-build"));
        assertFalse(owner.getComments("storage.type").isEmpty());
        assertFalse(owner.getComments(Settings.MOVEPENALTY.getPath()).isEmpty());
    }

    @Test public void allShippedLeafSettingsAreExplainedAndDefaultsAreSqliteAndZero() throws Exception {
        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(plugin.getResource("config.yml"), StandardCharsets.UTF_8));
        for(String key : defaults.getKeys(true)) {
            if(!defaults.isConfigurationSection(key)) assertFalse("Missing comments: " + key, defaults.getComments(key).isEmpty());
        }
        assertEquals("sqlite", defaults.getString("storage.type"));
        for(Settings setting : List.of(Settings.BLOCKBREAKPENALTY, Settings.BLOCKPLACEPENALTY, Settings.COMMANDPENALTY,
                Settings.CROPTRAMPLINGPENALTY, Settings.MOVEPENALTY, Settings.PREVENTINTERACTIONBLOCKSPENALTY, Settings.PREVENTINTERACTIONITEMSPENALTY)) {
            assertEquals(setting.name(), Long.valueOf(0), Util.getTime(defaults.getString(setting.getPath())));
        }
    }

    @Test public void regeneratedConfigurationKeepsEveryCommentAndRemovesLegacyUpdater() throws Exception {
        YamlConfiguration empty = new YamlConfiguration();
        // JavaPlugin attaches bundled defaults to sparse/deleted configurations.
        empty.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(plugin.getResource("config.yml"), StandardCharsets.UTF_8)));
        empty.set("system.updates.notification", true);
        try(InputStreamReader template = new InputStreamReader(plugin.getResource("config.yml"), StandardCharsets.UTF_8)) {
            ConfigDocumentation.apply(empty, template, "1.0.0");
        }
        YamlConfiguration saved = new YamlConfiguration();
        saved.loadFromString(empty.saveToString());
        for(String path : saved.getKeys(true)) {
            if(!saved.isConfigurationSection(path)) assertFalse("Missing regenerated comment: " + path, saved.getComments(path).isEmpty());
        }
        assertFalse(saved.contains("system.updates"));
        assertEquals("sqlite", saved.getString("storage.type"));
    }

    @Test public void registeredSignsRejectEditingAndInteractionForEveryPlayer() {
        fixture.addJail("signs");
        Cell cell = new Cell("protected");
        Location location = fixture.getPlayer().getLocation();
        cell.addSign(new SimpleLocation(location));
        plugin.getJailManager().getJail("signs").addCell(cell, false);
        Block block = mock(Block.class);
        when(block.getState()).thenReturn(mock(Sign.class));
        when(block.getWorld()).thenReturn(location.getWorld());
        when(block.getX()).thenReturn(location.getBlockX());
        when(block.getY()).thenReturn(location.getBlockY());
        when(block.getZ()).thenReturn(location.getBlockZ());
        CellSignListener listener = new CellSignListener(plugin);
        for(EquipmentSlot hand : List.of(EquipmentSlot.HAND, EquipmentSlot.OFF_HAND)) {
            PlayerInteractEvent event = new PlayerInteractEvent(fixture.getPlayer(), Action.RIGHT_CLICK_BLOCK,
                    new ItemStack(Material.HONEYCOMB), block, BlockFace.NORTH, hand);
            listener.preventRegisteredSignInteraction(event);
            assertEquals(Result.DENY, event.useInteractedBlock());
            assertEquals(Result.DENY, event.useItemInHand());
        }
        org.bukkit.event.block.SignChangeEvent edit = new org.bukkit.event.block.SignChangeEvent(block, fixture.getPlayer(), new String[] {"a", "b", "c", "d"});
        listener.preventRegisteredSignEditing(edit);
        assertTrue(edit.isCancelled());
        when(block.getX()).thenReturn(location.getBlockX() + 1);
        org.bukkit.event.block.SignChangeEvent unrelated = new org.bukkit.event.block.SignChangeEvent(block, fixture.getPlayer(), new String[] {"a", "b", "c", "d"});
        listener.preventRegisteredSignEditing(unrelated);
        assertFalse(unrelated.isCancelled());
    }
}
