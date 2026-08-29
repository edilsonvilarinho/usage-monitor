# Relatório de bug: trilha de eventos, captura em crash e export para issue

Plano de execução da [issue #123](https://github.com/edilsonvilarinho/usage-monitor/issues/123).
Branch `feat/bug-report-breadcrumbs`, worktree `../usage-monitor-123`.

## Problema

Um erro no app não deixa rastro nenhum fora dos recorders opt-in de crédito da Anthropic e do Codex —
e os dois guardam **corpo de resposta HTTP**, não a sequência de passos do usuário. Quando algo
quebra, a única fonte é o que o usuário lembra de descrever.

## Objetivo

1. Trilha leve de eventos (*breadcrumbs*) sempre gravada em disco, para reconstruir os passos até o erro.
2. Um ponto na aba Geral das Configurações para descrever um problema e gerar um pacote de diagnóstico.
3. Captura best-effort da janela do app em caso de falha não tratada.
4. Abertura de uma issue pré-preenchida no GitHub, para o usuário revisar e publicar.

**Nada sai da máquina do usuário automaticamente.**

## Decisões registradas

### Entrega por arquivo + navegador, nunca por envio

A ideia original — mandar o relatório por e-mail para um endereço fixo — foi descartada na issue. O
app é distribuído publicamente e não existe infraestrutura de envio no repositório. As três formas de
viabilizá-la (endpoint próprio com SMTP, `mailto:` manual, ou chave de provedor transacional embutida
no binário) ou exigem hospedar um serviço novo, ou vazam uma credencial num app público. A mesma
régua que já rejeita hardcode de credencial nas integrações existentes rejeita as três.

O app grava o pacote em disco por um diálogo de salvar — mesmo padrão do `DesktopUsageExportWriter` —
e abre o navegador na página de nova issue com título e corpo pré-preenchidos. Quem publica é o
usuário.

### Respostas às três perguntas em aberto da issue

1. **A aba Geral não tem botão `PRIMARY` hoje.** Verificado em
   `SettingsDialogContent.kt` (`GeneralSettingsTab`, ~386–494): ela é só dois `AppDataSurfaceFlush`
   com `AppSectionHeader` ("Aparência" e "Sistema") e linhas de opção, sem nenhum `AppButton`. Logo
   "Reportar um bug" entra como **`PRIMARY`**, no `trailing` do cabeçalho de uma seção nova
   "Diagnóstico" — o mesmo lugar do "Adicionar" da aba Contas, que é o precedente do repositório para
   ação sobre a seção inteira.
2. **Cortes.** `breadcrumbs.jsonl` usa **200/100 linhas**, os mesmos `MAX_LINES`/`KEPT_LINES` do
   `StartupDiagnostics` — dois cortes para o mesmo tipo de arquivo seriam dois donos da mesma
   decisão. No corpo pré-preenchido da issue entram os **últimos 30 breadcrumbs**, e o corpo é
   truncado em **6.000 caracteres** antes do percent-encoding, com folga para o título.
3. **Repositório alvo.** Constante única `BUG_REPORT_REPOSITORY_URL` em `desktopMain`, sem override
   por variável de ambiente: build de fork continua abrindo o upstream, que é onde as issues são
   triadas.

### Desenho

- **`LocalBreadcrumbRecorder` é modelado em `StartupDiagnostics.kt`**: mesmo lock, mesmo trim antes do
  append, mesmo `restrictToOwnerReadWrite`, mesmo `runCatching` que nunca derruba quem chamou. Não
  abrir um segundo desenho de recorder para o mesmo tipo de arquivo.
- **`toJson()` do envelope é montado à mão, não por `kotlinx.serialization`.** O envelope mora no
  domain, e o domain não importa biblioteca externa (`CLAUDE.md`) — foi por isso que o
  `UsageExporter` foi parar em `data`. Aqui o pacote é uma árvore de cinco campos e um vetor de
  objetos de três campos: um escapador de string coberto por teste custa menos que mover a entidade
  para fora do domain só para serializá-la.
- **Writer e capturer injetáveis**, precedente exato do `DesktopUsageExportWriter` e do
  `rememberClipboardWriter`: teste de componente não abre diálogo de arquivo, não escreve no disco de
  quem roda a suíte, e `java.awt.Robot` não é utilizável em CI headless.
- **Nenhuma composable nova em `main()`** — o método já estourou o backend JVM uma vez. O handler de
  crash é registrado **antes** do `application { }`, e a leitura do marcador é função de topo chamada
  de dentro de um `remember`.
  - Para isso o ponto de entrada virou **corpo de bloco**: `main()` cria a trilha e chama
    `runUsageMonitor(args, breadcrumbs)`, que é a mesma função gigante de antes com outro nome. Não
    reparte nada, não cria composable, e é a única forma de existir código **antes** do
    `application { }` com a forma de expressão que o arquivo usava.
  - Os passos de navegação entram como **uma linha dentro de lambdas que já existem** (`onOpenSettings`,
    `onOpenCliSessions`, …). Nenhum `LaunchedEffect` novo: o de #120 já registrou que o jeito de não
    crescer `main()` é usar os que já estão lá.
- **Sucesso repetido não vira passo — a trilha tem 200 linhas de orçamento.** O laço do dashboard
  roda a cada 10 min e os das janelas ao vivo a cada 5s; anotar "coleta ok" em todos eles encheria o
  arquivo em minutos e expulsaria dele justamente o passo que explica a falha. Por isso o B10 grava
  em **dois** pontos, e não em quatro: o pedido **do usuário** (`refresh()` e suas sobrecargas), que
  é a ação que ele vai descrever, e a **falha**, em `handleTargetFailure`, que é o funil único por
  onde toda coleta que dá errado passa — poll silencioso, atualização pedida ou recarga de banner. É
  um desvio consciente da leitura literal de "início e resultado" da issue, e a razão é medível:
  sete fontes × dois passos × seis coletas por hora esvaziariam a trilha a cada duas horas e meia.
- **Falha interna vira `nome da classe da exceção`, nunca `error.message`** (`breadcrumbReasonOf`).
  Mensagem de falha de I/O ou de SQLite carrega o caminho absoluto do arquivo, e no Windows caminho
  absoluto começa com `C:\Users\<nome da pessoa>`. A classe responde "que tipo de falha foi" sem
  responder "de quem é a máquina".
- **Quatro `catch` silenciosos entraram no B11**, todos de caminho de baixa frequência: restauração
  do cache do dashboard (uma vez por arranque), orçamento mensal e tendência do time (uma leitura por
  abertura de janela) e o semáforo de sessões (laço de 30s, com deduplicação por motivo).
  - O semáforo precisa da deduplicação: sem ela, uma leitura quebrada escreveria 120 passos por hora.
    Um `var` simples basta e não há corrida — quem chama `refreshCliPulses` é o laço único de
    `start()`, sempre na mesma coroutine.
  - **`refreshRiskSummaries` ficou de fora, e isso é decisão registrada, não esquecimento.** Ele roda
    em **paralelo por alvo** a cada coleta bem-sucedida, então a deduplicação precisaria de um mapa
    compartilhado com trava — e `commonMain` não tem `synchronized`. Sem trava seria a mesma corrida
    que o próprio `historyUseCase` já documenta; com flood, sete fontes falhando esvaziariam a trilha
    em duas horas. Fica como candidato a issue própria.
- **A mensagem gravada é a saneada, nunca a crua.** `sanitizeUiErrorMessage` já é o filtro que decide
  o que pode aparecer na tela do usuário, e o relatório é mais público que a tela dele.
- **Passo de navegação não carrega identidade.** O apelido do perfil é digitado pelo usuário e
  costuma ser o e-mail da conta; a `accountKey` é identificador de conta. Nenhum dos dois entra na
  trilha — o nome da tela responde à pergunta "onde ele estava" sem responder "quem ele é".
- **O que o `CrashHandler` não cobre está escrito no próprio arquivo.** `setDefaultUncaughtExceptionHandler`
  pega o que derruba uma thread comum. **Não** pega exceção lançada dentro do laço de eventos da AWT —
  o `EventDispatchThread` a captura e a imprime por conta própria — nem falha dentro de coroutine com
  `SupervisorJob`, que vai para o `CoroutineExceptionHandler` do escopo. Preferir o registro escrito a
  descobrir isso depois; ampliar a cobertura depende do `sun.awt.exception.handler`, que é API interna
  e depreciada, e de um handler por escopo de coroutine — outro escopo, outra issue.
- **O handler repassa a exceção para o anterior, e por último.** Sem o repasse, uma queda que hoje
  aparece no console passaria a não aparecer em lugar nenhum: o app teria trocado um diagnóstico por
  outro em vez de somar os dois. Por último porque é o handler anterior quem pode encerrar o processo.
- **A mensagem da exceção entra no marcador de crash**, ao contrário do que `breadcrumbReasonOf` faz
  com as falhas engolidas. São dois casos diferentes: aquelas são frequentes, de baixo valor e nunca
  revisadas; esta é o evento único que motiva o relatório inteiro, e o usuário lê o pacote antes de
  publicá-lo.
- **`appMainWindow` é `@Volatile` de topo, e não `mainWindowRef`.** Aquele é estado de composição; o
  handler de crash roda fora dela, possivelmente com a composição já morta. Quem escreve é a thread da
  UI e quem lê é a thread que caiu.
- **A leitura do marcador não apaga o marcador.** Apagar na leitura perderia a queda se o app fosse
  fechado antes de a tela aparecer — que é exatamente o que acontece quando ele volta quebrado. Quem
  apaga é o B20, depois de ter oferecido o relatório.
- **O B15 instala o handler e entrega `readPendingCrashMarker()`; o ponto de chamada dela é o B20.**
  Um `val` que ninguém lê é aviso do compilador, não funcionalidade, e o consumidor da leitura é o
  diálogo, que só existe no B17. Ajuste declarado à ordem do plano, sem mudar o conteúdo de nenhuma
  das duas atividades.
- **A captura é dos limites da janela, nunca da tela.** O recorte é a diferença entre um diagnóstico e
  um vazamento: a tela inteira traria o que mais estivesse aberto — outra janela, um e-mail, um
  terminal com uma credencial na linha de comando — e o pacote vira issue pública. O que o `Robot` lê
  é o conteúdo do monitor naquele retângulo, então janela sobreposta aparece; não há como pedir ao
  compositor o conteúdo próprio da janela sem passar pelo pipeline do Swing, que não descreve o que o
  Skia do Compose desenhou. Está escrito no arquivo.
- **A captura roda depois do marcador**, dentro do handler: ela é a parte mais cara e a mais provável
  de falhar, e o marcador sozinho já entrega o relatório. Na ordem inversa, uma captura travada
  levaria junto o registro da queda. Sem captura, a imagem de uma queda anterior é **apagada** — ela
  mostraria uma tela que não é a do defeito sendo reportado.
- **`AppTextArea` é primitiva nova, criada e consumida no mesmo commit.** A descrição do bug é o único
  texto corrido que alguém escreve neste app, e o `AppTextField` é `singleLine` — uma descrição longa
  rolaria na horizontal numa linha só. Irmã e não um `singleLine` configurável: aquele campo é filtro,
  URL, apelido e chave numa linha de altura de controle, este cresce para baixo e ancora no topo, e um
  parâmetro faria a mesma primitiva responder a duas perguntas deixando a altura mínima sem dono.
  Registrada em `docs/design-system/components/forms/` (`.prompt.md`, `.jsx`, `.d.ts`) e no índice do
  `readme.md`, no mesmo commit — a regra de precedência só se sustenta enquanto os dois documentos
  descreverem o app inteiro.
- **`PRIMARY` do diálogo é "Salvar arquivo", não "Abrir issue no GitHub".** A ordem do fluxo é salvar e
  depois anexar; o botão que abre o navegador antes de existir arquivo levaria o usuário a publicar uma
  issue sem o pacote, que é o caso que o formulário existe para evitar. E `PRIMARY` é uma por tela.
- **Ação desabilitada carrega o motivo na tela.** Com a descrição em branco os dois botões ficam
  desabilitados e uma frase diz o que falta — controle cinza sem explicação é pior que controle nenhum,
  a mesma régua do interruptor de atualização automática.
- **A caixa da captura some quando não há como capturar** (`screenshotSupported = false`). Interruptor
  que não pode fazer nada é pior que interruptor nenhum: a caixa marcada prometeria uma imagem que
  nunca vem.
- **Nenhuma animação infinita** no diálogo — trava o `waitForIdle` dos testes de componente. A prévia
  entra e sai da composição, sem transição: animação finita não acrescenta nada a um bloco de texto.
- **Sem hostname e sem usuário do sistema.** O pacote vira o corpo de uma issue pública, e as duas
  informações identificam a pessoa sem ajudar a diagnosticar o app.

## Pontos de situação

| # | Micro-atividade | Situação | Commit | Verificação |
|---|---|---|---|---|
| B01 | Plano `docs/planos/relatorio-de-bug-execucao.md` com a tabela de pontos de situação | ✅ Concluída | `2a0ad62` | `gh issue view 123` lido; `SettingsDialogContent.kt:386-494` lido — `GeneralSettingsTab` sem nenhum `AppButton`, confirmando a resposta 1 |
| B02 | Comentário vivo criado na issue #123 com a tabela | ✅ Concluída | `20e588d` | `gh issue comment 123 --body-file status-123.md` → `issues/123#issuecomment-5462958514` |
| B03 | `BreadcrumbCategory` + `Breadcrumb` (domain) | ✅ Concluída | `ba721bf` | `desktopTest --tests "com.usagemonitor.domain.BreadcrumbTest"` → `tests="6" failures="0" errors="0"`, BUILD SUCCESSFUL em 34s |
| B04 | `BugReportMachineInfo` — OS, versão, arquitetura, JVM, versão do app, idioma, escala de UI, resolução, fuso | ✅ Concluída | `24e30b0` | `desktopTest --tests "com.usagemonitor.domain.BugReportMachineInfoTest"` → `tests="3" failures="0" errors="0"`, BUILD SUCCESSFUL em 33s |
| B05 | `BugReportEnvelope` + `toJson()` | ✅ Concluída | `3aa760e` | `desktopTest --tests "com.usagemonitor.domain.BugReportEnvelopeJsonTest"` → `tests="5" failures="0" errors="0"`, BUILD SUCCESSFUL em 29s |
| B06 | `toGithubIssueBody()` com truncagem (30 breadcrumbs, 6.000 chars) | ✅ Concluída | `89f1ddf` | `desktopTest --tests "com.usagemonitor.domain.BugReportIssueBodyTest"` → `tests="7" failures="0" errors="0"`, BUILD SUCCESSFUL em 1m 7s |
| B07 | `BreadcrumbRecorder` (interface no domain) + implementação nula | ✅ Concluída | `b4d5516` | `desktopTest --tests "com.usagemonitor.domain.BreadcrumbRecorderTest"` → `tests="1" failures="0" errors="0"`, BUILD SUCCESSFUL em 39s |
| B08 | `LocalBreadcrumbRecorder` — jsonl, lock, trim 200/100, `restrictToOwnerReadWrite` | ✅ Concluída | `887b7a3` | `desktopTest --tests "com.usagemonitor.data.LocalBreadcrumbRecorderTest"` → `tests="7" failures="0" errors="0"` |
| B09 | Pontos de chamada de navegação (abertura de cada tela/modal) | ✅ Concluída | `d60f74e` | `gradlew.bat compileKotlinDesktop` → BUILD SUCCESSFUL. **Nenhum teste da suíte exercita `main()`**; quem fecha isto é a `allTests` da auditoria |
| B10 | Pontos de chamada de use case (pedido do usuário e falha) | ✅ Concluída | (este commit) | `desktopTest --tests "com.usagemonitor.presentation.*"` → 32 classes, 382 testes, nenhuma `failures>0` (inclui os 4 do `DashboardViewModelBreadcrumbTest`) |
| B11 | Pontos de chamada nos `catch` que hoje falham em silêncio | ✅ Concluída | (este commit) | `desktopTest --tests "com.usagemonitor.presentation.*"` → 384 testes, nenhuma `failures>0` (382 antes, +2 da deduplicação do semáforo) |
| B12 | `GenerateBugReportUseCase` | ✅ Concluída | (este commit) | `desktopTest --tests "com.usagemonitor.domain.GenerateBugReportUseCaseTest"` → `tests="2" failures="0" errors="0"` |
| B13 | `CrashHandler` — handler, breadcrumb `CRASH`, marcador `pending-crash.json` | ✅ Concluída | (este commit) | `desktopTest --tests "com.usagemonitor.CrashHandlerTest"` → `tests="4" failures="0" errors="0"` |
| B14 | Captura best-effort da janela via capturer injetável | ✅ Concluída | (este commit) | `desktopTest --tests "…CrashHandlerTest" --tests "…WindowScreenshotCapturerTest"` → `tests="6"` e `tests="2"`, `failures="0"` nos dois |
| B15 | Registro do handler antes de `application { }`; `readPendingCrashMarker()` disponível | ✅ Concluída | (este commit) | `desktopTest --tests "com.usagemonitor.CrashHandlerTest"` → `tests="10" failures="0" errors="0"`; `compileKotlinDesktop` → BUILD SUCCESSFUL |
| B16 | `DesktopBugReportWriter` — diálogo de salvar, writer injetável | ✅ Concluída | (este commit) | `desktopTest --tests "com.usagemonitor.DesktopBugReportWriterTest"` → `tests="3" failures="0" errors="0"` |
| B17 | `BugReportDialog` stateless + primitiva `AppTextArea` | ✅ Concluída | (este commit) | `desktopTest --tests "com.usagemonitor.ui.BugReportDialogTest"` → `tests="12" failures="0" errors="0"` |
| B18 | Seção "Diagnóstico" na aba Geral com o botão `PRIMARY` | ⏳ Pendente | — | — |
| B19 | Botão "Abrir issue no GitHub" — URL montada e navegador aberto | ⏳ Pendente | — | — |
| B20 | Fluxo do marcador: arranque seguinte oferece o relatório e apaga o marcador | ⏳ Pendente | — | — |
| B21 | `prototipo-visual-opencode.html` — `§12 #cfg-geral` ganha a seção nova | ⏳ Pendente | — | — |
| B22 | `allTests` verde + QA manual: crash proposital, dark/light, PT/EN | ⏳ Pendente | — | — |

## Verificação

```bat
gradlew.bat desktopTest --tests "com.usagemonitor.domain.*"
gradlew.bat desktopTest --tests "com.usagemonitor.data.*"
gradlew.bat desktopTest --tests "com.usagemonitor.ui.*"
gradlew.bat allTests
```

QA manual: gerar um crash proposital e confirmar que o próximo arranque oferece o relatório com a
trilha e, quando possível, a captura da janela.

## Fora do escopo

- Envio automático de qualquer tipo (e-mail, webhook, telemetria).
- Log geral de toda a aplicação.
- Hostname, usuário do sistema, IP.
- Conteúdo de prompt, resposta de IA, corpo de resposta HTTP ou credencial em qualquer forma.
