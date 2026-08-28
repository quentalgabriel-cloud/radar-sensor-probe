package br.com.radardarede.sensor;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int EXPORT_REQUEST = 404;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView captureStatus, accessStatus, listenerStatus, whatsappStatus, testStatus, recentEvents, probeCount;
    private Button grantAccessButton;
    private String pendingExport;

    private final Runnable refreshLoop = new Runnable() {
        @Override public void run() { refresh(); handler.postDelayed(this, 2000); }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(refreshLoop);
        handler.post(refreshLoop);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(refreshLoop);
        super.onPause();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(Color.rgb(255,253,252));
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            int top;
            int bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                top = bars.top;
                bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(0, top, 0, bottom);
            return insets;
        });
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(40));
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView kicker = text("★  RADAR DA REDE", 13, Color.rgb(198,40,40), true);
        root.addView(kicker);
        TextView title = text("Sensor Probe", 30, Color.rgb(33,29,29), true);
        title.setPadding(0, dp(4), 0, 0);
        root.addView(title);
        TextView subtitle = text("Descoberta e validação da captura do WhatsApp no Moto G84.", 15, Color.rgb(111,102,102), false);
        subtitle.setPadding(0, dp(4), 0, dp(18));
        root.addView(subtitle);

        LinearLayout statusCard = card();
        statusCard.addView(sectionLabel("CAPTURA"));
        captureStatus = text("Verificando…", 22, Color.rgb(33,29,29), true);
        captureStatus.setPadding(0, dp(8), 0, dp(10));
        statusCard.addView(captureStatus);
        accessStatus = statusLine("Acesso às notificações", "—");
        listenerStatus = statusLine("Sensor", "—");
        whatsappStatus = statusLine("WhatsApp", "—");
        statusCard.addView(accessStatus); statusCard.addView(listenerStatus); statusCard.addView(whatsappStatus);
        probeCount = text("0 snapshots locais", 13, Color.rgb(111,102,102), false);
        probeCount.setPadding(0, dp(10), 0, 0);
        statusCard.addView(probeCount);
        root.addView(statusCard);

        grantAccessButton = primaryButton("Permitir acesso às notificações");
        grantAccessButton.setOnClickListener(v -> openNotificationAccess());
        root.addView(grantAccessButton, spaced());

        Button settings = secondaryButton("Configurações do app / bateria");
        settings.setOnClickListener(v -> openAppSettings());
        root.addView(settings, spacedSmall());

        LinearLayout testCard = card();
        testCard.addView(sectionLabel("TESTE LOCAL DE CAPTURA"));
        testStatus = text("Pronto para verificar a chegada de uma notificação ao armazenamento local.", 15, Color.rgb(33,29,29), false);
        testStatus.setPadding(0, dp(8), 0, dp(10));
        testCard.addView(testStatus);
        Button test = primaryButton("Iniciar teste");
        test.setOnClickListener(v -> {
            HealthStore.startTest(this);
            ProbeDatabase.get(this).addIncident("CAPTURE_TEST_STARTED", null);
            refresh();
            Toast.makeText(this, "Agora envie uma mensagem para um dos 3 grupos de teste.", Toast.LENGTH_LONG).show();
        });
        testCard.addView(test);
        root.addView(testCard, spaced());

        LinearLayout eventsCard = card();
        eventsCard.addView(sectionLabel("EVENTOS RECENTES"));
        recentEvents = text("Nenhuma notificação do WhatsApp observada ainda.", 14, Color.rgb(33,29,29), false);
        recentEvents.setTypeface(Typeface.MONOSPACE);
        recentEvents.setPadding(0, dp(8), 0, 0);
        eventsCard.addView(recentEvents);
        root.addView(eventsCard, spaced());

        Button export = secondaryButton("Exportar diagnóstico sanitizado");
        export.setOnClickListener(v -> exportDiagnostics());
        root.addView(export, spacedSmall());

        Button clear = secondaryButton("Limpar dados do Probe");
        clear.setOnClickListener(v -> {
            ProbeDatabase.get(this).clearProbeData();
            Toast.makeText(this, "Dados locais do Probe apagados.", Toast.LENGTH_SHORT).show();
            refresh();
        });
        root.addView(clear, spacedSmall());

        TextView footer = text("V0.2 • Esta versão observa snapshots locais; ainda não interpreta nem envia mensagens ao Radar. O diagnóstico remove conteúdo textual e pseudonimiza identificadores.", 12, Color.rgb(111,102,102), false);
        footer.setPadding(0, dp(24), 0, 0);
        root.addView(footer);
        return scroll;
    }

    private void refresh() {
        boolean access = hasNotificationAccess();
        boolean listener = HealthStore.isListenerConnected(this);
        boolean whatsapp = isWhatsAppInstalled();
        long last = HealthStore.lastWhatsapp(this);
        int count = ProbeDatabase.get(this).snapshotCount();

        accessStatus.setText("Acesso às notificações   " + (access ? "✓ Ativo" : "✕ Pendente"));
        listenerStatus.setText("Sensor                    " + (listener ? "✓ Conectado" : "• Aguardando"));
        whatsappStatus.setText("WhatsApp                  " + (whatsapp ? (last > 0 ? "✓ Atividade detectada" : "✓ Instalado") : "✕ Não encontrado"));
        grantAccessButton.setText(access ? "Revisar acesso às notificações" : "Permitir acesso às notificações");
        probeCount.setText(count + (count == 1 ? " snapshot local" : " snapshots locais"));

        if (!access) {
            captureStatus.setText("Configuração necessária");
            captureStatus.setTextColor(Color.rgb(154,103,0));
        } else if (!listener) {
            captureStatus.setText("Aguardando sensor");
            captureStatus.setTextColor(Color.rgb(154,103,0));
        } else if (last == 0) {
            captureStatus.setText("Pronto para testar");
            captureStatus.setTextColor(Color.rgb(35,122,75));
        } else {
            captureStatus.setText("Captura local observada");
            captureStatus.setTextColor(Color.rgb(35,122,75));
        }

        if (HealthStore.isTestWaiting(this)) {
            testStatus.setText("Aguardando… envie agora uma mensagem para um dos grupos monitorados. Iniciado " + time(HealthStore.testStarted(this)) + ".");
        } else if (HealthStore.testPassed(this) > 0) {
            long latency = HealthStore.testPassed(this) - HealthStore.testStarted(this);
            testStatus.setText("✓ Captura local confirmada. Notificação observada em ~" + Math.max(0, latency/1000.0) + "s.");
        } else {
            testStatus.setText("Pronto para iniciar. O teste confirma somente a chegada de uma nova notificação ao armazenamento local.");
        }

        JSONArray items = ProbeDatabase.get(this).recentSnapshots(8);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.length(); i++) {
            JSONObject o = items.optJSONObject(i);
            if (o == null) continue;
            if (i > 0) sb.append("\n\n");
            sb.append(time(o.optLong("captured_at"))).append("  •  ")
              .append(empty(o.optString("conversation_label")) ? "Conversa não identificada" : o.optString("conversation_label"))
              .append("\n")
              .append(o.optInt("message_count", 0)).append(" mensagens no payload")
              .append("  •  snapshot local");
        }
        recentEvents.setText(sb.length() == 0 ? "Nenhuma notificação do WhatsApp observada ainda." : sb.toString());
    }

    private boolean hasNotificationAccess() {
        ComponentName cn = new ComponentName(this, RadarNotificationListenerService.class);
        if (Build.VERSION.SDK_INT >= 27) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            return nm != null && nm.isNotificationListenerAccessGranted(cn);
        }
        String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return enabled != null && enabled.contains(cn.flattenToString());
    }

    private boolean isWhatsAppInstalled() {
        try {
            getPackageManager().getPackageInfo("com.whatsapp", 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) { return false; }
    }

    private void openNotificationAccess() {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                Intent i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS);
                i.putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                        new ComponentName(this, RadarNotificationListenerService.class));
                startActivity(i);
            } else startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        } catch (Exception e) { startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
    }

    private void openAppSettings() {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()));
        startActivity(i);
    }

    private void exportDiagnostics() {
        pendingExport = DiagnosticExporter.buildSanitized(this);
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, "radar-sensor-diagnostico-" + System.currentTimeMillis() + ".json");
        startActivityForResult(i, EXPORT_REQUEST);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == EXPORT_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null && pendingExport != null) {
            try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                if (out != null) out.write(pendingExport.getBytes(StandardCharsets.UTF_8));
                Toast.makeText(this, "Diagnóstico exportado.", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Não foi possível exportar: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
            pendingExport = null;
        }
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18), dp(16), dp(18), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE); bg.setCornerRadius(dp(18)); bg.setStroke(dp(1), Color.rgb(235,229,227));
        c.setBackground(bg); c.setElevation(dp(2));
        return c;
    }

    private TextView sectionLabel(String value) { return text(value, 12, Color.rgb(198,40,40), true); }
    private TextView statusLine(String label, String value) {
        TextView t = text(label + "   " + value, 14, Color.rgb(33,29,29), false);
        t.setPadding(0, dp(5), 0, dp(5)); return t;
    }
    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(sp); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); t.setLineSpacing(0f, 1.12f); return t;
    }
    private Button primaryButton(String value) {
        Button b = new Button(this); b.setText(value); b.setTextColor(Color.WHITE); b.setTextSize(14); b.setAllCaps(false);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.rgb(198,40,40)); bg.setCornerRadius(dp(14)); b.setBackground(bg);
        b.setPadding(dp(16), dp(8), dp(16), dp(8)); return b;
    }
    private Button secondaryButton(String value) {
        Button b = new Button(this); b.setText(value); b.setTextColor(Color.rgb(33,29,29)); b.setTextSize(14); b.setAllCaps(false);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.WHITE); bg.setCornerRadius(dp(14)); bg.setStroke(dp(1), Color.rgb(220,211,208)); b.setBackground(bg);
        b.setPadding(dp(16), dp(8), dp(16), dp(8)); return b;
    }
    private LinearLayout.LayoutParams spaced() { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(0, dp(14), 0, 0); return p; }
    private LinearLayout.LayoutParams spacedSmall() { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(0, dp(9), 0, 0); return p; }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private String time(long ms) { return ms <= 0 ? "—" : new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(ms)); }
    private boolean empty(String s) { return TextUtils.isEmpty(s) || "null".equalsIgnoreCase(s); }
}
