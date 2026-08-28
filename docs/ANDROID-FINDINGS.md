# Android Findings

Este documento só deve registrar fatos observados no aparelho.

## VERIFIED

- APK `0.1.0-probe` instalado e executado no Motorola Moto G84 5G com Android 15.
- acesso às notificações concedido e `NotificationListenerService` conectado;
- teste local iniciado em 27/08/2026 às 19:42:33 e primeira notificação observada em aproximadamente 35,2 segundos;
- cinco snapshots locais foram exportados: dois callbacks no primeiro instante observado e três no segundo;
- a notificação detalhada reutilizou a mesma chave e evoluiu de um para dois itens em `EXTRA_MESSAGES`;
- o segundo payload detalhado preservou o item anterior e acrescentou um novo, confirmando comportamento cumulativo;
- callbacks auxiliares/resumo chegaram sem itens em `EXTRA_MESSAGES`;
- `EXTRA_CONVERSATION_TITLE` variou com a quantidade acumulada e não deve ser tratado isoladamente como identidade estável;
- conteúdo textual, timestamp e remetente estavam presentes nos itens detalhados exportados de forma sanitizada.

## INFERRED

- um callback não equivale a uma mensagem;
- o parser candidato deve selecionar snapshots detalhados e emitir apenas o delta ainda não processado;
- a identidade da conversa deve combinar uma referência técnica estável com um rótulo normalizado para apresentação;
- callbacks auxiliares e resumos precisam ser classificados antes de qualquer emissão de `NormalizedEvent`.

## UNKNOWN

- completude da captura em uso prolongado;
- comportamento de grupos silenciados;
- comportamento em burst;
- identidade estável de grupo;
- WhatsApp em foreground;
- granularidade de mídia;
- recuperação após reboot.

## FAILED

- a hipótese “um callback representa uma nova mensagem” foi rejeitada;
- `last_parsed_event_at` na v0.1 representava snapshot persistido, não evento interpretado.
