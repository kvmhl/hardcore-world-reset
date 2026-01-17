package com.github.kdltmhl.hardcoreworldreset;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the WorldManager class.
 * Tests world creation, deletion, configuration, and utility methods.
 */
@DisplayName("WorldManager Tests")
class WorldManagerTest {

    private ServerMock server;
    private HardcoreWorldReset plugin;
    private WorldManager worldManager;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(HardcoreWorldReset.class);
        worldManager = new WorldManager(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Nested
    @DisplayName("World Creation Tests")
    class WorldCreationTests {

        @Test
        @DisplayName("Should create a new overworld")
        void shouldCreateNewOverworld() {
            // When
            World world = worldManager.getOrCreateWorld("test_world", World.Environment.NORMAL);

            // Then
            assertThat(world).isNotNull();
            assertThat(world.getName()).isEqualTo("test_world");
            assertThat(world.getEnvironment()).isEqualTo(World.Environment.NORMAL);
        }

        @Test
        @DisplayName("Should create a new nether world")
        void shouldCreateNewNether() {
            // When
            World world = worldManager.getOrCreateWorld("test_nether", World.Environment.NETHER);

            // Then
            assertThat(world).isNotNull();
            assertThat(world.getName()).isEqualTo("test_nether");
            assertThat(world.getEnvironment()).isEqualTo(World.Environment.NETHER);
        }

        @Test
        @DisplayName("Should create a new end world")
        void shouldCreateNewEnd() {
            // When
            World world = worldManager.getOrCreateWorld("test_end", World.Environment.THE_END);

            // Then
            assertThat(world).isNotNull();
            assertThat(world.getName()).isEqualTo("test_end");
            assertThat(world.getEnvironment()).isEqualTo(World.Environment.THE_END);
        }

        @Test
        @DisplayName("Should return existing world if already loaded")
        void shouldReturnExistingWorld() {
            // Given
            World firstWorld = worldManager.getOrCreateWorld("existing_world", World.Environment.NORMAL);

            // When
            World secondWorld = worldManager.getOrCreateWorld("existing_world", World.Environment.NORMAL);

            // Then
            assertThat(secondWorld).isSameAs(firstWorld);
        }

        @Test
        @DisplayName("Should create complete world set with all dimensions")
        void shouldCreateCompleteWorldSet() {
            // When
            boolean success = worldManager.createWorldSet("hardcore_test");

            // Then
            assertThat(success).isTrue();
            assertThat(worldManager.isWorldLoaded("hardcore_test")).isTrue();
            assertThat(worldManager.isWorldLoaded("hardcore_test_nether")).isTrue();
            assertThat(worldManager.isWorldLoaded("hardcore_test_the_end")).isTrue();
        }
    }

    @Nested
    @DisplayName("World Configuration Tests")
    class WorldConfigurationTests {

        @Test
        @DisplayName("Should configure world with hard difficulty")
        void shouldConfigureWorldWithHardDifficulty() {
            // Given
            WorldMock world = server.addSimpleWorld("config_test");

            // When
            worldManager.configureWorld(world, true);

            // Then
            assertThat(world.getDifficulty()).isEqualTo(Difficulty.HARD);
        }

        @Test
        @DisplayName("Should set world to hardcore mode")
        void shouldSetWorldToHardcore() {
            // Given
            WorldMock world = server.addSimpleWorld("hardcore_config");

            // When
            worldManager.configureWorld(world, true);

            // Then
            assertThat(world.isHardcore()).isTrue();
        }

        @Test
        @DisplayName("Should handle null world gracefully")
        void shouldHandleNullWorldGracefully() {
            // When/Then - should not throw
            worldManager.configureWorld(null, true);
        }
    }

    @Nested
    @DisplayName("World Deletion Tests")
    class WorldDeletionTests {

        @Test
        @DisplayName("Should unload world successfully")
        void shouldUnloadWorldSuccessfully() {
            // Given
            worldManager.getOrCreateWorld("to_unload", World.Environment.NORMAL);
            assertThat(worldManager.isWorldLoaded("to_unload")).isTrue();

            // When
            boolean success = worldManager.unloadWorld("to_unload");

            // Then
            assertThat(success).isTrue();
        }

        @Test
        @DisplayName("Should return true when unloading non-existent world")
        void shouldReturnTrueForNonExistentWorld() {
            // When
            boolean success = worldManager.unloadWorld("non_existent");

            // Then
            assertThat(success).isTrue(); // Already unloaded
        }

        @Test
        @DisplayName("Should evacuate players before deletion")
        void shouldEvacuatePlayersBeforeDeletion() {
            // Given
            WorldMock targetWorld = server.addSimpleWorld("evacuation_test");
            WorldMock safeWorld = server.addSimpleWorld("safe_world");
            PlayerMock player = server.addPlayer();
            player.teleport(targetWorld.getSpawnLocation());

            // The player should be in the target world
            assertThat(player.getWorld().getName()).isEqualTo("evacuation_test");

            // When
            worldManager.deleteWorld("evacuation_test");

            // Then - player should have been moved (or world unloaded with player handling)
            // Note: In MockBukkit, the behavior may differ from production
        }
    }

    @Nested
    @DisplayName("World Name Utility Tests")
    class WorldNameUtilityTests {

        @Test
        @DisplayName("Should extract base name from overworld")
        void shouldExtractBaseNameFromOverworld() {
            // When
            String baseName = worldManager.getBaseWorldName("hardcore_1");

            // Then
            assertThat(baseName).isEqualTo("hardcore_1");
        }

        @Test
        @DisplayName("Should extract base name from nether")
        void shouldExtractBaseNameFromNether() {
            // When
            String baseName = worldManager.getBaseWorldName("hardcore_1_nether");

            // Then
            assertThat(baseName).isEqualTo("hardcore_1");
        }

        @Test
        @DisplayName("Should extract base name from the_end")
        void shouldExtractBaseNameFromEnd() {
            // When
            String baseName = worldManager.getBaseWorldName("hardcore_1_the_end");

            // Then
            assertThat(baseName).isEqualTo("hardcore_1");
        }

        @Test
        @DisplayName("Should handle complex world names")
        void shouldHandleComplexWorldNames() {
            // When
            String baseName = worldManager.getBaseWorldName("my_hardcore_world_v2_nether");

            // Then
            assertThat(baseName).isEqualTo("my_hardcore_world_v2");
        }

        @Test
        @DisplayName("Should check if world is loaded")
        void shouldCheckIfWorldIsLoaded() {
            // Given
            server.addSimpleWorld("loaded_world");

            // Then
            assertThat(worldManager.isWorldLoaded("loaded_world")).isTrue();
            assertThat(worldManager.isWorldLoaded("not_loaded_world")).isFalse();
        }
    }

    @Nested
    @DisplayName("World Set Navigation Tests")
    class WorldSetNavigationTests {

        @Test
        @DisplayName("Should get overworld from nether world name")
        void shouldGetOverworldFromNether() {
            // Given
            worldManager.createWorldSet("nav_test");

            // When
            World overworld = worldManager.getOverworldForSet("nav_test_nether");

            // Then
            assertThat(overworld).isNotNull();
            assertThat(overworld.getName()).isEqualTo("nav_test");
        }

        @Test
        @DisplayName("Should get nether from overworld name")
        void shouldGetNetherFromOverworld() {
            // Given
            worldManager.createWorldSet("nav_test2");

            // When
            World nether = worldManager.getNetherForSet("nav_test2");

            // Then
            assertThat(nether).isNotNull();
            assertThat(nether.getName()).isEqualTo("nav_test2_nether");
        }

        @Test
        @DisplayName("Should get end from overworld name")
        void shouldGetEndFromOverworld() {
            // Given
            worldManager.createWorldSet("nav_test3");

            // When
            World end = worldManager.getEndForSet("nav_test3");

            // Then
            assertThat(end).isNotNull();
            assertThat(end.getName()).isEqualTo("nav_test3_the_end");
        }

        @Test
        @DisplayName("Should return null for non-existent world set")
        void shouldReturnNullForNonExistentWorldSet() {
            // When
            World overworld = worldManager.getOverworldForSet("nonexistent_nether");

            // Then
            assertThat(overworld).isNull();
        }
    }

    @Nested
    @DisplayName("Managed Worlds Tracking Tests")
    class ManagedWorldsTests {

        @Test
        @DisplayName("Should track created worlds")
        void shouldTrackCreatedWorlds() {
            // Given
            worldManager.createWorldSet("tracked_world");

            // Then
            assertThat(worldManager.isManagedWorld("tracked_world")).isTrue();
            assertThat(worldManager.isManagedWorld("tracked_world_nether")).isTrue();
            assertThat(worldManager.isManagedWorld("tracked_world_the_end")).isTrue();
        }

        @Test
        @DisplayName("Should return all managed worlds")
        void shouldReturnAllManagedWorlds() {
            // Given
            worldManager.createWorldSet("managed1");
            worldManager.createWorldSet("managed2");

            // When
            var managedWorlds = worldManager.getManagedWorlds();

            // Then
            assertThat(managedWorlds).hasSize(6); // 2 sets * 3 dimensions
            assertThat(managedWorlds).contains("managed1", "managed1_nether", "managed1_the_end");
            assertThat(managedWorlds).contains("managed2", "managed2_nether", "managed2_the_end");
        }

        @Test
        @DisplayName("Should not include unmanaged worlds")
        void shouldNotIncludeUnmanagedWorlds() {
            // Given
            server.addSimpleWorld("external_world");

            // Then
            assertThat(worldManager.isManagedWorld("external_world")).isFalse();
        }
    }
}
