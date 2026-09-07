package com.github.kdltmhl.hardcoreworldreset;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.Material;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Random;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Manages world creation, deletion, and configuration for the hardcore world reset plugin.
 * This class replaces the functionality previously provided by Multiverse-Core.
 */
public class WorldManager {

    private static final int WAITING_ROOM_FLOOR_Y = 64;
    private static final int WAITING_ROOM_CEILING_Y = 68;

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
     * Creates or loads the persistent void world used while a seamless swap
     * prepares the next complete world set. The world is deliberately not a
     * numbered run world and is therefore never removed by world cleanup.
     *
     * @return the waiting world, or null when it could not be created
     */
    public World createWaitingWorld() {
        if (!plugin.getConfigManager().isWaitingRoomEnabled()) {
            return null;
        }

        String waitingWorldName = plugin.getConfigManager().getWaitingWorldName();
        World world = Bukkit.getWorld(waitingWorldName);
        if (world == null && !plugin.isTestEnvironmentForWorldManager()) {
            try {
                WorldCreator creator = new WorldCreator(waitingWorldName);
                creator.environment(World.Environment.NORMAL);
                creator.generator(new VoidChunkGenerator());
                creator.generateStructures(false);
                creator.hardcore(false);
                world = creator.createWorld();
            } catch (Exception exception) {
                logger.severe("Failed to create waiting world " + waitingWorldName
                        + ": " + exception.getMessage());
                return null;
            }
        }

        if (world == null) {
            return null;
        }

        try {
            managedWorlds.add(waitingWorldName);
            configureWaitingWorld(world);
            buildWaitingRoom(world);
            return world;
        } catch (RuntimeException exception) {
            logger.severe("Failed to configure waiting world " + waitingWorldName
                    + ": " + exception.getMessage());
            return null;
        }
    }

