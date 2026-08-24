# Refatoração visual OpenCode — plano de execução

> Fonte de verdade desta iniciativa. O documento do Codex
> ([`refatoracao-visual-opencode.md`](refatoracao-visual-opencode.md)) passa a ser referência
> histórica: o diagnóstico dele continua válido, o levantamento técnico não.
>
> Este arquivo mora na branch de integração `refactor/visual-opencode` e é atualizado **no commit
> final de cada fase**. Quem retomar a iniciativa lê o Ponto de situação primeiro e não precisa de
> mais nada.

---

## Ponto de situação

**Estado atual:** `Concluída. Doze fases fundidas em refactor/visual-opencode; a main permanece intocada`
**Última atualização:** 2026-08-18
**Branch de integração:** `refactor/visual-opencode` (criada a partir de `origin/main` @ `2947b4d`)
**Worktree:** `C:\Users\edils\workspace\usage-monitor-visual`
**Checkout principal:** `C:\Users\edils\workspace\usage-monitor`, em `main`, intocado

### ★ Protótipo aprovado — referência obrigatória

[`docs/planos/prototipo-visual-opencode.html`](prototipo-visual-opencode.html) — aprovado
integralmente pelo usuário em 2026-08-18: paleta, tipografia, marca, densidade, dashboard, telas
tabulares, configurações, PDF e tema claro.

**Abrir e consultar antes de escrever qualquer código de UI, em toda sessão.** Ele é a
especificação visual desta iniciativa; este plano descreve a execução, não a aparência. Divergência
entre o Compose e o protótipo é defeito do Compose — salvo quando o protótipo estiver
tecnicamente errado, e aí a decisão vai para a tabela de decisões antes de o código mudar.

Cópias vivas: neste repositório (a canônica), em `C:\Users\edils\Desktop\prototipo-usage-monitor.html`
e no artifact `https://claude.ai/code/artifact/c054eb5b-1077-4bdc-8f50-c0e41c80b8f3`.

### ▶ Próxima atividade

**Nenhuma.** A refatoração está completa na branch de integração `refactor/visual-opencode`.

O que falta é decisão do usuário: **abrir o PR para a `main`**. O CI roda `allTests` em
`windows-latest` em todo PR, e o release depende do job `build-macos` para o `.dmg` — é lá que o
`.icns` novo é validado pela primeira vez, o único item desta iniciativa que nenhuma máquina Windows
consegue verificar.

Antes do merge, o que só o usuário pode fazer: rodar `gradlew.bat run` e olhar as seis janelas em
uso real — hover, foco, arrasto de card, redimensionamento, escala do Windows em 150% e as duas
línguas. O que foi verificado aqui está na seção de fechamento abaixo.

### Progresso por fase

| Fase | Branch | Estado | Commits | Concluída em |
|---|---|---|---|---|
| A — Protótipo | — | ✅ aprovado | 1/1 | 2026-08-18 |
| B — Isolamento | — | ✅ concluída | 1/1 | 2026-08-18 |
| C — Fundação | `visual/c-foundation` | ✅ concluída | 8/8 | 2026-08-18 |
| D — Dashboard | `visual/d-dashboard` | ✅ concluída | 5/5 | 2026-08-18 |
| E — Histórico | `visual/e-history` | ✅ concluída | 4/4 | 2026-08-18 |
| F — Sessões CLI | `visual/f-cli-sessions` | ✅ concluída | 4/5 | 2026-08-18 |
| G — Time | `visual/g-team` | ✅ concluída | 2/5 | 2026-08-18 |
| H — Configurações | `visual/h-settings` | ✅ concluída | 1/4 | 2026-08-18 |
| I — Chrome das janelas | `visual/i-window-chrome` | ✅ concluída | 1/2 | 2026-08-18 |
| J — Marca | `visual/j-brand` | ✅ concluída | 1/2 | 2026-08-18 |
| K — Relatório PDF | `visual/k-report` | ✅ concluída | 1/2 | 2026-08-18 |
| L — Capturas e docs | `visual/l-docs` | ✅ concluída | 2/3 | 2026-08-18 |
| M — Fechamento | — | ✅ concluída | 1/1 | 2026-08-18 |

Legenda: ⬜ pendente · 🟡 em andamento · ✅ concluída e fundida na integração · ⛔ bloqueada

### Registro de execução

Uma linha por commit, em ordem cronológica.

| Data | Fase | Commit | Testes |
|---|---|---|---|
| 2026-08-18 | M | `docs(plan): close the visual refactor` | `clean build` + `allTests` (1082, 0 falhas) + `createDistributable` + capturas nos dois temas |
| 2026-08-18 | L3 | `docs: record the visual system and its constraints` | n/a — documentos |
| 2026-08-18 | L1–L2 | `chore(screenshots): regenerate README captures and the tour gif` | `allTests` — verde |
| 2026-08-18 | K | `feat(report): embed IBM Plex in the PDF and apply the new palette` | `allTests` — verde; PDFs curto e longo renderizados em PNG e inspecionados |
| 2026-08-18 | J | `feat(brand): add the deterministic icon renderer and apply the new mark` | `allTests` — verde; folha de contato de 16 a 128px inspecionada |
| 2026-08-18 | I | `refactor(window): standardize the title bar and window frame` | `allTests` — verde |
| 2026-08-18 | H | `refactor(settings): replace the tab chips with side navigation and restyle the controls` | `allTests` — verde; capturas inspecionadas |
| 2026-08-18 | G3–G5 | `refactor(team): restyle trend, presence and the keys admin screen` | `allTests` — verde; capturas inspecionadas |
| 2026-08-18 | G2 | `refactor(team): render member usage as aligned rows` (inclui as âncoras do G1) | `ui.*` — verde |
| 2026-08-18 | F5 | `refactor(cli-sessions): restyle the header, export and glossary surfaces` | `allTests` — verde; capturas inspecionadas |
| 2026-08-18 | F4 | `refactor(cli-sessions): restyle the breakdown pane and activity grid` | `ui.*` — verde |
| 2026-08-18 | F3 | `refactor(cli-sessions): restyle the session detail` | `ui.*` — verde |
| 2026-08-18 | F2 | `refactor(cli-sessions): render the session list as aligned rows` (inclui as âncoras do F1) | `ui.*` — verde |
| 2026-08-18 | E4 | `refactor(history): lay out metrics as a table` | `allTests` — verde; capturas inspecionadas |
| 2026-08-18 | E3 | `refactor(history): restyle the chart panel and tooltip` | `ui.*` — verde |
| 2026-08-18 | E2 | `refactor(history): merge source, account and range into one toolbar` | `ui.*` — verde |
| 2026-08-18 | E1 | `test(ui): anchor history assertions on test tags` | `ui.*` — verde antes e depois |
| 2026-08-18 | D5 | `refactor(dashboard): apply state primitives to loading, empty and error` | `allTests` — verde; capturas inspecionadas |
| 2026-08-18 | D4 | `refactor(dashboard): convert the footer into a status bar` | `ui.*` — verde |
| 2026-08-18 | D3 | `refactor(dashboard): restyle the card chrome and actions` | `ui.*` — verde |
| 2026-08-18 | D2 | `refactor(dashboard): turn quota blocks into compact rows` | `allTests` — verde |
| 2026-08-18 | D1 | `test(ui): anchor dashboard assertions on test tags` | `ui.*` — verde antes e depois, nenhum pixel alterado |
| 2026-08-18 | C8 | `refactor(ui): replace the DepthSurface glow with bordered surfaces` | `allTests` — 1084 testes, 0 falhas, 0 ignorados; capturas geradas |
| 2026-08-18 | C7 | `feat(ui): add state primitives` | `ui.*` — verde |
| 2026-08-18 | C6 | `feat(ui): add control primitives` | `ui.*` — verde |
| 2026-08-18 | C5 | `feat(ui): add structural primitives` | `ui.*` — verde |
| 2026-08-18 | C4 | `feat(theme): tighten radius, elevation, spacing and motion tokens` | `ui.*` — verde |
| 2026-08-18 | C3 | `feat(theme): load IBM Plex and rebuild the type scale` | `ui.*` — verde após ampliar a cena de cinco testes de resumo |
| 2026-08-18 | C2 | `feat(theme): replace the color scheme with a neutral palette` | `ui.*` + `AppAccentsContrastTest` — verde |
| 2026-08-18 | C1 | `chore(assets): add IBM Plex Mono and Sans with the OFL license` | `ui.*` — 199 testes, 0 falhas |
| 2026-08-18 | B | `docs(plan): add the visual refactor execution plan` | `allTests` — 1068 testes, 0 falhas, 0 ignorados |
| 2026-08-18 | A | `docs(plan): add the approved visual prototype` | n/a — documento |

