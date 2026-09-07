package com.github.kdltmhl.hardcoreworldreset;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;

/**
 * Main plugin class for HardcoreWorldReset.
 * Manages world resets on player death in hardcore mode.
 */
public class HardcoreWorldReset extends JavaPlugin {

    private ConfigManager configManager;
    private WorldManager worldManager;
    private PortalHandler portalHandler;

    private String activeWorldName;
    private String standbyWorldName;
    private int worldCounter;

    private BukkitTask timerTask;
    private long startTime = 0L;
    private long pausedTime = 0L;
    private boolean timerHasStarted = false;
    private boolean isTimerRunning = false;
    private boolean isSwapping = false;
    private boolean activeWorldReady = true;
    private long resetGeneration = 0L;
    private NamespacedKey resetGenerationKey;

    @Override
    public void onEnable() {
        // Enabling/reloading/updating this plugin must never be interpreted as
        // a world reset. Inventory cleanup is only armed by
        // beginNewRunAndCleanPlayers().
        getLogger().info("Plugin startup is inventory-safe; no inventory cleanup is performed during enable.");

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
        resetGenerationKey = new NamespacedKey(this, "reset-generation");

        // Delay initialization to ensure server is fully loaded
        Bukkit.getScheduler().runTaskLater(this, this::initialize, 1L);
    }

    @Override
    public void onDisable() {
        // Preserve the current run before cancelling the scheduler. A normal
        // server restart must not reset the timer.
        persistTimerState();
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
        prepareInitialWorlds();

        getLogger().info("HardcoreWorldReset v" + getDescription().getVersion() + " enabled successfully!");
    }

    /**
     * Loads state from configuration.
     */
    private void loadStateFromConfig() {
        this.resetGeneration = configManager.getResetGeneration();
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

        loadTimerStateFromConfig();
    }

    /**
     * Loads timer progress without starting a scheduler before players are online.
     */
    private void loadTimerStateFromConfig() {
        timerHasStarted = configManager.isTimerStarted();
        pausedTime = timerHasStarted ? configManager.getTimerElapsedMillis() : 0L;
        startTime = 0L;
        isTimerRunning = false;
    }

    /**
     * Saves the current plugin state to config.
     */
    private void savePluginState() {
        configManager.saveState(activeWorldName, standbyWorldName, worldCounter, resetGeneration);
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
            if (configManager.isWaitingRoomEnabled() && worldManager.createWaitingWorld() == null) {
                getLogger().warning("The configured waiting room could not be prepared. Seamless swaps will use the kick fallback if needed.");
            }
        }

