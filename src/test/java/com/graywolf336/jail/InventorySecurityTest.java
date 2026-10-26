package com.graywolf336.jail;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.junit.*;
import com.graywolf336.jail.beans.*;
import test.java.com.graywolf336.jail.util.TestInstanceCreator;

public class InventorySecurityTest {
    private TestInstanceCreator fixture;
    private JailMain plugin;
    private Cell cell;
    private Inventory chest;
    private CellChestProtection protection;
    private Prisoner prisoner;

    @Before public void setup() {
        fixture = new TestInstanceCreator(); assertTrue(fixture.setup()); plugin = fixture.getMain();
        fixture.addJail("property"); cell = new Cell("cell");
        Location location = fixture.getPlayer().getLocation();
        cell.setChestLocation(new SimpleLocation(location));
        plugin.getJailManager().getJail("property").addCell(cell, false);
        prisoner = new Prisoner(fixture.getPlayer().getUniqueId().toString(), "Test", 60000L);
        chest = mock(Inventory.class); when(chest.getLocation()).thenReturn(location);
        protection = new CellChestProtection(plugin);
    }
    @After public void cleanup() { fixture.tearDown(); }

    private void serialization() {
        org.bukkit.UnsafeValues unsafe = mock(org.bukkit.UnsafeValues.class);
        when(unsafe.getDataVersion()).thenReturn(3105);
        when(unsafe.getMaterial(anyString(), anyInt())).thenAnswer(call -> Material.matchMaterial(call.getArgument(0)));
        when(fixture.getServer().getUnsafe()).thenReturn(unsafe);
        when(fixture.getServer().getItemFactory().getItemMeta(any(Material.class))).thenReturn(null);
    }

    @Test public void reclamationRequiresEscapeEnabledAndTheAssignedPrisoner() {
        cell.setPrisoner(prisoner);
        assertFalse(protection.mayReclaim(fixture.getPlayer(), cell));
        plugin.getConfig().set("jailing.during.moveProtection", false);
        assertTrue(protection.mayReclaim(fixture.getPlayer(), cell));
        Player outsider = mock(Player.class); when(outsider.getUniqueId()).thenReturn(UUID.randomUUID());
        assertFalse(protection.mayReclaim(outsider, cell));
        plugin.getConfig().set("jailing.during.openChest", false);
        assertFalse(protection.mayReclaim(fixture.getPlayer(), cell));
        cell.removePrisoner();
        assertFalse(protection.mayReclaim(fixture.getPlayer(), cell));
    }

    @Test public void reclamationUsesOnlyAvailableStorageCapacity() throws Exception {
        serialization();
        ItemStack[] saved = {new ItemStack(Material.DIAMOND, 7)};
        prisoner.setInventory(Util.itemStackArrayToBase64(saved));
        when(chest.getItem(0)).thenReturn(saved[0]);
        ItemStack[] storage = new ItemStack[36];
        Arrays.fill(storage, new ItemStack(Material.STONE, 64)); storage[0] = new ItemStack(Material.DIAMOND, 63);
        PlayerInventory inventory = fixture.getPlayer().getInventory();
        when(inventory.getStorageContents()).thenReturn(storage); when(inventory.getMaxStackSize()).thenReturn(64);
        assertTrue(PropertyInventory.reclaim(fixture.getPlayer(), prisoner, chest, 0, Integer.MAX_VALUE));
        assertEquals(6, PropertyInventory.read(prisoner)[0].getAmount());
        assertEquals(63, storage[0].getAmount()); // Planning never mutates live source stacks.
        org.mockito.ArgumentCaptor<ItemStack[]> written = org.mockito.ArgumentCaptor.forClass(ItemStack[].class);
        verify(inventory).setStorageContents(written.capture());
        assertEquals(64, written.getValue()[0].getAmount());
        when(inventory.getStorageContents()).thenReturn(written.getValue());
        ItemStack remaining = PropertyInventory.read(prisoner)[0];
        when(chest.getItem(0)).thenReturn(remaining);
        String before = prisoner.getInventory();
        assertFalse(PropertyInventory.reclaim(fixture.getPlayer(), prisoner, chest, 0, 1));
        assertEquals(before, prisoner.getInventory());
    }

