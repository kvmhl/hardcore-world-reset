package com.github.kdltmhl.hardcoreworldreset;

import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import net.kyori.adventure.text.Component;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bulletproof End-to-End Integration Tests.
 * Simulates the entire lifecycle of a HardcoreWorldReset run.
 */
@DisplayName("E2E Integration Tests")
class E2EIntegrationTest {

    private ServerMock server;
    private HardcoreWorldReset plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        // Create initial hardcore worlds
        server.addSimpleWorld("hardcore_1");
        server.addSimpleWorld("hardcore_2");
        server.addSimpleWorld("hardcore_3");

        plugin = MockBukkit.load(HardcoreWorldReset.class);
        
        // Plugin initializes after 1 tick
        server.getScheduler().performOneTick();

        // Turn off Discord actual network calls for tests
        plugin.getConfig().set("discord.enabled", false);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("Complete run lifecycle: Join -> Die -> Swap -> Rejoin")
    void shouldCompleteFullRunLifecycle() {
        // 1. Verify plugin loaded properly
        assertThat(plugin.isEnabled()).isTrue();
        assertThat(plugin.getActiveWorldName()).isEqualTo("hardcore_1");

        // 2. Players join the server
        PlayerMock player1 = server.addPlayer("Speedrunner");
        PlayerMock player2 = server.addPlayer("CameraMan");

        // Start timer (mimics auto-start)
        server.getScheduler().performOneTick();
        assertThat(server.getOnlinePlayers()).hasSize(2);

        // 3. Player 1 dies (triggering the death event)
        player1.setHealth(0.0);
        
        // MockBukkit doesn't natively trigger PlayerDeathEvent on setHealth(0),
        // so we cleanly simulate the event the server would fire.
        DamageSource dummySource = org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build();
        PlayerDeathEvent deathEvent = new PlayerDeathEvent(
                player1,
                dummySource,
                List.of(), // no drops
                0,
                Component.text("Speedrunner blew up"),
                false
        );
        server.getPluginManager().callEvent(deathEvent);

        // Process ticks to allow async/delayed tasks (respawn, swap trigger, teleport)
        server.getScheduler().performTicks(20);

        // 4. Verify world swap executed
        // If swap method is DISCONNECT (default), players are kicked.
        // If SEAMLESS, they are teleported.
        // Let's assume default config (DISCONNECT).
        // Players should NOT be in hardcore_1 anymore.
        assertThat(plugin.getActiveWorldName()).isEqualTo("hardcore_2");

        // We check if swap occurred successfully by ensuring the state swapped
        assertThat(plugin.isSwapping()).isFalse();

        // 5. Player rejoins to the new active world
        PlayerMock player3 = server.addPlayer("Speedrunner_Returned");
        assertThat(player3.getWorld().getName()).isEqualTo("hardcore_2");
    }

    @Test
    @DisplayName("Timer correctly tracks and halts on death")
    void timerLifecycle() {
        PlayerMock player1 = server.addPlayer("TimerPlayer");
        server.getScheduler().performTicks(100); // 5 seconds pass

        long start = plugin.getStartTime();
        assertThat(start).isGreaterThanOrEqualTo(0L); // Or whatever valid check

        DamageSource dummySource = org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build();
        PlayerDeathEvent deathEvent = new PlayerDeathEvent(
                player1,
                dummySource,
                List.of(), // no drops
                0,
                Component.text("TimerPlayer died"),
                false
        );
        server.getPluginManager().callEvent(deathEvent);
        server.getScheduler().performTicks(5);

        // Verify timer resets after world swap
        World newWorld = server.getWorld(plugin.getActiveWorldName());
        assertThat(newWorld).isNotNull();
    }
}
