package com.graywolf336.jail;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.graywolf336.jail.beans.AnyCell;
import com.graywolf336.jail.beans.Cell;
import com.graywolf336.jail.beans.Jail;
import com.graywolf336.jail.beans.NoCell;
import com.graywolf336.jail.beans.Prisoner;
import com.graywolf336.jail.enums.Lang;
import com.graywolf336.jail.enums.Settings;
import com.graywolf336.jail.events.OfflinePrisonerJailedEvent;
import com.graywolf336.jail.events.PrePrisonerReleasedEvent;
import com.graywolf336.jail.events.PrisonerJailedEvent;
import com.graywolf336.jail.events.PrisonerReleasedEvent;
import com.graywolf336.jail.events.PrisonerTransferredEvent;
import com.graywolf336.jail.exceptions.AsyncUnJailingNotSupportedException;
import com.graywolf336.jail.exceptions.JailRequiredException;
import com.graywolf336.jail.exceptions.PrisonerAlreadyJailedException;
import com.graywolf336.jail.exceptions.PrisonerRequiredException;
import com.graywolf336.jail.interfaces.ICell;

/**
 * Provides methods, non-statically, that do the preparing of jails, jailing, etc.
 *
 * <p>
 *
 * <ul>
 * 	<li>{@link #prepareJail(Jail, ICell, Player, Prisoner) preparejail}</li>
 * 	<li>{@link #schedulePrisonerRelease(Prisoner) schedulePrisonerRelease}</li>
 * 	<li>{@link #unJail(Jail, ICell, Player, Prisoner, CommandSender) unJail}</li>
 * 	<li>{@link #forceRelease(Prisoner, CommandSender) forceRelease}</li>
 *  <li>{@link #forceUnJail(Jail, Cell, Player, Prisoner, CommandSender) forceUnJail}</li>
 *  <li>{@link #transferPrisoner(Jail, Cell, Jail, Cell, Prisoner) transferPrisoner}</li>
 * </ul>
 *
 * @author graywolf336
 * @since 2.x.x
 * @version 3.0.0
 */
public class PrisonerManager {
    private JailMain pl;
    private java.util.concurrent.ConcurrentLinkedQueue<Prisoner> releases;
    private java.util.Set<UUID> queued;

