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

---

## Problemas em aberto e riscos

| # | Risco | Estado |
|---|---|---|
| R1 | Forks paralelos podem introduzir intermitência nos testes de componente Compose, que hoje rodam numa JVM só. O critério de aceitação é três runs verdes seguidos; na primeira intermitência a atividade é revertida | aberto |
| R2 | `gradle/actions/setup-gradle` é action fora do namespace `actions/*`. É mantida pela Gradle Inc. e é a recomendação oficial; a alternativa (`gradlew --stop` como último passo do job, deixando o `setup-java` cuidar do cache) fica registrada aqui caso se queira voltar atrás | aberto |
| R3 | O cache do Gradle no Windows é grande e o repositório tem teto de 10 GB. Com `cache-read-only` nos PRs só a `main` escreve, então há no máximo uma entrada Windows por hash de build | aberto |
| R6 | O `Test` do Gradle é uma tarefa cacheável, então com `org.gradle.caching=true` um run cujas entradas não mudaram restaura o resultado em vez de reexecutar a suíte. É a semântica correta do cache — mesma entrada, mesmo resultado — e o `build/test-results` volta junto, então o resumo continua trazendo os números reais. Fica registrado porque, lido no relógio do check, um job de 20 s parece suíte pulada | aberto |
| R5 | O `jpackage`/instalador deixa `build/compose/binaries/main/app/Usage Monitor/Usage Monitor.exe` com atributo **ReadOnly**, e o `clean` do Gradle não o apaga. Só afeta build local — no CI o workspace nasce vazio | aberto |
| R4 | A DSL do Kover mudou entre a 0.7 e a 0.9; os nomes de tarefa usados na A5 precisam ser confirmados com `gradlew.bat tasks --group verification` antes de irem para o workflow | aberto |
