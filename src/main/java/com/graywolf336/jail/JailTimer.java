package com.graywolf336.jail;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Timer;

import org.bukkit.entity.Player;

import com.graywolf336.jail.beans.Jail;
import com.graywolf336.jail.beans.Prisoner;
import com.graywolf336.jail.enums.Lang;
import com.graywolf336.jail.enums.Settings;
import com.graywolf336.jail.events.PrisonerTimeChangeEvent;

/**
 * Contains all the logic for counting down the time of the prisoners time.
 * 
 * @author graywolf336
 * @since 2.x.x
 * @version 3.0.0
 */
public class JailTimer {
    private JailMain pl;
    private Timer timer;
    private Long lastTime;
    private Long afkTime = 0L;
    private org.bukkit.scheduler.BukkitTask sentenceTask;
    private Boolean bukkitTimer;

    protected JailTimer(JailMain plugin) {
        this.pl = plugin;
        this.lastTime = System.currentTimeMillis();
        reloadSettings();

        // Save once per minute; reloading settings must not duplicate this task.
        pl.getServer().getScheduler().runTaskTimer(pl, () -> {
            for(Jail j : pl.getJailManager().getJails()) pl.getJailIO().saveJail(j);
        }, 1200L, 1200L);
    }

    /** Refresh AFK limits and replace the sentence task only when its mode changes. */
    public void reloadSettings() {
        try {
            afkTime = Util.getTime(pl.getConfig().getString(Settings.MAXAFKTIME.getPath()));
        } catch (Exception e) {
            pl.getLogger().severe("Error while processing the max afk time: " + e.getMessage());
        }

        boolean useBukkit = pl.getConfig().getBoolean(Settings.USEBUKKITTIMER.getPath());
        if(bukkitTimer != null && bukkitTimer == useBukkit) return;
        if(sentenceTask != null) sentenceTask.cancel();
        if(timer != null) timer.stop();
        timer = null;
        sentenceTask = null;
        bukkitTimer = useBukkit;
        if(useBukkit) {
            pl.debug("Sentence timer: Bukkit scheduler.");
            sentenceTask = pl.getServer().getScheduler().runTaskTimer(pl, new TimeEvent(), 200, 200);
        }else {
            pl.debug("Sentence timer: Java timer.");
            timer = new Timer(10000, new ActionListener () {
                public void actionPerformed (ActionEvent event) {
                    pl.getServer().getScheduler().scheduleSyncDelayedTask(pl, new TimeEvent());
                };
            });

            timer.start();
        }

    }

    /**
     * Returns the instance of this timer.
     * 
     * @return the {@link Timer} instance
     */
    public Timer getTimer() {
        return this.timer;
    }

    private class TimeEvent implements Runnable {
        public void run() {
            long timePassed = System.currentTimeMillis() - lastTime;
            lastTime = System.currentTimeMillis();

            for(Jail j : pl.getJailManager().getJails()) {
                for(Prisoner p : j.getAllPrisoners().values()) {
                    //only execute this code if the prisoner's time is more than 0 milliseconds
                    //and they don't have any offline pending things
                    if(p.getRemainingTime() == 0 && !p.isOfflinePending()) {
                        pl.getPrisonerManager().schedulePrisonerRelease(p, null, "sentence_completed");
                    }
                    if(p.getRemainingTime() > 0 && !p.isOfflinePending()) {
                        final Player player = pl.getServer().getPlayer(p.getUUID());

                        //Check if the player is offline
                        if(player == null) {
                            //if they are offline AND the config has counting down the time
                            //while the prisoner is offline, then let's do it
                            if(pl.getConfig().getBoolean(Settings.COUNTDOWNTIMEOFFLINE.getPath())) {
                                //Set their remaining time but if it is less than zero, set it to zero
                            	long before = p.getRemainingTime();
                            	long after = Math.max(0, p.getRemainingTime() - timePassed);
                            	
                            	PrisonerTimeChangeEvent event = new PrisonerTimeChangeEvent(j, j.getCellPrisonerIsIn(p.getUUID()), p, player, before, after);
                            	pl.getServer().getPluginManager().callEvent(event);
                            	
                            	if(!event.isCancelled()) {
                            		after = event.getTimeAfterChange();
                            		p.setRemainingTime(after);
                                    if(p.getRemainingTime() == 0) pl.getPrisonerManager().schedulePrisonerRelease(p, null, "sentence_completed");
                            	}
                            }
                        }else {
                            if(afkTime > 0) {
                                p.setAFKTime(p.getAFKTime() + timePassed);
                                if(p.getAFKTime() > afkTime) {
                                    p.setAFKTime(0);
                                    //This is so we kick players on the main thread
                                    //instead of on the async thread(s), as spigot
                                    //has a protection against this enabled.
                                    pl.getServer().getScheduler().scheduleSyncDelayedTask(pl, new Runnable() {
                                    	public void run() {
                                    		player.kickPlayer(Lang.AFKKICKMESSAGE.get());
                                    	}
                                    });
                                }
                            }

                            //The prisoner isn't offline, so let's count down
                            //Set their remaining time but if it is less than zero, set it to zero
                            long before = p.getRemainingTime();
                        	long after = Math.max(0, p.getRemainingTime() - timePassed);
                        	
                        	PrisonerTimeChangeEvent event = new PrisonerTimeChangeEvent(j, j.getCellPrisonerIsIn(p.getUUID()), p, player, before, after);
                        	pl.getServer().getPluginManager().callEvent(event);
                        	
                        	if(!event.isCancelled()) {
                        		after = event.getTimeAfterChange();
                        		p.setRemainingTime(after);
                                if(p.getRemainingTime() == 0) pl.getPrisonerManager().schedulePrisonerRelease(p, null, "sentence_completed");
                        	}
                        }
                    }
                }
            }
        }
    }
}