    @Test public void reclaimedPropertySurvivesSavedRecordReloadAndReleaseWithoutDuplication() throws Exception {
        serialization();
        ItemStack[] saved = new ItemStack[41];
        saved[7] = new ItemStack(Material.DIAMOND, 7); saved[39] = new ItemStack(Material.CARVED_PUMPKIN);
        prisoner.setInventory(Util.itemStackArrayToBase64(saved)); when(chest.getItem(7)).thenReturn(saved[7]);
        ItemStack[] carried = new ItemStack[41];
        PlayerInventory inventory = fixture.getPlayer().getInventory();
        when(inventory.getStorageContents()).thenAnswer(call -> Arrays.copyOf(carried, 36));
        when(inventory.getMaxStackSize()).thenReturn(64); when(inventory.getSize()).thenReturn(41);
        when(inventory.getArmorContents()).thenReturn(new ItemStack[4]);
        when(inventory.getItem(anyInt())).thenAnswer(call -> carried[(int) call.getArgument(0)]);
        doAnswer(call -> { System.arraycopy(call.getArgument(0), 0, carried, 0, 36); return null; })
                .when(inventory).setStorageContents(any(ItemStack[].class));
        doAnswer(call -> { carried[(int) call.getArgument(0)] = call.getArgument(1); return null; })
                .when(inventory).setItem(anyInt(), any(ItemStack.class));
        assertTrue(PropertyInventory.reclaim(fixture.getPlayer(), prisoner, chest, 7, Integer.MAX_VALUE));
        assertEquals(new ItemStack(Material.DIAMOND, 7), carried[0]);
        assertNull(PropertyInventory.read(prisoner)[7]);
        carried[7] = new ItemStack(Material.EMERALD, 3);
        Prisoner reloaded = new Prisoner(prisoner.getUUID().toString(), "Test", 60000L);
        reloaded.setInventory(prisoner.getInventory());
        plugin.getConfig().set("jailing.during.moveProtection", true);
        PropertyInventory.restore(fixture.getPlayer(), reloaded, null, true);
        assertEquals(new ItemStack(Material.DIAMOND, 7), carried[0]);
        assertEquals(new ItemStack(Material.EMERALD, 3), carried[7]);
        assertEquals(saved[39], carried[39]);
        verify(inventory, never()).clear();
    }

    @Test public void rightClickClaimsOnlyOneAndRejectsMismatchedDisplay() {
        serialization();
        ItemStack item = new ItemStack(Material.DIAMOND, 7);
        prisoner.setInventory(Util.itemStackArrayToBase64(new ItemStack[] {item}));
        PlayerInventory inventory = fixture.getPlayer().getInventory();
        when(inventory.getStorageContents()).thenReturn(new ItemStack[36]); when(inventory.getMaxStackSize()).thenReturn(64);
        when(chest.getItem(0)).thenReturn(new ItemStack(Material.EMERALD));
        assertFalse(PropertyInventory.reclaim(fixture.getPlayer(), prisoner, chest, 0, 1));
        when(chest.getItem(0)).thenReturn(item);
        assertTrue(PropertyInventory.reclaim(fixture.getPlayer(), prisoner, chest, 0, 1));
        assertEquals(6, PropertyInventory.read(prisoner)[0].getAmount());
        assertTrue(ItemBytes.isCurrent(prisoner.getInventory()));
    }

    @Test public void escapeModeAllowsGearChangesAndDoesNotForceItBackOn() {
        cell.setPrisoner(prisoner);
        plugin.getConfig().set("jailing.during.moveProtection", false);
        JailUniform uniform = new JailUniform(plugin);
        assertFalse(uniform.required(fixture.getPlayer()));
        for(ClickType click : ClickType.values()) {
            InventoryClickEvent event = new InventoryClickEvent(view(), InventoryType.SlotType.ARMOR, 5, click, InventoryAction.UNKNOWN);
            uniform.click(event); assertFalse(click.name(), event.isCancelled());
        }
        PlayerInteractEvent swap = new PlayerInteractEvent(fixture.getPlayer(), org.bukkit.event.block.Action.RIGHT_CLICK_AIR,
                new ItemStack(Material.DIAMOND_HELMET), null, BlockFace.SELF);
        uniform.equipByUse(swap); assertNotEquals(org.bukkit.event.Event.Result.DENY, swap.useItemInHand());
        PlayerDropItemEvent drop = mock(PlayerDropItemEvent.class); when(drop.getPlayer()).thenReturn(fixture.getPlayer());
        uniform.drop(drop); verify(drop, never()).setCancelled(true);
        PlayerArmorStandManipulateEvent stand = mock(PlayerArmorStandManipulateEvent.class); when(stand.getPlayer()).thenReturn(fixture.getPlayer());
        uniform.armorStand(stand); verify(stand, never()).setCancelled(true);
        uniform.reloaded(new com.graywolf336.jail.events.JailPluginReloadedEvent(plugin));
        verify(fixture.getPlayer().getInventory(), never()).setArmorContents(any());
        verify(fixture.getPlayer().getInventory(), never()).setItem(anyInt(), any());
    }

