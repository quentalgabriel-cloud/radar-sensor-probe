package br.com.radardarede.sensor;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SyncCoordinator {
    private static final int BATCH_LIMIT = 50;
    private static final long HEARTBEAT_INTERVAL_MS = 60_000L;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private SyncCoordinator() { }

    public static void requestSync(Context context) {
        Context app = context.getApplicationContext();
        IO.execute(() -> sync(app));
    }

    static void sync(Context context) {
        if (!RUNNING.compareAndSet(false, true)) return;
        try {
            SensorConfig config = SensorConfig.current();
            HealthStore.provisioned(context, config.isProvisioned());
            if (!config.isProvisioned()) return;

            ProbeDatabase db = ProbeDatabase.get(context);
            IngestClient client = new IngestClient(config);
            long now = System.currentTimeMillis();
            List<ProbeDatabase.OutboxRecord> records = db.pendingEvents(BATCH_LIMIT, now);
            boolean uploadAttempted = !records.isEmpty();
            if (uploadAttempted) uploadEvents(context, db, client, config, records, now);

            if (uploadAttempted || now - HealthStore.lastHeartbeatAttempt(context) >= HEARTBEAT_INTERVAL_MS) {
                uploadHealth(context, db, client, config, System.currentTimeMillis());
            }
        } finally {
            RUNNING.set(false);
        }
    }

    private static void uploadEvents(Context context, ProbeDatabase db, IngestClient client,
                                     SensorConfig config, List<ProbeDatabase.OutboxRecord> records,
                                     long now) {
        List<String> eventIds = new ArrayList<>();
        JSONArray events = new JSONArray();
        try {
            for (ProbeDatabase.OutboxRecord record : records) {
                eventIds.add(record.eventId);
                events.put(new JSONObject(record.payloadJson));
            }
            JSONObject batch = new JSONObject();
            batch.put("schema_version", "0.1.0");
            batch.put("batch_id", EventIdentity.batchId(eventIds));
            batch.put("network_id", config.networkId);
            batch.put("device_id", config.deviceId);
            batch.put("sent_at", Instant.ofEpochMilli(now).toString());
            batch.put("events", events);
            IngestClient.Result result = client.post("/ingest-events", batch.toString());
            if (result.successful) {
                db.markEventsSent(eventIds, now);
                HealthStore.uploadSucceeded(context, now, eventIds.size());
            } else {
                db.markEventsFailed(records, now, result.error);
                HealthStore.uploadFailed(context, now, result.error);
            }
        } catch (Exception error) {
            db.markEventsFailed(records, now, error.getClass().getSimpleName());
            HealthStore.uploadFailed(context, now, error.getClass().getSimpleName());
        }
    }

    private static void uploadHealth(Context context, ProbeDatabase db, IngestClient client,
                                     SensorConfig config, long now) {
        HealthStore.heartbeatAttempted(context, now);
        try {
            int pending = db.outboxPendingCount();
            JSONObject heartbeat = new JSONObject();
            heartbeat.put("schema_version", "0.1.0");
            heartbeat.put("heartbeat_id", UUID.randomUUID().toString());
            heartbeat.put("network_id", config.networkId);
            heartbeat.put("device_id", config.deviceId);
            heartbeat.put("source", "android_notification");
            heartbeat.put("observed_at", Instant.ofEpochMilli(now).toString());
            heartbeat.put("adapter_version", BuildConfig.VERSION_NAME);
            heartbeat.put("parser_version", SnapshotEventParser.VERSION);
            heartbeat.put("status", HealthStore.remoteStatus(context, pending));
            heartbeat.put("outbox_pending", pending);
            long oldest = db.oldestPendingAt();
            if (oldest > 0) heartbeat.put("oldest_pending_at", Instant.ofEpochMilli(oldest).toString());
            long lastEvent = HealthStore.lastSnapshot(context);
            if (lastEvent > 0) heartbeat.put("last_event_captured_at", Instant.ofEpochMilli(lastEvent).toString());
            long lastUpload = HealthStore.lastUploadSucceeded(context);
            if (lastUpload > 0) heartbeat.put("last_upload_succeeded_at", Instant.ofEpochMilli(lastUpload).toString());
            JSONObject counters = new JSONObject();
            counters.put("snapshots_local", db.snapshotCount());
            counters.put("events_uploaded", HealthStore.eventsUploaded(context));
            counters.put("upload_failures", HealthStore.uploadFailures(context));
            heartbeat.put("counters", counters);

            IngestClient.Result result = client.post("/ingest-health", heartbeat.toString());
            if (result.successful) HealthStore.heartbeatSucceeded(context, now);
            else HealthStore.heartbeatFailed(context, now, result.error);
        } catch (Exception error) {
            HealthStore.heartbeatFailed(context, now, error.getClass().getSimpleName());
        }
    }
}
