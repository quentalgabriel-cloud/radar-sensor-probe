# Radar Sensor Probe v0.2.1

APK experimental do **Radar da Rede** para observar, registrar e validar como o WhatsApp oficial publica notificações no Moto G84.

## O que esta versão faz

- usa `NotificationListenerService` oficial do Android;
- processa somente `com.whatsapp`;
- registra cada callback como **NotificationSnapshot**, sem assumir que callback = mensagem;
- inspeciona campos básicos e `MessagingStyle`/`EXTRA_MESSAGES` quando presentes;
- persiste snapshots em SQLite interno antes de qualquer dependência de rede;
- limita a retenção local a sete dias, 500 snapshots e 500 incidentes recentes;
- mostra estado do acesso às notificações, listener e atividade do WhatsApp;
- executa teste local de captura sem afirmar que houve interpretação ou sincronização;
- só conclui o teste com payload de mensagem recente, ignorando notificações auxiliares e recuperações antigas;
- tenta recuperar snapshots ainda ativos quando o listener reconecta;
- exporta diagnóstico **sanitizado** em JSON;
- não envia mensagens, não usa WhatsApp Web, não lê contatos e não acessa o banco do WhatsApp.

## Estado desta build

Esta é uma **Probe build**. O objetivo é aprender como o Moto G84 + versão atual do WhatsApp se comportam antes de consolidar deduplicação, parser final e sincronização Supabase.

As observações no Moto G84 e em um Samsung SM-A075M confirmaram que notificações detalhadas podem carregar payload cumulativo, snapshots antigos podem reaparecer após reconexão e uma mesma atividade pode gerar callbacks auxiliares. Por isso, esta versão continua deliberadamente sem emitir `NormalizedEvent`.

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
```

## Privacidade

Os snapshots ficam no armazenamento interno do aplicativo. A opção **Exportar diagnóstico sanitizado** remove texto e pseudonimiza nomes/títulos antes de gerar o JSON compartilhável.
