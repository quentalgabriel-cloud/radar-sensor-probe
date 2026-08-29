package br.com.radardarede.sensor;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SensorConfigTest {
    @Test public void requiresCompleteScopedConfiguration() {
        assertFalse(SensorConfig.of("", "", "", "").isProvisioned());
        assertTrue(SensorConfig.of(
                "https://example.supabase.co/functions/v1/",
                "11111111-1111-4111-8111-111111111111",
                "22222222-2222-4222-8222-222222222222",
                "01234567890123456789012345678901").isProvisioned());
    }
}
