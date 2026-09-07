package com.github.kdltmhl.hardcoreworldreset;

import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Arrays;
import java.util.List;

/**
 * Manages plugin configuration with validation and defaults.
 * Provides a clean interface for accessing configuration values.
 */
public class ConfigManager {

    private final HardcoreWorldReset plugin;
    private FileConfiguration config;

    // Cached values for performance
    private boolean pluginEnabled;
    private String worldPrefix;
    private SwapMethod swapMethod;
    private EndGoal endGoal;
    private boolean autoStartTimer;
    private boolean preserveInventoryOnSwap;
    private boolean announceDeaths;
    private boolean showTimerInTab;
    private int worldPregenDistance;
    private int teleportDelay;
    private int minPlayersToStart;
    private boolean waitingRoomEnabled;
    private String waitingWorldName;
    private int waitingRoomRadius;
    private boolean soundsEnabled;
    private Sound resetStartSound;
    private Sound waitingRoomSound;
    private Sound worldReadySound;
    private float soundVolume;
    private float soundPitch;
    private Messages messages;

    /**
     * Swap method enumeration.
     */
    public enum SwapMethod {
        /** Instantly teleports all players to the new world */
        SEAMLESS,
        /** Disconnects all players until the new world is ready */
        DISCONNECT
    }

    /**
     * End goal enumeration.
     */
    public enum EndGoal {
        ENDER_DRAGON,
        WITHER,
        ELDER_GUARDIAN,
        NONE
    }

    /**
     * Container for configurable messages.
     */
    public static class Messages {
        public final String kickReason;
        public final String titleMain;
        public final String titleSubtitle;
        public final String dragonDefeat;
        public final String redirect;
        public final String worldResetting;
        public final String timerStarted;
        public final String timerPaused;
        public final String playerDied;

        public Messages(String kickReason, String titleMain, String titleSubtitle,
                String dragonDefeat, String redirect, String worldResetting,
                String timerStarted, String timerPaused, String playerDied) {
            this.kickReason = ChatColor.translateAlternateColorCodes('&', kickReason);
            this.titleMain = ChatColor.translateAlternateColorCodes('&', titleMain);
            this.titleSubtitle = ChatColor.translateAlternateColorCodes('&', titleSubtitle);
            this.dragonDefeat = ChatColor.translateAlternateColorCodes('&', dragonDefeat);
            this.redirect = ChatColor.translateAlternateColorCodes('&', redirect);
            this.worldResetting = ChatColor.translateAlternateColorCodes('&', worldResetting);
            this.timerStarted = ChatColor.translateAlternateColorCodes('&', timerStarted);
            this.timerPaused = ChatColor.translateAlternateColorCodes('&', timerPaused);
            this.playerDied = ChatColor.translateAlternateColorCodes('&', playerDied);
        }
    }

    /**
     * Creates a new ConfigManager.
     *
     * @param plugin The main plugin instance
     */
    public ConfigManager(HardcoreWorldReset plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads and validates the configuration.
     */
    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();

        loadCoreSettings();
        loadGameplaySettings();
        loadSoundSettings();
        loadMessages();

        validateConfig();
    }

    /**
     * Reloads the configuration from disk.
     */
    public void reload() {
        load();
        plugin.getLogger().info("Configuration reloaded successfully.");
    }

    /**
     * Loads core plugin settings.
     */
    private void loadCoreSettings() {
        pluginEnabled = config.getBoolean("plugin-enabled", true);
        worldPrefix = config.getString("world-prefix", "hardcore_");

        String swapMethodStr = config.getString("swap-method", "SEAMLESS").toUpperCase();
        try {
            swapMethod = SwapMethod.valueOf(swapMethodStr);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid swap-method: " + swapMethodStr + ". Using SEAMLESS.");
            swapMethod = SwapMethod.SEAMLESS;
        }

        String endGoalStr = config.getString("end-goal", "ENDER_DRAGON").toUpperCase();
        try {
            endGoal = EndGoal.valueOf(endGoalStr);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid end-goal: " + endGoalStr + ". Using ENDER_DRAGON.");
            endGoal = EndGoal.ENDER_DRAGON;
        }
    }

