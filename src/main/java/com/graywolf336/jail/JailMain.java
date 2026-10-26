package com.graywolf336.jail;

import java.util.Collections;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import com.graywolf336.jail.beans.Jail;
import com.graywolf336.jail.beans.Prisoner;
import com.graywolf336.jail.command.CommandHandler;
import com.graywolf336.jail.command.JailHandler;
import com.graywolf336.jail.enums.Lang;
import com.graywolf336.jail.enums.Settings;
import com.graywolf336.jail.events.JailPluginReloadedEvent;
import com.graywolf336.jail.interfaces.IJailPayManager;
import com.graywolf336.jail.interfaces.IJailStickManager;
import com.graywolf336.jail.listeners.CacheListener;
import com.graywolf336.jail.listeners.CellSignListener;
import com.graywolf336.jail.listeners.EntityListener;
import com.graywolf336.jail.listeners.HandCuffListener;
import com.graywolf336.jail.listeners.JailingListener;
import com.graywolf336.jail.listeners.MoveProtectionListener;
import com.graywolf336.jail.listeners.PlayerListener;
import com.graywolf336.jail.listeners.ProtectionListener;
import com.graywolf336.jail.listeners.WorldListener;

/**
 * The main class for this Jail plugin, holds instances of vital classes.
 * 
 * @author graywolf336
 * @since 1.x.x
 * @version 3.0.0
 */
public class JailMain extends JavaPlugin {
    private CommandHandler cmdHand;
    private HandCuffManager hcm;
    private JailHandler jh;
    private JailIO io;
    private JailManager jm;
    private IJailPayManager jpm;
    private IJailStickManager jsm;
    private JailTimer jt;
    private JailVoteManager jvm;
    private PrisonerManager pm;
    private ScoreBoardManager sbm;
    private MoveProtectionListener mpl;
    private volatile boolean debug = false;
    private JailConsole console;
    private boolean started;

