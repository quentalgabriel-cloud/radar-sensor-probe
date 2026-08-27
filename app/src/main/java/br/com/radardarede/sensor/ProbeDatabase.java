package br.com.radardarede.sensor;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;

public final class ProbeDatabase extends SQLiteOpenHelper {
    private static final String DB_NAME = "radar_probe.db";
    private static final int DB_VERSION = 1;
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
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }

    public long insertSnapshot(String snapshotId, long capturedAt, String notificationKey,
                               String conversationLabel, int messageCount, String rawJson) {
        ContentValues v = new ContentValues();
        v.put("snapshot_id", snapshotId);
        v.put("captured_at", capturedAt);
        v.put("notification_key", notificationKey);
        v.put("conversation_label", conversationLabel);
        v.put("message_count", messageCount);
        v.put("raw_json", rawJson);
        return getWritableDatabase().insertWithOnConflict("snapshots", null, v, SQLiteDatabase.CONFLICT_IGNORE);
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
    }
}
