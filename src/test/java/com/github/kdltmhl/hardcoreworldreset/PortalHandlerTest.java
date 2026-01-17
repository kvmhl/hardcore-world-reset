package com.github.kdltmhl.hardcoreworldreset;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.bukkit.Location;
import org.bukkit.PortalType;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for the PortalHandler class.
 * Tests portal destination calculations and coordinate scaling.
 */
@DisplayName("PortalHandler Tests")
class PortalHandlerTest {

    private ServerMock server;
    private HardcoreWorldReset plugin;
    private WorldManager worldManager;
    private PortalHandler portalHandler;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(HardcoreWorldReset.class);
        worldManager = new WorldManager(plugin);
        portalHandler = new PortalHandler(plugin, worldManager);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Nested
    @DisplayName("Coordinate Scaling Tests")
    class CoordinateScalingTests {

        @Test
        @DisplayName("Should scale overworld to nether coordinates (divide by 8)")
        void shouldScaleOverworldToNether() {
            // Given
            WorldMock nether = (WorldMock) server.createWorld(new org.bukkit.WorldCreator("nether").environment(World.Environment.NETHER));
            Location overworldLocation = new Location(null, 800, 64, 800);

            // When
            Location netherLocation = portalHandler.scaleCoordinates(overworldLocation, nether, 1.0 / 8.0);

            // Then
            assertThat(netherLocation.getX()).isCloseTo(100, within(0.01));
            assertThat(netherLocation.getZ()).isCloseTo(100, within(0.01));
            assertThat(netherLocation.getWorld()).isEqualTo(nether);
        }

        @Test
        @DisplayName("Should scale nether to overworld coordinates (multiply by 8)")
        void shouldScaleNetherToOverworld() {
            // Given
            WorldMock overworld = (WorldMock) server.createWorld(new org.bukkit.WorldCreator("overworld").environment(World.Environment.NORMAL));
            Location netherLocation = new Location(null, 100, 64, 100);

            // When
            Location overworldLocation = portalHandler.scaleCoordinates(netherLocation, overworld, 8.0);

            // Then
            assertThat(overworldLocation.getX()).isCloseTo(800, within(0.01));
            assertThat(overworldLocation.getZ()).isCloseTo(800, within(0.01));
            assertThat(overworldLocation.getWorld()).isEqualTo(overworld);
        }

        @Test
        @DisplayName("Should preserve Y coordinate for normal scaling")
        void shouldPreserveYCoordinate() {
            // Given
            WorldMock overworld = (WorldMock) server.createWorld(new org.bukkit.WorldCreator("overworld").environment(World.Environment.NORMAL));
            Location location = new Location(null, 100, 75, 100);

            // When
            Location scaled = portalHandler.scaleCoordinates(location, overworld, 8.0);

            // Then
            assertThat(scaled.getY()).isEqualTo(75);
        }

        @Test
        @DisplayName("Should clamp Y coordinate for nether (max 126)")
        void shouldClampYForNether() {
            // Given
            WorldMock nether = (WorldMock) server.createWorld(new org.bukkit.WorldCreator("nether").environment(World.Environment.NETHER));
            Location highLocation = new Location(null, 100, 200, 100);

            // When
            Location netherLocation = portalHandler.scaleCoordinates(highLocation, nether, 1.0 / 8.0);

            // Then
            assertThat(netherLocation.getY()).isLessThanOrEqualTo(126);
        }

        @Test
        @DisplayName("Should clamp Y coordinate for nether (min 4)")
        void shouldClampMinYForNether() {
            // Given
            WorldMock nether = (WorldMock) server.createWorld(new org.bukkit.WorldCreator("nether").environment(World.Environment.NETHER));
            Location lowLocation = new Location(null, 100, 1, 100);

            // When
            Location netherLocation = portalHandler.scaleCoordinates(lowLocation, nether, 1.0 / 8.0);

            // Then
            assertThat(netherLocation.getY()).isGreaterThanOrEqualTo(4);
        }

