package br.com.radardarede.sensor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class EventIdentity {
    private EventIdentity() { }

    public static String eventId(String fingerprint) {
        return UUID.nameUUIDFromBytes(("radar-event-v1:" + fingerprint)
                .getBytes(StandardCharsets.UTF_8)).toString();
    }

    public static String batchId(List<String> eventIds) {
        List<String> sorted = new ArrayList<>(eventIds);
        Collections.sort(sorted);
        return UUID.nameUUIDFromBytes(("radar-batch-v1:" + String.join("|", sorted))
                .getBytes(StandardCharsets.UTF_8)).toString();
    }

    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte item : digest) out.append(String.format("%02x", item));
            return out.toString();
        } catch (Exception impossible) {
            throw new IllegalStateException("SHA-256 indisponivel", impossible);
        }
    }
}
