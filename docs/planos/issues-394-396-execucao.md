# Issues #394 e #396 — web local animada e bot do Telegram

## Ponto de situação

**Estado atual:** `Em execução na branch.`

**Direções escolhidas (2026-10-06):** #394 → **U5 · Paridade + próxima leitura** · #396 → **V2 · Três passos
numerados** (card nas Configurações) + **W5 · W1 + botões na mensagem** (respostas do bot).
Galeria das 18 opções em [`issues-394-396-visual/options.html`](issues-394-396-visual/options.html)
(abrir por HTTP local: o painel de arquivo não roda o JS).

| Campo | Valor |
| --- | --- |
| Modelo | Claude Opus 5.5 (`claude-opus-5-5`) |
| Ferramenta | Claude Code (desktop, aba Code) |
| Data | 2026-10-06 |
| Branch | `feat/issues-394-396` (criada a partir de `main` em `937d47cd`) |
| Autor dos commits | skill `usage-monitor-commit-push`, trailer `Co-Authored-By: Claude Opus 5.5` |

- A alteração pré-existente em `server/package-lock.json` fica fora desta entrega.

## Diagnóstico (evidência)

| # | Problema relatado | Evidência no código | Estado |
| --- | --- | --- | --- |
| D1 | web mostra "sem coleta ainda" e o `/status` "coleta —" com a coleta andando | `lastCollectedAt` = maior `ApiUsageStats.fetchedAt` (`UsageSnapshot.kt:21`). Só a restauração do cache (`DashboardCacheMapper.kt:92`) e a retenção após falha (`statsRetainedAfterFailure`) preenchem o campo; `applyFetchResult` publica a leitura ao vivo com `fetchedAt = null`. A cópia de `DashboardViewModel.kt:686` só vai para o arquivo de cache | confirmado |
| D2 | web sem as animações da HUD | `web/hud.html` desenha o anel uma vez com fase fixa `.3`; nada anima | confirmado |
| D3 | `/start <código>` difícil de copiar | `TelegramBotSection.kt:124`: texto corrido, sem ação | confirmado |
| D4 | mensagens do bot mal formatadas | `TelegramBotApi.sendMessage` manda texto puro, sem `parse_mode`; `/status` junta as cotas numa linha com ` · ` (`TelegramBotMessages.kt:26`) | confirmado |
| D5 | conversa de exemplo confusa | `TelegramBotSection` repete três trocas em texto cru numa coluna estreita; a linha da cota quebra no meio | confirmado |

## Execução — uma atividade, um commit

| # | Atividade | Arquivos principais |
| --- | --- | --- |
| A01 | Plano e galeria | este arquivo, `issues-394-396-visual/` |
| A02 | Carimbar `fetchedAt` na coleta ao vivo (D1) | `DashboardViewModel.kt`, teste em `commonTest` |
| A03 | Web U5: plasma/disco, cometa de sessão, ondas R1, horizonte D5, filete até a próxima releitura, "há N s" (D2) | `web/hud.html`, `docs/presentation.md`, protótipo, skill |
| A04 | Bot W1: `parse_mode: HTML`, cartão por conta com barra, `/ajuda` com `<code>`, `setMyCommands` (D4) | `TelegramBotApi.kt`, `TelegramBotMessages.kt`, `TelegramBotService.kt`, `docs/integrations.md` |
| A05 | Bot W5: teclado inline (Atualizar, Silenciar 1h, Limiares), `callback_query`, `editMessageText`, silêncio temporário | domain `UsageAlertSettings`, `UsageAlertPreferences`, `TelegramBotService.kt`, `AppViewModels.kt` |
| A06 | Card V2: três passos, Copiar, "Abrir no Telegram" (`getMe`), exemplo recolhido (D3, D5) | `TelegramBotSection.kt`, `TelegramBotActions.kt`, protótipo, design system |

## Pontos de situação

| Atividade | Commit | Evidência | Estado |
| --- | --- | --- | --- |
| A01 | `docs: plan issues 394 and 396` | plano e galeria gravados | concluída |
| A02 | `fix(dashboard): stamp fetchedAt on live readings` | `gradlew.bat desktopTest --tests "com.usagemonitor.presentation.*" --tests "com.usagemonitor.domain.*" --tests "com.usagemonitor.data.*"` verde; teste novo `a live reading carries the collection instant`; dois testes que comparavam a leitura por igualdade passaram a ignorar `fetchedAt` | concluída |
| A03 | `feat(web): animate the local web HUD like the desktop HUD` | navegador do app contra `/api/snapshot` simulado (`stub_server.py`, rótulo com `<b>` para conferir o escape): ondas, cometa, horizonte no número e ponto dourado visíveis em captura, console sem erro; `gradlew.bat desktopTest --tests "com.usagemonitor.LocalWebAccessServiceTest"` verde | concluída |
| A04 | `feat(telegram): format bot replies as HTML cards with a command menu` | `gradlew.bat desktopTest --tests "com.usagemonitor.data.TelegramBotApiTest" --tests "com.usagemonitor.presentation.TelegramBotMessagesTest" --tests "com.usagemonitor.TelegramBotServiceTest" --tests "com.usagemonitor.ui.TelegramBotSectionTest"` verde; testes novos de formato, escape, barra, `parse_mode` e `setMyCommands` | concluída |

## Problemas em aberto e riscos

| # | Risco | Estado |
| --- | --- | --- |
| R1 | "Silenciar 1h" exige estado novo (silêncio até um instante); campo novo com default em `UsageAlertSettings`, nunca valor novo em enum | aberto |
| R2 | "Atualizar" pede coleta ao app: precisa respeitar o backoff de 429 do `DashboardViewModel` e não pode prender o long polling | aberto |
| R3 | `parse_mode: HTML` exige escapar `<`, `>` e `&` de todo texto variável (rótulo de conta é do usuário) — senão o Telegram recusa a mensagem com 400 | fechado em A04 — `TelegramBotMessages.escape`, coberto por teste com rótulo `<Padrão>` |
| R4 | JS da página web não roda na suíte; conferência só por navegador com `/api/snapshot` simulado | aceito — conferido em A03; `prefers-reduced-motion` só lido no código, sem emulação no navegador do app |
| R5 | Nenhum teste exercita sistema de janelas (#342): o card V2 só é verificado por teste de componente | aceito |

## Desvios do plano e achados da execução

—
