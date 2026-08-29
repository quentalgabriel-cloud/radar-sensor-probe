# Arquitetura — Sensor conectado v0.3

```text
WhatsApp oficial
      ↓
NotificationListenerService
      ↓
NotificationSnapshot (imutável)
      ↓
Parser conservador
      ↓
NormalizedEvent 0.1.0
      ↓
Transação SQLite: snapshot + outbox
      ↓
HTTPS autenticado / retry idempotente
      ↓
Supabase ingest-events + ingest-health
```

Quando a build ainda não foi provisionada, o caminho termina no snapshot local,
preservando o comportamento de diagnóstico da v0.2.1.

## Regras de emissão

- somente `EXTRA_MESSAGES` com texto e timestamp válidos vira evento;
- payload cumulativo é deduplicado por ID determinístico da evidência;
- batch também tem ID determinístico, seguro para repetir após timeout;
- envio HTTP 2xx marca os itens como enviados; qualquer outra resposta mantém a fila;
- bearer pertence apenas ao device/network cadastrados e pode ser revogado;
- heartbeat remoto informa fila, último evento e último upload a cada ciclo útil.

A v0.3 diferencia **captura local**, **evento aguardando**, **sincronizado** e
**build não provisionada**. O teste local continua local; o teste ponta a ponta
só será confirmado quando o Supabase registrar o evento e o heartbeat do Moto.