        @Test
        @DisplayName("Should preserve yaw and pitch")
        void shouldPreserveYawAndPitch() {
            // Given
            WorldMock world = (WorldMock) server.createWorld(new org.bukkit.WorldCreator("world").environment(World.Environment.NORMAL));
            Location location = new Location(null, 100, 64, 100, 45.0f, -30.0f);

            // When
            Location scaled = portalHandler.scaleCoordinates(location, world, 1.0);

            // Then
            assertThat(scaled.getYaw()).isEqualTo(45.0f);
            assertThat(scaled.getPitch()).isEqualTo(-30.0f);
        }

        @Test
        @DisplayName("Should handle negative coordinates correctly")
        void shouldHandleNegativeCoordinates() {
            // Given
            WorldMock nether = (WorldMock) server.createWorld(new org.bukkit.WorldCreator("nether").environment(World.Environment.NETHER));
            Location location = new Location(null, -800, 64, -400);

            // When
            Location scaled = portalHandler.scaleCoordinates(location, nether, 1.0 / 8.0);

            // Then
            assertThat(scaled.getX()).isCloseTo(-100, within(0.01));
            assertThat(scaled.getZ()).isCloseTo(-50, within(0.01));
        }
    }

    @Nested
    @DisplayName("Nether Portal Destination Tests")
    class NetherPortalTests {

        @Test
        @DisplayName("Should calculate nether destination from overworld")
        void shouldCalculateNetherDestinationFromOverworld() {
            // Given
            worldManager.createWorldSet("portal_test");
            World overworld = worldManager.getOverworldForSet("portal_test");
            Location fromLocation = new Location(overworld, 160, 64, 80);

            // When
            Location destination = portalHandler.calculateDestination(fromLocation, overworld, PortalType.NETHER);

            // Then
            assertThat(destination).isNotNull();
            assertThat(destination.getWorld().getEnvironment()).isEqualTo(World.Environment.NETHER);
            assertThat(destination.getX()).isCloseTo(20, within(0.01));
            assertThat(destination.getZ()).isCloseTo(10, within(0.01));
        }

        @Test
        @DisplayName("Should calculate overworld destination from nether")
        void shouldCalculateOverworldDestinationFromNether() {
            // Given
            worldManager.createWorldSet("portal_test2");
            World nether = worldManager.getNetherForSet("portal_test2");
            Location fromLocation = new Location(nether, 20, 64, 10);

            // When
            Location destination = portalHandler.calculateDestination(fromLocation, nether, PortalType.NETHER);

            // Then
            assertThat(destination).isNotNull();
            assertThat(destination.getWorld().getEnvironment()).isEqualTo(World.Environment.NORMAL);
            assertThat(destination.getX()).isCloseTo(160, within(0.01));
            assertThat(destination.getZ()).isCloseTo(80, within(0.01));
        }

        @Test
        @DisplayName("Should return null when nether world not found")
        void shouldReturnNullWhenNetherNotFound() {
            // Given
            WorldMock overworld = server.addSimpleWorld("lonely_world");
            Location fromLocation = new Location(overworld, 100, 64, 100);

            // When
            Location destination = portalHandler.calculateDestination(fromLocation, overworld, PortalType.NETHER);

            // Then
            assertThat(destination).isNull();
        }
    }

    @Nested
    @DisplayName("End Portal Destination Tests")
    class EndPortalTests {

        @Test
        @DisplayName("Should calculate end destination from overworld")
        void shouldCalculateEndDestinationFromOverworld() {
            // Given
            worldManager.createWorldSet("end_test");
            World overworld = worldManager.getOverworldForSet("end_test");
            Location fromLocation = new Location(overworld, 0, 64, 0);

            // When
            Location destination = portalHandler.calculateDestination(fromLocation, overworld, PortalType.ENDER);

            // Then
            assertThat(destination).isNotNull();
            assertThat(destination.getWorld().getEnvironment()).isEqualTo(World.Environment.THE_END);
            // End portal always spawns at the obsidian platform (100, 50, 0)
            assertThat(destination.getX()).isCloseTo(100.5, within(0.1));
            assertThat(destination.getZ()).isCloseTo(0.5, within(0.1));
        }

