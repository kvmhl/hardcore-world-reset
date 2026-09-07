package com.github.kdltmhl.hardcoreworldreset;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.PortalType;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.logging.Logger;

/**
 * Handles portal teleportation between dimensions within the same world set.
 * This class replaces the functionality previously provided by
 * Multiverse-NetherPortals.
 */
public class PortalHandler implements Listener {

    /**
     * The coordinate scale factor between overworld and nether (8:1 ratio).
     */
    public static final double NETHER_SCALE = 8.0;

    /**
     * Default Y-level for end portal spawns.
     */
    private static final int END_SPAWN_Y = 50;

    private final HardcoreWorldReset plugin;
    private final WorldManager worldManager;
    private final Logger logger;

    /**
     * Creates a new PortalHandler.
     *
     * @param plugin       The main plugin instance
     * @param worldManager The world manager instance
     */
    public PortalHandler(HardcoreWorldReset plugin, WorldManager worldManager) {
        this.plugin = plugin;
        this.worldManager = worldManager;
        this.logger = plugin.getLogger();
    }

    /**
     * Handles player portal events for both nether and end portals.
     *
     * @param event The portal event
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Player player = event.getPlayer();
        World fromWorld = player.getWorld();
        String fromWorldName = fromWorld.getName();

        // The waiting room is a protected void world with no dimensions.
        // Never let vanilla portal handling or this plugin's dimension mapping
        // move a player out of it during a swap.
        if (worldManager.isWaitingWorld(fromWorld)) {
            event.setCancelled(true);
            return;
        }

        // Only handle portals in managed worlds
        String worldPrefix = plugin.getWorldPrefix();
        if (!fromWorldName.startsWith(worldPrefix)) {
            return;
        }

        PortalType portalType = event.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                ? PortalType.NETHER
                : PortalType.ENDER;

        Location destination = calculateDestination(player.getLocation(), fromWorld, portalType);

        if (destination != null) {
            event.setTo(destination);
            logger.fine("Handling " + portalType + " portal for " + player.getName()
                    + " from " + fromWorldName + " to " + destination.getWorld().getName());
        } else {
            event.setCancelled(true);
            logger.warning("Could not calculate portal destination for " + player.getName());
        }
    }

    /**
     * Calculates the destination location for a portal teleport.
     *
     * @param from       The origin location
     * @param fromWorld  The origin world
     * @param portalType The type of portal being used
     * @return The destination location, or null if destination world not found
     */
    public Location calculateDestination(Location from, World fromWorld, PortalType portalType) {
        String baseWorldName = worldManager.getBaseWorldName(fromWorld.getName());
        World.Environment fromEnv = fromWorld.getEnvironment();

        if (portalType == PortalType.NETHER) {
            return calculateNetherPortalDestination(from, fromWorld, baseWorldName, fromEnv);
        } else if (portalType == PortalType.ENDER) {
            return calculateEndPortalDestination(from, fromWorld, baseWorldName, fromEnv);
        }

        return null;
    }

    /**
     * Calculates destination for nether portal teleportation.
     *
     * @param from          The origin location
     * @param fromWorld     The origin world
     * @param baseWorldName The base world name
     * @param fromEnv       The origin environment
     * @return The destination location
     */
    private Location calculateNetherPortalDestination(Location from, World fromWorld,
            String baseWorldName, World.Environment fromEnv) {

        if (fromEnv == World.Environment.NORMAL) {
            // Overworld -> Nether
            World nether = worldManager.getNetherForSet(fromWorld.getName());
            if (nether == null) {
                logger.warning("Nether world not found for " + baseWorldName);
                return null;
            }
            return scaleCoordinates(from, nether, 1.0 / NETHER_SCALE);

        } else if (fromEnv == World.Environment.NETHER) {
            // Nether -> Overworld
            World overworld = worldManager.getOverworldForSet(fromWorld.getName());
            if (overworld == null) {
                logger.warning("Overworld not found for " + baseWorldName);
                return null;
            }
            return scaleCoordinates(from, overworld, NETHER_SCALE);
        }

        return null;
    }

