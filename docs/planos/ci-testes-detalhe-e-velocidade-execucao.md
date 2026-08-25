# CI de testes: detalhe, confiança e tempo — plano de execução

| | |
|---|---|
| **Modelo** | Claude Opus 5 — `claude-opus-5` |
| **Nível de esforço** | não exposto ao agente nesta sessão |
| **Ferramenta** | Claude Code (CLI) |
| **Data** | 2026-08-25 |
| **Issue** | [#93](https://github.com/edilsonvilarinho/usage-monitor/issues/93) |
| **Branch** | `fix/ci-tests-detail-and-speed-93`, criada de `main` (`1e13fa0`) |
| **PR alvo** | `main` |
| **Autor dos commits** | `claude <claude@anthropic.com>` |

A seção **Pontos de situação** é atualizada a cada atividade, **no mesmo commit da atividade**, e a
seção **Problemas em aberto e riscos** recebe toda descoberta que aparecer no caminho.

## Contexto

A issue #93 pede duas coisas: *"avaliar sempre os CI de teste para validar o cenário"* e otimizar
para não ficar lento. A captura anexada mostra três checks verdes num PR — `CI / installer-scenarios`
em 54 s, `CI / tests` em **5 min** e `CI Server / tests` em **5 s**.

Os 5 s do check de servidor são o retrato do problema de confiança: naquele PR o filtro de path pulou
a suíte e **nenhum teste rodou**, e o check verde é indistinguível do de uma suíte executada. Os
5 min são o problema de tempo.

### Estado medido antes de qualquer mudança (2026-08-25)

| Verificação | Resultado |
|---|---|
| `CI / tests`, últimos três runs | 4 min 36 s, 4 min 39 s, 4 min 50 s — estável |
| Composição desses ~280 s (run `32841192171`) | ~57 s até a primeira tarefa (download do wrapper + configuração), ~22 s de recursos/codegen, ~114 s de `compileKotlinDesktop` + `desktopJar` + `compileTestKotlinDesktop`, **~91 s de `desktopTest`** |
| Cache do Gradle | `gradle cache is not found` em **todo** run desde 23/08, inclusive quando a chave gravada no run anterior era idêntica (`16bf0ce8…` em 23/08 e 24/08) |
| Caches ativos do repositório | `active_caches_size_in_bytes: 1571668810`, `active_caches_count: 4` — **todos macOS**, gravados pelo `release-linux.yml` em 19/08 e 22/08. Nenhuma entrada Windows |
| Causa do cache perdido | post-step do `setup-java`: `tar.exe: C\:/Users/runneradmin/.gradle/caches/8.6/fileHashes/fileHashes.lock: Read error at byte 0 … Device or resource busy` e, no run `32780176169`, `##[warning]Failed to save: … The process 'C:\Program Files\Git\usr\bin\tar.exe' failed with exit code 2`. O daemon do Gradle continua vivo quando o arquivo é criado |
| `org.gradle.caching` / configuration cache | não configurados |
| Suíte desktop | 1293 testes, 122 classes, 0 falhas, **0 `@Ignore`**, ~2,6 asserts por teste |
| Classes mais caras | `ComponentTest` 38,5 s/69 testes; `TeamUsageScreenTest` 14,1 s/47; `CliSessionsScreenTest` 7,2 s/45 |
| `maxParallelForks` | não configurado → **1 fork**, execução serial |
| Kover 0.9.8 | plugin aplicado e `:koverFindJar` roda no CI, mas **nenhuma tarefa de relatório é executada em lugar nenhum**. Custo de instrumentação pago, número de cobertura zero |
| Suíte do servidor | 167 testes, 13 arquivos, 5,5 s |
| Isolamento de estado nos testes | 19 arquivos criam diretório temporário próprio; os testes de `Preferences` usam nó com nome aleatório; nenhum `System.setProperty`; nenhuma escrita no `~/.usage-monitor` real |
| Detalhe publicado num run verde | **nenhum** — o relatório só sobe `if: failure()` e não existe step summary |
| Vermelho na `main` em 25/08 00:17 (run `32793042676`) | `choco install nsis` → `community.chocolatey.org` respondeu **504 Gateway Timeout**. Sem defeito de código, sem retry |

O resultado pretendido: um check verde que diz **o que rodou, quanto rodou e quanto cobriu**; um
check que pulou a suíte dizendo que pulou; e o job `tests` abaixo de ~3 min.

### Medição de linha de base local (A1, 2026-08-25)

Máquina de desenvolvimento, 16 processadores lógicos, dependências já no `~/.gradle` e daemon quente.
Serve para separar o que é custo do projeto do que é custo do runner.

| Cenário | Comando | Tempo |
|---|---|---|
| Build completo do zero (só o `build/` limpo) | `gradlew.bat clean` + `gradlew.bat allTests --profile` | **2 min 28,78 s** |
| — dentro dele, `:desktopTest` | | 1 min 21,71 s (55%) |
| — dentro dele, `:compileKotlinDesktop` | | 44,52 s |
| — dentro dele, `:compileTestKotlinDesktop` | | 19,81 s |
| — dentro dele, `:desktopJar` | | 0,84 s |
| — configuração + resolução de dependências | | **0,44 s** |
| Só a suíte, serial, Kover ligado | `gradlew.bat cleanDesktopTest desktopTest -PtestForks=1` | 1 min 24 s |
| Só a suíte, 2 forks | `-PtestForks=2` | 1 min 12 s (−14%) |
| Só a suíte, 4 forks | `-PtestForks=4` | **52 s (−38%)** |
| Só a suíte, 4 forks, **sem** Kover | plugin comentado | 45 s |
| Só a suíte, serial, **sem** Kover | plugin comentado | 1 min 18 s |

O que esses números decidem:

1. **A fatia de ~57 s que o CI gasta antes da primeira tarefa é download, não configuração.** Aqui a
   mesma fase custa 0,44 s porque o `~/.gradle` já tem tudo. É exatamente o que a A2 recupera.
2. **Forks paralelos valem a atividade A3.** O piso é a classe mais lenta: `ComponentTest` sozinha
   leva 41,4 s dos 52 s, porque o Gradle distribui por classe. Além de 4 forks não há o que ganhar
   enquanto ela existir. A soma das durações por classe é 108,2 s contra 52 s de relógio — a
   paralelização está de fato acontecendo, e o resultado é **idêntico**: 1293 testes, 122 classes,
   0 falhas, 0 pulados, somados dos XML de `build/test-results/desktopTest`.
3. **A instrumentação do Kover custa 6–7 s por passada e hoje não produz nada.** Por isso a A5 a
   deixa desligada por default e a liga sob `-Pcoverage`, que é o que o passo da `main` usa.
   A API foi conferida no fonte da 0.9.8 (`KoverProjectExtension`, `KoverVariantConfig`,
   `KoverNames`): `currentProject { instrumentation { disabledForAll } }` e as tarefas
   `koverXmlReport`/`koverHtmlReport`/`koverLog`/`koverVerify` existem com esses nomes.

### Fora de escopo

- **Configuration cache.** Ganho estimado de 10–20 s e exige `cache-encryption-key` (segredo novo)
  para persistir entre runs, além de risco com `buildNsisInstaller`, que avalia `file(...).exists()`
  e chama `logger` em tempo de configuração.
- **Matriz Linux/macOS para `allTests`.** Os testes de componente usam `runDesktopComposeUiTest` e
  nunca rodaram headless; o comentário no `ci.yml` já registra a decisão.
- **Sharding em vários runners.** Só compensa depois de o cache de build funcionar; recompilar em
  dois runners hoje custaria mais do que economiza.
- **Retry automático de teste falho.** Não há flakiness medida na suíte — as duas falhas recentes
  foram infraestrutura (chocolatey) e o `no merge base` já corrigido no PR #85.

## Atividades

| # | Atividade | Entrega |
|---|---|---|
| A1 | Linha de base medida localmente | Tabela de medição neste documento |
| A2 | Corrigir o cache do Gradle no CI | `ci.yml`, `gradle.properties` |
| A3 | Paralelizar a execução dos testes | `build.gradle.kts` |
| A4 | Detalhe do que rodou, em todo run | `tools/ci/test-summary.mjs`, os dois workflows |
| A5 | Cobertura publicada, sem trava | `build.gradle.kts`, `ci.yml` |
| A6 | Tirar o chocolatey do caminho crítico | `ci.yml` |
| A7 | Auditoria do que não é testado | Seção neste documento + issues |
| A8 | Registrar as decisões no `CLAUDE.md` | `CLAUDE.md` |

## Auditoria do que não é testado (A7)

Feita sobre o relatório da A5 — 1005 classes com contador de linha, **82,7%** de linhas cobertas —,
não sobre a heurística de "arquivo sem teste homônimo", que apontava 127 de 235 arquivos e não
distinguia caso de uso exercitado pelo teste do ViewModel de código que nunca roda.

### Lacuna real

| Classe | Linhas descobertas | Cobertura | Por quê é lacuna |
|---|---:|---:|---|
| `data.datasource.RemoteTeamDataSource` | 158 | **1,9%** | 480 linhas, 20 métodos `open suspend fun`. Os testes **herdam** dela (`FakeRemoteTeamDataSource : RemoteTeamDataSource(HttpClient())`) e sobrescrevem tudo — a costura testada fica um nível acima do código que carrega o risco. Nada do que o `CLAUDE.md` descreve como sutil executa: o 404 lembrado por URL de `presence`/`trend`, o `x-admin-token` recusado no ingest, o `allowClaim`. O repositório já tem a ferramenta: `ktor-client-mock` está em `commonTest` e **onze** arquivos usam `MockEngine` |
| `usecase.GetTeamUsageTrendUseCase` | 15 | **0%** | Aplica `ModelPricingTable` sobre as linhas cruas do servidor e monta o eixo de dias — as duas decisões que o `CLAUDE.md` registra como cliente-side |
| `TeamUsageWindowPreferencesKt` | 54 | **0%** | Mesmo padrão de oito arquivos de preferência que **têm** teste (`MainWindowPreferencesTest`, `HistoryWindowPreferencesTest`, …) |
| `CliSessionsWindowPreferencesKt` | 54 | **0%** | idem |
| `BudgetPreferencesKt` | 17 | **0%** | idem |
| `update.UpdateReceiptReaderKt` | 28 | **0%** | Leitura de arquivo, do mesmo tipo já coberto por `UpdateArtifactDownloaderTest` |
| `datasource.LocalDashboardCacheDataSource` | 23 | **0%** | Único `Local*DataSource` sem teste; todos os outros têm |
| `presentation.ui.UsageAlertMessagesKt` | 23 | **0%** | Formatação de texto, do mesmo tipo de `SessionPulseFormattingTest` |

### Não testável pela suíte atual — e por isso não vira issue

| Classe | Linhas descobertas | Motivo |
|---|---:|---|
| `presentation.ui.DesktopWindowFrameKt` (+3 lambdas) | 121 + 107 | Carrega `WindowDraggableArea`, que exige janela AWT real; `runDesktopComposeUiTest` compõe fora de janela |
| `components.ShimmerBoxKt` | 24 | Animação infinita — o próprio `CLAUDE.md` proíbe uma em teste, porque trava o `waitForIdle` |
| `ReleaseNotesWindowKt`, `update.AutoUpdateControllerKt` | 16 + 13 | Ciclo de vida de janela e de processo |
| `usage_monitor.generated.resources.*` | 15 | Código gerado |
| `components.ActivityHeatmapGridKt` | 59 | Desenho puro em `Canvas`; a lógica da grade vive no domain e **está** coberta |
| `AutoStartManager` | 132 | 41,3%: o que falta são os ramos de macOS e Linux, que não executam no runner Windows |

### Coberto indiretamente — não é lacuna

`data.dto` fica em 75,7% porque DTO não tem lógica: o que o exercita são os testes de mapper. O mesmo
vale para as interfaces de repositório e para os `*Labels`, atingidos pelos testes de componente.

---

## Pontos de situação

Uma linha por atividade, escrita **no mesmo commit** da atividade. `Evidência` é o comando que rodou
e o resultado, não a intenção. A coluna `Commit` guarda o **assunto** do commit, não o hash: um
commit não pode conter o próprio hash.

| # | Data | Commit | Atividade | Estado | Evidência |
|---|---|---|---|---|---|
| A1 | 2026-08-25 | `docs(plan): measure the CI test baseline before changing it` | Linha de base medida | concluída | Tabela *Medição de linha de base local* acima. `gradlew.bat allTests --profile` deu **2 min 28,78 s** com `:desktopTest` em 1 min 21,71 s e **0,44 s** de configuração + resolução — contra os ~57 s que o mesmo trecho leva no runner, o que localiza a perda no **download**, não na configuração. Forks: 1m24s serial → 1m12s com 2 → **52 s com 4**, com o resultado idêntico (1293 testes, 0 falhas) conferido nos XML de `build/test-results`. Kover custa 6–7 s por passada sem gerar relatório nenhum. **Descoberta fora do roteiro:** o `clean` falhou com `Unable to delete directory 'build'` porque o `Usage Monitor.exe` de `build/compose/binaries` estava com o atributo **ReadOnly** — nenhum processo o segurava (`[System.IO.File]::Open` devolveu *Access denied*, não *in use*). Removido o arquivo, o `clean` passou |
| A2 | 2026-08-25 | `ci: persist the gradle cache with the gradle action` | Corrigir o cache do Gradle no CI | concluída | `cache: 'gradle'` sai do `actions/setup-java` e entra `gradle/actions/setup-gradle@v4` depois do checkout, com `cache-read-only: ${{ github.ref != 'refs/heads/main' }}`; a chamada vira `gradlew.bat allTests --build-cache` e o `gradle.properties` ganha `org.gradle.caching=true`. **Precedente já no repositório:** o job `build-windows` do `release-linux.yml` usa a antecessora `gradle/gradle-build-action@v2`, então não é action nova de terceiro. `python -c "yaml.safe_load(...)"` nos dois workflows: `ci.yml -> ['tests', 'installer-scenarios']`, `ci-server.yml -> ['tests']`. `gradlew.bat desktopJar --build-cache`: BUILD SUCCESSFUL in 16s, 8 actionable tasks. A prova de que o cache passou a existir só sai no runner e vai na verificação final |
| A3 | 2026-08-25 | `build: run the test suite in parallel forks` | Paralelizar a execução dos testes | concluída | `tasks.withType<Test>` com `maxParallelForks = availableProcessors().coerceIn(1, 4)` e `maxHeapSize = "1g"`, com override por `-PtestForks` para medir sem editar o build. **Três execuções reais** (`gradlew.bat cleanDesktopTest desktopTest --no-build-cache`): 52 s, 53 s e 55 s contra 1 min 24 s serial, e as três com `1293 | 0 | 0 | 0 | 122` — mesma contagem de teste, classe e falha da execução serial. Efeito colateral medido do `org.gradle.caching` da A2: repetir a tarefa sem mudar entrada devolve **BUILD SUCCESSFUL in 2s**, com o `Test` restaurado do cache de build — daí o `--no-build-cache` nas três medições, senão a segunda e a terceira não teriam executado nada |
| A4 | 2026-08-25 | `ci: publish what each test suite actually ran` | Detalhe do que rodou, em todo run | concluída | `tools/ci/test-summary.mjs` novo, sem dependência externa, lendo JUnit XML — o mesmo parser para os dois jobs, porque duas implementações divergiriam justamente na contagem. O `vitest` passa a escrever o mesmo formato (`npm run test:ci`). Os dois workflows publicam o resumo com `if: always()`, e o ramo que pula a suíte publica **NAO EXECUTADA** com o motivo e a contagem de arquivos avaliados — o filtro perdeu o `break` para poder contar. `--require` derruba o job quando a suíte devia rodar e não produziu XML: é o que faz "passou sem executar" ficar vermelho. O checkout deixou de ser condicional (~5 s) porque o script precisa existir no ramo que ele anuncia. Verificado localmente com `GITHUB_STEP_SUMMARY` apontando para um arquivo: saíram os três blocos — `1293 | 0 | 0 | 0 | 122`, `167 | 0 | 0 | 0 | 13` e o bloco de suíte não executada. `yaml.safe_load` nos dois workflows lista os oito/nove passos na ordem certa |
| A5 | 2026-08-25 | `ci: report coverage on main instead of paying for it silently` | Cobertura publicada, sem trava | concluída | Bloco `kover { }` novo: a instrumentação vira **opt-in** por `-Pcoverage` (`disabledForAll.set(!providers.gradleProperty("coverage").isPresent)`), `MainKt` sai do relatório por filtro e `html`/`xml` saem do `check`. **O gatilho foi provado, não deduzido:** `gradlew.bat desktopTest` sem a propriedade **reexecutou** a tarefa (`> Task :desktopTest`, 47 s) contra 53 s instrumentado — o agente muda a entrada da tarefa. `gradlew.bat koverXmlReport koverHtmlReport -Pcoverage`: BUILD SUCCESSFUL in 9s com `:desktopTest` UP-TO-DATE, que é o desenho — a mesma passada serve suíte e relatório. **Linha de base: 82,7% de linhas, 52,3% de ramos, 80,4% de métodos, 81,1% de classes (15 985 de 19 323 linhas).** `grep -c 'name="com/usagemonitor/MainKt' report.xml` devolveu **0**, confirmando o filtro. `tools/ci/coverage-summary.mjs` novo lê os contadores do **fechamento** do documento — ler os do topo devolvia o primeiro pacote e imprimia `--` no lugar do total, defeito pego na primeira execução |
| A6 | 2026-08-25 | `ci: stop a chocolatey outage from failing the installer job` | Tirar o chocolatey do caminho crítico | concluída | Os dois passos passam a **detectar antes de instalar** — `makensis.exe` nos dois caminhos do `Resolve-MakeNsis` e `candle.exe` pelo `%WIX%in` ou pelo PATH, a mesma detecção do `Resolve-WixBin` — e só chamam o chocolatey quando falta, com três tentativas e espera crescente. O WiX **não lança** ao fim: sem ele o roteiro pula o S7 com aviso, e derrubar o job custaria os outros seis cenários. Sintaxe dos dois blocos validada com `[System.Management.Automation.Language.Parser]::ParseInput`: ambos sem erro (os três blocos que o parser reprova contêm `${{ }}` do GitHub ou são `bash`, e já eram assim). Motivo registrado no comentário: o 504 do run `32793042676` |
| A7 | 2026-08-25 | `docs(plan): audit what the suite does not cover` | Auditoria do que não é testado | concluída | Seção *Auditoria do que não é testado* acima, feita sobre o relatório da A5 (1005 classes, 82,7% de linhas). **O achado que muda a resposta da issue:** `RemoteTeamDataSource` está em **1,9%** não por falta de teste, mas porque os testes **herdam da classe real** (`FakeRemoteTeamDataSource : RemoteTeamDataSource(HttpClient())`) e sobrescrevem os 20 métodos — a costura testada fica um nível acima do código que carrega o risco, e os 480 linhas de HTTP nunca executam. Duas issues abertas: [#94](https://github.com/edilsonvilarinho/usage-monitor/issues/94) e [#95](https://github.com/edilsonvilarinho/usage-monitor/issues/95). O resto foi classificado como não testável (`DesktopWindowFrame` precisa de janela AWT, `ShimmerBox` é animação infinita) ou coberto indiretamente (DTO pelos mappers), e **não** virou issue |
| A8 | 2026-08-25 | `docs: record the CI and test decisions` | Registrar as decisões no `CLAUDE.md` | concluída | Seção **CI e testes** nova, com as sete decisões e o número que sustenta cada uma: por que o cache é da action da Gradle, por que só a `main` escreve, o teto de 4 forks e a advertência sobre teste que grava em caminho fixo, por que um job pulado tem de dizer que pulou, o parser único de JUnit XML, a cobertura como relatório e não trava (linha de base 82,7%) e a lição de costura do `RemoteTeamDataSource`. O bloco de comandos ganhou `-PtestForks` e `-Pcoverage` |
| A9 | 2026-08-25 | `ci: make the installer scenarios job say what it ran` | Resumo do job de cenários do instalador | concluída | **Atividade não prevista, descoberta ao ver o próprio PR rodar.** O `installer-scenarios` do run `32853549920` ficou **verde em 12 s** sem executar cenário nenhum — o filtro de path pulou tudo —, que é exatamente o defeito da issue #93 num terceiro check que a A4 não tinha tratado. Agora o passo publica a linha `Verificacoes: N   Falhas: M` que o roteiro já imprimia, e o ramo pulado publica **NAO EXECUTADA**. **Defeito pego na medição:** com `2>&1` o `Tee-Object` não recebia linha nenhuma, porque o roteiro fala por `Write-Host`, que escreve no stream de informação (6) e não no de erro — o resumo saía com "contagem nao encontrada". Com `*>&1` e `ToString()` a linha aparece. Os três caminhos foram exercitados com roteiros sintéticos: saída 0 → `passou` com a contagem; `exit 1` → `FALHOU` com a contagem; `throw` → `FALHOU` pelo `catch`, com o código de saída reemitido à mão porque o epílogo do pwsh só olha o último comando |
| A10 | 2026-08-25 | `build: default the suite back to a single test fork` | Reverter o default dos forks paralelos | concluída | **A A3 caiu no critério que ela mesma declarou.** O run `32854143312` derrubou `AppThemeScaleTest` mais 40 testes de `ComponentTest` com `ExceptionInInitializerError`. Causa raiz no XML do artefato, não deduzida: `java.nio.file.AccessDeniedException: ~/.skiko/5e2d47dc…/skiko16867069771766376779 -> …/skiko-windows-x64.dll` em `org.jetbrains.skiko.Library.unpackIfNeeded(Library.kt:40)`. Cada fork é uma JVM, todas carregam o Skiko, e no Windows o `Files.move` falha quando outro processo já abriu o destino. **Isso explica a divergência local × CI:** a máquina de desenvolvimento tem esse arquivo, com esse mesmo hash, desde 15/05/2025 — cache quente, sem extração, sem corrida; o runner nasce vazio. O primeiro run do CI (`32853549920`) passou por sorte de escalonamento, o segundo não. O default volta a **1 fork** e `-PtestForks=N` fica como opt-in para máquina com o cache quente. `gradlew.bat cleanDesktopTest allTests --no-build-cache`: BUILD SUCCESSFUL in 1m 24s, `1293 | 0 | 0 | 0 | 122`. Continuação em [#97](https://github.com/edilsonvilarinho/usage-monitor/issues/97) |

---

## Problemas em aberto e riscos

| # | Risco | Estado |
|---|---|---|
| R1 | Forks paralelos podem introduzir intermitência nos testes de componente Compose | **materializou-se e foi revertido na A10.** Não era intermitência do teste: é corrida na extração da nativa do Skiko, que só existe com `~/.skiko` frio. Continuação em [#97](https://github.com/edilsonvilarinho/usage-monitor/issues/97) |
| R2 | `gradle/actions/setup-gradle` é action fora do namespace `actions/*`. É mantida pela Gradle Inc. e é a recomendação oficial; a alternativa (`gradlew --stop` como último passo do job, deixando o `setup-java` cuidar do cache) fica registrada aqui caso se queira voltar atrás | aberto |
| R3 | O cache do Gradle no Windows é grande e o repositório tem teto de 10 GB. Com `cache-read-only` nos PRs só a `main` escreve, então há no máximo uma entrada Windows por hash de build | aberto |
| R6 | O `Test` do Gradle é uma tarefa cacheável, então com `org.gradle.caching=true` um run cujas entradas não mudaram restaura o resultado em vez de reexecutar a suíte. É a semântica correta do cache — mesma entrada, mesmo resultado — e o `build/test-results` volta junto, então o resumo continua trazendo os números reais. Fica registrado porque, lido no relógio do check, um job de 20 s parece suíte pulada | aberto |
| R5 | O `jpackage`/instalador deixa `build/compose/binaries/main/app/Usage Monitor/Usage Monitor.exe` com atributo **ReadOnly**, e o `clean` do Gradle não o apaga. Só afeta build local — no CI o workspace nasce vazio | aberto |
| R4 | A DSL do Kover mudou entre a 0.7 e a 0.9; os nomes de tarefa usados na A5 precisam ser confirmados antes de irem para o workflow | **fechado na A5** — conferidos no fonte da 0.9.8 (`KoverNames`, `KoverProjectExtension`, `KoverVariantConfig`) e executados de verdade: `koverXmlReport` e `koverHtmlReport` rodaram e escreveram `build/reports/kover/` |
