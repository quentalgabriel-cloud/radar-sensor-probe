package br.com.radardarede.sensor;

import android.content.Context;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class DiagnosticExporter {
    private DiagnosticExporter() { }

    public static String buildSanitized(Context context) {
        JSONObject out = new JSONObject();
        try {
            out.put("generated_at", System.currentTimeMillis());
            out.put("app", appInfo(context));
            out.put("device", deviceInfo());
            out.put("health", HealthStore.asJson(context));
            out.put("snapshot_count", ProbeDatabase.get(context).snapshotCount());
            out.put("incidents", sanitizeIncidents(ProbeDatabase.get(context).recentIncidents(50)));

            JSONArray raw = ProbeDatabase.get(context).recentSnapshots(80);
            JSONArray sanitized = new JSONArray();
            for (int i = 0; i < raw.length(); i++) {
                sanitized.put(sanitizeSnapshot(raw.getJSONObject(i)));
            }
            out.put("snapshots", sanitized);
            out.put("privacy_note", "Conteudo textual e identificadores foram removidos ou pseudonimizados; a estrutura tecnica foi preservada.");
        } catch (Exception ignored) { }

        try {
            return out.toString(2);
        } catch (Exception ignored) {
            return out.toString();
        }
    }

    private static JSONObject appInfo(Context context) {
        JSONObject out = new JSONObject();
        try {
            String version = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
            out.put("package", context.getPackageName());
            out.put("version", version);
            out.put("parser_version", "not-implemented");
            out.put("probe_contract", "0.2.0");
        } catch (Exception ignored) { }
        return out;
    }

    private static JSONObject deviceInfo() {
        JSONObject out = new JSONObject();
        try {
            out.put("manufacturer", Build.MANUFACTURER);
            out.put("model", Build.MODEL);
            out.put("android_release", Build.VERSION.RELEASE);
            out.put("sdk_int", Build.VERSION.SDK_INT);
            out.put("security_patch", Build.VERSION.SECURITY_PATCH);
        } catch (Exception ignored) { }
        return out;
    }

    private static JSONArray sanitizeIncidents(JSONArray incidents) {
        JSONArray out = new JSONArray();
        for (int i = 0; i < incidents.length(); i++) {
            JSONObject source = incidents.optJSONObject(i);
            if (source == null) continue;
            JSONObject item = new JSONObject();
            try {
                item.put("occurred_at", source.optLong("occurred_at"));
                item.put("type", source.optString("type"));
                if (source.has("detail") && !source.isNull("detail")) {
                    item.put("detail_ref", pseudonym("DETAIL", source.optString("detail")));
                }
            } catch (Exception ignored) { }
            out.put(item);
        }
        return out;
    }

    private static JSONObject sanitizeSnapshot(JSONObject source) {
        JSONObject copy;
        try {
            copy = new JSONObject(source.toString());
        } catch (Exception ignored) {
            return new JSONObject();
        }

        try {
            replaceWithPseudonym(copy, "snapshot_id", "SNAPSHOT");
            replaceWithPseudonym(copy, "notification_key", "NOTIFICATION");
            replaceWithPseudonym(copy, "conversation_label", "GROUP");
            JSONObject raw = copy.optJSONObject("raw");
            if (raw != null) sanitizeObject(raw);
        } catch (Exception ignored) { }
        return copy;
    }

    private static void sanitizeObject(JSONObject object) throws Exception {
        JSONArray names = object.names();
        if (names == null) return;
        for (int i = 0; i < names.length(); i++) {
            String key = names.getString(i);
            Object value = object.opt(key);
            if (value instanceof JSONObject) {
                sanitizeObject((JSONObject) value);
            } else if (value instanceof JSONArray) {
                sanitizeArray((JSONArray) value);
            } else if (value instanceof String) {
                object.put(key, sanitizedString(key, (String) value));
            }
        }
    }

    private static void sanitizeArray(JSONArray array) throws Exception {
        for (int i = 0; i < array.length(); i++) {
            Object value = array.opt(i);
            if (value instanceof JSONObject) {
                sanitizeObject((JSONObject) value);
            } else if (value instanceof JSONArray) {
                sanitizeArray((JSONArray) value);
            } else if (value instanceof String) {
                array.put(i, "TEXT_" + (i + 1));
            }
        }
    }

    private static String sanitizedString(String key, String value) {
        String normalized = key.toLowerCase();
        if (normalized.contains("sender") || normalized.equals("name") || normalized.contains("person")) {
            return pseudonym("USER", value);
        }
        if (normalized.contains("conversation") || normalized.contains("title")) {
            return pseudonym("GROUP", value);
        }
        if (normalized.contains("notification_key") || normalized.equals("notification_tag")) {
            return pseudonym("NOTIFICATION", value);
        }
        if (normalized.equals("key")) return pseudonym("IDENTIFIER", value);
        if (normalized.equals("group_key") || normalized.equals("group") || normalized.equals("sort_key")) {
            return pseudonym("ANDROID_GROUP", value);
        }
        if (normalized.equals("snapshot_id")) return pseudonym("SNAPSHOT", value);
        if (normalized.equals("uri")) return "URI_REDACTED";
        if (normalized.contains("error")) return "ERROR_REDACTED";
        if (normalized.contains("text")) return "TEXT_REDACTED";
        return value;
    }

    private static void replaceWithPseudonym(JSONObject object, String key, String prefix) throws Exception {
        if (object.has(key) && !object.isNull(key)) {
            object.put(key, pseudonym(prefix, object.optString(key)));
        }
    }

    private static String pseudonym(String prefix, String value) {
        if (value == null || value.isEmpty()) return prefix + "_UNKNOWN";
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < 6; i++) out.append(String.format("%02x", digest[i]));
            return prefix + "_" + out;
        } catch (Exception ignored) {
            return prefix + "_UNKNOWN";
        }
    }
}