**Contagem de testes: 1068 → 1084.** As dezesseis linhas novas são os testes das primitivas
(`AppStructureTest`, `AppControlsTest`, `AppStatesTest`), acrescentados junto de C5, C6 e C7. O plano
não os previa; primitiva consumida por oito suítes sem teste próprio faz um defeito nela aparecer em
oito lugares com a causa ambígua.

**Linha de base — `allTests` no worktree limpo em `2947b4d`, antes de qualquer edição:
1068 testes, 0 falhas, 0 ignorados, 3m28s.** Toda falha posterior é comparada contra este número;
sem ele, "quebrou agora" e "já estava assim" são indistinguíveis.

### Fechamento — o que foi verificado

| Verificação | Resultado |
|---|---|
| `gradlew.bat clean build` | ✅ |
| `gradlew.bat allTests` | ✅ 1.082 testes, 0 falhas, 0 ignorados |
| `gradlew.bat createDistributable` | ✅ `Usage Monitor.exe` + runtime image gerados |
| `gradlew.bat generateScreenshots` | ✅ nove capturas, determinísticas (segunda execução não muda byte) |
| `gradlew.bat generateTourGif` | ✅ |
| Dashboard, Histórico, Sessões CLI, Resumo, Time, Presença, Configurações | ✅ inspecionadas em captura |
| Tema claro | ✅ dashboard e presença inspecionados; contraste dos acentos coberto por `AppAccentsContrastTest` |
| Relatório PDF curto e longo | ✅ renderizados em PNG e inspecionados: colunas alinhadas, truncamento, cabeçalho repetido na continuação |
| Ícone de 16 a 128px | ✅ folha de contato inspecionada |

**Contagem de testes: 1.068 → 1.082.** As catorze linhas novas são os testes das primitivas; nenhum
teste existente foi removido.

**O que continua sem verificação e por quê:**

- **`.icns`** — nenhuma máquina Windows abre o formato. Validação no job `build-macos` do release.
- **Escala do Windows em 150%, hover, foco, arrasto e redimensionamento** — o `ScreenshotGenerator`
  renderiza offscreen com relógio manual e densidade fixa; nada disso passa por ele.
- **Instalador NSIS** — fora do escopo desta refatoração; nada aqui toca o `.nsi`.

### Decisões tomadas durante a execução

Toda decisão que este plano não previa — desvio de escopo, alternativa técnica, item adiado — com o
motivo.