    /**
     * Calculates destination for end portal teleportation.
     *
     * @param from          The origin location
     * @param fromWorld     The origin world
     * @param baseWorldName The base world name
     * @param fromEnv       The origin environment
     * @return The destination location
     */
    private Location calculateEndPortalDestination(Location from, World fromWorld,
            String baseWorldName, World.Environment fromEnv) {

        if (fromEnv == World.Environment.NORMAL) {
            // Overworld -> The End
            World end = worldManager.getEndForSet(fromWorld.getName());
            if (end == null) {
                logger.warning("End world not found for " + baseWorldName);
                return null;
            }
            // End portal spawns at the obsidian platform
            return getEndSpawnLocation(end);

        } else if (fromEnv == World.Environment.THE_END) {
            // The End -> Overworld (through end gateway or dragon defeat)
            World overworld = worldManager.getOverworldForSet(fromWorld.getName());
            if (overworld == null) {
                logger.warning("Overworld not found for " + baseWorldName);
                return null;
            }
            return worldManager.getSafeSpawnLocation(overworld);
        }

        return null;
    }

    /**
     * Scales coordinates between dimensions.
     *
     * @param from        The origin location
     * @param toWorld     The destination world
     * @param scaleFactor The scale factor to apply to X and Z coordinates
     * @return The scaled location in the destination world
     */
    public Location scaleCoordinates(Location from, World toWorld, double scaleFactor) {
        double x = from.getX() * scaleFactor;
        double z = from.getZ() * scaleFactor;
        double y = from.getY();

        // Clamp Y to valid range for nether
        if (toWorld.getEnvironment() == World.Environment.NETHER) {
            y = Math.min(y, 126);
            y = Math.max(y, 4);
        }

        return new Location(toWorld, x, y, z, from.getYaw(), from.getPitch());
    }

    /**
     * Gets the spawn location for The End (the obsidian platform).
     *
     * @param endWorld The End world
     * @return The spawn location on the obsidian platform
     */
    public Location getEndSpawnLocation(World endWorld) {
        // The obsidian platform is always at 100, 49, 0 in The End
        Location spawn = new Location(endWorld, 100.5, END_SPAWN_Y, 0.5);

        // Ensure the platform area exists
        ensureEndPlatform(endWorld, spawn);

        return spawn;
    }

    /**
     * Ensures the obsidian platform exists at the spawn location in The End.
     *
     * @param endWorld The End world
     * @param spawn    The spawn location
     */
    private void ensureEndPlatform(World endWorld, Location spawn) {
        int baseX = spawn.getBlockX();
        int baseY = spawn.getBlockY() - 1;
        int baseZ = spawn.getBlockZ();

        // Create a 5x5 obsidian platform
        for (int x = baseX - 2; x <= baseX + 2; x++) {
            for (int z = baseZ - 2; z <= baseZ + 2; z++) {
                endWorld.getBlockAt(x, baseY, z).setType(org.bukkit.Material.OBSIDIAN);
                // Clear air above the platform
                for (int y = baseY + 1; y <= baseY + 3; y++) {
                    endWorld.getBlockAt(x, y, z).setType(org.bukkit.Material.AIR);
                }
            }
        }
    }

    /**
     * Checks if a world name belongs to a managed world set.
     *
     * @param worldName The world name to check
     * @return true if the world is part of a managed set
     */
    public boolean isManagedWorldSet(String worldName) {
        return worldName.startsWith(plugin.getWorldPrefix());
    }

    /**
     * Gets the environment that a portal leads to from the given environment.
     *
     * @param fromEnv    The origin environment
     * @param portalType The portal type
     * @return The destination environment
     */
    public World.Environment getDestinationEnvironment(World.Environment fromEnv, PortalType portalType) {
        if (portalType == PortalType.NETHER) {
            if (fromEnv == World.Environment.NORMAL) {
                return World.Environment.NETHER;
            } else if (fromEnv == World.Environment.NETHER) {
                return World.Environment.NORMAL;
            }
        } else if (portalType == PortalType.ENDER) {
            if (fromEnv == World.Environment.NORMAL) {
                return World.Environment.THE_END;
            } else if (fromEnv == World.Environment.THE_END) {
                return World.Environment.NORMAL;
            }
        }
        return null;
    }
}
