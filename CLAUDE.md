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
  `restoreMainWindow`. `activateWindow` alterna `alwaysOnTop` `false → true → anterior`.
- Atualização automática: SHA-256 contra o `digest` da API do GitHub; flags de build e piso de
  versão por plataforma; Windows só NSIS per-user; Linux só árvore XDG gerenciada. Texto de
  reinício diz **o que** reinicia. Progresso é texto, não animação.
- Novidades: gatilho é `CURRENT_APP_VERSION` ≠ `releaseNotesSeenVersion`, **nunca** o recibo.
  Ordenação de versões só em `AppVersionComparison.kt`.
- Ajuda: passos citam o **rótulo real** do controle; GIF animado pelo `Codec` do Skia com cópia
  imutável dos bytes; laço de quadros em `desktopMain`; `gradlew.bat generateHelpMedia` regenera.

### Injeção de dependências

Manual, sem framework. `AppGraph.kt` monta data sources, repositórios e use cases (sequência
`HttpClient(OkHttp)` → datasources → repos → use cases); `AppViewModels.kt` monta os view models e é
o **dono único do encerramento** (`shutdown()`, idempotente), chamado pela saída do app, pelo
`onDispose` da composição e pelo shutdown hook — antes eram três cópias divergentes, o hook não
fechava o índice do Codex e a saída pela janela não fechava o `profileRegistry`.
`Main.kt` só faz o arranque e compõe os hosts (`MainWindowHost`, `ModalWindowsHost`,
`SettingsWindowHost`, `AppTrayHost`, `HudWindowHost`); estado de shell e de modais mora em
`AppShellState`/`AppModalState`. Os arquivos ficaram no pacote `com.usagemonitor`, e não num
subpacote, para não abrir a visibilidade dos helpers `internal`/`private` que eles usam.