| Data | Decisão | Motivo |
|---|---|---|
| 2026-08-18 | Gate = protótipo HTML, não fatia vertical em Compose | Escolha explícita do usuário |
| 2026-08-18 | Marca completa, incluindo `.icns` | Escolha explícita do usuário; risco de verificação registrado na Fase J |
| 2026-08-18 | Mono estrutural + Sans em texto corrido | Escolha explícita do usuário, sobrepondo "mono em tudo" do documento do Codex |
| 2026-08-18 | Fase B executada **antes** da Fase A | Este documento tem de viver na branch da refatoração, e `main` não recebe commit nenhum. Criar o worktree antes é o único jeito de o plano existir versionado sem sujar `main`. Nenhum código foi tocado |
| 2026-08-18 | `git branch --unset-upstream` na branch de integração | O `git worktree add -b ... origin/main` deixa a branch nova **rastreando `origin/main`**; um `git push` sem argumento iria para a `main` do remoto. Sem upstream, o push exige destino explícito |
| 2026-08-18 | O script `commit_and_push.ps1` **não é usado** nesta iniciativa | Ele grava a identidade temporária com `git config --local`, e num worktree o `.git` é um arquivo: o comando falha com `could not lock config file .git/config`. Os commits usam `git -c user.name=claude -c user.email=claude@anthropic.com commit`, que não altera configuração nenhuma e por isso dispensa a restauração que o `finally` do script existe para garantir |
| 2026-08-18 | Protótipo **versionado no repositório**, contra o que este plano previa ("fora do repositório") | A regra existia para impedir código Compose antes da aprovação, e isso já aconteceu. Aprovado, ele virou a especificação visual, e especificação que vive só no scratchpad de uma sessão não sobrevive à sessão seguinte |
| 2026-08-18 | TTFs vindos dos **releases do GitHub** (`ibm-plex-mono.zip` @2.5.0, `ibm-plex-sans.zip` @1.1.0), não do npm | Os pacotes `@ibm/plex-*` atuais só publicam `woff`/`woff2`, que o Skia não carrega do classpath. O `LICENSE.txt` é byte-idêntico nas duas famílias, daí um `OFL.txt` só |
| 2026-08-18 | Fonte carregada por `Font(resource: String, …)`, a sobrecarga de classpath | Bloqueio C3 resolvido por inspeção do `ui-text-desktop-1.7.1.jar`: as duas sobrecargas existem. A de `java.io.File` não serve — o jpackage não deixa TTF solto no disco para um caminho absoluto apontar |
| 2026-08-18 | `AppAccentsContrastTest` continua medindo contra `surface`, agora `#1B1818` / `#FFFCFC` | Medir contra `surfaceVariant` seria o pior caso aritmético, mas contra o claro `cacheRead` e `opencode` caem a 4,30:1, e corrigir isso exigiria mexer nos acentos que o protótipo congela. A regra do teste não mudou; mudaram os dois valores |
| 2026-08-18 | Cinco testes do resumo passaram a pedir cena de 1.600px | Quem limita o que o `LazyColumn` compõe é a **cena** do teste (1024 × 768 por padrão), não o `Box` interno. A Plex é mais larga que a fonte de sistema anterior, os rótulos quebram em mais linhas e o último item da página caía fora: os asserts falhavam por viewport, não por comportamento |
| 2026-08-18 | Testes próprios para as dezessete primitivas, que o plano não previa | Defeito em primitiva consumida por oito suítes aparece em oito lugares de uma vez. Foi assim que o sublinhado de `AppTabs` — um `Box(fillMaxWidth)` que esticava a aba inteira e engolia os cliques das vizinhas — apareceu em C5, antes de qualquer tela usá-lo |
| 2026-08-18 | `DepthSurface` **perdeu** `accent` e `glowAlpha` em C8, e a cor da fonte fica ausente até a fase da tela | Manter o parâmetro só para pintar um marcador em toda superfície poria cor onde o protótipo não põe — inclusive nos blocos internos do detalhe de sessão. O marcador entra no lugar certo (o cabeçalho) quando cada tela migrar para `AppSectionHeader` |
| 2026-08-18 | As ações de navegação do card viraram **barra de estado no rodapé do card**, mas continuam botões de **ícone**, não de texto como no protótipo | A `contentDescription` de cada uma carrega a explicação do pisca ("1 sessão ativa agora pede atenção: …"), que é a razão de o semáforo existir e o que os testes observam. Botão de texto não teria onde levá-la |
| 2026-08-18 | O card minimizado mantém os **badges por cota**, em vez do resumo "68% · 41%" do protótipo | A forma do protótipo apaga o rótulo da cota e a tooltip por cota, que são dado e ação existentes. Os badges foram reestilizados (superfície neutra, borda, raio 6) em vez de removidos |
| 2026-08-18 | `compactPercentageLabel` passou a **truncar** em vez de arredondar | Era o único lugar do app que arredondava: o arco fazia `toInt()` e os limiares da bandeja são piso. O mesmo card mostrava 26% na cota expandida e 27% no badge minimizado para o mesmo 12 de 45 |
| 2026-08-18 | `colorFor(UsageRiskLevel)` passou a sair de `AppTone` | Os três literais (`0xFF4CAF50`, `0xFFFFC107`, `0xFFF44336`) nunca foram medidos contra as superfícies dos dois temas — o âmbar dava menos de 3:1 sobre a clara — e escapavam do `AppAccentsContrastTest` |
| 2026-08-18 | As fases I, J, K e L saíram com **menos commits** que o plano previa | Cada uma tinha dois ou três commits que tocavam o mesmo arquivo na mesma região (moldura + constantes, gerador + saída, fonte + paleta, capturas + gif). Separá-los daria commits que não representam estados verificáveis distintos |
| 2026-08-18 | As linhas da tela de presença viraram `AppDataRow` só na Fase L | A Fase G converteu botões e filtro mas deixou as linhas como painéis empilhados; a captura de fim de L mostrou vinte blocos onde as outras listas já eram tabela. Corrigido no commit das capturas, que é onde o defeito apareceu |
| 2026-08-18 | O PDF cai para Helvetica se o TTF não estiver no classpath | Um empacotamento que esqueça a pasta `fonts/` produz um relatório com a fonte errada em vez de nenhum relatório. A largura das colunas se recalcula sozinha; o dado continua correto |
| 2026-08-18 | A Fase H saiu **num commit só**, em vez dos quatro previstos | A navegação lateral e a conversão dos controles (campo com debounce, interruptores, botões, seletor de idioma) tocam os mesmos três arquivos nas mesmas regiões. Quatro commits se sobreporiam sem entregar quatro estados testáveis |
| 2026-08-18 | O `modifier` do `DebouncedTextField` desce até o campo, e não fica na coluna | Ele traz a `testTag`, e a ação de digitar mora no campo: tag na coluna deixa `performTextInput` sem o `RequestFocus` que ele exige |
| 2026-08-18 | A caixa de seleção de participação no time virou interruptor | A linha diz se a conta participa — estado ligado ou desligado, o mesmo que os outros controles da tela dizem, agora com o mesmo desenho |
| 2026-08-18 | G3, G4 e G5 saíram **num commit só** | As três telas do time compartilham os mesmos componentes e a conversão dos `TextButton` foi uma passada única sobre os três arquivos. Separá-las daria três commits que se sobrepõem no mesmo trecho |
| 2026-08-18 | `AppToggleChip` é primitiva nova, fora das dezessete previstas | O filtro "só conectados" é uma restrição ligada ou desligada. Segmentado de duas opções afirmaria uma escolha entre alternativas, e reusar `AppButton` perderia o `selectable` que o teste observa |
| 2026-08-18 | O placeholder do `AppTextField` sai da árvore semântica | O `BasicTextField` mescla descendentes: um campo vazio passava a "conter" o texto de exemplo, e `onNodeWithText` do exemplo encontrava dois nós |
| 2026-08-18 | A linha do integrante do time encolheu de 109dp para 88dp | Deixou de ser card e virou linha de tabela. O assert de altura continua existindo pelo motivo de antes — a linha não pode voltar a crescer — com o número novo |
| 2026-08-18 | F1 e F2 saíram **no mesmo commit** | O F1 seria um commit cujo único conteúdo é uma constante que nada referencia ainda. A prova de neutralidade que a separação existe para dar continua nos testes, verdes antes e depois |
| 2026-08-18 | A lista de sessões **não** ganhou cabeçalho de coluna único; cada célula mantém o próprio rótulo | As células somam quase 1.000dp e a janela abre menor: a linha precisa quebrar, e cabeçalho fixo sobre linha que quebra desalinha. O protótipo resolve com rolagem horizontal, que esconderia colunas dos asserts de componente |
| 2026-08-18 | As células da linha de sessão ficam num `FlowRow`, não numa `Row` | Numa `Row` a última coluna — onde mora o botão de remover do modo administrativo — saía da área visível sem rolagem horizontal para alcançá-la. A suíte do time pegou isso |
| 2026-08-18 | `AppBanner` ganhou uma terceira linha (`detail`) | O aviso de sessão saturada diz três coisas e as três são observadas por teste: veredito, número que o gerou e o que fazer. Emendá-las num texto só quebrou o assert do conselho |
| 2026-08-18 | A tabela de métricas do histórico é `Row` de duas `Column` com `weight`, **nunca** `FlowRow` | Num `FlowRow` a linha mede pelo conteúdo, o `weight` do valor fica sem referência e o Compose deixa o texto **sem posicionar**: `isPlaced` falso, nó presente na árvore semântica e nada desenhado. O sintoma é `assertIsDisplayed` falhando com `boundsInRoot` válido |
| 2026-08-18 | `UsageHistoryLineChart` passou a receber `accentColor` | A identidade é da fonte, e as séries de Anthropic e Codex desenhavam a mesma linha `primary`. A cor que o card usa para distingui-las não chegava ao gráfico |
| 2026-08-18 | O emblema de reinício do gráfico foi de 64dp para 84dp | A largura cabia "Reinício" na fonte de sistema anterior; a IBM Plex Mono é mais larga e a palavra quebrava letra a letra dentro do emblema |
| 2026-08-18 | O `ShimmerBox` saiu da tela de carregamento do dashboard, mas **o componente fica** | Ele é a única animação infinita da app e estava justamente na primeira tela que um teste de componente encontra. Apagar o componente seria remover algo que o plano não mandou remover; deixá-lo em uso é que era o problema |

### Bloqueios e riscos abertos

| Item | Fase | Estado |
|---|---|---|
| Assinatura exata da API de carregamento de fonte no Compose Desktop 1.7.1 | C3 | ✅ resolvido — `Font(resource: String, weight, style)` existe |
| Brilho de acento inline em `ApiUsageCard`, fora do alcance de C8 | D3 | ✅ resolvido — gradiente e rastro de coleta removidos |
| `.icns` não verificável em Windows — só no job `build-macos` do release | J2 | ⚠ risco aceito |
| Embutir Plex no PDFBox recalcula `getStringWidth` de todas as colunas | K1 | ⚠ risco conhecido, commit isolado |

### Ajustes posteriores ao fechamento

A iniciativa está fechada; o sistema visual, não. O que muda a aparência depois do merge entra
aqui, com o motivo — e **o protótipo muda junto**, ou a regra "divergência entre o Compose e o
protótipo é defeito do Compose" fica sem árbitro.

| Data | Origem | O que mudou | Por quê |
|---|---|---|---|
| 2026-08-19 | issue #71 | Dashboard passa a usar o corpo denso: padding `--s3`/`--s2` no lugar de `--s4`, e o vão da grade cai de 16 para 12dp (`AppSpacing.md`). | É a janela que o usuário deixa estreita ao lado do editor. Com 16dp de margem mais 16dp entre cards, a largura que sobrava não bastava para as cotas caberem numa linha. **O vão de 12 já era o do protótipo** (`.g2 { gap: var(--s3) }`) — era o Compose que usava 16. As demais janelas continuam em `--s4`. |
| 2026-08-19 | issue #70 | Modo somente cards: `DesktopWindowFrame(compact = true)` tira a barra de título do fluxo e `DashboardScreen(showFooter = false)` tira o rodapé. A moldura volta como faixa sobreposta enquanto o ponteiro está nos 34dp do topo. | A janela tinha 64dp verticais de cromo permanente para mostrar dois cards. A faixa **só é composta durante o hover**: ela carrega a `WindowDraggableArea`, que usa arrasto imediato, e o card usa arrasto depois de pressão longa — presente o tempo todo, ela venceria a pressão longa e reordenar o primeiro card ficaria impossível. Três saídas (faixa, bandeja, `Ctrl+Shift+M`) porque o modo esconde o botão de fechar. |
| 2026-08-19 | issue #69 | Lista do time: some o vão de 8dp entre itens (cada linha já traz divisória), a faixa da conta ganha `surfaceVariant`, marcador de 2dp, a palavra "Conta" e divisória, e o bloco de sessões ganha `surface` além do recuo. | Conta, integrante e sessão eram três retângulos de mesmo peso empilhados sobre o mesmo fundo. O protótipo já resolvia isso com `.row` sem vão e bloco aninhado com fundo próprio; o Compose é que tinha divergido. **Correção no próprio protótipo:** o bloco aninhado era `--raised`, que é o realce de `.row:hover` — com ele ali, passar o mouse numa sessão deixava de dar retorno. Passou a `--bg` lá e a `--surface` no Compose — a regra é **um degrau de distância do fundo da lista**, na direção que houver —, e `--raised` ficou reservado à faixa da conta e ao hover. |
| 2026-08-19 | captura do usuário | Presença na visão global recebe a mesma faixa de conta da lista do time (`surfaceVariant`, marcador de 2dp, a palavra "Conta", divisória) e perde o vão de 8dp entre itens. A coluna de ação sai do `FlowRow` das colunas e vira coluna fixa à direita, na faixa e na linha; a janela ganha largura mínima de 940dp. Protótipo ganhou a seção **10b · Presença — todas as contas**, que não existia. | O modal "Conectados agora — todas as contas" ficou de fora da passada da issue #69 e continuava com conta e integrante como retângulos de mesmo peso. Na mesma faixa, o botão de apagar conta era o último item do `FlowRow` e portanto o primeiro a quebrar: em ~800dp ele caía para uma linha própria e virava um ícone vermelho solto abaixo do e-mail. A largura mínima existia como comentário de orçamento e não como código — quem arrasta a borda é o usuário. |

