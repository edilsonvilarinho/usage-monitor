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

# Forks paralelos: o default e 1. So ligue numa maquina com `~/.skiko` ja
# populado -- num cache frio os forks se atropelam extraindo a nativa do Skiko.
gradlew.bat allTests -PtestForks=4

# Cobertura: a instrumentacao do Kover e opt-in, senao custa 6-7s por passada
# para produzir um numero que ninguem le. No CI ela liga em todo run que executa a suite.
gradlew.bat allTests -Pcoverage
gradlew.bat koverHtmlReport -Pcoverage

# Limpar build cache
gradlew.bat clean
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
| `desktopMain` | `LocalCredentialDataSource` (usa `java.io.File`) + `Main.kt` (arranque e composição dos hosts) + `AppGraph.kt` (DI) + hosts de janela |
| `commonTest` | Testes unitários de domain, mappers e ViewModel |
| `desktopTest` | Testes de componente Compose (`runDesktopComposeUiTest`) |

### Camada domain (`commonMain/domain/`)

Núcleo puro — **zero imports de Ktor, Compose ou bibliotecas externas**.

- `QuotaInfo`: entidade com `percentageUsed` e `remaining` calculados. `UsageUnit` diferencia `TOKENS`, `REQUESTS` (MiniMax), `PERCENTAGE` (Anthropic/Codex) e `CURRENCY_USD` (saldo DeepSeek). `currencyCode` (default `"USD"`) diz em que moeda os valores monetários estão. **Não crie valores novos em `UsageUnit`**: os `when` exaustivos do card, do histórico e do gráfico quebram; campo novo com default é retrocompatível, valor de enum novo não é.
- `ApiUsageStats`: agrega lista de `QuotaInfo` por API.
- Interfaces `AnthropicRepository` / `MiniMaxRepository`: o domain define o contrato; `data` implementa.
- Use cases usam `operator fun invoke()` — chamados como `useCase()`.

### Camada data (`commonMain/data/` + `desktopMain/data/`)

Decisões e histórico por fornecedor em [`docs/integrations.md`](docs/integrations.md), seção
"Decisões de implementação". **Leia a seção da fonte antes de mexer nela.** Regras que não podem
ser esquecidas:

- DTOs com `@Serializable` + `@SerialName`; repositórios usam `Result.runCatching { }`.
- Leitura de ficheiro só em **desktopMain** (`LocalCredentialDataSource`, `LocalApiKeyDataSource`).
  Chaves de MiniMax/DeepSeek/OpenCode Go só de `~/.usage-monitor/api-keys.json`, nunca hardcode nem
  variável de ambiente. Fontes com chave: `API_KEY_DEPENDENT_SOURCES`, não literal repetido.
- **Renovação OAuth**: o corpo precisa de `client_id` e `scope` (sem eles, `400 Invalid request
  format`); checar status HTTP **antes** de desserializar; regravar o ficheiro por patch de
  `JsonObject`, nunca pelo DTO (apagaria `mcpOAuth`/`refreshTokenExpiresAt`).
- `User-Agent: claude-code/1.0.0` no `/api/oauth/usage` é **obrigatório**.
- Créditos Anthropic: `extra_usage` primário, `spend` secundário; moeda nem sempre USD; expoente ≠ 2
  não vira cota; `AnthropicQuotaLabels.EXTRA_CREDITS` é chave de série — não renomear.
- Última leitura mantida em falha para toda fonte (`statsRetainedAfterFailure`, teto 7 dias); 429
  tipado com backoff persistido (`RateLimitedException`, `RefreshSchedule.kt`).
- Endpoint não documentado degrada por campo ausente e **falha** com resposta vazia (preserva o
  cache). Número que a API não informa não é derivado.
- Proxy: só vale após reiniciar; falha de conectividade é classificada por **tipo** de exceção.
- TLS: `cacerts` + repositório do SO (`SystemTrustStore.kt`); `jdk.crypto.mscapi` entra no
  `modules(...)` **só** no build Windows (jlink de outro SO falharia).
- Tipos com credencial (`CursorSessionCredentials`) não são `data class` — o `toString` vaza o token.
- Antigravity: argumento por lista, nunca por shell; disjuntor e TTL de 5 min obrigatórios.
- Codex: uma janela ao vivo basta; limite por modelo vem do rollout local, soma depois das janelas
  ao vivo e nunca derruba a fonte. `CodexQuotaLabels` é chave de série — não renomear.
