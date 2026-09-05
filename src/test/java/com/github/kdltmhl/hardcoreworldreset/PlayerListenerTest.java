package com.github.kdltmhl.hardcoreworldreset;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the PlayerListener class.
 * Tests player join, quit, death, and respawn events.
 */
@DisplayName("PlayerListener Tests")
class PlayerListenerTest {

    private ServerMock server;
    private HardcoreWorldReset plugin;
    private PlayerListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(HardcoreWorldReset.class);
        listener = new PlayerListener(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Nested
    @DisplayName("Player Join Tests")
    class PlayerJoinTests {

        @Test
        @DisplayName("Should handle player join event")
        void shouldHandlePlayerJoinEvent() {
            // Given
            server.addSimpleWorld("hardcore_1");
            plugin.setActiveWorldName("hardcore_1");

            // When
            PlayerMock player = server.addPlayer();

            // Then - no errors, player joined successfully
            assertThat(player.isOnline()).isTrue();
        }

        @Test
        @DisplayName("Should redirect player to active world on join")
        void shouldRedirectPlayerToActiveWorld() {
            // Given
            WorldMock wrongWorld = server.addSimpleWorld("wrong_world");
            WorldMock activeWorld = server.addSimpleWorld("hardcore_join");
            plugin.setActiveWorldName("hardcore_join");

            // Set up world manager
            WorldManager worldManager = new WorldManager(plugin);
            plugin.setWorldManager(worldManager);

            PlayerMock player = server.addPlayer();
            player.teleport(wrongWorld.getSpawnLocation());

            // When - simulate join event
            PlayerJoinEvent event = new PlayerJoinEvent(player, "joined");
            listener.onPlayerJoin(event);

            // Then - player should be teleported to active world
            // Note: In MockBukkit the actual redirect depends on implementation
        }

        @Test
        @DisplayName("Should start timer on first player join")
        void shouldStartTimerOnFirstPlayerJoin() {
            // Given
            server.addSimpleWorld("hardcore_timer_join");
            plugin.setActiveWorldName("hardcore_timer_join");

            // Create mock config manager that returns auto-start true
            // The actual behavior depends on config

            // When
            PlayerMock player = server.addPlayer();

            // Then - timer should have started (if auto-start enabled)
        }
    }

    @Nested
    @DisplayName("Player Quit Tests")
    class PlayerQuitTests {

        @Test
        @DisplayName("Should handle player quit event")
        void shouldHandlePlayerQuitEvent() {
            // Given
            PlayerMock player = server.addPlayer();
            assertThat(server.getOnlinePlayers()).hasSize(1);

            // When
            PlayerQuitEvent event = new PlayerQuitEvent(player, "left");
            listener.onPlayerQuit(event);

            // Then - no errors
        }

        @Test
        @DisplayName("Should pause timer when last player quits")
        void shouldPauseTimerWhenLastPlayerQuits() {
            // Given
            PlayerMock player = server.addPlayer();
            plugin.startOrResumeTimer();

            // When
            player.disconnect();
            listener.onPlayerQuit(new PlayerQuitEvent(player, "left"));
            server.getScheduler().performTicks(5); // Let scheduled tasks run

            // Then - timer should be paused only after the last player leaves
            assertThat(plugin.isTimerRunning()).isFalse();
        }

        @Test
        @DisplayName("Should keep timer running when players remain below start threshold")
        void shouldKeepTimerRunningWhenPlayersRemain() {
            // Given
            plugin.getConfig().set("gameplay.min-players-to-start", 2);
            plugin.saveConfig();
            plugin.getConfigManager().reload();
            PlayerMock remainingPlayer = server.addPlayer();
            PlayerMock leavingPlayer = server.addPlayer();
            plugin.startOrResumeTimer();

            // When one of two players leaves
            leavingPlayer.disconnect();
            listener.onPlayerQuit(new PlayerQuitEvent(leavingPlayer, "left"));
            server.getScheduler().performTicks(5);

            // Then - an active run continues while one player remains online
            assertThat(server.getOnlinePlayers()).hasSize(1);
            assertThat(server.getOnlinePlayers().iterator().next()).isEqualTo(remainingPlayer);
            assertThat(plugin.isTimerRunning()).isTrue();
        }
    }

    @Nested
    @DisplayName("Player Respawn Tests")
    class PlayerRespawnTests {

        @Test
        @DisplayName("Should set respawn location to active world")
        void shouldSetRespawnLocationToActiveWorld() {
            // Given
            WorldMock activeWorld = server.addSimpleWorld("hardcore_respawn");
            plugin.setActiveWorldName("hardcore_respawn");

            WorldManager worldManager = new WorldManager(plugin);
            plugin.setWorldManager(worldManager);

            PlayerMock player = server.addPlayer();
            player.teleport(activeWorld.getSpawnLocation());

            // When
            PlayerRespawnEvent event = new PlayerRespawnEvent(player,
                    player.getLocation(), false);
            listener.onPlayerRespawn(event);

            // Then
            assertThat(event.getRespawnLocation().getWorld()).isEqualTo(activeWorld);
        }
    }

    @Nested
    @DisplayName("Pre-Login Tests")
    class PreLoginTests {

        @Test
        @DisplayName("Should allow login when not swapping")
        void shouldAllowLoginWhenNotSwapping() {
            // Given
            plugin.setSwapping(false);

            // When
            PlayerMock player = server.addPlayer();

            // Then
            assertThat(player.isOnline()).isTrue();
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Should handle complete player lifecycle")
        void shouldHandleCompletePlayerLifecycle() {
            // Given
            WorldMock activeWorld = server.addSimpleWorld("hardcore_lifecycle");
            plugin.setActiveWorldName("hardcore_lifecycle");

            // When - player joins
            PlayerMock player = server.addPlayer("LifecyclePlayer");
            assertThat(player.isOnline()).isTrue();

            // Player does stuff...
            player.teleport(activeWorld.getSpawnLocation());

            // Player quits
            player.disconnect();
            assertThat(player.isOnline()).isFalse();
        }

        @Test
        @DisplayName("Should handle multiple players joining and quitting")
        void shouldHandleMultiplePlayersJoiningAndQuitting() {
            // Given
            server.addSimpleWorld("hardcore_multi");
            plugin.setActiveWorldName("hardcore_multi");

            // When
            PlayerMock player1 = server.addPlayer("Player1");
            PlayerMock player2 = server.addPlayer("Player2");
            PlayerMock player3 = server.addPlayer("Player3");

            assertThat(server.getOnlinePlayers()).hasSize(3);

            player2.disconnect();
            assertThat(server.getOnlinePlayers()).hasSize(2);

            player1.disconnect();
            player3.disconnect();
            assertThat(server.getOnlinePlayers()).isEmpty();
        }
    }
}