    @Test public void pendingReclamationRechecksProtectionAndOpenViewNextTick() {
        cell.setPrisoner(prisoner); when(chest.getSize()).thenReturn(54);
        plugin.getConfig().set("jailing.during.moveProtection", false);
        InventoryView opened = view(); when(fixture.getPlayer().getOpenInventory()).thenReturn(opened);
        when(fixture.getPlayer().isOnline()).thenReturn(true);
        List<Runnable> queued = new ArrayList<>();
        when(fixture.getServer().getScheduler().runTask(eq(plugin), any(Runnable.class)))
                .thenAnswer(call -> { queued.add(call.getArgument(1)); return null; });
        InventoryClickEvent click = new InventoryClickEvent(opened, InventoryType.SlotType.CONTAINER, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        protection.click(click); assertTrue(click.isCancelled()); assertEquals(1, queued.size());
        plugin.getConfig().set("jailing.during.moveProtection", true); queued.remove(0).run();
        verify(fixture.getPlayer().getInventory(), never()).setStorageContents(any());
        plugin.getConfig().set("jailing.during.moveProtection", false);
        protection.click(new InventoryClickEvent(opened, InventoryType.SlotType.CONTAINER, 0, ClickType.RIGHT, InventoryAction.PICKUP_HALF));
        assertEquals(1, queued.size());
        InventoryView closed = mock(InventoryView.class); when(fixture.getPlayer().getOpenInventory()).thenReturn(closed);
        queued.remove(0).run();
        verify(fixture.getPlayer().getInventory(), never()).setStorageContents(any());
    }

    @Test public void reEnablingProtectionStoresReplacementArmorBeforeIssuingUniform() {
        cell.setPrisoner(prisoner);
        when(fixture.getServer().getItemFactory().getItemMeta(any(Material.class))).thenAnswer(call -> {
            org.bukkit.inventory.meta.ItemMeta meta = mock(org.bukkit.inventory.meta.Damageable.class);
            when(meta.getPersistentDataContainer()).thenReturn(mock(org.bukkit.persistence.PersistentDataContainer.class));
            when(meta.clone()).thenReturn(meta); return meta;
        });
        ItemStack replacement = new ItemStack(Material.DIAMOND_HELMET);
        when(fixture.getPlayer().getInventory().getArmorContents()).thenReturn(new ItemStack[] {null, null, null, replacement});
        new JailUniform(plugin).reloaded(new com.graywolf336.jail.events.JailPluginReloadedEvent(plugin));
        verify(fixture.getPlayer().getInventory()).addItem(replacement);
        verify(fixture.getPlayer().getInventory(), times(2)).setArmorContents(any());
    }

    @Test public void legacyPackedPropertyMergesIntoStorageWithoutTreatingChestSlotsAsArmor() {
        serialization();
        ItemStack[] packed = new ItemStack[54]; packed[39] = new ItemStack(Material.DIAMOND, 2);
        prisoner.setInventory(ItemBytes.RECLAIMED + Util.itemStackArrayToBase64(packed));
        PlayerInventory inventory = fixture.getPlayer().getInventory(); when(inventory.getSize()).thenReturn(41);
        PropertyInventory.restore(fixture.getPlayer(), prisoner, null, true);
        verify(inventory).addItem(packed[39]);
        verify(inventory, never()).setItem(eq(39), any());
        verify(inventory, never()).clear();
    }

    private InventoryView view() {
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(chest);
        when(view.getPlayer()).thenReturn(fixture.getPlayer());
        return view;
    }

    @Test public void vacantAndOtherPrisonersCannotOpenEvenWithOpPermissions() {
        InventoryOpenEvent empty = new InventoryOpenEvent(view()); protection.open(empty); assertTrue(empty.isCancelled());
        cell.setPrisoner(prisoner);
        InventoryOpenEvent own = new InventoryOpenEvent(view()); protection.open(own); assertFalse(own.isCancelled());
        Player other = mock(Player.class); when(other.getUniqueId()).thenReturn(UUID.randomUUID()); when(other.isOp()).thenReturn(true);
        InventoryView view = view(); when(view.getPlayer()).thenReturn(other);
        InventoryOpenEvent unrelated = new InventoryOpenEvent(view); protection.open(unrelated); assertTrue(unrelated.isCancelled());
    }

    @Test public void bothHalvesAreProtectedButAnAdjacentSeparateChestIsNot() {
        Location registered = cell.getChestLocation();
        Chest left = mock(Chest.class), right = mock(Chest.class);
        when(left.getLocation()).thenReturn(registered);
        when(right.getLocation()).thenReturn(registered.clone().add(1,0,0));
        DoubleChest holder = mock(DoubleChest.class);
        when(holder.getLeftSide()).thenReturn(right); when(holder.getRightSide()).thenReturn(left);
        when(chest.getHolder()).thenReturn(holder);
        assertSame(cell, protection.findCell(chest));
        when(chest.getHolder()).thenReturn(right);
        assertNull(protection.findCell(chest));
    }

    @Test public void allClickModesAndBothInventoryAreasAreReadOnly() {
        for(ClickType click : ClickType.values()) {
            for(int raw : new int[] {-999, 0, 55}) {
                InventoryClickEvent event = new InventoryClickEvent(view(), InventoryType.SlotType.CONTAINER, raw, click, InventoryAction.UNKNOWN);
                protection.click(event); assertTrue(click.name(), event.isCancelled());
            }
        }
        InventoryDragEvent drag = mock(InventoryDragEvent.class); InventoryView dragView = view(); when(drag.getView()).thenReturn(dragView);
        when(drag.getWhoClicked()).thenReturn(fixture.getPlayer());
        protection.drag(drag); verify(drag).setCancelled(true);
    }

    @Test public void hoppersCannotWithdrawDepositOrCollectIntoPropertyChests() {
        Inventory other = mock(Inventory.class);
        for(boolean outgoing : new boolean[] {true, false}) {
            InventoryMoveItemEvent event = new InventoryMoveItemEvent(outgoing ? chest : other, new ItemStack(Material.DIAMOND), outgoing ? other : chest, true);
            protection.move(event); assertTrue(event.isCancelled());
        }
        InventoryPickupItemEvent pickup = mock(InventoryPickupItemEvent.class); when(pickup.getInventory()).thenReturn(chest);
        protection.pickup(pickup); verify(pickup).setCancelled(true);
    }

    @Test public void prisonerCannotMoveOrReplaceArmorThroughAnyClickType() {
        cell.setPrisoner(prisoner);
        JailUniform uniform = new JailUniform(plugin);
        for(ClickType click : ClickType.values()) {
            InventoryClickEvent event = new InventoryClickEvent(view(), InventoryType.SlotType.ARMOR, 5, click, InventoryAction.UNKNOWN);
            uniform.click(event); assertTrue(click.name(), event.isCancelled());
        }
        InventoryDragEvent drag = mock(InventoryDragEvent.class);
        when(drag.getWhoClicked()).thenReturn(fixture.getPlayer()); when(drag.getRawSlots()).thenReturn(Set.of(5));
        InventoryView view = view(); when(view.getSlotType(5)).thenReturn(InventoryType.SlotType.ARMOR); when(drag.getView()).thenReturn(view);
        uniform.drag(drag); verify(drag).setCancelled(true);
        PlayerInteractEvent swap = new PlayerInteractEvent(fixture.getPlayer(), org.bukkit.event.block.Action.RIGHT_CLICK_AIR,
                new ItemStack(Material.ELYTRA), null, BlockFace.SELF);
        uniform.equipByUse(swap); assertEquals(org.bukkit.event.Event.Result.DENY, swap.useItemInHand());
    }

    @Test public void unreadableSavedItemsDoNotClearInventory() {
        prisoner.setInventory("JAIL2N:invalid");
        try { PropertyInventory.restore(fixture.getPlayer(), prisoner, null, true); fail("Corrupt data must reject release"); }
        catch(IllegalStateException expected) { }
        verify(fixture.getPlayer().getInventory(), never()).clear();
        assertEquals("JAIL2N:invalid", prisoner.getInventory());
    }

    @Test public void legacyEncodingStillLoadsAndNewEncodingKeepsNullSlots() throws Exception {
        org.bukkit.UnsafeValues unsafe = mock(org.bukkit.UnsafeValues.class);
        when(unsafe.getDataVersion()).thenReturn(3105);
        when(unsafe.getMaterial(anyString(), anyInt())).thenAnswer(call -> Material.matchMaterial(call.getArgument(0)));
        when(fixture.getServer().getUnsafe()).thenReturn(unsafe);
        when(fixture.getServer().getItemFactory().getItemMeta(any(Material.class))).thenReturn(null);
        ItemStack[] items = {new ItemStack(Material.DIAMOND, 7), null, new ItemStack(Material.SHIELD)};
        assertArrayEquals(items, Util.itemStackArrayFromBase64(Util.legacyItemStackArrayToBase64(items)));
        assertArrayEquals(items, Util.itemStackArrayFromBase64(Util.itemStackArrayToBase64(items)));
    }

    @Test public void uniformCannotDropOrBreakAndDoesNotBecomeDeathLoot() {
        cell.setPrisoner(prisoner);
        ItemStack item = mock(ItemStack.class);
        org.bukkit.inventory.meta.ItemMeta meta = mock(org.bukkit.inventory.meta.ItemMeta.class);
        org.bukkit.persistence.PersistentDataContainer data = mock(org.bukkit.persistence.PersistentDataContainer.class);
        when(item.hasItemMeta()).thenReturn(true); when(item.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(data);
        when(data.has(any(NamespacedKey.class), eq(org.bukkit.persistence.PersistentDataType.BYTE))).thenReturn(true);
        JailUniform uniform = new JailUniform(plugin);
        org.bukkit.entity.Item dropped = mock(org.bukkit.entity.Item.class); when(dropped.getItemStack()).thenReturn(item);
        PlayerDropItemEvent drop = new PlayerDropItemEvent(fixture.getPlayer(), dropped); uniform.drop(drop); assertTrue(drop.isCancelled());
        PlayerItemDamageEvent damage = new PlayerItemDamageEvent(fixture.getPlayer(), item, 5); uniform.damage(damage); assertTrue(damage.isCancelled());
        org.bukkit.event.entity.PlayerDeathEvent death = mock(org.bukkit.event.entity.PlayerDeathEvent.class);
        when(death.getEntity()).thenReturn(fixture.getPlayer());
        ItemStack normal = new ItemStack(Material.DIAMOND);
        List<ItemStack> drops = new ArrayList<>(List.of(item, normal)); when(death.getDrops()).thenReturn(drops);
        uniform.death(death); assertEquals(List.of(normal), drops);
    }

    @Test public void armorLocksStopAfterReleaseAndNormalChestsRemainUsable() {
        InventoryClickEvent armor = new InventoryClickEvent(view(), InventoryType.SlotType.ARMOR, 5, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        new JailUniform(plugin).click(armor); assertFalse(armor.isCancelled());
        InventoryView other = view(); Inventory normal = mock(Inventory.class); when(other.getTopInventory()).thenReturn(normal);
        InventoryClickEvent click = new InventoryClickEvent(other, InventoryType.SlotType.CONTAINER, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        protection.click(click); assertFalse(click.isCancelled());
    }

    @Test public void movingFromNoCellToACellDoesNotLeaveADuplicatePrisoner() {
        Jail jail = plugin.getJailManager().getJail("property");
        jail.addPrisoner(prisoner);
        cell.setTeleport(new SimpleLocation(fixture.getPlayer().getLocation()));
        plugin.getPrisonerManager().transferPrisoner(jail, null, jail, cell, prisoner);
        assertFalse(jail.getPrisonersNotInCells().containsKey(prisoner.getUUID()));
        assertSame(prisoner, cell.getPrisoner());
        cell.removePrisoner();
        assertFalse(jail.isPlayerJailed(prisoner.getUUID()));
    }

    @Test public void jailUniformCannotBeSpentAsItemCurrency() {
        plugin.getConfig().set(com.graywolf336.jail.enums.Settings.JAILPAYITEM.getPath(), "leather_helmet");
        JailPayManager payments = new JailPayManager(plugin);
        ItemStack uniform = mock(ItemStack.class);
        org.bukkit.inventory.meta.ItemMeta meta = mock(org.bukkit.inventory.meta.ItemMeta.class);
        org.bukkit.persistence.PersistentDataContainer tags = mock(org.bukkit.persistence.PersistentDataContainer.class);
        when(uniform.getType()).thenReturn(Material.LEATHER_HELMET); when(uniform.getAmount()).thenReturn(64);
        when(uniform.hasItemMeta()).thenReturn(true); when(uniform.getItemMeta()).thenReturn(meta); when(meta.getPersistentDataContainer()).thenReturn(tags);
        when(tags.has(any(NamespacedKey.class), eq(org.bukkit.persistence.PersistentDataType.BYTE))).thenReturn(true);
        ItemStack[] contents = new ItemStack[41]; contents[39] = uniform;
        when(fixture.getPlayer().getInventory().getContents()).thenReturn(contents);
        assertFalse(payments.hasEnoughToPay(fixture.getPlayer(), 1));
        try { payments.pay(fixture.getPlayer(), 1); fail("Uniform cannot pay a sentence"); }
        catch(IllegalArgumentException expected) { }
        verify(fixture.getPlayer().getInventory(), never()).clear(anyInt());
    }
}
