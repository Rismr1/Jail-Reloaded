package com.graywolf336.jail;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import java.lang.reflect.Method;
import java.sql.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.*;
import org.junit.*;
import com.graywolf336.jail.beans.*;
import com.graywolf336.jail.enums.Settings;
import com.graywolf336.jail.events.*;
import com.graywolf336.jail.listeners.*;
import test.java.com.graywolf336.jail.util.TestInstanceCreator;

/** Regressions reproduced during the full plugin audit. */
public class AuditRegressionTest {
    private TestInstanceCreator fixture;
    private JailMain plugin;
    private Jail jail;
    private Prisoner prisoner;
    private Player player;
    private World world;
    @Before public void setup() {
        fixture = new TestInstanceCreator(); assertTrue(fixture.setup());
        plugin = fixture.getMain(); player = fixture.getPlayer(); world = player.getWorld();
        plugin.getConfig().set(Settings.SCOREBOARDENABLED.getPath(),false);
        if(world == null) world = player.getLocation().getWorld();
        fixture.addJail("audit"); jail = plugin.getJailManager().getJail("audit");
        jail.setWorld(world.getName()); jail.setMinPoint(new int[] {0, 60, 0}); jail.setMaxPoint(new int[] {10, 80, 10});
        jail.setTeleportIn(new Location(world, 5,65,5));
        prisoner = new Prisoner(player.getUniqueId().toString(), player.getName(), 120000L);
        prisoner.setChanged(true);
        jail.addPrisoner(prisoner);
        plugin.getJailManager().addCacheObject(new CachePrisoner(jail, prisoner));
    }
    @After public void cleanup() { fixture.tearDown(); }

