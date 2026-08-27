# Matriz de testes reais — Moto G84

Use mensagens numeradas para termos ground truth.

| ID | Cenário | Procedimento | Objetivo |
|---|---|---|---|
| T01 | Mensagem simples | Enviar `G01-T01-0001` no Grupo 1 | Confirmar callback + identidade + payload |
| T02 | Sequência | Enviar `G01-T02-0001` até `0010` | Observar snapshots cumulativos/updates |
| T03 | Grupo silenciado | Silenciar Grupo 2 e enviar mensagens numeradas | Saber se o WhatsApp ainda publica notification |
| T04 | Burst | 30–50 mensagens rápidas no Grupo 3 | Medir agrupamento, callbacks e perdas |
| T05 | Multigrupo | Alternar G1/G2/G3 rapidamente | Validar isolamento de conversas |
| T06 | Foreground | Manter WhatsApp aberto enquanto mensagens chegam | Saber se foreground elimina notifications |
| T07 | Tela bloqueada | Tela apagada/bloqueada | Validar cenário principal de appliance |
| T08 | Offline | Sem internet durante captura, quando outbox estiver pronta | Validar local-first e sincronização posterior |
| T09 | Reboot | Reiniciar sem abrir apps e enviar mensagem | Validar reconexão do listener |
| T10 | Mídia | link, imagem+legenda, áudio, PDF, sticker | Mapear metadados observáveis |

## Registro

Para cada teste anote:

- mensagens enviadas;
- snapshots observados;
- quantidade de `EXTRA_MESSAGES`;
- identificador da conversa disponível;
- duplicações aparentes;
- mensagens ausentes;
- comportamento inesperado.
