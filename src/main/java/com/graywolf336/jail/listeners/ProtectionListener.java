package com.graywolf336.jail.listeners;

import java.util.concurrent.TimeUnit;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import com.graywolf336.jail.JailMain;
import com.graywolf336.jail.Util;
import com.graywolf336.jail.enums.Lang;
import com.graywolf336.jail.enums.Settings;

public class ProtectionListener implements Listener {
    private JailMain pl;
    private final java.util.Map<java.util.UUID, Long> mainHandInteractions = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Long> entityInteractions = new java.util.HashMap<>();

    public ProtectionListener(JailMain plugin) {
        this.pl = plugin;
    }

    @EventHandler(ignoreCancelled=true, priority = EventPriority.LOW)
    public void protectionBlockBreaking(BlockBreakEvent event) {
        //Before we check if the player is jailed, let's save a
        //tiny bit of resources and check if this protection is enabled
        if(pl.getConfig().getBoolean(Settings.BLOCKBREAKPROTECTION.getPath())) {
            //Let's check if the player is jailed
            if(pl.getJailManager().isPlayerJailed(event.getPlayer().getUniqueId())) {
                //Get the breaking whitelist, check if the current item is in there
                if(!Util.isStringInsideList(event.getBlock().getType().toString().toLowerCase(),
                        pl.getConfig().getStringList(Settings.BLOCKBREAKWHITELIST.getPath()))) {
                    //As our Util.getTime throws an exception when the time is in an
                    //incorrect format, we catch the exception and don't add any time
                    //as a fail safe, don't want us to go crazy adding tons of time.
                    try {
                        long add = Util.getTime(pl.getConfig().getString(Settings.BLOCKBREAKPENALTY.getPath()));
                        pl.getJailManager().getPrisoner(event.getPlayer().getUniqueId()).addTime(add);

                        String msg = "";
                        if(add == 0L) {
                            //Generate the protection message, provide the method with one argument
                            //which is the thing we are protecting against
                            msg = Lang.PROTECTIONMESSAGENOPENALTY.get(Lang.BLOCKBREAKING.get());
                        }else {
                            //Generate the protection message, provide the method with two arguments
                            //First is the time in minutes and second is the thing we are protecting against
                            msg = Lang.PROTECTIONMESSAGE.get(new String[] { String.valueOf(TimeUnit.MINUTES.convert(add, TimeUnit.MILLISECONDS)), Lang.BLOCKBREAKING.get() });
                        }

                        //Send the message
                        event.getPlayer().sendMessage(msg);
                    }catch (Exception e) {
                        pl.getLogger().severe("Block break penalty's time is in the wrong format, please fix.");
                    }

                    //Stop the event from happening, as the block wasn't in the whitelist
                    event.setCancelled(true);
                }
            }else {
            	//The player is not jailed but they're trying to break blocks inside of the Jail
                //If there is no jail let's skedaddle
                if(pl.getJailManager().getJailFromLocation(event.getBlock().getLocation()) == null) return;

                //If the player doesn't have permission to modify the jail,
                //then we stop it here. We won't be doing any of the additions to
                //a prisoner's sentence here as that's for the protections listener
                if(!event.getPlayer().hasPermission("jail.modifyjail")) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(ignoreCancelled=true, priority = EventPriority.LOW)
    public void protectionBlockPlacing(BlockPlaceEvent event) {
        //Before we check if the player is jailed, let's save a
        //tiny bit of resources and check if this protection is enabled
        if(pl.getConfig().getBoolean(Settings.BLOCKPLACEPROTECTION.getPath())) {
            //Let's check if the player is jailed
            if(pl.getJailManager().isPlayerJailed(event.getPlayer().getUniqueId())) {
                //Get the placing whitelist, check if the current item is in there
                if(!Util.isStringInsideList(event.getBlock().getType().toString().toLowerCase(), 
                        pl.getConfig().getStringList(Settings.BLOCKPLACEWHITELIST.getPath()))) {
                    //As our Util.getTime throws an exception when the time is in an
                    //incorrect format, we catch the exception and don't add any time
                    //as a fail safe, don't want us to go crazy adding tons of time.
                    try {
                        long add = Util.getTime(pl.getConfig().getString(Settings.BLOCKPLACEPENALTY.getPath()));
                        pl.getJailManager().getPrisoner(event.getPlayer().getUniqueId()).addTime(add);

                        String msg = "";
                        if(add == 0L) {
                            //Generate the protection message, provide the method with one argument
                            //which is the thing we are protecting against
                            msg = Lang.PROTECTIONMESSAGENOPENALTY.get(Lang.BLOCKPLACING);
                        }else {
                            //Generate the protection message, provide the method with two arguments
                            //First is the time in minutes and second is the thing we are protecting against
                            msg = Lang.PROTECTIONMESSAGE.get(new String[] { String.valueOf(TimeUnit.MINUTES.convert(add, TimeUnit.MILLISECONDS)), Lang.BLOCKPLACING.get() });
                        }

                        //Send the message
                        event.getPlayer().sendMessage(msg);
                    }catch (Exception e) {
                        pl.getLogger().severe("Block place penalty's time is in the wrong format, please fix.");
                    }

                    //Stop the event from happening, as the block wasn't in the whitelist
                    event.setCancelled(true);
                }
            }else {
            	//The player is not jailed but they're trying to place blocks inside of the Jail
                //If there is no jail let's skedaddle
                if(pl.getJailManager().getJailFromLocation(event.getBlock().getLocation()) == null) return;

                //If the player doesn't have permission to modify the jail,
                //then we stop it here. We won't be doing any of the additions to
                //a prisoner's sentence here as that's for the protections listener
                if(!event.getPlayer().hasPermission("jail.modifyjail")) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(ignoreCancelled=true, priority = EventPriority.LOW)
    public void commandProtection(PlayerCommandPreprocessEvent event) {
        //Before we check if the player is jailed, let's save a
        //tiny bit of resources and check if this protection is enabled
        if(pl.getConfig().getBoolean(Settings.COMMANDPROTECTION.getPath())) {
            //Let's check if this player is jailed, if so then we continue
            //otherwise we don't care about commands in here
            if(pl.getJailManager().isPlayerJailed(event.getPlayer().getUniqueId())) {
                String requested = event.getMessage().trim().toLowerCase(java.util.Locale.ROOT);
                String canonical = com.graywolf336.jail.command.CommandLocale.canonicalLine(requested);
                boolean match = (requested.equals("/jail") || requested.equals("/jailreloaded:jail"))
                        && event.getPlayer().hasPermission("jail.command.jailhelp");
                for(String whited : pl.getConfig().getStringList(Settings.COMMANDWHITELIST.getPath())) {
                    String allowed = whited.trim().toLowerCase(java.util.Locale.ROOT);
                    if(requested.equals(allowed) || requested.startsWith(allowed + " ")) match = true;
                    if(canonical.equals(allowed) || canonical.startsWith(allowed + " ")) match = true;
                }

                //If no match found in the whitelist, then let's block this command.
                if(!match) {
                    try {
                        long add = Util.getTime(pl.getConfig().getString(Settings.COMMANDPENALTY.getPath()));
                        pl.getJailManager().getPrisoner(event.getPlayer().getUniqueId()).addTime(add);

                        String msg = "";
                        if(add == 0L) {
                            //Generate the protection message, provide the method with one argument
                            //which is the thing we are protecting against
                            msg = Lang.PROTECTIONMESSAGENOPENALTY.get(Lang.COMMAND);
                        }else {
                            //Generate the protection message, provide the method with two arguments
                            //First is the time in minutes and second is the thing we are protecting against
                            msg = Lang.PROTECTIONMESSAGE.get(new String[] { String.valueOf(TimeUnit.MINUTES.convert(add, TimeUnit.MILLISECONDS)), Lang.COMMAND.get() });
                        }

                        //Send the message
                        event.getPlayer().sendMessage(msg);
                    }catch (Exception e) {
                        pl.getLogger().severe("Command Protection penalty's time is in the wrong format, please fix.");
                    }

                    //Stop the command from happening, as it wasn't whitelisted
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(ignoreCancelled=true, priority = EventPriority.LOW)
    public void chestProtection(PlayerInteractEvent event) {
        //First thing is first, let's be sure the player we're dealing with is in jail
        if(pl.getJailManager().isPlayerJailed(event.getPlayer().getUniqueId())) {
            //Next, let's check if it is a chest and if they're in a cell
            //If they are in a cell and are opening a chest, then we check
            //the config to see if they can open the chests
            if(event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null
                    && event.getClickedBlock().getState() instanceof org.bukkit.block.Chest) {
                com.graywolf336.jail.beans.Cell cell = pl.getJailManager()
                        .getJailPlayerIsIn(event.getPlayer().getUniqueId())
                        .getCellPrisonerIsIn(event.getPlayer().getUniqueId());
                org.bukkit.block.Chest clicked = (org.bukkit.block.Chest) event.getClickedBlock().getState();
                // Check the connected inventory, since adjacent chests can be separate containers.
                boolean ownDoubleChest = cell != null && cell.hasChest()
                        && java.util.Objects.equals(clicked.getInventory().getLocation(), cell.getChest().getInventory().getLocation());
                if(!pl.getConfig().getBoolean(Settings.PRISONEROPENCHEST.getPath()) || !ownDoubleChest) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(ignoreCancelled=true, priority = EventPriority.LOWEST)
    public void cropTramplingProtection(PlayerInteractEvent event) {
        //First thing is first, let's be sure the player we're dealing with is in jail
        if(pl.getJailManager().isPlayerJailed(event.getPlayer().getUniqueId())) {
            //Next, check if crap trampling protection is enabled
            if(pl.getConfig().getBoolean(Settings.CROPTRAMPLINGPROTECTION.getPath())) {
                if(event.getAction() == Action.PHYSICAL && event.getClickedBlock().getType() == Material.FARMLAND) {
                    if(pl.getJailManager().getJailFromLocation(event.getClickedBlock().getLocation()) != null) {
                        try {
                            long add = Util.getTime(pl.getConfig().getString(Settings.CROPTRAMPLINGPENALTY.getPath()));
                            pl.getJailManager().getPrisoner(event.getPlayer().getUniqueId()).addTime(add);

                            String msg = "";
                            if(add == 0L) {
                                //Generate the protection message, provide the method with one argument
                                //which is the thing we are protecting against
                                msg = Lang.PROTECTIONMESSAGENOPENALTY.get(Lang.CROPTRAMPLING);
                            }else {
                                //Generate the protection message, provide the method with two arguments
                                //First is the time in minutes and second is the thing we are protecting against
                                msg = Lang.PROTECTIONMESSAGE.get(new String[] { String.valueOf(TimeUnit.MINUTES.convert(add, TimeUnit.MILLISECONDS)), Lang.CROPTRAMPLING.get() });
                            }

                            //Send the message
                            event.getPlayer().sendMessage(msg);
                        }catch (Exception e) {
                            pl.getLogger().severe("Crop Trampling penalty's time is in the wrong format, please fix.");
                        }

                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    @EventHandler
    public void interactionProtection(PlayerInteractEvent event) {
        if(event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if(!pl.getJailManager().isPlayerJailed(event.getPlayer().getUniqueId())) return;

        // Vanilla can pre-cancel air clicks. Inspect item use independently of block use.
        boolean blockedBlock = event.getClickedBlock() != null
                && com.graywolf336.jail.InteractionPolicy.blocks(event.getClickedBlock().getType(),
                    pl.getConfig().getStringList(Settings.PREVENTINTERACTIONBLOCKS.getPath()));
        boolean blockedItem = event.getItem() != null
                && com.graywolf336.jail.InteractionPolicy.blocks(event.getItem().getType(),
                    pl.getConfig().getStringList(Settings.PREVENTINTERACTIONITEMS.getPath()));
        // The dedicated openChest option still governs the prisoner's assigned double chest.
        if(blockedBlock && isOwnAllowedChest(event)) blockedBlock = false;
        if(!blockedBlock && !blockedItem) return;

        boolean blockAlreadyDenied = event.useInteractedBlock() == org.bukkit.event.Event.Result.DENY;
        boolean itemAlreadyDenied = event.useItemInHand() == org.bukkit.event.Event.Result.DENY;
        if(blockedBlock) event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        if(blockedItem) event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);

        // Charge one penalty for a click; the second hand can still be denied without doubling it.
        long now = System.currentTimeMillis();
        java.util.UUID playerId = event.getPlayer().getUniqueId();
        boolean offhand = event.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND;
        boolean charge = !offhand || now - mainHandInteractions.getOrDefault(playerId, 0L) > 100;
        if(!offhand) mainHandInteractions.put(playerId, now);
        if(charge && ((blockedBlock && !blockAlreadyDenied) || (blockedItem && !itemAlreadyDenied))) {
            try {
                long add = Util.getTime(pl.getConfig().getString((blockedBlock
                        ? Settings.PREVENTINTERACTIONBLOCKSPENALTY : Settings.PREVENTINTERACTIONITEMSPENALTY).getPath()));
                if(add != 0) pl.getJailManager().getPrisoner(event.getPlayer().getUniqueId()).addTime(add);
                com.graywolf336.jail.enums.Lang kind = blockedBlock ? Lang.INTERACTIONBLOCKS : Lang.INTERACTIONITEMS;
                event.getPlayer().sendMessage(add == 0 ? Lang.PROTECTIONMESSAGENOPENALTY.get(kind)
                        : Lang.PROTECTIONMESSAGE.get(String.valueOf(TimeUnit.MINUTES.convert(add, TimeUnit.MILLISECONDS)), kind.get()));
            } catch(Exception invalidPenalty) {
                pl.getLogger().severe("Interaction penalty has an invalid time format; use 0, 30s, 5m, 1h, or 1d.");
            }
        }
    }

    @EventHandler
    public void forgetInteractions(org.bukkit.event.player.PlayerQuitEvent event) {
        mainHandInteractions.remove(event.getPlayer().getUniqueId());
        entityInteractions.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled=true)
    public void entityItemInteraction(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        if(!pl.getJailManager().isPlayerJailed(event.getPlayer().getUniqueId())) return;
        org.bukkit.inventory.ItemStack item = event.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND
                ? event.getPlayer().getInventory().getItemInOffHand() : event.getPlayer().getInventory().getItemInMainHand();
        if(item == null || !com.graywolf336.jail.InteractionPolicy.blocks(item.getType(),
                pl.getConfig().getStringList(Settings.PREVENTINTERACTIONITEMS.getPath()))) return;
        event.setCancelled(true);
        long now = System.currentTimeMillis();
        java.util.UUID id = event.getPlayer().getUniqueId();
        Long previous = entityInteractions.put(id, now);
        if(previous != null && now - previous < 100) return;
        try {
            long add = Util.getTime(pl.getConfig().getString(Settings.PREVENTINTERACTIONITEMSPENALTY.getPath()));
            if(add != 0) pl.getJailManager().getPrisoner(id).addTime(add);
            event.getPlayer().sendMessage(add == 0 ? Lang.PROTECTIONMESSAGENOPENALTY.get(Lang.INTERACTIONITEMS)
                    : Lang.PROTECTIONMESSAGE.get(String.valueOf(TimeUnit.MINUTES.convert(add, TimeUnit.MILLISECONDS)), Lang.INTERACTIONITEMS.get()));
        } catch(Exception invalidPenalty) {
            pl.getLogger().severe("Item interaction penalty has an invalid time format.");
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void preciseEntityItemInteraction(org.bukkit.event.player.PlayerInteractAtEntityEvent event) {
        entityItemInteraction(event);
    }

    private boolean isOwnAllowedChest(PlayerInteractEvent event) {
        if(!pl.getConfig().getBoolean(Settings.PRISONEROPENCHEST.getPath())
                || !(event.getClickedBlock().getState() instanceof org.bukkit.block.Chest)) return false;
        com.graywolf336.jail.beans.Cell cell = pl.getJailManager().getJailPlayerIsIn(event.getPlayer().getUniqueId())
                .getCellPrisonerIsIn(event.getPlayer().getUniqueId());
        org.bukkit.block.Chest clicked = (org.bukkit.block.Chest) event.getClickedBlock().getState();
        return cell != null && cell.hasChest() && java.util.Objects.equals(
                clicked.getInventory().getLocation(), cell.getChest().getInventory().getLocation());
    }
}
