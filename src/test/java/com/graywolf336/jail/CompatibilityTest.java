package com.graywolf336.jail;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.Arrays;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.*;
import com.graywolf336.jail.beans.*;
import com.graywolf336.jail.enums.Settings;
import com.graywolf336.jail.events.*;
import com.graywolf336.jail.listeners.*;
import test.java.com.graywolf336.jail.util.TestInstanceCreator;

public class CompatibilityTest {
    private TestInstanceCreator fixture;
    private JailMain plugin;

    @Before public void setup() {
        fixture = new TestInstanceCreator();
        assertTrue(fixture.setup());
        plugin = fixture.getMain();
        fixture.addJail("compatibility");
    }

    @After public void cleanup() { fixture.tearDown(); }

    @Test public void sentenceAndSaveTasksUseServerThread() {
        verify(fixture.getServer().getScheduler()).runTaskTimer(eq(plugin), any(Runnable.class), eq(200L), eq(200L));
        verify(fixture.getServer().getScheduler()).runTaskTimer(eq(plugin), any(Runnable.class), eq(1200L), eq(1200L));
        verify(fixture.getServer().getScheduler(), never()).runTaskTimerAsynchronously(eq(plugin), any(Runnable.class), eq(200L), eq(200L));
    }

    @Test public void failedStorageReplacementPreservesTheWorkingSqliteConnection() throws Exception {
        JailIO io = plugin.getJailIO();
        java.lang.reflect.Field connection = JailIO.class.getDeclaredField("con");
        connection.setAccessible(true);
        java.sql.Connection previous = (java.sql.Connection) connection.get(io);
        assertNotNull(previous);
        plugin.getConfig().set("storage.type", "mysql");
        plugin.getConfig().set("storage.mysql.host", "127.0.0.1");
        plugin.getConfig().set("storage.mysql.port", 1);
        assertFalse(io.prepareStorage(true));
        assertSame(previous, connection.get(io));
        assertFalse(previous.isClosed());
        plugin.getConfig().set("storage.type", "sqlite");
        plugin.getConfig().set("storage.mysql.prefix", "broken`");
        assertFalse("Table creation failure must reject replacement storage", io.prepareStorage(true));
        assertSame(previous, connection.get(io));
        assertFalse(previous.isClosed());
        try(java.sql.Statement statement = previous.createStatement()) {
            assertTrue(statement.executeQuery("SELECT 1").next());
        }
    }

    @Test public void timerReloadAppliesAfkAndDoesNotDuplicateSentenceOrSaveTasks() throws Exception {
        java.lang.reflect.Field field = JailMain.class.getDeclaredField("jt");
        field.setAccessible(true);
        JailTimer timer = (JailTimer) field.get(plugin);
        plugin.getConfig().set(Settings.MAXAFKTIME.getPath(), "2m");
        timer.reloadSettings(); timer.reloadSettings();
        java.lang.reflect.Field afk = JailTimer.class.getDeclaredField("afkTime");
        afk.setAccessible(true);
        assertEquals(Long.valueOf(120000), afk.get(timer));
        verify(fixture.getServer().getScheduler(), times(1)).runTaskTimer(eq(plugin), any(Runnable.class), eq(200L), eq(200L));
        verify(fixture.getServer().getScheduler(), times(1)).runTaskTimer(eq(plugin), any(Runnable.class), eq(1200L), eq(1200L));
        plugin.getConfig().set(Settings.USEBUKKITTIMER.getPath(), false);
        timer.reloadSettings();
        assertTrue(timer.getTimer().isRunning());
        javax.swing.Timer previous = timer.getTimer();
        plugin.getConfig().set(Settings.USEBUKKITTIMER.getPath(), true);
        timer.reloadSettings();
        assertFalse(previous.isRunning());
        assertNull(timer.getTimer());
        verify(fixture.getServer().getScheduler(), times(2)).runTaskTimer(eq(plugin), any(Runnable.class), eq(200L), eq(200L));
        verify(fixture.getServer().getScheduler(), times(1)).runTaskTimer(eq(plugin), any(Runnable.class), eq(1200L), eq(1200L));
    }

