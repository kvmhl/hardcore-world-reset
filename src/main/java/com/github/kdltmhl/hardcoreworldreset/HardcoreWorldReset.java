package com.github.kdltmhl.hardcoreworldreset;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;

/**
 * Main plugin class for HardcoreWorldReset.
 * Manages world resets on player death in hardcore mode.
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
        getLogger().info("Active world: " + activeWorldName);
        getLogger().info("Standby world: " + standbyWorldName);
        getLogger().info("Swap method: " + configManager.getSwapMethod());

        setupInitialWorlds();

        // Register event listeners
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(portalHandler, this);

        getLogger().info("HardcoreWorldReset v" + getDescription().getVersion() + " enabled successfully!");
    }

    /**
     * Loads state from configuration.
     */
    private void loadStateFromConfig() {
        String savedActiveWorld = configManager.getActiveWorldName();
        String prefix = configManager.getWorldPrefix();

        if (savedActiveWorld == null || !savedActiveWorld.startsWith(prefix)) {
            getLogger().warning("No valid state found or world-prefix has changed. Resetting state.");
            this.activeWorldName = prefix + "1";
            this.standbyWorldName = prefix + "2";
            this.worldCounter = 2;
            savePluginState();
        } else {
            this.activeWorldName = savedActiveWorld;
            this.standbyWorldName = configManager.getStandbyWorldName();
            this.worldCounter = configManager.getWorldCounter();
        }
    }

    /**
     * Saves the current plugin state to config.
     */
    private void savePluginState() {
        configManager.saveState(activeWorldName, standbyWorldName, worldCounter);
    }

    /**
     * Sets up initial worlds on plugin startup.
     */
    private void setupInitialWorlds() {
        // Unload default world if it exists (we manage our own worlds)
        if (Bukkit.getWorld("world") != null) {
            getLogger().info("Unloading default 'world' - we manage our own worlds.");
            // Don't unload in test environment
            if (!isTestEnvironment()) {
                Bukkit.unloadWorld("world", false);
            }
        }

        // Create or load the active world set
        worldManager.createWorldSet(activeWorldName);

        // Create or load the standby world set (for seamless swapping)
        if (configManager.getSwapMethod() == ConfigManager.SwapMethod.SEAMLESS) {
            worldManager.createWorldSet(standbyWorldName);
        }

        getLogger().info("World setup complete. Ready for hardcore gameplay!");
    }

    /**
     * Triggers a world swap after a player death.
     *
     * @param deadPlayer       The player who died
     * @param originalGameMode The player's original game mode
     */
    public void triggerWorldSwap(Player deadPlayer, GameMode originalGameMode) {
        this.isSwapping = true;
        this.resetTimer();

        // Announce death if configured
        if (configManager.isAnnounceDeaths() && deadPlayer != null) {
            String deathMessage = configManager.getMessages().playerDied
                    .replace("%player%", deadPlayer.getName());
            Bukkit.broadcastMessage(deathMessage);
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
        String oldWorldBaseName = this.activeWorldName;
        String newStandbyBaseName = getWorldPrefix() + (++worldCounter);
        this.activeWorldName = this.standbyWorldName;
        this.standbyWorldName = newStandbyBaseName;
        savePluginState();

        // Delayed cleanup and new world creation
        int delay = configManager.getTeleportDelay() + 80; // Extra time for teleports to complete
        Bukkit.getScheduler().runTaskLater(this, () -> {
            worldManager.deleteWorldSet(oldWorldBaseName);

            // Ensure the new active world is created immediately (vital for DISCONNECT
            // mode)
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

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.teleport(spawnLocation);
            player.sendTitle(messages.titleMain, messages.titleSubtitle, 10, 70, 20);
        }

        getLogger().info("Teleported " + Bukkit.getOnlinePlayers().size() + " players to " + standbyWorldName);
    }

    /**
     * Handles disconnect swap by kicking all players.
     */
    private void handleDisconnectSwap() {
        String kickReason = configManager.getMessages().kickReason;
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.kickPlayer(kickReason);
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

    // ==================== Timer Methods ====================

    /**
     * Starts or resumes the timer.
     */
    public void startOrResumeTimer() {
        if (isTimerRunning) {
            return;
        }

        startTime = System.currentTimeMillis() - pausedTime;
        pausedTime = 0L;
        isTimerRunning = true;

        if (configManager.isShowTimerInTab()) {
            timerTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
                long elapsedMillis = System.currentTimeMillis() - startTime;
                String formattedTime = formatTime(elapsedMillis);
                String footer = ChatColor.GOLD + "Time: " + formattedTime;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.setPlayerListFooter(footer);
                }
            }, 0L, 1L);
        }
    }

    /**
     * Pauses the timer.
     */
    public void pauseTimer() {
        if (!isTimerRunning) {
            return;
        }

        if (timerTask != null) {
            timerTask.cancel();
        }
        pausedTime = System.currentTimeMillis() - startTime;
        isTimerRunning = false;
    }

    /**
     * Stops the timer and announces the final time.
     *
     * @return The formatted final time
     */
    public String stopTimerAndAnnounce() {
        if (!isTimerRunning && pausedTime == 0L) {
            return "";
        }

        if (timerTask != null) {
            timerTask.cancel();
        }

        long finalMillis = isTimerRunning ? (System.currentTimeMillis() - startTime) : pausedTime;
        String formattedTime = formatTime(finalMillis);
        String footer = ChatColor.GREEN + "Final Time: " + formattedTime;

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setPlayerListFooter(footer);
        }

        isTimerRunning = false;
        pausedTime = 0L;
        return formattedTime;
    }

    /**
     * Resets the timer.
     */
    public void resetTimer() {
        if (timerTask != null) {
            timerTask.cancel();
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setPlayerListFooter(null);
        }

        isTimerRunning = false;
        startTime = 0L;
        pausedTime = 0L;
    }

    /**
     * Formats milliseconds into HH:MM:SS.ms format.
     *
     * @param millis The milliseconds to format
     * @return The formatted time string
     */
    private String formatTime(long millis) {
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
        long hundreds = (millis / 10) % 100;
        return String.format("%02d:%02d:%02d.%02d", hours, minutes, seconds, hundreds);
    }

    // ==================== Getters ====================

    /**
     * Checks if the plugin is currently swapping worlds.
     *
     * @return true if swapping
     */
    public boolean isSwapping() {
        return this.isSwapping;
    }

    /**
     * Gets the currently active world.
     *
     * @return The active world, or null if not loaded
     */
    public World getActiveWorld() {
        return Bukkit.getWorld(activeWorldName);
    }

    /**
     * Gets the active world name.
     *
     * @return The active world name
     */
    public String getActiveWorldName() {
        return activeWorldName;
    }

    /**
     * Gets the world prefix from configuration.
     *
     * @return The world prefix
     */
    public String getWorldPrefix() {
        return configManager.getWorldPrefix();
    }

    /**
     * Gets the configuration manager.
     *
     * @return The config manager
     */
    public ConfigManager getConfigManager() {
        return configManager;
    }

    /**
     * Gets the world manager.
     *
     * @return The world manager
     */
    public WorldManager getWorldManager() {
        return worldManager;
    }

    /**
     * Checks if running in a test environment.
     *
     * @return true if in test environment
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

    /**
     * Sets the world manager (for testing).
     *
     * @param worldManager The world manager
     */
    void setWorldManager(WorldManager worldManager) {
        this.worldManager = worldManager;
    }

    /**
     * Sets the config manager (for testing).
     *
     * @param configManager The config manager
     */
    void setConfigManager(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Sets the active world name (for testing).
     *
     * @param activeWorldName The active world name
     */
    void setActiveWorldName(String activeWorldName) {
        this.activeWorldName = activeWorldName;
    }

    /**
     * Sets the standby world name (for testing).
     *
     * @param standbyWorldName The standby world name
     */
    void setStandbyWorldName(String standbyWorldName) {
        this.standbyWorldName = standbyWorldName;
    }

    /**
     * Sets the swapping state (for testing).
     *
     * @param isSwapping The swapping state
     */
    void setSwapping(boolean isSwapping) {
        this.isSwapping = isSwapping;
    }
}