    /**
     * Teleports every currently online player into the waiting room.
     * This is called before any new run chunks are generated.
     */
    public boolean teleportPlayersToWaitingWorld() {
        World waitingWorld = createWaitingWorld();
        if (waitingWorld == null) {
            logger.warning("Waiting world is unavailable; the caller must use a safe fallback before generation.");
            return false;
        }

        Location waitingLocation = waitingWorld.getSpawnLocation();
        boolean allPlayersTeleported = true;
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                if (!player.teleport(waitingLocation)) {
                    allPlayersTeleported = false;
                    logger.warning("Could not teleport " + player.getName() + " to the waiting world.");
                } else {
                    player.sendMessage(plugin.getConfigManager().getMessages().worldResetting);
                }
            } catch (RuntimeException exception) {
                allPlayersTeleported = false;
                logger.warning("Waiting-world teleport failed for " + player.getName()
                        + ": " + exception.getMessage());
            }
        }
        logger.info("Teleported " + Bukkit.getOnlinePlayers().size()
                + " players to the waiting world while the next run is prepared.");
        return allPlayersTeleported;
    }

    /**
     * Gets the currently loaded configured waiting world.
     *
     * @return the waiting world, or null if disabled/unavailable
     */
    public World getWaitingWorld() {
        if (!plugin.getConfigManager().isWaitingRoomEnabled()) {
            return null;
        }
        return Bukkit.getWorld(plugin.getConfigManager().getWaitingWorldName());
    }

    /**
     * Checks whether a world is the dedicated swap waiting world.
     */
    public boolean isWaitingWorld(World world) {
        return world != null && plugin.getConfigManager().getWaitingWorldName().equals(world.getName());
    }

    private void configureWaitingWorld(World world) {
        world.setDifficulty(Difficulty.PEACEFUL);
        world.setHardcore(false);
        world.setPVP(false);
        world.setSpawnFlags(false, false);
        world.setKeepSpawnInMemory(true);
        world.setTime(6000L);
        world.setStorm(false);
        world.setThundering(false);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setSpawnLocation(0, WAITING_ROOM_FLOOR_Y + 1, 0);
    }

    private void buildWaitingRoom(World world) {
        int radius = plugin.getConfigManager().getWaitingRoomRadius();
        int minimum = -radius;
        int maximum = radius;
        for (int x = minimum; x <= maximum; x++) {
            for (int z = minimum; z <= maximum; z++) {
                world.getBlockAt(x, WAITING_ROOM_FLOOR_Y, z).setType(Material.GLASS, false);
                world.getBlockAt(x, WAITING_ROOM_CEILING_Y, z).setType(Material.GLASS, false);
            }
        }

        for (int y = WAITING_ROOM_FLOOR_Y + 1; y < WAITING_ROOM_CEILING_Y; y++) {
            for (int coordinate = minimum; coordinate <= maximum; coordinate++) {
                world.getBlockAt(minimum, y, coordinate).setType(Material.GLASS, false);
                world.getBlockAt(maximum, y, coordinate).setType(Material.GLASS, false);
                world.getBlockAt(coordinate, y, minimum).setType(Material.GLASS, false);
                world.getBlockAt(coordinate, y, maximum).setType(Material.GLASS, false);
            }
        }
    }

    /** Empty chunk generator for the dedicated waiting world. */
    private static final class VoidChunkGenerator extends ChunkGenerator {
        @Override
        public ChunkData generateChunkData(World world, Random random, int chunkX, int chunkZ,
                BiomeGrid biome) {
            return createChunkData(world);
        }
    }

    /**
     * Pre-generates a square of chunks around the spawn in every dimension.
     * Paper performs each chunk request asynchronously. Requests are chained
     * one at a time instead of flooding the server with a whole rectangle;
     * this keeps the preparation work from producing a large TPS spike. The
     * completion callback is always returned to the server scheduler.
     *
     * @param baseName world-set base name
     * @param onReady  callback after all requested chunks are generated
     */
    public void prepareWorldSet(String baseName, Runnable onReady) {
        if (!isCompleteWorldSetLoaded(baseName)) {
            logger.warning("Cannot prepare incomplete world set: " + baseName
                    + ". Retrying after the missing dimension(s) are available.");
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTaskLater(plugin,
                        () -> prepareWorldSet(baseName, onReady), 20L);
            }
            return;
        }

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

        List<ChunkRequest> requests = createChunkRequests(baseName, distance);
        AtomicInteger nextRequest = new AtomicInteger(0);
        AtomicBoolean completed = new AtomicBoolean(false);
        Runnable[] pump = new Runnable[1];
        pump[0] = () -> {
            if (completed.get() || !plugin.isEnabled()) {
                return;
            }

            int requestIndex = nextRequest.getAndIncrement();
            if (requestIndex >= requests.size()) {
                completePreparation(baseName, completed, onReady, distance);
                return;
            }

            ChunkRequest request = requests.get(requestIndex);
            try {
                request.world.getChunkAtAsync(request.chunkX, request.chunkZ, true, false,
                        ignored -> Bukkit.getScheduler().runTask(plugin, pump[0]));
            } catch (RuntimeException exception) {
                logger.warning("Could not pre-generate " + request.world.getName() + " chunk "
                        + request.chunkX + "," + request.chunkZ + ": " + exception.getMessage());
                Bukkit.getScheduler().runTask(plugin, pump[0]);
            }
        };

        if (requests.isEmpty()) {
            completePreparation(baseName, completed, onReady, distance);
        } else {
            Bukkit.getScheduler().runTask(plugin, pump[0]);
        }
    }

    private List<ChunkRequest> createChunkRequests(String baseName, int distance) {
        World[] worlds = {
                Bukkit.getWorld(baseName),
                Bukkit.getWorld(baseName + "_nether"),
                Bukkit.getWorld(baseName + "_the_end")
        };
        List<ChunkRequest> requests = new ArrayList<>();

        if (Arrays.stream(worlds).anyMatch(Objects::isNull)) {
            logger.warning("Cannot pre-generate incomplete world set: " + baseName);
        }

        for (World world : worlds) {
            if (world == null) {
                continue;
            }

            Location spawn = world.getSpawnLocation();
            int centerChunkX = spawn.getBlockX() >> 4;
            int centerChunkZ = spawn.getBlockZ() >> 4;
            for (int offsetX = -distance; offsetX <= distance; offsetX++) {
                for (int offsetZ = -distance; offsetZ <= distance; offsetZ++) {
                    requests.add(new ChunkRequest(world, centerChunkX + offsetX,
                            centerChunkZ + offsetZ, Math.max(Math.abs(offsetX), Math.abs(offsetZ))));
                }
            }
        }

        requests.sort(Comparator.comparingInt(ChunkRequest::distanceFromSpawn));
        return requests;
    }

    private void completePreparation(String baseName, AtomicBoolean completed,
            Runnable onReady, int distance) {
        if (!completed.compareAndSet(false, true)) {
            return;
        }

        preparedWorldSets.add(baseName);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (plugin.isEnabled()) {
                logger.info("Pre-generated " + baseName
                        + " within " + distance + " chunks of each spawn.");
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

    /**
     * Checks that all three dimensions of a world set are currently loaded.
     */
    public boolean isCompleteWorldSetLoaded(String baseName) {
        return Bukkit.getWorld(baseName) != null
                && Bukkit.getWorld(baseName + "_nether") != null
                && Bukkit.getWorld(baseName + "_the_end") != null;
    }

    private record ChunkRequest(World world, int chunkX, int chunkZ, int distanceFromSpawn) {
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
