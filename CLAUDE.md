# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bat
# Rodar a aplicação Desktop
gradlew.bat run

# Compilar sem rodar
gradlew.bat desktopJar

# Todos os testes (domain + data + ViewModel + UI)
# A task raiz `test` não existe neste projeto KMP — use `allTests`.
gradlew.bat allTests

# Apenas testes do commonTest (domain, mappers, ViewModel)
gradlew.bat desktopTest --tests "com.usagemonitor.domain.*"
gradlew.bat desktopTest --tests "com.usagemonitor.data.*"
gradlew.bat desktopTest --tests "com.usagemonitor.presentation.*"

# Apenas testes de componente UI (desktopTest)
gradlew.bat desktopTest --tests "com.usagemonitor.ui.*"

# Limpar build cache
gradlew.bat clean
```

Antes de rodar, definir variável de ambiente:
```bat
set MINIMAX_API_KEY=sua_chave_aqui
```

Servidor de time (`server/`, opcional — só quem usa a integração com time):
```bash
cd server
npm install
npm test        # vitest + supertest
npm run dev     # http://localhost:3000
```

## Arquitetura

KMP Desktop (JVM único alvo). Código organizado em três camadas com dependências unidirecionais: `presentation → domain ← data`.

### Source sets

| Source set | Conteúdo |
|---|---|
| `commonMain` | domain + data (exceto leitura de ficheiros) + presentation/UI |
| `desktopMain` | `LocalCredentialDataSource` (usa `java.io.File`) + `Main.kt` (DI + janela) |
| `commonTest` | Testes unitários de domain, mappers e ViewModel |
| `desktopTest` | Testes de componente Compose (`runDesktopComposeUiTest`) |

### Camada domain (`commonMain/domain/`)

Núcleo puro — **zero imports de Ktor, Compose ou bibliotecas externas**.

- `QuotaInfo`: entidade com `percentageUsed` e `remaining` calculados. `UsageUnit` diferencia `TOKENS`, `REQUESTS` (MiniMax), `PERCENTAGE` (Anthropic/Codex) e `CURRENCY_USD` (saldo DeepSeek). `currencyCode` (default `"USD"`) diz em que moeda os valores monetários estão. **Não crie valores novos em `UsageUnit`**: os `when` exaustivos do card, do histórico e do gráfico quebram; campo novo com default é retrocompatível, valor de enum novo não é.
- `ApiUsageStats`: agrega lista de `QuotaInfo` por API.
- Interfaces `AnthropicRepository` / `MiniMaxRepository`: o domain define o contrato; `data` implementa.
- Use cases usam `operator fun invoke()` — chamados como `useCase()`.

### Camada data (`commonMain/data/` + `desktopMain/data/`)

- DTOs com `@Serializable` + `@SerialName` para mapear snake_case do JSON.
- `LocalCredentialDataSource` em **desktopMain** (usa `java.io.File`). Lê `~/.claude/.credentials.json` → `claudeAiOauth.accessToken`. Valida `expiresAt`. A origem sai de `AnthropicCredentialStore`: o ficheiro tem prioridade sempre; **no macOS**, quando ele não existe, cai na entrada `Claude Code-credentials` do Keychain via `security` (só para o perfil padrão — perfis de `CLAUDE_CONFIG_DIR` não têm entrada lá). A gravação pós-refresh volta para a mesma origem.
- `RemoteApiDataSource`: Anthropic faz `GET /api/oauth/usage` (headers `anthropic-beta: oauth-2025-04-20`, `User-Agent: claude-code/1.0.0`) e lê a utilização das janelas direto do corpo JSON. MiniMax faz `GET /v1/token_plan/remains`.
- **Créditos de uso (Anthropic)**: `extra_usage` é a fonte primária — `monthly_limit` e `used_credits` vêm em unidades menores da moeda (55000 = R$ 550,00) e `currency` traz a moeda real da conta, que **não é sempre USD**. `spend` só reforça (o `percent` dele vem arredondado; a moeda cai para "USD" quando o recurso está desligado). `AnthropicMapper` só cria a terceira `QuotaInfo` quando `is_enabled` é `true`; o rótulo `AnthropicQuotaLabels.EXTRA_CREDITS` é chave da série histórica e não pode ser renomeado.
- `MiniMaxRepositoryImpl` lê `System.getenv("MINIMAX_API_KEY")` — nunca hardcode.
- Ambos os repos usam `Result.runCatching { }` para encapsular falhas.

### Camada presentation (`commonMain/presentation/`)

- `UiState`: `sealed interface` com `Loading`, `Success(data)`, `Error(message)`. Se uma API falhar e outra tiver sucesso, emite `Success` com dados parciais.
- `DashboardViewModel`: `StateFlow<UiState>` + polling silencioso via `while(true) + delay(10 * 60 * 1_000L)`. Escopo com `SupervisorJob` — falha de uma coroutine não cancela as outras. Chamar `onDestroy()` ao fechar janela.
- Componentes UI: **todos stateless** (recebem dados via parâmetros, emitem eventos via lambdas). `DashboardScreen` é o único stateful.
- Timezone de reset: sempre `TimeZone.of("America/Sao_Paulo")` com label `BRT`.
- **Copiar sessão** (`CopySessionCommandButton`): a tela mostra o id truncado em 8 (`shortSessionId`), que **não** retoma nada — `claude --resume` só volta direto para a conversa com o session ID inteiro; com um prefixo ele cai no seletor interativo. Por isso o botão copia `claude --resume <uuid completo>` (`resumeSessionCommand`). No modal do time o transcript é de outra máquina, então ali `isLocalSession = false` copia só o identificador — comando que cairia num seletor vazio seria pior que botão nenhum. A escrita no clipboard passa por `rememberClipboardWriter()`, injetável, para o teste de componente não apagar o clipboard de quem roda a suíte.
- **Semáforo de sessão** (`SessionPulseViewModel`, laço de 30s): faz os botões de Sessões CLI e de time do card piscarem quando há sessão com turno nos últimos 5 min (`ACTIVE_SESSION_WINDOW_MILLIS`) e veredito `ATTENTION`/`SATURATED`. O corte de 5 min é `sinceEpochMillis` na consulta, **nunca** um valor novo em `CliSessionRange` — os `when` exaustivos dos chips quebrariam. O laço indexa antes de ler, senão a latência seria a do laço de background (10min). Com a janela minimizada (`isAppVisible` falso) a passada **local continua** — é ela que alimenta o alerta de sessão saturada, cujo destinatário é justamente quem não está olhando a tela; o que fica suspenso é a leitura do time (`refreshOnce(includeTeam = false)`), uma requisição por conta a cada 30s sem ninguém para ver o pisca. Os pulsos de time guardados envelhecem mesmo assim, senão voltariam acesos ao restaurar a janela. Leitura que falha **mantém** o pulso anterior; quem o apaga é `SessionPulse.prunedAt`, pela idade dos alertas — sem isso um servidor de time fora do ar deixaria o botão piscando indefinidamente. `sessionPulseFrame` é função pura (fase → severidade + alpha) para o pisca ser testável sem Compose, e `rememberSessionPulseFrame` **não cria transição infinita** sem pulso: uma animação sem fim trava o `waitForIdle` dos testes de componente.

- **Resumo por eixo** (`CliUsageBreakdown.kt` + `GetCliUsageBreakdownUseCase` + aba na tela de Sessões CLI): consumo da janela recortado por projeto (`cwd`), branch e modelo, mais a economia agregada do cache. As colunas já existiam no índice e nunca eram somadas.
  - **A query nova é a irmã de `SELECT_SESSIONS_SINCE_SQL`**: mesmo `GROUP BY (session_id, model)` e mesmo corte. É isso que garante que o total do resumo bata com o do cabeçalho da lista; divergência ali significa que alguém abriu um segundo caminho de precificação.
  - **O custo é recalculado dos tokens com `ModelPricingTable`**, nunca rateado a partir de `cli_sessions.cost_micros` — o índice só guarda custo por sessão, e ratear entre modelos é justamente o que o resumo existe para evitar. Somar tokens antes de precificar é exato: `costMicros` soma os produtos e divide uma vez.
  - **As três listas descrevem os mesmos turnos**; somar baldes de listas diferentes contaria o mesmo gasto três vezes, e a tela diz isso em texto.
  - Ordem total e determinística (custo desc, tokens, rótulo) pela razão de sempre: duas leituras iguais têm de dar listas iguais, ou o `StateFlow` reemite e a tela recompõe a cada tique.
  - **Modelo sem tarifa não vira custo zero silencioso**: `unpricedTurnCount` sobe e o valor exibido leva `+`, marcando piso e não total.
  - A aba só é lida quando o usuário a abre, e o laço ao vivo só a recalcula com ela aberta — um `GROUP BY` sobre a tabela de turnos a cada 5s sem ninguém olhando seria desperdício. Leitura que falha **mantém** os números anteriores e publica só a mensagem.
  - **Grade de atividade** (`CliActivityHeatmap`): a query agrupa por `ts / 3_600_000`, que é hora **UTC**; quem traduz para hora local é o domain, com `America/Sao_Paulo`. Agrupar em SQL deslocaria tudo em três horas — o bastante para trocar a madrugada pela noite anterior. A intensidade é do **custo** e relativa ao pico da própria janela: escala fixa apagaria o padrão tanto num dia calmo quanto numa semana cheia. Falha na grade **não derruba** o resumo; ela é acessória.
  - **Ritmo de queima** (`burnRateOf`): divide pelo **tempo decorrido** desde o início da janela, nunca pela duração nominal — cinco horas fixas subestimariam o ritmo em toda a primeira hora, justamente quando o aviso serviria. Abaixo de `MIN_BURN_RATE_ELAPSED_MILLIS` (5 min) não há ritmo: um turno caro num minuto daria "US$ 60/h", verdadeiro na aritmética e falso como previsão. Sem `resets_at` conhecido não há projeção. Mede tokens e dinheiro reais dos turnos — grandeza **diferente** de `UsageHistorySeries.averageDisplayConsumptionPerHour`, que é percentual de quota sobre snapshots; a UI rotula as duas separadamente.
  - `projectNameFromCwd` é compartilhada com `CliSessionSummary.projectName`: duas derivações do mesmo caminho divergiriam em algum caso de borda e a tela mostraria dois rótulos para um projeto só.

- **Alertas na bandeja** (`evaluateUsageAlerts` em `domain/entity/UsageAlert.kt` + `UsageAlertViewModel` + `Tray` no `Main.kt`): notificação nativa quando uma cota cruza um limiar (default 75/90/100) ou uma sessão CLI satura. A decisão é **função pura** — entra `UiState.Success` (que já traz `riskSummaries`), o `SessionPulse` mesclado e o estado anterior; sai a lista a emitir e o estado novo. O view model **não tem laço próprio**: reage às emissões do polling de 10min e da passada de 30s que já existem.
  - **`UsageAlertState` é a dedup**, e sem ela o mesmo alerta sairia a cada coleta. A chave da janela é `QuotaAlertScope` (alvo + rótulo + `periodType`), **sem** o `periodEndAt`: o reset entra como valor guardado e a comparação passa por `isSamePeriod`, a mesma tolerância de 5 min que o histórico usa contra o jitter de ~1s do `resets_at`. Comparar o reset por igualdade rearmaria o alerta a cada poll.
  - **Cota vencida não alerta** (`isExpiredAt`): a janela descreve um período que já não existe.
  - **O limiar é piso**: o percentual é truncado, não arredondado — 89,9% não cruzou 90%.
  - **Silêncio adia, não consome.** No período silenciado o limiar cruzado **não** é marcado como disparado: dentro de uma janela o consumo só cresce, então o aviso é reavaliado e sai quando o silêncio terminar. Marcá-lo ali perderia o alerta para sempre.
  - **Desligar o alerta zera o estado**, para religá-lo voltar a avisar sobre a janela corrente em vez de herdar disparos antigos.
  - Só `SATURATED` vira notificação de sessão. `ATTENTION` apareceu em 7 das 70 sessões medidas (`CliSessionHealthThresholds`): notificar nesse patamar tornaria o alerta rotina.
  - O ícone da bandeja é o do app **com um ponto** de risco no canto (`TrayRiskIconPainter`, com `equals` sobrescrito para o `Tray` não reconstruir a imagem AWT a cada recomposição). `ON_TRACK` não acende nada — ponto verde permanente vira decoração.
  - As preferências vão em `PreferencesSettings` (`UsageAlertPreferences.kt`), não em `~/.usage-monitor/`: ali moram os segredos do time, e limiar não é segredo. **`UserPreferences` (domain) é código morto** — nenhuma leitura o referencia; não use aquele caminho.
  - Fechar a janela continua encerrando o app: não existe "minimizar para a bandeja".

### Empacotamento

`TargetFormat.Exe`/`Msi` (Windows), `Deb`/`Rpm` (Linux) e `Dmg` (macOS). O jpackage **não faz cross-compile**: o `.dmg` só sai rodando em macOS, por isso o release depende do job `build-macos` (`macos-latest` arm64 + `macos-15-intel` x64) em `.github/workflows/release-linux.yml`. Os DMGs vão sem assinatura Apple — o Gatekeeper exige liberação manual, documentada no README.

Auto-start (`AutoStartManager`): registro `Run` no Windows, `.desktop` no Linux, LaunchAgent (`~/Library/LaunchAgents/com.usagemonitor.app.plist` + `launchctl`) no macOS. O enum `Platform` é exaustivo em três `when` do arquivo — valor novo quebra a compilação nos três.

### Injeção de dependências

Manual, em `Main.kt` (desktopMain). Sem framework. Sequência: `HttpClient(OkHttp)` → datasources → repos → use cases → `DashboardViewModel` → `DashboardScreen`.

## Integração com time (`server/`)

Recurso opcional, desligado por default. Servidor Node.js **self-hosted pela empresa** (Express 4 + TypeScript + SQLite) que recebe os turnos indexados de cada máquina e devolve a visão agregada por conta Anthropic. Contrato da API e passo a passo de deploy no Dokploy em [`server/README.md`](server/README.md).

- **Chave de agrupamento:** o `accountUuid` da conta (`UsageAccountKey.providerAccountId`), sem o `organizationUuid` — este é nulo em parte das instalações e usá-lo na chave quebraria o agrupamento entre máquinas da mesma conta.
- **Chave de time é por pessoa, emitida pelo app do admin** (`server/src/repositories/teamKeyRepository.ts`). Ela nasce sem conta e se amarra a um `accountUuid` por `POST /v1/claim` (o botão "Testar conexão") ou dentro de um ingest. **Nenhum `GET` reivindica** — senão bastaria varrer uuid para adotar conta alheia. Depender só do ingest travou o time em produção: numa máquina que já enviou todo o histórico, trocar a chave não gera requisição nenhuma, e a conta fica sem dona com a leitura recusada.
- **O marcador de identidade do `TeamSyncService` inclui a chave de time**, não só o apelido. É o que faz trocar a chave forçar um envio mesmo sem turno pendente. Índice único em `team_key_accounts(account_key)`: uma conta pertence a no máximo uma chave, e é ele — não uma checagem prévia — que decide a corrida entre dois ingests. `maxAccounts` (default 1) cobre a máquina logada em duas contas da empresa, que usa a **mesma** chave para as duas.
- **`label` da chave é texto livre do admin, não verificado.** É onde ele digita o e-mail da pessoa. Quem prova o vínculo é o `accountUuid` ao lado; a UI mostra os dois juntos de propósito.
- **`TEAM_API_KEY` virou legado.** Com `TEAM_LEGACY_KEY_MODE=open` (default) ela continua lendo tudo, que é o comportamento anterior; `off` a rejeita e é o passo que efetiva o isolamento. `TEAM_ADMIN_TOKEN` monta `/api/admin/*` e vale como credencial **de leitura** em `/v1/team`, `/v1/session` e `/v1/member` — é o que evita uma família de rotas admin paralela. No `/v1/ingest` ele é recusado: admin lê, não escreve consumo em nome de ninguém.
- **Modo admin é independente de conta.** `TeamIntegrationSettings.isAdminMode` não entra em `isConfigured`/`isActive`: administrar não exige chave de time, apelido nem conta marcada. O botão da barra inferior (`FooterBar.onOpenAdminOverview`, `null` esconde) abre a mesma `TeamUsageScreen` em modo global.
- **`TeamMemberUsage.memberKey`** (`accountKey/deviceId`, ou só `deviceId` fora do modo global) é a identidade da linha na tela. O `deviceId` sozinho não serve: na visão global a mesma máquina em duas contas expandiria as duas juntas e a remoção acertaria a conta errada.
- **Na visão global o recorte de 5h é deslizante.** Cada conta reseta a quota numa hora, e ancorar numa delas daria um número que não corresponde a nenhuma. `GetAdminTeamOverviewUseCase` resolve a janela com `CliQuotaWindows()` vazio e a tela avisa.
- **Configuração** em `~/.usage-monitor/team.json` (`LocalTeamSettingsDataSource`), com escrita atômica e `restrictToOwnerReadWrite`. Nunca em `PreferencesSettings`: a chave do servidor é segredo e as preferências vão em claro para o registro.
- **Envio:** `TeamSyncService`, laço de 30s, roda com a janela fechada. Marcador em `team_sync_state` (mesmo `usage-history.db`), pela conexão **compartilhada** do `LocalCliSessionDataSource` — duas conexões para o mesmo arquivo dariam `SQLITE_BUSY`.
- **Cada passada indexa antes de enviar** (`ensureIndexFresh`, ligado ao mesmo `SyncCliSessionIndexUseCase` da tela de Sessões CLI). O serviço só enxerga turno que já está no índice: sem isso a latência seria a do laço de background (10min), não a dos 30s. Falha na indexação não cancela o envio do que já está indexado. Abrir o modal do time chama `requestImmediateSync()` e antecipa uma passada.
- **Identidade (apelido) viaja no ingest, não numa rota própria.** O servidor grava o `alias` no upsert de `team_members`, dentro do `POST /v1/ingest`. Por isso `TeamSyncService` envia um payload **só com o membro** (`sessions`/`turns` vazios, `PushTeamUsageUseCase(force = true)`) sempre que o apelido difere do último confirmado — marcador em memória, por conta. Sem isso, quem renomeia e para de usar o CLI fica com o nome velho na tela do time indefinidamente. O commit do campo nas Configurações chama `requestImmediateSync()` para não esperar os 30s.
- **Renomear nunca duplica integrante**: a chave é `(account_key, device_id)`. Duas linhas para a mesma máquina só aparecem quando o `deviceId` muda, e o único caminho que o gera é `LocalTeamSettingsDataSource` com `team.json` ausente ou ilegível — daí o arquivo corrompido ir para `.corrupt` em vez de ser sobrescrito. Para limpar um fantasma já criado existe `DELETE /api/v1/member` e o botão de remover no modal, bloqueado para o próprio `deviceId`.
- **Desvincular não apaga nada, e não faz a conta sumir.** `DELETE /api/admin/v1/keys/:id/accounts/:accountKey` mexe em uma linha de `team_key_accounts`; a visão global é derivada de `team_members` ∪ `team_turns`, então a conta desvinculada continua na lista, agora com `label: null` — pior do que antes. Quem a remove é `DELETE /api/admin/v1/accounts/:accountKey` (servidor 0.5.0+), que apaga integrantes, sessões, turnos e o vínculo. Na rota a **ordem é dados primeiro, vínculo depois**: falhando o segundo passo sobra um vínculo sem dados, inócuo; o inverso deixaria a conta órfã e adotável por outra chave com o histórico ainda no banco. **Apagar não impede a conta de voltar** — ingest e presença reivindicam sozinhos, e as travas são o cliente desmarcar a conta ou o `maxAccounts` da chave encher.
- **A tela de presença deixou de ser só leitura em modo admin.** `TeamPresenceViewModel` ganhou `removeTeamMember`/`deleteTeamAccount` **opcionais** (`null` = instalação sem administração, mesmo tratamento de `getAdminTeamPresence`) e um segundo flow, `actionError`, fora do `Success` pelo motivo de sempre: o laço de 5s republica o estado e apagaria a mensagem antes de ela ser lida. Os dois botões são bloqueados pelo `localDeviceId` — a própria máquina e a conta de que ela participa voltam no envio seguinte, então removê-las só apagaria histórico à toa.
- **Leitura:** `TeamUsageViewModel`, laço ao vivo de 5s, só com a janela aberta. Mesmas restrições anti-flicker do `CliSessionsViewModel`.
- **Precificação no cliente:** o servidor devolve tokens por `(deviceId, sessionId, model)` e não calcula custo. `WindowedSessionAccumulator` (domain) aplica `ModelPricingTable` — a mesma classe que o índice local usa, para os dois modais não divergirem.
- **Não trafega conteúdo de prompt ou resposta**, só metadados de uso.
- **Presença é rota própria** (`POST /api/v1/presence`, servidor 0.4.0+), e não uma leitura de `team.ts`: é escrita, com a conta no **corpo**, `x-admin-token` recusado (401) e `allowClaim: true` — o oposto da família de leitura nos três eixos. Ela escreve na `team_members.last_seen_at` que já existia: **nenhuma coluna nova, nenhuma migração**. Contra servidor anterior a rota dá 404 e `TeamUsageRepositoryImpl` cai num ingest só-membro, que carimba o mesmo campo; o 404 é lembrado **por URL** (`presenceRouteMissingFor`), senão seriam 404 a cada 30s para sempre. Só o 404 cai no fallback — 401/403/500 continuam falha, senão chave errada viraria batida "bem-sucedida".
- **Duas camadas de estado, não uma escala.** *Online* = heartbeat dentro de `PRESENCE_ONLINE_WINDOW_MILLIS` (90s = três batidas de 30s do `TeamSyncService`); *trabalhando agora* = turno dentro de `ACTIVE_SESSION_WINDOW_MILLIS` (5 min). Colapsá-las esconderia o caso que a tela existe para mostrar: quem está com o app aberto e parado. `TeamMemberPresence` deriva de `TeamMemberUsage` e usa **dois booleanos, não um enum** — enum novo obrigaria `when` exaustivos na tela. O corte da consulta vai como `cutoffMillis` cru, **nunca** um valor novo em `CliSessionRange`.
- **`last_seen_at` é o relógio do servidor**, e a rota o devolve na resposta justamente para o cliente medir o desvio (`TeamServerClockOffset`). Comparar o carimbo do servidor com `Clock.System.now()` local deixaria um cliente atrasado vendo o time inteiro online **para sempre** — falha silenciosa. `clockSkewSuspected` denuncia carimbo no futuro; sem medida (admin puro, servidor antigo) o offset fica em zero. Instalação em modo admin puro **não bate presença** e isso é intencional: admin não é integrante.
- **A ordem de `toTeamPresence` é total e determinística.** Não é estética: duas leituras iguais têm de produzir listas iguais, ou o `StateFlow` reemite e a tela recompõe a cada 5s. Pelo mesmo motivo o estado não guarda tempo decorrido — a tela mostra hora absoluta, e o desvio entra arredondado em minutos.
- **O ponto de estado da linha não pisca.** Animação infinita numa lista trava o `waitForIdle` dos testes de componente, e o botão do card de presença também não recebe `pulse`: o pisca significa uma coisa só neste app — sessão em atenção — e o botão de time ao lado já a carrega.

## Convenções de código

- **Nomes em inglês**, comentários em português.
- Evitar scope functions aninhadas (`let`, `apply`, `run`). Preferir fluxo explícito.
- Commits: Conventional Commits em inglês + `Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>`.

## Endpoints externos

| API | Endpoint | Auth |
|---|---|---|
| Anthropic | `GET https://api.anthropic.com/api/oauth/usage` | `Authorization: Bearer {accessToken}` do credentials.json + `anthropic-beta: oauth-2025-04-20` |
| MiniMax | `GET https://www.minimax.io/v1/token_plan/remains` | `Authorization: Bearer {MINIMAX_API_KEY}` |

Response Anthropic retorna `five_hour`/`seven_day` com `utilization` em **percentual** (0–100) e `resets_at` em ISO 8601 (pode ser nulo), mais `extra_usage`/`spend` com os créditos de uso em unidades menores da moeda da conta.

Response MiniMax retorna `model_remains[]` com cotas em **requests** (não tokens), timestamps em epoch milliseconds.
