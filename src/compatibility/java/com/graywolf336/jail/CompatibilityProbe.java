package com.graywolf336.jail;

import java.lang.reflect.Proxy;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import com.graywolf336.jail.beans.*;
import com.graywolf336.jail.enums.Settings;
import com.graywolf336.jail.events.PrisonerTimeChangeEvent;
import com.graywolf336.jail.listeners.ProtectionListener;

/** Runs only on disposable test servers. Never included in the release JAR. */
public final class CompatibilityProbe extends JavaPlugin implements Listener {
    private JailMain jail;
    private Jail origin, target;
    private Cell cell;
    private Prisoner prisoner, timerPrisoner;
    private Player player;
    private Inventory inventory;
    private Sign sign;
    private ItemStack customItem, originalHead;
    private Chest propertyChest;
    private boolean timerSeen;
    private int checks;
    private final List<String> helpMessages = new ArrayList<>();
    private final List<String> helpNavigation = new ArrayList<>();

    public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> guarded(this::start), 1);
    }

    private void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
        getLogger().info("CHECK PASS: " + label);
    }

    private void guarded(Checked action) {
        try { action.run(); }
        catch (Throwable failure) {
            getLogger().log(java.util.logging.Level.SEVERE, "COMPATIBILITY FAIL", failure);
            Bukkit.shutdown();
        }
    }

    private void start() throws Exception {
        jail = (JailMain) Bukkit.getPluginManager().getPlugin("JailReloaded");
        check(jail != null && jail.isEnabled(), "plugin enabled");
        check("1.0.0".equals(jail.getDescription().getVersion()), "fork version has no build or snapshot suffix");
        check(!jail.getConfig().contains("system.updates"), "legacy updater settings removed");
        check("1.19".equals(jail.getDescription().getAPIVersion()), "API baseline 1.19");
        check("sqlite".equals(jail.getConfig().getString("storage.type"))
                && new java.io.File(jail.getDataFolder(), "jail3.sqlite").isFile(), "SQLite default creates database");
        String documentedConfig = java.nio.file.Files.readString(new java.io.File(jail.getDataFolder(), "config.yml").toPath());
        check(documentedConfig.contains("Original Creator: graywolf336") && documentedConfig.contains("Fork By: Rismr1")
                && documentedConfig.contains("https://plugins.imagine-craft.net/"), "documented config header survives startup save");
        check(Arrays.stream(Material.values()).filter(material -> !material.isLegacy() && !material.isAir())
                .allMatch(material -> InteractionPolicy.blocks(material, jail.getConfig().getStringList(Settings.PREVENTINTERACTIONITEMS.getPath()))),
                "default wildcard covers every runtime material");
        World world = Bukkit.getWorlds().get(0);
        Location location = new Location(world, 4, 80, 4);
        origin = makeJail("probe", world, 0);
        target = makeJail("target", world, 100);
        cell = new Cell("cell");
        cell.setTeleport(new SimpleLocation(location));
        world.getBlockAt(5, 79, 5).setType(Material.STONE);
        world.getBlockAt(5, 80, 5).setType(Material.OAK_SIGN);
        sign = (Sign) world.getBlockAt(5, 80, 5).getState();
        cell.addSign(new SimpleLocation(sign.getLocation()));
        origin.addCell(cell, true);

        // Real server inventories and ItemMeta, with a proxy for the networked player.
        inventory = Bukkit.createInventory(null, 54);
        player = makePlayer(location);
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        org.bukkit.inventory.meta.BookMeta meta = (org.bukkit.inventory.meta.BookMeta) book.getItemMeta();
        meta.setTitle("Evidence"); meta.setAuthor("Probe"); meta.setPages("Retain this text");
        book.setItemMeta(meta);
        inventory.setItem(0, book);
        inventory.setItem(40, new ItemStack(Material.SHIELD));
        String encoded = Util.toBase64(player.getInventory());
        ItemStack[] restored = Util.itemStackArrayFromBase64(encoded);
        check(restored.length == 41 && book.equals(restored[0]) && restored[40].getType() == Material.SHIELD,
                "inventory metadata and offhand serialization");
        check(Util.fromBase64("").getSize() == 9, "empty legacy inventory");
        check(Util.itemStackArrayFromBase64(Util.itemStackArrayToBase64(new ItemStack[] {book, null}))[0].equals(book),
                "item-array serialization");
        check(Util.getWand().getItemMeta().hasDisplayName(), "jail and cell creation wand");
        checkLocales();
        checkHelp(location);
        checkWand(location);
        propertyChest = doubleChest(world, 14, 14);
        cell.setChestLocation(new SimpleLocation(propertyChest.getLocation()));
        customItem = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta custom = customItem.getItemMeta();
        custom.setDisplayName("Custom evidence"); custom.setLore(List.of("Preserve every byte", "Model and plugin data"));
        custom.setCustomModelData(1729);
        byte[] opaque = new byte[90000]; new Random(1729).nextBytes(opaque);
        custom.getPersistentDataContainer().set(new NamespacedKey(this, "opaque"), org.bukkit.persistence.PersistentDataType.BYTE_ARRAY, opaque);
        ((org.bukkit.inventory.meta.Damageable) custom).setDamage(17);
        customItem.setItemMeta(custom);
        inventory.setItem(7, customItem);
        originalHead = new ItemStack(Material.CARVED_PUMPKIN);
        ItemMeta headMeta = originalHead.getItemMeta(); headMeta.setDisplayName("Original custom head slot"); originalHead.setItemMeta(headMeta);
        inventory.setItem(39, originalHead);
        String nativeData = Util.itemStackArrayToBase64(new ItemStack[] {customItem, null, originalHead});
        check(nativeData.startsWith("JAIL2N:"), "native NBT byte serialization selected on Paper");
        check(Arrays.equals(new ItemStack[] {customItem, null, originalHead}, Util.itemStackArrayFromBase64(nativeData)),
                "custom model lore PDC damage and null slots survive native roundtrip");
        java.lang.reflect.Method nativeBytes = ItemStack.class.getMethod("serializeAsBytes");
        java.lang.reflect.Method nativeRead = ItemStack.class.getMethod("deserializeBytes", byte[].class);
        check(customItem.equals(nativeRead.invoke(null, (Object) nativeBytes.invoke(Util.itemStackArrayFromBase64(nativeData)[0]))),
                "complete native NBT item data retained across serialization");
        java.lang.reflect.Method legacyWrite = Util.class.getDeclaredMethod("legacyItemStackArrayToBase64", ItemStack[].class);
        legacyWrite.setAccessible(true);
        String legacyData = (String) legacyWrite.invoke(null, (Object) new ItemStack[] {customItem, originalHead});
        check(Arrays.equals(new ItemStack[] {customItem, originalHead}, Util.itemStackArrayFromBase64(legacyData)),
                "Bukkit fallback and legacy data preserve complex custom metadata");
        jail.getConfig().set(Settings.JAILPAYITEM.getPath(), "diamond");
        jail.getConfig().set(Settings.JAILPAYENABLED.getPath(), true);
        jail.getConfig().set(Settings.COUNTDOWNTIMEOFFLINE.getPath(), true);
        jail.saveConfig();
        jail.reloadEverything();
        check(jail.getJailStickManager().isValidStick(Material.STICK), "jail sticks loaded");
        check(jail.getJailPayManager() != null && jail.getJailPayManager().usingItemsForPayment(), "item payment without Vault");
        check(jail.getJailPayManager().calculateBill(new Prisoner(UUID.randomUUID().toString(), "Infinite", -1L)) == 10000,
                "infinite sentence payment price");
        if(Bukkit.getPluginManager().isPluginEnabled("Vault")) checkVault();
        check(Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "jailreloaded:jail list"), "namespaced jail command");
        check(jail.onTabComplete(player, jail.getCommand("jail"), "jailreloaded:jail", new String[] {""}).contains("create"),
                "namespaced command completion");
        UUID id = player.getUniqueId();
        jail.getHandCuffManager().addHandCuffs(id, location);
        check(jail.getHandCuffManager().isHandCuffed(id), "handcuff and unhandcuff state");
        com.graywolf336.jail.listeners.HandCuffListener handcuffs = new com.graywolf336.jail.listeners.HandCuffListener(jail);
        PlayerMoveEvent cuffMove = new PlayerMoveEvent(player, location, location.clone().add(5, 0, 0));
        handcuffs.onPlayerMove(cuffMove);
        check(cuffMove.getTo().equals(location), "handcuffed movement stays at anchor");
        PlayerTeleportEvent cuffTeleport = new PlayerTeleportEvent(player, location, location.clone().add(50, 0, 0));
        handcuffs.onPlayerTeleport(cuffTeleport);
        check(cuffTeleport.getTo().equals(location), "handcuffed teleport redirects without recursion");
        jail.getHandCuffManager().removeHandCuffs(id);
        prisoner = new Prisoner(id.toString(), player.getName(), true, 120000L, "Probe", "Compatibility");
        jail.getPrisonerManager().prepareJail(origin, cell, player, prisoner);
        Bukkit.getScheduler().runTaskLater(this, () -> guarded(this::afterJailing), 35);
    }

    private Jail makeJail(String name, World world, int offset) {
        Jail result = new Jail(jail, name);
        result.setWorld(world.getName());
        result.setMinPoint(new int[] {offset, 60, 0});
        result.setMaxPoint(new int[] {offset + 30, 100, 30});
        result.setTeleportIn(new Location(world, offset + 4, 80, 4));
        result.setTeleportFree(new Location(world, offset + 40, 80, 4));
        jail.getJailManager().addJail(result, false);
        return result;
    }

    private void afterJailing() throws Exception {
        UUID id = player.getUniqueId();
        check(jail.getJailManager().isPlayerJailed(id) && cell.hasPrisoner(), "online jailing and automatic cell data");
        check(!prisoner.getInventory().isEmpty(), "stored inventory during jailing");
        check(player.getInventory().getHelmet().getType() == Material.LEATHER_HELMET, "default inmate clothing");
        check(JailUniform.isUniform(player.getInventory().getHelmet()) && player.getInventory().getHelmet().getItemMeta().isUnbreakable(),
                "uniform is marked and unbreakable");
        checkPropertySecurity();
        check(((Sign) sign.getBlock().getState()).getLine(0).contains(player.getName()), "cell sign updates");
        try {
            java.lang.reflect.Method waxed = Sign.class.getMethod("isWaxed");
            check(Boolean.TRUE.equals(waxed.invoke(sign.getBlock().getState())), "registered signs are natively waxed");
        } catch(NoSuchMethodException olderApi) {
            check(true, "sign protection retains 1.19 API compatibility");
        }
        SignChangeEvent edit = new SignChangeEvent(sign.getBlock(), player, new String[] {"overwrite", "", "", ""});
        Bukkit.getPluginManager().callEvent(edit);
        check(edit.isCancelled(), "registered sign rejects player text edits");
        check(player.getScoreboard().getObjective("test") != null, "sentence scoreboard");

        ProtectionListener protections = new ProtectionListener(jail);
        BlockBreakEvent breakEvent = new BlockBreakEvent(sign.getWorld().getBlockAt(4, 79, 4), player);
        protections.protectionBlockBreaking(breakEvent);
        check(breakEvent.isCancelled(), "block breaking protection");
        jail.getConfig().set(Settings.PREVENTINTERACTIONITEMS.getPath(), Arrays.asList("ender_pearl"));
        PlayerInteractEvent useEvent = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR,
                new ItemStack(Material.ENDER_PEARL), null, BlockFace.SELF, EquipmentSlot.OFF_HAND);
        protections.interactionProtection(useEvent);
        check(useEvent.useItemInHand() == Event.Result.DENY, "offhand item interaction protection");
        PlayerCommandPreprocessEvent commandEvent = new PlayerCommandPreprocessEvent(player, "/spawn");
        protections.commandProtection(commandEvent);
        check(commandEvent.isCancelled(), "command protection");
        jail.getJailManager().addCacheObject(new CachePrisoner(origin, prisoner));
        PlayerMoveEvent move = new PlayerMoveEvent(player, cell.getTeleport(), new Location(sign.getWorld(), 60, 80, 60));
        jail.getPlayerMoveListener().moveProtection(move);
        check(origin.isInside(move.getTo()), "escape prevention");
        AsyncPlayerChatEvent chat = new AsyncPlayerChatEvent(false, player, "muted", new HashSet<>());
        new com.graywolf336.jail.listeners.PlayerListener(jail).chatting(chat);
        check(chat.isCancelled(), "muted prisoner chat");

        jail.getPrisonerManager().unJail(origin, cell, player, prisoner, Bukkit.getConsoleSender());
        check(!jail.getJailManager().isPlayerJailed(id) && !cell.hasPrisoner(), "release and cell cleanup");
        check(inventory.contains(Material.WRITTEN_BOOK) && inventory.contains(Material.SHIELD), "release inventory restoration");
        check(customItem.equals(inventory.getItem(7)) && originalHead.equals(inventory.getItem(39))
                && inventory.getItem(40).getType() == Material.SHIELD, "release restores exact custom item armor and offhand slots");
        check(propertyChest.getInventory().isEmpty(), "property chest cleared after successful restoration");
        check(player.getScoreboard().getObjective("test") == null, "release scoreboard cleanup");
        checkReclamation();

        Prisoner offline = new Prisoner(UUID.randomUUID().toString(), "OfflineProbe", 60000L, "Probe", "Transfer");
        jail.getPrisonerManager().prepareJail(origin, null, null, offline);
        jail.getPrisonerManager().transferPrisoner(origin, null, target, null, offline);
        check(target.getPrisoner(offline.getUUID()) != null && origin.getPrisoner(offline.getUUID()) == null, "offline transfer");
        jail.getPrisonerManager().forceRelease(offline, Bukkit.getConsoleSender());
        check(!jail.getJailManager().isPlayerJailed(offline.getUUID()), "force release");

        // Let the production scheduler count down a prisoner and deliver its custom event.
        timerPrisoner = new Prisoner(UUID.randomUUID().toString(), "TimerProbe", 60000L);
        jail.getPrisonerManager().prepareJail(origin, null, null, timerPrisoner);
        timerPrisoner.setOfflinePending(false);
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskLater(this, () -> guarded(this::finish), 210);
    }

    private Chest doubleChest(World world, int x, int z) {
        Block a = world.getBlockAt(x, 80, z), b = a.getRelative(BlockFace.EAST);
        a.setType(Material.CHEST, false); b.setType(Material.CHEST, false);
        org.bukkit.block.data.type.Chest left = (org.bukkit.block.data.type.Chest) a.getBlockData();
        org.bukkit.block.data.type.Chest right = (org.bukkit.block.data.type.Chest) b.getBlockData();
        left.setFacing(BlockFace.NORTH); right.setFacing(BlockFace.NORTH);
        left.setType(org.bukkit.block.data.type.Chest.Type.LEFT); right.setType(org.bukkit.block.data.type.Chest.Type.RIGHT);
        a.setBlockData(left, false); b.setBlockData(right, false);
        if(((Chest) a.getState()).getInventory().getSize() != 54) {
            left.setType(org.bukkit.block.data.type.Chest.Type.RIGHT); right.setType(org.bukkit.block.data.type.Chest.Type.LEFT);
            a.setBlockData(left, false); b.setBlockData(right, false);
        }
        return (Chest) a.getState();
    }

    private void checkReclamation() throws Exception {
        prisoner = new Prisoner(player.getUniqueId().toString(), player.getName(), 120000L, "Probe", "Reclaim");
        cell.setPrisoner(prisoner);
        java.lang.reflect.Method jailNow = PrisonerManager.class.getDeclaredMethod("jailPrisoner", Jail.class,
                com.graywolf336.jail.interfaces.ICell.class, Player.class, Prisoner.class);
        jailNow.setAccessible(true); jailNow.invoke(jail.getPrisonerManager(), origin, cell, player, prisoner);
        checkAuditProtections();
        CellChestProtection protection = new CellChestProtection(jail);
        check(!protection.mayReclaim(player, cell), "movement protection keeps property read only");
        jail.getConfig().set(Settings.MOVEPROTECTION.getPath(), false);
        check(protection.mayReclaim(player, cell), "escape enabled permits assigned prisoner reclamation");
        check(!new JailUniform(jail).required(player), "escape enabled unlocks jail gear");
        check(!protection.mayReclaim(makePlayer(cell.getTeleport(), false), cell), "escape enabled still denies outsiders");
        java.lang.reflect.Method reclaim = PropertyInventory.class.getDeclaredMethod("reclaim", Player.class, Prisoner.class, Inventory.class, int.class, int.class);
        reclaim.setAccessible(true);
        ItemStack uniform = player.getInventory().getHelmet().clone();
        ItemStack[] full = new ItemStack[36]; Arrays.fill(full, new ItemStack(Material.STONE, 64));
        player.getInventory().setStorageContents(full);
        String saved = prisoner.getInventory();
        check(!(Boolean) reclaim.invoke(null, player, prisoner, propertyChest.getInventory(), 7, Integer.MAX_VALUE)
                && saved.equals(prisoner.getInventory()), "full inventory retains complete saved property");
        player.getInventory().setStorageContents(new ItemStack[36]);
        checkFailedStorageHandoffs();
        check((Boolean) reclaim.invoke(null, player, prisoner, propertyChest.getInventory(), 7, Integer.MAX_VALUE), "custom property reclaimed into storage");
        check(customItem.equals(player.getInventory().getItem(0)), "reclaimed item retains native NBT custom model and 90KB PDC");
        check(Util.itemStackArrayFromBase64(prisoner.getInventory())[7] == null && propertyChest.getInventory().getItem(7) == null,
                "reclamation removes saved and displayed copies");
        check(uniform.equals(player.getInventory().getHelmet()), "reclaiming leaves gear available for voluntary removal");
        jail.getJailIO().saveCell(origin, cell, true);
        java.lang.reflect.Method connection = JailIO.class.getDeclaredMethod("getConnection"); connection.setAccessible(true);
        try(java.sql.PreparedStatement query = ((java.sql.Connection) connection.invoke(jail.getJailIO())).prepareStatement(
                "SELECT inventory FROM " + jail.getConfig().getString("storage.mysql.prefix") + "prisoners WHERE uuid=?")) {
            query.setString(1, player.getUniqueId().toString());
            try(java.sql.ResultSet record = query.executeQuery()) {
                check(record.next() && prisoner.getInventory().equals(new String(record.getBytes(1), java.nio.charset.StandardCharsets.UTF_8)),
                        "remaining property and reclaim release mode persist to SQLite");
            }
        }
        player.getInventory().setItem(7, new ItemStack(Material.EMERALD, 3));
        jail.getPrisonerManager().transferPrisoner(origin, cell, target, null, prisoner);
        jail.getPrisonerManager().transferPrisoner(target, null, origin, cell, prisoner);
        check(propertyChest.getInventory().getItem(7) == null, "transfer cannot repopulate reclaimed property");
        jail.getConfig().set(Settings.MOVEPROTECTION.getPath(), true);
        jail.getPrisonerManager().unJail(origin, cell, player, prisoner, Bukkit.getConsoleSender());
        check(customItem.equals(player.getInventory().getItem(0)) && player.getInventory().getItem(7).getAmount() == 3,
                "release after config change preserves reclaimed and earned items");
        check(originalHead.equals(player.getInventory().getItem(39)) && player.getInventory().getItem(40).getType() == Material.SHIELD,
                "release returns remaining armor and offhand property");
        check(Arrays.stream(player.getInventory().getContents()).filter(Objects::nonNull).filter(customItem::equals).count() == 1,
                "release never duplicates reclaimed custom item");
    }

    private void checkAuditProtections() throws Exception {
        PlayerTeleportEvent teleport = new PlayerTeleportEvent(player,cell.getTeleport(),cell.getTeleport().clone().add(60,0,0));
        Bukkit.getPluginManager().callEvent(teleport);
        check(teleport.isCancelled() || origin.isInside(teleport.getTo()),"actual teleport event cannot escape jail");
        PlayerMoveEvent vertical = new PlayerMoveEvent(player,cell.getTeleport(),cell.getTeleport().clone().add(0,200,0));
        jail.getPlayerMoveListener().moveProtection(vertical);
        check(vertical.isCancelled() || origin.isInside(vertical.getTo()),"vertical movement cannot escape cuboid");
        com.graywolf336.jail.listeners.CacheListener cache = new com.graywolf336.jail.listeners.CacheListener(jail);
        com.graywolf336.jail.events.PrePrisonerTransferredEvent transfer = new com.graywolf336.jail.events.PrePrisonerTransferredEvent(origin,cell,target,null,prisoner,player,"Probe");
        cache.beforeTransferringListener(transfer); transfer.setCancelled(true);
        check(jail.getJailManager().inCache(player.getUniqueId()),"canceled transfer keeps prisoner movement state");
        PlayerKickEvent kick = new PlayerKickEvent(player,"probe",""); cache.kickListener(kick); kick.setCancelled(true);
        check(jail.getJailManager().inCache(player.getUniqueId()),"canceled kick keeps prisoner movement state");
        Cell duplicate = new Cell("duplicate"); duplicate.setChestLocation(new SimpleLocation(propertyChest.getBlock().getRelative(BlockFace.EAST).getLocation()));
        boolean sharedRejected = false;
        try { CellResources.requireAvailable(jail.getJailManager(),origin,duplicate); }
        catch(IllegalArgumentException expected) { sharedRejected = true; }
        check(sharedRejected,"other half of registered property chest cannot be assigned twice");
        List<Block> affected = new ArrayList<>(List.of(sign.getWorld().getBlockAt(4,79,4),sign.getWorld().getBlockAt(60,79,4)));
        java.lang.reflect.Constructor<?> constructor = Arrays.stream(BlockExplodeEvent.class.getConstructors())
                .max(java.util.Comparator.comparingInt(java.lang.reflect.Constructor::getParameterCount)).orElseThrow();
        Object[] arguments = new Object[constructor.getParameterCount()]; Class<?>[] types = constructor.getParameterTypes();
        for(int i=0;i<types.length;i++) {
            if(types[i] == Block.class) arguments[i] = sign.getBlock();
            else if(types[i] == BlockState.class) arguments[i] = sign.getBlock().getState();
            else if(List.class.isAssignableFrom(types[i])) arguments[i] = affected;
            else if(types[i] == float.class) arguments[i] = 1.0f;
            else if(types[i].isEnum()) arguments[i] = types[i].getEnumConstants()[0];
            else throw new IllegalStateException("Unknown block explosion event argument: " + types[i]);
        }
        BlockExplodeEvent explosion = (BlockExplodeEvent)constructor.newInstance(arguments);
        Bukkit.getPluginManager().callEvent(explosion);
        check(affected.size() == 1 && affected.get(0).getX() == 60,"block explosion preserves jail region while allowing outside damage");
    }

    private void checkFailedStorageHandoffs() throws Exception {
        java.lang.reflect.Method get = JailIO.class.getDeclaredMethod("getConnection"); get.setAccessible(true);
        java.sql.Connection sql = (java.sql.Connection)get.invoke(jail.getJailIO());
        String table = jail.getConfig().getString("storage.mysql.prefix") + "prisoners";
        String before = prisoner.getInventory(); ItemStack[] carried = player.getInventory().getContents();
        boolean rejected = false;
        try(java.sql.Statement statement = sql.createStatement()) {
            statement.execute("CREATE TRIGGER probe_block_release BEFORE DELETE ON " + table + " BEGIN SELECT RAISE(ABORT,'probe delete failure'); END");
            try { jail.getPrisonerManager().unJail(origin,cell,player,prisoner,Bukkit.getConsoleSender()); }
            catch(IllegalStateException expected) { rejected = true; }
            finally { statement.execute("DROP TRIGGER probe_block_release"); }
        }
        check(rejected && cell.getPrisoner() == prisoner && before.equals(prisoner.getInventory())
                && Arrays.equals(carried,player.getInventory().getContents()),"failed release delete retains inventory property and prisoner");
        rejected = false;
        java.lang.reflect.Method reclaim = PropertyInventory.class.getDeclaredMethod("reclaim",Player.class,Prisoner.class,Inventory.class,int.class,int.class,Runnable.class);
        reclaim.setAccessible(true);
        try(java.sql.Statement statement = sql.createStatement()) {
            statement.execute("CREATE TRIGGER probe_block_reclaim BEFORE INSERT ON " + table + " BEGIN SELECT RAISE(ABORT,'probe write failure'); END");
            try { reclaim.invoke(null,player,prisoner,propertyChest.getInventory(),7,1,
                    (Runnable)() -> jail.getJailIO().saveCellOrPrisoner(origin,cell,prisoner)); }
            catch(java.lang.reflect.InvocationTargetException expected) {
                if(!(expected.getCause() instanceof IllegalStateException)) throw expected;
                rejected = true;
            } finally { statement.execute("DROP TRIGGER probe_block_reclaim"); }
        }
        check(rejected && before.equals(prisoner.getInventory()) && customItem.equals(propertyChest.getInventory().getItem(7))
                && Arrays.equals(carried,player.getInventory().getContents()),"failed reclaim write awards no items and retains complete native data");
    }

    private void checkPropertySecurity() throws Exception {
        CellChestProtection protection = new CellChestProtection(jail);
        check(protection.findCell(propertyChest.getInventory()) == cell, "registered double chest resolves to its cell");
        check(protection.mayView(player, cell), "assigned prisoner can view own chest");
        check(!protection.mayView(makePlayer(cell.getTeleport(), false), cell), "outsider cannot view occupied cell chest");
        cell.removePrisoner();
        check(!protection.mayView(player, cell), "vacant cell chest cannot be opened");
        cell.setPrisoner(prisoner);
        org.bukkit.event.inventory.InventoryMoveItemEvent hopper = new org.bukkit.event.inventory.InventoryMoveItemEvent(
                propertyChest.getInventory(), new ItemStack(Material.DIAMOND), Bukkit.createInventory(null, 9), true);
        Bukkit.getPluginManager().callEvent(hopper);
        check(hopper.isCancelled(), "hopper cannot extract stored property");
        Chest partner = (Chest) propertyChest.getBlock().getRelative(BlockFace.EAST).getState();
        org.bukkit.event.inventory.InventoryMoveItemEvent halfHopper = new org.bukkit.event.inventory.InventoryMoveItemEvent(
                partner.getBlockInventory(), new ItemStack(Material.DIAMOND), Bukkit.createInventory(null, 9), true);
        Bukkit.getPluginManager().callEvent(halfHopper);
        check(halfHopper.isCancelled(), "hopper cannot extract through the other chest half");
        BlockBreakEvent breakChest = new BlockBreakEvent(propertyChest.getBlock(), makePlayer(cell.getTeleport(), false));
        Bukkit.getPluginManager().callEvent(breakChest);
        check(breakChest.isCancelled(), "registered property chest cannot be broken");
        PlayerItemDamageEvent armorDamage = new PlayerItemDamageEvent(player, player.getInventory().getHelmet(), 1);
        Bukkit.getPluginManager().callEvent(armorDamage);
        check(armorDamage.isCancelled(), "required uniform cannot break from damage");
        String oldPaymentItem = jail.getConfig().getString(Settings.JAILPAYITEM.getPath());
        jail.getConfig().set(Settings.JAILPAYITEM.getPath(), "leather_helmet");
        java.lang.reflect.Constructor<JailPayManager> paymentConstructor = JailPayManager.class.getDeclaredConstructor(JailMain.class);
        paymentConstructor.setAccessible(true);
        JailPayManager uniformPayments = paymentConstructor.newInstance(jail);
        jail.getConfig().set(Settings.JAILPAYITEM.getPath(), oldPaymentItem);
        check(!uniformPayments.hasEnoughToPay(player, 1), "required uniform cannot be spent as item currency");
        PlayerInteractEvent swap = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, new ItemStack(Material.DIAMOND_HELMET), null, BlockFace.SELF);
        Bukkit.getPluginManager().callEvent(swap);
        check(swap.useItemInHand() == Event.Result.DENY, "right click cannot replace jail uniform");
        String saved = prisoner.getInventory();
        Cell destination = new Cell("property-target"); destination.setTeleport(new SimpleLocation(target.getTeleportIn()));
        destination.setChestLocation(new SimpleLocation(doubleChest(propertyChest.getWorld(), 114, 14).getLocation()));
        target.addCell(destination, true);
        jail.getPrisonerManager().transferPrisoner(origin, cell, target, destination, prisoner);
        check(saved.equals(prisoner.getInventory()) && customItem.equals(destination.getChest().getInventory().getItem(7)),
                "cell transfer keeps custom property snapshot and display");
        jail.getPrisonerManager().transferPrisoner(target, destination, origin, null, prisoner);
        check(saved.equals(prisoner.getInventory()) && destination.getChest().getInventory().isEmpty(),
                "transfer without a chest retains exact stored item bytes");
        jail.getPrisonerManager().transferPrisoner(origin, null, origin, cell, prisoner);
        check(saved.equals(prisoner.getInventory()) && customItem.equals(propertyChest.getInventory().getItem(7)),
                "transfer back to chest restores protected property display");
    }

    @EventHandler
    public void timer(PrisonerTimeChangeEvent event) {
        if (event.getPrisoner() == timerPrisoner) {
            check(Bukkit.isPrimaryThread(), "timer custom events on server thread");
            timerSeen = true;
        }
    }

    private void finish() throws Exception {
        check(timerSeen && timerPrisoner.getRemainingTime() < 60000L, "production sentence timer");
        java.lang.reflect.Method save = JailIO.class.getDeclaredMethod("saveJail", Jail.class);
        save.setAccessible(true);
        save.invoke(jail.getJailIO(), origin);
        java.lang.reflect.Method load = JailIO.class.getDeclaredMethod("loadJails");
        load.setAccessible(true);
        load.invoke(jail.getJailIO());
        check(jail.getJailManager().getPrisoner(timerPrisoner.getUUID()) != null, "storage save and load");
        jail.getConfig().set("storage.type", "flatfile");
        jail.saveConfig(); jail.reloadEverything();
        load.invoke(jail.getJailIO());
        check(jail.getJailManager().getPrisoner(timerPrisoner.getUUID()) != null, "SQLite to flatfile migration");
        long preciseTime = 987654321012L;
        jail.getJailManager().getPrisoner(timerPrisoner.getUUID()).setRemainingTime(preciseTime);
        jail.getConfig().set("storage.type", "sqlite");
        jail.getConfig().set("storage.mysql.prefix", "feedback_");
        jail.saveConfig(); jail.reloadEverything();
        load.invoke(jail.getJailIO());
        check(jail.getJailManager().getPrisoner(timerPrisoner.getUUID()) != null, "SQLite storage migration and load");
        check(jail.getJailManager().getPrisoner(timerPrisoner.getUUID()).getRemainingTime() == preciseTime, "SQLite sentence precision");
        if(System.getProperty("jail.probe.mysql.port") != null) {
            jail.getConfig().set("storage.type", "mysql");
            jail.getConfig().set("storage.mysql.host", "127.0.0.1");
            jail.getConfig().set("storage.mysql.port", Integer.parseInt(System.getProperty("jail.probe.mysql.port")));
            jail.getConfig().set("storage.mysql.database", System.getProperty("jail.probe.mysql.database", "jail_probe"));
            jail.getConfig().set("storage.mysql.username", System.getenv().getOrDefault("JAIL_PROBE_MYSQL_USER", "root"));
            jail.getConfig().set("storage.mysql.password", System.getenv("JAIL_PROBE_MYSQL_PASSWORD"));
            jail.getConfig().set("storage.mysql.prefix", "probe_" + System.currentTimeMillis() + "_");
            jail.saveConfig(); jail.reloadEverything();
            load.invoke(jail.getJailIO());
            check(jail.getJailManager().getPrisoner(timerPrisoner.getUUID()) != null, "MySQL storage migration and load");
            check(jail.getJailManager().getPrisoner(timerPrisoner.getUUID()).getRemainingTime() == preciseTime, "MySQL sentence precision");
            String table = jail.getConfig().getString("storage.mysql.prefix") + "prisoners";
            try(java.sql.Connection sql = java.sql.DriverManager.getConnection("jdbc:mysql://127.0.0.1:"
                    + System.getProperty("jail.probe.mysql.port") + "/jail_probe", "root", System.getenv("JAIL_PROBE_MYSQL_PASSWORD"));
                    java.sql.Statement statement = sql.createStatement()) {
                statement.executeUpdate("ALTER TABLE `" + table + "` MODIFY inventory BLOB NULL, MODIFY armor BLOB NULL");
                jail.reloadEverything();
                try(java.sql.ResultSet columns = sql.getMetaData().getColumns(null, null, table, "inventory")) {
                    check(columns.next() && columns.getString("TYPE_NAME").equalsIgnoreCase("LONGBLOB"),
                            "legacy MySQL item columns upgraded beyond 64 KiB");
                }
            }
            Prisoner large = new Prisoner(UUID.randomUUID().toString(), "LargeItemProbe", 60000L);
            large.setInventory(Util.itemStackArrayToBase64(new ItemStack[] {customItem}));
            check(large.getInventory().length() > 65535, "large custom item exceeds old MySQL BLOB limit");
            Jail loadedJail = jail.getJailManager().getJail("probe"); loadedJail.addPrisoner(large);
            save.invoke(jail.getJailIO(), loadedJail); load.invoke(jail.getJailIO());
            Prisoner loadedLarge = jail.getJailManager().getPrisoner(large.getUUID());
            check(loadedLarge != null && customItem.equals(Util.itemStackArrayFromBase64(loadedLarge.getInventory())[0]),
                    "large custom item survives MySQL save and reload");
        }
        jail.getConfig().set(Settings.JAILSTICKENABLED.getPath(), false);
        jail.getConfig().set(Settings.SCOREBOARDENABLED.getPath(), false);
        jail.saveConfig(); jail.reloadEverything();
        check(jail.getJailStickManager() == null, "reload disables jail sticks");
        checkReloadSafety(load);
        getLogger().info("COMPATIBILITY PASS: " + Bukkit.getBukkitVersion() + " checks=" + checks);
        Bukkit.shutdown();
    }

    private void checkReloadSafety(java.lang.reflect.Method load) throws Exception {
        java.nio.file.Path configPath = new java.io.File(jail.getDataFolder(), "config.yml").toPath();
        String valid = java.nio.file.Files.readString(configPath);
        jail.getConfig().set(Settings.DEBUG.getPath(), true);
        jail.getConfig().set(Settings.MAXAFKTIME.getPath(), "2m");
        jail.saveConfig(); jail.reloadEverything();
        check(jail.inDebug(), "reload refreshes debug flag");
        java.lang.reflect.Field afk = JailTimer.class.getDeclaredField("afkTime");
        afk.setAccessible(true);
        java.lang.reflect.Field timer = JailMain.class.getDeclaredField("jt"); timer.setAccessible(true);
        check(Long.valueOf(120000).equals(afk.get(timer.get(jail))), "reload refreshes AFK limit");
        int tasks = Bukkit.getScheduler().getPendingTasks().stream().filter(task -> task.getOwner() == jail).toList().size();
        jail.reloadEverything(); jail.reloadEverything();
        check(tasks == Bukkit.getScheduler().getPendingTasks().stream().filter(task -> task.getOwner() == jail).toList().size(),
                "repeated reloads do not duplicate scheduled tasks");
        java.nio.file.Files.writeString(configPath, "system: [broken\n");
        boolean rejected = false;
        try { jail.reloadEverything(); } catch(org.bukkit.configuration.InvalidConfigurationException expected) { rejected = true; }
        check(rejected && jail.inDebug(), "invalid YAML rejected without replacing live settings");
        check(java.nio.file.Files.readString(configPath).contains("[broken"), "invalid YAML left intact for correction");
        java.nio.file.Files.writeString(configPath, valid);
        jail.reloadEverything();
        java.nio.file.Files.delete(configPath);
        jail.reloadEverything();
        org.bukkit.configuration.file.YamlConfiguration regenerated = new org.bukkit.configuration.file.YamlConfiguration();
        regenerated.load(configPath.toFile());
        check(!regenerated.getComments("storage.type").isEmpty()
                && !regenerated.getComments(Settings.MOVEPENALTY.getPath()).isEmpty()
                && java.nio.file.Files.readString(configPath).contains("Original Creator: graywolf336"),
                "deleted config regenerated with header and setting comments");
        check(!regenerated.contains("system.updates"), "regenerated config has no updater");
        load.invoke(jail.getJailIO());
        check(jail.getJailManager().getPrisoner(timerPrisoner.getUUID()) != null,
                "config regeneration preserves active prisoners in storage");
    }

    private void checkLocales() throws Exception {
        for (String code : LocaleManager.CODES) {
            jail.getConfig().set("system.language", code);
            LocaleManager.load(jail);
            String help = com.graywolf336.jail.command.CommandLocale.word("help");
            String players = com.graywolf336.jail.command.CommandLocale.word("players");
            helpMessages.clear();
            jail.onCommand(player, jail.getCommand("jail"), "jail", new String[] {help, players});
            String text = ChatColor.stripColor(String.join("\n", helpMessages));
            check(new java.io.File(jail.getDataFolder(), "locales/" + code + ".yml").isFile()
                    && text.contains(com.graywolf336.jail.enums.Lang.text("help.players"))
                    && text.contains("/jail ") && !text.contains("help.entry"), "localized help and subcommand execute: " + code);
            helpMessages.clear();
            jail.onCommand(player, jail.getCommand("jail"), "jail", new String[] {"help", "players"});
            check(ChatColor.stripColor(String.join("\n", helpMessages)).contains(com.graywolf336.jail.enums.Lang.text("help.players"))
                    && !com.graywolf336.jail.enums.Lang.text("creation.start").contains("\ufffd"), "English command retained with UTF-8 locale: " + code);
        }
        jail.getConfig().set("system.language", "en");
        LocaleManager.load(jail);
    }

    private void checkHelp(Location location) {
        Player regular = makePlayer(location, false);
        helpMessages.clear();
        jail.onCommand(regular, jail.getCommand("jail"), "jail", new String[0]);
        String text = ChatColor.stripColor(String.join("\n", helpMessages));
        check(text.contains("Player Commands") && !text.contains("Admin Commands"), "bare jail opens permission-filtered player help");
        check(text.indexOf("Originally By: graywolf336") < text.indexOf("Jail Commands"), "help credits above command title");
        helpMessages.clear(); helpNavigation.clear();
        jail.onCommand(player, jail.getCommand("jail"), "jail", new String[] {"help", "admin", "2"});
        text = ChatColor.stripColor(String.join("\n", helpMessages));
        check(text.contains("Admin Commands") && text.contains("Page 2/"), "admin help pagination");
        check(helpNavigation.stream().anyMatch(json -> json.contains("/jail help admin 1") && json.contains("/jail help admin 3")),
                "clickable previous and next links");
    }

    private void checkWand(Location location) {
        ItemStack previous = inventory.getItem(0);
        ItemStack wand = Util.getWand(); inventory.setItem(0, wand);
        JailManager manager = jail.getJailManager();
        manager.addCreatingCell(player.getName(), origin.getName(), "wandcell");
        CreationPlayer state = manager.getCellCreationPlayer(player.getName());
        state.setTeleportIn(location); state.setStep(2);
        Block selectedSign = location.getWorld().getBlockAt(8, 80, 8);
        selectedSign.getRelative(BlockFace.DOWN).setType(Material.STONE);
        selectedSign.setType(Material.OAK_SIGN);
        PlayerInteractEvent selection = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, wand, selectedSign, BlockFace.UP, EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(selection);
        check(selection.useInteractedBlock() == Event.Result.DENY && !state.getSigns().isEmpty(), "wand selects sign without editor interaction");
        PlayerInteractEvent secondHand = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, wand, selectedSign, BlockFace.UP, EquipmentSlot.OFF_HAND);
        Bukkit.getPluginManager().callEvent(secondHand);
        check(secondHand.useInteractedBlock() == Event.Result.DENY && state.getSigns().size() == 1, "offhand wand selection is consumed once");
        Block chest = location.getWorld().getBlockAt(10, 80, 10), partner = chest.getRelative(BlockFace.EAST);
        chest.setType(Material.CHEST, false); partner.setType(Material.CHEST, false);
        org.bukkit.block.data.type.Chest left = (org.bukkit.block.data.type.Chest) chest.getBlockData();
        org.bukkit.block.data.type.Chest right = (org.bukkit.block.data.type.Chest) partner.getBlockData();
        left.setFacing(BlockFace.NORTH); right.setFacing(BlockFace.NORTH);
        left.setType(org.bukkit.block.data.type.Chest.Type.LEFT); right.setType(org.bukkit.block.data.type.Chest.Type.RIGHT);
        chest.setBlockData(left, false); partner.setBlockData(right, false);
        if(((Chest) chest.getState()).getInventory().getSize() != 54) {
            left.setType(org.bukkit.block.data.type.Chest.Type.RIGHT); right.setType(org.bukkit.block.data.type.Chest.Type.LEFT);
            chest.setBlockData(left, false); partner.setBlockData(right, false);
        }
        state.setStep(3);
        PlayerInteractEvent chestSelection = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, wand, chest, BlockFace.UP, EquipmentSlot.HAND);
        // A separate listener instance removes the click-throttle delay from this isolated selection test.
        new com.graywolf336.jail.listeners.PlayerListener(jail).jailOrCellCreation(chestSelection);
        check(chestSelection.useInteractedBlock() == Event.Result.DENY && origin.getCell("wandcell") != null
                && origin.getCell("wandcell").hasChest(), "wand registers double chest without opening inventory");
        manager.removeCellCreationPlayer(player.getName());
        inventory.setItem(0, previous);
    }

    private void checkVault() throws Exception {
        final double[] balance = {1000D};
        net.milkbowl.vault.economy.Economy provider = (net.milkbowl.vault.economy.Economy) Proxy.newProxyInstance(
                net.milkbowl.vault.economy.Economy.class.getClassLoader(), new Class<?>[] {net.milkbowl.vault.economy.Economy.class},
                (proxy, method, args) -> {
                    switch(method.getName()) {
                        case "getName": return "CompatibilityEconomy";
                        case "currencyNamePlural": return "coins";
                        case "currencyNameSingular": return "coin";
                        case "isEnabled": case "hasAccount": return true;
                        case "getBalance": return balance[0];
                        case "has": return balance[0] >= ((Number) args[args.length - 1]).doubleValue();
                        case "withdrawPlayer":
                            double amount = ((Number) args[args.length - 1]).doubleValue();
                            balance[0] -= amount;
                            return new net.milkbowl.vault.economy.EconomyResponse(amount, balance[0],
                                    net.milkbowl.vault.economy.EconomyResponse.ResponseType.SUCCESS, "");
                        case "fractionalDigits": return 2;
                        case "format": return args[0] + " coins";
                        default: return null;
                    }
                });
        Bukkit.getServicesManager().register(net.milkbowl.vault.economy.Economy.class, provider, this, org.bukkit.plugin.ServicePriority.Highest);
        jail.getConfig().set(Settings.JAILPAYITEM.getPath(), "air");
        jail.getConfig().set(Settings.JAILPAYENABLED.getPath(), true);
        jail.saveConfig(); jail.reloadEverything();
        check(jail.getJailPayManager() != null && !jail.getJailPayManager().usingItemsForPayment(), "Vault economy provider binding");
        check(jail.getJailPayManager().hasEnoughToPay(player, 3D), "Vault balance check");
        jail.getJailPayManager().pay(player, 3D);
        check(balance[0] == 997D, "Vault currency withdrawal");
    }

    private Player makePlayer(Location initial) {
        return makePlayer(initial, true);
    }

    private Player makePlayer(Location initial, boolean administrator) {
        UUID id = UUID.nameUUIDFromBytes(("JailReloadedProbe" + administrator).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Location[] location = {initial.clone()};
        Scoreboard[] board = {Bukkit.getScoreboardManager().getMainScoreboard()};
        GameMode[] gameMode = {GameMode.SURVIVAL};
        Map<String, Integer> armor = new HashMap<>();
        armor.put("Boots", 36); armor.put("Leggings", 37); armor.put("Chestplate", 38); armor.put("Helmet", 39);
        PlayerInventory playerInventory = (PlayerInventory) Proxy.newProxyInstance(PlayerInventory.class.getClassLoader(), new Class<?>[] {PlayerInventory.class}, (proxy, method, args) -> {
            String name = method.getName();
            if (name.equals("getSize")) return 41;
            if (name.equals("getContents")) return Arrays.copyOf(inventory.getContents(), 41);
            if (name.equals("getStorageContents")) return Arrays.copyOf(inventory.getContents(), 36);
            if (name.equals("setStorageContents")) {
                ItemStack[] contents = (ItemStack[]) args[0];
                for(int i = 0; i < 36; i++) inventory.setItem(i, contents == null ? null : contents[i]);
                return null;
            }
            if (name.equals("getArmorContents")) return Arrays.copyOfRange(inventory.getContents(), 36, 40);
            if (name.equals("setArmorContents")) {
                ItemStack[] contents = (ItemStack[]) args[0];
                for (int i = 0; i < 4; i++) inventory.setItem(i + 36, contents == null ? null : contents[i]);
                return null;
            }
            if (name.startsWith("get") && armor.containsKey(name.substring(3))) return air(inventory.getItem(armor.get(name.substring(3))));
            if (name.startsWith("set") && armor.containsKey(name.substring(3))) { inventory.setItem(armor.get(name.substring(3)), (ItemStack) args[0]); return null; }
            if (name.equals("getItemInMainHand") || name.equals("getItemInHand")) return air(inventory.getItem(0));
            if (name.equals("getItemInOffHand")) return air(inventory.getItem(40));
            if (name.equals("getHeldItemSlot")) return 0;
            return Inventory.class.getMethod(name, method.getParameterTypes()).invoke(inventory, args);
        });
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getUniqueId": return id;
                case "getName": case "getDisplayName": return "ProbePlayer";
                case "getInventory": return playerInventory;
                case "getLocation": return location[0].clone();
                case "getWorld": return location[0].getWorld();
                case "getServer": return Bukkit.getServer();
                case "getGameMode": return gameMode[0];
                case "setGameMode": gameMode[0] = (GameMode) args[0]; return null;
                case "getFoodLevel": return 20;
                case "getHealth": return 20D;
                case "hasPermission": return administrator ? !"jail.cantbejailed".equals(args[0])
                        : ((String) args[0]).startsWith("jail.usercmd.") || "jail.command.jailhelp".equals(args[0]);
                case "sendMessage":
                    if(args[0] instanceof String) helpMessages.add((String) args[0]);
                    return null;
                case "spigot": return new Player.Spigot() {
                    @Override public void sendMessage(net.md_5.bungee.api.chat.BaseComponent message) {
                        helpNavigation.add(net.md_5.bungee.chat.ComponentSerializer.toString(message));
                    }
                };
                case "isOnline": case "isValid": return true;
                case "teleport": location[0] = ((Location) args[0]).clone(); return true;
                case "getScoreboard": return board[0];
                case "setScoreboard": board[0] = (Scoreboard) args[0]; return null;
                case "hashCode": return id.hashCode();
                case "equals": return proxy == args[0];
                case "toString": return "CompatibilityPlayer";
                default:
                    Class<?> type = method.getReturnType();
                    if (type == boolean.class) return false;
                    if (type == int.class) return 0;
                    if (type == double.class) return 0D;
                    if (type == long.class) return 0L;
                    return null;
            }
        });
    }

    private static ItemStack air(ItemStack item) { return item == null ? new ItemStack(Material.AIR) : item; }
    private interface Checked { void run() throws Exception; }
}
