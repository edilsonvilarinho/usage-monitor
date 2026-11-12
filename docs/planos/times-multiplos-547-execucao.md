# Vários times ao mesmo tempo — issue #547

Procedência: Claude Opus 5.5 (orquestradora), Claude Code CLI no Windows 11, 2026-11-09, branch
`feat/issue-547-multiple-teams`, commits com a identidade `claude <claude@anthropic.com>`. Implementação pelos agentes
`backend-dev`/`frontend-dev` (modelo no frontmatter de `.claude/agents/`).

## Objetivo

Hoje a integração com time guarda um servidor e uma chave por instalação (`~/.usage-monitor/team.json`). Quem trabalha
para dois times (conta pessoal num servidor, conta da empresa noutro) só consegue integrar um. A #547 transforma a
configuração numa lista de times, cada um com servidor, chave, nome, apelido, contas e token de admin próprios.

## Fatos confirmados (2026-11-09, `de697cac`)

- `TeamIntegrationSettings.kt:15-31`: um `serverUrl`, um `apiKey`, um `alias`, `deviceId`,
  `participatingProfileIds: Set`, `adminToken`. `toString` já mascara segredo.
- `LocalTeamSettingsDataSource.kt:15-28`: DTO plano; `load()` cria `deviceId`; `peek()` sem efeito (#441);
  `Json { ignoreUnknownKeys = true }` — versão anterior ignora campo novo.
- `AppSettingsStore.kt:71-72`: um `teamSettingsFlow` global; `AppGraph.kt:214,219`: repositórios recebem
  `settingsProvider = { teamSettingsFlow.value }`; `AppGraph.kt:210`: um `InMemoryTeamServerClockOffset` por grafo,
  não por servidor.
- `TeamSyncService.kt:267-295`: `syncOnce` itera os alvos e filtra por `settings.participates`; estado de falha e
  identidade (`:210-211`) globais.
- Servidor: uma conta pertence a no máximo uma chave (`team_key_accounts`, índice único em `account_key`,
  `server/src/db/schema.ts:100-111`); `maxAccounts` (default 1) limita contas por chave. Nada no servidor impede o
  cliente de falar com dois servidores.
- Painel do time: `TeamUsageScreen.kt:159` em modal (`ModalWindowsHost.kt:167`), alimentado por
  `TeamUsageViewModel` (743 linhas: perto do teto de 800).
- CLI: `team join|status|leave` com `--server --alias --accounts --skip-check` (`TerminalTeamCommandLine.kt:31-38`);
  `status --json` não tem campo de time (`TerminalJson.kt`). TUI: `team-key`, `team-leave` (`TuiSettingsCommands.kt:28-29`).
- Tamanhos (linhas): `TeamSyncService` 542, `TeamIntegrationSection` 452, `SettingsActions` 432, `CollectorEngine` 437,
  `TeamAdminRepositoryImpl` 359, `AppSettingsFeedback` 278.

## Decisões

- **Escopo aprovado pelo dono (2026-11-09):** cada conta participa de **no máximo um** time; o painel mostra um
  **seletor por time**, sem somar servidores; o token de admin é **por time**.
- **Modelo:** `TeamIntegrationSettings` passa a descrever **um** time e ganha `id` e `name`; um agregado novo
  (`TeamIntegrations`: `deviceId` + `teams`) é o que o app guarda. Assim os repositórios e o envio, que já recebem um
  `TeamIntegrationSettings`, continuam valendo por time. O `deviceId` é da instalação, não do time.
- **Participação exclusiva** é regra do domain: marcar a conta num time a desmarca nos outros (função pura testada).
- **`team.json` v2:** `{ deviceId, teams: [...] }`. Leitura do formato plano vira um time só, sem ação do usuário.
  Gravação v2 **espelha o primeiro time nos campos planos**: versão anterior (downgrade, canal beta) continua
  sincronizando esse time em vez de perder a integração.
- **Repositórios, offset de relógio e memória de 404 por time:** uma instância por `id`, criada sob demanda, com
  `settingsProvider` lendo o time do `id`. Offset de relógio de um servidor nunca corrige o de outro.
- **Id do time:** gerado uma vez (`team-` + UUID), estável ao renomear; o migrado do formato plano também recebe um.
- **Servidor sem mudança.**
- **Direção visual B, mestre-detalhe (escolha do dono, 2026-11-09, `prototipos/times-547/b-mestre-detalhe/`):** lista
  estreita de times com status e "Adicionar time" abaixo, formulário do time escolhido ao lado; no painel do time o
  título é o seletor (`Sessões do time — Empresa ▾`, `AppMenu`) e só aparece com mais de um time ativo.
- **Usuário já configurado não é afetado (exigência do dono):** o `team.json` atual vira um time sem ação nenhuma.

## Atividades

| Id | Atividade | Agente |
|---|---|---|
| A01 | Este plano | Neferpitou |
| A02 | Domain: `id`/`name` no time, agregado `TeamIntegrations`, participação exclusiva, busca do time de uma conta | backend-dev |
| A03 | Data: `team.json` v2, migração do plano, espelho do 1º time, `peek` sem efeito | backend-dev |
| A04 | Sync por time, em três commits: A04a marcador por time; A04b registro de repositórios/offset/404 por `id` e store; A04c `TeamSyncService` itera times | backend-dev |
| A05 | Configurações: `SettingsActions`/`AppSettingsFeedback`/`ConfigApplier` por time (adicionar, remover, editar, testar) | backend-dev |
| A06 | Admin por time: `TeamAdminRepositoryImpl`/`TeamKeysAdminViewModel` recebem o time | backend-dev |
| A07 | `TeamUsageViewModel`: extração (teto 800) e time selecionado | backend-dev |
| A08 | Tela: seção Team com lista de times (direção escolhida no protótipo) + registro no protótipo e no design system | frontend-dev |
| A09 | Tela: seletor de time no painel de uso + registro no protótipo e no design system | frontend-dev |
| A10 | CLI/TUI: `--team <nome>` em `team join/status/leave`, TUI no time escolhido | backend-dev |
| A11 | `docs/team-integration.md` e `CLAUDE.md` (regra nova) | Neferpitou |

Portão de protótipo antes de A08/A09: opções em `prototipos/times-547/`, escolha do dono antes do Kotlin.

## Ponto de situação

Cada linha é escrita no commit da atividade que descreve, com o comando executado e o resultado.

| Atividade | Alteração | Comando e resultado |
|---|---|---|
| A01 | Plano com fatos medidos, decisões e atividades | `gh issue comment 547` → comentário 6742060751; varredura do `explorer` (caminhos acima) |
| A02 | `TeamIntegrations` (agregado, participação exclusiva, `enabled`/`deviceId` da instalação) e `id`/`name` no time | vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.domain.*"` → `TeamIntegrationsTest` 10 de 17 falhando na asserção (ex.: `toString do agregado nao expoe chave nem token de admin FAILED`); verde: mesmo comando → 550 testes, 0 falhas |
| A03 | `team.json` v2 (`teams[]`), migração do plano para `team-legacy` sem ação, espelho do 1º time nos campos planos, `loadAll`/`peekAll`/`saveAll` | vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.data.LocalTeamSettingsDataSourceTest"` → `expected:<1> but was:<0>`, `expected:<[team-a, team-b]> but was:<[]>`; verde: mesmo comando → 21 testes, 0 falhas (inclui "usuário já configurado segue ativo após migração e reabertura") |
| A04a | Marcador de envio por time (`team_sync_watermarks`, PK `team_id, session_id`); cópia única de `team_sync_state` para `team-legacy`, tabela antiga intacta | vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.data.LocalTeamSyncStateDataSourceTest"` → `:38 expected:<1> but was:<0>` (tabela antiga apagada) e, com a guarda desligada, `expected:<[1, 2]> but was:<[2]>`; verde: `--tests LocalTeamSyncStateDataSourceTest --tests TeamSyncServiceTest` → 5/5 e 29/29 |
| A04b | `teamIntegrationsFlow` como fonte (`teamSettingsFlow` derivado, só leitura); `TeamRepositoryRegistry` (uso, admin e offset por time); `TeamIntegrations.LEGACY_TEAM_ID` único para o time sem id | vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.TeamRepositoryRegistryTest"` → `TeamRepositoryRegistryTest.kt:44` AssertionError (sem cache por id); `SettingsActionsTest.kt:420 expected:<[team-legacy]> but was:<[]>` (instalação sem time gravava id vazio). Troca do flow para o agregado: refatoração forçada pela compilação, sem vermelho próprio, coberta pela suíte existente. Verde: `--tests TeamRepositoryRegistryTest --tests SettingsActionsTest --tests AppSettingsFeedbackTest --tests "com.usagemonitor.data.*" --tests TeamSyncServiceTest --tests "com.usagemonitor.architecture.*"` → 734 testes, 0 falhas |
| A04c | `TeamSyncService` itera `activeTeams`: uma indexação por passada, fábricas de envio/presença por time, identidade por `(teamId, accountKey)`, `syncStatusByTeam` | vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.TeamSyncServiceTest"` → `cada time recebe so o lote da sua conta` `expected:<[msg-2]> but was:<[]>`, `status de cada time fica separado` `actual value is null`; verde: `--tests TeamSyncServiceTest --tests "com.usagemonitor.Collector*"` → 34/34 e 5/5 |
| A05a | Ações das Configurações por time (`teamId` em todas; `addTeam`/`removeTeam`/`selectTeam`; mover conta entre times); switch geral no agregado (corrige `enabled` descartado pelo `upsert`, também no `team join` do CLI) | vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.SettingsActionsTest"` → `:451`, `:477`, `:492` AssertionError (mover conta, `addTeam`, remover o aberto) e `:318`/`:329` `assertTrue(store.teamIntegrationsFlow.value.enabled)`; verde: `--tests SettingsActionsTest --tests ConfigChannelTest` → 31 e 14, 0 falhas; `gradlew.bat desktopJar` → BUILD SUCCESSFUL |
| A05b | Retorno de conexão e de admin por time (`teamConnection(teamId)`), repositório/vínculo/validação pelo registro do time; `removeTeam` limpa o retorno do time removido | vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.AppSettingsFeedbackTest"` → `:180 expected:<FAILED> but was:<IDLE>`, `:289 expected:<[team-b]> but was:<[]>`; verde: `--tests AppSettingsFeedbackTest --tests SettingsActionsTest` → 16 e 31, 0 falhas; `desktopJar` → BUILD SUCCESSFUL. Limpeza no `removeTeam` sem vermelho próprio (entrou depois do verde) |
| A06 | Repositórios roteados (`RoutedTeamUsageRepository`, `RoutedTeamAdminRepository`, offset roteado) e `TeamViewSelection`: painel e presença seguem o time do painel; admin de chaves segue o time aberto nas Configurações (queda no primeiro time, não no primeiro ativo) | vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.RoutedTeamRepositoriesTest"` → `:101` offset esperado 2000, era 0; `:67 expected "a", was "gone"`; verde: `desktopJar desktopTest --tests RoutedTeamRepositoriesTest --tests SettingsActionsTest --tests TeamRepositoryRegistryTest --tests AppSettingsFeedbackTest --tests "com.usagemonitor.architecture.*"` → 72 testes, 0 falhas |
| A07a | Seleção do painel como funções puras (`TeamDashboardSelection.kt`: opções = times ativos, seletor só com 2+, time de uma conta, conta da troca, queda no primeiro ativo); `dashboardTeam()` usa a queda | vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.TeamDashboardSelectionTest"` → `expected:<[t1, t3]> but was:<[]>`, `expected:<AllAccounts> but was:<None>`; verde: `desktopJar desktopTest --tests TeamDashboardSelectionTest --tests RoutedTeamRepositoriesTest` → 7 e 4, 0 falhas |
| A07b | Ligação: card grava o time da conta; overview admin existe com qualquer time admin e grava o primeiro admin; `selectDashboardTeam` abre a conta do time ou o overview | vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.AppShellActionsFactoryTest"` → 7 AssertionError (ex.: `:214 assertEquals("t2", selection.value)`); verde: `--tests AppShellActionsFactoryTest --tests TeamDashboardSelectionTest` → 13 e 8, 0 falhas; `desktopJar` → BUILD SUCCESSFUL |
| A08a | `changeTeamName(teamId, name)`: renomear preserva o id e não toca o outro time; aviso "Integração com time salva" (sem valor novo em `SettingsField`) | vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.SettingsActionsTest"` → `:508` `ComparisonFailure` (nome não gravado); verde: mesmo comando → 32, 0 falhas |
| A08 | Tela: seção Time em mestre-detalhe (lista com status escrito, "Adicionar time", formulário do time aberto com nome, conta de outro time com aviso de mover, "Remover time" com confirmação, vazio); textos em `TeamIntegrationLabels`; parâmetros de time agrupados (`TeamSectionState`/`TeamSectionActions`, `SettingsDialogContent` abaixo de 300 linhas); catraca PT/EN 702 → 700; registro em `prototipo-visual-opencode.html#cfg-time` e `Settings.jsx` | vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.ui.TeamIntegrationSectionTest"` → 6/6 `Expected exactly '1' node but could not find any node that satisfies: (Text … contains 'Pessoal')`; verde: `desktopJar desktopTest --tests "com.usagemonitor.ui.*" --tests "com.usagemonitor.architecture.*" --tests SettingsActionsTest --tests AppSettingsFeedbackTest` → 711 testes, 0 falhas; protótipo conferido em render do Edge |
| A09a | Botão de time nos cards de conta de **qualquer** time ativo (`teamParticipatingProfileIds`, ligado no `Main.kt`; antes só o primeiro time) | vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.TeamDashboardSelectionTest"` → `expected:<[p-a, p-b, p-c]> but was:<[]>`; verde: `desktopJar desktopTest --tests TeamDashboardSelectionTest` → 9, 0 falhas |
| A09b | `TeamUsageScreen` com `teamSelector` opcional: gatilho "Time: <nome> ▾" + `AppMenu` só com 2+ opções (`TeamSelector.kt`, rótulos por `localized`) | vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.ui.TeamUsageScreenTest"` → `Expected exactly '1' node but could not find any node that satisfies: (TestTag = 'teamUsageSelector')`; verde: mesmo comando → 60, 0 falhas (os dois casos sem gatilho são guarda, passam desde o início) |
| A09c | Seletor ligado no painel (`ModalWindowsHost` recebe o agregado e `selectDashboardTeam`; `canManage` = time do painel admin); título com o nome do time; `TeamSelectorOption` duplicado removido; registro em `prototipo-visual-opencode.html` (modal "Sessões do time") e `TeamUsage.jsx` | vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.presentation.TeamUsageLabelsTest"` → `TeamUsageLabelsTest.kt:33 expected:<Sessões do time[ — Empresa]> but was:<Sessões do time[]>`; verde: `desktopJar desktopTest --tests TeamDashboardSelectionTest --tests "com.usagemonitor.presentation.*" --tests "com.usagemonitor.ui.*" --tests "com.usagemonitor.architecture.*" --tests AppShellActionsFactoryTest --tests "com.usagemonitor.Modal*"` → 1793 testes, 0 falhas |
| A10 | CLI com `--team <nome>` em `team join/status/leave`; `ConfigChange.TeamJoin(teamName)`/`TeamLeave(teamName)` (JSON antigo `team_leave` decodifica); `join` com nome novo cria time; sem nome e 2+ times recusa; `leave` apaga só aquele time e desliga o agregado só sem time ativo; `team status` uma seção por time; registro em `prototipo-modo-cli.html` | **sem vermelho registrado**: o agente (sonnet, a pedido do dono) escreveu a implementação antes dos 7 testes novos; verde: `desktopJar desktopTest --tests "com.usagemonitor.terminal.*" --tests ConfigChannelTest --tests "com.usagemonitor.architecture.*" --tests SettingsActionsTest` → 250 testes, 0 falhas (TerminalTeamCommandTest 12) |
| A11 | `docs/team-integration.md` (seção "Vários times ao mesmo tempo") e regra curta no `CLAUDE.md` | sem harness de teste (documentação); conferido contra o código dos commits A02–A10 |

## Problemas em aberto e riscos

- TUI: a leitura (`TuiTeamReader`/`TuiSettingsReader`) ainda mostra só o primeiro time; `team-key`/`team-leave` com 2+ times caem na recusa do `ConfigApplier` (pede `--team`). Fica para issue própria.
- Downgrade para versão sem a #547 enxerga só o primeiro time (espelho); os outros ficam inertes até voltar.
- `TeamUsageViewModel` em 743 linhas: A07 começa extraindo, nunca crescendo.
- #342: A08/A09 mexem só em conteúdo de janela já existente (modal e Configurações), sem host de janela novo.
