package br.com.radardarede.sensor;

import android.app.Notification;
import android.app.Person;
import android.content.LocusId;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.UUID;

public final class NotificationSnapshotExtractor {
    private NotificationSnapshotExtractor() { }

    public static Result extract(StatusBarNotification sbn) {
        long capturedAt = System.currentTimeMillis();
        JSONObject root = new JSONObject();
        JSONArray messages = new JSONArray();
        String conversation = null;
        try {
            Notification n = sbn.getNotification();
            Bundle e = n.extras != null ? n.extras : Bundle.EMPTY;
            conversation = stringValue(firstNonNull(
                    e.get(Notification.EXTRA_CONVERSATION_TITLE),
                    e.get(Notification.EXTRA_TITLE),
                    e.get(Notification.EXTRA_SUB_TEXT)));

            root.put("snapshot_id", UUID.randomUUID().toString());
            root.put("captured_at", capturedAt);
            root.put("package_name", sbn.getPackageName());
            root.put("notification_key", sbn.getKey());
            root.put("notification_id", sbn.getId());
            root.put("notification_tag", sbn.getTag());
            root.put("post_time", sbn.getPostTime());
            root.put("group_key", sbn.getGroupKey());
            root.put("is_group", sbn.isGroup());
            root.put("category", n.category);
            root.put("flags", n.flags);
            root.put("when", n.when);
            root.put("group", n.getGroup());
            root.put("sort_key", n.getSortKey());
            root.put("channel_id", n.getChannelId());

            // Puramente diagnóstico: nenhuma identidade de grupo depende disto
            // ainda. O título arrastra a contagem cumulativa de mensagens do
            // WhatsApp e muda a cada notificação (docs/GROUP-IDENTITY-PLAN.md,
            // etapa 4); shortcutId/LocusId são a única API do Android que
            // promete identidade estável por conversa, independente do texto
            // exibido, mas não há confirmação de que o WhatsApp os preenche.
            // Esta captura existe para responder essa pergunta com dado real
            // do aparelho antes de qualquer mudança na resolução de grupo.
            if (Build.VERSION.SDK_INT >= 29) {
                String shortcutId = sbn.getShortcutId();
                if (!TextUtils.isEmpty(shortcutId)) root.put("shortcut_id", shortcutId);
                LocusId locusId = n.getLocusId();
                if (locusId != null && !TextUtils.isEmpty(locusId.getId())) {
                    root.put("locus_id", locusId.getId());
                }
            }

            JSONObject extras = new JSONObject();
            putText(extras, "title", e.get(Notification.EXTRA_TITLE));
            putText(extras, "title_big", e.get(Notification.EXTRA_TITLE_BIG));
            putText(extras, "text", e.get(Notification.EXTRA_TEXT));
            putText(extras, "big_text", e.get(Notification.EXTRA_BIG_TEXT));
            putText(extras, "sub_text", e.get(Notification.EXTRA_SUB_TEXT));
            putText(extras, "summary_text", e.get(Notification.EXTRA_SUMMARY_TEXT));
            putText(extras, "conversation_title", e.get(Notification.EXTRA_CONVERSATION_TITLE));
            if (e.containsKey(Notification.EXTRA_IS_GROUP_CONVERSATION)) {
                extras.put("is_group_conversation", e.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION));
            }
            CharSequence[] lines = e.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            if (lines != null) {
                JSONArray arr = new JSONArray();
                for (CharSequence line : lines) arr.put(line == null ? JSONObject.NULL : line.toString());
                extras.put("text_lines", arr);
            }

            parseBundleArray(messages, e.getParcelableArray(Notification.EXTRA_MESSAGES));
            JSONArray historic = new JSONArray();
            parseBundleArray(historic, e.getParcelableArray(Notification.EXTRA_HISTORIC_MESSAGES));
            extras.put("messages", messages);
            extras.put("historic_messages", historic);

            Parcelable personP = e.getParcelable(Notification.EXTRA_MESSAGING_PERSON);
            if (personP instanceof Person) {
                extras.put("messaging_person", personToJson((Person) personP));
            }
            root.put("extras", extras);
        } catch (Exception ex) {
            try { root.put("extract_error", ex.getClass().getSimpleName() + ": " + ex.getMessage()); }
            catch (Exception ignored) { }
        }
        String snapshotId = root.optString("snapshot_id", UUID.randomUUID().toString());
        return new Result(snapshotId, capturedAt, sbn.getKey(), conversation, messages.length(),
                latestMessageAt(messages), root.toString());
    }

    private static void parseBundleArray(JSONArray out, Parcelable[] array) {
        if (array == null) return;
        for (Parcelable p : array) {
            try {
                if (!(p instanceof Bundle)) continue;
                Bundle b = (Bundle) p;
                JSONObject m = new JSONObject();
                putText(m, "text", b.get("text"));
                if (b.containsKey("time")) m.put("time", b.getLong("time"));
                putText(m, "sender", b.get("sender"));
                Parcelable sp = b.getParcelable("sender_person");
                if (sp instanceof Person) m.put("sender_person", personToJson((Person) sp));
                putText(m, "type", b.get("type"));
                putText(m, "uri", b.get("uri"));
                out.put(m);
            } catch (Exception ignored) { }
        }
    }

    private static JSONObject personToJson(Person p) {
        JSONObject o = new JSONObject();
        try {
            o.put("name", p.getName() == null ? JSONObject.NULL : p.getName().toString());
            o.put("key", p.getKey());
            o.put("is_bot", p.isBot());
            o.put("is_important", p.isImportant());
        } catch (Exception ignored) { }
        return o;
    }

    private static void putText(JSONObject o, String key, Object value) throws Exception {
        if (value == null) return;
        if (value instanceof CharSequence) o.put(key, value.toString());
        else o.put(key, String.valueOf(value));
    }

    private static Object firstNonNull(Object... values) {
        for (Object v : values) if (v != null && !TextUtils.isEmpty(String.valueOf(v))) return v;
        return null;
    }
    private static String stringValue(Object value) { return value == null ? null : String.valueOf(value); }

    private static long latestMessageAt(JSONArray messages) {
        long latest = 0L;
        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.optJSONObject(i);
            if (message != null) latest = Math.max(latest, message.optLong("time", 0L));
        }
        return latest;
    }

    public static final class Result {
        public final String snapshotId;
        public final long capturedAt;
        public final String notificationKey;
        public final String conversationLabel;
        public final int messageCount;
        public final long latestMessageAt;
        public final String json;
        Result(String snapshotId, long capturedAt, String notificationKey, String conversationLabel,
               int messageCount, long latestMessageAt, String json) {
            this.snapshotId = snapshotId; this.capturedAt = capturedAt; this.notificationKey = notificationKey;
            this.conversationLabel = conversationLabel; this.messageCount = messageCount;
            this.latestMessageAt = latestMessageAt; this.json = json;
        }
    }
}
