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
            SyncCoordinator.requestSync(RadarNotificationListenerService.this);
            heartbeatHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS);
        }
    };

    @Override public void onListenerConnected() {
        super.onListenerConnected();
        HealthStore.listenerConnected(this, true);
        heartbeatHandler.removeCallbacks(heartbeat);
        heartbeatHandler.post(heartbeat);
        ProbeDatabase.get(this).addIncident("LISTENER_CONNECTED", null);
        SyncCoordinator.requestSync(this);
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
        if (sbn == null || !isWhatsApp(sbn.getPackageName())) return;
        persist(sbn, false);
    }

    @Override public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn != null && isWhatsApp(sbn.getPackageName())) {
            ProbeDatabase.get(this).addIncident("WHATSAPP_NOTIFICATION_REMOVED", sbn.getKey());
        }
    }

    private void recoverActiveNotifications() {
        io.execute(() -> {
            try {
                StatusBarNotification[] active = getActiveNotifications();
                if (active == null) return;
                for (StatusBarNotification sbn : active) {
                    if (sbn != null && isWhatsApp(sbn.getPackageName())) persist(sbn, true);
                }
            } catch (Exception ex) {
                ProbeDatabase.get(this).addIncident("ACTIVE_NOTIFICATION_RECOVERY_FAILED", ex.getMessage());
            }
        });
    }

    private void persist(StatusBarNotification sbn, boolean recovered) {
        String shortcutId = lookupShortcutId(sbn);
        io.execute(() -> {
            NotificationSnapshotExtractor.Result r = NotificationSnapshotExtractor.extract(sbn, shortcutId);
            ProbeDatabase db = ProbeDatabase.get(this);
            SensorConfig config = SensorConfig.current();
            HealthStore.provisioned(this, config.isProvisioned());
            org.json.JSONArray events = config.isProvisioned()
                    ? SnapshotEventParser.parse(r.json, config.networkId, config.deviceId)
                    : new org.json.JSONArray();
            long row = db.insertSnapshotWithEvents(r.snapshotId, r.capturedAt, r.notificationKey,
                    r.conversationLabel, r.messageCount, r.json, events);
            HealthStore.whatsappObserved(this, r.capturedAt);
            if (row != -1) {
                HealthStore.snapshotStored(this, r.capturedAt);
                HealthStore.maybePassTest(this, r.capturedAt, r.messageCount, r.latestMessageAt, recovered);
                if (events.length() > 0) SyncCoordinator.requestSync(this);
            }
            if (recovered) db.addIncident("ACTIVE_SNAPSHOT_RECOVERED", r.notificationKey);
        });
    }

    // shortcutId só existe na Ranking do listener, nunca na StatusBarNotification
    // em si -- por isso é resolvido aqui, no contexto do serviço, e passado como
    // valor simples para o extrator, que não precisa saber de onde ele veio.
    private String lookupShortcutId(StatusBarNotification sbn) {
        if (android.os.Build.VERSION.SDK_INT < 29) return null;
        try {
            RankingMap rankingMap = getCurrentRanking();
            if (rankingMap == null) return null;
            Ranking ranking = new Ranking();
            if (rankingMap.getRanking(sbn.getKey(), ranking)) return ranking.getShortcutId();
        } catch (Exception ignored) { }
        return null;
    }

    private static boolean isWhatsApp(String packageName) {
        return "com.whatsapp".equals(packageName) || "com.whatsapp.w4b".equals(packageName);
    }

    @Override public void onDestroy() {
        heartbeatHandler.removeCallbacks(heartbeat);
        HealthStore.listenerConnected(this, false);
        io.shutdown();
        super.onDestroy();
    }
}