    /**
     * Loads gameplay-related settings.
     */
    private void loadGameplaySettings() {
        autoStartTimer = config.getBoolean("gameplay.auto-start-timer", true);
        preserveInventoryOnSwap = config.getBoolean("gameplay.preserve-inventory-on-swap", false);
        announceDeaths = config.getBoolean("gameplay.announce-deaths", true);
        showTimerInTab = config.getBoolean("gameplay.show-timer-in-tab", true);
        worldPregenDistance = config.getInt("performance.world-pregen-distance", 8);
        teleportDelay = config.getInt("gameplay.teleport-delay-ticks", 20);
        minPlayersToStart = config.getInt("gameplay.min-players-to-start", 1);
        waitingRoomEnabled = config.getBoolean("waiting-room.enabled", true);
        waitingWorldName = config.getString("waiting-room.world-name", "hardcore_waiting");
        waitingRoomRadius = config.getInt("waiting-room.room-radius", 4);
    }

    /**
     * Loads message configurations.
     */
    private void loadMessages() {
        messages = new Messages(
                config.getString("messages.kick-reason", "&6A player has died! The world is resetting."),
                config.getString("messages.title-main", "&cA player has died!"),
                config.getString("messages.title-subtitle", ""),
                config.getString("messages.dragon-defeat",
                        "&aThe Ender Dragon has been defeated! &fFinal Time: &e%time%"),
                config.getString("messages.redirect", "&aMoving you to the active hardcore world."),
                config.getString("messages.world-resetting", "&6The world is resetting, please wait..."),
                config.getString("messages.timer-started", "&aThe timer has started!"),
                config.getString("messages.timer-paused", "&eTimer paused - no players online."),
                config.getString("messages.player-died", "&c%player% has died!"));
    }

    /** Loads optional transition sound effects with safe fallbacks. */
    private void loadSoundSettings() {
        soundsEnabled = config.getBoolean("sounds.enabled", true);
        resetStartSound = readSound("sounds.reset-start", Sound.BLOCK_PORTAL_TRIGGER);
        waitingRoomSound = readSound("sounds.waiting-room", Sound.BLOCK_PORTAL_TRAVEL);
        worldReadySound = readSound("sounds.world-ready", Sound.ENTITY_PLAYER_LEVELUP);

        soundVolume = (float) config.getDouble("sounds.volume", 1.0D);
        soundPitch = (float) config.getDouble("sounds.pitch", 1.0D);
        if (!Float.isFinite(soundVolume) || soundVolume <= 0.0F) {
            soundVolume = 1.0F;
        }
        if (!Float.isFinite(soundPitch) || soundPitch <= 0.0F) {
            soundPitch = 1.0F;
        }
    }

    private Sound readSound(String path, Sound fallback) {
        String configured = config.getString(path, fallback.name());
        if (configured == null || configured.isBlank()) {
            return fallback;
        }

        try {
            return Sound.valueOf(configured.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Invalid sound at " + path + ": " + configured
                    + ". Using " + fallback.name() + ".");
            return fallback;
        }
    }

