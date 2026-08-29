# Prompt de execução — Onda 1 / Ponte Conectada

Atue como Product Engineer sênior Android responsável por evoluir o Radar
Sensor Probe v0.2.1 para a v0.3 conectada, preservando a evidência obtida no
Moto G84 e reutilizando os contratos de ingestão já publicados no Supabase.

## Resultado obrigatório

Uma nova mensagem com evidência válida em `Notification.EXTRA_MESSAGES` deve:

1. ser preservada como snapshot local;
2. ser interpretada conservadoramente como `NormalizedEvent 0.1.0`;
3. entrar na mesma transação SQLite em uma outbox durável;
4. ser enviada em lote a `/ingest-events` com bearer exclusivo do device;
5. permanecer pendente e ser reenviada com backoff após falha;
6. não duplicar evento nem batch em retries;
7. atualizar `/ingest-health` com último contato, fila e último upload;
8. aparecer na UI como captura local, sincronização pendente ou sincronizada.

## Restrições

- Não usar service role, senha humana ou chave Supabase administrativa no APK.
- Não criar schema, endpoint, SDK, painel de devices ou integração WhatsApp Web.
- Não inferir mensagem a partir de callback sem payload textual de mensagem.
- Não sincronizar conversa individual; o indicador Android de grupo deve ser explícito.
- Não apagar snapshot ou item de outbox antes da confirmação HTTP 2xx.
- Não bloquear a thread principal com SQLite pesado ou rede.
- Não depender apenas da memória: fila e estado de envio sobrevivem ao processo.
- Não expor token, texto ou identificadores brutos no diagnóstico sanitizado.
- Uma build sem configuração deve continuar operando como probe local e dizer
  claramente que ainda não está provisionada.

## Estratégia mínima

- Java 17 + APIs Android existentes; `HttpURLConnection` e SQLite interno.
- Configuração de release via `BuildConfig` alimentada por secrets/variables CI.
- Endpoint, network ID e device ID não são credenciais; bearer é revogável e
  escopado no servidor ao par device/network.
- IDs de eventos e batches são determinísticos para retries idempotentes.
- Parser em versão `0.3.0`, aceitando somente mensagens textuais explícitas.
- Lote máximo inicial de 50 eventos; heartbeat remoto no ciclo de 60 segundos.
- Backoff exponencial limitado, retomado por heartbeat ou nova notificação.

## Critérios de aceite

- Testes unitários cobrem IDs determinísticos, parser conservador e backoff.
- Migração SQLite preserva snapshots da v0.2.1.
- Build, lint, testes e verificador estrutural passam.
- Release CI exige assinatura e configuração conectada completa.
- README e diagnóstico distinguem local, pendente e sincronizado.
- Nenhuma mudança no backend ou no Radar Web nesta onda.

## Definition of done

O código da v0.3 está pronto para receber as quatro configurações do device,
gerar APK assinado e executar o teste físico Moto G84 → Supabase. A onda só é
considerada validada em campo quando o Supabase confirmar evento e heartbeat;
compilar o APK não é evidência ponta a ponta.