Uma decisão de ordenação entrou junto, e ela não é visual: as contas da visão global passaram a sair
em ordem alfabética pelo e-mail, com o consumo ordenando dentro de cada uma. Ordenar as contas por
consumo fazia a mesma conta subir e descer a lista entre dois tiques do laço de 5s.

### Passada de conformidade — 2026-08-23

Levantamento a pedido do usuário: **a fundação (Fase C) foi construída e a adoção nas telas ficou
parcial.** `AppWindowScaffold` e `AppToolbar` tinham **zero** usos em tela nenhuma;
`AppStatusIndicator` tinha um. E havia regras do próprio sistema violadas em produção — acento como
cor de valor no modal do time, elevação 6/10dp em três lugares, dois gradientes, superfície com
alpha, número em IBM Plex Sans nas duas listas tabulares, e as cinco abas das Configurações sem
painel, sem divisória e com rótulo em sans.

Escopo executado: regras **e** anatomia. Onze commits, `allTests` verde em cada fase.

| O que mudou | Por quê |
|---|---|
| Elevação, gradiente e superfície com alpha voltam aos tokens | Os cards de histórico da DeepSeek e do OpenCode nunca passaram pela Fase E — aquela tela só é composta com essas fontes selecionadas, e nenhuma captura passava por ali. As duas tooltips de gráfico flutuavam mais alto que a tooltip padrão do app |
| O acento sai da cor de valor no modal do time | `CliSessionsHeader` já tinha a decisão oposta, com o comentário explicando. As duas telas dizem a mesma coisa e liam diferente |
| Valor numérico passa a `label*` (mono) nas duas listas | `body*` é sans e existe para texto corrido; número em fonte proporcional não alinha coluna, que é a razão de a mono estar na escala |
| Badge de estado com **ponto e palavra** no cabeçalho do card | O único indicador de risco do dashboard era o `RiskSemaphoreDot`, um ponto colorido sem palavra nenhuma — cor informando sozinha |
| O card ganha a anatomia de painel: cabeçalho com divisória, cotas como linhas de dados | O padding num bloco só em volta de tudo fazia a divisória parar a 12dp de cada lado, e a lista de cotas deixava de ler como tabela |
| `AppWindowScaffold` e a barra de estado entram no Histórico, nas Sessões CLI e no modal do time | O aviso de recarga aparece e some a cada troca de janela; no topo, ele deslocava para baixo tudo o que estava sendo lido |
| Totais viram blocos de métrica, e `MetricCard` sobe para `AppMetricBlock` | Primitiva consumida por três telas morava num arquivo de tela — o padrão que a Fase C existiu para acabar |
| As cinco abas das Configurações viram painéis com cabeçalho e linhas de dados | Sete controles empilhados sem divisória e todo rótulo em sans: achar uma opção era ler a lista inteira |
| Os dois controles deslizantes recebem trilha e polegar do sistema | Trilha de 16dp, indicadores de parada e polegar em cápsula — três coisas que este sistema não tem em lugar nenhum. A semântica de progresso continua vindo do `Slider` |
| `AppSwitch` ligado passa de `primary` para o verde de estado | O azul deste sistema é informação — linha de gráfico e realce de seleção —, e com ele ali um interruptor ligado lia como item selecionado |
| Sai o Material residual: `TextButton`, `IconButton`, `OutlinedTextField`, `Checkbox`, `HorizontalDivider`, `Card` e 85 imports mortos | Cada um trazia altura, raio e cor próprios ao lado de controles que já são retângulo de raio 6 |

**Duas mudanças de texto de interface**, as únicas: o seletor de tema deixou de ser `🌙 Escuro` /
`☀️ Claro` — único emoji da interface — e virou segmentado `Escuro` / `Claro`; e o bloco de total de
sessões ganhou o rótulo `Sessões` / `Sessions`, porque o número deixou de vir emendado à palavra.
Os asserts que observavam os textos antigos mudaram nos mesmos commits.

**O protótipo cedeu em três pontos**, por decisão explícita do usuário, e o HTML foi atualizado:
rodapé do card em botões de **ícone** (a `contentDescription` carrega a explicação do pisca, e botão
de texto não teria onde levá-la); card minimizado com **badges por cota** (o resumo `68% · 41%` apaga
o rótulo e a tooltip de cada cota); lista de sessões com **rótulo por célula** (as células somam
quase 1.000dp e cabeçalho fixo sobre linha que quebra desalinha).

**Um item do protótipo não foi implementado:** a contagem `4 fontes` na barra de estado do
dashboard. Ela afirma um estado agregado que o app não calcula, e com uma fonte em erro o verde
mentiria — escolher o tom seria inventar semântica que o protótipo não define. Falha de fonte já tem
o banner por alvo. Registrado no próprio HTML.


### Passada de conformidade — 2026-08-24 (issue #81)

Levantamento do usuário, de novo por captura: cinco comparações lado a lado — protótipo à esquerda,
app à direita — das seções **8 · Resumo por eixo**, **9 · Uso do time**, **9b · Tendência do time**,
**10 · Presença** e **10b · Presença — todas as contas**. A passada de 23/08 corrigiu as regras e a
anatomia do dashboard, do histórico e das Configurações; estas cinco telas ficaram com a anatomia
antiga.

O eixo da passada é um só: **a legenda pertence à coluna, não à célula.** Quatro listas repetiam o
rótulo dentro de cada célula — "Máquina", "Custo", "Tempo ativo", "do time" — e numa lista de time
isso dobra o texto da tela, com o ruído crescendo com o número de linhas.