    /**
     * Validates the configuration and logs any issues.
     */
    private void validateConfig() {
        List<String> issues = new java.util.ArrayList<>();

        if (worldPrefix == null || worldPrefix.isEmpty()) {
            issues.add("world-prefix cannot be empty");
            worldPrefix = "hardcore_";
        }

        if (worldPrefix.contains(" ")) {
            issues.add("world-prefix should not contain spaces");
            worldPrefix = worldPrefix.replace(" ", "_");
        }

        if (teleportDelay < 0) {
            issues.add("teleport-delay-ticks cannot be negative");
            teleportDelay = 20;
        }

        if (minPlayersToStart < 1) {
            issues.add("min-players-to-start must be at least 1");
            minPlayersToStart = 1;
        }

        if (worldPregenDistance < 0 || worldPregenDistance > 32) {
            issues.add("world-pregen-distance must be between 0 and 32");
            worldPregenDistance = Math.max(0, Math.min(32, worldPregenDistance));
        }

        if (waitingWorldName == null || waitingWorldName.isBlank()
                || !waitingWorldName.matches("[A-Za-z0-9._-]+")
                || waitingWorldName.equals(".") || waitingWorldName.equals("..")
                || waitingWorldName.equals(worldPrefix + "1")
                || waitingWorldName.equals(worldPrefix + "2")) {
            issues.add("waiting-room.world-name is invalid; using hardcore_waiting");
            waitingWorldName = "hardcore_waiting";
        }

        if (waitingRoomRadius < 2 || waitingRoomRadius > 32) {
            issues.add("waiting-room.room-radius must be between 2 and 32");
            waitingRoomRadius = Math.max(2, Math.min(32, waitingRoomRadius));
        }

        if (!waitingRoomEnabled && swapMethod == SwapMethod.SEAMLESS) {
            plugin.getLogger().warning("Waiting room is disabled. Only disable it on a server with enough performance headroom: world generation will happen while players remain in the active world.");
        }

        if (!issues.isEmpty()) {
            plugin.getLogger().warning("Configuration issues detected:");
            for (String issue : issues) {
                plugin.getLogger().warning("  - " + issue);
            }
        }
    }

    // ==================== Getters ====================

    /**
     * @return true if the plugin is enabled
     */
    public boolean isPluginEnabled() {
        return pluginEnabled;
    }

    /**
     * @return The world name prefix
     */
    public String getWorldPrefix() {
        return worldPrefix;
    }

    /**
     * @return The swap method
     */
    public SwapMethod getSwapMethod() {
        return swapMethod;
    }

    /**
     * @return The end goal
     */
    public EndGoal getEndGoal() {
        return endGoal;
    }

    /**
     * @return true if timer should auto-start
     */
    public boolean isAutoStartTimer() {
        return autoStartTimer;
    }

    /**
     * @return true if inventory should be preserved on world swap
     */
    public boolean isPreserveInventoryOnSwap() {
        return preserveInventoryOnSwap;
    }

    /**
     * @return true if deaths should be announced
     */
    public boolean isAnnounceDeaths() {
        return announceDeaths;
    }

    /**
     * @return true if timer should be shown in tab
     */
    public boolean isShowTimerInTab() {
        return showTimerInTab;
    }

    /**
     * @return The world pregen distance in chunks
     */
    public int getWorldPregenDistance() {
        return worldPregenDistance;
    }

    /**
     * @return The teleport delay in ticks
     */
    public int getTeleportDelay() {
        return teleportDelay;
    }

    /**
     * @return The minimum players required to start
     */
    public int getMinPlayersToStart() {
        return minPlayersToStart;
    }

    /**
     * @return true when seamless swaps should use the protected waiting room
     */
    public boolean isWaitingRoomEnabled() {
        return waitingRoomEnabled;
    }

    /**
     * @return the persistent waiting world name
     */
    public String getWaitingWorldName() {
        return waitingWorldName;
    }

    /**
     * @return the waiting room half-size in blocks
     */
    public int getWaitingRoomRadius() {
        return waitingRoomRadius;
    }

    public boolean isSoundsEnabled() {
        return soundsEnabled;
    }

    public Sound getResetStartSound() {
        return resetStartSound;
    }

    public Sound getWaitingRoomSound() {
        return waitingRoomSound;
    }

    public Sound getWorldReadySound() {
        return worldReadySound;
    }

    public float getSoundVolume() {
        return soundVolume;
    }

    public float getSoundPitch() {
        return soundPitch;
    }

    /**
     * @return The messages container
     */
    public Messages getMessages() {
        return messages;
    }