**O `gradle.properties` dá 3 GB ao daemon, e isso não é mais paliativo de um método gigante.** O
`main()` de ~2.400 linhas foi quebrado (#298), e o build sem a folga continua em
`OutOfMemoryError: GC overhead limit exceeded`, agora num composable de ~165 linhas: falta heap para
o módulo, não para um método. Medida e números em
[`main-refatoracao-298-execucao.md`](docs/planos/main-refatoracao-298-execucao.md).

## Regras de arquitetura e tamanho

Impostas por `ArchitectureRulesTest` (`src/desktopTest/.../architecture/`), que roda no `allTests`
— a regra não depende de revisão lembrar dela.

- **Direção das camadas por import**: `domain` não importa Ktor, Compose, `kotlinx.serialization`,
  `java.io`, `data` nem `presentation`; `data` não importa `presentation` nem Compose;
  `presentation` não importa `data`. Quando a apresentação precisa de algo de `data`, o contrato
  sobe para o domain como porta e `data` o implementa (precedente: `UsageExportEncoder`).
- **Arquivo de produção ≤ 800 linhas; função ≤ 300**, medida por varredura de chaves que ignora
  comentário e string. Nada de arquivo-deus nem composable-deus: estado, efeitos e ações de uma
  janela moram em arquivos próprios, e um host compõe.
- **As exceções eram uma lista congelada com teto exato** (`FILE_CEILINGS`/`FUNCTION_CEILINGS`), e
  **as duas estão vazias desde as issues #302–#309**. O mecanismo continua no teste, mas não há
  item para ele congelar: arquivo ou função acima do limite falha direto. **Exceção nova não entra
  na lista** — divida o arquivo. Vários arquivos ficaram entre 750 e 800 linhas
  (`DashboardViewModel`, `LocalCliSessionDataSource`, `ApiUsageCardFormatting`, `AutoStartManager`):
  a próxima mudança neles começa extraindo, não crescendo.

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
`AppTabs.kt`, `AppSettingsNav.kt`, `AppSurfaceDepth.kt`, `AppChips.kt`, `AppMenu.kt` e
`AppTooltip.kt`, que saíram dos dois primeiros pelo limite de 800 linhas (#308). Se a primitiva não
existir, o commit que a cria e o commit que a consome são o mesmo — primitiva construída e não
adotada não conserta nada, e é exatamente assim que `AppWindowScaffold`, `AppToolbar`, `AppTooltip` e
`AppEmptyState` ficaram meses com adoção zero.

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
- Modais: todas por `AppDialogWindow`; a janela nasce na primeira abertura e depois só se esconde;
  pedido por `StateFlow`; nome na trilha é fixo (`diagnosticName`). Diálogo interno é `AppDialog`,
  nunca `AlertDialog`.
- Tipografia: Plex Mono em `label*`/`title*`/`headline*`/`display*`, Plex Sans em `body*`; carga do
  classpath, **nunca** `composeResources`. Número é `label*`.
- Primitivas stateless em `presentation/ui/components/App*.kt` — procure antes de desenhar
  retângulo. Aba × segmentado × chip têm papéis distintos. Cor nunca informa sozinha. Acento é
  identidade de fonte. `AppSwitch` ligado é verde.

**Armadilhas pagas uma vez cada** — todas custaram uma suíte vermelha:

1. `weight` dentro de `FlowRow` não tem referência de largura: o Compose deixa o filho **sem
   posicionar** e o sintoma é `assertIsDisplayed` falhando com `boundsInRoot` válido.
2. Ação que virou ícone precisa de `contentDescription` na **semântica**, não só de `onClickLabel` —
   é `onNodeWithContentDescription` que as suítes usam. `AppIconButton` já traz os dois.
3. `BasicTextField` mescla descendentes: o placeholder precisa de `clearAndSetSemantics`, ou o campo
   vazio passa a "conter" o texto de exemplo e duplica nós para o `onNodeWithText`.
4. Tela que ficou mais alta obriga a subir a altura da **cena** do teste de componente (1024 × 768
   por padrão), nunca a do `Box` interno — o `Box` não é o que limita o `LazyColumn`.
5. O `modifier` de um campo composto desce até o `BasicTextField`, não fica na coluna: ele carrega a
   `testTag`, e `performTextInput` exige o `RequestFocus` que só o campo tem.
6. `Modifier.border` arredonda o traço **para cima** (`ceil(width.toPx())`, `Border.kt`) e o pinta
   **depois** do conteúdo. Numa caixa de 4dp o anel de 1dp vira 2px a partir de densidade 1,05 e come
   a caixa inteira: a barra de cota ficava cinza com a cota em 37% nas escalas de 105% e 110%
   (issue #83). Borda que precisa ocupar layout é **fundo mais padding** — o `roundToPx` do padding
   acompanha a altura, e é o `box-sizing: border-box` que o protótipo já especificava. O defeito é de
   **pintura**: `boundsInRoot` devolvia a altura cheia nas duas escalas, então só bitmap
   (`captureToImage`) o pega.

**Escala da interface** (`AppTheme(uiScalePercent = …)` + `UiScalePreferences.kt` + slider na aba
Geral): a escala troca a **densidade** da composição, nunca a escala tipográfica. Subir só a
tipografia deixaria ícone, padding, altura de linha e alvo de clique — todos `Dp` fixos — do tamanho
anterior, e o rodapé continuaria pequeno ao lado de um texto maior; densidade é o único ponto em que
dp e sp crescem juntos e as proporções do protótipo permanecem. Só `density` é multiplicado:
multiplicar `fontScale` junto aplicaria a escala duas vezes ao texto.

- **O padrão persistido é 115 e o default do parâmetro é 100.** O neutro existe para o
  `ScreenshotGenerator`, o `TourGifGenerator` e os testes de componente manterem a geometria de
  referência — as capturas do README **não** são geradas na escala do app.
- **Cada janela precisa receber o valor.** `Window`/`DialogWindow` do Compose Desktop têm composição
  própria e a plataforma reprovisiona `LocalDensity` na raiz de cada uma: provisionar na janela pai
  não atravessa para a filha, e a janela esquecida renderiza a 100% sem erro nenhum.
- **A moldura não escala sozinha.** Densidade maior mostra o mesmo conteúdo maior dentro da mesma
  janela, ou seja, menos conteúdo. `scaledWindowSize` corrige a janela principal pela razão entre a
  escala aplicada e a nova — nunca contra 100, ou duas mudanças seguidas multiplicariam duas vezes —
  e nos tamanhos default das outras janelas o fator entra na criação. Tamanho **persistido** é
  escolha do usuário e não é reescalado, com uma exceção de uma vez só: quem já tinha janela salva
  antes desta versão a recebe corrigida de 100 para o padrão novo, e é `hasPersistedUiScale` — chave
  presente, não valor igual ao default — que fecha essa porta depois.
- O redimensionamento acontece no commit do coletor com debounce, não no callback do slider: janela
  AWT reposicionada por pixel arrastado é inutilizável. O conteúdo, esse, escala ao vivo.

**Monitores** (`ScreenLocator.kt`; issue #273): toda medida de tela lia o monitor padrão
(`defaultScreenDevice`, `maximumWindowBounds`). As janelas com posição salva (Histórico, Sessões CLI,
Uso e Presença do time) eram presas ao primário ao reabrir, e a principal nem guardava posição. Agora
`workAreaForPosition` encaixa a janela na área útil do monitor que contém o retângulo salvo (a maior
interseção), e a principal grava `windowX`/`windowY`, negativos inclusive. Sem posição, ou com o
retângulo fora de todo monitor, vale o padrão. As funções de escolha recebem a lista de monitores e são
puras (`ScreenLocatorTest`, com monitor à direita, à esquerda e acima). **Monitores com escalas
diferentes só se validam em máquina real**: cada monitor tem o próprio espaço de usuário escalado e o
app trata pixel como dp. Os testes não pegam isso.
- O teste que prova a fiação (`AppThemeScaleTest`) mede **pixels** (`boundsInRoot`), não `Dp`: a
  conversão para `Dp` usa a densidade do próprio nó, que é a que está sendo alterada, e devolveria
  100dp nos dois casos — um teste que passa sem medir nada.

**Densidade do dashboard** (`DashboardScreen.SuccessContent` + `ResponsiveDashboardCardGrid`): a
janela principal usa o **corpo denso** do protótipo — `AppSpacing.md` na horizontal, `AppSpacing.sm`
na vertical e `AppSpacing.md` entre cards —, e não o `AppSpacing.lg` das outras cinco. É a única
janela que o usuário deixa estreita ao lado do editor, e ali 16dp de margem mais 16dp de vão eram
largura que faltava dentro do card. A coluna rolável **não** reserva folga para a barra de rolagem:
ela flutua sobre o padding direito da grade. Somadas, as duas davam 28dp à direita contra 16 à
esquerda.

**Movimento da grade** (`ResponsiveDashboardCardGrid` + `previewCardOrder`): os cards deslizam para
a vaga nova pela mola `GENTLE` ao reordenar, ao minimizar um vizinho e na troca de colunas; a
primeira colocação é salto, e por isso as capturas não mudam. Durante o arrasto a grade já é
disposta na ordem em que o card cairia, com as caixas do alvo **congeladas** no início — medir contra
caixas que se movem com a prévia faria o vão pular de lado a cada quadro.

**Modo somente cards** (`DesktopWindowFrame(compact)` + `DashboardScreen(showFooter)` +
`CardsOnlyModePreferences.kt`): a janela sem barra de título e sem rodapé. **Não é valor novo em
enum nenhum** — são dois booleanos, um por moldura, e a preferência é um `Boolean` em
`PreferencesSettings`, ao lado de "manter sempre visível".
- **A faixa de título só é composta durante o hover.** Ela carrega a `WindowDraggableArea`, que usa
  arrasto **imediato**; o card usa `detectDragGesturesAfterLongPress`. Com a faixa presente o tempo
  todo, o arrasto da janela venceria a pressão longa e reordenar o primeiro card seria impossível.
  Invisível ela também não pode ser clicável: um botão de fechar transparente é pior que nenhum.
- **Três saídas, e nenhuma é dispensável**: a faixa, o item na bandeja e `Ctrl+Shift+M`. O modo
  esconde o botão de fechar e a engrenagem; com a janela coberta por outra, só o teclado resta. A
  bandeja também passou a abrir as Configurações, que só existiam no rodapé.
- A escala neutra dos geradores de captura não conhece o modo: `showFooter` é `true` por default, e
  as capturas do README continuam com a moldura inteira.

**Menu de modos no rodapé** (`WindowMode` + `AppMenu` + `FooterBar`; issue #187): o ícone que abre
as três molduras — padrão, somente os cards e barra HUD — com a corrente marcada. Antes dele as duas
molduras reduzidas só eram alcançadas por dois interruptores no meio da seção "Sistema" das
Configurações, por `Ctrl+Shift+M`/`Ctrl+Shift+H` ou pela bandeja, e por isso só eram descobertas por
acidente.
- **`WindowMode` é enum novo, e as preferências continuam sendo dois booleanos.** `cardsOnlyMode` e
  `hudMode` seguem separados em `PreferencesSettings`, e a exclusão mútua continua sendo regra dos
  setters em `AppShellState.kt` (`changeHudMode`/`changeCardsOnlyMode`): o enum descreve o que o **controle** oferece, não como o estado é guardado.
  Os rótulos são os **mesmos** das Configurações — dois nomes para a mesma moldura fariam o passo da
  ajuda apontar para um controle que a tela chama de outra coisa.
- **`AppMenu` é primitiva nova, e é `Popup` com a superfície deste sistema — não o `DropdownMenu` do
  Material.** Aquele traz a própria superfície, o próprio raio, a própria animação de entrada e a
  própria altura de item, e nenhum dos quatro é o deste sistema. O item selecionado carrega **marca
  além do realce**, com o espaço da marca reservado em todas as linhas: sem isso o rótulo da
  selecionada anda para o lado a cada troca de opção.
- **O menu abre para cima quando não cabe abaixo**, e isso está afirmado por teste numa cena de
  240×320dp — o piso de arrasto da janela principal. Popup no Compose Desktop é camada **dentro** da
  janela, recortada pelos limites dela (a #164 pagou isso), e o rodapé é a última linha: um menu que
  só soubesse abrir para baixo nasceria fora da janela.
- **Ele existe só no modo padrão**, porque o rodapé só é composto ali. Os caminhos de volta continuam
  sendo os quatro que já existiam, e nenhum deles some. `onWindowModeChange = null` esconde o
  controle — mesmo padrão de `onOpenAdminOverview`, e é o que mantém os geradores de captura
  intactos.

**Barra HUD — notch** (`HudWindow.kt`, `HudNotch.kt`, `HudBalloon.kt`, `HudHandles.kt`,
`HudNotchGeometry.kt`, `HudModel.kt`; issue #164): decisões e histórico em
[`docs/hud-notch.md`](docs/hud-notch.md). **Leia antes de mexer na HUD.** Regras:

- `hudMode` é booleano, exclusivo com somente cards pelos setters de `AppShellState.kt`; `HudEdge` é
  enum próprio. A janela principal fica escondida com geometria intacta.
- Um anel por conta, até três arcos; janela mais longa por fora; uma linha por anel com janela e
  percentual; palavra do **pior** risco. Faixa compacta acima de 45% da borda.
- O notch não cresce: o balão é de **uma** conta (hover no anel) ou da engrenagem (hover ou clique,
  que só abre — #317). Botões do card têm dona única (`cardActionsFor`).
- **O tamanho é da geometria** (`hudNotchSizes`), nunca medido da composição; 1dp de folga por texto
  (`HudNotchTextFitTest`). Janela com origem e tamanho fixos ao abrir; área de clique recortada por
  `Window.shape`; nenhum redimensionamento AWT por quadro.
- Arrasto só pela mão, medido por `hudDragWindowBounds`; posição é borda + fração + monitor; parado
  mora na área útil, fora da barra de tarefas.
- Clique num anel recoleta aquela conta; botão direito vai a somente cards. Atualização pendente é o
  ponto da engrenagem; reiniciar só pelo botão do balão.
- Movimento contínuo (órbita de sessão, pulso de atenção) só atrás de `AppMotionPolicy.continuous`.
- Balão é conteúdo da janela, nunca `Popup`; nenhum formato novo de percentual/reset/rótulo.

**Piso de largura da tooltip de cota** (`shouldShowQuotaTooltip` em `ApiUsageCardDensity.kt`):
abaixo de 320dp de card o popup não abre. Ele tem piso de 180dp e cinco a seis linhas de métrica, e
a janela do modo somente cards tem ~230dp úteis — ali a tooltip cobre o card inteiro, escondendo
justamente o número que o ponteiro apontava. Constante **própria** e não reuso de
`NarrowCardWidthThreshold`, que coincide no valor mas responde a outra pergunta: uma é sobre apertar
padding, a outra é sobre o popup caber. O preço está aceito: em card estreito não há caminho visual
para a projeção de uso, e ela volta abrindo a janela. Só as tooltips de **cota** caem — as de uma
linha (nome truncado da API, botão de sessão) ficam, porque não cobrem nada.
- **A `testTag` do bloco de cota mora no conteúdo, não no `HoverTooltipBox`.** Presa à tooltip, ela
  desapareceria da árvore junto com ela em card estreito, e os testes que buscam `quotaBlockTag`
  passariam a não encontrar nó nenhum.
- **A explicação do semáforo é o `footnote` da tooltip da cota.** O ponto colorido nunca teve
  tooltip própria — os dois usos de `RiskSemaphoreDot` passam `showTooltip = false`, porque dois
  `TooltipBox` aninhados disputam o mesmo hover —, e por isso a frase de `riskDotTooltipSubtitle`
  não chegava à tela em tamanho nenhum de janela. A métrica `Projeção de uso` continua ao lado: ela
  diz qual é o estado, o rodapé diz o que ele significa.
- **O estado da fonte tem ponto e palavra no cabeçalho** (`API_USAGE_CARD_STATUS_TAG` +
  `worstQuotaRisk`): o `RiskSemaphoreDot` de cada cota é só ponto, e sozinho ele deixava a cor
  informando o estado — que é exatamente o que este sistema visual não faz. O badge resume o **pior**
  risco entre as cotas pela ordem do enum, não pelo percentual: 40% às onze da manhã pode ser pior
  que 80% dez minutos antes do reinício. Cota vencida não entra, e sem projeção conhecida não há
  badge — "Normal" ali seria uma garantia que ninguém deu. Os rótulos saem de `riskLevelLabel`, que
  já existia. O badge inteiro também abre um `HoverTooltipBox` persistente: ele informa a cota que
  determinou o pior estado e reutiliza a explicação da projeção, inclusive para `Normal` — a cota
  deve resetar antes de esgotar — e para `Atenção`/`Crítico` — a cota deve esgotar antes do reset.
- **Saldo pré-pago não usa a régua de razão** (`riskSummary` com `hasKnownResetAt = false`): aquela
  régua pergunta "quanto do tempo até o reset a cota aguenta", e um saldo não reseta. O DeepSeek
  grava `periodEndAt = Instant.DISTANT_FUTURE`, o que dava razão de ~0,002 e fazia **qualquer**
  consumo maior que zero virar `WILL_EXCEED` — card em Crítico permanente, ponto de risco da bandeja
  aceso para sempre e a tooltip prometendo um reset uma linha abaixo de "Saldo não expira"
  (issue #109). Sem reset a pergunta é absoluta: **tempo de autonomia**, com
  `BALANCE_CRITICAL_RUNWAY_MILLIS` (7 dias) e `BALANCE_WARNING_RUNWAY_MILLIS` (14 dias), e a data
  prevista continua preenchida **inclusive em `ON_TRACK`** — ali ela é a resposta a "quando acaba",
  não o aviso. A conta em si já estava certa e não mudou: para `CURRENCY_USD`,
  `calculatePositiveDelta` soma as **quedas** do saldo e `remaining` é o saldo atual.
  - **`hasKnownResetAt` passou a ser persistido** (`usage_snapshots.has_known_reset_at`, `DEFAULT 1`,
    migração por `hasColumn` como a de `account_id`). Sem a coluna o histórico só via `periodEndAt`,
    e ele não distingue "reset distante" de "não existe reset": DeepSeek grava `DISTANT_FUTURE`,
    Kilo e OpenCode gravam o próprio `capturedAt`, os créditos da Anthropic gravam outra sentinela.
    A decisão lê o **último** ponto da série, então a primeira coleta depois da migração já corrige a
    cota — não é preciso reescrever histórico.
  - **`currentSegment` não foi tocado**, e é por isso que Kilo e OpenCode continuam sem projeção
    nenhuma: com `periodEndAt = capturedAt` cada coleta parece um reset, o segmento fica com um ponto
    e o forecast devolve `InsufficientData`. Mesma raiz, sintoma oposto, issue própria — ligá-la aqui
    acenderia projeção em duas fontes sem verificação delas.

**Aviso de fonte é hint, não banner** (`CardNoticeHint` em `ApiUsageCardNotice.kt`): os
`ApiUsageNotice` saem como uma exclamação âmbar (`Icons.Rounded.ErrorOutline`) no cabeçalho, ao
lado do badge de status, e o texto vive na tooltip. Eram `AppBanner` empilhados abaixo das cotas,
e como o texto deles não muda entre coletas, na janela estreita os dois avisos do Codex ocupavam
mais altura que o `39%` que o card existe para mostrar (issue #76). Um ícone por card, não um por
aviso: a tooltip lista todos, com bullet só a partir do segundo.
- **Este hint não tem piso de largura**, ao contrário da tooltip de cota: aquele piso existe porque
  o popup cobre o número que o ponteiro apontava, e este não aponta número nenhum. Sem tooltip o
  aviso ficaria inacessível justamente na janela estreita, que é onde ele mais atrapalhava.
- **As frases inteiras vão no `contentDescription` do ícone.** Sem hover a tooltip não existe na
  árvore, então é por ela que leitor de tela e testes chegam ao aviso — os dois asserts de notice
  em `ComponentTest` usam `onNodeWithContentDescription(..., substring = true)`.

**Regras que continuam valendo**: animação infinita só atrás de `AppMotionPolicy.continuous`
(sem a política ela trava o `waitForIdle`); `ShimmerBox` foi apagado — não tinha chamador; nenhuma composable nova em `runUsageMonitor` — ele só compõe os hosts; nenhum
`Column + verticalScroll` vira `LazyColumn`; nenhum valor novo em enum existente.

**Marca**: `tools/brand/render_icons.py` gera PNG, ICO e ICNS a partir do monograma descrito em
código — `monogram.svg` ao lado é referência e não é lido. O `.icns` só é validado no job
`build-macos` do release.

**Relatório PDF**: a IBM Plex Mono vai embutida (`PDType0Font.load` com subconjunto) e o
saneamento WinAnsi de `UsageReportDocument.sanitized` **permanece** — a fonte tem os acentos, mas
trocar o contrato de caracteres é outra migração. Sem o recurso no classpath, o relatório cai para
Helvetica em vez de falhar.

## CI e testes

Decisões, medições e incidentes em [`docs/build-and-release.md`](docs/build-and-release.md), seção
"CI e testes — decisões". Regras:

- Workflows: `ci.yml` (desktop no Windows + instalador) e `ci-server.yml` (servidor). Cache do Gradle
  pela `gradle/actions/setup-gradle`, gravado só na `main`.
- CI roda com `-PtestForks=3`; localmente 1. Com forks, `extractSkikoNative` roda antes. **Verde
  local e vermelho no CI em teste de UI: olhe o `~/.skiko` antes do teste.**
- Teste de tela usa `ScreenTestTheme` (`Reduced`); teste de primitiva que anima usa `AppTheme`.
  Teste de tela novo vai no arquivo da tela dele.
- Job que pulou a suíte diz que pulou (`tools/ci/test-summary.mjs --require`).
- **`delay` em `runTest` é tempo virtual**: espera de estado de view model usa `yield()` +
  `Thread.sleep` (`pauseForBackgroundWork`). `awaitSettledState` espera a coleta inteira
  (`refreshingTargets` vazio), não o primeiro `Success`.
- Cobertura opt-in por `-Pcoverage`, sem piso. Push na `main` reaproveita a árvore verificada no PR.
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

Response Anthropic retorna `five_hour`/`seven_day` com `utilization` em **percentual** (0–100) e `resets_at` em ISO 8601 (pode ser nulo), mais `extra_usage`/`spend` com os créditos de uso em unidades menores da moeda da conta.

Response MiniMax retorna `model_remains[]` com cotas em **requests** (não tokens), timestamps em epoch milliseconds.

Response OpenCode Go retorna `usage.{rolling,weekly,monthly}`, cada uma com `status` (`ok` ou `rate-limited`), `percent` (0–100) e `resetsAt` em ISO 8601. **Não devolve valor em dinheiro** — nem gasto, nem limite. O endpoint **não está documentado publicamente** (PR anomalyco/opencode#16513, merged em 2026-08-11) e não declara versão; sem `Authorization` responde `401 AuthError`, e com chave válida sem plano Go responde `403 EntitlementError`. O saldo pago do Zen **não tem endpoint**: `/zen/v1/balance` responde 404.
