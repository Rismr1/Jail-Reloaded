package com.graywolf336.jail;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.*;
import org.junit.*;
import com.graywolf336.jail.beans.*;
import com.graywolf336.jail.enums.Settings;
import test.java.com.graywolf336.jail.util.TestInstanceCreator;

public class AuditHardeningTest {
    private TestInstanceCreator fixture;
    private JailMain plugin;
    private Jail jail;
    private World world;
    @Before public void setup() {
        fixture = new TestInstanceCreator(); assertTrue(fixture.setup()); plugin = fixture.getMain();
        fixture.addJail("secure"); jail = plugin.getJailManager().getJail("secure");
        world = fixture.getPlayer().getLocation().getWorld(); jail.setMinPoint(new int[] {0,60,0}); jail.setMaxPoint(new int[] {10,80,10});
        plugin.getConfig().set(Settings.SCOREBOARDENABLED.getPath(),false);
    }
    @After public void cleanup() { fixture.tearDown(); }

    @Test public void blockExplosionsProtectOnlyTheCorrectWorldAndRegion() {
        Block inside = mock(Block.class), outside = mock(Block.class), elsewhere = mock(Block.class);
        World other = mock(World.class); when(other.getName()).thenReturn("other");
        when(inside.getLocation()).thenReturn(new Location(world,5,65,5));
        when(outside.getLocation()).thenReturn(new Location(world,50,65,5));
        when(elsewhere.getLocation()).thenReturn(new Location(other,5,65,5));
        BlockExplodeEvent event = mock(BlockExplodeEvent.class);
        List<Block> affected = new ArrayList<>(List.of(inside,outside,elsewhere)); when(event.blockList()).thenReturn(affected);
        new com.graywolf336.jail.listeners.EntityListener(plugin).onBlockExplode(event);
        assertEquals(List.of(outside,elsewhere),affected);
    }

    @Test public void sharedChestsAndSignsAreRejectedAndLoadedConflictsAreClosed() {
        Cell first = new Cell("first"), second = new Cell("second");
        first.setChestLocation(new SimpleLocation(new Location(world,4,65,4)));
        second.setChestLocation(new SimpleLocation(new Location(world,4,65,4)));
        jail.addCell(first,false);
        assertThrows(IllegalArgumentException.class, () -> CellResources.requireAvailable(plugin.getJailManager(),jail,second));
        jail.addCell(second,false); CellResources.validate(plugin.getJailManager());
        assertTrue(first.hasResourceConflict()); assertTrue(second.hasResourceConflict());
        Prisoner owner = new Prisoner(fixture.getPlayer().getUniqueId().toString(),"Test",60000L); first.setPrisoner(owner);
        assertFalse(new CellChestProtection(plugin).mayView(fixture.getPlayer(),first));
        assertNotNull(first.updateSigns().get("removed"));
        first.addSign(new SimpleLocation(new Location(world,1,65,1)));
        Cell signOnly = new Cell("sign"); signOnly.addSign(new SimpleLocation(new Location(world,1,65,1)));
        assertThrows(IllegalArgumentException.class, () -> CellResources.requireAvailable(plugin.getJailManager(),jail,signOnly));
    }

    @Test public void sqlCellsAndAssignmentsSurviveALateLoadedWorld() {
        when(fixture.getServer().getWorld("late_world")).thenReturn(null);
        jail.setWorld("late_world"); jail.setTeleportIn(new SimpleLocation("late_world",5,65,5,0,0));
        jail.setTeleportFree(new SimpleLocation("late_world",20,65,5,0,0));
        Cell cell = new Cell("late_cell"); cell.setTeleport(new SimpleLocation("late_world",5,65,5,0,0));
        Prisoner prisoner = new Prisoner(UUID.randomUUID().toString(),"Late",60000L); cell.setPrisoner(prisoner);
        jail.addCell(cell,false); plugin.getJailIO().saveEverything(); plugin.getJailIO().loadJails();
        Jail loaded = plugin.getJailManager().getJail("secure");
        assertFalse(loaded.isEnabled()); assertNotNull(loaded.getCell("late_cell"));
        assertEquals(Integer.MAX_VALUE,loaded.getDistance(fixture.getPlayer().getLocation()),0.0);
        assertNotNull(plugin.getJailManager().getNearestJail(fixture.getPlayer()));
        assertEquals(prisoner.getUUID(),loaded.getCell("late_cell").getPrisoner().getUUID());
        World late = mock(World.class); when(late.getName()).thenReturn("late_world");
        when(fixture.getServer().getWorld("late_world")).thenReturn(late);
        new com.graywolf336.jail.listeners.WorldListener(plugin).worldLoaded(new WorldLoadEvent(late));
        assertTrue(loaded.isEnabled()); assertSame(late,loaded.getTeleportIn().getWorld());
        assertSame(late,loaded.getCell("late_cell").getTeleport().getWorld());
    }

