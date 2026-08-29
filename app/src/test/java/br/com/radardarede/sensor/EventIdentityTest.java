package br.com.radardarede.sensor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

import java.util.Arrays;

public class EventIdentityTest {
    @Test public void sameEvidenceCreatesSameEventId() {
        assertEquals(EventIdentity.eventId("same"), EventIdentity.eventId("same"));
        assertNotEquals(EventIdentity.eventId("same"), EventIdentity.eventId("other"));
    }

    @Test public void batchIdDoesNotDependOnOrder() {
        assertEquals(EventIdentity.batchId(Arrays.asList("b", "a")),
                EventIdentity.batchId(Arrays.asList("a", "b")));
    }
}
