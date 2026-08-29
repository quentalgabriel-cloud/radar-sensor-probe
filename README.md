# Radar Sensor v0.3.0

APK do **Radar da Rede** que preserva a captura local do WhatsApp e prepara sua sincronização autenticada com o backend.

## O que esta versão faz

- usa `NotificationListenerService` oficial do Android;
- processa somente `com.whatsapp` e `com.whatsapp.w4b`;
- registra cada callback como **NotificationSnapshot**, sem assumir que callback = mensagem;
- inspeciona campos básicos e `MessagingStyle`/`EXTRA_MESSAGES` quando presentes;
- interpreta somente mensagens textuais explícitas, temporalmente válidas e marcadas como grupo;
- persiste snapshot e evento na mesma transação SQLite;
- mantém outbox durável com retry, backoff e IDs idempotentes;
- envia lotes autenticados a `ingest-events` e saúde a `ingest-health` quando provisionado;
- limita a retenção local a sete dias, 500 snapshots e 500 incidentes recentes;
- mostra estado do acesso às notificações, listener e atividade do WhatsApp;
- executa teste local de captura sem afirmar que houve interpretação ou sincronização;
- só conclui o teste com payload de mensagem recente, ignorando notificações auxiliares e recuperações antigas;
- tenta recuperar snapshots ainda ativos quando o listener reconecta;
- exporta diagnóstico **sanitizado** em JSON;
- não envia mensagens, não usa WhatsApp Web, não lê contatos e não acessa o banco do WhatsApp.

## Estado desta build

Esta é a primeira **build conectada**. O parser permanece deliberadamente conservador: callbacks auxiliares, snapshots recuperados e payloads sem mensagem textual datada continuam locais e não viram eventos remotos.

As observações no Moto G84 e em um Samsung SM-A075M confirmaram payload cumulativo, snapshots antigos após reconexão e callbacks auxiliares. Por isso, os IDs usam a evidência da mensagem, e não o callback, para impedir duplicações.

Uma build sem as quatro configurações abaixo continua funcionando como probe local e informa que ainda não está conectada. Compilar não substitui o teste físico Moto G84 → Supabase.

## Build local

Requisitos: JDK 17, Android SDK 35, Build Tools 35.0.0 e Gradle 8.9.

```bash
gradle :app:assembleDebug
```

APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Build no GitHub

O workflow `.github/workflows/build-apk.yml` gera automaticamente o APK de debug e publica o arquivo como artifact do GitHub Actions.

Tags compatíveis com o `versionName` acionam `.github/workflows/release-apk.yml`. O workflow de release exige uma chave permanente nos GitHub Actions Secrets, valida a assinatura e publica APK + SHA-256 em uma GitHub Release.

Secrets obrigatórios:

```text
ANDROID_KEYSTORE_BASE64
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
RADAR_DEVICE_SECRET
```

Variables obrigatórias da release conectada:

```text
RADAR_INGEST_ENDPOINT
RADAR_NETWORK_ID
RADAR_DEVICE_ID
```

`RADAR_DEVICE_SECRET` é uma credencial revogável e limitada ao device. Nenhuma service role, senha humana ou chave administrativa entra no aplicativo.

## Privacidade

Os snapshots ficam no armazenamento interno do aplicativo. A opção **Exportar diagnóstico sanitizado** remove texto e pseudonimiza nomes/títulos antes de gerar o JSON compartilhável.