| O que mudou | Por quê |
|---|---|
| `AppColumnHeaderRow`, `AppColumnHeaderLabel` e `AppCellValue` entram em `AppStructure` | `ColumnHeaderLabel` e `MetricValue` moravam em `CliSessionsScreen.kt`, arquivo de tela, consumidos por três telas — o mesmo defeito que fez `MetricCard` virar `AppMetricBlock` |
| Resumo por eixo: totais viram três blocos de métrica e as linhas viram tabela | Eram dois painéis com título em azul e em verde, e um card por balde com barra de largura total — quatro elementos para dizer o que uma linha de tabela diz com colunas alinhadas |
| Resumo por eixo: o paginador sobe para a faixa de controles | No rodapé ele é a primeira coisa a sair da tela numa janela baixa, que é justamente quando a lista é longa. Filtro, ordem e página escolhem parâmetros do mesmo conteúdo e ficam juntos |
| Lista de sessões e lista do time ganham faixa de legendas | A legenda pertence à coluna. Ver a reversão abaixo |
| Cabeçalho das duas janelas sai do `DepthSurface`, e as abas vêm antes das métricas | O corpo da janela já é a superfície; o retângulo com borda transformava barra de controles, métricas e abas num bloco só. A aba escolhe o que a janela mostra, e os totais são conteúdo dela |
| Tendência: faixa por integrante vira barras agrupadas com legenda e grade | Da faixa dava para ver que houve um pico, não em que dia nem de quem |
| Presença: sete colunas na ordem do protótipo, com o Estado primeiro | Estado e Trabalhando carregavam **dois dados por célula** — a palavra e um carimbo —, e célula com dois dados não tem uma legenda |
| Presença ganha o campo "Filtrar integrante" | O chip liga uma restrição; num time de vinte máquinas ele sozinho não acha ninguém. O texto mora no `Success` ao lado de `onlyOnline`, um dono só para "o que a lista mostra" |
| Sessões CLI e Uso do time ganham piso de janela; o da Presença sobe de 940 para 1030 | Faixa de legendas sobre linha que quebra promete um alinhamento que o conteúdo não cumpre, e quem arrasta a borda é o usuário |

**A concessão do rótulo por célula foi revertida**, e ela era a primeira dos três pontos em que "o
protótipo cedeu" na passada anterior. O motivo original era largura: as células da linha de sessão
somavam quase 1.000dp contra uma janela de 960. O que a desfez foi o **veredito de saturação sair do
fluxo de colunas** — ele media 210dp e desceu para uma segunda linha da própria linha, junto da razão
que o gerou, que é como a §6 do protótipo já desenhava a linha. Com ele fora as seis colunas somam
766dp com o vão, e o piso de janela garante que continuem cabendo. As outras duas concessões — botões
de ícone no rodapé do card e badges por cota no card minimizado — seguem valendo.

**Uma exceção consciente entra no lugar**: na tendência do time **a cor identifica o integrante**,
contra a regra de que acento é identidade de fonte e não de valor. Num gráfico agrupado a cor é o
único jeito de dizer de quem é a barra; é o que o protótipo desenha e foi decisão explícita do
usuário. A paleta reusa os acentos de fonte já medidos por `AppAccentsContrastTest` e **cicla** — do
sétimo integrante em diante duas séries repetem o tom, e quem as separa é a legenda.

**Larguras de coluna são orçamento, não gosto.** Cada lista carrega a conta no comentário das
constantes: soma das colunas + vão × (n − 1) + marcador + barra de rolagem + coluna de ação ≤ largura
da janela − cromo. Sessões CLI e Uso do time cabem em 960dp; a Presença precisa de 1030 porque é a
única que imprime dois carimbos `12/08 10:58 BRT` por linha, e truncá-los apagaria justamente o fuso
que a frase existe para dizer.

**Três textos de interface mudaram**, e nenhum é cosmético: `lastSeen` perdeu a palavra "último"
(a frase mora na terceira linha da coluna de identidade e as duas palavras a faziam truncar no
carimbo); `TeamPresenceLabels.columnWorking` virou `workingNow`, que é o valor da coluna Estado e não
o nome de uma coluna; e `CliSessionsLabels.columnShare` nasceu como "Participação", porque
`TeamUsageLabels.columnShare` é "do time" — rótulo de uma célula que já traz o número junto, e como
legenda de coluna ele diria a preposição sem o substantivo. `trendTitle` saiu: era o título de um
painel de explicação que deixou de existir.

**O protótipo mudou junto** nas seis seções: §6 (a nota da concessão reescrita e a lista com faixa de
legendas), §8 (paginador no topo, "Por página", ordem e rótulo das abas alinhados ao código, grade
7 × 24 no lugar da fileira de 24 horas), §9 (colunas do integrante, faixa da conta reservando as
mesmas colunas, bloco de sessões com o cabeçalho da §6), §9b (piso e teto de largura de barra, e a
nota da cor por integrante), §10 (colunas de Último turno e Status, chip e campo de filtro juntos) e
§10b (faixa de legendas e os agregados da conta numa célula que atravessa as colunas vazias).


### Passada de conformidade — 2026-08-24 (issue #83)

Uma primitiva, um defeito: a **barra de cota desaparecia** nas escalas de 105% e 110% do slider de
interface. O levantamento veio por captura do dashboard, e a medição de pixel fechou o diagnóstico
antes de qualquer alteração — a 80% o trilho tem 3px com 1px de verde nos primeiros 37% da largura;
a 110% as quatro linhas do trilho são `0xFF3D3838`, que é `outlineVariant`. Não é a barra desenhada
errada: é a barra desenhada e apagada.

A causa está em `Modifier.border`, não no app. `Border.kt` do Compose 1.7.1 faz
`strokeWidthPx = ceil(width.toPx())` e desenha em `onDrawWithContent { drawContent(); … }`, ou seja,
**por cima dos filhos**. Com `TRACK_HEIGHT` de 4dp e borda de 1dp:

| Escala | Trilho | Traço | Interior |
|---|---|---|---|
| 80–85% | 3px | 1px | 1px |
| 90–100% | 4px | 1px | 2px |
| **105–110%** | **4px** | **2px** | **0px — barra cega** |
| 115–135% | 5px | 2px | 1px |
| 140–150% | 6px | 2px | 2px |

O padrão de fábrica é 115%, então a barra vinha de fábrica como um fio de 1px e sumia em duas
posições do slider.

`AppProgressTrack` passou a montar a borda como **fundo mais padding** em vez de `Modifier.border`.
O padding usa `roundToPx`, que acompanha a altura do trilho, e é exatamente o
`box-sizing: border-box` que a §2 do protótipo já especificava — ali a borda reserva layout e o
preenchimento nunca fica por baixo do anel. **A renderização a 100% é a mesma de antes** (2px de cor
entre dois anéis de 1px), então nenhuma captura do README precisa ser regerada: os geradores rodam
na escala neutra, e foi por isso que o defeito nunca apareceu numa delas.

O protótipo **não mudou**. Ele estava certo; quem divergiu foi o Compose.

O teste de regressão é em **bitmap**, não em layout: o `Box` de preenchimento sempre foi medido com
a altura cheia, e `boundsInRoot` devolvia 4px nas duas escalas cegas — um teste de layout passaria
com a barra apagada. `AppStatesTest` percorre a grade inteira do slider (80…150 de 5 em 5),
renderiza a barra com fração 0 e com fração 1, e compara os dois bitmaps. Contra o código anterior a
diferença a 105% é de **12 pixels** — só o antialiasing das pontas arredondadas — contra um piso de
meia linha da largura do trilho. A grade inteira é percorrida de propósito: o defeito morava em duas
posições específicas, e uma escala amostrada passaria.

Ficaram de fora, verificados: **toda** borda de 1dp do app engrossa para 2px na mesma faixa, mas em
botão, campo e superfície de dados isso é uma borda mais grossa e não conteúdo apagado — o interior
deles tem dezenas de dp; e o ponto vazado de `AppStatusIndicator`, de 6dp, continua vazado com 3px
de interior. Nenhum outro elemento com `.size`/`.height` de um dígito em dp usa `border`.

---

## Protocolo de retomada de sessão

Ao iniciar **qualquer** sessão desta iniciativa:

1. Ler `AGENTS.md` e `CLAUDE.md` integralmente.
2. Ler este arquivo, começando pelo Ponto de situação.
3. **Abrir `docs/planos/prototipo-visual-opencode.html`** — a especificação visual aprovada. Ele se
   consulta antes de escrever UI e outra vez ao revisar o que saiu; nenhuma tela é desenhada "de
   memória" do plano, que descreve execução e não aparência.
4. `git worktree list`, `git status` e `git log --oneline -5` no worktree `usage-monitor-visual`. O
   checkout principal não é tocado.
5. Confirmar que a suíte está verde antes de editar:
   `gradlew.bat desktopTest --tests "com.usagemonitor.ui.*"`.
6. Executar **a atividade indicada em ▶ Próxima atividade**, e só ela.

Ao encerrar **cada fase**:

1. `gradlew.bat allTests` e `gradlew.bat generateScreenshots`; inspecionar as capturas.
2. Atualizar aqui: tabela de progresso, registro de execução, decisões, bloqueios e o campo
   **▶ Próxima atividade**.
3. Commitar esta atualização **junto com o último commit da fase** — nunca depois, ou o estado no
   repositório mente sobre o que foi feito.
4. `git merge --no-ff` da branch da fase na integração.

Fase interrompida no meio: marcar 🟡 com o commit exato onde parou e o que falta, antes de encerrar
a sessão.

---

## Contexto

O documento do Codex define uma refatoração visual integral com a linguagem do OpenCode: mono,
superfícies neutras, bordas de 1dp, raios pequenos, densidade alta. Está **correto no diagnóstico e
na direção**, mas foi escrito contra um estado do repositório que já mudou e não enfrenta os pontos
onde esta base de código quebra sob refatoração visual.

Decisões herdadas e mantidas: fidelidade quase-réplica adaptada, dark + light com o escuro como
referência, densidade compacta e adaptativa, janelas separadas, design system próprio sobre
Material3/Foundation, nenhum asset do OpenCode.

Decisões desta sessão, sobrepondo o documento do Codex: gate por protótipo HTML; marca completa
incluindo `.icns`; IBM Plex Mono estrutural + IBM Plex Sans em texto corrido.

---

## O que o Codex acertou (mantido sem alteração)

- **O diagnóstico.** Profundidade por volume e cor, raios de 16–28dp em tudo, containers aninhados
  de mesmo peso, densidade inconsistente entre telas. Confirmado no código: `AppShapes` é
  10/16/20/28dp, `DepthSurface` pinta um gradiente de acento em toda superfície e `AppGlow` nomeia
  três patamares de brilho.
- **Rejeitar Jewel, Compose Unstyled e MaterialKolor.** Jewel exigiria subir Compose, Kotlin e JDK; e
  os gráficos (`UsageHistoryLineChart` com 1209 linhas, `TurnSeriesChart`, `TeamTrendChart`,
  `ActivityHeatmapGrid`) carregam regra de domínio, binning e testes — trocá-los é risco funcional
  sem ganho proporcional.
- **A lista de contratos intocáveis**: domain, DTOs, mappers, repositories, data sources, endpoints,
  SQLite, preferências, intervalos de polling, ViewModels.
- **Manter o saneamento WinAnsi do PDF** nesta iniciativa, em vez de transformá-la numa migração de
  contrato Unicode.
- **PDF como layout canônico escuro**, independente do tema ativo da interface.
- **Nenhuma animação infinita nova.**

## O que está desatualizado no documento do Codex

| Afirmação do Codex | Realidade em 2026-08-18 |
|---|---|
| `origin/main` em `fada75d` | `main` em `2947b4d`, dois commits à frente |
| Working tree com alterações locais protegidas (bump 34→35, `img/presence*.png` não versionados, `output/`) | `git status` **limpo**; a versão 35.0.0 já está commitada em `build.gradle.kts` e no `.nsi` |
| Fase 2 inteira, sobre não apagar o estado sujo do checkout principal | Obsoleta; a criação do worktree foi trivial |

## O que faltou no documento do Codex

1. **Nenhuma estratégia para os ~390 asserts por texto.** As suítes de UI localizam nós
   majoritariamente por texto, não por tag: `ComponentTest.kt` (2662 linhas, 210 `onNodeWithText`
   contra 14 `onNodeWithTag`), `CliSessionsScreenTest` (89 × 15), `TeamUsageScreenTest` (63 × 46),
   `FooterBarTest` (12 × 6). Existem **15 testTags distintos** em todo o código de produção. O
   documento diz "ações continuam localizáveis por semântica ou testTag" sem dizer como. Sem
   tratamento, cada fase quebra dezenas de testes e a causa fica ambígua entre "mudei o visual" e
   "quebrei o comportamento".
2. **`AppAccentsContrastTest` trava a paleta.** Ele fixa as duas superfícies de referência
   (`#242424` e `#F8F9FA`) como constantes e mede 13 acentos contra elas em AA 4,5:1, além de exigir
   preservação de matiz entre temas e separação de 20° entre as seis cores de integração. Não é
   citado pelo Codex. Nota favorável: as superfícies novas (`#1B1818` / `#FFFCFC`) são
   respectivamente mais escura e mais clara que as atuais, então os acentos **ganham** contraste — a
   paleta de acentos provavelmente não muda, só as constantes do teste.
3. **Não existe caminho definido para carregar a fonte.** `compose.components.resources` está
   declarado no `build.gradle.kts` mas **não há** `src/commonMain/composeResources/`; o único
   recurso lido hoje sai de `getResourceAsStream` em `Main.kt:220`. `AppTheme` vive em `commonMain` e
   a API de fonte por arquivo do Compose Desktop é JVM-only. É decisão de arquitetura, não detalhe
   de estilo.
4. **Embutir a fonte no PDFBox recalcula o layout inteiro do relatório.**
   `PdfUsageReportRenderer` usa `PDType1Font(Standard14Fonts.HELVETICA)`, e toda a paginação e o
   alinhamento de colunas dependem do `getStringWidth` dessa fonte. É o item de **maior risco
   funcional** da iniciativa; o Codex o lista como item de estilo.
5. **`main()` está no limite do backend JVM.** O `gradle.properties` documenta o `OutOfMemoryError`
   dentro do ASM: `Main.kt` tem 1950 linhas e `main()` é um composable único com seis janelas. A
   Fase 5 do Codex toca esse arquivo. Regra: **nenhuma composable nova dentro de `main()`**.
6. **Listas Lazy contra asserts por texto.** `CliSessionsScreen`, `CliUsageBreakdownPane`,
   `TeamUsageScreen`, `TeamPresenceScreen` e `TeamKeysAdminScreen` já usam `LazyColumn`; Dashboard,
   Histórico e Configurações usam `Column + verticalScroll`. Converter qualquer uma dessas três em
   Lazy por densidade quebra todo assert de texto fora do viewport.
7. **`ScreenshotGenerator` é calibrado contra os tokens de motion.** `WARMUP_FRAMES = 20`,
   `WARMUP_SLEEP_MILLIS = 50` e `FRAME_STEP_NANOS = 100ms` foram ajustados contra o
   `delay(index * 90)` da grade de cards e o fade de entrada. Mexer em stagger ou duração sem
   revalidar o gerador produz capturas meio-desenhadas — já aconteceu na primeira versão dele.
8. **`ShimmerBox` é a única animação infinita** e vive no estado de carregamento do dashboard. Não
   replicar: a regra existe porque animação sem fim trava o `waitForIdle` dos testes de componente.
9. **O ícone toca a bandeja.** `TrayRiskIconPainter` sobrepõe um ponto de risco ao `app_icon.png` e
   sobrescreve `equals` para o `Tray` não reconstruir a imagem AWT a cada recomposição. Trocar o PNG
   exige revalidar os quatro estados.
10. **Colisão de nome.** `AppAccents.opencode` é a cor da **integração** OpenCode monitorada pelo
    app. A inspiração visual e uma das seis fontes de dados têm o mesmo nome; o acento da integração
    não pode virar a cor da marca.
11. **Áreas ausentes da lista de 16 do protótipo:** banner de atualização disponível
    (`AppUpdateBanner`, alterado em `2947b4d`), `RefreshWarningDialog`, toasts de configurações
    (`SettingsToast`), tooltips de gráfico (`UsageTooltip`), glossário de sessões
    (`CliSessionsGlossary`), diálogo de exportação, indicador de pulso (`SessionPulseIndicator`) e a
    linha de orçamento mensal.
12. **Ordem da marca.** O Codex a põe na Fase 4, antes das telas. Ela só é consumida pelo chrome das
    janelas, pela bandeja, pelo PDF e pelas capturas — todos no fim. Movida para depois das telas.

---

## Decisão técnica: como a fonte chega ao Compose

`AppTheme` vive em `commonMain`; a leitura de arquivo é JVM.