    @Test public void invalidFlatfileIsRejectedWithoutChangingItsBytes() throws Exception {
        File data = new File(plugin.getDataFolder(),"data.yml"); String corrupt = "jails: [unclosed";
        Files.writeString(data.toPath(),corrupt); plugin.getConfig().set("storage.type","flatfile");
        assertFalse(plugin.getJailIO().prepareStorage(true));
        assertEquals(corrupt,Files.readString(data.toPath()));
        plugin.getConfig().set("storage.type","sqlite");
        assertTrue(plugin.getJailIO().prepareStorage(true));
    }

    @Test public void malformedCandidateConfigDoesNotReplaceLiveManagers() throws Exception {
        YamlConfiguration candidate = new YamlConfiguration(); candidate.loadFromString(plugin.getConfig().saveToString());
        candidate.set(Settings.JAILSTICKSTICKS.getPath(),List.of("stick,5m"));
        candidate.set(Settings.MOVEPROTECTION.getPath(),false);
        candidate.save(new File(plugin.getDataFolder(),"config.yml"));
        Object oldSticks = plugin.getJailStickManager();
        assertThrows(IllegalArgumentException.class, () -> plugin.reloadEverything());
        assertSame(oldSticks,plugin.getJailStickManager());
        assertTrue(plugin.getConfig().getBoolean(Settings.MOVEPROTECTION.getPath()));
        assertThrows(IllegalArgumentException.class, () -> ConfigValidation.validateStick("stone,no_time,,bad"));
    }

    @Test public void failedReclaimPersistenceDoesNotAwardOrRemoveItems() {
        org.bukkit.UnsafeValues unsafe = mock(org.bukkit.UnsafeValues.class); when(unsafe.getDataVersion()).thenReturn(3105);
        when(unsafe.getMaterial(anyString(),anyInt())).thenAnswer(call -> Material.matchMaterial(call.getArgument(0)));
        when(fixture.getServer().getUnsafe()).thenReturn(unsafe);
        when(fixture.getServer().getItemFactory().getItemMeta(any(Material.class))).thenReturn(null);
        Prisoner prisoner = new Prisoner(fixture.getPlayer().getUniqueId().toString(),"Test",60000L);
        ItemStack item = new ItemStack(Material.DIAMOND,7); prisoner.setInventory(Util.itemStackArrayToBase64(new ItemStack[] {item}));
        String original = prisoner.getInventory(); Inventory chest = mock(Inventory.class); when(chest.getItem(0)).thenReturn(item);
        when(fixture.getPlayer().getInventory().getStorageContents()).thenReturn(new ItemStack[36]);
        when(fixture.getPlayer().getInventory().getMaxStackSize()).thenReturn(64);
        assertThrows(IllegalStateException.class, () -> PropertyInventory.reclaim(fixture.getPlayer(),prisoner,chest,0,7,
                () -> { throw new IllegalStateException("Injected write failure"); }));
        assertEquals(original,prisoner.getInventory());
        verify(chest,never()).setItem(anyInt(),any());
        verify(fixture.getPlayer().getInventory(),never()).setStorageContents(any());
    }

