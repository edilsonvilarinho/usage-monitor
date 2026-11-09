# Configurações › Time em etapas, chave por e-mail — issue #551

Procedência: Claude Opus 5.5 (orquestradora), Claude Code CLI no Windows 11, 2026-11-09, branch
`feat/issue-551-team-steps`, commits com a identidade `claude <claude@anthropic.com>`. Implementação pelos agentes
`backend-dev`/`frontend-dev` (modelo no frontmatter de `.claude/agents/`).

## Objetivo

Depois da #549 a seção Time ficou um formulário longo (nome, servidor, chave do time, apelido, contas com campo de chave
dentro da linha, teste, admin, remover) e o dono a achou complexa. Além disso o modelo mental certo, dito pelo dono, é
**a chave é do e-mail**: um servidor tem N e-mails, cada um com a sua chave. A #551 reorganiza a seção na direção
**AN2 · Etapas** (rodada AN, `prototipos/issue-551/index.html`) e tira o campo "Chave do time" da tela.

## Fatos confirmados (2026-11-09, `cb72b244`)

- `TeamIntegrationSettings.apiKeyFor` (#549) cai na chave do time sempre que a conta não tem a sua — uma conta recém-
  ligada herdaria a chave de outra pessoa (o servidor recusa por rótulo de e-mail, 403).
- `SettingsActions.changeTeamProfileParticipation` (`:386`) liga/desliga a conta; `changeTeamAccountKey` (#549) grava a
  chave própria.
- `TeamSyncService.kt:330` só filtra por `participates`; não há guarda para conta sem chave.
- `TeamIntegrationSection.kt` 634 linhas; `AppExpandable` existe em `AppStates.kt:562` (bloco recolhível).
- Escolha do dono: AN2 (2026-11-09), casos A/B/C no protótipo.

## Decisões

- **Modo legado preservado (zero impacto):** enquanto `accountApiKeys` está vazio, `apiKeyFor` cai na chave do time
  como hoje. Assim que existe ao menos uma chave por conta, **não há mais queda**: conta sem chave própria fica sem chave.
- **Materialização:** a primeira edição de chave ou a primeira conta ligada pela tela nova copia a chave do time para
  cada conta já participante sem chave própria (`withOwnKeysMaterialized()`), e só então aplica a mudança. A chave do
  time continua gravada (downgrade lê dela).
- **Conta ligada sem chave não envia nada** (ingest, presença, `claim`): status escrito "falta a chave".
- **Tela (AN2):** 1 Conexão (nome, servidor, apelido, Testar conexão, status), 2 Contas (nome, e-mail, chave mascarada
  com olho, status escrito, interruptor; conta ligada sem chave mostra campo "Cole a chave deste e-mail"), 3
  Administração (bloco admin atual). Bloco fechado mostra uma linha de resumo. Abre em Contas se o servidor está
  preenchido, senão em Conexão. "Chave do time" some da tela; o campo da conta mostra `apiKeyFor` (no legado, a chave
  antiga).
- Sem token, primitiva ou valor de enum novo: blocos com `AppExpandable` e primitivas existentes.

## Atividades

| Id | O quê | Dono |
|---|---|---|
| A01 | Este plano | Neferpitou |
| A02 | Domain: queda só no legado, `withOwnKeysMaterialized()`, `isConfigured` | backend-dev |
| A03 | Envio, presença e `claim` pulam conta sem chave; status "falta a chave" | backend-dev |
| A04 | `SettingsActions`: ligar conta e gravar chave materializam o legado antes | backend-dev |
| A05 | Tela em etapas (AN2) + protótipo `#cfg-time`, `Settings.jsx` e tabela da rodada AN na skill | frontend-dev |
| A06 | `docs/team-integration.md` e `docs/presentation.md` | Neferpitou |

## Ponto de situação

Cada linha é escrita no commit da atividade que descreve, com o comando executado e o resultado.

| Atividade | Alteração | Comando e resultado |
|---|---|---|
| A01 | Plano com fatos, decisões e atividades | `gh issue create` → #551; galeria AN aprovada pelo dono (AN2) |
| A02 | `apiKeyFor` cai na chave do time só no legado (mapa vazio); `withOwnKeysMaterialized()`; `missingKeyProfileIds()`; `isConfigured` = ao menos uma chave utilizável; `TeamSyncService` pula conta ligada sem chave (absorvido do A03 para o commit ficar verde); testes da #549 que afirmavam a queda com mapa não vazio reescritos para o contrato novo | vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.domain.TeamIntegrationSettingsTest"` → 7 de 19 na asserção (ex.: `:47` ComparisonFailure, conta sem entrada herdava a chave do time; `:153` materialização); `--tests TeamSyncServiceTest` → `:499` AssertionError (conta sem chave enviava); verde: `gradlew.bat desktopTest --tests "com.usagemonitor.domain.*" --tests "com.usagemonitor.TeamSyncServiceTest" --tests "com.usagemonitor.data.*" --tests "com.usagemonitor.Routed*"` → 1285 testes, 0 falhas |
| A03 | Testar conexão (app e `team join`): conta ligada sem chave não faz `claim` e vira recusa da linha "falta a chave desta conta" (app por `localized`; terminal em PT como as demais recusas dele) | vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.AppSettingsFeedbackTest" --tests "com.usagemonitor.terminal.*"` → `TerminalTeamCheckTest.kt:148` AssertionError, `AppSettingsFeedbackTest.kt:258 expected:<FAILED> but was:<OK>`; teste da #549 que afirmava herança (`TerminalTeamCheckTest.kt:173`) reescrito; verde: `--tests AppSettingsFeedbackTest --tests "com.usagemonitor.terminal.*" --tests "com.usagemonitor.architecture.*"` → 224 testes, 0 falhas |
| A04 | `SettingsActions`: ligar conta e gravar/apagar chave aplicam `withOwnKeysMaterialized()` antes (legado vira chaves explícitas sem perder nenhuma; `apiKey` nunca apagado) | vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.SettingsActionsTest"` → `:462 expected:<{default=chave-antiga}> but was:<{}>`, `:514 expected:<{default=chave-nova, work=chave-antiga}> but was:<{default=chave-nova}>` (2 guardas já passavam); verde: `--tests SettingsActionsTest --tests "com.usagemonitor.ui.*"` → 692 testes, 0 falhas |
| A05a | Tela AN2: `TeamIntegrationSteps.kt` (3 blocos `AppExpandable` numerados com ▸/▾ e resumo — status igual ao da lista, "N de M ligadas", "Modo admin"/"Desligado"); nome, servidor, apelido e teste no bloco 1; contas com e-mail, chave (`apiKeyFor`) e status escrito no bloco 2; admin no bloco 3; campo "Chave do time" e `onApiKeyChange` removidos; abre em Conexão com servidor vazio ou última conexão falhou, senão Contas. Devolvido 2× (abria Administração com token de admin; captura divergia do AN2) | vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.ui.TeamIntegrationSectionTest"` → `:217` campo "Chave do time" presente, `:224` "falta a chave" ausente, `:237` campo da conta vazio no legado, `:292` falha não abria Conexão, `:301` admin não abria Contas, `:141` resumo com status diferente da lista; verde: `--tests "com.usagemonitor.ui.*" --tests "com.usagemonitor.architecture.*" --tests SettingsActionsTest` → 722 testes, 0 falhas; captura real `gradlew.bat generateScreenshots` (cena `settingsTeam`) conferida |
| A05b | Aviso "Chave salva" (e falha "chave", sem "do time"); Ajuda PT/EN cita "Chave da conta" no bloco Contas; captura `settingsTeam` 610 → 680 dp; registro da tela no protótipo `#cfg-time`, `Settings.jsx` e linha da rodada AN na skill `usage-monitor-visual-options` (o registro da tela entrou um commit depois da A05a) | vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.presentation.*"` → `SettingsToastMessageTest.kt:71 expected:<Chave []salva> but was:<Chave [do time ]salva>`, `HelpCatalogTest.kt:163 passo sem "Chave da conta" no bloco "Contas" em PT`; verde: `--tests "com.usagemonitor.ui.*" --tests "com.usagemonitor.presentation.*" --tests "com.usagemonitor.architecture.*"` → 1777 testes, 0 falhas; protótipo conferido em render headless do Edge |
| A06 | `docs/team-integration.md`, seção "Chave por e-mail e tela em etapas (#551)" | sem harness (documentação); conferido contra os commits A02–A05b |

## Problemas em aberto e riscos

- Downgrade depois da materialização: a versão anterior usa a chave do time para todas as contas ligadas (o que já fazia).
- CLI (`team join`) e TUI continuam com a chave do time.