- Arquivos em `src/desktopMain/resources/fonts/`: `IBMPlexMono-{Regular,Medium,SemiBold}.ttf`,
  `IBMPlexSans-{Regular,Medium,SemiBold}.ttf` e `OFL.txt`.
- `commonMain/presentation/ui/theme/AppFonts.kt`: `expect val appFontFamilies: AppFontFamilies`
  (data class com `mono` e `sans`).
- `desktopMain/.../AppFonts.desktop.kt`: o `actual`, carregando do classpath.
- `AppTheme` monta a `Typography` a partir daí. Um ponto de verdade só: app, testes de componente e
  geradores de captura recebem a fonte real sem configuração extra.

**Verificação obrigatória antes de fechar o commit C3:** confirmar qual sobrecarga existe em Compose
Multiplatform 1.7.1 — `androidx.compose.ui.text.platform.Font(resource: String, weight, style)` ou
`Font(file: File, ...)`. Se nenhuma servir, o fallback é `java.awt.Font.createFont` mais o
`Typeface` do Skia. Nenhum código de tela é escrito antes de isso fechar, porque a métrica da fonte
define toda a densidade.

**Não adotar `composeResources` nesta iniciativa:** o carregamento é assíncrono, e o
`ImageComposeScene` do `ScreenshotGenerator` renderiza offscreen com relógio manual — captura com
fonte de fallback é falha silenciosa.

---

## Estratégia de Git

Nada é commitado em `main`. O checkout principal não é tocado.

```bash
git worktree add C:/Users/edils/workspace/usage-monitor-visual -b refactor/visual-opencode origin/main
git branch --unset-upstream   # senão um push sem argumento vai para a main do remoto
```

- `refactor/visual-opencode` é a branch **de integração** desta versão.
- Uma branch por fase, criada a partir da integração e devolvida a ela com `git merge --no-ff`:
  `visual/c-foundation`, `visual/d-dashboard`, `visual/e-history`, `visual/f-cli-sessions`,
  `visual/g-team`, `visual/h-settings`, `visual/i-window-chrome`, `visual/j-brand`,
  `visual/k-report`, `visual/l-docs`.
- Cada merge de fase é ponto de checagem: `allTests` verde e capturas inspecionadas antes de fundir.
- Merge para `main` **só sob pedido explícito**, por PR — o CI já roda `allTests` em
  `windows-latest` em todo PR.
- Commits pela skill `usage-monitor-commit-push`, com stage explícito por arquivo. Nunca
  `git add -A`.

---

## Fases e commits

### Fase A — Protótipo ✅ aprovado em 2026-08-18

Entregue em [`prototipo-visual-opencode.html`](prototipo-visual-opencode.html). HTML navegável,
alternância dark/light, dados de `ScreenshotFixtures` — sintéticos, sem nenhuma conta, máquina,
caminho ou chave real. Google Fonts serve IBM Plex Mono e Sans reais; sem rede a página cai no
monoespaçado do sistema e a densidade exibida deixa de ser a real.

Áreas: as 16 do Codex mais as 8 do item 11. Estados: sucesso, carregamento, sucesso parcial, erro
total, lista vazia, atualização em andamento, card minimizado, quota saudável/atenção/crítica,
sessão saudável/atenção/saturada, time ativo/inativo, ação destrutiva com confirmação, janela larga
e estreita.

Abre por uma **prancha de tokens** — paleta (dark `#131010` / `#1B1818` / `#211E1E` / `#3D3838` /
`#F2EDED` / `#B8B2B2`; light `#F6F3F3` / `#FFFCFC` / `#EFEAEA` / `#D7D0D0` / `#171414` / `#686060`),
escala tipográfica 10/12/14/16/20/28, raios 4/6/8/10, elevações 0/2/8, grade 4/8/12/16/24/32, motion
120/180/240 — para a aprovação ser sobre o sistema, não sobre 24 telas soltas.

**Gate:** aprovação explícita de paleta, tipografia, marca, densidade, dashboard, telas tabulares,
configurações, PDF e tema claro. Rejeição em qualquer ponto significa atualizar o protótipo, não
começar o código.

### Fase B — Isolamento

Worktree e branch criados; `allTests` rodado antes de qualquer edição para registrar a linha de
base. Commit único: este documento.

### Fase C — Fundação (`visual/c-foundation`)

| # | Commit | Alvo |
|---|---|---|
| C1 | `chore(assets): add IBM Plex Mono and Sans with the OFL license` | `src/desktopMain/resources/fonts/` |
| C2 | `feat(theme): replace the color scheme with a neutral palette` | `AppTheme.kt` (dark + light), `AppAccentsContrastTest.kt` (constantes de superfície) |
| C3 | `feat(theme): load IBM Plex and rebuild the type scale` | `AppFonts.kt`, `AppFonts.desktop.kt`, `AppTheme.kt` |
| C4 | `feat(theme): tighten radius, elevation, spacing and motion tokens` | `AppTheme.kt`: `AppShapes` → 4/6/8/10, `AppElevation`, `AppMotion` → 120/180/240, `AppSpacing` novo |
| C5 | `feat(ui): add structural primitives` | `AppWindowScaffold`, `AppToolbar`, `AppSectionHeader`, `AppDataSurface`, `AppDataRow`, `AppTabs` |
| C6 | `feat(ui): add control primitives` | `AppButton`, `AppIconButton`, `AppTextField`, `AppSwitch`, `AppTooltip` |
| C7 | `feat(ui): add state primitives` | `AppBanner`, `AppEmptyState`, `AppLoadingState`, `AppErrorState`, `AppStatusIndicator`, `AppProgressTrack` |
| C8 | `refactor(ui): replace the DepthSurface glow with bordered surfaces` | `DepthSurface.kt`, `AppGlow` |

Todas as primitivas stateless: dados por parâmetro, eventos por lambda. C4 obriga a revalidar o
`ScreenshotGenerator`. C8 é o único commit da fase que altera pixel de tela existente — os
anteriores só acrescentam.

### Fase D — Dashboard (`visual/d-dashboard`)

| # | Commit | Alvo |
|---|---|---|
| D1 | `test(ui): anchor dashboard assertions on test tags` | `ComponentTest.kt`, `FooterBarTest.kt`, mais tags em `ApiUsageCard.kt` e `FooterBar.kt` |
| D2 | `refactor(dashboard): turn quota blocks into compact rows` | `ApiUsageCard.kt`, `ApiUsageCardDensity.kt` |
| D3 | `refactor(dashboard): restyle the card chrome and actions` | `ApiUsageCard.kt`, `SessionPulseIndicator.kt`, `RiskSemaphoreDot.kt`, `ResponsiveDashboardCardGrid.kt` |
| D4 | `refactor(dashboard): convert the footer into a status bar` | `FooterBar.kt` |
| D5 | `refactor(dashboard): apply state primitives to loading, empty and error` | `DashboardScreen.kt`, `DashboardScreenWarnings.kt`, `PersistentApiWarningBanner.kt`, `RefreshWarningDialog.kt` |

O padrão D1 se repete em toda fase de tela: **primeiro** um commit que só move asserts de texto para
`testTag`, com comportamento inalterado e suíte verde antes e depois; **depois** o commit visual.
É o que torna cada refatoração visual provadamente neutra.

### Fase E — Histórico (`visual/e-history`)

E1 tags · E2 `refactor(history): merge source, account and range into one toolbar`
(`HistoryScreen.kt`, `ApiSelector.kt`) · E3 `refactor(history): restyle the chart panel and tooltip`
(`UsageHistoryLineChart.kt`, `UsageArcChart.kt`, `UsageTooltip.kt`) ·
E4 `refactor(history): lay out metrics as a table` (`HistoryScreen.kt`, `HistoryScreenFormatting.kt`).

Preservar: seleção de fonte e de conta, `24h`/`7 dias`/`30 dias`/`Total`, reinícios de janela, média
por hora, forecast, comparação com período anterior, regras de saldo e de créditos.

### Fase F — Sessões CLI (`visual/f-cli-sessions`)