        @Test
        @DisplayName("Should calculate overworld destination from end")
        void shouldCalculateOverworldDestinationFromEnd() {
            // Given
            worldManager.createWorldSet("end_test2");
            World end = worldManager.getEndForSet("end_test2");
            World overworld = worldManager.getOverworldForSet("end_test2");
            Location fromLocation = new Location(end, 100, 50, 0);

            // When
            Location destination = portalHandler.calculateDestination(fromLocation, end, PortalType.ENDER);

            // Then
            assertThat(destination).isNotNull();
            assertThat(destination.getWorld()).isEqualTo(overworld);
            // Should return to spawn location
            assertThat(destination).isEqualTo(overworld.getSpawnLocation());
        }
    }

    @Nested
    @DisplayName("End Spawn Location Tests")
    class EndSpawnTests {

        @Test
        @DisplayName("Should get end spawn at obsidian platform location")
        void shouldGetEndSpawnAtObsidianPlatform() {
            // Given
            WorldMock end = (WorldMock) server.createWorld(new org.bukkit.WorldCreator("end").environment(World.Environment.THE_END));

            // When
            Location spawn = portalHandler.getEndSpawnLocation(end);

            // Then
            assertThat(spawn.getWorld()).isEqualTo(end);
            assertThat(spawn.getX()).isCloseTo(100.5, within(0.1));
            assertThat(spawn.getZ()).isCloseTo(0.5, within(0.1));
        }
    }

    @Nested
    @DisplayName("Destination Environment Tests")
    class DestinationEnvironmentTests {

        @Test
        @DisplayName("Should return NETHER for nether portal from overworld")
        void shouldReturnNetherForPortalFromOverworld() {
            // When
            World.Environment dest = portalHandler.getDestinationEnvironment(
                    World.Environment.NORMAL, PortalType.NETHER);

            // Then
            assertThat(dest).isEqualTo(World.Environment.NETHER);
        }

        @Test
        @DisplayName("Should return NORMAL for nether portal from nether")
        void shouldReturnNormalForPortalFromNether() {
            // When
            World.Environment dest = portalHandler.getDestinationEnvironment(
                    World.Environment.NETHER, PortalType.NETHER);

            // Then
            assertThat(dest).isEqualTo(World.Environment.NORMAL);
        }

        @Test
        @DisplayName("Should return THE_END for end portal from overworld")
        void shouldReturnEndForEndPortalFromOverworld() {
            // When
            World.Environment dest = portalHandler.getDestinationEnvironment(
                    World.Environment.NORMAL, PortalType.ENDER);

            // Then
            assertThat(dest).isEqualTo(World.Environment.THE_END);
        }

        @Test
        @DisplayName("Should return NORMAL for end portal from the end")
        void shouldReturnNormalForEndPortalFromEnd() {
            // When
            World.Environment dest = portalHandler.getDestinationEnvironment(
                    World.Environment.THE_END, PortalType.ENDER);

            // Then
            assertThat(dest).isEqualTo(World.Environment.NORMAL);
        }

        @Test
        @DisplayName("Should return null for invalid portal/environment combinations")
        void shouldReturnNullForInvalidCombinations() {
            // When - nether portal from the end makes no sense
            World.Environment dest = portalHandler.getDestinationEnvironment(
                    World.Environment.THE_END, PortalType.NETHER);

            // Then
            assertThat(dest).isNull();
        }
    }

    @Nested
    @DisplayName("Managed World Set Detection Tests")
    class ManagedWorldSetTests {

        @Test
        @DisplayName("Should detect managed world set by prefix")
        void shouldDetectManagedWorldSetByPrefix() {
            // When/Then
            assertThat(portalHandler.isManagedWorldSet("hardcore_1")).isTrue();
            assertThat(portalHandler.isManagedWorldSet("hardcore_1_nether")).isTrue();
            assertThat(portalHandler.isManagedWorldSet("hardcore_1_the_end")).isTrue();
        }

        @Test
        @DisplayName("Should not detect non-managed worlds")
        void shouldNotDetectNonManagedWorlds() {
            // When/Then
            assertThat(portalHandler.isManagedWorldSet("world")).isFalse();
            assertThat(portalHandler.isManagedWorldSet("survival_world")).isFalse();
        }
    }
}


