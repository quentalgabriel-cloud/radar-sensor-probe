package br.com.radardarede.sensor;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CaptureTestEvaluatorTest {
    private static final long STARTED_AT = 1_000_000L;

    @Test public void acceptsFreshMessagePayload() {
        assertTrue(CaptureTestEvaluator.shouldPass(
                true, STARTED_AT, STARTED_AT + 15_000L, 1, STARTED_AT - 16_000L, false));
    }

    @Test public void rejectsAuxiliaryNotificationWithoutMessages() {
        assertFalse(CaptureTestEvaluator.shouldPass(
                true, STARTED_AT, STARTED_AT + 14_000L, 0, 0L, false));
    }

    @Test public void rejectsRecoveredSnapshot() {
        assertFalse(CaptureTestEvaluator.shouldPass(
                true, STARTED_AT, STARTED_AT + 1_000L, 2, STARTED_AT, true));
    }

    @Test public void rejectsOldMessageRepostedDuringTest() {
        assertFalse(CaptureTestEvaluator.shouldPass(
                true, STARTED_AT, STARTED_AT + 20_000L, 7, STARTED_AT - 120_000L, false));
    }

    @Test public void rejectsObservationBeforeTest() {
        assertFalse(CaptureTestEvaluator.shouldPass(
                true, STARTED_AT, STARTED_AT - 1L, 1, STARTED_AT - 1L, false));
    }
}