F1 tags · F2 `refactor(cli-sessions): render the session list as aligned rows`
(`CliSessionsScreen.kt`) · F3 `refactor(cli-sessions): restyle the session detail`
(`TurnSeriesChart.kt`) · F4 `refactor(cli-sessions): restyle the breakdown pane and activity grid`
(`CliUsageBreakdownPane.kt`, `ActivityHeatmapGrid.kt`) ·
F5 `refactor(cli-sessions): restyle export and glossary surfaces` (`CliSessionsGlossary.kt`,
`CopySessionCommandButton.kt`).

`LazyColumn` permanece `LazyColumn`. Paginação, filtro, ordem e abas do resumo continuam no
`remember` da pane, não no ViewModel.

### Fase G — Time (`visual/g-team`)

G1 tags · G2 `refactor(team): render member usage as aligned rows` (`TeamUsageScreen.kt`) ·
G3 `refactor(team): restyle the trend chart` (`TeamTrendChart.kt`, escala única entre integrantes
preservada) · G4 `refactor(team): restyle the presence list` (`TeamPresenceScreen.kt`, o ponto de
estado continua sem piscar) · G5 `refactor(team): restyle the keys admin screen`
(`TeamKeysAdminScreen.kt`).

Nenhuma mudança de contrato do servidor. Confirmações destrutivas preservadas; a própria máquina
continua protegida contra remoção.

### Fase H — Configurações (`visual/h-settings`)

H1 tags · H2 `refactor(settings): replace the tab chips with side navigation`
(`SettingsDialogContent.kt`: reusa o enum `SettingsTab` sem valor novo, mantém um `ScrollState` por
aba e só a aba escolhida na composição) · H3 `refactor(settings): restyle general, alerts and APIs`
(`AlertSettingsSection.kt`, `DebouncedTextField.kt`, `SettingsToast.kt`) ·
H4 `refactor(settings): restyle accounts and team` (`TeamIntegrationSection.kt`).

### Fase I — Chrome das janelas (`visual/i-window-chrome`)

I1 `refactor(window): standardize the title bar and window frame` (`DesktopWindowFrame.kt`, raio da
janela alinhado à escala nova; `applyWindowShape` continua reagindo a `componentResized`) ·
I2 `chore(window): align window sizes with the new density` (`Main.kt`, **só constantes**:
Configurações para ~820×720dp).

Regra dura: nenhuma composable nova em `main()`. Se o layout exigir estado novo ali, extrair antes,
em commit próprio.

### Fase J — Marca (`visual/j-brand`)

J1 `feat(brand): add the deterministic icon renderer` — `tools/brand/monogram.svg` e
`tools/brand/render_icons.py`; construção vetorial determinística do monograma `UM` e wordmark em
IBM Plex Mono.
J2 `feat(brand): apply the new mark to app, tray and packaging` —
`src/desktopMain/resources/icons/app_icon.{png,ico,icns}` e `src/desktopMain/resources/icon.{png,ico}`.
O `.ico` sai do `build-ico.ps1` que já existe no repositório (16/32/48/256); o `.icns` é montado pelo
script, com chunks `ic07`–`ic14` de payload PNG.

**Risco aceito:** o `.icns` não é verificável nesta máquina Windows. A validação real acontece no
job `build-macos` de `.github/workflows/release-linux.yml`. Antes de fundir, verificar legibilidade
em 16/20/24/32/48/64/128/256 px e os quatro estados do ícone de bandeja.

### Fase K — Relatório PDF (`visual/k-report`)

K1 `feat(report): embed IBM Plex in the PDF renderer` — **commit isolado de propósito**: troca
`PDType1Font(HELVETICA)` por fonte embutida, o que recalcula `getStringWidth` de todas as colunas e
toda a paginação. Validar relatórios curto, médio e longo antes de qualquer mudança de cor. O
saneamento WinAnsi de `UsageReportDocument.sanitized` permanece como está.
K2 `refactor(report): apply the new palette and table style` — `PdfUsageReportRenderer.kt`,
`UsageReportBuilders.kt`. O documento continua escuro independente do tema da UI; seções, valores,
paginação e rodapé preservados; o relatório do time continua sem grade de atividade e sem
ferramentas.

### Fase L — Capturas e documentação (`visual/l-docs`)

L1 `chore(screenshots): regenerate README captures` — `gradlew.bat generateScreenshots`, mais
capturas de resumo por eixo, tendência e administração se o visual novo depender delas.
L2 `chore(screenshots): regenerate the tour gif` — `gradlew.bat generateTourGif`.
L3 `docs: record the visual system and its constraints` — seção nova em `CLAUDE.md` e `AGENTS.md`
(tokens, primitivas, regra de não converter para Lazy, regra do `main()`), `README.md` só onde o
texto ficou incorreto, e o documento do Codex marcado como superseded.

### Fase M — Fechamento

`allTests` → `build` → `createDistributable` → QA visual manual. Todas as fases já fundidas em
`refactor/visual-opencode`. A `main` permanece intocada até pedido explícito de PR.

---

## Regras invioláveis desta iniciativa

1. **Nenhum texto de interface muda.** Strings visíveis são o que ~390 asserts observam. Alterar um
   rótulo é decisão separada, fora deste escopo.
2. **Nenhum `Column + verticalScroll` vira `LazyColumn`.** Item não composto é assert falhando por
   motivo que não é visual.
3. **Rótulo que vira ícone ganha `contentDescription` com o mesmo texto.**
4. **Nenhuma animação infinita nova.** Trava o `waitForIdle`.
5. **Nenhuma composable nova em `main()`.** OOM no ASM, documentado no `gradle.properties`.
6. **Nenhum valor novo em enum existente** — `UsageUnit`, `CliSessionRange`, `BreakdownAxis`,
   `TeamUsageView`, `SettingsTab`, `Platform`: há `when` exaustivos espalhados que quebram a
   compilação.
7. **Nenhuma mudança em domain, data, DTOs, repositories, endpoints, SQLite, preferências,
   intervalos de polling ou ViewModels.** Assinatura só muda em componente interno de UI.
8. **Nenhuma ação, campo, filtro, gráfico, exportação ou mensagem é removida.**
9. **Nenhum asset do OpenCode.** Só os TTFs oficiais da IBM Plex sob OFL.
10. **`git add` explícito por arquivo.** `output/`, `.kotlin/` e `build/` nunca entram.

## Verificação

Por commit:

```bat
gradlew.bat desktopTest --tests "com.usagemonitor.ui.*"
```

Por fase, antes do merge na integração:

```bat
gradlew.bat allTests
gradlew.bat generateScreenshots
```

mais inspeção das capturas em `img/` nos dois temas.

Na Fase K, adicionalmente:

```bat
gradlew.bat desktopTest --tests "com.usagemonitor.data.PdfUsageReportRendererTest"
```

mais renderização e inspeção de um relatório curto, um médio e um longo.

No fechamento:

```bat
gradlew.bat allTests
gradlew.bat build
gradlew.bat generateScreenshots
gradlew.bat generateTourGif
gradlew.bat createDistributable
```

Sem tarefas Gradle pesadas em paralelo no Windows. Se o empacotamento falhar ao substituir o
executável, checar o atributo read-only do artefato antes de qualquer outra hipótese.

**QA manual, obrigatório antes do merge final:** dark e light; PT e EN; dashboard em uma e duas
colunas; tamanho mínimo de cada uma das seis janelas; escala do Windows em 100% e 150%; hover,
pressed, focus e disabled; loading, empty, success, parcial e erro; conteúdos longos; múltiplas
contas Anthropic; as seis integrações; contraste AA; alinhamento de colunas e métricas; tooltips dos
gráficos; ícone em oito tamanhos; bandeja com e sem risco; PNG, ICO e ICNS; PDF curto, médio e
longo; capturas e GIF gerados.

## Dimensionamento

~22.400 linhas na camada de apresentação, 20 arquivos com formas e raios, 8 suítes de UI.
**12 fases, ~40 commits atômicos, 10 branches de fase.** A Fase A é o único ponto onde nada é
reversível por código — daí ser gate.
