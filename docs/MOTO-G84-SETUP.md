# Moto G84 — configuração do Radar Sensor Probe

## Antes de instalar

1. Confirme que o WhatsApp oficial padrão está funcionando com o número dedicado.
2. Deixe os três grupos de laboratório existentes.
3. Durante a primeira bateria, não vincule WhatsApp Web/Desktop.
4. Mantenha Wi‑Fi estável e dados móveis disponíveis como contingência.
5. Desative Economia de bateria durante os testes.
6. Em bateria/uso em segundo plano, deixe WhatsApp e Radar Sensor como **Sempre permitir**, quando essa opção estiver disponível.

## Instalação

1. Baixe o APK da seção **Releases** do GitHub e confira se a versão é a esperada.
2. Autorize temporariamente a instalação por essa fonte se o Android pedir.
3. Abra **Radar Sensor**.
4. Toque **Permitir acesso às notificações**.
5. Na tela do Android, habilite **Radar Sensor**.
6. Volte ao app.
7. Confirme que aparecem:
   - Acesso às notificações: Ativo
   - Sensor: Conectado
   - WhatsApp: Instalado

### Transição única da v0.1

A v0.1 de Victor foi uma build de debug com assinatura temporária. Depois que a release assinada v0.2 estiver disponível:

1. exporte o último diagnóstico da v0.1;
2. desinstale a v0.1;
3. instale a v0.2 pela seção **Releases**;
4. conceda novamente o acesso às notificações e revise a bateria.

Não será necessário desinstalar nas versões seguintes enquanto a mesma chave permanente for preservada.

## Primeiro teste

1. Toque **Iniciar teste**.
2. Sem abrir o WhatsApp no Moto G84, envie de outro aparelho uma mensagem para o Grupo Teste 1.
3. Aguarde alguns segundos.
4. O aplicativo deve mostrar **Captura local confirmada** e adicionar um snapshot em **Eventos recentes**.
5. Se não passar, exporte o diagnóstico sanitizado e envie para a equipe técnica.

Esse teste confirma WhatsApp → listener Android → armazenamento local. Ele ainda não confirma interpretação, upload ou chegada ao Radar Web.
