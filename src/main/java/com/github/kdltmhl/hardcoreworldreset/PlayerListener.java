package com.github.kdltmhl.hardcoreworldreset;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Handles player-related events for the hardcore world reset plugin.
 */
public class PlayerListener implements Listener {

    private final HardcoreWorldReset plugin;

    /**
     * Creates a new PlayerListener.
     *
     * @param plugin The main plugin instance
     */
    public PlayerListener(HardcoreWorldReset plugin) {
        this.plugin = plugin;
    }

    /** Prevents accidental damage while players are waiting for the swap. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWaitingWorldDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player
                && plugin.getWorldManager().isWaitingWorld(player.getWorld())) {
            event.setCancelled(true);
        }
    }

    /** Keeps the visible waiting room intact. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWaitingWorldBlockBreak(BlockBreakEvent event) {
        if (plugin.getWorldManager().isWaitingWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    /** Keeps players from modifying the waiting room. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWaitingWorldBlockPlace(BlockPlaceEvent event) {
        if (plugin.getWorldManager().isWaitingWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    /** Prevents players from using buckets, buttons, items, or other interactions there. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWaitingWorldInteract(PlayerInteractEvent event) {
        if (plugin.getWorldManager().isWaitingWorld(event.getPlayer().getWorld())) {
            event.setCancelled(true);
        }
    }

    /** Prevents explosions from damaging the waiting room. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWaitingWorldEntityExplode(EntityExplodeEvent event) {
        if (plugin.getWorldManager().isWaitingWorld(event.getLocation().getWorld())) {
            event.setCancelled(true);
        }
    }

    /** Prevents block-triggered explosions from damaging the waiting room. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWaitingWorldBlockExplode(BlockExplodeEvent event) {
        if (plugin.getWorldManager().isWaitingWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    /**
     * Handles player join events.
     * Ensures players are in the active world and starts the timer if needed.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.cleanupPlayerForCurrentReset(player);

        // A reconnect race can occasionally pass the async pre-login check
        // after a swap has started. Keep that player in the safe room.
        if (plugin.isSwapping()) {
            World waitingWorld = plugin.getWorldManager().getWaitingWorld();
            if (waitingWorld != null && player.teleport(waitingWorld.getSpawnLocation())) {
                player.sendMessage(plugin.getConfigManager().getMessages().worldResetting);
            } else {
                player.kickPlayer(plugin.getConfigManager().getMessages().worldResetting);
            }
            return;
        }

        World activeWorld = plugin.getActiveWorld();

        if (activeWorld != null) {
            // Set spawn point to active world
            player.setBedSpawnLocation(activeWorld.getSpawnLocation(), true);

            // Teleport player to active world if they're not in it
            String playerWorldBase = plugin.getWorldManager().getBaseWorldName(player.getWorld().getName());
            String activeWorldBase = plugin.getWorldManager().getBaseWorldName(activeWorld.getName());

            if (!playerWorldBase.equals(activeWorldBase)) {
                player.teleport(activeWorld.getSpawnLocation());
                String redirectMessage = plugin.getConfigManager().getMessages().redirect;
                if (redirectMessage != null && !redirectMessage.isEmpty()) {
                    player.sendMessage(redirectMessage);
                }
            }
        }

        // Start timer if this is the first player and auto-start is enabled
        ConfigManager config = plugin.getConfigManager();
        int minPlayers = config.getMinPlayersToStart();

        if (Bukkit.getOnlinePlayers().size() >= minPlayers && !plugin.isSwapping()) {
            if (config.isAutoStartTimer()) {
                plugin.startOrResumeTimer();
            }
        }
    }

    /**
     * Handles player respawn events.
     * Ensures players respawn in the active world.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        // Any death during the preparation window is contained in the
        // waiting room and must never respawn a player into a half-ready run.
        if (plugin.isSwapping()) {
            World waitingWorld = plugin.getWorldManager().getWaitingWorld();
            if (waitingWorld != null) {
                event.setRespawnLocation(waitingWorld.getSpawnLocation());
                return;
            }
        }

        World activeWorld = plugin.getActiveWorld();

        // Handle end dimension respawn (death in the end)
        if (player.getWorld().getEnvironment() == World.Environment.THE_END && activeWorld != null) {
            // Use delayed teleport to ensure respawn completes first
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.teleport(activeWorld.getSpawnLocation());
            }, 1L);
            return;
        }

        // Set respawn location to active world
        if (activeWorld != null) {
            event.setRespawnLocation(activeWorld.getSpawnLocation());
        }
    }

    /**
     * Handles Ender Dragon death events.
     * Stops the timer and announces completion when the goal is achieved.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEnderDragonDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon)) {
            return;
        }

        // Check if this is the configured end goal
        ConfigManager config = plugin.getConfigManager();
        if (config.getEndGoal() != ConfigManager.EndGoal.ENDER_DRAGON) {
            return;
        }

        World activeWorld = plugin.getActiveWorld();
        if (activeWorld == null) {
            return;
        }

        // Verify the dragon died in our active world's end dimension
        String expectedEndWorld = activeWorld.getName() + "_the_end";
        if (!event.getEntity().getWorld().getName().equals(expectedEndWorld)) {
            return;
        }

        // Stop timer and announce victory
        String finalTime = plugin.stopTimerAndAnnounce();
        String messageTemplate = config.getMessages().dragonDefeat;
        String finalMessage = messageTemplate.replace("%time%", finalTime);
        Bukkit.broadcastMessage(finalMessage);

        plugin.getLogger().info("Run completed! Final time: " + finalTime);
    }

    /**
     * Handles player death events.
     * Triggers world reset when a player dies in the active world.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player deadPlayer = event.getEntity();

        if (plugin.isSwapping()) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (deadPlayer.isOnline()) {
                    deadPlayer.spigot().respawn();
                }
            }, 1L);
            return;
        }

        World activeWorld = plugin.getActiveWorld();

        if (activeWorld == null) {
            return;
        }

        // Check if the death occurred in the active world set
        String playerWorldBase = plugin.getWorldManager().getBaseWorldName(deadPlayer.getWorld().getName());
        String activeWorldBase = plugin.getWorldManager().getBaseWorldName(activeWorld.getName());

        if (!playerWorldBase.equals(activeWorldBase)) {
            return;
        }

        // Store original game mode
        GameMode originalGameMode = deadPlayer.getGameMode();

        // Clear drops and XP
        event.getDrops().clear();
        event.setDroppedExp(0);

        // Respawn player and trigger world swap
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            deadPlayer.spigot().respawn();
            plugin.triggerWorldSwap(deadPlayer, originalGameMode);
        }, 1L);
    }

    /**
     * Handles player quit events.
     * Pauses timer only when no players remain online. Falling below the
     * configured start threshold does not pause an active run.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (Bukkit.getOnlinePlayers().isEmpty()) {
                plugin.pauseTimer();
            }
        }, 1L);
    }

    /**
     * Handles async player pre-login events.
     * Prevents players from joining during world swap.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (plugin.isSwapping()) {
            String reason = plugin.getConfigManager().getMessages().worldResetting;
            event.disallow(
                    AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    reason);
        }
    }
}