    // ==================== State Management ====================

    /**
     * Gets the active world name from saved state.
     *
     * @return The active world name
     */
    public String getActiveWorldName() {
        return config.getString("state.active-world", worldPrefix + "1");
    }

    /**
     * Gets the standby world name from saved state.
     *
     * @return The standby world name
     */
    public String getStandbyWorldName() {
        return config.getString("state.standby-world", worldPrefix + "2");
    }

    /**
     * Gets the world counter from saved state.
     *
     * @return The world counter
     */
    public int getWorldCounter() {
        return config.getInt("state.world-counter", 2);
    }

    /**
     * Gets the generation identifier of the latest world reset.
     *
     * @return reset generation, never negative
     */
    public long getResetGeneration() {
        return Math.max(0L, config.getLong("state.reset-generation", 0L));
    }

    /**
     * Returns whether a real world reset has requested cleanup for players
     * who were offline. Missing state is deliberately treated as false so a
     * plugin update or legacy-config migration can never delete inventory.
     */
    public boolean isResetCleanupPending() {
        return config.getBoolean("state.reset-cleanup-pending", false);
    }

    /**
     * Saves the current plugin state.
     *
     * @param activeWorld  The active world name
     * @param standbyWorld The standby world name
     * @param worldCounter The world counter
     */
    public void saveState(String activeWorld, String standbyWorld, int worldCounter) {
        saveState(activeWorld, standbyWorld, worldCounter, getResetGeneration());
    }

    /**
     * Saves the current plugin state and reset generation atomically.
     *
     * @param activeWorld     the active world name
     * @param standbyWorld    the standby world name
     * @param worldCounter    the world counter
     * @param resetGeneration the current reset generation
     */
    public void saveState(String activeWorld, String standbyWorld, int worldCounter, long resetGeneration) {
        config.set("state.active-world", activeWorld);
        config.set("state.standby-world", standbyWorld);
        config.set("state.world-counter", worldCounter);
        config.set("state.reset-generation", Math.max(0L, resetGeneration));
        // Preserve this flag during startup/reload/update. Only
        // saveResetGeneration may turn it on for a real reset.
        config.set("state.reset-cleanup-pending", isResetCleanupPending());
        plugin.saveConfig();
    }

    /**
     * Saves the reset generation used for join cleanup.
     *
     * @param resetGeneration the current reset generation
     */
    public void saveResetGeneration(long resetGeneration) {
        config.set("state.reset-generation", Math.max(0L, resetGeneration));
        config.set("state.reset-cleanup-pending", true);
        plugin.saveConfig();
    }

    /**
     * Returns whether a timer run has been started and should be retained.
     *
     * @return true if a timer run exists
     */
    public boolean isTimerStarted() {
        return config.getBoolean("state.timer.started", false);
    }

    /**
     * Gets the elapsed time saved for the current timer run.
     *
     * @return elapsed milliseconds, never negative
     */
    public long getTimerElapsedMillis() {
        return Math.max(0L, config.getLong("state.timer.elapsed-millis", 0L));
    }

    /**
     * Persists timer state so a server restart does not reset the current run.
     *
     * @param started       whether a run exists
     * @param elapsedMillis elapsed time for the run
     */
    public void saveTimerState(boolean started, long elapsedMillis) {
        config.set("state.timer.started", started);
        config.set("state.timer.elapsed-millis", Math.max(0L, elapsedMillis));
        plugin.saveConfig();
    }

    /**
     * Gets available swap methods as a list.
     *
     * @return List of swap method names
     */
    public static List<String> getAvailableSwapMethods() {
        return Arrays.stream(SwapMethod.values())
                .map(Enum::name)
                .toList();
    }

    /**
     * Gets available end goals as a list.
     *
     * @return List of end goal names
     */
    public static List<String> getAvailableEndGoals() {
        return Arrays.stream(EndGoal.values())
                .map(Enum::name)
                .toList();
    }
}
