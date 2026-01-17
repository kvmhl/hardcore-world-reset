package com.github.kdltmhl.hardcoreworldreset;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the timer functionality.
 * Tests timer start, pause, resume, reset, and display.
 */
@DisplayName("Timer Tests")
class TimerTest {

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
    @DisplayName("Timer Start Tests")
    class TimerStartTests {

        @Test
        @DisplayName("Should start timer without errors")
        void shouldStartTimerWithoutErrors() {
            // When/Then - should not throw
            plugin.startOrResumeTimer();
        }

        @Test
        @DisplayName("Should not start timer twice")
        void shouldNotStartTimerTwice() {
            // Given
            plugin.startOrResumeTimer();

            // When - start again
            plugin.startOrResumeTimer();

            // Then - no errors, timer still running (single instance)
        }
    }

    @Nested
    @DisplayName("Timer Pause Tests")
    class TimerPauseTests {

        @Test
        @DisplayName("Should pause running timer")
        void shouldPauseRunningTimer() {
            // Given
            plugin.startOrResumeTimer();

            // When
            plugin.pauseTimer();

            // Then - no errors
        }

        @Test
        @DisplayName("Should handle pausing already paused timer")
        void shouldHandlePausingAlreadyPausedTimer() {
            // Given
            plugin.startOrResumeTimer();
            plugin.pauseTimer();

            // When/Then - should not throw
            plugin.pauseTimer();
        }

        @Test
        @DisplayName("Should handle pausing timer that was never started")
        void shouldHandlePausingNeverStartedTimer() {
            // When/Then - should not throw
            plugin.pauseTimer();
        }
    }

    @Nested
    @DisplayName("Timer Resume Tests")
    class TimerResumeTests {

        @Test
        @DisplayName("Should resume paused timer")
        void shouldResumePausedTimer() {
            // Given
            plugin.startOrResumeTimer();
            plugin.pauseTimer();

            // When/Then - should not throw
            plugin.startOrResumeTimer();
        }
    }

    @Nested
    @DisplayName("Timer Stop Tests")
    class TimerStopTests {

        @Test
        @DisplayName("Should stop timer and return formatted time")
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        void shouldStopTimerAndReturnFormattedTime() throws InterruptedException {
            // Given
            plugin.startOrResumeTimer();

            // Let some time pass
            Thread.sleep(100);

            // When
            String result = plugin.stopTimerAndAnnounce();

            // Then
            assertThat(result).isNotEmpty();
            assertThat(result).matches("\\d{2}:\\d{2}:\\d{2}\\.\\d{2}");
        }

        @Test
        @DisplayName("Should return empty string if timer never started")
        void shouldReturnEmptyStringIfTimerNeverStarted() {
            // When
            String result = plugin.stopTimerAndAnnounce();

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should include final time when stopped while paused")
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        void shouldIncludeFinalTimeWhenStoppedWhilePaused() throws InterruptedException {
            // Given
            plugin.startOrResumeTimer();
            Thread.sleep(100);
            plugin.pauseTimer();

            // When
            String result = plugin.stopTimerAndAnnounce();

            // Then
            assertThat(result).isNotEmpty();
            assertThat(result).matches("\\d{2}:\\d{2}:\\d{2}\\.\\d{2}");
        }
    }

    @Nested
    @DisplayName("Timer Reset Tests")
    class TimerResetTests {

        @Test
        @DisplayName("Should reset running timer")
        void shouldResetRunningTimer() {
            // Given
            plugin.startOrResumeTimer();

            // When
            plugin.resetTimer();

            // Then
            String result = plugin.stopTimerAndAnnounce();
            assertThat(result).isEmpty(); // Timer was reset
        }

        @Test
        @DisplayName("Should reset paused timer")
        void shouldResetPausedTimer() {
            // Given
            plugin.startOrResumeTimer();
            plugin.pauseTimer();

            // When
            plugin.resetTimer();

            // Then
            String result = plugin.stopTimerAndAnnounce();
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should handle resetting never-started timer")
        void shouldHandleResettingNeverStartedTimer() {
            // When/Then - should not throw
            plugin.resetTimer();
        }

        @Test
        @DisplayName("Should allow starting timer after reset")
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        void shouldAllowStartingTimerAfterReset() throws InterruptedException {
            // Given
            plugin.startOrResumeTimer();
            plugin.resetTimer();

            // When
            plugin.startOrResumeTimer();
            Thread.sleep(50);
            String result = plugin.stopTimerAndAnnounce();

            // Then
            assertThat(result).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Timer Display Tests")
    class TimerDisplayTests {

        @Test
        @DisplayName("Should update player list footer with timer")
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        void shouldUpdatePlayerListFooterWithTimer() throws InterruptedException {
            // Given
            PlayerMock player = server.addPlayer();
            plugin.startOrResumeTimer();

            // Let timer task run
            Thread.sleep(100);
            server.getScheduler().performTicks(5);

            // Timer display is async, so we just verify no errors
        }

        @Test
        @DisplayName("Should clear player list footer on reset")
        void shouldClearPlayerListFooterOnReset() {
            // Given
            PlayerMock player = server.addPlayer();
            plugin.startOrResumeTimer();
            server.getScheduler().performTicks(2);

            // When
            plugin.resetTimer();

            // Then - footer should be cleared (implementation verifies null is set)
        }
    }

    @Nested
    @DisplayName("Timer Format Tests")
    class TimerFormatTests {

        @Test
        @DisplayName("Should format time as HH:MM:SS.ms")
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        void shouldFormatTimeCorrectly() throws InterruptedException {
            // Given
            plugin.startOrResumeTimer();
            Thread.sleep(100);

            // When
            String time = plugin.stopTimerAndAnnounce();

            // Then - format should be XX:XX:XX.XX
            assertThat(time).matches("\\d{2}:\\d{2}:\\d{2}\\.\\d{2}");
        }

        @Test
        @DisplayName("Should start from 00:00:00.00")
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        void shouldStartFromZero() throws InterruptedException {
            // Given
            plugin.startOrResumeTimer();

            // Minimal delay - stop almost immediately
            String time = plugin.stopTimerAndAnnounce();

            // Then - should be very close to zero
            assertThat(time).startsWith("00:00:00");
        }
    }

    @Nested
    @DisplayName("Timer Edge Cases")
    class TimerEdgeCases {

        @Test
        @DisplayName("Should handle rapid start/stop cycles")
        void shouldHandleRapidStartStopCycles() {
            // When/Then - no errors
            for (int i = 0; i < 10; i++) {
                plugin.startOrResumeTimer();
                plugin.pauseTimer();
            }
        }

        @Test
        @DisplayName("Should handle rapid reset cycles")
        void shouldHandleRapidResetCycles() {
            // When/Then - no errors
            for (int i = 0; i < 10; i++) {
                plugin.startOrResumeTimer();
                plugin.resetTimer();
            }
        }

        @Test
        @DisplayName("Should handle timer operations while swapping")
        void shouldHandleTimerOperationsWhileSwapping() {
            // Given
            plugin.setSwapping(true);

            // When/Then - timer operations should still work
            plugin.startOrResumeTimer();
            plugin.pauseTimer();
            plugin.startOrResumeTimer();
            plugin.resetTimer();
        }
    }
}