- Contas Codex extras (`CodexProfileRegistry`): a padrão segue **sem** `profileId`; id das extras
  começa com `codex-`. Cor/emoji por conta leem `anthropicProfileId`, nunca `profileId` cru.
- Índice CLI (#381): `message.id` repetido **funde por `MAX`** (a saída cresce entre linhas); vazão é ponta a ponta
  (`request_ts` → `last_line_ts`), soma de tokens e de tempo, nunca média de razões. Codex versiona em `codex_cli_index_meta`.
- Acesso web local (#388) e bot Telegram (#387): opt-in, segredo em `~/.usage-monitor/{web-access,telegram}.json`, tipos
  sem `data class`. Servidor só `GET`, token em toda rota (`MessageDigest.isEqual`); bot começa do agora e só atende
  conversa pareada. Saem do `UsageSnapshot` — nunca prompt, resposta ou caminho.

### Camada presentation (`commonMain/presentation/`)

Decisões e histórico de cada funcionalidade em [`docs/presentation.md`](docs/presentation.md).
**Leia a seção da funcionalidade antes de mexer nela.** Regras transversais:

- `UiState`: `sealed interface` `Loading`/`Success(data)`/`Error(message)`; falha parcial emite
  `Success` com dados parciais. `onDestroy()` ao fechar janela; escopos com `SupervisorJob`.
- `DashboardViewModel`: coleta adaptativa **por alvo** (60 s com sessão ativa, 5 min sem, backoff
  em 429), publicação incremental por alvo sob `stateMutex`, histórico sequencial (`historyMutex`).
  `refresh(target)` mexe só no alvo pedido.
- Componentes UI **stateless**; só `DashboardScreen` é stateful.
- Timezone de reset: sempre `TimeZone.of("America/Sao_Paulo")`, label `BRT`. Agrupamento por hora
  sai do SQL em UTC e é traduzido no domain.
- **Ordem total e determinística** em toda lista publicada por `StateFlow`: duas leituras iguais têm
  de dar listas iguais, senão a tela recompõe a cada tique.
- Leitura que falha **mantém** os números anteriores e publica só a mensagem.
- Uso detectado pela cota (`QuotaActivityTracker`, #385) acende o arco da HUD e **nunca** entra no `cliBusy` (realimentaria o polling).
- Nunca valor novo em `CliSessionRange` para cortes de tempo; use parâmetro (`sinceEpochMillis`).
- Custo é recalculado dos tokens com `ModelPricingTable`, nunca rateado. Modelo sem tarifa não vira
  custo zero (`unpricedTurnCount`, `+` no valor). Ferramenta não entra em custo.
- `null` é "não medido", zero é "medido e sem valor" — não colapsar.
- `INDEX_SCHEMA_VERSION` vive em `cli_index_meta`, não em `PRAGMA user_version`; coluna nova em
  `cli_sessions` precisa de quem a preencha num arquivo que nunca mais muda.
- Alertas da bandeja: decisão é função pura; `UsageAlertState` é a dedup; limiar é piso (trunca);
  silêncio adia, não consome. `UserPreferences` (domain) é código morto.
- Só metadados de uso saem do app (exportação, relatório, time) — nunca prompt ou resposta.
- Animação infinita só atrás de `AppMotionPolicy.continuous` (trava o `waitForIdle`).

### Empacotamento, atualização automática, novidades e ajuda

Decisões e histórico em [`docs/build-and-release.md`](docs/build-and-release.md), seção
"Decisões de empacotamento e atualização". Regras:

- Formatos: `Exe` (NSIS), `Deb`/`Rpm`, `Dmg`. **Sem `Msi`**; o `upgradeUuid` fica para remover
  instalações MSI antigas. jpackage não faz cross-compile.
- Auto-start: entrada com `--autostart`; o **nome** do valor `Run` não muda. Agendador de Tarefas
  foi medido e recusado.
- Segunda instância nunca sai calada: pedido em `~/.usage-monitor/focus.request`, atendido por
  `focusHud` (a janela da HUD). `activateWindow` alterna `alwaysOnTop` `false → true → anterior`.
- Atualização automática: SHA-256 contra o `digest` da API do GitHub; flags de build e piso de
  versão por plataforma; Windows só NSIS per-user; Linux só árvore XDG gerenciada. Texto de
  reinício diz **o que** reinicia. Progresso é texto, não animação.
- Novidades: gatilho é `CURRENT_APP_VERSION` ≠ `releaseNotesSeenVersion`, **nunca** o recibo.
  Ordenação de versões só em `AppVersionComparison.kt` (SemVer: `X.Y.Z-beta.N < X.Y.Z`).
- Canal beta (#355): tag `vX.Y.Z-beta.N` publicada como prerelease e **nunca** `latest` — é isso que
  protege quem não optou. Opt-in `receiveBetaUpdates`; desligar não faz downgrade; só o sufixo
  `-beta.N` é aceito (workflow e updater do Linux). Exe/Dmg levam só o número; Deb/Rpm `~beta.N`.
  Publicação pela skill `usage-monitor-release-beta`.
- Ajuda: passos citam o **rótulo real** do controle; GIF animado pelo `Codec` do Skia com cópia
  imutável dos bytes; laço de quadros em `desktopMain`; `gradlew.bat generateHelpMedia` regenera.

### Injeção de dependências

Manual, sem framework. `AppGraph.kt` monta data sources, repositórios e use cases (sequência
`HttpClient(OkHttp)` → datasources → repos → use cases); `AppViewModels.kt` monta os view models e é
o **dono único do encerramento** (`shutdown()`, idempotente), chamado pela saída do app, pelo
`onDispose` da composição e pelo shutdown hook — nunca uma segunda cópia (histórico no plano #298).
`Main.kt` só faz o arranque e compõe os hosts (`HudWindowHost`, `ModalWindowsHost`,
`BugReportWindow`, `SettingsWindowHost`, `AppTrayHost`). **Não há janela principal**: a HUD é a
única de visualização e a âncora do app (`anchorAppWindow`: `graph.mainWindow`, registro de
arranque e ACK de atualização); estado de shell e de modais mora em
`AppShellState`/`AppModalState`. Os arquivos ficaram no pacote `com.usagemonitor`, e não num
subpacote, para não abrir a visibilidade dos helpers `internal`/`private` que eles usam.

**O `gradle.properties` dá 3 GB ao daemon, e não é paliativo:** sem a folga o build cai em
`OutOfMemoryError: GC overhead limit exceeded` — falta heap para o módulo, não para um método. Medida
em [`main-refatoracao-298-execucao.md`](docs/planos/main-refatoracao-298-execucao.md) (M7).

## Regras de arquitetura e tamanho

Impostas por `ArchitectureRulesTest` (`src/desktopTest/.../architecture/`), que roda no `allTests`
— a regra não depende de revisão lembrar dela.

- **Comportamento nativo medido numa plataforma só nasce restrito a ela** (issue #340): chamada ao
  sistema de janelas medida só no Windows entra atrás de função pura com teste da lista de
  plataformas (precedente: `hudUsesHitRegion`); liberar outra plataforma é decisão com medição.
  `window.shape`/`setShape` só em `HudWindow.kt` e `DesktopWindowFrame.kt` — outro arquivo falha.
- **Direção das camadas por import**: `domain` não importa Ktor, Compose, `kotlinx.serialization`,
  `java.io`, `data` nem `presentation`; `data` não importa `presentation` nem Compose;
  `presentation` não importa `data`. Quando a apresentação precisa de algo de `data`, o contrato
  sobe para o domain como porta e `data` o implementa (precedente: `UsageExportEncoder`).
- **Arquivo de produção ≤ 800 linhas; função ≤ 300**, medida por varredura de chaves que ignora
  comentário e string. Nada de arquivo-deus nem composable-deus: estado, efeitos e ações de uma
  janela moram em arquivos próprios, e um host compõe.
- **Sem exceções:** `FILE_CEILINGS`/`FUNCTION_CEILINGS` estão vazias e **exceção nova não entra** —
  divida o arquivo (histórico em `docs/planos/divisao-arquivos-grandes-302-309-execucao.md`). Entre 750 e 800 linhas (`DashboardViewModel`, `LocalCliSessionDataSource`,
  `ApiUsageCardFormatting`, `AutoStartManager`), a próxima mudança começa extraindo, não crescendo.

## Integração com time (`server/`)

Recurso opcional, servidor Node.js self-hosted (Express 4 + TypeScript + SQLite). Contrato da API e
deploy em [`server/README.md`](server/README.md); decisões e histórico em
[`docs/team-integration.md`](docs/team-integration.md). **Leia antes de mexer em cliente ou
servidor do time.** Regras:

- Chave de agrupamento é o `accountUuid`, sem `organizationUuid`.
- Chave de time é por pessoa; se amarra a conta por `POST /v1/claim` ou ingest. **Nenhum `GET`
  reivindica.** `label` com e-mail é portão (`TEAM_KEY_LABEL_MATCH`, nasce `strict`).
- `TEAM_ADMIN_TOKEN` lê e é o portão único dos `DELETE` (`requireAdminToken` não se toca);
  `TEAM_REPORT_TOKEN` é leitura global sem poder destrutivo; nenhum dos dois escreve no ingest.
- Apagar conta: ordem **dados → vínculo → bloqueio**; bloqueio checado antes da credencial na escrita.
- Configuração do cliente em `~/.usage-monitor/team.json` (segredo), nunca em `PreferencesSettings`.
- Envio pelo `TeamSyncService` (30 s), indexa antes de enviar, conexão SQLite compartilhada;
  apelido viaja no ingest. Renomear nunca duplica: chave `(account_key, device_id)`.
- Servidor **não precifica** (exceto `/metrics`); o cliente aplica `ModelPricingTable`.
  `tools/ci/check-pricing-parity.mjs` compara as duas tabelas — o formato das listas é contrato.
- Rota nova contra servidor antigo: 404 lembrado **por URL**, só o 404 cai no fallback. Campo novo
  em rota existente: sem gate.
- Relógio: presença usa o offset medido do servidor, nunca `Clock.System.now()` cru.
- `until` é semiaberto. Métricas: BigInt, gauges de janela, sem `session_id`/`cwd` como rótulo.
- Ordem das listas do time: total, determinística, sem carimbo de tempo no comparador.
- Nunca trafega conteúdo de prompt ou resposta.

## Sistema visual

Refatoração de agosto de 2026, inspirada na linguagem do OpenCode. O plano de execução com o
histórico das decisões está em [`docs/planos/refatoracao-visual-opencode-execucao.md`](docs/planos/refatoracao-visual-opencode-execucao.md).

### Design system — precedência

**A fonte de verdade visual é [`docs/design-system/`](docs/design-system/).** Ali moram os tokens
(`tokens/*.css`), as primitivas publicadas — cada uma com o contrato escrito em
`components/**/*.prompt.md` — e as regras de conteúdo, iconografia e fundação do
[`readme.md`](docs/design-system/readme.md). O protótipo aprovado,
[`docs/planos/prototipo-visual-opencode.html`](docs/planos/prototipo-visual-opencode.html), continua
sendo o mockup obrigatório de **cada tela** — layout, colunas, estados —, mas deixou de ser a
especificação de token, primitiva e copy: o design system foi derivado dele e o descreve com mais
precisão.

| Divergência | Quem vence | O que fazer |
|---|---|---|
| Compose × design system | design system | corrigir o Compose |
| Compose × protótipo (layout de tela) | protótipo | corrigir o Compose |
| Design system × protótipo | design system | corrigir o protótipo, no mesmo commit |
| Design system tecnicamente errado | o Kotlin | corrigir `docs/design-system/`, com a decisão registrada |

**Nenhuma tela reimplementa uma primitiva.** Antes de escrever `Surface`, `Card`, `Modifier.border`,
`.background` com cor de superfície ou `RoundedCornerShape`, procure em
`presentation/ui/components/` — `AppStructure.kt`, `AppControls.kt`, `AppStates.kt` e os vizinhos
`AppTabs.kt`, `AppSettingsNav.kt`, `AppSurfaceDepth.kt`, `AppChips.kt`, `AppMenu.kt`, `AppTooltip.kt`
e `AppStatusPill.kt`. Se a primitiva não existir, o commit que a cria e o commit que a consome são o
mesmo: primitiva sem adoção não conserta nada (histórico em
[`compose-implementation.md`](docs/design-system/compose-implementation.md)).

**Cor de acento sai de `AppAccents.current` e de `AppTone`**, nunca de `darkAppAccents` ou
`lightAppAccents` diretamente. Um `val` de topo de arquivo é resolvido uma vez por processo e não lê
o tema em vigor: congelar a variante escura faz o valor cair a 2,64:1 contra a `surface` clara, que é
o que `AppAccentsContrastTest` existe para impedir.

**Toda alteração de tela é registrada nos dois documentos, no mesmo commit da mudança.** A regra de
precedência acima só se sustenta enquanto os dois descreverem o app inteiro; desatualizado, cada um
vira ponteiro para um documento que não descreve mais o produto.

- **No protótipo:** tela nova ou estado que ainda não existe ganha seção `<h2 id="…">N · …</h2>`
  própria mais o link em `nav.index`; controle, coluna ou texto novo dentro de tela já desenhada vira
  linha no mockup dela; risco conhecido e decisão pendente vão para `§15 #checklist`.
- **No design system:** primitiva nova ou contrato alterado vira `components/<grupo>/<Nome>.prompt.md`
  mais a entrada no índice do `readme.md`; tela que tem kit em `ui_kits/desktop-app/` tem o `.jsx`
  correspondente atualizado.

Vale para qualquer superfície visível — janela, diálogo, faixa, bandeja e relatório PDF.

**Tokens, profundidade, motion, modais, tipografia e primitivas**: decisões e histórico em
[`docs/design-system/compose-implementation.md`](docs/design-system/compose-implementation.md).
Regras:

- Tokens em `AppTheme.kt`: superfícies (`AppSurfaces`), raios 4/6/8/10 com **teto 10**,
  `AppDepth` (5 patamares), `AppSpacing`, `AppMotion`. `AppSurfaceLadder` é derivado, nunca retocado.
- Motion: tween para cor/opacidade, mola para posição/tamanho; só `GENTLE`/`SNAPPY`/`EXPRESSIVE`;
  **sem overshoot em dado**. `AppMotionPolicy` nasce `Static`; só o `Main` passa a preferência, e
  **a todas as janelas**.
- Modais: todas por `AppDialogWindow`; a janela nasce na primeira abertura (ou no pré-aquecimento
  opt-in, só Windows) e depois só se esconde;
  pedido por `StateFlow`; nome na trilha é fixo (`diagnosticName`). Diálogo interno é `AppDialog`,
  nunca `AlertDialog`.
- Tipografia: Plex Mono em `label*`/`title*`/`headline*`/`display*`, Plex Sans em `body*`; carga do
  classpath, **nunca** `composeResources`. Número é `label*`.
- Primitivas são stateless. Aba × segmentado × chip têm papéis distintos. Cor nunca informa sozinha. Acento é
  identidade de fonte. `AppSwitch` ligado é verde.

**Antes de escrever teste de tela, leia "Armadilhas de teste de tela"** em
[`compose-implementation.md`](docs/design-system/compose-implementation.md) — seis defeitos
(`FlowRow`+`weight`, semântica de ícone, placeholder, altura da cena, `modifier` do campo, borda) que
já custaram uma suíte vermelha cada.

**Janelas, cards e tooltips**: decisões e histórico em [`docs/presentation.md`](docs/presentation.md),
seção "Sistema visual — janelas, cards e tooltips". **Leia a seção antes de mexer.** Regras:

- Escala da interface troca só a **densidade**, nunca `fontScale`. Padrão persistido 115, default do
  parâmetro 100 (geradores de captura e testes). **Cada** `Window`/`DialogWindow` recebe o valor.
  Gravar no commit com debounce. `AppThemeScaleTest` mede pixel.
- Monitores: `workAreaForPosition` (`ScreenLocator.kt`) encaixa no monitor de maior interseção, nunca
  `defaultScreenDevice`. Escalas diferentes por monitor só se validam em máquina real.
- **A barra HUD é o único modo de visualização** (plano `hud-modo-unico-execucao.md`): saíram a janela
  principal com o dashboard, o modo somente cards, o menu de modos (`WindowMode`), "Manter sempre
  visível" e `Ctrl+Shift+H`/`Ctrl+Shift+M`. Não reintroduzir seletor de modo nem segunda moldura.
  `DashboardScreen` e a grade de cards só existem para testes e geradores de captura.
- Tooltip de cota não abre abaixo de 320dp de card (`shouldShowQuotaTooltip`, constante própria, não
  `NarrowCardWidthThreshold`); a `testTag` do bloco de cota mora no conteúdo; a explicação do
  semáforo é o `footnote` da tooltip. Cabeçalho tem ponto **e** palavra do pior risco
  (`worstQuotaRisk`, pela ordem do enum); sem projeção conhecida, sem badge.
- Saldo pré-pago (`hasKnownResetAt = false`, persistido em `has_known_reset_at`) usa **tempo de
  autonomia** (`BALANCE_*_RUNWAY_MILLIS`, 7/14 dias), nunca a razão até o reset.
- Aviso de fonte é hint (`CardNoticeHint`), nunca `AppBanner`: um ícone por card, sem piso de
  largura, frases inteiras no `contentDescription`.

**Barra HUD — notch** (`HudWindow.kt`, `HudNotch.kt`, `HudBalloon.kt`, `HudHandles.kt`,
`HudNotchGeometry.kt`, `HudModel.kt`; issue #164): decisões e histórico em
[`docs/hud-notch.md`](docs/hud-notch.md). **Leia antes de mexer na HUD.** Regras:

- A HUD está sempre composta — não há `hudMode`; `HudEdge` é enum próprio. Sem API habilitada as
  Configurações abrem sozinhas uma vez por arranque (`OpenSettingsWithoutApis`).
- Um anel por conta, até três arcos; janela mais longa por fora; uma linha por anel com janela e
  percentual; palavra do **pior** risco. Faixa compacta acima de 45% da borda.
- O notch não cresce: o balão é de **uma** conta (hover no anel) ou da engrenagem (hover ou clique,
  que só abre — #317). Botões do card têm dona única (`cardActionsFor`).
- **O tamanho é da geometria** (`hudNotchSizes`), nunca medido da composição; 1dp de folga por texto
  (`HudNotchTextFitTest`). Janela com origem e tamanho fixos ao abrir; área de clique recortada por
  `Window.shape` **só no Windows** (`hudUsesHitRegion`, #340); nenhum redimensionamento AWT por quadro.
- Arrasto só pela mão, medido por `hudDragWindowBounds`; posição é borda + fração + monitor; parado
  mora na área útil, fora da barra de tarefas.
- Clique num anel recoleta aquela conta; botão direito não faz nada. Atualização pendente é o
  ponto da engrenagem; reiniciar só pelo botão do balão.
- Movimento contínuo (órbita de sessão, pulso de atenção) só atrás de `AppMotionPolicy.continuous`.
- Balão é conteúdo da janela, nunca `Popup`; nenhum formato novo de percentual/reset/rótulo.

**Regras que continuam valendo**: nenhuma composable nova em `runUsageMonitor` — ele só compõe os
hosts; nenhum `Column + verticalScroll` vira `LazyColumn`; nenhum valor novo em enum existente.

**Marca**: `tools/brand/render_icons.py` gera PNG, ICO e ICNS do ícone Gargantua com o nome (I10),
desenhado **por tamanho lógico**: ≥ 96 px nome inteiro, 32–64 px `U·M`, ≤ 24 px só o núcleo. Janela
lê `app_icon_window.png`, bandeja `app_icon_tray.png` — nunca o de 512 px, que traz texto. O `.icns`
só é validado no job `build-macos` do release.

**Relatório PDF**: a IBM Plex Mono vai embutida (`PDType0Font.load` com subconjunto) e o
saneamento WinAnsi de `UsageReportDocument.sanitized` **permanece** — a fonte tem os acentos, mas
trocar o contrato de caracteres é outra migração. Sem o recurso no classpath, o relatório cai para
Helvetica em vez de falhar.

## CI e testes

Decisões, medições e incidentes em [`docs/build-and-release.md`](docs/build-and-release.md), seção
"CI e testes — decisões". Regras:

- **Plataformas** ("Platform reality" no `CONTRIBUTING.md`, #342): a suíte só roda no Windows e nenhum
  teste exercita o sistema de janelas. Mudança em host de janela é aberta no Linux (X11) antes do
  merge, ou o PR diz o risco; o PR diz em que plataformas foi testado.

- Workflows (#344): `ci.yml` único — job `changes` recorta por path, jobs em paralelo só do que
  mudou, **`ci-ok` é o check único** (pulado conta como sucesso); `release-linux.yml` só por tag,
  **versão vem da tag** (`-PappVersion`), sem commit de bump. Cache do Gradle pela
  `gradle/actions/setup-gradle`, gravado só na `main` — tag e CodeQL só leem.
- CI roda com `-PtestForks=3`; localmente 1. Com forks, `extractSkikoNative` roda antes. **Verde
  local e vermelho no CI em teste de UI: olhe o `~/.skiko` antes do teste.**
- Teste de tela usa `ScreenTestTheme` (`Reduced`); teste de primitiva que anima usa `AppTheme`.
  Teste de tela novo vai no arquivo da tela dele.
- Job que pulou a suíte diz que pulou (`tools/ci/test-summary.mjs --require`).
- **`delay` em `runTest` é tempo virtual**: espera de estado de view model usa `yield()` +
  `Thread.sleep` (`pauseForBackgroundWork`). `awaitSettledState` espera a coleta inteira
  (`refreshingTargets` vazio), não o primeiro `Success`.
- Cobertura opt-in por `-Pcoverage`, sem piso.
- Classe `open` com todo método `open` sobrescrito no teste: cobertura alta sem costura testada.

## Convenções de código

- **Nomes em inglês**, comentários em português.
- Evitar scope functions aninhadas (`let`, `apply`, `run`). Preferir fluxo explícito.
- Commits: Conventional Commits em inglês + `Co-Authored-By: Claude <nome do modelo> <noreply@anthropic.com>`
  — o nome do modelo que gerou o commit (ex.: `Claude Sonnet 5`, `Claude Opus 5`), não um valor fixo:
  planos em `docs/planos/` já registram qual modelo executou cada atividade, e o trailer do commit é a
  mesma informação em outro lugar.
- **Uma atividade, um commit.** Cada unidade de trabalho fecha sozinha: código, teste e documentação
  da mesma decisão entram juntos. Commit que só compila com o próximo não é atômico, e commit que
  junta duas decisões impede reverter uma sem perder a outra.
- **Issue ou comentário criado no GitHub abre com `🤖 Escrito por Claude Code, a pedido de
  @<usuário>`.** O `gh` CLI fica autenticado com a conta pessoal do usuário, não com uma conta ou bot
  próprio de Claude — sem a linha, o texto aparece publicamente como se o usuário tivesse escrito, o
  que já causou confusão (issue #124). Vale para `gh issue create`/`gh issue comment`/`gh api` sobre
  `issues/comments`; não muda a autoria de commit, que já tem o trailer acima.
- **Trabalho com plano em `docs/planos/` mantém ali a tabela de pontos de situação**, uma linha por
  atividade, escrita **no mesmo commit** da atividade que ela descreve — em commit separado a linha
  pode existir sem a mudança e vice-versa, e o registro deixa de servir para auditoria. Cada entrada
  carrega o comando que rodou e o resultado, nunca a intenção.

## Endpoints externos

| API | Endpoint | Auth |
|---|---|---|
| Anthropic | `GET https://api.anthropic.com/api/oauth/usage` | `Authorization: Bearer {accessToken}` do credentials.json + `anthropic-beta: oauth-2025-04-20` |
| Anthropic (renovação) | `POST https://platform.claude.com/v1/oauth/token` | Corpo JSON com `grant_type`, `refresh_token`, `client_id` e `scope` — sem `client_id` responde 400 `Invalid request format` |
| MiniMax | `GET https://www.minimax.io/v1/token_plan/remains` | `Authorization: Bearer {apiKey}` lida de `~/.usage-monitor/api-keys.json` |
| OpenCode Go | `GET https://opencode.ai/zen/go/v1/usage` | `Authorization: Bearer {apiKey}` lida de `~/.usage-monitor/api-keys.json` — a mesma chave do `chat/completions` do Zen |
| Cursor | `GET https://cursor.com/api/usage-summary` (rota não documentada) | Cookie `WorkosCursorSessionToken` montado da sessão do editor em `state.vscdb`, lida em read-only e nunca persistida |
| Antigravity CLI | `agy --sandbox --print-timeout 30s --output-format json --print /usage` (processo local) | Sessão já autenticada do próprio CLI; o app nunca inicia login |
| Telegram (bot, opcional) | `https://api.telegram.org/bot{token}/getUpdates`, `sendMessage`, `deleteWebhook` | Token do bot em `~/.usage-monitor/telegram.json`; long polling de 25 s com timeout estendido |

Formato das respostas (unidades, campos, códigos de erro): [`docs/integrations.md`](docs/integrations.md#formato-das-respostas).