    @Test public void handcuffsRedirectTeleportWithoutStartingAnotherTeleport() {
        org.bukkit.Location anchor = fixture.getPlayer().getLocation();
        plugin.getHandCuffManager().addHandCuffs(fixture.getPlayer().getUniqueId(), anchor);
        org.bukkit.event.player.PlayerTeleportEvent event = new org.bukkit.event.player.PlayerTeleportEvent(
                fixture.getPlayer(), anchor, anchor.clone().add(100, 0, 0));
        new HandCuffListener(plugin).onPlayerTeleport(event);
        assertEquals(anchor, event.getTo());
        verify(fixture.getPlayer(), never()).teleport(any(org.bukkit.Location.class));
    }

    @Test public void namespacedCommandUsesRegisteredCommandName() {
        Command command = mock(Command.class);
        when(command.getName()).thenReturn("jail");
        assertTrue(plugin.onCommand(fixture.getPlayer(), command, "jailreloaded:jail", new String[] {"create", "newjail"}));
        assertTrue(plugin.getJailManager().isCreatingSomething(fixture.getPlayer().getName()));
        assertTrue(plugin.onTabComplete(fixture.getPlayer(), command, "jailreloaded:jail", new String[] {""}).contains("create"));
    }

    @Test public void offhandItemProtectionUsesEventItemWithoutBlock() throws Exception {
        Jail jail = plugin.getJailManager().getJail("compatibility");
        Prisoner prisoner = new Prisoner(fixture.getPlayer().getUniqueId().toString(), "Test", 60000L);
        jail.addPrisoner(prisoner);
        plugin.getConfig().set(Settings.PREVENTINTERACTIONITEMS.getPath(), Arrays.asList("ender_pearl"));
        PlayerInteractEvent event = new PlayerInteractEvent(fixture.getPlayer(), Action.RIGHT_CLICK_AIR,
                new ItemStack(Material.ENDER_PEARL), null, BlockFace.SELF, EquipmentSlot.OFF_HAND);
        new ProtectionListener(plugin).interactionProtection(event);
        assertEquals(org.bukkit.event.Event.Result.DENY, event.useItemInHand());
        assertEquals(60000L, prisoner.getRemainingTime());
    }

    @Test public void itemPaymentDoesNotRequireVaultAndUsesInfinitePrice() throws Exception {
        plugin.getConfig().set(Settings.JAILPAYITEM.getPath(), "diamond");
        plugin.getConfig().set(Settings.JAILPAYENABLED.getPath(), true);
        plugin.reloadEverything();
        assertNotNull(plugin.getJailPayManager());
        assertTrue(plugin.getJailPayManager().usingItemsForPayment());
        assertEquals(10000D, plugin.getJailPayManager().calculateBill(new Prisoner(UUID.randomUUID().toString(), "Infinite", -1L)), 0D);
    }

    @Test public void disablingJailSticksOnReloadClearsUsers() throws Exception {
        plugin.reloadEverything();
        com.graywolf336.jail.interfaces.IJailStickManager previous = plugin.getJailStickManager();
        UUID id = UUID.randomUUID();
        previous.toggleUsingStick(id);
        plugin.getConfig().set(Settings.JAILSTICKENABLED.getPath(), false);
        plugin.reloadEverything();
        assertNull(plugin.getJailStickManager());
        assertFalse(previous.isUsingJailStick(id));
    }

    @Test public void nullTargetCellIsValidForOfflineTransfers() {
        Jail jail = plugin.getJailManager().getJail("compatibility");
        PrisonerTransferredEvent event = new PrisonerTransferredEvent(jail, null, jail, null,
                new Prisoner(UUID.randomUUID().toString(), "Offline", 60000L), null);
        assertNull(event.getTargetCell());
        assertFalse(event.hasTargetCell());
        assertFalse(event.isOnline());
        PrePrisonerTransferredEvent before = new PrePrisonerTransferredEvent(jail, null, jail, null, event.getPrisoner(), null, "Test");
        assertNull(before.getTargetCell());
        assertFalse(before.hasTargetCell());
    }

