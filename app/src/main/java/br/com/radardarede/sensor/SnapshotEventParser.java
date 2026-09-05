package br.com.radardarede.sensor;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;

public final class SnapshotEventParser {
    public static final String VERSION = "0.3.1";
    private static final long FUTURE_TOLERANCE_MS = 5L * 60L * 1000L;

    private SnapshotEventParser() { }

    public static JSONArray parse(String snapshotJson, String networkId, String deviceId) {
        JSONArray events = new JSONArray();
        try {
            JSONObject snapshot = new JSONObject(snapshotJson);
            long capturedAt = snapshot.optLong("captured_at", 0L);
            String snapshotId = snapshot.optString("snapshot_id", "");
            JSONObject extras = snapshot.optJSONObject("extras");
            if (capturedAt <= 0 || extras == null
                    || !isGroupConversation(extras.optBoolean("is_group_conversation", false))) return events;

            String conversation = firstText(extras, "conversation_title", "title", "title_big");
            JSONArray messages = extras.optJSONArray("messages");
            if (empty(conversation) || messages == null) return events;

            String conversationId = "wa_" + EventIdentity.sha256(conversation).substring(0, 32);
            // Diagnóstico apenas (ver NotificationSnapshotExtractor): não participa
            // do cálculo de conversationId acima.
            String shortcutId = snapshot.optString("shortcut_id", null);
            String locusId = snapshot.optString("locus_id", null);
            for (int index = 0; index < messages.length(); index++) {
                JSONObject message = messages.optJSONObject(index);
                if (message == null) continue;
                String text = message.optString("text", "").trim();
                long occurredAt = message.optLong("time", 0L);
                if (!isEligibleMessage(text, occurredAt, capturedAt)) continue;

                String sender = message.optString("sender", "");
                if (sender.isEmpty()) {
                    JSONObject person = message.optJSONObject("sender_person");
                    if (person != null) sender = person.optString("name", "");
                }
                String senderRef = sender.isEmpty() ? null
                        : "sender_" + EventIdentity.sha256(sender).substring(0, 24);
                String fingerprint = conversationId + "|" + occurredAt + "|"
                        + (senderRef == null ? "" : senderRef) + "|" + EventIdentity.sha256(text);
                String eventId = EventIdentity.eventId(fingerprint);

                JSONObject metadata = new JSONObject();
                metadata.put("snapshot_id", snapshotId);
                metadata.put("message_index", index);
                metadata.put("evidence", "notification_messaging_style");
                if (shortcutId != null) metadata.put("shortcut_id", shortcutId);
                if (locusId != null) metadata.put("locus_id", locusId);

                JSONObject event = new JSONObject();
                event.put("schema_version", "0.1.0");
                event.put("event_id", eventId);
                event.put("network_id", networkId);
                event.put("device_id", deviceId);
                event.put("source", "android_notification");
                event.put("source_event_id", "android_notification:" + EventIdentity.sha256(fingerprint));
                event.put("conversation_id", conversationId);
                event.put("conversation_label", conversation);
                event.put("occurred_at", Instant.ofEpochMilli(occurredAt).toString());
                event.put("captured_at", Instant.ofEpochMilli(capturedAt).toString());
                event.put("message_type", "text");
                event.put("text", text);
                if (senderRef != null) event.put("sender_ref", senderRef);
                event.put("parser_version", VERSION);
                event.put("metadata", metadata);
                events.put(event);
            }
        } catch (Exception ignored) { }
        return events;
    }

    private static String firstText(JSONObject object, String... keys) {
        for (String key : keys) {
            String value = object.optString(key, "").trim();
            if (!value.isEmpty()) return value;
        }
        return null;
    }

    private static boolean empty(String value) { return value == null || value.trim().isEmpty(); }

    static boolean isEligibleMessage(String text, long occurredAt, long capturedAt) {
        return text != null && !text.trim().isEmpty() && occurredAt > 0 && capturedAt > 0
                && occurredAt <= capturedAt + FUTURE_TOLERANCE_MS;
    }

    static boolean isGroupConversation(boolean explicitGroupFlag) { return explicitGroupFlag; }
}
