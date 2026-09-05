import { readFileSync } from 'node:fs';

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');
const failures = [];
const requireMatch = (value, pattern, message) => {
  if (!pattern.test(value)) failures.push(message);
};
const rejectMatch = (value, pattern, message) => {
  if (pattern.test(value)) failures.push(message);
};

const gradle = read('app/build.gradle');
const health = read('app/src/main/java/br/com/radardarede/sensor/HealthStore.java');
const listener = read('app/src/main/java/br/com/radardarede/sensor/RadarNotificationListenerService.java');
const activity = read('app/src/main/java/br/com/radardarede/sensor/MainActivity.java');
const exporter = read('app/src/main/java/br/com/radardarede/sensor/DiagnosticExporter.java');
const database = read('app/src/main/java/br/com/radardarede/sensor/ProbeDatabase.java');
const sync = read('app/src/main/java/br/com/radardarede/sensor/SyncCoordinator.java');
const config = read('app/src/main/java/br/com/radardarede/sensor/SensorConfig.java');
const parser = read('app/src/main/java/br/com/radardarede/sensor/SnapshotEventParser.java');
const extractor = read('app/src/main/java/br/com/radardarede/sensor/NotificationSnapshotExtractor.java');
const releaseWorkflow = read('.github/workflows/release-apk.yml');

requireMatch(gradle, /versionCode\s+5\b/, 'versionCode desta build deve ser 5.');
requireMatch(gradle, /versionName\s+'0\.3\.1-shortcut-diagnostic'/, 'versionName deve identificar o diagnóstico de shortcutId/LocusId.');
requireMatch(gradle, /RADAR_KEYSTORE_PATH/, 'Build de release deve aceitar a chave permanente.');

requireMatch(listener, /HEARTBEAT_INTERVAL_MS\s*=\s*60_000L/, 'Listener deve publicar heartbeat local a cada 60 segundos.');
requireMatch(listener, /r\.latestMessageAt, recovered/, 'Teste local deve receber evidência temporal e origem do snapshot.');
requireMatch(health, /CaptureTestEvaluator\.shouldPass/, 'HealthStore deve centralizar o critério do teste local.');
requireMatch(activity, /TESTE LOCAL DE CAPTURA/, 'A UI deve identificar o teste como local.');
rejectMatch(activity, /Teste passou/, 'A UI não pode prometer teste ponta a ponta nesta versão.');
rejectMatch(activity, /shortKey|optString\("notification_key"\)/, 'A tela não deve expor a chave técnica da notificação.');
requireMatch(exporter, /SnapshotEventParser\.VERSION/, 'Diagnóstico deve declarar a versão real do parser.');
requireMatch(exporter, /sanitizeIncidents/, 'Incidentes exportados precisam passar pelo sanitizador.');
requireMatch(database, /DB_VERSION\s*=\s*2/, 'SQLite deve migrar para a outbox sem apagar snapshots anteriores.');
requireMatch(database, /insertSnapshotWithEvents[\s\S]*beginTransaction/, 'Snapshot e outbox devem ser persistidos na mesma transação.');
requireMatch(sync, /BATCH_LIMIT\s*=\s*50/, 'Upload deve usar lotes pequenos e limitados.');
requireMatch(sync, /EventIdentity\.batchId/, 'Retry deve reutilizar identidade determinística do batch.');
requireMatch(config, /deviceSecret\.length\(\)\s*>=\s*32/, 'Credencial de device deve exigir tamanho mínimo.');
requireMatch(parser, /is_group_conversation[\s\S]*isGroupConversation/, 'Parser remoto deve exigir evidência explícita de grupo.');
rejectMatch(gradle + sync + config, /service[_-]?role/i, 'APK não pode receber service role.');

// Diagnóstico de shortcutId/LocusId (docs/GROUP-IDENTITY-PLAN.md, etapa 4):
// captura, mas não decide identidade ainda — a resposta real do WhatsApp a
// essas APIs não está confirmada, e a resolução de grupo não pode depender
// de uma suposição.
requireMatch(extractor, /Build\.VERSION\.SDK_INT >= 29/, 'Captura de shortcutId/LocusId precisa checar a versão do Android.');
requireMatch(extractor, /getShortcutId/, 'Extractor deve capturar o shortcutId da notificação.');
requireMatch(extractor, /getLocusId/, 'Extractor deve capturar o LocusId da notificação.');
requireMatch(parser, /shortcut_id/, 'Parser deve propagar shortcut_id para o metadata do evento.');
rejectMatch(
  parser.slice(0, parser.indexOf('conversationId = "wa_"')),
  /shortcutId|locusId/,
  'shortcutId/LocusId não podem participar do cálculo de conversationId ainda — são só diagnóstico.',
);

for (const secret of [
  'ANDROID_KEYSTORE_BASE64',
  'ANDROID_KEYSTORE_PASSWORD',
  'ANDROID_KEY_ALIAS',
  'ANDROID_KEY_PASSWORD',
]) {
  requireMatch(releaseWorkflow, new RegExp(`secrets\\.${secret}`), `Workflow de release não referencia ${secret}.`);
}
requireMatch(releaseWorkflow, /apksigner" verify/, 'Workflow deve verificar a assinatura do APK.');
requireMatch(releaseWorkflow, /gh release create/, 'Workflow deve publicar uma GitHub Release.');
for (const value of ['RADAR_INGEST_ENDPOINT', 'RADAR_NETWORK_ID', 'RADAR_DEVICE_ID', 'RADAR_DEVICE_SECRET']) {
  requireMatch(releaseWorkflow, new RegExp(value), `Release conectada não referencia ${value}.`);
}

if (failures.length) {
  console.error('Falha na fundação da v0.2.1:');
  for (const failure of failures) console.error(`- ${failure}`);
  process.exit(1);
}

console.log('Fundação conectada da v0.3 validada.');
