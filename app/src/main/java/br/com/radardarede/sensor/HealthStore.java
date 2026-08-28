package br.com.radardarede.sensor;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

public final class HealthStore {
    private static final String PREF = "radar_health";
    private static final long LISTENER_FRESH_MS = 150_000L;
    private static SharedPreferences p(Context c) { return c.getSharedPreferences(PREF, Context.MODE_PRIVATE); }

    public static void listenerConnected(Context c, boolean connected) {
        long now = System.currentTimeMillis();
        SharedPreferences.Editor edit = p(c).edit().putBoolean("listener_connected", connected)
                .putLong(connected ? "listener_connected_at" : "listener_disconnected_at", now);
        if (connected) edit.putLong("listener_heartbeat_at", now);
        edit.apply();
    }
    public static void listenerHeartbeat(Context c) {
        p(c).edit().putLong("listener_heartbeat_at", System.currentTimeMillis()).apply();
    }
    public static void whatsappObserved(Context c, long at) {
        p(c).edit().putLong("last_whatsapp_notification_at", at)
                .putLong("listener_heartbeat_at", System.currentTimeMillis()).apply();
    }
    public static void snapshotStored(Context c, long at) {
        p(c).edit().putLong("last_snapshot_stored_at", at).apply();
    }
    public static boolean isListenerConnected(Context c) {
        SharedPreferences pref = p(c);
        long heartbeat = pref.getLong("listener_heartbeat_at", 0L);
        return pref.getBoolean("listener_connected", false)
                && heartbeat > 0
                && System.currentTimeMillis() - heartbeat <= LISTENER_FRESH_MS;
    }
    public static long lastWhatsapp(Context c) { return p(c).getLong("last_whatsapp_notification_at", 0L); }
    public static void startTest(Context c) {
        p(c).edit().putBoolean("test_waiting", true).putLong("test_started_at", System.currentTimeMillis())
                .remove("test_passed_at").apply();
    }
    public static void maybePassTest(Context c, long eventAt) {
        SharedPreferences pref = p(c);
        if (pref.getBoolean("test_waiting", false) && eventAt >= pref.getLong("test_started_at", Long.MAX_VALUE)) {
            pref.edit().putBoolean("test_waiting", false).putLong("test_passed_at", eventAt).apply();
        }
    }
    public static boolean isTestWaiting(Context c) { return p(c).getBoolean("test_waiting", false); }
    public static long testStarted(Context c) { return p(c).getLong("test_started_at", 0L); }
    public static long testPassed(Context c) { return p(c).getLong("test_passed_at", 0L); }

    public static JSONObject asJson(Context c) {
        JSONObject o = new JSONObject();
        try {
            SharedPreferences pref = p(c);
            o.put("listener_connected", isListenerConnected(c));
            o.put("listener_state_recorded", pref.getBoolean("listener_connected", false));
            o.put("listener_heartbeat_at", pref.getLong("listener_heartbeat_at", 0L));
            o.put("last_whatsapp_notification_at", pref.getLong("last_whatsapp_notification_at", 0L));
            o.put("last_snapshot_stored_at", pref.getLong("last_snapshot_stored_at", 0L));
            o.put("listener_connected_at", pref.getLong("listener_connected_at", 0L));
            o.put("listener_disconnected_at", pref.getLong("listener_disconnected_at", 0L));
            o.put("test_waiting", pref.getBoolean("test_waiting", false));
            o.put("test_started_at", pref.getLong("test_started_at", 0L));
            o.put("test_passed_at", pref.getLong("test_passed_at", 0L));
        } catch (Exception ignored) { }
        return o;
    }
}
