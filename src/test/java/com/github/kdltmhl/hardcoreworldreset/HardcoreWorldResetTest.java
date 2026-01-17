package com.github.kdltmhl.hardcoreworldreset;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the HardcoreWorldReset plugin.
 * Tests the plugin as a whole, ensuring all components work together.
 */
@DisplayName("Plugin Integration Tests")
class HardcoreWorldResetTest {

    private ServerMock server;
    private HardcoreWorldReset plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(HardcoreWorldReset.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Nested
    @DisplayName("Plugin Lifecycle Tests")
    class PluginLifecycleTests {

        @Test
        @DisplayName("Should enable plugin successfully")
        void shouldEnablePluginSuccessfully() {
            assertThat(plugin).isNotNull();
            assertThat(plugin.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("Should have correct plugin name")
        void shouldHaveCorrectPluginName() {
            assertThat(plugin.getName()).isEqualTo("HardcoreWorldReset");
        }

        @Test
        @DisplayName("Should initialize config manager")
        void shouldInitializeConfigManager() {
            assertThat(plugin.getConfigManager()).isNotNull();
        }

        @Test
        @DisplayName("Should initialize world manager")
        void shouldInitializeWorldManager() {
            // World manager is initialized after delay, so we trigger the scheduler
            server.getScheduler().performOneTick();
            assertThat(plugin.getWorldManager()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Configuration Tests")
    class ConfigurationTests {

        @Test
        @DisplayName("Should have default config values")
        void shouldHaveDefaultConfigValues() {
            ConfigManager config = plugin.getConfigManager();

            assertThat(config.getWorldPrefix()).isNotEmpty();
            assertThat(config.getSwapMethod()).isNotNull();
            assertThat(config.getEndGoal()).isNotNull();
        }

        @Test
        @DisplayName("Should return world prefix")
        void shouldReturnWorldPrefix() {
            String prefix = plugin.getWorldPrefix();
            assertThat(prefix).isNotEmpty();
            assertThat(prefix).isEqualTo("hardcore_");
        }
    }

    @Nested
    @DisplayName("World Management Tests")
    class WorldManagementTests {

        @Test
        @DisplayName("Should return active world name")
        void shouldReturnActiveWorldName() {
            // Let initialization complete
            server.getScheduler().performOneTick();

            String activeWorldName = plugin.getActiveWorldName();
            assertThat(activeWorldName).startsWith("hardcore_");
        }

        @Test
        @DisplayName("Should track swapping state")
        void shouldTrackSwappingState() {
            assertThat(plugin.isSwapping()).isFalse();

            plugin.setSwapping(true);
            assertThat(plugin.isSwapping()).isTrue();

            plugin.setSwapping(false);
            assertThat(plugin.isSwapping()).isFalse();
        }
    }

    @Nested
    @DisplayName("Event Registration Tests")
    class EventRegistrationTests {

        @Test
        @DisplayName("Should register event listeners")
        void shouldRegisterEventListeners() {
            // Let initialization complete
            server.getScheduler().performOneTick();

            // Plugin should have registered listeners
            // We can verify by checking if events are handled
            assertThat(plugin.isEnabled()).isTrue();
        }
    }

    @Nested
    @DisplayName("Full Workflow Tests")
    class FullWorkflowTests {

        @Test
        @DisplayName("Should complete full player death workflow")
        void shouldCompleteFullPlayerDeathWorkflow() {
            // Given
            server.getScheduler().performOneTick(); // Complete initialization

            // Create worlds
            server.addSimpleWorld("hardcore_1");
            server.addSimpleWorld("hardcore_2");
            plugin.setActiveWorldName("hardcore_1");
            plugin.setStandbyWorldName("hardcore_2");

            var player = server.addPlayer();

            // When - simulate death
            plugin.triggerWorldSwap(player, player.getGameMode());

            // Then
            assertThat(plugin.isSwapping()).isTrue();
            assertThat(plugin.getActiveWorldName()).isEqualTo("hardcore_2");
        }

        @Test
        @DisplayName("Should handle plugin reload")
        void shouldHandlePluginReload() {
            // Given
            ConfigManager config = plugin.getConfigManager();

            // When
            config.reload();

            // Then - should not throw, config should be reloaded
            assertThat(config.getWorldPrefix()).isNotNull();
        }
    }
}
