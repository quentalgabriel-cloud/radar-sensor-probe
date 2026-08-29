package br.com.radardarede.sensor;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SnapshotEventParserTest {
    @Test public void acceptsOnlyExplicitTimedText() {
        long captured = 1_000_000L;
        assertTrue(SnapshotEventParser.isEligibleMessage("Mensagem", captured - 1_000L, captured));
        assertFalse(SnapshotEventParser.isEligibleMessage("", captured, captured));
        assertFalse(SnapshotEventParser.isEligibleMessage("Mensagem", 0L, captured));
        assertFalse(SnapshotEventParser.isEligibleMessage("Mensagem", captured + 301_000L, captured));
    }

    @Test public void groupEvidenceMustBeExplicit() {
        assertTrue(SnapshotEventParser.isGroupConversation(true));
        assertFalse(SnapshotEventParser.isGroupConversation(false));
    }
}
