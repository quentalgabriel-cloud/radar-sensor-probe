package br.com.radardarede.sensor;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BackoffPolicyTest {
    @Test public void growsAndStopsAtFifteenMinutes() {
        assertEquals(15_000L, BackoffPolicy.delayMs(0));
        assertEquals(30_000L, BackoffPolicy.delayMs(1));
        assertEquals(900_000L, BackoffPolicy.delayMs(20));
    }
}
