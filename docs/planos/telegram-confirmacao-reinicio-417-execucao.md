# Confirmação do reinício pedido pelo bot — issue #417

## Problema

Depois de tocar em "⬆ Atualizar e reiniciar o app" no Telegram, o bot não diz que o app reiniciou nem
mostra as novidades (captura da issue: os avisos da `41.11.0-beta.2` e da `beta.3` sem nenhuma resposta
depois, só o `/status` pedido à mão).

### Fatos confirmados no código

- O toque (`TelegramBotHandlers.kt`, `BotTap.UpdateNow`) responde só com `answerCallbackQuery` — aviso
  efêmero, "Reiniciando o app para instalar…" — e chama `readings.applyUpdate()`, que encerra o processo.
- Nada é gravado dizendo que o reinício foi pedido pelo bot: o app novo sobe sem saber que uma conversa
  espera resposta.
- O próprio aviso (`TelegramBotChatMessages.updateReady`) manda as novidades para "a janela do app depois
  do reinício"; a janela (`ReleaseNotesController`) só existe na UI.

## Decisões

1. **Gatilho:** só o reinício pedido pelo bot. A atualização aplicada pela faixa do app não manda nada.
2. **Marca `pendingRestart`** em `TelegramBotPreferences` (`telegram.json`), com a versão pedida e a de
   origem, gravada **antes** de `applyUpdate()`. Estado ao lado de `announcedUpdateVersion`; campo com
   default, o arquivo antigo continua valendo.
3. **Quando confirmar:** depois do ACK de atualização (`anchorAppWindow`). Antes do ACK o updater do Linux
   ainda pode restaurar a versão anterior (`ReleaseNotes.kt`). A marca é limpa **antes** do envio, como o
   aviso de versão pronta: falha no envio perde a confirmação em vez de repeti-la a cada abertura.
4. **Desfecho** por função pura (`telegramRestartOutcome`, `compareAppVersions`): versão em execução ≥
   pedida confirma a instalação com as novidades (os mesmos itens `feat`/`fix` da janela, por
   `GetReleaseNotesUseCase`, até 10 e "+N" com o link); menor diz que a atualização não foi aplicada.
   Busca das notas falhando: confirma sem a lista e aponta a janela do app. Lista vazia: sem lista.
5. **Destino:** todas as conversas pareadas (`broadcast`): o aviso com o botão foi para todas.
6. **Texto:** `localized()` em todo texto novo (catraca `INLINE_LANGUAGE_CHECKS_CEILING`).

### Fora do alcance

- App que não reabre (instalador falhou e nada relança) não confirma; a confirmação sai na abertura
  seguinte, com o desfecho real.
- Itens das novidades em inglês: são os assuntos de commit, iguais aos da janela do app.
- Nenhum teste com bot real: a API do Telegram é falsa nos testes.

## Atividades

| Item | O que faz |
|---|---|
| T1 | Domínio: `TelegramPendingRestart`, `telegramRestartOutcome`; `pendingRestart` nas preferências e no `telegram.json` |
| T2 | Handler grava a marca antes de `applyUpdate()`, só no toque que reinicia |
| T3 | Serviço confirma no arranque, depois do ACK, com as novidades |
| T4 | `docs/integrations.md`, protótipo (12b-2) e `allTests` |

## Ponto de situação

Cada linha é escrita no commit da atividade que descreve, com o comando executado e o resultado.

| Atividade | Alteração | Comando e resultado |
|---|---|---|
| T1 | `TelegramRestartConfirmation.kt` (domínio): `TelegramPendingRestart(targetVersion, fromVersion)`, `TelegramRestartOutcome` (`UPDATED`/`NOT_APPLIED`) e `telegramRestartOutcome` por `compareAppVersions` — a estável conta como mais nova que as betas da série. `TelegramBotPreferences.pendingRestart` com DTO próprio no `telegram.json`, default nulo | `gradlew.bat desktopTest --tests "com.usagemonitor.domain.TelegramRestartConfirmationTest" --tests "com.usagemonitor.data.LocalTelegramSettingsDataSourceTest" --tests "com.usagemonitor.architecture.*"`: 16 testes, 0 falhas |
| T2 | Toque `upd:<versão>` com desfecho `STARTED` grava `pendingRestart` (versão pedida e `readings.currentVersion()`, nova leitura com default `CURRENT_APP_VERSION`) e só então chama `applyUpdate()`. `STALE` e `NOT_ALLOWED` não gravam. O teste captura o que estava gravado no instante do `applyUpdate` | `gradlew.bat desktopTest --tests "com.usagemonitor.TelegramBotServiceTest" --tests "com.usagemonitor.architecture.*"`: 46 testes, 0 falhas |
