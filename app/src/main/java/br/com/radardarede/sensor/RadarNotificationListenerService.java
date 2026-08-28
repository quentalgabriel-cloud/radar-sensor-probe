package br.com.radardarede.sensor;

import android.content.ComponentName;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RadarNotificationListenerService extends NotificationListenerService {
    private static final long HEARTBEAT_INTERVAL_MS = 60_000L;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler heartbeatHandler = new Handler(Looper.getMainLooper());
    private final Runnable heartbeat = new Runnable() {
        @Override public void run() {
            HealthStore.listenerHeartbeat(RadarNotificationListenerService.this);
            heartbeatHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS);
        }
    };

    @Override public void onListenerConnected() {
        super.onListenerConnected();
        HealthStore.listenerConnected(this, true);
        heartbeatHandler.removeCallbacks(heartbeat);
        heartbeatHandler.post(heartbeat);
        ProbeDatabase.get(this).addIncident("LISTENER_CONNECTED", null);
        recoverActiveNotifications();
    }

    @Override public void onListenerDisconnected() {
        HealthStore.listenerConnected(this, false);
        heartbeatHandler.removeCallbacks(heartbeat);
        ProbeDatabase.get(this).addIncident("LISTENER_DISCONNECTED", null);
        try { requestRebind(new ComponentName(this, RadarNotificationListenerService.class)); }
        catch (Exception ignored) { }
        super.onListenerDisconnected();
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || !"com.whatsapp".equals(sbn.getPackageName())) return;
        persist(sbn, false);
    }

    @Override public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn != null && "com.whatsapp".equals(sbn.getPackageName())) {
            ProbeDatabase.get(this).addIncident("WHATSAPP_NOTIFICATION_REMOVED", sbn.getKey());
        }
    }

    private void recoverActiveNotifications() {
        io.execute(() -> {
            try {
                StatusBarNotification[] active = getActiveNotifications();
                if (active == null) return;
                for (StatusBarNotification sbn : active) {
                    if (sbn != null && "com.whatsapp".equals(sbn.getPackageName())) persist(sbn, true);
                }
            } catch (Exception ex) {
                ProbeDatabase.get(this).addIncident("ACTIVE_NOTIFICATION_RECOVERY_FAILED", ex.getMessage());
            }
        });
    }

    private void persist(StatusBarNotification sbn, boolean recovered) {
        io.execute(() -> {
            NotificationSnapshotExtractor.Result r = NotificationSnapshotExtractor.extract(sbn);
            ProbeDatabase db = ProbeDatabase.get(this);
            long row = db.insertSnapshot(r.snapshotId, r.capturedAt, r.notificationKey, r.conversationLabel, r.messageCount, r.json);
            HealthStore.whatsappObserved(this, r.capturedAt);
            if (row != -1) {
                HealthStore.snapshotStored(this, r.capturedAt);
                HealthStore.maybePassTest(this, r.capturedAt);
            }
            if (recovered) db.addIncident("ACTIVE_SNAPSHOT_RECOVERED", r.notificationKey);
        });
    }

    @Override public void onDestroy() {
        heartbeatHandler.removeCallbacks(heartbeat);
        HealthStore.listenerConnected(this, false);
        io.shutdown();
        super.onDestroy();
    }
}