        getLogger().info("World setup complete. Ready for hardcore gameplay!");
    }

    /**
     * Prepares the active world before allowing the timer to start. In
     * seamless mode the standby world is prepared as well, so the first swap
     * never starts a run while spawn chunks are still being generated.
     */
    private void prepareInitialWorlds() {
        activeWorldReady = false;
        worldManager.prepareWorldSet(activeWorldName, () -> {
            if (configManager.getSwapMethod() == ConfigManager.SwapMethod.SEAMLESS) {
                worldManager.prepareWorldSet(standbyWorldName, () -> {
                    // Keep one additional world set ready as a buffer. This
                    // means the first reset can swap immediately instead of
                    // starting generation while the first run is active.
                    prepareWorldSetIfNeeded(getWorldPrefix() + (worldCounter + 1), () -> {
                        activeWorldReady = true;
                        startTimerIfPlayersReady();
                    });
                });
            } else {
                activeWorldReady = true;
                startTimerIfPlayersReady();
            }
        });
    }

    private void startTimerIfPlayersReady() {
        if (configManager.isAutoStartTimer()
                && Bukkit.getOnlinePlayers().size() >= configManager.getMinPlayersToStart()
                && !isTimerRunning) {
            startOrResumeTimer();
        }
    }

    /**
     * Triggers a world swap after a player death.
     *
     * @param deadPlayer       The player who died
     * @param originalGameMode The player's original game mode
     */
    public void triggerWorldSwap(Player deadPlayer, GameMode originalGameMode) {
        if (isSwapping) {
            return;
        }

        this.isSwapping = true;
        this.resetTimer();
        beginNewRunAndCleanPlayers();

        // Announce death if configured
        if (configManager.isAnnounceDeaths() && deadPlayer != null) {
            String deathMessage = configManager.getMessages().playerDied
                    .replace("%player%", deadPlayer.getName());
            Bukkit.broadcastMessage(deathMessage);
        }

        // Revoke all advancements for all players
        revokeAllAdvancements();

        String oldWorldBaseName = this.activeWorldName;
        String newActiveWorldName = this.standbyWorldName;
        int nextWorldCounter = ++worldCounter;
        String newStandbyWorldName = getWorldPrefix() + nextWorldCounter;

        // A disconnect swap can prepare the replacement after players have
        // been kicked. A seamless swap must prepare everything before the
        // teleport, otherwise generation would lag the active run.
        if (configManager.getSwapMethod() == ConfigManager.SwapMethod.SEAMLESS) {
            prepareAndCompleteSeamlessSwap(deadPlayer, originalGameMode,
                    oldWorldBaseName, newActiveWorldName, newStandbyWorldName,
                    nextWorldCounter);
            return;
        } else {
            handleDisconnectSwap();
        }

        completeDisconnectSwap(deadPlayer, originalGameMode, oldWorldBaseName,
                newActiveWorldName, newStandbyWorldName, nextWorldCounter);
    }

    private void prepareAndCompleteSeamlessSwap(Player deadPlayer, GameMode originalGameMode,
            String oldWorldBaseName, String newActiveWorldName, String newStandbyWorldName,
            int nextWorldCounter) {
        // Move players out of the active run before starting any generation.
        // The timer stays stopped because isSwapping remains true until the
        // complete replacement set is prepared and players return to it.
        if (configManager.isWaitingRoomEnabled()
                && !worldManager.teleportPlayersToWaitingWorld()) {
            // Never start generation with players exposed to a broken or
            // missing waiting room. Reconnect is blocked until preparation
            // completes, so this is the safe fallback.
            getLogger().warning("Waiting-room transition failed; falling back to DISCONNECT behavior for this swap.");
            handleDisconnectSwap();
        }

        String futureStandbyWorldName = getWorldPrefix() + (nextWorldCounter + 1);
        prepareWorldSetIfNeeded(newStandbyWorldName, () ->
                prepareWorldSetIfNeeded(futureStandbyWorldName, () ->
                        prepareWorldSetIfNeeded(newActiveWorldName, () -> {
                            this.activeWorldName = newActiveWorldName;
                            this.standbyWorldName = newStandbyWorldName;
                            this.worldCounter = nextWorldCounter;
                            this.activeWorldReady = true;
                            savePluginState();

                            if (!handleSeamlessSwap()) {
                                recoverFailedSeamlessSwap();
                                return;
                            }
                            restoreGameMode(deadPlayer, originalGameMode);
                            startTimerIfPlayersReady();
                            scheduleSwapCleanup(oldWorldBaseName);
                        })));
    }

    private void completeDisconnectSwap(Player deadPlayer, GameMode originalGameMode,
            String oldWorldBaseName, String newActiveWorldName, String newStandbyWorldName,
            int nextWorldCounter) {
        restoreGameMode(deadPlayer, originalGameMode);
        this.activeWorldName = newActiveWorldName;
        this.standbyWorldName = newStandbyWorldName;
        this.worldCounter = nextWorldCounter;
        this.activeWorldReady = false;
        savePluginState();

        int delay = configManager.getTeleportDelay() + 80; // Extra time for teleports to complete
        Bukkit.getScheduler().runTaskLater(this, () -> {
            worldManager.deleteWorldSet(oldWorldBaseName);

            // Ensure the new active world is created after kicked players can
            // reconnect. The timer starts from PlayerJoinEvent once ready.
            worldManager.createWorldSet(activeWorldName);
            activeWorldReady = true;
            this.isSwapping = false;
            getLogger().info("World swap complete. New active world: " + activeWorldName);
        }, delay);
    }

    private void prepareWorldSetIfNeeded(String worldSetName, Runnable onReady) {
        if (Bukkit.getWorld(worldSetName) == null
                || Bukkit.getWorld(worldSetName + "_nether") == null
                || Bukkit.getWorld(worldSetName + "_the_end") == null) {
            worldManager.createWorldSet(worldSetName);
        }

        if (worldManager.isWorldSetPrepared(worldSetName)) {
            onReady.run();
        } else {
            worldManager.prepareWorldSet(worldSetName, onReady);
        }
    }

    /**
     * Recovers from the unlikely case where a prepared active world vanished
     * before the final teleport. Players are kept out of the broken world and
     * the plugin does not start a timer against a missing world set.
     */
    private void recoverFailedSeamlessSwap() {
        getLogger().severe("The prepared active world disappeared before the seamless teleport. Falling back safely.");
        handleDisconnectSwap();
        activeWorldReady = false;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            boolean recreated = worldManager.createWorldSet(activeWorldName);
            if (recreated && worldManager.isCompleteWorldSetLoaded(activeWorldName)) {
                activeWorldReady = true;
                isSwapping = false;
                getLogger().warning("Active world was recreated after the failed seamless swap.");
                startTimerIfPlayersReady();
            } else {
                isSwapping = false;
                getLogger().severe("Could not recover active world " + activeWorldName
                        + ". Check the server logs and world storage before allowing players to rejoin.");
            }
        }, 1L);
    }

    private void restoreGameMode(Player player, GameMode originalGameMode) {
        if (player != null && player.isOnline()) {
            if (originalGameMode == GameMode.CREATIVE || originalGameMode == GameMode.SPECTATOR) {
                player.setGameMode(originalGameMode);
            } else {
                player.setGameMode(GameMode.SURVIVAL);
            }
        }
    }

    private void scheduleSwapCleanup(String oldWorldBaseName) {
        int delay = configManager.getTeleportDelay() + 80;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            worldManager.deleteWorldSet(oldWorldBaseName);
            this.isSwapping = false;
            getLogger().info("World swap complete. New active world: " + activeWorldName);
        }, delay);
    }

    /**
     * Handles seamless world swap by teleporting all players.
     */
    private boolean handleSeamlessSwap() {
        World newWorld = Bukkit.getWorld(activeWorldName);
        if (newWorld == null) {
            getLogger().severe("Active world '" + activeWorldName + "' not found! Cannot swap.");
            return false;
        }

        Location spawnLocation = newWorld.getSpawnLocation();
        ConfigManager.Messages messages = configManager.getMessages();

        for (Player player : Bukkit.getOnlinePlayers().toArray(Player[]::new)) {
            try {
                if (!player.teleport(spawnLocation)) {
                    getLogger().warning("Could not teleport " + player.getName()
                            + " to the new active world; disconnecting them safely.");
                    player.kickPlayer(configManager.getMessages().kickReason);
                    continue;
                }
                player.sendTitle(messages.titleMain, messages.titleSubtitle, 10, 70, 20);
            } catch (RuntimeException exception) {
                getLogger().warning("Final world teleport failed for " + player.getName()
                        + ": " + exception.getMessage());
                player.kickPlayer(configManager.getMessages().kickReason);
            }
        }

        getLogger().info("Teleported " + Bukkit.getOnlinePlayers().size() + " players to " + activeWorldName);
        return true;
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

        if (!activeWorldReady) {
            return;
        }

        startTime = System.currentTimeMillis() - pausedTime;
        pausedTime = 0L;
        timerHasStarted = true;
        isTimerRunning = true;
        persistTimerState();

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
        pausedTime = getElapsedMillis();
        isTimerRunning = false;
        persistTimerState();
    }

    /**
     * Stops the timer and announces the final time.
     *
     * @return The formatted final time
     */
    public String stopTimerAndAnnounce() {
        if (!timerHasStarted) {
            return "";
        }

        if (timerTask != null) {
            timerTask.cancel();
        }

        long finalMillis = getElapsedMillis();
        String formattedTime = formatTime(finalMillis);
        String footer = ChatColor.GREEN + "Final Time: " + formattedTime;

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setPlayerListFooter(footer);
        }

        isTimerRunning = false;
        timerHasStarted = false;
        startTime = 0L;
        pausedTime = 0L;
        persistTimerState();
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
        timerHasStarted = false;
        startTime = 0L;
        pausedTime = 0L;
        persistTimerState();
    }

    /**
     * Gets the current timer value, whether running or paused.
     */
    public long getElapsedMillis() {
        if (!timerHasStarted) {
            return 0L;
        }
        if (!isTimerRunning) {
            return Math.max(0L, pausedTime);
        }
        return Math.max(0L, System.currentTimeMillis() - startTime);
    }

    /**
     * Checks whether the timer scheduler is currently active.
     *
     * @return true while the timer is running
     */
    public boolean isTimerRunning() {
        return isTimerRunning;
    }

    /**
     * Persists the current timer run without changing whether it is active.
     */
    private void persistTimerState() {
        if (configManager != null) {
            configManager.saveTimerState(timerHasStarted, getElapsedMillis());
        }
    }

    /**
     * Starts a new run generation and resets every online player's state.
     * Players who are offline receive the same cleanup on their next join by
     * comparing the generation stored in their persistent data container.
     */
    private void beginNewRunAndCleanPlayers() {
        resetGeneration++;
        configManager.saveResetGeneration(resetGeneration);

        for (Player player : Bukkit.getOnlinePlayers()) {
            clearPlayerState(player);
            markPlayerResetGeneration(player);
        }
    }

    /**
     * Applies reset cleanup to a player if they have not seen the current
     * world-reset generation yet.
     *
     * @param player player to clean
     */
    public void cleanupPlayerForCurrentReset(Player player) {
        if (player == null || resetGenerationKey == null) {
            return;
        }

        Long lastSeen = player.getPersistentDataContainer().get(resetGenerationKey, PersistentDataType.LONG);
        if (lastSeen == null) {
            markPlayerResetGeneration(player);
            return;
        }

        // A changed generation alone is not enough to authorize cleanup. The
        // explicit persisted flag is armed only by an actual world reset and
        // prevents plugin updates, reloads, or malformed legacy state from
        // deleting a player's inventory.
        if (configManager.isResetCleanupPending() && lastSeen != resetGeneration) {
            clearPlayerState(player);
            markPlayerResetGeneration(player);
        }
    }

    private void markPlayerResetGeneration(Player player) {
        player.getPersistentDataContainer().set(resetGenerationKey,
                PersistentDataType.LONG, resetGeneration);
    }

    private void clearPlayerState(Player player) {
        if (configManager.isPreserveInventoryOnSwap()) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(new ItemStack[4]);
        inventory.setItemInOffHand(new ItemStack(Material.AIR));
        player.getEnderChest().clear();

        player.setTotalExperience(0);
        player.setLevel(0);
        player.setExp(0.0F);
        player.getActivePotionEffects().forEach(effect ->
                player.removePotionEffect(effect.getType()));
        player.setFireTicks(0);
        player.setFreezeTicks(0);
        player.setFallDistance(0.0F);
        player.setNoDamageTicks(0);
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
        for (String mockBukkitClass : new String[]{
                "org.mockbukkit.mockbukkit.MockBukkit",
                "be.seeseemelk.mockbukkit.MockBukkit"
        }) {
            try {
                Class.forName(mockBukkitClass);
                return true;
            } catch (ClassNotFoundException ignored) {
                // Try the other package name for older MockBukkit releases.
            }
        }
        return false;
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

    /**
     * Exposes the environment check to WorldManager without making it part of
     * the public plugin API.
     */
    boolean isTestEnvironmentForWorldManager() {
        return isTestEnvironment();
    }
}
