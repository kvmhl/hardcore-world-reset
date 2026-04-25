package com.github.kdltmhl.hardcoreworldreset;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

/**
 * Main plugin class for HardcoreWorldReset.
 *
 * <p>Manages world resets on player death in hardcore mode.
 * As of v3.0.0 (Minecraft 1.21.5) all text output uses the
 * Adventure API — {@code ChatColor}, {@code broadcastMessage},
 * and {@code kickPlayer(String)} are fully removed.
 */
public final class HardcoreWorldReset extends JavaPlugin {

    private ConfigManager configManager;
    private WorldManager worldManager;
    private PortalHandler portalHandler;

    private String activeWorldName;
    private String standbyWorldName;
    private int worldCounter;

    private BukkitTask timerTask;
    private long startTime = 0L;
    private long pausedTime = 0L;
    private boolean isTimerRunning = false;
    private boolean isSwapping = false;

    // ==================== Lifecycle ====================

    @Override
    public void onEnable() {
        // Load configuration
        configManager = new ConfigManager(this);
        configManager.load();

        // Check if plugin should be enabled
        if (!configManager.isPluginEnabled()) {
            getLogger().warning("Plugin is disabled via config.yml. Shutting down.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Check hardcore mode (skip in test environments)
        if (!isTestEnvironment() && !Bukkit.isHardcore()) {
            getLogger().severe("SERVER IS NOT IN HARDCORE MODE! Disabling plugin.");
            getLogger().severe("Set 'hardcore=true' in server.properties to enable.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize managers
        worldManager = new WorldManager(this);
        portalHandler = new PortalHandler(this, worldManager);

        // Delay initialization to ensure server is fully loaded
        Bukkit.getScheduler().runTaskLater(this, this::initialize, 1L);
    }

    @Override
    public void onDisable() {
        if (timerTask != null) {
            timerTask.cancel();
        }
        getLogger().info("HardcoreWorldReset disabled.");
    }

    /**
     * Initializes the plugin after server startup.
     */
    private void initialize() {
        loadStateFromConfig();

        getLogger().info("HardcoreWorldReset Initializing...");
        getLogger().info("Active world:  " + activeWorldName);
        getLogger().info("Standby world: " + standbyWorldName);
        getLogger().info("Swap method:   " + configManager.getSwapMethod());

        setupInitialWorlds();

        // Register event listeners
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(portalHandler, this);

        // Register /hwr command
        var executor = new AdminCommandExecutor(this);
        var cmd = getCommand("hwr");
        if (cmd != null) {
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        getLogger().info("HardcoreWorldReset v" + getDescription().getVersion() + " enabled successfully!");
    }

    // ==================== State ====================

    /**
     * Loads state from configuration.
     */
    private void loadStateFromConfig() {
        String savedActiveWorld = configManager.getActiveWorldName();
        String prefix = configManager.getWorldPrefix();

        if (savedActiveWorld == null || !savedActiveWorld.startsWith(prefix)) {
            getLogger().warning("No valid state found or world-prefix has changed. Resetting state.");
            this.activeWorldName  = prefix + "1";
            this.standbyWorldName = prefix + "2";
            this.worldCounter     = 2;
            savePluginState();
        } else {
            this.activeWorldName  = savedActiveWorld;
            this.standbyWorldName = configManager.getStandbyWorldName();
            this.worldCounter     = configManager.getWorldCounter();
        }
    }

    /**
     * Saves the current plugin state to config.
     */
    private void savePluginState() {
        configManager.saveState(activeWorldName, standbyWorldName, worldCounter);
    }

    // ==================== World Setup ====================

    /**
     * Sets up initial worlds on plugin startup.
     */
    private void setupInitialWorlds() {
        if (Bukkit.getWorld("world") != null) {
            getLogger().info("Unloading default 'world' — we manage our own worlds.");
            if (!isTestEnvironment()) {
                Bukkit.unloadWorld("world", false);
            }
        }

        worldManager.createWorldSet(activeWorldName);

        if (configManager.getSwapMethod() == ConfigManager.SwapMethod.SEAMLESS) {
            worldManager.createWorldSet(standbyWorldName);
        }

        getLogger().info("World setup complete. Ready for hardcore gameplay!");
    }

    // ==================== World Swap ====================

    /**
     * Triggers a world swap after a player death.
     *
     * @param deadPlayer       The player who died
     * @param originalGameMode The player's game mode before death
     */
    public void triggerWorldSwap(Player deadPlayer, GameMode originalGameMode) {
        this.isSwapping = true;
        this.resetTimer();

        // Announce death if configured
        if (configManager.isAnnounceDeaths() && deadPlayer != null) {
            Component msg = configManager.getMessages().buildPlayerDied(deadPlayer.getName());
            Bukkit.broadcast(msg);
        }

        // Revoke all advancements for all players
        revokeAllAdvancements();

        // Handle swap based on method
        if (configManager.getSwapMethod() == ConfigManager.SwapMethod.SEAMLESS) {
            handleSeamlessSwap();
        } else {
            handleDisconnectSwap();
        }

        // Restore game mode for dead player
        if (deadPlayer != null && deadPlayer.isOnline()) {
            if (originalGameMode == GameMode.CREATIVE || originalGameMode == GameMode.SPECTATOR) {
                deadPlayer.setGameMode(originalGameMode);
            } else {
                deadPlayer.setGameMode(GameMode.SURVIVAL);
            }
        }

        // Start timer for new run
        if (configManager.isAutoStartTimer()) {
            this.startOrResumeTimer();
        }

        // Schedule world cleanup and new standby creation
        String oldWorldBaseName    = this.activeWorldName;
        String newStandbyBaseName  = getWorldPrefix() + (++worldCounter);
        this.activeWorldName  = this.standbyWorldName;
        this.standbyWorldName = newStandbyBaseName;
        savePluginState();

        int delay = configManager.getTeleportDelay() + 80;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            worldManager.deleteWorldSet(oldWorldBaseName);

            worldManager.createWorldSet(activeWorldName);

            if (configManager.getSwapMethod() == ConfigManager.SwapMethod.SEAMLESS) {
                worldManager.createWorldSet(newStandbyBaseName);
            }
            this.isSwapping = false;
            getLogger().info("World swap complete. New active world: " + activeWorldName);
        }, delay);
    }

    /**
     * Handles seamless world swap by teleporting all players.
     */
    private void handleSeamlessSwap() {
        World newWorld = Bukkit.getWorld(standbyWorldName);
        if (newWorld == null) {
            getLogger().severe("Standby world '" + standbyWorldName + "' not found! Cannot swap.");
            return;
        }

        Location spawnLocation = newWorld.getSpawnLocation();
        ConfigManager.Messages messages = configManager.getMessages();

        // Build Adventure title with fade timings
        Title title = Title.title(
                messages.titleMain,
                messages.titleSubtitle,
                Title.Times.times(
                        Duration.ofMillis(500),   // fade in
                        Duration.ofSeconds(3),    // stay
                        Duration.ofMillis(1000)   // fade out
                )
        );

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.teleport(spawnLocation);
            player.showTitle(title);
        }

        getLogger().info("Teleported " + Bukkit.getOnlinePlayers().size() + " players to " + standbyWorldName);
    }

    /**
     * Handles disconnect swap by kicking all players.
     * Uses {@link Player#kick(Component)} — the modern Adventure replacement
     * for the deprecated {@code kickPlayer(String)}.
     */
    private void handleDisconnectSwap() {
        Component kickReason = configManager.getMessages().kickReason;
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.kick(kickReason);
        }
        getLogger().info("Kicked all players for world reset.");
    }

    /**
     * Revokes all advancements for all online players.
     */
    private void revokeAllAdvancements() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Iterator<Advancement> iterator = Bukkit.getServer().advancementIterator();
            while (iterator.hasNext()) {
                AdvancementProgress progress = player.getAdvancementProgress(iterator.next());
                for (String criteria : progress.getAwardedCriteria()) {
                    progress.revokeCriteria(criteria);
                }
            }
        }
    }

    // ==================== Timer ====================

    /**
     * Starts or resumes the speedrun timer.
     */
    public void startOrResumeTimer() {
        if (isTimerRunning) return;

        startTime     = System.currentTimeMillis() - pausedTime;
        pausedTime    = 0L;
        isTimerRunning = true;

        if (configManager.isShowTimerInTab()) {
            timerTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
                long elapsedMillis = System.currentTimeMillis() - startTime;
                Component footer = Component.text("⏱ Time: ", NamedTextColor.GOLD)
                        .append(Component.text(formatTime(elapsedMillis), NamedTextColor.YELLOW));
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendPlayerListFooter(footer);
                }
            }, 0L, 1L);
        }
    }

    /**
     * Pauses the speedrun timer.
     */
    public void pauseTimer() {
        if (!isTimerRunning) return;

        if (timerTask != null) timerTask.cancel();
        pausedTime     = System.currentTimeMillis() - startTime;
        isTimerRunning = false;
    }

    /**
     * Stops the timer and updates the tab footer with the final time.
     *
     * @return The formatted final time string, or empty string if timer was not running
     */
    public String stopTimerAndAnnounce() {
        if (!isTimerRunning && pausedTime == 0L) return "";

        if (timerTask != null) timerTask.cancel();

        long finalMillis = isTimerRunning
                ? (System.currentTimeMillis() - startTime)
                : pausedTime;

        String formattedTime = formatTime(finalMillis);

        Component footer = Component.text("✔ Final Time: ", NamedTextColor.GREEN)
                .append(Component.text(formattedTime, NamedTextColor.YELLOW)
                        .decorate(TextDecoration.BOLD));

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendPlayerListFooter(footer);
        }

        isTimerRunning = false;
        pausedTime     = 0L;
        return formattedTime;
    }

    /**
     * Resets the speedrun timer.
     */
    public void resetTimer() {
        if (timerTask != null) timerTask.cancel();

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendPlayerListFooter(Component.empty());
        }

        isTimerRunning = false;
        startTime      = 0L;
        pausedTime     = 0L;
    }

    /**
     * Formats milliseconds into HH:MM:SS.cc format.
     *
     * @param millis The milliseconds to format
     * @return The formatted time string
     */
    private String formatTime(long millis) {
        long hours   = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
        long hundreds = (millis / 10) % 100;
        return String.format("%02d:%02d:%02d.%02d", hours, minutes, seconds, hundreds);
    }

    // ==================== Getters ====================

    /** @return true if the plugin is currently swapping worlds */
    public boolean isSwapping() { return this.isSwapping; }

    /**
     * Gets the currently active overworld.
     *
     * @return The active world, or null if not loaded
     */
    public World getActiveWorld() { return Bukkit.getWorld(activeWorldName); }

    /** @return The active world name */
    public String getActiveWorldName() { return activeWorldName; }

    /** @return The world prefix from configuration */
    public String getWorldPrefix() { return configManager.getWorldPrefix(); }

    /** @return The configuration manager */
    public ConfigManager getConfigManager() { return configManager; }

    /** @return The world manager */
    public WorldManager getWorldManager() { return worldManager; }

    /**
     * Checks if running inside a MockBukkit test environment.
     *
     * @return true if MockBukkit is on the classpath
     */
    private boolean isTestEnvironment() {
        try {
            Class.forName("be.seeseemelk.mockbukkit.MockBukkit");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    // ==================== Package-private setters for testing ====================

    /** @param worldManager The world manager (for testing) */
    void setWorldManager(WorldManager worldManager) { this.worldManager = worldManager; }

    /** @param configManager The config manager (for testing) */
    void setConfigManager(ConfigManager configManager) { this.configManager = configManager; }

    /** @param activeWorldName The active world name (for testing) */
    void setActiveWorldName(String activeWorldName) { this.activeWorldName = activeWorldName; }

    /** @param standbyWorldName The standby world name (for testing) */
    void setStandbyWorldName(String standbyWorldName) { this.standbyWorldName = standbyWorldName; }

    /** @param isSwapping The swapping state (for testing) */
    void setSwapping(boolean isSwapping) { this.isSwapping = isSwapping; }
}