# Camada presentation — decisões e histórico

> Movido do `CLAUDE.md` em 2026-09-27 pela skill `usage-monitor-token-cleanup` (#319). O `CLAUDE.md` guarda as regras curtas e aponta para cá; o texto abaixo é o original, com os links relativos ajustados a este diretório.

### Camada presentation (`commonMain/presentation/`)

- `UiState`: `sealed interface` com `Loading`, `Success(data)`, `Error(message)`. Se uma API falhar e outra tiver sucesso, emite `Success` com dados parciais.
- `DashboardViewModel`: `StateFlow<UiState>` + coleta silenciosa **por alvo** (issue #269, plano [`coleta-adaptativa-269-execucao.md`](planos/coleta-adaptativa-269-execucao.md)). Escopo com `SupervisorJob` — falha de uma coroutine não cancela as outras. Chamar `onDestroy()` ao fechar janela.
  - **Cadência adaptativa, o `shouldRefresh` do Codenotch** (`isTargetDue` em `RefreshSchedule.kt`, estado em `DashboardRefreshScheduler`): 60 s com sessão CLI rodando (`isBusy`, ligado em `AppViewModels` a `SessionPulseViewModel.activeTargets`), 5 min sem nenhuma, já no reset vencido desde a última tentativa, nunca em backoff. Uso não anda enquanto nada o usa, e bater no endpoint numa tarde parada só gasta orçamento de rate limit. As constantes ficam em `DashboardViewModelConfig`; se a trilha mostrar 429 da Anthropic a 60 s, o próximo degrau é 300 s (o valor do ai-usagebar), e o ajuste é uma linha.
  - **Volta do laço que lança não encerra o laço** (issue #326): cada volta de `startCountdown` roda em `runCountdownTick` dentro de `try/catch`; a falha vira breadcrumb `ERROR` e a volta seguinte espera `pollLoopRecoveryDelay` (30 s). Sem isso o `SupervisorJob` mantinha o app vivo com a coleta automática morta, que é o defeito do codenotch#316 (11 dias com o número congelado). `CancellationException` continua subindo, senão `onDestroy` não encerraria o laço. Teste: `DashboardViewModelCadenceTest.a failing tick does not stop the collection loop`, que reprova sem o `catch`.
  - **Um laço só**, que coleta só os alvos devidos e dorme até a próxima cadência, o próximo reset ou um sinal (coleta nova, sessão começando, janela voltando). A tentativa é marcada **no despacho** (`recordAttempt`), não no fim: o laço não pode escolher o mesmo alvo duas vezes. A janela minimizada segura só a cadência; o reset coleta mesmo escondida.
  - **`refresh(target)`/`refresh(source)` mexem só no alvo pedido.** Antes a atualização de uma conta reiniciava a contagem de todas — o clique numa célula gastava o orçamento de rate limit das outras. A contagem do rodapé e do balão é a do alvo mais próximo (`publishNextPoll`), gravada em `nextRefreshAtMillis`; reabrir o app dentro dela não coleta de novo (`initialAttemptAnchor`). A gravação compara com o **último prazo gravado**, nunca com o da tela (issue #331): o prazo inicial não é gravado, e no Windows o relógio fica parado por até ~15 ms — a primeira coleta empatava com ele e a gravação era pulada. Era a falha "intermitente" do `DashboardViewModelRefreshPersistenceTest`, que virou sistemática na suíte completa; o teste com relógio congelado reproduz o empate sempre.
  - **`refresh(source)` de fonte já desligada poda e republica na hora** (`pruneAndPublish`), sem ir à rede. É o par que `SettingsActions.toggleApi` e `removeApiKey` chamam depois de tirar a fonte de `enabledApis`; antes o ramo era um `return` seco e o anel da fonte ficava na HUD até a coleta seguinte de outra fonte.
  - **Contas Anthropic devidas juntas saem espaçadas** por `anthropicStagger` (800 ms, `anthropicStaggerOrder`): o ai-usagebar registrou 429 com várias contas batendo juntas nos endpoints de uso e de token.
  - **Volta do sleep**: espera que termina mais de `sleepJumpThreshold` (2 min) depois do pedido é o PC acordando (`looksLikeWakeFromSleep`, o substituto na JVM do `didWakeNotification`), e todo alvo fica devido.
  - A fila de coletas (`DashboardFetchQueue`) e o funil de falhas (`DashboardFailureHandler`) saíram do view model pelo limite de 800 linhas.
  - **Publicação incremental** (`applyFetchResult`): cada alvo entra na tela quando a coleta dele termina, sob o `stateMutex`. Antes a fonte mais lenta segurava todas até o `awaitAll`. A gravação no histórico continua **sequencial** (`historyMutex`): a conexão do SQLite é serializada e disputada pelo indexador de sessões CLI. A dedup de `UsageAlertState` já tolera as emissões a mais.
  - **Prazo por fonte** (`perSourceTimeoutOverrides`/`timeoutFor`): a Anthropic tem 45 s, porque a renovação do token (25 s, `OAUTH_REFRESH_TIMEOUT_MILLIS`) e o GET de uso (15 s, `ANTHROPIC_USAGE_TIMEOUT_MILLIS`) têm prazos próprios por requisição e dividiam os 20 s. O Antigravity tem 50 s, acima dos 45 s do CLI, para a mensagem de timeout do processo chegar em vez da genérica, e `readUsageJson` roda em `runInterruptible`: com `withContext` o cancelamento não soltava a thread presa no `waitFor`, que segurava uma vaga do semáforo das coletas.
- Componentes UI: **todos stateless** (recebem dados via parâmetros, emitem eventos via lambdas). `DashboardScreen` é o único stateful.
- Timezone de reset: sempre `TimeZone.of("America/Sao_Paulo")` com label `BRT`.
- **Copiar sessão** (`CopySessionCommandButton`): a tela mostra o id truncado em 8 (`shortSessionId`), que **não** retoma nada — `claude --resume` só volta direto para a conversa com o session ID inteiro; com um prefixo ele cai no seletor interativo. Por isso o botão copia `claude --resume <uuid completo>` (`resumeSessionCommand`). No modal do time o transcript é de outra máquina, então ali `isLocalSession = false` copia só o identificador — comando que cairia num seletor vazio seria pior que botão nenhum. A escrita no clipboard passa por `rememberClipboardWriter()`, injetável, para o teste de componente não apagar o clipboard de quem roda a suíte.
- **Semáforo de sessão** (`SessionPulseViewModel`, laço de 30s): faz os botões de Sessões CLI e de time do card piscarem quando há sessão com turno nos últimos 5 min (`ACTIVE_SESSION_WINDOW_MILLIS`) e veredito `ATTENTION`/`SATURATED`. O corte de 5 min é `sinceEpochMillis` na consulta, **nunca** um valor novo em `CliSessionRange` — os `when` exaustivos dos chips quebrariam. O laço indexa antes de ler, senão a latência seria a do laço de background (10min). Com a janela minimizada (`isAppVisible` falso) a passada **local continua** — é ela que alimenta o alerta de sessão saturada, cujo destinatário é justamente quem não está olhando a tela; o que fica suspenso é a leitura do time (`refreshOnce(includeTeam = false)`), uma requisição por conta a cada 30s sem ninguém para ver o pisca. Os pulsos de time guardados envelhecem mesmo assim, senão voltariam acesos ao restaurar a janela. Leitura que falha **mantém** o pulso anterior; quem o apaga é `SessionPulse.prunedAt`, pela idade dos alertas — sem isso um servidor de time fora do ar deixaria o botão piscando indefinidamente. `sessionPulseFrame` é função pura (fase → severidade + alpha) para o pisca ser testável sem Compose, e `rememberSessionPulseFrame` **não cria transição infinita** sem pulso: uma animação sem fim trava o `waitForIdle` dos testes de componente. **Com pulso, o pisca mora atrás de `AppMotionPolicy.continuous`**: sem ela (testes, geradores, "Reduzir animações") o botão fica aceso e parado no pico da primeira severidade. A mesma leitura publica `activeTargets` — contas com turno nos últimos 5 min, **com ou sem** atenção —, que acende o arco de sessão ativa da HUD; `cliPulses` só guarda o que merece o pisca, e uma sessão saudável trabalhando não produzia sinal nenhum. `GetActiveCliSessionPulsesUseCase.activity()` devolve os dois de um `SELECT` só.

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
  - **Um eixo por aba, com filtro, ordem e paginação** (`CliUsageBreakdownPaging.kt` + `CliUsageBreakdownPane`): as seções empilhadas somavam projeto + modelo + branch + ferramentas + grade numa coluna só, e em produção isso é rolagem sem fim. `BreakdownAxis` é enum próprio — `CliSessionsView`/`TeamUsageView` escolhem a tela, este escolhe o recorte dentro dela, e um enum só carregaria combinações que não existem. **Só entram as abas com dado**: no modal do time não há ferramenta nem grade. `ACTIVITY` não tem filtro nem paginador porque não é lista — controle desligado é pior que controle ausente. `BreakdownSort` tem três valores e não seis: baldes e ferramentas respondem à mesma pergunta com números diferentes, e o rótulo da opção muda com o eixo (a ferramenta não tem custo nem tokens).
  - **O filtro casa por trecho e contra o texto exibido**, não contra o rótulo cru: o que identifica um caminho está no meio, e filtrar pelo `null` esconderia a linha que a tela chama "Sem branch".
  - **Preso na tela fica só o cromo pequeno** — abas e, numa faixa só, filtro, ordem e paginação. Os três escolhem parâmetros do mesmo conteúdo, e o paginador no rodapé era a primeira coisa a sair da tela numa janela baixa, justamente quando a lista é longa e ele serve para alguma coisa. Os totais entram na área rolável, pela mesma razão invertida: ~200dp de altura fixa no topo empurram a lista para fora. Filtro sem resultado **não apaga os totais**, que continuam verdadeiros.
  - **As linhas do eixo são tabela, não card**: uma faixa de legendas e `AppDataRow` por balde, com as colunas de `CliUsageBreakdownPane`. A coluna de tempo ativo aparece **uma vez para a lista inteira** ou não aparece — modelo e ferramenta não têm hora, e coluna que existe em algumas linhas e some em outras desloca tudo o que vem depois.
  - **A página se prende ao intervalo que existe** (`paginate`): a lista encolhe a cada tique do laço ao vivo, e a página que sumiu mostraria vazio em vez do fim dos dados. Lista vazia continua tendo uma página — "página 1 de 0" não é uma frase. Trocar eixo, filtro, ordem ou tamanho volta para a primeira página, via `remember(chaves)`.
  - **Filtro, ordem e página moram num `remember` da pane, não no ViewModel.** Ali só ficam as escolhas que a carga precisa conhecer; estas não mudam o que é lido do índice nem do servidor, e o `remember` sobrevive às emissões do laço ao vivo porque a pane não sai da composição entre elas.

- **Configurações em abas** (`SettingsTab` em `SettingsDialogContent.kt`): as seções (Geral, Alertas, APIs, Contas, Time, Rede — esta última da issue #174) eram uma coluna única de cartões empilhados, e achar qualquer uma exigia rolagem. Enum próprio e **navegação lateral**, com a coluna de seções fixa à esquerda — ela é o controle, e o conteúdo rolando não pode tirá-la da vista. **Só a aba escolhida entra na composição**, e não apenas fica fora da vista: com todas montadas as abas seriam decoração sobre a mesma coluna. A escolha mora num `remember` do próprio diálogo, que é uma janela separada e cujo estado nenhuma outra parte do app precisa conhecer; `initialTab` existe para os geradores de captura escolherem a seção. Cada aba tem seu próprio `ScrollState` começando no topo — reaproveitar um só faria a aba curta abrir rolada pela posição que a longa deixou.
  - **Cada aba monta o próprio painel** (`AppDataSurfaceFlush` + `AppSectionHeader`), com uma linha de dados por opção: rótulo em mono, descrição em sans, controle à direita, divisória entre elas. Não existe mais um cartão genérico envolvendo tudo — sete controles empilhados sem divisória e com todo rótulo em sans obrigavam a ler a lista inteira para achar uma opção.
  - **Ação que age sobre a lista inteira vai no `trailing` do cabeçalho** — "Redetectar" e "Adicionar" na aba Contas, o interruptor da integração na aba Time. No corpo elas competiam com as linhas.
  - **`PRIMARY` é uma por tela.** A aba Time tinha três botões primários ao mesmo tempo; primária é a ação que a tela propõe, e três delas não propõem nada.
  - **Tema é segmentado, não interruptor.** Interruptor diz ligado/desligado, e tema é escolha entre duas alternativas — a mesma pergunta que o seletor de idioma logo abaixo já respondia com um segmentado. O rótulo com emoji que existia ali era o único emoji da interface.

- **Banner de erro por alvo** (`DashboardWarning.target` + `warningTargetLabel`): o título usa `UiApiError.targetLabel` ("Anthropic — <perfil>") e cai no rótulo da fonte só quando não há alvo nomeado. Com várias contas Anthropic o título fixo produzia dois banners textualmente idênticos e ninguém sabia qual conta falhou. A ação recarrega **só o alvo do banner** (`refresh(target)`), não a fonte inteira — refazer a coleta dos perfis saudáveis é justamente o custo que um botão por banner evita. A ordem dos testes em `warningFor` importa: 429 e 503 são avaliados **antes** de credencial, então renovação que falha por limite ou indisponibilidade continua no banner de "aguarde" em vez de pedir login.

- **Aviso de recarga** (`isRefreshing` nos dois `Success` + `REFRESHING_NOTICE_TAG`): trocar a janela não passa por `Loading` — apagar a tela a cada clique é o pisca que o laço ao vivo existe para evitar. Mas sem aviso os números da janela **anterior** ficam na tela durante a ida ao servidor, e quem clicou em "30 dias" lê o total de "5h" como resposta. Só a ação do usuário liga a bandeira; o tique de 5s não, senão o aviso piscaria de cinco em cinco segundos e viraria ruído. É **texto, não indicador animado**: animação infinita trava o `waitForIdle` dos testes de componente. Na tela da máquina lista e resumo são duas leituras, e `breakdownReloadPending` segura o aviso até as duas chegarem — sem ele a primeira a terminar apagaria o aviso com o resumo antigo ainda na tela. Falha também apaga o aviso: a espera acabou, e o que resta é o erro, que tem linha própria.

- **Métricas por ferramenta** (`cli_turn_tools` + `CliToolUsage`): `ClaudeTranscriptMessageDto.content` é lido como `JsonElement` **cru** porque o campo é polimórfico — array de blocos nas linhas do assistente, string em outras; um tipo fixo faria o parse da linha inteira falhar num dos dois casos. Só o **nome** da ferramenta é extraído, nunca o `input` nem o texto. A tabela nasceu vazia para tudo que já estava indexado, e `syncIndex` só reprocessa arquivo alterado: por isso existe `INDEX_SCHEMA_VERSION` — subir o número força a releitura completa. O reset não apaga sessões nem turnos, que voltam pelos mesmos `INSERT OR IGNORE`/`OR REPLACE` e não duplicam. **O contador mora em `cli_index_meta`, não no `PRAGMA user_version`**, e o reset **apaga as linhas de `cli_session_files`**, não os offsets: as duas coisas juntas eram o motivo de a versão 1 nunca ter chegado a rodar. O `user_version` é um valor único por arquivo e o histórico de uso, que vive no mesmo `.db`, grava `3` ali a cada abertura — o índice lia esse número alheio como seu e concluía que já havia migrado. E zerar `last_offset` era inerte porque `syncIndex` pula por tamanho + data de modificação **antes** de consultar o offset; sem a linha do arquivo a varredura o trata como novo. **Ferramenta não entra em custo**: um turno que chama `Read` e `Bash` gastou tokens uma vez só, e ratear o custo entre as duas contaria o mesmo gasto duas vezes — a tela diz isso.

- **Conta da sessão** (`cli_sessions.profile_id`): os transcripts não carregam identidade, então a conta vem da raiz de onde o arquivo foi lido. Ela é gravada em **dois** lugares e nenhum é dispensável. `recomputeSession` só roda para sessão com turno novo, e um arquivo reprocessado só porque mudou de conta não traz turno nenhum: por isso `indexFile` carimba as sessões do arquivo **fora** do laço de `touchedSessions`. E `backfillSessionProfile` roda em **toda abertura**, não só quando a coluna é criada, porque foi exatamente assim que 58 sessões ficaram com `profile_id` nulo — o arquivo foi carimbado, a sessão não, e a partir daí a varredura passou a pulá-lo. Conta nula não é "todas as contas": `profile_id = ?` não casa com nulo e a sessão some de toda tela, sem erro nenhum. Ao acrescentar coluna nova em `cli_sessions`, pergunte por quem a preenche num arquivo que nunca mais vai mudar.

- **Tempo ativo de sessão** (`activeTimeMillisOf`): soma só os intervalos entre turnos consecutivos **menores** que `TURN_GAP_CUTOFF_MILLIS`, que é o mesmo `ACTIVE_SESSION_WINDOW_MILLIS` de 5 min já usado pelo semáforo — um segundo corte para a mesma pergunta daria duas respostas. Sem o corte, "duração" seria a distância entre o primeiro e o último turno e uma sessão retomada no dia seguinte "duraria" vinte horas. Só a thread principal: o subagente roda em paralelo e somar os intervalos dele contaria em dobro. Sessão de um turno não tem intervalo para medir e a métrica **não aparece** — "0min" seria lido como sessão instantânea.

- **Tempo ativo agregado** (`CliSessionSummary.activeMillis` + `CliUsageBucket.activeMillis`): a mesma definição de `activeTimeMillisOf` — soma dos intervalos entre turnos consecutivos da thread principal menores que `TURN_GAP_CUTOFF_MILLIS` — agora também por sessão da janela, por projeto, por branch e por integrante do time.
  - **`null` é "não medido" e zero é "medido e sem intervalo".** Colapsar os dois faria a tela afirmar "não trabalhou" onde a resposta certa é "não se sabe": sessão de um turno só dá zero, servidor de time anterior à 0.7.0 dá nulo.
  - **A consulta é `activeTimeMillisOf` escrita em SQL** (`SELECT_SESSION_ACTIVE_TIME_SQL`, com `LAG` sobre `cli_turns`), e a equivalência entre as duas é afirmada por teste, não deduzida da semelhança. O corte de 5 min vai **ligado como parâmetro**, nunca literal no SQL: a constante continua morando no domain. `is_sidechain = 0` porque o subagente roda em paralelo e somar os intervalos dele contaria o mesmo tempo duas vezes.
  - **Os eixos de modelo e de ferramenta ficam sempre nulos.** O intervalo entre dois turnos não pertence a um modelo; ratear inventaria número. Só projeto, branch e integrante — eixos em que a sessão inteira cai num balde só — têm hora. Por isso `toUsageBreakdown` recebe o mapa `sessão → millis` e o soma dentro do mesmo conjunto de sessões distintas que já alimenta `sessionCount`: uma sessão com três modelos entra com a hora dela uma vez só.
  - **No time o cálculo é do servidor** (`activity` em `GET /v1/team`, 0.7.0+), porque ele agrega por `(máquina, sessão, modelo)` e nunca mandou carimbo de turno. Lista **separada** de `rows`, nunca coluna dela, pelo mesmo motivo do parágrafo acima. O corte viaja do cliente em `gapCutoffMs` — o servidor não pode ser um segundo dono da constante, pelo mesmo princípio que já o mantém sem tabela de preços. Servidor antigo ignora o parâmetro e omite o campo; o cliente lê isso como hora não medida, sem gate por 404: é campo novo em rota existente, não rota nova.

- **Relatório PDF** (`presentation/ui/report/` + `desktopMain/PdfUsageReportRenderer.kt`): o recorte que está na tela em PDF, nas duas telas de sessões. **O documento é montado em `commonMain` e o desenho fica no desktop** — o PDFBox é JVM-only, e a divisão faz o *conteúdo* do relatório ser testável sem gerar um byte de PDF; o que o renderizador pode errar é só desenho.
  - **Não é um valor a mais em `UsageExportFormat`.** Os `when` daquele enum são sobre formato de texto, e um `PDF` ali obrigaria um ramo impossível em cada um. O PDF entra como ação própria (`exportReport`), e `UsageExportRequest` passa a carregar um `UsageExportPayload` — `Text` para CSV/JSON, `Report` para o documento.
  - **O relatório não segue a aba**, ao contrário do CSV: ele é a janela inteira, com totais, eixos e sessões juntos. Na tela da máquina isso obriga `exportReport` a **carregar o resumo se ele ainda não foi lido** — um PDF sem a seção de projetos surpreenderia mais que a espera. No time o resumo já vem na mesma resposta da lista, então não há o que carregar.
  - **Todo texto passa por um saneamento único** (`UsageReportDocument.sanitized`): o Helvetica base-14 só escreve WinAnsi, e um caractere fora dele faz o PDFBox lançar no meio da escrita — o relatório inteiro morreria por causa de um emoji num nome de pasta. Acento português passa direto; o resto vira `?`.
  - **`java.logging` entra na lista do jpackage** por causa do `commons-logging` que o PDFBox traz. Módulo faltando no runtime image **só aparece no app empacotado**, nunca no `gradlew run`.
  - Sem grade de atividade e sem ferramentas no relatório do time, pela mesma razão que a aba de resumo dele já as pula: o servidor não agrega por hora nem por ferramenta, e seção vazia sugere que não houve atividade.

- **Exportação** (`data/export/UsageExporter.kt` + `UsageExportWriter`): CSV e JSON de sessões, turnos e resumo. Mora em `data` porque o JSON usa `kotlinx.serialization`, que o domain não pode importar. O CSV escapa pelo RFC 4180 — nome de projeto e branch são texto livre, e uma vírgula sem escape deslocaria todas as colunas seguintes. Turno sem tarifa exporta célula **vazia**, não zero: zero afirmaria que não custou nada. O writer é injetável (`DesktopUsageExportWriter`) pelo mesmo motivo de `rememberClipboardWriter`: teste de componente não abre diálogo nem escreve no disco de quem roda a suíte. Cancelar o diálogo devolve `null` e **não** publica resultado — não é sucesso nem erro. Só metadados de uso saem daqui, nunca conteúdo de prompt ou resposta.

- **Orçamento mensal** (`MonthlyBudget.kt` + `GetMonthlyBudgetStatusUseCase`): teto em USD contra o custo estimado do índice CLI no mês corrente. **Independe do chip de janela** — orçamento é mensal, e amarrá-lo às 5h daria um número sem significado. O mês é o do fuso da apresentação, não UTC: às 22h do dia 31 em BRT já é dia 1 em UTC e o gasto cairia no mês seguinte. **Os créditos de uso da Anthropic ficam numa linha separada, com a moeda explícita** (`AccountCreditUsage`), e nunca somados: eles vêm na moeda real da conta — que pode ser BRL — e o custo do índice é sempre USD. Somar sem taxa de câmbio produziria um número inventado. Os créditos chegam do dashboard via `setAccountCredits`, porque a origem é a API e esta janela só conhece o índice local.

- **Comparativo período a período** (`UsagePeriodComparison`): a leitura do histórico passou a começar em `HistoryRange.previousWindowStart`, e os pontos anteriores entram no delta e, desde a #215, no gráfico como linha tracejada neutra (some sob zoom e quando há série sobreposta). Compara o **delta** de cada janela, nunca o acumulado: o acumulado zera no reset e a comparação viraria função de quando o reset caiu. Sem ponto na janela anterior não há comparação (zero ali significaria "não consumiu", quando foi "não havia dado"), e `changeRatio` é `null` com anterior zerado em vez de "infinito por cento". `TOTAL` não tem janela anterior. Série que só existe na janela anterior é descartada, e `lastUpdatedAt` continua sendo o carimbo da janela **corrente**.
- **Semanal sobreposta à intervalar** (issue #320, `HistoryChartOverlay`): o card agrupado (`buildGenericHistoryGroups`) desenhava só a série `INTERVAL`; a `WEEKLY` existia só como tabela, e a progressão dela no intervalo não aparecia em lugar nenhum. Agora as duas dividem o gráfico, com legenda escrita (`5h`/`7d`, de `quotaWindowLabel`). A principal continua dona do que depende de índice — zoom, ponto em foco, reinícios —, e a sobreposta é casada **por carimbo de tempo**, nunca por índice: as duas saem da mesma coleta, mas nada garante a mesma contagem de pontos. Só com as duas percentuais (eixo 0–100% comum). Com sobreposição a linha do período anterior some: três traçados deixam de ser legíveis, e o comparativo continua na tabela.
- **Análise por janela** (issue #320, `QuotaWindowAnalysis.kt`): "Consumido no período" somava os deltas de todas as janelas do intervalo e dividia pelo total de **uma** — trinta janelas de 5h numa semana davam 173%. `quotaWindowsOf` corta a série pelo mesmo critério de reinício da previsão (`splitIntoQuotaWindows`, que o `currentSegment` do repositório passou a usar: a última janela **é** o trecho corrente) e devolve por janela pico, instante em que esgotou (`null` = não esgotou), consumo em pontos percentuais e ritmo por hora. `quotaWindowStatsOf` faz as médias só sobre janelas **fechadas** — a aberta ainda sobe. `quotaHourlyDistributionOf` soma as subidas por hora BRT; a queda do reinício não entra. Tudo calculado **antes** da amostragem do Total: um ponto a cada N pode descartar justamente o pico ou o 100%. Saldo, cota reportada e série sem reinício conhecido não têm janela.
- **Seletor `5h | 7d | Ambas`** (issue #320, `HistoryQuotaView`, enum próprio — `BOTH` não é tipo de período): `selectQuotaView` só republica o `Success`, sem reler o SQLite, e a escolha sobrevive à troca de intervalo. Aparece só quando alguma família tem as duas janelas (`quotaViewLabels`); os rótulos saem das séries via `quotaWindowLabel`, porque a intervalar do MiniMax não é de 5 horas. O Codex passou para `GroupedHistoryContent` e ganha um card só para 5h+7d; reportada e mensal mantêm título e subtítulo da própria série. Na tabela, "Consumido no período" só fica para série **sem** janelas; com janelas entram "Janelas no intervalo" (com quantas esgotaram), "Pico médio por janela" e "Consumo médio por janela".
- **Painel de janelas** (issue #320, `HistoryWindowAnalysis.kt`): abaixo da tabela de cada série visível, as janelas do intervalo (mais recentes primeiro, teto `HISTORY_WINDOW_ROW_LIMIT` = 8; as demais ficam no resumo agregado) com início **observado**, pico, tempo até esgotar contado da primeira leitura e ritmo; `null` vira `—`, nunca zero. Abaixo, 24 barras do consumo por hora BRT; a frase da hora de pico é a informação e o `contentDescription` do `Canvas`, as barras não levam número. Série sem janela e sem distribuição não ganha painel — um painel dizendo "sem janelas" seria cromo.
- **Troca de intervalo sem piscar** (issue #320): `HistoryViewModel.reload(keepContent)` mantém o `Success` da mesma fonte com `isRefreshing = true` e o intervalo novo já marcado, e a tela esmaece o conteúdo (`REFRESHING_CONTENT_ALPHA`) até a leitura chegar. Antes cada troca publicava `Loading` e a janela inteira virava "Carregando histórico..." entre dois gráficos da mesma fonte. Trocar de **fonte** continua passando por `Loading`: o relatório anterior é de outra API. A entrada dos cards, a revelação da linha e o esmaecimento passaram para `appTween`, e com "Reduzir animações" viram salto — antes usavam `tween` cru e ignoravam a preferência.

- **Marca de ritmo na barra de cota** (issue #327; `QuotaInfo.periodStartAt` + `elapsedFractionAt` + `AppProgressTrack(marker)`): traço de 2dp na fração da janela já decorrida, onde o preenchimento estaria em ritmo constante até o reset. `periodStartAt` é campo novo **com default `null`** (retrocompatível, nenhum valor novo em enum): Anthropic o dá pelo nome da janela (`five_hour` = reset − 5h, `seven_day` = reset − 7d, e sem `resets_at` não há início), Codex por `resetAt − limit_window_seconds`, MiniMax por `start_time`/`weekly_start_time`. As outras fontes ficam sem marca — início não informado não é derivado. Sem reset conhecido (saldo pré-pago) ou com a janela vencida, também sem marca. A marca é pintada por `drawWithContent` **antes** do padding do trilho, para ocupar os 4dp inteiros e não os 2dp internos; não anda por mola. Posição não informa sozinha: a tooltip da cota ganha a linha "Janela decorrida: N% (marca na barra)" e o trilho recebe o mesmo texto como `stateDescription`. O cache do dashboard carrega o campo (`periodStartAtEpochMillis`, nulo no cache antigo). Prova por bitmap em `AppStatesTest` (armadilha #6: defeito de pintura só aparece em pixel).
- **Contas Codex extras** (issue #329; `CodexProfileRegistry`, `DashboardTargetFetcher`, `CodexAccountsSection`): um card e um anel da HUD por conta, "Codex — <pasta>". A conta padrão continua sem `profileId`; as extras entram logo depois dela em `enabledTargetsOf`. O roteamento de coleta por alvo saiu do `DashboardViewModel` para `DashboardTargetFetcher` (o view model estava em 754 linhas). Cor e emoji por conta continuam só da Anthropic: as três consultas passaram a ler `UsageTargetKey.anthropicProfileId`, que é `null` fora da Anthropic — o `profileId` sozinho deixou de dizer de qual registro ele é. O banner de falha nomeia a conta ("Codex — trabalho"). Configurações › Contas ganhou a seção "Contas Codex extras" abaixo da Anthropic; o motivo da recusa (sem `auth.json`, diretório da conta padrão) aparece num aviso ali, sem toast — `SettingsField` não ganhou valor novo.
- **Anel de uso no ícone da bandeja** (issue #328; `trayUsageRingFraction` em `presentation/ui/TrayUsageRing.kt`, `TrayRiskIconPainter(ringFraction)`, preferência `trayUsageRing` em `TrayUsageRingPreferences.kt`): desligado por padrão, liga em Configurações › Geral › Sistema. O valor é o **maior** percentual entre as cotas vigentes de todas as contas — a pergunta "alguma coisa está perto de acabar?" — e não o da primeira conta, que obrigaria a escolher uma conta sem pedir. Saldo em dinheiro e janela vencida ficam de fora; sem cota elegível não há anel. Cor pelos cortes 75/90 dos alertas, cinza claro abaixo (não verde, como o ponto em `ON_TRACK`). O painter entra no `equals` com a fração: o `Tray` reconstrói a imagem AWT a cada painter diferente, então ele só muda quando o percentual muda. Legibilidade em 16 px nas bandejas Linux e macOS não foi verificada em máquina real.
- **Alertas na bandeja** (`evaluateUsageAlerts` em `domain/entity/UsageAlert.kt` + `UsageAlertViewModel` + `Tray` no `AppTrayHost.kt`): notificação nativa quando uma cota cruza um limiar (default 75/90/100) ou uma sessão CLI satura. A decisão é **função pura** — entra `UiState.Success` (que já traz `riskSummaries`), o `SessionPulse` mesclado e o estado anterior; sai a lista a emitir e o estado novo. O view model **não tem laço próprio**: reage às emissões da coleta adaptativa (60 s com sessão ativa, 5 min sem — #269) e da passada de 30s que já existem.
  - **`UsageAlertState` é a dedup**, e sem ela o mesmo alerta sairia a cada coleta. A chave da janela é `QuotaAlertScope` (alvo + rótulo + `periodType`), **sem** o `periodEndAt`: o reset entra como valor guardado e a comparação passa por `isSamePeriod`, a mesma tolerância de 5 min que o histórico usa contra o jitter de ~1s do `resets_at`. Comparar o reset por igualdade rearmaria o alerta a cada poll.
  - **Cota vencida não alerta** (`isExpiredAt`): a janela descreve um período que já não existe.
  - **O limiar é piso**: o percentual é truncado, não arredondado — 89,9% não cruzou 90%.
  - **Silêncio adia, não consome.** No período silenciado o limiar cruzado **não** é marcado como disparado: dentro de uma janela o consumo só cresce, então o aviso é reavaliado e sai quando o silêncio terminar. Marcá-lo ali perderia o alerta para sempre.
  - **Desligar o alerta zera o estado**, para religá-lo voltar a avisar sobre a janela corrente em vez de herdar disparos antigos.
  - Só `SATURATED` vira notificação de sessão. `ATTENTION` apareceu em 7 das 70 sessões medidas (`CliSessionHealthThresholds`): notificar nesse patamar tornaria o alerta rotina.
  - **Anomalia de gasto** (`UsageSpike.kt` + `UsageAlert.SpendSpike`; issue #163, plano [`anomalia-de-gasto-163-execucao.md`](planos/anomalia-de-gasto-163-execucao.md)): pergunta **diferente** da do limiar. Aquele mede distância até o teto; este mede distância até o hábito do próprio usuário, e um dia três vezes acima do normal não cruza limiar nenhum enquanto estiver longe do limite — que é onde mora o vazamento de custo.
    - **Sai do relatório de histórico que o `DashboardViewModel` já lê a cada coleta** (`refreshHistoryDerivedState`, antes `refreshRiskSummaries`), e não de uma leitura própria: os pontos que a linha de referência precisa já estão ali. Uma segunda ida ao SQLite por alvo a cada dez minutos pagaria de novo por dado idêntico. `publishSpikes()` só é chamada **sob o `stateMutex`**, como `publishUiState`: há uma coroutine por alvo escrevendo `cachedSpikeByTarget`.
    - **A referência é a mediana, não a média.** Com três a seis amostras, um único incidente anterior levanta a média e mascara o próximo — que é justamente o que a detecção existe para pegar. Divergência consciente do texto da issue.
    - **Os dias anteriores são recortados na mesma hora do dia.** Comparar um dia parcial com dias completos só produziria aviso à noite, quando ele já não interrompe nada. O corte de dia é **local** (`ACTIVITY_TIME_ZONE_ID`): em UTC, 23h BRT já é o dia seguinte.
    - **Três recusas, todas deliberadas**: menos de `MIN_BASELINE_DAYS` (3) dias completos, mediana zerada (com ela, o primeiro dia de uso de quem consome quase nada já dispararia) e `PeriodType.REPORTED` ou `total <= 0` — a mesma recusa que a média por hora e a previsão já aplicam. Mais o piso de 20% da cota, que barra a razão explodindo onde os números são pequenos demais para significar algo.
    - **A dedup é por `(QuotaAlertScope, dia local)` e a memória não é podada pela lista corrente**, ao contrário de `evaluateQuotaAlerts`. O fator é `hoje / mediana` e os dois lados crescem ao longo do dia, então uma cota sai da lista e volta no mesmo dia; podando, a volta seria aviso novo sobre o mesmo dia.
    - O título da notificação é **fixo** e o alvo vai no corpo: o de `QuotaThreshold` já é `alvo · cota`, e repetir a fórmula faria os dois avisos chegarem à bandeja com a primeira linha idêntica dizendo coisas diferentes.
    - Na tela, a linha `Hoje vs. mediana diária` fica ao lado de `vs. período anterior` no Histórico — as duas respondem "está mais ou menos que antes". Os painéis próprios do DeepSeek e do OpenCode montam a própria tabela e não a trazem; o alerta continua cobrindo as oito fontes.
  - O ícone da bandeja é o do app **com um ponto** de risco no canto (`TrayRiskIconPainter`, com `equals` sobrescrito para o `Tray` não reconstruir a imagem AWT a cada recomposição). `ON_TRACK` não acende nada — ponto verde permanente vira decoração.
  - As preferências vão em `PreferencesSettings` (`UsageAlertPreferences.kt`), não em `~/.usage-monitor/`: ali moram os segredos do time, e limiar não é segredo. **`UserPreferences` (domain) é código morto** — nenhuma leitura o referencia; não use aquele caminho.
  - Fechar a janela continua encerrando o app: não existe "minimizar para a bandeja".

- **Sessão CLI sem resposta** (`CliStalledSession.kt` + `GetStalledCliSessionsUseCase` + `readSessionTails`;
  issue #177, plano [`sessao-cli-travada-execucao.md`](planos/sessao-cli-travada-execucao.md)): aviso quando
  o último pedido de uma sessão fica sem resposta acima do limiar (default 2h, segmentado de 30min a 4h na aba
  Alertas). Sai na bandeja e marca a linha da tela de Sessões CLI.
  - **A regra sugerida na issue não sobrevive à medição.** "Sem turno novo há X" marcaria **as 323 sessões** dos
    transcripts reais das três contas: pelo `last_ts` do índice, sessão encerrada e sessão travada são idênticas —
    as duas param de produzir turno. O discriminador está no `.jsonl`, não no índice.
  - **O marcador é `{"type":"system","subtype":"turn_duration"}`**, escrito pelo próprio CLI ao fechar um turno —
    não por hook do usuário (66 dos 181 arquivos que o trazem não têm `stop_hook_summary` nenhum). Pedido `user`
    posterior ao último marcador significa turno aberto que nunca fechou; com essa regra a marcação cai para **4 de
    181** sessões avaliáveis, todas abandonadas há mais de 500h.
  - **Ausência de marcador é `NOT_EVALUATED`, nunca "sem resposta"** — mesma recusa de `withKnownWindow()`. Entre as
    sessões que o app indexa a cobertura é ~96%; ficam de fora os transcripts de subagente (0 de 102) e um punhado
    de sessões conduzidas por harness de agente.
  - **Teto de 24h** (`STALLED_SESSION_MAX_AGE_MILLIS`): terminal fechado no meio de um turno deixa a cauda pendente
    para sempre, e sem o teto essas sessões virariam alerta a cada arranque — a dedup de `UsageAlertState` vive em
    memória. Processo que não existe há um dia também não queima cota, que é o que a detecção existe para flagrar.
  - **Só os últimos 256 KB do arquivo são lidos** (`SESSION_TAIL_WINDOW_BYTES`), e isso reproduz o veredito do
    arquivo inteiro nos 323 casos medidos; com 64 KB um deles degrada para `NOT_EVALUATED`. A primeira linha da
    janela vem cortada ao meio e é descartada.
  - **A cauda usa `ClaudeTranscriptTailLineDto`, um DTO próprio.** `ClaudeTranscriptLineDto` materializa
    `message.content` como `JsonElement`, e a cauda é justamente onde moram os `tool_result` de centenas de KB. O
    efeito colateral é a garantia de privacidade: esta leitura **não tem como** enxergar texto de prompt ou resposta.
  - **O caminho do transcript é derivado, não lido de `cli_sessions.file_path`.** O subagente vive em
    `<sessionId>/subagents/agent-*.jsonl`, carrega o `sessionId` do pai e é varrido junto — e o `UPSERT` grava
    `file_path = excluded.file_path`, então a coluna pode apontar para ele. A cauda do subagente responderia sobre
    o subagente.
  - **Nada disso entra no laço de indexação e não há bump de `INDEX_SCHEMA_VERSION`**: decodificar toda linha ali
    custaria uma releitura completa de 426 MB. A leitura é sob demanda, sobre um conjunto candidato de tipicamente
    0–5 arquivos, com cache por `(caminho, tamanho, data de modificação)` — sessão travada não escreve mais nada.
  - **Mora no laço do `SessionPulseViewModel`**, cuja passada local continua com a janela minimizada — que é o
    destinatário do aviso: quem deixou automação rodando e não está olhando a tela.
  - **O texto diz "sem resposta desde o último pedido", nunca "travou"**, e há teste afirmando isso nos dois
    idiomas: a evidência é o transcript, e o app não olha o sistema operacional.

## Sistema visual — janelas, cards e tooltips

Movido do `CLAUDE.md`, que guarda só as regras.

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
  janela, ou seja, menos conteúdo. A HUD se dimensiona pela própria geometria, que já recebe a
  escala; nos tamanhos default das janelas modais o fator entra na criação. Tamanho **persistido** é
  escolha do usuário e não é reescalado. (`scaledWindowSize`, que corrigia a janela principal pela
  razão entre a escala aplicada e a nova, saiu com ela.)
- A gravação acontece no commit do coletor com debounce, não no callback do slider. O conteúdo, esse,
  escala ao vivo.

**Monitores** (`ScreenLocator.kt`; issue #273): toda medida de tela lia o monitor padrão
(`defaultScreenDevice`, `maximumWindowBounds`). As janelas com posição salva (Histórico, Sessões CLI,
Uso e Presença do time) eram presas ao primário ao reabrir. Agora
`workAreaForPosition` encaixa a janela na área útil do monitor que contém o retângulo salvo (a maior
interseção), negativos inclusive; a HUD grava o monitor à parte (`HudWindowPreferences.kt`). Sem posição, ou com o
retângulo fora de todo monitor, vale o padrão. As funções de escolha recebem a lista de monitores e são
puras (`ScreenLocatorTest`, com monitor à direita, à esquerda e acima). **Monitores com escalas
diferentes só se validam em máquina real**: cada monitor tem o próprio espaço de usuário escalado e o
app trata pixel como dp. Os testes não pegam isso.
- O teste que prova a fiação (`AppThemeScaleTest`) mede **pixels** (`boundsInRoot`), não `Dp`: a
  conversão para `Dp` usa a densidade do próprio nó, que é a que está sendo alterada, e devolveria
  100dp nos dois casos — um teste que passa sem medir nada.

**Janela principal e dashboard — removidos do app** (setembro de 2026, plano
[`hud-modo-unico-execucao.md`](planos/hud-modo-unico-execucao.md)): a barra HUD é o único modo de
visualização. Saíram `MainWindowHost`, `DesktopWindowFrame`, a geometria persistida da janela
(`windowWidth`/`windowHeight`/`windowPlacement`/`windowX`/`windowY`), "Manter sempre visível"
(`alwaysOnTop`), o interruptor "Barra HUD", `Ctrl+Shift+H` e o sinal de janela minimizada
(`isAppVisible`: a HUD nunca minimiza). As chaves antigas ficam órfãs no registro, sem leitura.
- **O relatório de bug ganhou janela própria** (`BugReportWindow` em `AppWindowAnchor.kt`, uma
  `AppDialogWindow`): ele morava dentro da janela principal, e a HUD tem o tamanho do notch. É também
  a janela que abre no arranque depois de uma queda. A captura passa a ser da HUD.
- **Perdas aceitas**: reordenar cards por arrasto, minimizar card, o card completo com "tentar de
  novo" e a tela vazia "Abrir configurações". A HUD segue lendo o `cardOrder` gravado; recoletar é
  clique no anel; sem API as Configurações abrem sozinhas.
- `DashboardScreen`, a grade e o card continuam no código, consumidos só por testes e pelos geradores
  de captura. As duas seções abaixo descrevem esse código, não uma tela do app.

**Densidade do dashboard** (`DashboardScreen.SuccessContent` + `ResponsiveDashboardCardGrid`): a
janela principal usava o **corpo denso** do protótipo — `AppSpacing.md` na horizontal, `AppSpacing.sm`
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

**Modo somente cards e menu de modos — removidos** (setembro de 2026): a barra HUD passou a ser a
moldura reduzida única. Saíram `DesktopWindowFrame(compact)` com a faixa revelada no hover,
`DashboardScreen(showFooter)`, o enum `WindowMode` com o menu do rodapé e da faixa, as linhas de modo
no balão da engrenagem da HUD, o interruptor "Somente os cards", o item da bandeja, `Ctrl+Shift+M` e o
botão direito da HUD (hoje engolido pelo `hudPressGesture`, sem ação).
- Uma migração de `cardsOnlyMode=true` para `hudMode=true` entrou no primeiro commit e saiu no
  seguinte, quando a HUD passou a ser o único modo e `hudMode` deixou de existir.
- `AppMenu` continua primitiva publicada, sem consumidor no app.

**Piso de largura da tooltip de cota** (`shouldShowQuotaTooltip` em `ApiUsageCardDensity.kt`):
abaixo de 320dp de card o popup não abre. Ele tem piso de 180dp e cinco a seis linhas de métrica, e
a janela do antigo modo somente cards tinha ~230dp úteis — ali a tooltip cobre o card inteiro, escondendo
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
