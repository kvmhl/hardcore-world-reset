package com.github.kdltmhl.hardcoreworldreset;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Manages world creation, deletion, and configuration for the hardcore world reset plugin.
 * This class replaces the functionality previously provided by Multiverse-Core.
 */
public class WorldManager {

    private final HardcoreWorldReset plugin;
    private final Logger logger;
    private final Set<String> managedWorlds;
    private final Set<String> preparedWorldSets;

    /**
     * Creates a new WorldManager instance.
     *
     * @param plugin The main plugin instance
     */
    public WorldManager(HardcoreWorldReset plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.managedWorlds = new HashSet<>();
        this.preparedWorldSets = ConcurrentHashMap.newKeySet();
    }

    /**
     * Creates a complete world set (overworld, nether, and end) with the given base name.
     *
     * @param baseName The base name for the world set (e.g., "hardcore_1")
     * @return true if all worlds were created successfully
     */
    public boolean createWorldSet(String baseName) {
        preparedWorldSets.remove(baseName);
        boolean success = true;

        // Create overworld
        World overworld = getOrCreateWorld(baseName, World.Environment.NORMAL);
        if (overworld != null) {
            configureWorld(overworld, true);
            managedWorlds.add(baseName);
        } else {
            success = false;
        }

        // Create nether
        World nether = getOrCreateWorld(baseName + "_nether", World.Environment.NETHER);
        if (nether != null) {
            configureWorld(nether, false);
            managedWorlds.add(baseName + "_nether");
        } else {
            success = false;
        }

        // Create the end
        World end = getOrCreateWorld(baseName + "_the_end", World.Environment.THE_END);
        if (end != null) {
            configureWorld(end, false);
            managedWorlds.add(baseName + "_the_end");
        } else {
            success = false;
        }

        if (success) {
            logger.info("Created world set: " + baseName + " (overworld, nether, the_end)");
        } else {
            logger.warning("Failed to create complete world set: " + baseName);
        }

        return success;
    }

    /**
     * Pre-generates a square of chunks around the spawn in every dimension.
     * Paper performs the chunk work asynchronously, so world generation does
     * not block the main thread during a seamless swap. The completion
     * callback is always returned to the server scheduler.
     *
     * @param baseName world-set base name
     * @param onReady  callback after all requested chunks are generated
     */
    public void prepareWorldSet(String baseName, Runnable onReady) {
        if (preparedWorldSets.contains(baseName)) {
            onReady.run();
            return;
        }

        int distance = plugin.getConfigManager().getWorldPregenDistance();
        if (distance <= 0 || plugin.isTestEnvironmentForWorldManager()) {
            preparedWorldSets.add(baseName);
            onReady.run();
            return;
        }

        World[] worlds = {
                Bukkit.getWorld(baseName),
                Bukkit.getWorld(baseName + "_nether"),
                Bukkit.getWorld(baseName + "_the_end")
        };
        List<World> availableWorlds = Arrays.stream(worlds)
                .filter(Objects::nonNull)
                .toList();
        AtomicInteger remainingWorlds = new AtomicInteger(availableWorlds.size());
        AtomicBoolean completed = new AtomicBoolean(false);

        if (availableWorlds.size() != worlds.length) {
            logger.warning("Cannot pre-generate incomplete world set: " + baseName);
        }

        for (World world : availableWorlds) {
            LocationBounds bounds = getSpawnBounds(world, distance);
            try {
                world.getChunksAtAsync(bounds.minChunkX, bounds.minChunkZ,
                        bounds.maxChunkX, bounds.maxChunkZ, true, () -> {
                            finishPreparation(baseName, remainingWorlds, completed, onReady);
                        });
            } catch (RuntimeException exception) {
                logger.warning("Could not pre-generate " + world.getName() + ": "
                        + exception.getMessage());
                finishPreparation(baseName, remainingWorlds, completed, onReady);
            }
        }

        if (remainingWorlds.get() == 0 && completed.compareAndSet(false, true)) {
            preparedWorldSets.add(baseName);
            onReady.run();
        }
    }

