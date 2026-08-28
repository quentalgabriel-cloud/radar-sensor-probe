# Arquitetura — Probe v0.2

```text
WhatsApp oficial
      ↓
NotificationListenerService
      ↓
NotificationSnapshot (imutável)
      ↓
SQLite interno
      ↓
Probe UI / diagnóstico
```

Próxima evolução, apenas após fixtures reais:

```text
NotificationSnapshot
      ↓
WhatsApp Parser
      ↓
Diff / Dedup
      ↓
NormalizedEvent
      ↓
Durable Outbox
      ↓
Supabase Ingestion
```

A decisão essencial é não chamar cada callback de "mensagem" antes dos testes no aparelho. A v0.2 também diferencia **captura local confirmada** de um futuro teste ponta a ponta, que só existirá depois de parser, outbox e ingestão estarem implementados.
