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

## Atividades

| Id | Atividade | Agente |
|---|---|---|
| A01 | Este plano | Neferpitou |
| A02 | Domain: `id`/`name` no time, agregado `TeamIntegrations`, participação exclusiva, busca do time de uma conta | backend-dev |
| A03 | Data: `team.json` v2, migração do plano, espelho do 1º time, `peek` sem efeito | backend-dev |
| A04 | Sync por time: `TeamSyncService` itera times; repositórios/offset/404 por `id` | backend-dev |
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

## Problemas em aberto e riscos

- Downgrade para versão sem a #547 enxerga só o primeiro time (espelho); os outros ficam inertes até voltar.
- `TeamUsageViewModel` em 743 linhas: A07 começa extraindo, nunca crescendo.
- #342: A08/A09 mexem só em conteúdo de janela já existente (modal e Configurações), sem host de janela novo.
