package com.github.kdltmhl.hardcoreworldreset;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Guardian;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Handles player-related events for the hardcore world reset plugin.
 *
 * <p>As of v3.0.0 (Minecraft 1.21.5) all messaging uses the Adventure API.
 * Legacy {@code ChatColor} and {@code broadcastMessage} are fully removed.
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

    // ==================== Join & Respawn ====================

    /**
     * Handles player join events.
     * Ensures players are in the active world and starts the timer if needed.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        World activeWorld = plugin.getActiveWorld();

        if (activeWorld != null) {
            // Set spawn point to active world
            player.setBedSpawnLocation(activeWorld.getSpawnLocation(), true);

            // Teleport player to active world if they are not already in it
            String playerWorldBase = plugin.getWorldManager().getBaseWorldName(player.getWorld().getName());
            String activeWorldBase = plugin.getWorldManager().getBaseWorldName(activeWorld.getName());

            if (!playerWorldBase.equals(activeWorldBase)) {
                player.teleport(activeWorld.getSpawnLocation());
                Component redirectMsg = plugin.getConfigManager().getMessages().redirect;
                if (!redirectMsg.equals(Component.empty())) {
                    player.sendMessage(redirectMsg);
                }
            }
        }

        // Start timer if enough players are online and auto-start is enabled
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
        World activeWorld = plugin.getActiveWorld();

        // Handle end-dimension respawn (death in The End)
        if (player.getWorld().getEnvironment() == World.Environment.THE_END && activeWorld != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    player.teleport(activeWorld.getSpawnLocation()), 1L);
            return;
        }

        if (activeWorld != null) {
            event.setRespawnLocation(activeWorld.getSpawnLocation());
        }
    }

    // ==================== Death Events ====================

    /**
     * Handles player death events.
     * Triggers world reset when a player dies in the active world set.
     *
     * <p>Players with the {@code hardcoreworldreset.bypass} permission are
     * excluded from triggering a world reset (useful for admins / spectators).
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player deadPlayer = event.getEntity();

        // Bypass permission check
        if (deadPlayer.hasPermission("hardcoreworldreset.bypass")) {
            return;
        }

        World activeWorld = plugin.getActiveWorld();
        if (activeWorld == null) return;

        // Only trigger if the death happened inside the active world set
        String playerWorldBase = plugin.getWorldManager().getBaseWorldName(deadPlayer.getWorld().getName());
        String activeWorldBase = plugin.getWorldManager().getBaseWorldName(activeWorld.getName());

        if (!playerWorldBase.equals(activeWorldBase)) return;

        // Already handled — prevent double-trigger
        if (plugin.isSwapping()) return;

        GameMode originalGameMode = deadPlayer.getGameMode();

        // Clear drops and XP in true hardcore fashion
        event.getDrops().clear();
        event.setDroppedExp(0);

        // Respawn player first, then trigger world swap
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            deadPlayer.spigot().respawn();
            plugin.triggerWorldSwap(deadPlayer, originalGameMode);
        }, 1L);
    }

    /**
     * Handles entity death events for end-goal detection
     * (Ender Dragon, Wither, Elder Guardian depending on config).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        ConfigManager config = plugin.getConfigManager();
        World activeWorld = plugin.getActiveWorld();
        if (activeWorld == null) return;

        var entity = event.getEntity();
        ConfigManager.EndGoal goal = config.getEndGoal();

        boolean isGoalEntity = switch (goal) {
            case ENDER_DRAGON  -> entity instanceof EnderDragon;
            case WITHER        -> entity instanceof Wither;
            case ELDER_GUARDIAN -> isElderGuardian(entity);
            case NONE          -> false;
        };

        if (!isGoalEntity) return;

        // Verify death occurred in the active world set
        String entityWorldBase = plugin.getWorldManager().getBaseWorldName(entity.getWorld().getName());
        String activeWorldBase = plugin.getWorldManager().getBaseWorldName(activeWorld.getName());
        if (!entityWorldBase.equals(activeWorldBase)) return;

        // Stop timer and broadcast victory
        String finalTime = plugin.stopTimerAndAnnounce();
        Component victoryMsg = config.getMessages().buildDragonDefeat(finalTime);
        Bukkit.broadcast(victoryMsg);

        plugin.getLogger().info("Run completed! Final time: " + finalTime);
    }

    // ==================== Quit & Pre-login ====================

    /**
     * Handles player quit events.
     * Pauses the timer when fewer players are online than {@code min-players-to-start}.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            int minPlayers = plugin.getConfigManager().getMinPlayersToStart();
            if (Bukkit.getOnlinePlayers().size() < minPlayers) {
                plugin.pauseTimer();
            }
        }, 1L);
    }

    /**
     * Handles async player pre-login events.
     * Prevents players from joining while a world swap is in progress.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (plugin.isSwapping()) {
            Component reason = plugin.getConfigManager().getMessages().worldResetting;
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, reason);
        }
    }

    // ==================== Helpers ====================

    private boolean isElderGuardian(org.bukkit.entity.Entity entity) {
        if (entity instanceof Guardian guardian) {
            return guardian.isElder();
        }
        return false;
    }
}