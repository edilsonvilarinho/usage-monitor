# Chave de time por conta dentro do mesmo time — issue #549

Procedência: Claude Opus 5.5 (orquestradora), Claude Code CLI no Windows 11, 2026-11-09, branch
`feat/issue-549-per-account-key`, commits com a identidade `claude <claude@anthropic.com>`. Implementação pelos agentes
`backend-dev`/`frontend-dev` (modelo no frontmatter de `.claude/agents/`).

## Objetivo

A #547 permitiu vários times, mas cada time guarda **uma** chave. O servidor emite uma chave **por pessoa** (e-mail no
rótulo), e uma conta pertence a no máximo uma chave. Quem tem duas contas locais de pessoas diferentes no mesmo time
(ex.: `Padrão` = edilson, `TRABALHO` = thiago, mesmo servidor) só sincroniza uma delas. A #549 dá a cada conta
participante uma chave própria opcional; conta sem chave própria usa a chave do time (opção A, escolhida pelo dono).

## Fatos confirmados (2026-11-09, `ea9067c8`)

- `TeamIntegrationSettings.kt:18`: um `apiKey` por time; `isConfigured` (`:47-51`) exige `apiKey` não vazio.
- `TeamUsageRepositoryImpl.kt:64-77` (`push`) e `:94-111` (`touchPresence`) leem `settings.apiKey` via
  `settingsProvider()` a cada chamada; leituras (`fetch*`) usam `readCredential()` `:265-271` (chave do time, senão admin).
- `TeamSyncService.kt:389-469` (`syncTarget`): presença, identidade e envio **por conta** (`accountKey` do alvo);
  marcador de identidade em `pushIdentityIfChanged` `:522-550`.
- Testar conexão: `AppSettingsFeedback.kt:215` → `claimTeamKey` por conta participante (`:262`) →
  `TeamAdminRepositoryImpl.claimKeyForAccount` `:294-316`, credencial pela chave do time (`:318-319`). Terminal:
  `TerminalTeamRunner.kt:270`.
- `LocalTeamSettingsDataSource.kt:19-28`: `TeamEntryDto` v2 sem `@SerialName`; `Json { ignoreUnknownKeys = true }` —
  versão anterior ignora campo novo.
- `TeamRepositoryRegistry.kt:27-41`: repositório lê o time por lambda a cada chamada (não captura a chave).
- `TeamIntegrationSection.kt` 612 linhas; linhas de participação em `:217-231`, `TeamProfileCheckboxRow` `:545-554`.
- Medição: varredura do agente `explorer` sobre os caminhos acima; tamanhos por `wc -l`.

## Decisões

- **Opção A (dono, 2026-11-09):** `accountApiKeys: Map<profileId, chave>` no time; `apiKeyFor(profileId)` devolve a
  chave da conta, ou a do time quando a da conta está vazia.
- **Zero impacto em quem usa hoje (dono):** campo opcional com default vazio; `team.json` sem chaves por conta é lido e
  regravado **idêntico** (o campo não é escrito quando vazio); downgrade ignora o campo (`ignoreUnknownKeys`).
- `isConfigured` passa a aceitar time sem chave do time quando **toda** conta participante tem chave própria; time
  com chave do time continua como hoje.
- Envio, presença, marcador de identidade e `claim` usam `apiKeyFor` da conta. Trocar a chave de uma conta força
  reenvio da identidade daquela conta, como a troca da chave do time já faz.
- Leituras do painel: chave do time, senão a primeira chave de conta participante (ordem do `participatingProfileIds`
  ordenado), senão token de admin.
- Servidor não muda: a chave já se amarra à conta pelo `claim`.
- Tela: sem portão de protótipo — reaproveita o campo secreto existente da seção, sem direção visual a escolher.
  Registro no protótipo e no design system no commit da tela.

## Atividades

| Id | O quê | Dono |
|---|---|---|
| A01 | Este plano | Neferpitou |
| A02 | Domain: `accountApiKeys`, `apiKeyFor`, `isConfigured`, `toString` mascarado | backend-dev |
| A03 | Data: `team.json` v2 com `accountApiKeys` omitido quando vazio; ida e volta idêntica | backend-dev |
| A04 | Envio, presença, identidade e leitura com a chave da conta | backend-dev |
| A05 | Testar conexão (app e terminal) com a chave de cada conta; ação para gravar a chave da conta | backend-dev |
| A06 | Tela: campo `Key` opcional na linha da conta ligada + protótipo e design system | frontend-dev |
| A07 | `docs/team-integration.md`, seção da #549 | Neferpitou |

## Ponto de situação

Cada linha é escrita no commit da atividade que descreve, com o comando executado e o resultado.

| Atividade | Alteração | Comando e resultado |
|---|---|---|
| A01 | Plano com fatos medidos, decisões e atividades | `gh issue create` → #549; varredura do `explorer` (caminhos acima) |
| A02 | `accountApiKeys` (profileId → chave), `apiKeyFor` (própria aparada, senão a do time), `isConfigured` aceita time sem chave quando toda participante tem a sua, `toString` mascara os valores | vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.domain.TeamIntegrationSettingsTest"` → 3 falhas na asserção (`:32 expected:<chave-[conta-1]> but was:<chave-[time]>`, `:65 Expected value to be true.`); verde: `gradlew.bat desktopTest --tests "com.usagemonitor.domain.*"` → 558 testes, 0 falhas |

## Problemas em aberto e riscos

- CLI (`team join`) e TUI continuam só com a chave do time; chave por conta só pela tela.
- Rótulo da chave com e-mail: chave de uma pessoa colada na conta de outra é recusada pelo servidor (403) no `claim` —
  comportamento correto, aparece pelo retorno do "Testar conexão".
