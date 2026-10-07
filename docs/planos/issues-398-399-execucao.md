# Issues #398 e #399 — bot do Telegram com mais funções e Configurações reorganizadas

## Ponto de situação

**Estado atual:** `Em execução — A05 concluída.`

**Direções escolhidas (2026-10-07):** #399 → **X1 · Aparência e Sistema viram abas** + **X10 · Aparência com prévia
ao vivo** · #398 → **Y1 · resumo diário**, **Y2 · aviso de reinício de cota**, **Y4 · uma conta por vez**,
**Y5 · painel fixado ao vivo**, **Y6 · gráfico das últimas 24 h**, **Y8 · controle remoto**.
Galeria das 20 opções em [`issues-398-399-visual/options.html`](issues-398-399-visual/options.html).

| Campo | Valor |
| --- | --- |
| Modelo | Claude Opus 5.5 (`claude-opus-5-5`) |
| Ferramenta | Claude Code (desktop, aba Code) |
| Data | 2026-10-07 |
| Branch | `feat/398-399-settings-telegram` (criada a partir de `main` em `ec8c8095`) |
| Autor dos commits | identidade temporária da skill `usage-monitor-commit-push`, trailer `Co-Authored-By: Claude Opus 5.5` |

- A alteração pré-existente em `server/package-lock.json` fica fora desta entrega.

## Diagnóstico (evidência)