    private void finishPreparation(String baseName, AtomicInteger remainingWorlds,
            AtomicBoolean completed, Runnable onReady) {
        if (remainingWorlds.decrementAndGet() != 0
                || !completed.compareAndSet(false, true)) {
            return;
        }

        preparedWorldSets.add(baseName);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (plugin.isEnabled()) {
                onReady.run();
            }
        });
    }

    /**
     * Checks whether the requested area of a world set is ready for play.
     *
     * @param baseName world-set base name
     * @return true when pre-generation completed or is disabled
     */
    public boolean isWorldSetPrepared(String baseName) {
        return preparedWorldSets.contains(baseName);
    }

    private LocationBounds getSpawnBounds(World world, int distance) {
        Location spawn = world.getSpawnLocation();
        int centerChunkX = spawn.getBlockX() >> 4;
        int centerChunkZ = spawn.getBlockZ() >> 4;
        return new LocationBounds(centerChunkX - distance, centerChunkZ - distance,
                centerChunkX + distance, centerChunkZ + distance);
    }

    private record LocationBounds(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
    }

    /**
     * Gets an existing world or creates a new one with the specified environment.
     *
     * @param worldName   The name of the world
     * @param environment The environment type (NORMAL, NETHER, THE_END)
     * @return The world, or null if creation failed
     */
    public World getOrCreateWorld(String worldName, World.Environment environment) {
        // Check if world is already loaded
        World existingWorld = Bukkit.getWorld(worldName);
        if (existingWorld != null) {
            logger.info("World already loaded: " + worldName);
            return existingWorld;
        }

        // Check if world folder exists on disk
        File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
        if (worldFolder.exists()) {
            logger.info("Loading existing world from disk: " + worldName);
        } else {
            logger.info("Creating new world: " + worldName);
        }

        try {
            WorldCreator creator = new WorldCreator(worldName);
            creator.environment(environment);
            creator.hardcore(true);

            // Set generator settings based on environment
            if (environment == World.Environment.NORMAL) {
                creator.generateStructures(true);
            }

            World world = creator.createWorld();
            if (world != null) {
                logger.info("Successfully created/loaded world: " + worldName);
            }
            return world;
        } catch (Exception e) {
            logger.severe("Failed to create world " + worldName + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Configures a world with the appropriate settings for hardcore gameplay.
     *
     * @param world       The world to configure
     * @param isOverworld Whether this is the main overworld
     */
    public void configureWorld(World world, boolean isOverworld) {
        if (world == null) {
            return;
        }

        // Set difficulty to hard
        world.setDifficulty(Difficulty.HARD);

        // Set hardcore mode
        world.setHardcore(true);

        // Configure game rules
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, false);
        world.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, true);
        world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, true);
        world.setGameRule(GameRules.SHOW_DEATH_MESSAGES, true);

        // Overworld-specific settings
        if (isOverworld) {
            world.setGameRule(GameRules.ADVANCE_TIME, true);
            world.setGameRule(GameRules.ADVANCE_WEATHER, true);
            world.setGameRule(GameRules.RESPAWN_RADIUS, 10);
        }

        logger.fine("Configured world settings for: " + world.getName());
    }

    /**
     * Deletes an entire world set (overworld, nether, and end).
     *
     * @param baseName The base name of the world set
     * @return true if all worlds were deleted successfully
     */
    public boolean deleteWorldSet(String baseName) {
        boolean success = true;

        success &= deleteWorld(baseName);
        success &= deleteWorld(baseName + "_nether");
        success &= deleteWorld(baseName + "_the_end");

        if (success) {
            logger.info("Deleted world set: " + baseName);
        } else {
            logger.warning("Some worlds in set " + baseName + " could not be deleted");
        }

        return success;
    }

    /**
     * Safely deletes a single world by first evacuating players, unloading it, then deleting files.
     *
     * @param worldName The name of the world to delete
     * @return true if deletion was successful
     */
    public boolean deleteWorld(String worldName) {
        preparedWorldSets.remove(getBaseWorldName(worldName));
        World world = Bukkit.getWorld(worldName);

        // Evacuate any players from the world
        if (world != null) {
            World fallbackWorld = findFallbackWorld(worldName);
            if (fallbackWorld != null) {
                for (Player player : world.getPlayers()) {
                    player.teleport(fallbackWorld.getSpawnLocation());
                    logger.info("Evacuated player " + player.getName() + " from " + worldName);
                }
            }
        }

        // Unload the world
        if (!unloadWorld(worldName)) {
            logger.warning("Failed to unload world: " + worldName);
            return false;
        }

        // Delete the world folder
        File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
        if (worldFolder.exists()) {
            if (deleteDirectory(worldFolder)) {
                logger.info("Deleted world folder: " + worldName);
                managedWorlds.remove(worldName);
                return true;
            } else {
                logger.severe("Failed to delete world folder: " + worldName);
                return false;
            }
        }

        managedWorlds.remove(worldName);
        return true;
    }

    /**
     * Unloads a world from memory.
     *
     * @param worldName The name of the world to unload
     * @return true if unload was successful or world was not loaded
     */
    public boolean unloadWorld(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return true; // Already unloaded
        }

        // Can't unload if players are present
        if (!world.getPlayers().isEmpty()) {
            logger.warning("Cannot unload world " + worldName + " - players still present");
            return false;
        }

        return Bukkit.unloadWorld(world, false);
    }

    /**
     * Checks if a world with the given name is currently loaded.
     *
     * @param worldName The name of the world
     * @return true if the world is loaded
     */
    public boolean isWorldLoaded(String worldName) {
        return Bukkit.getWorld(worldName) != null;
    }

    /**
     * Checks if a world folder exists on disk.
     *
     * @param worldName The name of the world
     * @return true if the world folder exists
     */
    public boolean doesWorldExist(String worldName) {
        File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
        return worldFolder.exists() && worldFolder.isDirectory();
    }

    /**
     * Gets the base name of a world (without dimension suffix).
     *
     * @param worldName The full world name
     * @return The base name
     */
    public String getBaseWorldName(String worldName) {
        if (worldName.endsWith("_nether")) {
            return worldName.substring(0, worldName.length() - 7);
        } else if (worldName.endsWith("_the_end")) {
            return worldName.substring(0, worldName.length() - 8);
        }
        return worldName;
    }

    /**
     * Gets the overworld for a given world name in the same world set.
     *
     * @param worldName Any world name in the set
     * @return The overworld, or null if not found
     */
    public World getOverworldForSet(String worldName) {
        String baseName = getBaseWorldName(worldName);
        return Bukkit.getWorld(baseName);
    }

    /**
     * Gets the nether for a given world name in the same world set.
     *
     * @param worldName Any world name in the set
     * @return The nether world, or null if not found
     */
    public World getNetherForSet(String worldName) {
        String baseName = getBaseWorldName(worldName);
        return Bukkit.getWorld(baseName + "_nether");
    }

    /**
     * Gets the end for a given world name in the same world set.
     *
     * @param worldName Any world name in the set
     * @return The end world, or null if not found
     */
    public World getEndForSet(String worldName) {
        String baseName = getBaseWorldName(worldName);
        return Bukkit.getWorld(baseName + "_the_end");
    }

    /**
     * Finds a fallback world to teleport players to when their current world is being deleted.
     *
     * @param excludeWorldName The world to exclude from fallback options
     * @return A suitable fallback world, or null if none found
     */
    private World findFallbackWorld(String excludeWorldName) {
        String excludeBase = getBaseWorldName(excludeWorldName);

        // First try to find another managed world
        for (World world : Bukkit.getWorlds()) {
            String worldBase = getBaseWorldName(world.getName());
            if (!worldBase.equals(excludeBase) && world.getEnvironment() == World.Environment.NORMAL) {
                return world;
            }
        }

        // Fallback to default world
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
    }

    /**
     * Recursively deletes a directory and all its contents.
     *
     * @param directory The directory to delete
     * @return true if deletion was successful
     */
    private boolean deleteDirectory(File directory) {
        if (!directory.exists()) {
            return true;
        }

        try (Stream<Path> walk = Files.walk(directory.toPath())) {
            walk.sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
            return !directory.exists();
        } catch (IOException e) {
            logger.severe("Error deleting directory " + directory.getPath() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Gets the set of all managed world names.
     *
     * @return Set of managed world names
     */
    public Set<String> getManagedWorlds() {
        return new HashSet<>(managedWorlds);
    }

    /**
     * Checks if a world is managed by this plugin.
     *
     * @param worldName The world name to check
     * @return true if the world is managed
     */
    public boolean isManagedWorld(String worldName) {
        return managedWorlds.contains(worldName);
    }
}
