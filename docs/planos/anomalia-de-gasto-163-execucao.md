# Detecção de anomalia de gasto (#163)

Plano de execução da [issue #163](https://github.com/edilsonvilarinho/usage-monitor/issues/163).

A tabela de **pontos de situação** no fim é atualizada no mesmo commit da atividade que ela descreve —
cada entrada carrega o comando que rodou e o resultado, nunca a intenção.

---

## Contexto

O app tem dois avisos de consumo, e os dois medem distância até o **teto**:

- o limiar de cota (`evaluateUsageAlerts` → `UsageAlert.QuotaThreshold`, `domain/entity/UsageAlert.kt`),
  que dispara em 75/90/100% da janela;
- a projeção de estouro antes do reset (`UsageForecast.riskSummary`, `UsageHistoryModels.kt`), que
  compara o ritmo observado com o tempo até o reinício.

Nenhum dos dois responde **"isso é normal para mim?"**. Um dia que consome três vezes o habitual não
gera aviso nenhum enquanto estiver longe do limite — e é justamente aí que mora o vazamento de custo
que a issue descreve: laço de agente rodando solto, script errado gerando volume, automação esquecida
ligada. Quando a cota ou o orçamento mensal soam, o gasto já aconteceu.

A métrica que falta é uma **linha de referência do próprio usuário**, e o dado para calculá-la já
está no disco: `usage_snapshots` guarda uma leitura a cada dez minutos, por conta e por cota, desde
sempre.

---

## Decisões travadas

| Ponto | Decisão | Motivo |
|---|---|---|
| Base da referência | Delta de cota do histórico (`usage_snapshots`) | Cobre as oito fontes, não só o Claude Code. O índice CLI mede dinheiro real, mas só enxerga um fornecedor |
| Origem dos pontos | `UsageHistorySeries.points` do relatório `LAST_7_DAYS` que o `DashboardViewModel` **já lê** a cada coleta (`refreshRiskSummaries`) | Zero SQL novo, zero método de repositório novo, zero leitura extra. O relatório já vem por alvo, a cada dez minutos, e já traz os pontos que a conta precisa |
| Estatística | **Mediana** dos dias completos, não média | Com três a seis amostras, um único incidente anterior levanta a média e mascara o próximo — que é exatamente o caso que a detecção existe para pegar. Divergência consciente do texto da issue |
| Alinhamento | Hoje **até a hora local corrente** contra os dias anteriores **até a mesma hora** | Comparar dia parcial com dia inteiro só dispararia à noite, quando o aviso já não serve para nada |
| Mínimo de histórico | Três dias completos com dado; referência zerada não alerta | Dado insuficiente não pode virar falso positivo. Volta de férias — seis dias zerados — fica deliberadamente fora |
| Piso absoluto | Hoje ≥ 20% do total da cota | Sem ele, 0,02% contra 0,005% é "4×": verdadeiro na aritmética e inútil como aviso |
| `PeriodType.REPORTED` | Fora | Mesma recusa que `UsageHistoryRepositoryImpl` já aplica a média e previsão: janela sem semântica local confiável. Cota com `total <= 0` também sai — sem denominador não há régua |
| Dedup | Um alerta por `(QuotaAlertScope, dia local)` | O escopo já existe e já identifica a cota através dos períodos. A janela de cota não serve como chave: um dia contém quatro ou cinco janelas de 5h |
| Silêncio | Adia sem marcar | Mesma regra dos outros três alertas: dentro do dia o consumo só cresce, então o aviso continua verdadeiro quando o silêncio terminar |
| Superfície visível | Tela de **Histórico** | A tela de Sessões CLI lê o índice SQLite dos transcripts e não tem acesso a snapshot de cota nenhum; levá-la para lá exigiria injetar `UsageHistoryRepository` no `CliSessionsViewModel`. O Histórico é por fonte e por cota, é onde `UsageHistorySeries` já vive, e já mostra `vs. período anterior` na mesma tabela de métricas |
| Enum novo | Nenhum | `UsageAlert` é `sealed interface` e ganha variante — o `when` exaustivo de `usageAlertMessage` passa a ser o guarda do texto faltando. `UsageAlertSettings` ganha dois campos com default, que é retrocompatível |

---

## Riscos declarados

1. **Falso positivo no primeiro dia atípico legítimo.** Quem trabalha de terça a quinta e emenda um
   sábado inteiro vai receber o aviso. As mitigações são a mediana (um sábado anterior já não move a
   referência), o piso de 20% da cota e o fator configurável — não há como distinguir "atípico
   legítimo" de "vazamento" a partir de contagem de tokens, e o plano não finge que há.
2. **Silêncio total em quem usa pouco.** Referência mediana zerada não alerta, então uma conta que
   consome perto de zero na maior parte dos dias nunca dispara. É deliberado: com referência zero,
   qualquer consumo é "infinitas vezes acima", e o alerta viraria ruído no primeiro dia de uso.
3. **A janela de leitura são sete dias, então a referência tem no máximo seis dias completos.**
   Aumentar para trinta significaria trocar o `HistoryRange` que o `refreshRiskSummaries` usa, e esse
   relatório também alimenta a projeção de risco — mudar a entrada dele para ganhar amostra na
   referência mexeria em duas coisas ao mesmo tempo.

---

## Pontos de situação

| # | Atividade | Comando | Resultado |
|---|---|---|---|
| A01 | Dono único do delta positivo | `gradlew.bat desktopTest --tests "com.usagemonitor.domain.PositiveDeltaTest" --tests "com.usagemonitor.data.UsageHistoryRepositoryImplTest"` | `BUILD SUCCESSFUL`, 0 falhas. `calculatePositiveDelta` era `private` em `UsageHistoryRepositoryImpl` e sobe para `domain/entity/UsageHistoryModels.kt` como `positiveDeltaOf`, com a semântica intacta — as três chamadas do repositório passam a apontar para ela. Nenhum comportamento muda; sem isto a referência diária seria um **segundo dono** da mesma conta, e as duas divergiriam justamente no tratamento do reset. O teste novo trava o que só existia de forma indireta: reset de janela contribui zero em vez de delta negativo, saldo pré-pago soma as **quedas**, recarga não conta como gasto |
| A02 | A métrica, como função pura | `gradlew.bat desktopTest --tests "com.usagemonitor.domain.UsageSpikeTest"` | `BUILD SUCCESSFUL`, **15 testes, 0 falhas**. `domain/entity/UsageSpike.kt`: `UsageDailyBaseline`, `UsageHistorySeries.dailyBaseline(now, timeZone)`, `detectSpike(...)` e `medianOf`. Nada em produção lê isto ainda — o commit é inerte. Três decisões ficaram travadas por teste, e nenhuma delas é dedutível do código: **(a)** os dias anteriores são recortados na **mesma hora do dia** (o ponto das 20h não entra numa avaliação das 12h30, senão o dia de hoje pareceria abaixo do normal toda manhã); **(b)** a **mediana** ignora um incidente anterior isolado — com quatro dias `100/100/100/1000`, a média daria 325 e o dia de 400 sairia em 1,23×, sem aviso, e a mediana devolve 100 e o aviso sai; **(c)** o corte de dia é o **local**, e o teste usa 23h BRT — que é 02h UTC do dia seguinte — para provar que agrupar em UTC inventaria um dia de referência. O piso de 20% da cota tem teste próprio, com o caso `40` contra mediana `10`: quatro vezes acima e irrelevante |
| A03 | O alerta e o texto dele | `gradlew.bat desktopTest --tests "com.usagemonitor.domain.UsageAlertTest" --tests "com.usagemonitor.presentation.UsageAlertMessagesTest"` | `BUILD SUCCESSFUL`, 0 falhas. `UsageAlert.SpendSpike`, `UsageAlertSettings.spikeAlertsEnabled`/`spikeFactor`/`effectiveSpikeFactor`, `UsageAlertState.firedSpikeDays` e `evaluateSpikeAlerts`. **O texto entrou nesta atividade e não na seguinte**: o `when` de `usageAlertMessage` é exaustivo e o `compileKotlinDesktop` reprovou com `'when' expression must be exhaustive` no primeiro build — separar as duas produziria um commit que só compila com o próximo. A dedup tem uma diferença que **não** é detalhe e ficou travada por teste: a memória de anomalia **não é podada** pela lista corrente, ao contrário de `evaluateQuotaAlerts`. O fator é `hoje / mediana` e os dois lados crescem ao longo do dia, então uma cota sai da lista e volta no mesmo dia; podando, a volta seria aviso novo sobre o mesmo dia. O título é fixo (`Consumo acima do habitual`) e o alvo vai no corpo, porque o de `QuotaThreshold` já é `alvo · cota` — com a mesma fórmula, os dois avisos chegariam à bandeja com a primeira linha idêntica dizendo coisas diferentes. `formatSpikeFactor` não usa `"%.1f".format`: aquele lê o `Locale` da JVM e o mesmo valor sairia `4.0` ou `4,0` conforme a máquina |
| A04 | Publicação pelo dashboard e fiação | `gradlew.bat desktopTest --tests "com.usagemonitor.presentation.DashboardViewModel*" --tests "com.usagemonitor.presentation.UsageAlertViewModelTest"` | `BUILD SUCCESSFUL`, 0 falhas. `refreshRiskSummaries` virou `refreshHistoryDerivedState` e passou a extrair as anomalias do **mesmo** relatório — o teste `spikes are derived from the same history report` conta as leituras e afirma **uma**, que é a razão de a métrica morar aqui e não num laço próprio. `UsageAlertViewModel` ganhou a quinta fonte no `combine`, e `evaluate` ganhou parâmetro com default, então os testes anteriores continuaram compilando sem toque. Em `Main.kt` foram **duas** linhas mais um movimento: `alertSettingsFlow` subiu para antes do `DashboardViewModel`, porque é dela que sai o `spikeFactorProvider` e ela era declarada 50 linhas abaixo. **Um defeito foi introduzido e corrigido dentro da atividade**: `publishSpikes()` estava fora do `stateMutex`, e como há uma coroutine por alvo escrevendo `cachedSpikeByTarget`, percorrer o mapa fora do lock daria `ConcurrentModificationException` numa máquina com duas contas — o mesmo motivo pelo qual `publishUiState` sempre foi chamada sob o lock |
| A05 | Preferências e controle nas Configurações | `gradlew.bat desktopTest --tests "com.usagemonitor.ui.AlertSettingsSectionTest" --tests "com.usagemonitor.UsageAlertPreferencesTest"` | `BUILD SUCCESSFUL`, **6 + 13 testes, 0 falhas**. Interruptor e segmentado de 2×/3×/5× na aba Alertas, mais a frase que diz contra o que a régua mede **e quando ela não mede** — sem a segunda metade, o silêncio de quem tem pouco histórico pareceria defeito. Segmentado e não chip pela mesma razão do limiar de "sem resposta": fator é escolha única entre alternativas, e os chips de quota logo acima respondem uma pergunta que aceita várias respostas ao mesmo tempo. O fator é gravado em **décimos inteiros** (`30`, não `3.0`): o valor vai em claro para o registro e um `Double` ali teria separador decimal dependente do idioma do sistema. O teste de componente exercita `AlertSettingsSection` direto, sem o `SettingsDialogContent` em volta — a seção é stateless e devolve o objeto inteiro já alterado |
| A06 | Linha na tela de Histórico | `gradlew.bat desktopTest --tests "com.usagemonitor.presentation.ui.HistoryMetricEntriesTest" --tests "com.usagemonitor.presentation.ui.HistoryScreenFormattingTest"` | `BUILD SUCCESSFUL`, **5 + 5 testes, 0 falhas**. Entrada `Hoje vs. mediana diária` na tabela de métricas, logo depois de `vs. período anterior` — as duas respondem "está mais ou menos que antes", e é a linha de previsão que responde "quanto falta para o teto". A referência de tempo é `report.lastUpdatedAt`, o mesmo carimbo que `riskSummary` já usa, e **nunca** `Clock.System.now()`: num composable ele mudaria a cada recomposição e o teste não teria valor previsível para afirmar. `historyMetricEntries` passou de `private` a `internal` — ela **não é `@Composable`**, então a decisão de mostrar a linha fica exercitável sem cena, sem montar um `HistoryViewModel` de mentira para um `if`. **Limite declarado**: os painéis próprios do DeepSeek e do OpenCode montam a própria lista de entradas e não passam por aqui, então a linha não aparece neles; o alerta continua cobrindo as oito fontes |
| A07 | Documentação obrigatória e suíte completa | `gradlew.bat allTests` + conferência de tags do protótipo | `BUILD SUCCESSFUL`, **1920 testes, 0 falhas**, 180 classes. Protótipo: linha nova nos dois painéis de `§5 · Histórico`, controles novos em `§12b · Configurações — Alertas`, e uma nota em cada seção com a razão da decisão. Conferência de tags fecha (`div` 856/856, `span` 1034/1034, `p` 113/113, `tr` 15/15, `td` 70/70, `button` 190/190). Kits `History.jsx` e `Settings.jsx` atualizados; **nenhuma primitiva nova**, então nenhum `.prompt.md` — `AppSwitch`, `AppSegmentedControl` e `AppMetric` já cobrem tudo. Catálogo de ajuda: o tópico `ALERTS` passou a dizer que este aviso responde a **outra pergunta** que os três anteriores, e o passo cita o rótulo real do controle. `alerts.gif` **não foi regerado** — a demo mostra a aba inteira e continua verdadeira; regerar por uma linha a mais custaria um binário novo no repositório. **Não verificado no app real**: a linha do Histórico exige três dias de snapshot e a verificação visual em `gradlew.bat run` ficou pendente |