    public void onEnable() {
        long st = System.currentTimeMillis();
        loadConfig();
        try { ConfigValidation.validate(getConfig()); }
        catch(Exception failure) {
            getLogger().severe("Startup rejected: " + JailConsole.clean(failure.getMessage()));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        debug = getConfig().getBoolean(Settings.DEBUG.getPath());
        console = new JailConsole(this);
        console.banner();

        hcm = new HandCuffManager();
        jm = new JailManager(this);
        io = new JailIO(this);
        io.loadLanguage();

        //If the prepareStorage returns false, we need to disable the plugin
        if(!io.prepareStorage(true)) {
            getLogger().severe("Startup failed: jail storage unavailable. Check the storage settings and permissions. Plugin disabled.");
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }

        long beforeLoad = io.getFailureCount();
        io.loadJails();
        CellResources.validate(jm);
        if(io.getFailureCount() != beforeLoad) {
            getLogger().severe("Startup failed: jail data could not be loaded. Plugin disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        cmdHand = new CommandHandler(this);
        jh = new JailHandler(this);
        pm = new PrisonerManager(this);
        
        try {
            jvm = new JailVoteManager(this);
        }catch(Exception e) {
            e.printStackTrace();
            getLogger().severe("Failed to load the Jail Vote system, please see the stacktrace above (you probably misconfigured the time).");
        }

        PluginManager plm = this.getServer().getPluginManager();
        plm.registerEvents(new CacheListener(this), this);
        plm.registerEvents(new CellSignListener(this), this);
        plm.registerEvents(new EntityListener(this), this);
        plm.registerEvents(new HandCuffListener(this), this);
        plm.registerEvents(new JailingListener(this), this);
        plm.registerEvents(new PlayerListener(this), this);
        plm.registerEvents(new ProtectionListener(this), this);
        plm.registerEvents(new CellChestProtection(this), this);
        plm.registerEvents(new JailUniform(this), this);
        plm.registerEvents(new WorldListener(this), this);

        // Activity remains tracked in escape mode; containment follows the config.
        this.reloadMoveProtection();

        jt = new JailTimer(this);
        sbm = new ScoreBoardManager(this);
        reloadJailPayManager();
        reloadJailSticks();
        com.graywolf336.jail.listeners.CellSignListener.refreshLoadedSigns(this);

        new JailsAPI(this);
        started = true;
        console.ready(System.currentTimeMillis() - st);
    }

    public void onDisable() {
        long beforeSave = io == null ? 0 : io.getFailureCount();
        int jailCount = jm == null ? 0 : jm.getJails().size();
        int prisonerCount = jm == null ? 0 : jm.getAllPrisoners().size();
        if(jm != null && started)
            for(Jail j : jm.getJails())
                io.saveJail(j);

        if(jt != null)
            if(jt.getTimer() != null)
                jt.getTimer().stop();

        if(io != null)
            io.closeConnection();

        if(started) {
            if(io != null && io.getFailureCount() == beforeSave)
                getLogger().info("Disabled | saved " + jailCount + " jails and " + prisonerCount + " prisoners.");
            else getLogger().severe("Disabled | storage errors occurred; some data may not have been saved.");
        }
        started = false;

        getServer().getScheduler().cancelTasks(this);

        jvm = null;
        jt = null;
        sbm = null;
        jpm = null;
        cmdHand = null;
        pm = null;
        jm = null;
        jsm = null;
        io = null;
        hcm = null;
    }

    private void loadConfig() {
        //Only create the default config if it doesn't exist
        saveDefaultConfig();

        refreshConfigDocumentation();
    }

    private void refreshConfigDocumentation() {
        try(java.io.Reader source = new java.io.InputStreamReader(getResource("config.yml"), java.nio.charset.StandardCharsets.UTF_8)) {
            ConfigDocumentation.apply(getConfig(), source, getDescription().getVersion());
        } catch(java.io.IOException failure) {
            throw new IllegalStateException("Unable to read the bundled configuration documentation.", failure);
        }
        saveConfig();
    }

    /* Majority of the new command system was heavily influenced by the MobArena.
     * Thank you garbagemule for the great system you have in place there.
     *
     * Send the command off to the CommandHandler class, that way this main class doesn't get clogged up.
     */
    public boolean onCommand(CommandSender sender, Command command, String commandLabel, String[] args) {
    	if(jh == null || cmdHand == null) {
    		sender.sendMessage(Lang.PLUGINNOTLOADED.get());
    		getServer().getConsoleSender().sendMessage(Lang.PLUGINNOTLOADED.get());
    	}else {
            if((command != null && command.getName().equalsIgnoreCase("jail")) || commandLabel.equalsIgnoreCase("jail") || commandLabel.equalsIgnoreCase("j")) {
            	jh.parseCommand(jm, sender, args);
            }else {
                cmdHand.handleCommand(jm, sender, command.getName().toLowerCase(), args);
            }
    	}

        return true;//Always return true here, that way we can handle the help and command usage ourself.
    }
    
    public List<String> onTabComplete(CommandSender sender, Command command, String commandLabel, String[] args) {
        if(jh == null || cmdHand == null) {
            sender.sendMessage(Lang.PLUGINNOTLOADED.get());
            getServer().getConsoleSender().sendMessage(Lang.PLUGINNOTLOADED.get());
        }else {
            debug("Tab Complete Args (" + args.length + ") for '" + commandLabel + "': " + Util.getStringFromArray(", ", args));
            try {
                if((command != null && command.getName().equalsIgnoreCase("jail")) || commandLabel.equalsIgnoreCase("jail") || commandLabel.equalsIgnoreCase("j")) {
                    return jh.parseTabComplete(jm, sender, args);
                }else {
                    return cmdHand.parseTabComplete(jm, sender, command.getName().toLowerCase(), args);
                }
            }catch(Exception e) {
                if(this.debug)
                    e.printStackTrace();
                
                debug(e.getClass().getSimpleName() + " occured while providing tab complete: " + e.getMessage());
                return Collections.emptyList();
            }
        }
        
        return Collections.emptyList();
    }
    
    public void reloadEverything() throws Exception {
        long start = System.currentTimeMillis();
        try {
            reloadSettings();
            getLogger().info("Reload complete in " + (System.currentTimeMillis() - start) + " ms | active prisoners=" + jm.getAllPrisoners().size());
        } catch(org.bukkit.configuration.InvalidConfigurationException invalidYaml) {
            getLogger().warning("Reload rejected: invalid config.yml. Fix the YAML and retry; active settings retained.");
            throw invalidYaml;
        } catch(Exception failure) {
            if(failure.getMessage() != null && failure.getMessage().startsWith("Unable to prepare jail storage"))
                getLogger().warning("Reload failed: replacement storage unavailable. Previous storage connection retained.");
            else getLogger().warning("Reload failed: " + JailConsole.clean(failure.getMessage()));
            throw failure;
        }
    }

    private void reloadSettings() throws Exception {
        long storageFailures = io.getFailureCount();
        String previousConfig = getConfig().saveToString();
        //Reload the configuration file
        // Flush dirty data before reopening storage (especially flatfile).
        getJailIO().saveEverything();
        if(io.getFailureCount() != storageFailures) throw new IllegalStateException("Could not save active jail data; active settings retained.");
        saveDefaultConfig();
        // Reject malformed YAML instead of silently replacing it with defaults.
        org.bukkit.configuration.file.YamlConfiguration checked = new org.bukkit.configuration.file.YamlConfiguration();
        checked.load(new java.io.File(getDataFolder(), "config.yml"));
        try(java.io.Reader source = new java.io.InputStreamReader(getResource("config.yml"), java.nio.charset.StandardCharsets.UTF_8)) {
            ConfigDocumentation.apply(checked, source, getDescription().getVersion());
        }
        // Validate settings that constructors would otherwise reject halfway through reload.
        ConfigValidation.validate(checked);
        Util.getTime(checked.getString(Settings.MAXAFKTIME.getPath()));
        if(Util.getTime(checked.getString(Settings.JAILVOTETIMER.getPath())) <= 0)
            throw new IllegalArgumentException("The jail vote timer must be positive.");
        Util.getTime(checked.getString(Settings.JAILVOTETIME.getPath()));
        String storageType = checked.getString("storage.type");
        if(!java.util.Arrays.asList("sqlite", "mysql", "flatfile").contains(storageType.toLowerCase(java.util.Locale.ROOT)))
            throw new IllegalArgumentException("Unknown storage.type: " + storageType);
        reloadConfig();
        refreshConfigDocumentation();
        //Reload the storage settings and set them up if they don't exist.
        //We don't touch any of the data currently being stored in cache,
        //this way you can transfer from flatfile to mysql or flip flopped.
        if(!getJailIO().prepareStorage(true)) {
            getConfig().loadFromString(previousConfig);
            throw new IllegalStateException("Unable to prepare jail storage; check the server log and storage configuration.");
        }
        getJailIO().loadLanguage();
        debug = getConfig().getBoolean(Settings.DEBUG.getPath());
        jt.reloadSettings();
        reloadMoveProtection();
        //Reload all that has to do with the scoreboard, name and settings
        reloadScoreBoardManager();
        //Reload the jail sticks
        reloadJailSticks();
        //Reload the jail pay information and settings
        reloadJailPayManager();
        //Reload the jail vote information and settings
        reloadJailVoteManager();
        //Let the rest of the plugin(s) know we have reloaded so they can do stuff
        //if they rely on any of the configuration settings (such as signs)
        getServer().getPluginManager().callEvent(new JailPluginReloadedEvent(this));
    }
    
    /** Reloads/Loads the move protection listener based upon it's previous status and the config. */
    private void reloadMoveProtection() {
        // Keep activity tracking registered even when escape prevention is disabled.
        if(this.mpl == null) {
            this.mpl = new MoveProtectionListener(this);
            getServer().getPluginManager().registerEvents(mpl, this);
            this.debug("Movement and activity listener enabled.");
        }
    }

    /** Reloads the scoreboard manager class, useful when something is changed in the config about it. */
    private void reloadScoreBoardManager() {
        this.sbm.removeAllScoreboards();
        this.sbm = null;
        this.sbm = new ScoreBoardManager(this);

        if(getConfig().getBoolean(Settings.SCOREBOARDENABLED.getPath())) {
            for(Jail j : jm.getJails()) {
                for(Prisoner p : j.getAllPrisoners().values()) {
                    if(getServer().getPlayer(p.getUUID()) != null) {
                        this.sbm.addScoreBoard(getServer().getPlayer(p.getUUID()), p);
                    }
                }
            }
        }
    }

    /** Reloads the Jail Sticks, so the new ones can be loaded from the config. */
    private void reloadJailSticks() {
        if(getConfig().getBoolean(Settings.JAILSTICKENABLED.getPath())) {
            if(this.jsm != null) {
                this.jsm.removeAllStickUsers();
                this.jsm = null;
            }

            this.jsm = new JailStickManager(this);
        } else if(this.jsm != null) {
            this.jsm.removeAllStickUsers();
            this.jsm = null;
        }
    }

    /** Reloads the {@link JailPayManager}. */
    private void reloadJailPayManager() {
        this.jpm = null;

        if(getConfig().getBoolean(Settings.JAILPAYENABLED.getPath())) {
            org.bukkit.Material paymentItem = org.bukkit.Material.matchMaterial(getConfig().getString(Settings.JAILPAYITEM.getPath(), "air"));
            if((paymentItem != null && paymentItem != org.bukkit.Material.AIR) || getServer().getPluginManager().isPluginEnabled("Vault")) {
                this.jpm = new JailPayManager(this);
            }else {
                getConfig().set(Settings.JAILPAYENABLED.getPath(), false);
                getLogger().warning("Currency payments disabled: no economy provider. Check Vault and your economy plugin.");
            }
        }
    }
    
    /** Reloads the {@link JailVoteManager}. */
    private void reloadJailVoteManager() throws Exception {
        JailVoteManager replacement = new JailVoteManager(this);
        if(this.jvm != null) {
            for(Integer i : this.jvm.getRunningTasks().values()) {
                this.getServer().getScheduler().cancelTask(i);
            }
            
            this.jvm.getRunningTasks().clear();
            this.jvm.getVotes().clear();
        }
        
        this.jvm = replacement;
    }

    /**
     * Gets the {@link HandCuffManager} instance.
     * 
     * @return {@link HandCuffManager} instance
     */
    public HandCuffManager getHandCuffManager() {
        return this.hcm;
    }

    /**
     * Gets the {@link JailIO} instance.
     * 
     * @return {@link JailIO} instance
     */
    public JailIO getJailIO() {
        return this.io;
    }

    /**
     * Gets the {@link JailManager} instance.
     * 
     * @return {@link JailManager} instance
     */
    public JailManager getJailManager() {
        return this.jm;
    }

    /**
     * Gets an instance of the {@link IJailPayManager}.
     * 
     * @return {@link IJailPayManager}
     */
    public IJailPayManager getJailPayManager() {
        return this.jpm;
    }

    /**
     * Gets the {@link PrisonerManager} instance.
     * 
     * @return {@link PrisonerManager} instance
     */
    public PrisonerManager getPrisonerManager() {
        return this.pm;
    }

    /**
     * Gets an instance of the {@link JailStickManager}.
     * 
     * @return {@link JailStickManager}
     */
    public IJailStickManager getJailStickManager() {
        return this.jsm;
    }

    /**
     * Gets the {@link ScoreBoardManager} instance.
     * 
     * @return {@link ScoreBoardManager} instance
     */
    public ScoreBoardManager getScoreBoardManager() {
        return this.sbm;
    }
    
    /**
     * Gets the {@link JailVoteManager} instance.
     * 
     * @return {@link JailVoteManager} instance
     */
    public JailVoteManager getJailVoteManager() {
        return this.jvm;
    }


    /**
     * Sets whether the plugin is in debugging or not.
     * 
     * @param debug whether to be in debuggin or not
     * @return Whether we we debugging or not.
     */
    public boolean setDebugging(boolean debug) {
        this.debug = debug;

        //Save whether we are debugging when we disable the plugin
        getConfig().set(Settings.DEBUG.getPath(), this.debug);
        saveConfig();

        return this.debug;
    }

    /**
     * Returns if the plugin is in debug state or not.
     * 
     * @return Whether we are debugging or not.
     */
    public boolean inDebug() {
        return this.debug;
    }

    /**
     * Logs a debugging message to the console if debugging is enabled.
     * 
     * @param message the item to log as debugging
     */
    public void debug(String message) {
        if(inDebug()) getLogger().info("[DEBUG] " + JailConsole.clean(message));
    }

    public JailConsole console() {
        if(console == null) console = new JailConsole(this);
        return console;
    }

    /**
     * This method is only for testing, there is no need to use this.
     * 
     * @return the move protection listener
     * @deprecated
     */
    public MoveProtectionListener getPlayerMoveListener() {
        return this.mpl;
    }
}