    @Test public void concurrentLookupsAndAsyncReleaseRequestsDoNotLoseOrDuplicateState() throws Exception {
        Cell cell = new Cell("changing"); jail.addCell(cell,false);
        Prisoner prisoner = new Prisoner(fixture.getPlayer().getUniqueId().toString(),"Test",60000L);
        ExecutorService workers = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Void>> jobs = new ArrayList<>();
            jobs.add(() -> { for(int i=0;i<5000;i++) { cell.setPrisoner(prisoner); cell.removePrisoner(); } return null; });
            for(int t=0;t<3;t++) jobs.add(() -> {
                for(int i=0;i<5000;i++) { plugin.getJailManager().getPrisoner(prisoner.getUUID()); plugin.getPrisonerManager().schedulePrisonerRelease(prisoner); }
                return null;
            });
            for(Future<Void> result : workers.invokeAll(jobs)) result.get();
            java.lang.reflect.Field releases = PrisonerManager.class.getDeclaredField("releases"); releases.setAccessible(true);
            assertEquals(1,((Collection<?>)releases.get(plugin.getPrisonerManager())).size());
        } finally { workers.shutdownNow(); }
    }

    @Test public void occupiedCellTransferReturnsFeedbackWithoutLookingUpAJailByCellName() throws Exception {
        Prisoner prisoner = new Prisoner(fixture.getPlayer().getUniqueId().toString(),fixture.getPlayer().getName(),60000L);
        jail.addPrisoner(prisoner); fixture.addJail("destination");
        Cell taken = new Cell("taken"); taken.setPrisoner(new Prisoner(UUID.randomUUID().toString(),"Other",60000L));
        plugin.getJailManager().getJail("destination").addCell(taken,false);
        assertTrue(new com.graywolf336.jail.command.subcommands.JailTransferCommand().execute(plugin.getJailManager(),fixture.getConsoleSender(),
                "transfer","-p",fixture.getPlayer().getName(),"-j","destination","-c","taken"));
        assertSame(prisoner,jail.getPrisoner(prisoner.getUUID()));
    }

    @Test public void partialPaymentWithNoSentenceBenefitIsNotCharged() throws Exception {
        Prisoner prisoner = new Prisoner(fixture.getPlayer().getUniqueId().toString(),fixture.getPlayer().getName(),120000L); jail.addPrisoner(prisoner);
        plugin.getConfig().set(Settings.JAILPAYENABLED.getPath(),true);
        com.graywolf336.jail.interfaces.IJailPayManager payments = mock(com.graywolf336.jail.interfaces.IJailPayManager.class);
        when(payments.isTimedEnabled()).thenReturn(true); when(payments.hasEnoughToPay(any(),anyDouble())).thenReturn(true);
        when(payments.calculateBill(prisoner)).thenReturn(20.0); when(payments.getMinutesPayingFor(5)).thenReturn(0L);
        doReturn(payments).when(plugin).getJailPayManager();
        new com.graywolf336.jail.command.subcommands.JailPayCommand().execute(plugin.getJailManager(),fixture.getPlayer(),"pay","5");
        verify(payments,never()).pay(any(),anyDouble()); assertEquals(120000L,prisoner.getRemainingTime());
    }

    @Test public void invalidPricesColorsAndMaterialsAreRejectedBeforeReload() throws Exception {
        YamlConfiguration candidate = new YamlConfiguration(); candidate.loadFromString(plugin.getConfig().saveToString());
        candidate.set(Settings.JAILPAYPRICEPERMINUTE.getPath(),Double.NaN);
        assertThrows(IllegalArgumentException.class,() -> ConfigValidation.validate(candidate));
        candidate.set(Settings.JAILPAYPRICEPERMINUTE.getPath(),10);
        candidate.set(Settings.CLOTHINGHELMET.getPath(),"leather_helmet~999,0,0");
        assertThrows(IllegalArgumentException.class,() -> ConfigValidation.validate(candidate));
        candidate.set(Settings.CLOTHINGHELMET.getPath(),"not_a_material");
        assertThrows(IllegalArgumentException.class,() -> ConfigValidation.validate(candidate));
    }
}