    protected PrisonerManager(JailMain plugin) {
        this.pl = plugin;
        this.releases = new java.util.concurrent.ConcurrentLinkedQueue<Prisoner>();
        this.queued = java.util.concurrent.ConcurrentHashMap.newKeySet();

        // Schedule the releasing of prisoners
        plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            public void run() {
                releaseScheduledPrisoners();
            }
        }, 100L, 20L);
    }

    /**
     * Does everything preparing for the jailing of the provided prisoner, if they are online it forwards it to {@link #jailPrisoner(Jail, ICell, Player, Prisoner)}.
     *
     * <p>
     *
     * In this we do the following:
     * <ol>
     * 	<li>Checks if the jail is null, if so it throws an Exception</li>
     * 	<li>Checks if the prisoner is null, if so it throws an Exception</li>
     * 	<li>Sets the prisoner data to offline pending or not, player == null</li>
     * 	<li>Determine which type of cell is provided, add the prisoner data to the jail otherwise we set the cell's prisoner to this one. <em>Check before here if the cell already contains a prisoner.</em></li>
     * 	<li>Saves the jail information, goes out to the {@link JailIO} to initate a save.</li>
     * 	<li>If the prisoner is <em>not</em> offline, we will actually {@link #jailPrisoner(Jail, ICell, Player, Prisoner) jail} them now.</li>
     * 	<li>Does checks to get the right message for the next two items.</li>
     * 	<li>If we broadcast the jailing, then let's broadcast it.</li>
     * 	<li>If we log the jailing to console <em>and</em> we haven't broadcasted it, then we log it to the console.</li>
     * </ol>
     *
     * @param jail The {@link Jail jail instance} we are sending this prisoner to
     * @param cell The name of the {@link ICell cell} we are sending this prisoner to
     * @param player The {@link Player player} we are preparing the jail for.
     * @param prisoner The {@link Prisoner prisoner} file.
     * @throws JailRequiredException if the jail provided is null.
     * @throws PrisonerAlreadyJailedException if the prisoner is already jailed.
     * @throws PrisonerRequiredException if the prisoner's data provided is null.
     */
    public void prepareJail(final Jail jail, ICell cell, final Player player, final Prisoner prisoner) throws JailRequiredException, PrisonerAlreadyJailedException, PrisonerRequiredException {
        //Do some checks of whether the passed params are null.
        if(jail == null)
            throw new JailRequiredException("jailing a prisoner");

        if(cell == null)
            cell = new NoCell();

        if(prisoner == null)
            throw new PrisonerRequiredException("jailing a prisoner");

        if(this.pl.getJailManager().isPlayerJailed(prisoner.getUUID()))
            throw new PrisonerAlreadyJailedException(prisoner.getLastKnownName(), prisoner.getUUID().toString());

        //Set whether the prisoner is offline or not.
        prisoner.setOfflinePending(player == null);

        //Now that we've got those checks out of the way, let's start preparing.
        if(cell instanceof NoCell) {
            jail.addPrisoner(prisoner);
            cell = null;
        }else if(cell instanceof AnyCell) {
            cell = jail.getFirstEmptyCell();

            if(cell == null)
                jail.addPrisoner(prisoner);
            else
                cell.setPrisoner(prisoner);
        }else {
            cell.setPrisoner(prisoner);
        }

        //If they are are offline then throw the event otherwise jail them
        if(prisoner.isOfflinePending()) {
            pl.getServer().getPluginManager().callEvent(new OfflinePrisonerJailedEvent(jail, cell, prisoner));
            pl.console().player("JAILED", prisoner, "actor", prisoner.getJailer(), "jail", jail.getName(),
                    "cell", cell == null ? "none" : cell.getName(), "time", JailConsole.duration(prisoner.getRemainingTime()),
                    "reason", prisoner.getReason(), "state", "offline_pending");
        }else {
            final ICell c = cell;
            pl.getServer().getScheduler().runTask(pl, new Runnable() {
                public void run() {
                    jailPrisoner(jail, c, player, prisoner);
                }
            });
        }

        //Get a message ready for broadcasting or logging.
        String msg = "";

        if(prisoner.getRemainingTime() < 0L)
            msg = Lang.BROADCASTMESSAGEFOREVER.get(new String[] { prisoner.getLastKnownName(), prisoner.getReason(), jail.getName(), cell == null ? "" : cell.getName() });
        else
            msg = Lang.BROADCASTMESSAGEFORMINUTES.get(new String[] { prisoner.getLastKnownName(), String.valueOf(prisoner.getRemainingTimeInMinutes()), prisoner.getReason(), jail.getName(), cell == null ? "" : cell.getName() });

        //Broadcast the message, if it is enabled
        if(pl.getConfig().getBoolean(Settings.BROADCASTJAILING.getPath(), false)) {
            for(Player recipient : pl.getServer().getOnlinePlayers())
                if(recipient.hasPermission("jail.see.broadcast")) recipient.sendMessage(msg);
        }


    }

    /**
     * Jails the given player, <strong>only</strong> use when that player has offline data pending.
     *
     * @param uuid of the player to jail.
     */
    public void jailPlayer(UUID uuid) {
        Jail j = pl.getJailManager().getJailPlayerIsIn(uuid);
        jailPrisoner(j, j.getCellPrisonerIsIn(uuid), pl.getServer().getPlayer(uuid), j.getPrisoner(uuid));
    }

    /**
     * Jails the prisoner with the proper information given.
     *
     * @param jail where they are going
     * @param cell  where they are being placed in, can be null
     * @param player who is the prisoner
     * @param prisoner data containing everything pertaining to them
     */
    protected void jailPrisoner(final Jail jail, ICell cell, final Player player, final Prisoner prisoner) {
        if(cell instanceof NoCell)
            cell = null;
        else if(cell instanceof AnyCell)
            cell = null;

        //If they have handcuffs on them, then let's remove them before we continue
        //this way the handcuff listeners and this aren't battleing each other
        if(pl.getHandCuffManager().isHandCuffed(player.getUniqueId())) {
            pl.getHandCuffManager().removeHandCuffs(player.getUniqueId());
        }

        //They are no longer offline, so set that.
        boolean offlinePlacement = prisoner.isOfflinePending();
        prisoner.setOfflinePending(false);

        //We are getting ready to teleport them, so set it to true so that
        //the *future* move checkers won't be canceling our moving.
        prisoner.setTeleporting(true);

        //If their reason is empty send proper message, else send other proper message
        if(prisoner.getReason().isEmpty()) {
            player.sendMessage(Lang.JAILED.get());
        }else {
            player.sendMessage(Lang.JAILEDWITHREASON.get(prisoner.getReason()));
        }

        //If the config has releasing them back to their previous position,
        //then let's set it in the prisoner data.
        if(pl.getConfig().getBoolean(Settings.RELEASETOPREVIOUSPOSITION.getPath(), false)) {
            prisoner.setPreviousPosition(player.getLocation());
        }

        //If the config has restoring their previous gamemode enabled,
        //then let's set it in their prisoner data.
        if(pl.getConfig().getBoolean(Settings.RESTOREPREVIOUSGAMEMODE.getPath(), false)) {
            prisoner.setPreviousGameMode(player.getGameMode());
        }

        //Set their gamemode to the one in the config, if we get a null value
        //from the parsing then we set theirs to adventure
        try {
            player.setGameMode(GameMode.valueOf(pl.getConfig().getString(Settings.JAILEDGAMEMODE.getPath(), "ADVENTURE").toUpperCase()));
        }catch(Exception e) {
            StringBuilder gamemodes = new StringBuilder();
            for(GameMode m : GameMode.values()) {
                if(gamemodes.length() != 0)
                    gamemodes.append(", ");

                gamemodes.append(m.toString().toLowerCase());
            }

            pl.getLogger().warning("Your jailed gamemode setting is problematic. It is currently '"
                    + pl.getConfig().getString(Settings.JAILEDGAMEMODE.getPath())
                    + "' and should be one of the following: " + gamemodes.toString());
            player.setGameMode(GameMode.ADVENTURE);
        }

        //only eject them if they're inside a vehicle and also eject anyone else on top of them
        if(player.isInsideVehicle()) {
            player.getVehicle().eject();
            player.eject();
        }

        //If we are ignoring the sleeping state of prisoners,
        //then let's set that
        if(pl.getConfig().getBoolean(Settings.IGNORESLEEPINGSTATE.getPath(), true)) {
            player.setSleepingIgnored(true);
        }

        //Get the max and min food level in the config
        int maxFood = pl.getConfig().getInt(Settings.FOODCONTROLMAX.getPath(), 20);
        int minFood = pl.getConfig().getInt(Settings.FOODCONTROLMIN.getPath(), 10);

        //If their food level is less than the min food level, set it to the min
        //but if it is higher than the max, set it to the max
        if (player.getFoodLevel() <  minFood) {
            player.setFoodLevel(minFood);
        } else if (player.getFoodLevel() > maxFood) {
            player.setFoodLevel(maxFood);
        }

        // Encode complete property before taking items or equipping the uniform.
        if(pl.getConfig().getBoolean(Settings.JAILEDSTOREINVENTORY.getPath(), true) && prisoner.getInventory().isEmpty()) {
            PropertyInventory.confiscate(player, prisoner,
                    pl.getConfig().getStringList(Settings.JAILEDINVENTORYBLACKLIST.getPath()), cell);
        } else if(pl.getConfig().getBoolean(Settings.CLOTHINGENABLED.getPath()) && prisoner.getArmor().isEmpty()) {
            prisoner.setArmor(Util.itemStackArrayToBase64(player.getInventory().getArmorContents()));
        }
        player.teleport(cell == null ? jail.getTeleportIn() : cell.getTeleport());

        //Set them to not allowing teleporting, as we are not going to be moving them anymore
        //this way the move checkers will start checking this player.
        prisoner.setTeleporting(false);

        //Get the commands to execute after they are jailed
        //replace all of the %p% so that the commands can have a player name in them
        for(String command : pl.getConfig().getStringList(Settings.COMMANDSONJAIL.getPath())) {
            command = command.replace("%p%", player.getName());
            command = command.replace("%player%", player.getName());
            command = command.replace("%uuid%", player.getUniqueId().toString());
            command = command.replace("%reason%", prisoner.getReason());
            try { pl.getServer().dispatchCommand(pl.getServer().getConsoleSender(), command); }
            catch(RuntimeException failure) { pl.getLogger().warning("Configured command failed: " + JailConsole.clean(failure.getMessage())); }
        }

        //Add the scoreboard to them if it is enabled
        if(pl.getConfig().getBoolean(Settings.SCOREBOARDENABLED.getPath())) {
            pl.getScoreBoardManager().addScoreBoard(player, prisoner);
        }

        //Save the data, as we have changed it
        pl.getJailIO().saveJail(jail);

        //Call our custom event for when a prisoner is actually jailed.
        PrisonerJailedEvent event = new PrisonerJailedEvent(jail, cell == null ? null : (Cell)cell, prisoner, player);
        pl.getServer().getPluginManager().callEvent(event);
        pl.console().player(offlinePlacement ? "PLACED" : "JAILED", prisoner,
                "actor", offlinePlacement ? null : prisoner.getJailer(), "jail", jail.getName(),
                "cell", cell == null ? "none" : cell.getName(), "time", JailConsole.duration(prisoner.getRemainingTime()),
                "reason", offlinePlacement ? null : prisoner.getReason());
    }

    /**
     * Schedules a prisoner to be released, this method is to be used <strong>async</strong>.
     * 
     * <p>
     * 
     * If you're wanting to unjail a prisoner, see the {@link #unJail(Jail, ICell, Player, Prisoner, CommandSender)} method.
     *
     * @param prisoner to be released.
     */
    public void schedulePrisonerRelease(Prisoner prisoner) {
        if(queued.add(prisoner.getUUID())) releases.add(prisoner);
    }

    public void schedulePrisonerRelease(Prisoner prisoner, CommandSender actor, String cause) {
        pl.console().rememberRelease(prisoner, actor, cause);
        schedulePrisonerRelease(prisoner);
    }

    private void releaseScheduledPrisoners() {
        Prisoner prisoner;
        while((prisoner = releases.poll()) != null) {
            queued.remove(prisoner.getUUID());
            if(pl.getJailManager().getPrisoner(prisoner.getUUID()) == prisoner)
                releasePrisoner(pl.getServer().getPlayer(prisoner.getUUID()), prisoner);
        }
    }

    /**
     * Release the given prisoner from jailing, does the checks if they are offline or not.
     *
     * @param player we are releasing, can be null and if so they'll be treated as offline.
     * @param prisoner data to handle.
     */
    private void releasePrisoner(Player player, Prisoner prisoner) {
        if(player == null) {
            prisoner.setOfflinePending(true);
            prisoner.setRemainingTime(0);
        }else {
            Jail j = pl.getJailManager().getJailPlayerIsIn(player.getUniqueId());

            try {
                unJail(j, j.getCellPrisonerIsIn(player.getUniqueId()), player, prisoner, null);
            }catch(Exception e) {
                if(pl.inDebug()) {
                    e.printStackTrace();
                }

                pl.getLogger().severe("Unable to unjail the prisoner " + player.getName() + " because '" + e.getMessage() + "'.");
            }

        }
    }

    /**
     * Unjails a prisoner, <strong>sync</strong>, from jail, removing all their data.
     *
     * <p>
     *
     * Throws an exception if either the jail is null or the prisoner is null.
     *
     * @param jail where the prisoner is located at
     * @param cell which the prisoner is in, can be null
     * @param player instance for the prisoner we're unjailing
     * @param prisoner data where everything resides
     * @param sender The {@link CommandSender} who unjailed this player, can be null.
     * @throws AsyncUnJailingNotSupportedException when this method is called via a thread that is <strong>not</strong> the primary thread.
     * @throws JailRequiredException when the jail provided is null.
     * @throws PrisonerRequiredException when the provided prisoner data is null.
     *
     */
    public void unJail(final Jail jail, ICell cell, final Player player, final Prisoner prisoner, final CommandSender sender) throws AsyncUnJailingNotSupportedException, JailRequiredException, PrisonerRequiredException  {
        if(!pl.getServer().isPrimaryThread()) throw new AsyncUnJailingNotSupportedException();

        //Do some checks of whether the passed params are null.
        if(jail == null)
            throw new JailRequiredException("unjailing a prisoner");

        if(cell instanceof NoCell)
            cell = null;
        else if(cell instanceof AnyCell)
            cell = null;

        if(prisoner == null)
            throw new PrisonerRequiredException("unjailing a prisoner");

        //Throw the custom event which is called before we start releasing them
        PrePrisonerReleasedEvent preEvent = new PrePrisonerReleasedEvent(jail, cell, prisoner, player);
        pl.getServer().getPluginManager().callEvent(preEvent);

        //We are getting ready to teleport them, so set it to true so that
        //the *future* move checkers won't be canceling our moving.
        prisoner.setTeleporting(true);

        //In case they have somehow got in a vehicle, let's unmount
        //them so we can possibly teleport them
        if(player.isInsideVehicle()) {
            player.getVehicle().eject();
            player.eject();
        }
        
        boolean store = pl.getConfig().getBoolean(Settings.JAILEDSTOREINVENTORY.getPath(), true);
        final ICell releasingCell = cell;
        try {
            PropertyInventory.withRollback(player, cell, () -> pl.getJailIO().removePrisoner(jail,
                    releasingCell == null ? null : (Cell) releasingCell, prisoner,
                    () -> PropertyInventory.restore(player, prisoner, releasingCell, store)));
            pl.debug(JailConsole.format("PROPERTY_RESTORED", "player", player.getName(),
                    "slots", Integer.toString(player.getInventory().getSize()),
                    "codec", prisoner.getInventory().startsWith("JAIL2N:") ? "native_nbt" : "bukkit"));
        } catch(IllegalStateException invalidProperty) {
            prisoner.setTeleporting(false);
            throw invalidProperty;
        }
        if(cell != null) {
            cell.removePrisoner();
        } else {
            jail.removePrisoner(prisoner);
        }
        pl.getJailManager().removeCacheObject(prisoner.getUUID());

        //In case we had set their sleeping state to be ignored
        //let's enable their sleeping state taking place again
        player.setSleepingIgnored(false);
        
        pl.getServer().getScheduler().runTaskLater(pl, new Runnable() {
            public void run() {
                //If the config has us teleporting them back to their
                //previous position then let's do that
                boolean tpd = false;
                if(pl.getConfig().getBoolean(Settings.RELEASETOPREVIOUSPOSITION.getPath(), false)) {
                    if(prisoner.getPreviousLocation() != null)
                        tpd = player.teleport(prisoner.getPreviousLocation());
                }

                //If they haven't already been teleported and the config has us to teleport on release,
                //then we teleport players to the jail's free spot
                if(!tpd && pl.getConfig().getBoolean(Settings.TELEPORTONRELEASE.getPath(), true)) {
                    player.teleport(jail.getTeleportFree());
                }

                //If we are to restore their previous gamemode and we have it stored,
                //then by all means let's restore it
                if(pl.getConfig().getBoolean(Settings.RESTOREPREVIOUSGAMEMODE.getPath(), false)) {
                    player.setGameMode(prisoner.getPreviousGameMode());
                }
            };
        }, 5);

        //Get the commands to execute prisoners are unjailed
        //replace all of the %p% so that the commands can have a player name in them
        for(String command : pl.getConfig().getStringList(Settings.COMMANDSONRELEASE.getPath())) {
            command = command.replace("%p%", player.getName());
            command = command.replace("%player%", player.getName());
            command = command.replace("%uuid%", player.getUniqueId().toString());
            command = command.replace("%reason%", prisoner.getReason());
            try { pl.getServer().dispatchCommand(pl.getServer().getConsoleSender(), command); }
            catch(RuntimeException failure) { pl.getLogger().warning("Configured command failed: " + JailConsole.clean(failure.getMessage())); }
        }

        //Remove the scoreboard to them if it is enabled
        if(pl.getConfig().getBoolean(Settings.SCOREBOARDENABLED.getPath())) {
            pl.getScoreBoardManager().removeScoreBoard(player);
        }

        //Call the prisoner released event as we have released them.
        PrisonerReleasedEvent event = new PrisonerReleasedEvent(jail, cell == null ? null : (Cell)cell, prisoner, player);
        pl.getServer().getPluginManager().callEvent(event);
        pl.console().released(prisoner, jail, cell, sender);

        player.sendMessage(Lang.UNJAILED.get());
        if(sender != null) sender.sendMessage(Lang.UNJAILSUCCESS.get(player.getName()));
    }

    /**
     * Forcefully unjails a {@link Prisoner prisoner} from {@link Jail}.
     *
     * <p>
     *
     * This method forcefully removes all the references to this prisoner,
     * meaning if they're offline the following won't happened:
     * <ul>
     * 	<li>Inventory restored</li>
     * 	<li>Teleported anywhere</li>
     *  <li>No messages sent, they'll be clueless.</li>
     * </ul>
     *
     * But if they're online, it goes through the regular unjailing methods.
     *
     * <p>
     *
     * @param prisoner to release
     * @param sender who is releasing the prisoner, <em>can be null</em>
     * @throws PrisonerRequiredException when the prisoner data doesn't exist.
     * @throws JailRequiredException when the prisoner isn't jailed
     */
    public void forceRelease(Prisoner prisoner, CommandSender sender) throws JailRequiredException, PrisonerRequiredException {
        Jail j = pl.getJailManager().getJailPrisonerIsIn(prisoner);
        forceUnJail(j, j.getCellPrisonerIsIn(prisoner.getUUID()), pl.getServer().getPlayer(prisoner.getUUID()), prisoner, sender);
    }

    /**
     * Forcefully unjails a {@link Prisoner prisoner} from {@link Jail}.
     *
     *
     * <p>
     *
     * This method forcefully removes all the references to this prisoner,
     * meaning if they're offline the following won't happened:
     * <ul>
     * 	<li>Inventory restored</li>
     * 	<li>Teleported anywhere</li>
     *  <li>No messages sent, they'll be clueless.</li>
     * </ul>
     *
     * But if they're online, it goes through the regular unjailing methods.
     *
     * <p>
     *
     * @param jail the prisoner is in
     * @param cell the prisoner is in, <em>can be null</em>
     * @param player of the prisoner, if this is null then the player won't be teleported when they come back on.
     * @param prisoner to release and remove data
     * @param sender who is releasing the prisoner, <em>can be null</em>
     * @throws JailRequiredException when the provided jail is null.
     * @throws PrisonerRequiredException when the provided prisoner data is null.
     */
    public void forceUnJail(Jail jail, Cell cell, Player player, Prisoner prisoner, CommandSender sender) throws JailRequiredException, PrisonerRequiredException {
        if(jail == null)
            throw new JailRequiredException("jailing a prisoner");

        if(prisoner == null)
            throw new PrisonerRequiredException("jailing a prisoner");

        pl.console().rememberRelease(prisoner, sender, "force_release");
        if(player == null) {
            //Player is offline, we just forcefully remove them from the database
            pl.getJailIO().removePrisoner(jail, cell, prisoner);

            if(cell == null) {
                jail.removePrisoner(prisoner);
            }else {
                cell.removePrisoner();
            }
            pl.getJailManager().removeCacheObject(prisoner.getUUID());

            //Call the prisoner released event as we have released them.
            PrisonerReleasedEvent event = new PrisonerReleasedEvent(jail, cell, prisoner, player);
            pl.getServer().getPluginManager().callEvent(event);
            pl.console().released(prisoner, jail, cell, sender);

            if(sender != null) sender.sendMessage(Lang.FORCEUNJAILED.get(prisoner.getLastKnownName()));
        }else {
            try {
                unJail(jail, cell, player, prisoner, sender);
            } catch(IllegalStateException invalidProperty) {
                throw invalidProperty;
            } catch (Exception e) {
                releasePrisoner(player, prisoner);
            }
        }
    }

    /**
     * Transfers the prisoner from one jail, or cell, to another jail, and/or cell.
     *
     * @param originJail The jail where they are coming from.
     * @param originCell The cell where they are coming from.
     * @param targetJail The jail we're transferring them from.
     * @param targetCell The cell we're putting them into.
     * @param prisoner The prisoner data we're handling.
     */
    public void transferPrisoner(Jail originJail, Cell originCell, Jail targetJail, Cell targetCell, Prisoner prisoner) {
        transferPrisoner(originJail, originCell, targetJail, targetCell, prisoner, null);
    }

    public void transferPrisoner(Jail originJail, Cell originCell, Jail targetJail, Cell targetCell, Prisoner prisoner, CommandSender actor) {
        if(originCell == targetCell && originJail == targetJail) return;
        if(targetCell != null && targetCell.hasPrisoner() && !targetCell.getPrisoner().getUUID().equals(prisoner.getUUID()))
            throw new IllegalStateException("The target cell is occupied.");
        PropertyInventory.captureLegacy(prisoner, originCell);
        ItemStack[] storedProperty = PropertyInventory.read(prisoner);
        Player player = pl.getServer().getPlayer(prisoner.getUUID());
        if(player != null) player.closeInventory();

        if(originCell != null) originCell.removePrisoner();
        else originJail.removePrisoner(prisoner);
        if(targetCell != null) targetCell.setPrisoner(prisoner);
        else targetJail.addPrisoner(prisoner);

        if(originCell != null && originCell.hasChest()) {
            CellChestProtection.closeViewers(originCell.getChest().getInventory());
            originCell.getChest().getInventory().clear();
        }
        PropertyInventory.populate(targetCell, storedProperty);
        if(player == null) {
            prisoner.setOfflinePending(true);
            prisoner.setToBeTransferred(true);
        } else {
            prisoner.setTeleporting(true);
            try { player.teleport(targetCell == null ? targetJail.getTeleportIn() : targetCell.getTeleport()); }
            finally { prisoner.setTeleporting(false); }
            player.sendMessage(Lang.TRANSFERRED.get(targetJail.getName()));
        }
        if(originCell != null) originCell.updateSigns();
        if(targetCell != null) targetCell.updateSigns();
        pl.getServer().getPluginManager().callEvent(new PrisonerTransferredEvent(originJail, originCell, targetJail, targetCell, prisoner, player));
        pl.getJailIO().saveJail(originJail);
        if(originJail != targetJail) pl.getJailIO().saveJail(targetJail);
        pl.console().player("TRANSFERRED", prisoner, "actor", JailConsole.actor(actor),
                "from", JailConsole.cell(originJail, originCell), "to", JailConsole.cell(targetJail, targetCell));
    }
}