    @Test public void actualTeleportDestinationMustBeContained() {
        PlayerTeleportEvent event = new PlayerTeleportEvent(player, new Location(world,5,65,5), new Location(world,50,65,5));
        new MoveProtectionListener(plugin).onPlayerTeleport(event);
        assertTrue("Teleport handler changes a disposable event, leaving the real teleport outside", event.isCancelled() || jail.isInside(event.getTo()));
    }
    @Test public void verticalEscapeMustBeContained() {
        PlayerMoveEvent event = new PlayerMoveEvent(player,new Location(world,5,65,5),new Location(world,5,200,5));
        new MoveProtectionListener(plugin).moveProtection(event);
        assertTrue("Changing only Y skips cuboid containment", event.isCancelled() || jail.isInside(event.getTo()));
    }
    @Test public void canceledTransferMustKeepMovementCache() {
        PrePrisonerTransferredEvent event = new PrePrisonerTransferredEvent(jail,null,jail,null,prisoner,player,"Audit");
        new CacheListener(plugin).beforeTransferringListener(event);
        event.setCancelled(true); // Another listener cancels later in dispatch.
        assertTrue("Canceled transfer removed the only movement-protection cache entry", plugin.getJailManager().inCache(player.getUniqueId()));
    }
    @Test public void canceledKickMustKeepMovementCache() {
        PlayerKickEvent event = new PlayerKickEvent(player,"Audit","");
        new CacheListener(plugin).kickListener(event); event.setCancelled(true);
        assertTrue("Canceled kick removed movement protection for a player still online",plugin.getJailManager().inCache(player.getUniqueId()));
    }
    @Test public void offlineTransferMustFinishPendingStateOnJoin() {
        prisoner.setOfflinePending(true); prisoner.setToBeTransferred(true);
        new PlayerListener(plugin).checkForOfflineJailStuff(new PlayerJoinEvent(player,""));
        assertFalse("Transferred player remains offline-pending; sentence timer skips them",prisoner.isOfflinePending());
    }
    @Test public void secondJoinAfterOfflineTransferMustNotOverwriteSavedProperty() {
        prisoner.setOfflinePending(true); prisoner.setToBeTransferred(true);
        prisoner.setInventory("original-property-sentinel");
        plugin.getConfig().set(Settings.COMMANDSONJAIL.getPath(), Collections.emptyList());
        PlayerListener listener = new PlayerListener(plugin);
        listener.checkForOfflineJailStuff(new PlayerJoinEvent(player,""));
        listener.checkForOfflineJailStuff(new PlayerJoinEvent(player,""));
        assertEquals("Second join confiscates again and overwrites original property", "original-property-sentinel",prisoner.getInventory());
    }
    @Test public void identicalCoordinatesInAnotherWorldMustNotResolveToJail() {
        World other = mock(World.class); when(other.getName()).thenReturn("other_world");
        assertNull("Jail lookup ignores the world",plugin.getJailManager().getJailFromLocation(new Location(other,5,65,5)));
    }
    @Test public void subMinuteSentenceMustHavePositiveBill() {
        plugin.getConfig().set(Settings.JAILPAYITEM.getPath(),"diamond");
        plugin.getConfig().set(Settings.JAILPAYPRICEPERMINUTE.getPath(),10);
        prisoner.setRemainingTime(59000L);
        assertTrue("Positive remaining sentence rounds down to zero-cost release",new JailPayManager(plugin).calculateBill(prisoner) > 0);
    }
    @Test public void dollarSignsInReasonsMustNotAbortJailing() {
        prisoner.setReason("Owes $100");
        plugin.getConfig().set(Settings.COMMANDSONJAIL.getPath(),List.of("say %reason%"));
        plugin.getConfig().set(Settings.JAILEDSTOREINVENTORY.getPath(),false);
        plugin.getConfig().set(Settings.CLOTHINGENABLED.getPath(),false);
        plugin.getPrisonerManager().jailPrisoner(jail,null,player,prisoner);
    }
    @Test public void failedDatabaseDeleteMustNotCompleteRelease() throws Exception {
        plugin.getConfig().set(Settings.JAILEDSTOREINVENTORY.getPath(),false);
        plugin.getJailIO().saveJail(jail);
        Method get = JailIO.class.getDeclaredMethod("getConnection"); get.setAccessible(true);
        Connection sql = (Connection)get.invoke(plugin.getJailIO());
        String table = plugin.getConfig().getString("storage.mysql.prefix") + "prisoners";
        try(Statement statement = sql.createStatement()) {
            statement.execute("CREATE TRIGGER audit_block_delete BEFORE DELETE ON " + table + " BEGIN SELECT RAISE(ABORT,'audit delete failure'); END");
        }
        assertThrows(IllegalStateException.class, () -> plugin.getPrisonerManager().unJail(jail,null,player,prisoner,fixture.getConsoleSender()));
        try(PreparedStatement query = sql.prepareStatement("SELECT COUNT(*) FROM " + table + " WHERE uuid=?")) {
            query.setString(1,prisoner.getUUID().toString());
            try(ResultSet rows = query.executeQuery()) { rows.next();
                assertTrue("Release reports success with a live prisoner row still in SQLite", rows.getInt(1) == 0 || jail.getPrisoner(player.getUniqueId()) != null);
            }
        }
    }
    @Test public void forceClearOfOneJailMustNotClearAnotherJail() throws Exception {
        fixture.addJail("other"); Jail other = plugin.getJailManager().getJail("other");
        Prisoner second = new Prisoner(UUID.randomUUID().toString(),"OtherPlayer",60000L); other.addPrisoner(second);
        new com.graywolf336.jail.command.subcommands.JailClearCommand().execute(plugin.getJailManager(),fixture.getConsoleSender(),"clear","-f","audit");
        new com.graywolf336.jail.command.subcommands.JailConfirmCommand().execute(plugin.getJailManager(),fixture.getConsoleSender(),"confirm");
        assertNotNull("Explicit jail argument is discarded when -f adds a third argument",other.getPrisoner(second.getUUID()));
    }
    @Test public void forceClearMustDeletePersistentPrisonerRows() throws Exception {
        plugin.getJailIO().saveJail(jail);
        plugin.getJailManager().forcefullyClearJailOrJails("audit");
        Method get = JailIO.class.getDeclaredMethod("getConnection"); get.setAccessible(true);
        Connection sql = (Connection)get.invoke(plugin.getJailIO());
        String table = plugin.getConfig().getString("storage.mysql.prefix") + "prisoners";
        try(Statement query = sql.createStatement(); ResultSet rows = query.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rows.next(); assertEquals("Force-clear leaves old rows which reappear on restart",0,rows.getInt(1));
        }
    }
    @Test public void reducingSentencePastZeroMustNotCreateAnUnexpiringNegativeSentence() throws Exception {
        new com.graywolf336.jail.command.subcommands.JailTimeCommand().execute(plugin.getJailManager(),fixture.getConsoleSender(),"time","remove",player.getName(),"5m");
        assertTrue("Timer ignores negative times, stranding this prisoner",prisoner.getRemainingTime() >= 0);
        prisoner.setRemainingTime(-42L); assertEquals(0L,prisoner.getRemainingTime());
        prisoner.setRemainingTime(-1L); assertEquals(-1L,prisoner.getRemainingTime());
        assertEquals(0L,new Prisoner(UUID.randomUUID().toString(),"Legacy",-60000L).getRemainingTime());
    }
    @Test public void escapeModeMustStillResetAfkOnMovement() {
        plugin.getConfig().set(Settings.MOVEPROTECTION.getPath(),false);
        prisoner.setAFKTime(100000L);
        new MoveProtectionListener(plugin).moveProtection(new PlayerMoveEvent(player,new Location(world,5,65,5),new Location(world,6,65,5)));
        assertEquals("AFK activity tracking disappears when escape is enabled",0L,prisoner.getAFKTime());
    }
    @Test public void failedStorageMigrationMustRetainPreviousConnection() throws Exception {
        plugin.getJailIO().saveJail(jail);
        Method get = JailIO.class.getDeclaredMethod("getConnection"); get.setAccessible(true);
        Connection sql = (Connection)get.invoke(plugin.getJailIO());
        String original = plugin.getConfig().getString("storage.mysql.prefix");
        try(Statement statement = sql.createStatement()) {
            for(String table : List.of("jails","cells","prisoners","records"))
                statement.execute("CREATE TABLE migration_" + table + " AS SELECT * FROM " + original + table);
            statement.execute("CREATE TRIGGER audit_block_migration BEFORE INSERT ON migration_prisoners BEGIN SELECT RAISE(ABORT,'audit migration failure'); END");
        }
        plugin.getConfig().set("storage.mysql.prefix","migration_");
        assertFalse("Migration write failures are swallowed and replacement storage is accepted",plugin.getJailIO().prepareStorage(true));
        assertFalse("Prior connection should remain usable after rejected migration",sql.isClosed());
    }
    @Test public void releasingUnlockedGearMustPreserveReplacementArmorWhenGeneralStorageIsDisabled() {
        org.bukkit.UnsafeValues unsafe = mock(org.bukkit.UnsafeValues.class);
        when(unsafe.getDataVersion()).thenReturn(3105);
        when(unsafe.getMaterial(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer(call -> Material.matchMaterial(call.getArgument(0)));
        when(fixture.getServer().getUnsafe()).thenReturn(unsafe);
        when(fixture.getServer().getItemFactory().getItemMeta(org.mockito.ArgumentMatchers.any(Material.class))).thenReturn(null);
        org.bukkit.inventory.ItemStack original = new org.bukkit.inventory.ItemStack(Material.IRON_HELMET);
        org.bukkit.inventory.ItemStack replacement = new org.bukkit.inventory.ItemStack(Material.DIAMOND_HELMET);
        prisoner.setArmor(Util.itemStackArrayToBase64(new org.bukkit.inventory.ItemStack[] {null,null,null,original}));
        when(player.getInventory().getArmorContents()).thenReturn(new org.bukkit.inventory.ItemStack[] {null,null,null,replacement});
        org.bukkit.inventory.ItemStack[] carried = new org.bukkit.inventory.ItemStack[41]; carried[39] = replacement;
        when(player.getInventory().getContents()).thenReturn(carried);
        plugin.getConfig().set(Settings.MOVEPROTECTION.getPath(),false);
        PropertyInventory.restore(player,prisoner,null,false);
        verify(player.getInventory()).addItem(replacement);
    }
}
