package br.com.radardarede.sensor;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class ProbeDatabase extends SQLiteOpenHelper {
    private static final String DB_NAME = "radar_probe.db";
    private static final int DB_VERSION = 2;
    private static final int MAX_SNAPSHOTS = 500;
    private static final int MAX_INCIDENTS = 500;
    private static final long RETENTION_MS = 7L * 24L * 60L * 60L * 1000L;
    private static volatile ProbeDatabase instance;

    public static ProbeDatabase get(Context context) {
        if (instance == null) {
            synchronized (ProbeDatabase.class) {
                if (instance == null) instance = new ProbeDatabase(context.getApplicationContext());
            }
        }
        return instance;
    }

    private ProbeDatabase(Context context) { super(context, DB_NAME, null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE snapshots (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "snapshot_id TEXT UNIQUE NOT NULL," +
                "captured_at INTEGER NOT NULL," +
                "notification_key TEXT," +
                "conversation_label TEXT," +
                "message_count INTEGER DEFAULT 0," +
                "parser_status TEXT NOT NULL DEFAULT 'RAW'," +
                "raw_json TEXT NOT NULL)");
        db.execSQL("CREATE INDEX idx_snapshots_captured_at ON snapshots(captured_at DESC)");
        db.execSQL("CREATE TABLE incidents (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "occurred_at INTEGER NOT NULL," +
                "type TEXT NOT NULL," +
                "detail TEXT)");
        createOutbox(db);
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) createOutbox(db);
    }

    private static void createOutbox(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS event_outbox (" +
                "event_id TEXT PRIMARY KEY," +
                "created_at INTEGER NOT NULL," +
                "payload_json TEXT NOT NULL," +
                "attempts INTEGER NOT NULL DEFAULT 0," +
                "next_attempt_at INTEGER NOT NULL DEFAULT 0," +
                "sent_at INTEGER," +
                "last_error TEXT)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_event_outbox_pending " +
                "ON event_outbox(sent_at,next_attempt_at,created_at)");
    }

    public long insertSnapshot(String snapshotId, long capturedAt, String notificationKey,
                               String conversationLabel, int messageCount, String rawJson) {
        ContentValues v = new ContentValues();
        v.put("snapshot_id", snapshotId);
        v.put("captured_at", capturedAt);
        v.put("notification_key", notificationKey);
        v.put("conversation_label", conversationLabel);
        v.put("message_count", messageCount);
        v.put("raw_json", rawJson);
        SQLiteDatabase db = getWritableDatabase();
        long row = db.insertWithOnConflict("snapshots", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        if (row != -1) prune(db, System.currentTimeMillis());
        return row;
    }

    public long insertSnapshotWithEvents(String snapshotId, long capturedAt, String notificationKey,
                                         String conversationLabel, int messageCount, String rawJson,
                                         JSONArray events) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues snapshot = new ContentValues();
            snapshot.put("snapshot_id", snapshotId);
            snapshot.put("captured_at", capturedAt);
            snapshot.put("notification_key", notificationKey);
            snapshot.put("conversation_label", conversationLabel);
            snapshot.put("message_count", messageCount);
            snapshot.put("raw_json", rawJson);
            long row = db.insertWithOnConflict("snapshots", null, snapshot, SQLiteDatabase.CONFLICT_IGNORE);
            if (row != -1) {
                for (int index = 0; index < events.length(); index++) {
                    JSONObject event = events.optJSONObject(index);
                    if (event == null || event.optString("event_id", "").isEmpty()) continue;
                    ContentValues outbox = new ContentValues();
                    outbox.put("event_id", event.optString("event_id"));
                    outbox.put("created_at", capturedAt);
                    outbox.put("payload_json", event.toString());
                    db.insertWithOnConflict("event_outbox", null, outbox, SQLiteDatabase.CONFLICT_IGNORE);
                }
                prune(db, System.currentTimeMillis());
            }
            db.setTransactionSuccessful();
            return row;
        } finally {
            db.endTransaction();
        }
    }

    public List<OutboxRecord> pendingEvents(int limit, long now) {
        List<OutboxRecord> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT event_id,payload_json,attempts FROM event_outbox " +
                        "WHERE sent_at IS NULL AND next_attempt_at <= ? ORDER BY created_at ASC LIMIT ?",
                new String[]{String.valueOf(now), String.valueOf(limit)})) {
            while (c.moveToNext()) out.add(new OutboxRecord(c.getString(0), c.getString(1), c.getInt(2)));
        }
        return out;
    }

    public int outboxPendingCount() {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM event_outbox WHERE sent_at IS NULL", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public long oldestPendingAt() {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT MIN(created_at) FROM event_outbox WHERE sent_at IS NULL", null)) {
            return c.moveToFirst() && !c.isNull(0) ? c.getLong(0) : 0L;
        }
    }

    public void markEventsSent(List<String> eventIds, long sentAt) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (String eventId : eventIds) {
                ContentValues values = new ContentValues();
                values.put("sent_at", sentAt);
                values.putNull("last_error");
                db.update("event_outbox", values, "event_id = ?", new String[]{eventId});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void markEventsFailed(List<OutboxRecord> records, long now, String error) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (OutboxRecord record : records) {
                ContentValues values = new ContentValues();
                values.put("attempts", record.attempts + 1);
                values.put("next_attempt_at", now + BackoffPolicy.delayMs(record.attempts));
                values.put("last_error", safeError(error));
                db.update("event_outbox", values, "event_id = ?", new String[]{record.eventId});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private static String safeError(String error) {
        if (error == null) return "unknown";
        String normalized = error.replaceAll("[\\r\\n]+", " ");
        return normalized.length() > 160 ? normalized.substring(0, 160) : normalized;
    }

    public void addIncident(String type, String detail) {
        ContentValues v = new ContentValues();
        v.put("occurred_at", System.currentTimeMillis());
        v.put("type", type);
        v.put("detail", detail);
        getWritableDatabase().insert("incidents", null, v);
    }

    public JSONArray recentSnapshots(int limit) {
        JSONArray out = new JSONArray();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT snapshot_id,captured_at,notification_key,conversation_label,message_count,parser_status,raw_json " +
                        "FROM snapshots ORDER BY captured_at DESC LIMIT ?", new String[]{String.valueOf(limit)})) {
            while (c.moveToNext()) {
                JSONObject o = new JSONObject();
                o.put("snapshot_id", c.getString(0));
                o.put("captured_at", c.getLong(1));
                o.put("notification_key", c.getString(2));
                o.put("conversation_label", c.getString(3));
                o.put("message_count", c.getInt(4));
                o.put("parser_status", c.getString(5));
                try { o.put("raw", new JSONObject(c.getString(6))); }
                catch (Exception ignored) { o.put("raw", c.getString(6)); }
                out.put(o);
            }
        } catch (Exception ignored) { }
        return out;
    }

    public JSONArray recentIncidents(int limit) {
        JSONArray out = new JSONArray();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT occurred_at,type,detail FROM incidents ORDER BY occurred_at DESC LIMIT ?",
                new String[]{String.valueOf(limit)})) {
            while (c.moveToNext()) {
                JSONObject o = new JSONObject();
                o.put("occurred_at", c.getLong(0));
                o.put("type", c.getString(1));
                o.put("detail", c.getString(2));
                out.put(o);
            }
        } catch (Exception ignored) { }
        return out;
    }

    public int snapshotCount() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM snapshots", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public void clearProbeData() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("snapshots", null, null);
        db.delete("incidents", null, null);
        db.delete("event_outbox", null, null);
    }

    private void prune(SQLiteDatabase db, long now) {
        db.delete("snapshots", "captured_at < ?", new String[]{String.valueOf(now - RETENTION_MS)});
        db.delete("incidents", "occurred_at < ?", new String[]{String.valueOf(now - RETENTION_MS)});
        db.delete("event_outbox", "sent_at IS NOT NULL AND sent_at < ?",
                new String[]{String.valueOf(now - RETENTION_MS)});
        db.execSQL("DELETE FROM snapshots WHERE id NOT IN (SELECT id FROM snapshots ORDER BY captured_at DESC LIMIT " + MAX_SNAPSHOTS + ")");
        db.execSQL("DELETE FROM incidents WHERE id NOT IN (SELECT id FROM incidents ORDER BY occurred_at DESC LIMIT " + MAX_INCIDENTS + ")");
    }

    public static final class OutboxRecord {
        public final String eventId;
        public final String payloadJson;
        public final int attempts;

        OutboxRecord(String eventId, String payloadJson, int attempts) {
            this.eventId = eventId;
            this.payloadJson = payloadJson;
            this.attempts = attempts;
        }
    }
}