| # | Ponto | Evidência no código | Estado |
| --- | --- | --- | --- |
| D1 | aba Geral concentra opções (#399) | `SettingsGeneralTab.kt`: 10 controles em 3 painéis; `ThemePresetPicker` com 26 paletas ocupa a maior parte da altura e empurra Sistema e Diagnóstico para fora da primeira tela | confirmado |
| D2 | #398 sem corpo | `gh issue view 398`: só o título "Mais funcionalidades para a integração com Telegram" | confirmado |
| D3 | card do Telegram mora na aba Alertas | `SettingsDialogContent.kt`: `telegramBot?.let { TelegramBotSection(...) }` dentro de `SettingsTab.ALERTS` | confirmado |
| D4 | `sendMessage` não devolve o id da mensagem | `TelegramBotApi.sendMessage` retorna `Unit`; Y5 precisa do id para fixar e editar | confirmado |

## Decisões

- **Exceção à regra "nenhum valor novo em enum existente"** (CLAUDE.md): `SettingsTab.GENERAL` sai e entram
  `APPEARANCE` e `SYSTEM`. O usuário escolheu X1 com a exceção marcada na galeria. `SettingsTab` não é persistido
  nem serializado; só `initialTab` (testes e geradores de captura) o referencia de fora.
- **Y1**: o custo do resumo é das **últimas 24 h** por corte inicial (`sinceEpochMillis`). O repositório CLI só tem
  corte inicial, e a regra proíbe valor novo em `CliSessionRange`. Hora configurável, desligado por padrão; o
  horário de silêncio **adia** o envio para o fim do silêncio, não o pula.
- **Y2**: `UsageAlert.QuotaReset`, subtipo novo do sealed (não é enum). Dispara só para cota que **já tinha
  alertado** na janela anterior (`FiredQuotaWindow.firedPercents` não vazio) quando a janela vira. Vale para a
  bandeja e o bot. `quotaResetAlertsEnabled = true` em `UsageAlertSettings`, default retrocompatível.
- **Y5**: a cada 60 s monta o texto do painel e edita **só se mudou**. Ligar envia e fixa a mensagem; desligar a
  desafixa. O id da mensagem por conversa vai para `telegram.json`.
- **Y8**: `/api` sempre lista; **ligar ou desligar fonte pelo bot exige "Permitir mudar fontes pelo bot", que nasce
  desligado**. Silenciar ganha 1 h, 4 h e "até 08:00" (mesmo `snoozedUntilEpochMillis`). `/atualizar` reusa a
  coleta pedida pelo botão Atualizar do `/status`.
- `callback_data` com prefixo curto (`acc:<índice>`, `api:<ApiSource>`, `snz:<minutos>`), sempre ≤ 64 bytes, nunca
  rótulo nem e-mail.
- Só metadados de uso saem: nunca prompt, resposta, `cwd` ou `session_id`.

## Execução — uma atividade, um commit

| # | Atividade | Arquivos principais |
| --- | --- | --- |
| A00 | Plano, galeria e rodadas X/Y no histórico da skill de opções visuais | este arquivo, `issues-398-399-visual/`, `.claude/skills/usage-monitor-visual-options/SKILL.md` |
| A01 | X1: abas Aparência e Sistema | `SettingsDialogContent.kt`, `SettingsAppearanceTab.kt`, `SettingsSystemTab.kt`, testes, protótipo, `presentation.md` |
| A02 | `feat(settings): live HUD preview on the appearance tab` | `gradlew.bat desktopTest --tests SettingsDialogContentTest` 18/0 falhas (novo: prévia só na Aparência, hover não abre balão); `*Help*` + `ArchitectureRulesTest` 32/0; captura temporária da aba conferida a olho (notch com 3 contas, opacidade 80%) e descartada | concluída |
| A03 | `refactor(telegram): message ids, keyboard rows, pin and photo calls` | `gradlew.bat desktopTest --tests *Telegram*`: 34 testes, 0 falhas (novos: id devolvido e `silent`, linhas do teclado, fixar/desafixar, 400 tipado, `sendPhoto` multipart) | concluída |
| A04 | `feat(telegram): one account at a time with /conta` | `gradlew.bat desktopTest --tests *Telegram*`: 37 testes, 0 falhas (novos: parse `/conta`, `callback_data` curto e sem rótulo, botões e cartão de uma conta, fluxo `/conta` → toque no serviço) | concluída |
| A05 | `feat(telegram): remote control with /atualizar, /api and quiet durations` | `gradlew.bat desktopTest --tests *Telegram*`: 44 testes, 0 falhas (novos: parse dos comandos, ida e volta dos toques e dado forjado, próxima 08:00 BRT, `/api` sem permissão não muda nada, com permissão liga a fonte, menu do `/silencio` e 4 h, `/atualizar`, interruptor do card). Primeira rodada: 2 falhas nos testes novos (lista do menu e `performScrollTo` fora de rolagem), corrigidas no teste | concluída |
| A02 | X10: prévia do notch na Aparência | `SettingsWindowHost.kt`, `SettingsHudPreview.kt`, teste, protótipo, `presentation.md` |
| A03 | Infra do bot: id da mensagem, fixar/desafixar, foto, campos novos de `telegram.json`, tratadores fora do serviço | `TelegramBotApi.kt`, `TelegramBot.kt`, `LocalTelegramSettingsDataSource.kt`, `TelegramBotService.kt` |
| A04 | Y4: `/conta` | domain, `TelegramBotMessages.kt`, tratadores |
| A05 | Y8: `/atualizar`, `/api`, silenciar por duração | tratadores, `AppViewModels.kt`, `TelegramBotSection.kt` |
| A06 | Y2: aviso de reinício de cota | `UsageAlert.kt`, mensagens, `AlertSettingsSection.kt` |
| A07 | Y1: resumo diário e `/resumo` | serviço, mensagens, card |
| A08 | Y5: painel fixado ao vivo | serviço, mensagens, card |
| A09 | Y6: `/grafico` | renderer PNG em `desktopMain`, tratadores |
| A10 | Fechamento: `allTests`, docs, desvios, PR | docs |

## Pontos de situação

| Atividade | Commit | Evidência | Estado |
| --- | --- | --- | --- |
| A00 | `docs: plan issues 398 and 399` | `git diff --cached --stat`: plano, galeria e skill; revisão do diff | concluída |
| A01 | `feat(settings): split the general tab into appearance and system` | `gradlew.bat desktopTest --tests SettingsDialogContentTest DiagnosticsSettingsSectionTest BetaUpdatesToggleTest NetworkSettingsSectionTest ApiKeyDialogTest *Help*`: 55 testes, 0 falhas | concluída |

## Problemas em aberto e riscos

| # | Risco | Estado |
| --- | --- | --- |
| R1 | Janela de Configurações muda (host em `desktopMain`); a suíte só roda no Windows e nada exercita o sistema de janelas. O PR declara que X11 não foi aberto | aberto |
| R2 | Limites do Telegram: edição frequente (Y5) e `sendPhoto` multipart sem teste contra o Telegram real; conferência com bot real é do usuário | aberto |
| R3 | `AppViewModels.kt` (408 linhas) cresce com a fiação do bot; acima de ~700, extrair a fiação | aberto |

## Desvios do plano e achados da execução

- A03: o plano previa regravar `telegram.json` por patch de `JsonObject`. Não se aplica: o arquivo é inteiro do app (sem campos de terceiros, ao contrário do `credentials.json` da Anthropic) e o DTO com defaults já é retrocompatível. Os campos novos entram cada um na atividade que os usa, e não todos na A03.
- A01: a ajuda (`HelpCatalog.kt`, PT e EN) ainda citava "Manter sempre visível", removido com o modo HUD único; saiu junto com a troca de "Geral" por "Aparência"/"Sistema".
