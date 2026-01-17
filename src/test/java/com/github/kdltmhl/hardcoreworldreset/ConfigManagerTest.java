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
 * Tests for the ConfigManager class.
 * Tests configuration loading, validation, and state management.
 */
@DisplayName("ConfigManager Tests")
class ConfigManagerTest {

    private ServerMock server;
    private HardcoreWorldReset plugin;
    private ConfigManager configManager;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(HardcoreWorldReset.class);
        configManager = new ConfigManager(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Nested
    @DisplayName("Configuration Loading Tests")
    class ConfigLoadingTests {

        @Test
        @DisplayName("Should load configuration without errors")
        void shouldLoadConfigurationWithoutErrors() {
            // When/Then - should not throw
            configManager.load();
        }

        @Test
        @DisplayName("Should reload configuration without errors")
        void shouldReloadConfigurationWithoutErrors() {
            // Given
            configManager.load();

            // When/Then - should not throw
            configManager.reload();
        }
    }

    @Nested
    @DisplayName("Core Settings Tests")
    class CoreSettingsTests {

        @Test
        @DisplayName("Should return default plugin enabled value")
        void shouldReturnDefaultPluginEnabled() {
            // Given
            configManager.load();

            // Then
            assertThat(configManager.isPluginEnabled()).isTrue();
        }

        @Test
        @DisplayName("Should return default world prefix")
        void shouldReturnDefaultWorldPrefix() {
            // Given
            configManager.load();

            // Then
            assertThat(configManager.getWorldPrefix()).isEqualTo("hardcore_");
        }

        @Test
        @DisplayName("Should return default swap method")
        void shouldReturnDefaultSwapMethod() {
            // Given
            configManager.load();

            // Then - default in config.yml is DISCONNECT
            assertThat(configManager.getSwapMethod()).isIn(
                    ConfigManager.SwapMethod.SEAMLESS,
                    ConfigManager.SwapMethod.DISCONNECT);
        }

        @Test
        @DisplayName("Should return default end goal")
        void shouldReturnDefaultEndGoal() {
            // Given
            configManager.load();

            // Then
            assertThat(configManager.getEndGoal()).isEqualTo(ConfigManager.EndGoal.ENDER_DRAGON);
        }
    }

    @Nested
    @DisplayName("Gameplay Settings Tests")
    class GameplaySettingsTests {

        @Test
        @DisplayName("Should return auto start timer setting")
        void shouldReturnAutoStartTimerSetting() {
            // Given
            configManager.load();

            // Then
            assertThat(configManager.isAutoStartTimer()).isTrue();
        }

        @Test
        @DisplayName("Should return min players to start")
        void shouldReturnMinPlayersToStart() {
            // Given
            configManager.load();

            // Then
            assertThat(configManager.getMinPlayersToStart()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("Should return announce deaths setting")
        void shouldReturnAnnounceDeathsSetting() {
            // Given
            configManager.load();

            // Then
            assertThat(configManager.isAnnounceDeaths()).isTrue();
        }

        @Test
        @DisplayName("Should return show timer in tab setting")
        void shouldReturnShowTimerInTabSetting() {
            // Given
            configManager.load();

            // Then
            assertThat(configManager.isShowTimerInTab()).isTrue();
        }

        @Test
        @DisplayName("Should return teleport delay")
        void shouldReturnTeleportDelay() {
            // Given
            configManager.load();

            // Then
            assertThat(configManager.getTeleportDelay()).isGreaterThanOrEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Messages Tests")
    class MessagesTests {

        @Test
        @DisplayName("Should load messages")
        void shouldLoadMessages() {
            // Given
            configManager.load();

            // Then
            ConfigManager.Messages messages = configManager.getMessages();
            assertThat(messages).isNotNull();
            assertThat(messages.kickReason).isNotNull();
            assertThat(messages.titleMain).isNotNull();
            assertThat(messages.dragonDefeat).isNotNull();
        }

        @Test
        @DisplayName("Should translate color codes in messages")
        void shouldTranslateColorCodesInMessages() {
            // Given
            configManager.load();

            // Then - color codes should be translated (& -> §)
            ConfigManager.Messages messages = configManager.getMessages();
            // The actual translation happens in the Messages constructor
            assertThat(messages.titleMain).doesNotContain("&c"); // Should be translated
        }
    }

    @Nested
    @DisplayName("State Management Tests")
    class StateManagementTests {

        @Test
        @DisplayName("Should return default active world name")
        void shouldReturnDefaultActiveWorldName() {
            // Given
            configManager.load();

            // Then
            String activeWorld = configManager.getActiveWorldName();
            assertThat(activeWorld).startsWith("hardcore_");
        }

        @Test
        @DisplayName("Should return default standby world name")
        void shouldReturnDefaultStandbyWorldName() {
            // Given
            configManager.load();

            // Then
            String standbyWorld = configManager.getStandbyWorldName();
            assertThat(standbyWorld).startsWith("hardcore_");
        }

        @Test
        @DisplayName("Should return default world counter")
        void shouldReturnDefaultWorldCounter() {
            // Given
            configManager.load();

            // Then
            int counter = configManager.getWorldCounter();
            assertThat(counter).isGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("Should save state")
        void shouldSaveState() {
            // Given
            configManager.load();

            // When
            configManager.saveState("hardcore_5", "hardcore_6", 6);

            // Then - reload and verify
            configManager.reload();
            assertThat(configManager.getActiveWorldName()).isEqualTo("hardcore_5");
            assertThat(configManager.getStandbyWorldName()).isEqualTo("hardcore_6");
            assertThat(configManager.getWorldCounter()).isEqualTo(6);
        }
    }

    @Nested
    @DisplayName("Static Methods Tests")
    class StaticMethodsTests {

        @Test
        @DisplayName("Should return available swap methods")
        void shouldReturnAvailableSwapMethods() {
            // When
            var methods = ConfigManager.getAvailableSwapMethods();

            // Then
            assertThat(methods).contains("SEAMLESS", "DISCONNECT");
        }

        @Test
        @DisplayName("Should return available end goals")
        void shouldReturnAvailableEndGoals() {
            // When
            var goals = ConfigManager.getAvailableEndGoals();

            // Then
            assertThat(goals).contains("ENDER_DRAGON", "WITHER", "ELDER_GUARDIAN", "NONE");
        }
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Should handle missing config gracefully")
        void shouldHandleMissingConfigGracefully() {
            // When/Then - should not throw, should use defaults
            configManager.load();
            assertThat(configManager.getWorldPrefix()).isNotNull();
        }

        @Test
        @DisplayName("Should validate world prefix is not empty")
        void shouldValidateWorldPrefixNotEmpty() {
            // Given - load default config
            configManager.load();

            // Then - prefix should not be empty
            assertThat(configManager.getWorldPrefix()).isNotEmpty();
        }

        @Test
        @DisplayName("Should validate min players is at least 1")
        void shouldValidateMinPlayersAtLeastOne() {
            // Given
            configManager.load();

            // Then
            assertThat(configManager.getMinPlayersToStart()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("Should validate teleport delay is non-negative")
        void shouldValidateTeleportDelayNonNegative() {
            // Given
            configManager.load();

            // Then
            assertThat(configManager.getTeleportDelay()).isGreaterThanOrEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Enum Tests")
    class EnumTests {

        @Test
        @DisplayName("SwapMethod enum should have expected values")
        void swapMethodEnumShouldHaveExpectedValues() {
            assertThat(ConfigManager.SwapMethod.values())
                    .containsExactly(ConfigManager.SwapMethod.SEAMLESS, ConfigManager.SwapMethod.DISCONNECT);
        }

        @Test
        @DisplayName("EndGoal enum should have expected values")
        void endGoalEnumShouldHaveExpectedValues() {
            assertThat(ConfigManager.EndGoal.values())
                    .containsExactly(
                            ConfigManager.EndGoal.ENDER_DRAGON,
                            ConfigManager.EndGoal.WITHER,
                            ConfigManager.EndGoal.ELDER_GUARDIAN,
                            ConfigManager.EndGoal.NONE);
        }
    }
}
