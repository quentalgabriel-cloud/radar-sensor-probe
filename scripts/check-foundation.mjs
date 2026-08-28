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
const releaseWorkflow = read('.github/workflows/release-apk.yml');

requireMatch(gradle, /versionCode\s+2\b/, 'versionCode da v0.2 deve ser 2.');
requireMatch(gradle, /versionName\s+'0\.2\.0-probe'/, 'versionName deve identificar a v0.2 probe.');
requireMatch(gradle, /RADAR_KEYSTORE_PATH/, 'Build de release deve aceitar a chave permanente.');

rejectMatch(health + listener, /parsedEvent|last_parsed_event/, 'Snapshot bruto não pode ser chamado de evento interpretado.');
requireMatch(listener, /HEARTBEAT_INTERVAL_MS\s*=\s*60_000L/, 'Listener deve publicar heartbeat local a cada 60 segundos.');
requireMatch(activity, /TESTE LOCAL DE CAPTURA/, 'A UI deve identificar o teste como local.');
rejectMatch(activity, /Teste passou/, 'A UI não pode prometer teste ponta a ponta nesta versão.');
rejectMatch(activity, /shortKey|optString\("notification_key"\)/, 'A tela não deve expor a chave técnica da notificação.');
requireMatch(exporter, /parser_version",\s*"not-implemented"/, 'Diagnóstico deve declarar que o parser ainda não existe.');
requireMatch(exporter, /sanitizeIncidents/, 'Incidentes exportados precisam passar pelo sanitizador.');

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

if (failures.length) {
  console.error('Falha na fundação da v0.2:');
  for (const failure of failures) console.error(`- ${failure}`);
  process.exit(1);
}

console.log('Fundação da v0.2 validada.');