    @Test public void trappedChestProtectionChecksThePrisonersConnectedInventory() {
        JailMain isolated = mock(JailMain.class);
        JailManager manager = mock(JailManager.class);
        Jail jail = mock(Jail.class);
        Cell cell = mock(Cell.class);
        Block block = mock(Block.class);
        org.bukkit.block.Chest clicked = mock(org.bukkit.block.Chest.class), owned = mock(org.bukkit.block.Chest.class);
        org.bukkit.inventory.Inventory clickedInventory = mock(org.bukkit.inventory.Inventory.class), ownedInventory = mock(org.bukkit.inventory.Inventory.class);
        org.bukkit.configuration.file.FileConfiguration config = plugin.getConfig();
        when(isolated.getConfig()).thenReturn(config);
        when(isolated.getJailManager()).thenReturn(manager);
        UUID id = fixture.getPlayer().getUniqueId();
        when(manager.isPlayerJailed(id)).thenReturn(true);
        when(manager.getJailPlayerIsIn(id)).thenReturn(jail);
        when(jail.getCellPrisonerIsIn(id)).thenReturn(cell);
        when(cell.hasChest()).thenReturn(true);
        when(cell.getChest()).thenReturn(owned);
        when(block.getType()).thenReturn(Material.TRAPPED_CHEST);
        when(block.getState()).thenReturn(clicked);
        when(clicked.getInventory()).thenReturn(clickedInventory);
        when(owned.getInventory()).thenReturn(ownedInventory);
        org.bukkit.Location ownLocation = fixture.getPlayer().getLocation();
        when(ownedInventory.getLocation()).thenReturn(ownLocation);
        when(clickedInventory.getLocation()).thenReturn(ownLocation.clone().add(10, 0, 0));
        PlayerInteractEvent unrelated = new PlayerInteractEvent(fixture.getPlayer(), Action.RIGHT_CLICK_BLOCK, null, block, BlockFace.UP);
        ProtectionListener listener = new ProtectionListener(isolated);
        listener.chestProtection(unrelated);
        assertTrue(unrelated.isCancelled());
        when(clickedInventory.getLocation()).thenReturn(ownLocation);
        PlayerInteractEvent own = new PlayerInteractEvent(fixture.getPlayer(), Action.RIGHT_CLICK_BLOCK, null, block, BlockFace.UP);
        listener.chestProtection(own);
        assertFalse(own.isCancelled());
    }

    @Test public void offlineReleaseRemovesStaleCache() {
        Jail jail = plugin.getJailManager().getJail("compatibility");
        Prisoner prisoner = new Prisoner(UUID.randomUUID().toString(), "Offline", 60000L);
        plugin.getJailManager().addCacheObject(new CachePrisoner(jail, prisoner));
        new CacheListener(plugin).releaseListener(new PrisonerReleasedEvent(jail, null, prisoner, null));
        assertFalse(plugin.getJailManager().inCache(prisoner.getUUID()));
    }

    @Test public void legacyWrappedBase64StillDecodes() throws Exception {
        String encoded = Util.itemStackArrayToBase64(new ItemStack[] {null, null});
        String legacy = encoded.replaceAll("\\s", "").replaceAll("(.{64})", "$1\r\n");
        assertArrayEquals(new ItemStack[] {null, null}, Util.itemStackArrayFromBase64(legacy));
    }

    @Test public void omittedReasonUsesDefaultAndMessagesTreatReasonsLiterally() {
        Prisoner prisoner = new Prisoner(UUID.randomUUID().toString(), "Test", 60000L);
        assertNotNull(prisoner.getReason());
        String reason = "Cost $1 and C:\\path";
        assertTrue(com.graywolf336.jail.enums.Lang.JAILEDWITHREASON.get(reason).contains(reason));
    }

    @Test public void reloadingScoreboardStopsPreviousTask() {
        when(fixture.getServer().getScheduler().scheduleSyncRepeatingTask(eq(plugin), any(Runnable.class), eq(200L), eq(100L))).thenReturn(71);
        ScoreBoardManager board = new ScoreBoardManager(plugin);
        board.removeAllScoreboards();
        verify(fixture.getServer().getScheduler()).cancelTask(71);
        verify(fixture.getServer(), never()).getOfflinePlayer(anyString());
    }
}
