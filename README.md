# Radar Sensor Probe v0.1

APK experimental do **Radar da Rede** para observar, registrar e validar como o WhatsApp oficial publica notificações no Moto G84.

## O que esta versão faz

- usa `NotificationListenerService` oficial do Android;
- processa somente `com.whatsapp`;
- registra cada callback como **NotificationSnapshot**, sem assumir que callback = mensagem;
- inspeciona campos básicos e `MessagingStyle`/`EXTRA_MESSAGES` quando presentes;
- persiste snapshots em SQLite interno antes de qualquer dependência de rede;
- mostra estado do acesso às notificações, listener e atividade do WhatsApp;
- executa teste simples de captura;
- tenta recuperar snapshots ainda ativos quando o listener reconecta;
- exporta diagnóstico **sanitizado** em JSON;
- não envia mensagens, não usa WhatsApp Web, não lê contatos e não acessa o banco do WhatsApp.

## Estado desta build

Esta é uma **Probe build**. O objetivo é aprender como o Moto G84 + versão atual do WhatsApp se comportam antes de consolidar deduplicação, parser final e sincronização Supabase.

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

## Privacidade

Os snapshots ficam no armazenamento interno do aplicativo. A opção **Exportar diagnóstico sanitizado** remove texto e pseudonimiza nomes/títulos antes de gerar o JSON compartilhável.
