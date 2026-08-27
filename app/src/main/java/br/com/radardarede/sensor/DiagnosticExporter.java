package br.com.radardarede.sensor;

import android.content.Context;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

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
            out.put("incidents", ProbeDatabase.get(context).recentIncidents(50));
            JSONArray raw = ProbeDatabase.get(context).recentSnapshots(80);
            JSONArray sanitized = new JSONArray();
            for (int i = 0; i < raw.length(); i++) sanitized.put(sanitizeSnapshot(raw.getJSONObject(i), i));
            out.put("snapshots", sanitized);
            out.put("privacy_note", "Conteudo textual e identificadores humanos foram substituidos por placeholders; a estrutura tecnica foi preservada.");
        } catch (Exception ignored) { }
try {
    return out.toString(2);
} catch (org.json.JSONException e) {
    return out.toString();
}    }

    private static JSONObject appInfo(Context c) {
        JSONObject o = new JSONObject();
        try {
            String version = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
            o.put("package", c.getPackageName());
            o.put("version", version);
            o.put("parser_version", "0.1.0-probe");
        } catch (Exception ignored) { }
        return o;
    }

    private static JSONObject deviceInfo() {
        JSONObject o = new JSONObject();
        try {
            o.put("manufacturer", Build.MANUFACTURER);
            o.put("model", Build.MODEL);
            o.put("android_release", Build.VERSION.RELEASE);
            o.put("sdk_int", Build.VERSION.SDK_INT);
            o.put("security_patch", Build.VERSION.SECURITY_PATCH);
        } catch (Exception ignored) { }
        return o;
    }

    private static JSONObject sanitizeSnapshot(JSONObject source, int index) {
JSONObject copy;
try {
    copy = new JSONObject(source.toString());
} catch (org.json.JSONException e) {
    copy = new JSONObject();
}        try {
            copy.put("conversation_label", pseudonym("GROUP", source.optString("conversation_label")));
            JSONObject raw = copy.optJSONObject("raw");
            if (raw != null) sanitizeObject(raw);
        } catch (Exception ignored) { }
        return copy;
    }

    private static void sanitizeObject(JSONObject o) throws Exception {
        JSONArray names = o.names();
        if (names == null) return;
        for (int i = 0; i < names.length(); i++) {
            String key = names.getString(i);
            Object value = o.opt(key);
            if (value instanceof JSONObject) sanitizeObject((JSONObject) value);
            else if (value instanceof JSONArray) sanitizeArray((JSONArray) value);
            else if (value instanceof String && shouldRedact(key)) o.put(key, placeholderFor(key, (String) value));
        }
    }

    private static void sanitizeArray(JSONArray a) throws Exception {
        for (int i = 0; i < a.length(); i++) {
            Object value = a.opt(i);
            if (value instanceof JSONObject) sanitizeObject((JSONObject) value);
            else if (value instanceof JSONArray) sanitizeArray((JSONArray) value);
            else if (value instanceof String) a.put(i, "TEXT_" + (i + 1));
        }
    }

    private static boolean shouldRedact(String key) {
        String k = key.toLowerCase();
        return k.contains("text") || k.contains("title") || k.contains("sender") || k.contains("name") ||
                k.contains("conversation") || k.equals("uri") || k.equals("sub_text") || k.equals("summary_text");
    }

    private static String placeholderFor(String key, String value) {
        if (key.toLowerCase().contains("sender") || key.equalsIgnoreCase("name")) return pseudonym("USER", value);
        if (key.toLowerCase().contains("conversation") || key.toLowerCase().contains("title")) return pseudonym("GROUP", value);
        if (key.equalsIgnoreCase("uri")) return "URI_REDACTED";
        return "TEXT_REDACTED";
    }

    private static String pseudonym(String prefix, String value) {
        if (value == null || value.isEmpty()) return prefix + "_UNKNOWN";
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 3; i++) sb.append(String.format("%02x", digest[i]));
            return prefix + "_" + sb;
        } catch (Exception e) { return prefix + "_X"; }
    }
}
