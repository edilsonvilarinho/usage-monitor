# Migração de toolchain — Kotlin 2.4, Compose MP 1.12, kotlinx-datetime 0.8, Gradle 9 (#351) — execução

| | |
|---|---|
| **Modelo** | Claude Opus 5.5 — `claude-opus-5-5` |
| **Nível de esforço** | não exposto ao agente nesta sessão |
| **Ferramenta** | Claude Code (desktop) |
| **Data** | 2026-09-28 |
| **Issue** | [#351](https://github.com/edilsonvilarinho/usage-monitor/issues/351) |
| **Branch** | `chore/toolchain-migration-351`, criada de `main` (`22f37f6`) |
| **Substitui** | PRs do Dependabot #350 (grupo `gradle-minor-patch`, que substituiu o #333) e #337 (Gradle wrapper) |

## Contexto

O Dependabot abriu o #350 com Kotlin 2.1.0 → 2.4.20, Compose MP 1.7.1 → 1.12.1, Ktor 3.0.3 →
3.6.0, kotlinx-serialization 1.7.3 → 1.11.0, kotlinx-datetime 0.6.1 → 0.8.0, kotlinx-coroutines
1.9.0 → 1.11.0 e commons-compress 1.27.1 → 1.28.0, num commit só. O CI quebrou com 53 erros de
compilação, quase todos `Unresolved reference 'System'`: a partir da 0.7 o `kotlinx-datetime` não
tem mais `Clock` nem `Instant`, que foram para `kotlin.time`. São 243 arquivos com o import antigo
(226 de `Instant`, 66 de `Clock`).

O #337 (Gradle 8.6 → 9.8.0) passou a suíte, mas o CI não empacota, e com Kotlin 2.4 o Gradle 8.6
fica deprecado — ele deixa de ser opcional.

## Regras

- **Uma atividade, um commit**, cada um compilando e passando `gradlew.bat allTests` sozinho. Se
  duas atividades só fecharem juntas, o commit junta as duas e o motivo fica na linha dela.
- A tabela abaixo é atualizada no mesmo commit da atividade, com o comando e o resultado medido.

## Atividades

- **A0** — este plano e a linha de base na `main`.
- **A1** — Kotlin 2.1.0 → 2.4.20 (plugins `multiplatform`, `serialization` e `compose-compiler`),
  Compose e kotlinx-* intactos.
- **A2** — kotlinx-coroutines 1.9.0 → 1.11.0.
- **A3** — kotlinx-serialization 1.7.3 → 1.11.0.
- **A4** — Ktor 3.0.3 → 3.6.0.
- **A5** — kotlinx-datetime 0.6.1 → 0.8.0 com `Clock`/`Instant` migrados para `kotlin.time` (um
  commit: a versão e o import não compilam separados).
- **A6** — acessores `compose.*` do plugin → coordenadas explícitas no version catalog, ainda na
  1.7.1, sem mudar versão; Material3 e ícones com chave de versão própria.
- **A7** — Compose Multiplatform 1.7.1 → 1.12.1; `material-icons-extended` fixado em 1.7.3.
- **A8** — mídias de ajuda regeneradas, se o Compose novo mudar o desenho.
- **A9** — Gradle wrapper 8.6 → 9.8.0.
- **A10** — commons-compress 1.27.1 → 1.28.0.
- **A11** — empacotamento nas três plataformas (sem commit de código).
- **A12** — validação manual em Windows e Linux (X11), documentação e fechamento.

## Pontos de situação

| Atividade | Comando | Resultado |
|---|---|---|
| A0 — plano e linha de base | `gradlew.bat allTests --rerun-tasks` na `main` (`22f37f6`), Windows 11, JDK 17, heap de 3 GB do `gradle.properties` | `BUILD SUCCESSFUL in 5m 41s`, 14 tarefas executadas; 2354 testes, 0 falhas, 0 ignorados |
| A1 — Kotlin 2.4.20 | `gradlew.bat compileKotlinDesktop compileTestKotlinDesktop`; `gradlew.bat allTests` | Compila com o Compose 1.7.1 (a saída de emergência não foi usada), 3m 2s. O KGP avisa `Deprecated Gradle Version` (8.6; mínimo vira 8.14.4 no Kotlin 2.5) — resolvido na A9. 76 avisos de compilação (50 de opt-in `ExperimentalCoroutinesApi` nos testes), nenhum erro; o heap de 3 GB bastou. `BUILD SUCCESSFUL in 4m 43s`, 2354 testes, 0 falhas |
| A2 — kotlinx-coroutines 1.11.0 | `gradlew.bat allTests` | `BUILD SUCCESSFUL in 6m 4s`, 2354 testes, 0 falhas; nenhuma mudança de código (o Ktor 3.0.3 aceita a 1.11) |
| A3 — kotlinx-serialization 1.11.0 | `gradlew.bat allTests` | `BUILD SUCCESSFUL in 5m 48s`, 2354 testes, 0 falhas, incluindo os de mapper em `commonTest/data`; nenhum aviso de serialização e nenhuma mudança de código |
| A4 — Ktor 3.6.0 | `gradlew.bat allTests`; `javap` em `ktor-client-core-jvm-3.6.0.jar` | `BUILD SUCCESSFUL in 5m 59s`, 2354 testes, 0 falhas, sem mudança de código. Os testes de `NetworkFailure` montam as exceções à mão, então a hierarquia foi conferida no jar: `ConnectTimeoutException extends java.net.ConnectException` e `HttpRequestTimeoutException extends java.io.IOException`, como na 3.0.3; o socket timeout do Ktor na JVM continua sendo `java.net.SocketTimeoutException` (não há classe própria no jar). A classificação por tipo segue valendo |
| A5 — kotlinx-datetime 0.8.0 + `Clock`/`Instant` em `kotlin.time` | `sed` sobre os 243 arquivos com `kotlinx.datetime.Clock`/`Instant` (imports e 9 nomes qualificados); `gradlew.bat compileKotlinDesktop compileTestKotlinDesktop`; `gradlew.bat allTests` | 244 arquivos, 293 linhas trocadas uma a uma, nenhuma outra edição de código. Com Kotlin 2.4 os tipos de `kotlin.time` não pedem `@OptIn(ExperimentalTime::class)`, então o item do "fora do escopo" da issue deixa de existir. `DISTANT_FUTURE`, `parse`, `fromEpochMilliseconds`, `toLocalDateTime` e aritmética com `Duration` compilam sem ajuste; os erros do `DashboardScreen.kt:206/216` somem. Compilação 1m 3s, 0 erros; `BUILD SUCCESSFUL in 4m 42s`, 2354 testes, 0 falhas (inclui `ArchitectureRulesTest`). `AGENTS.md` atualizado: o domain usa `kotlin.time` para relógio e instante |
| A6 — acessores `compose.*` → version catalog | `gradlew.bat dependencies --configuration desktopRuntimeClasspath` e `desktopTestRuntimeClasspath` antes/depois + `diff`; `gradlew.bat allTests` | `runtime`, `foundation`, `material3`, `material-icons-extended`, `components-resources` e `ui-test` com coordenada explícita; `compose.desktop.currentOs` segue no plugin (não está deprecado). Material3 e ícones ganharam chave própria (`compose-material3`, `compose-material-icons`), ainda em 1.7.1 — a A7 é que move os ícones para 1.7.3. Os dois classpaths (431 e 481 linhas) saíram **idênticos**; `BUILD SUCCESSFUL in 5m 43s`, 2354 testes, 0 falhas |
| A7 — Compose Multiplatform 1.12.1 | `ComposePlugin$Dependencies` do `compose-gradle-plugin-1.12.1.jar` (qual Material3 o plugin usa); `gradlew.bat compileKotlinDesktop compileTestKotlinDesktop`; `gradlew.bat allTests` | O plugin 1.12.1 mapeia `compose.material3` para **1.9.0**, e o catálogo segue: `compose-material3 = "1.9.0"`, `compose-material-icons = "1.7.3"` (último publicado). `checkDesktop{Main,Test}ComposeLibrariesCompatibility` passam; 0 erros de compilação, 1m 39s. Nenhuma primitiva precisou de ajuste: `BUILD SUCCESSFUL in 1m 17s` (compilação já feita), 2354 testes, 0 falhas — incluindo `AppThemeScaleTest` (pixel), `AppAccentsContrastTest` e os testes de componente. Avisos: 103 → 560; os novos são 450 de `runDesktopComposeUiTest` (a v2 usa `StandardTestDispatcher` — muda comportamento, vai para issue própria), 5 de `rememberPlainTooltipPositionProvider` e 2 de `LocalClipboardManager`. Os 27 de `monthNumber`/`dayOfMonth` já vinham da A5 (kotlinx-datetime 0.8) |
| A8 — mídias de ajuda | `gradlew.bat generateHelpMedia` no branch e num worktree da `main` (`22f37f6`); diff quadro a quadro dos 12 GIFs (Pillow, limiar 24); sonda temporária de altura de `Text` por estilo nas duas árvores | **Sem commit de mídia: o Compose 1.12 não muda o desenho.** Alturas de texto idênticas nos 7 estilos medidos (ex.: `labelSmall` 13/27 dp, `bodyMedium` 18/39 dp para 1/2 linhas). Nos quadros estáveis a diferença é de antialiasing (3 a 165 px de 420 000); as diferenças grandes ficam só nos quadros de transição (1–5), onde muda o tempo da animação. À parte: 5 GIFs (`appearance`, `history`, `team`, `updates`, `window-modes`) **já estavam desatualizados na `main`** — por exemplo, a faixa de atualização ainda mostra o link anterior ao botão do #291. Fica para acompanhamento próprio, fora desta migração |
| A9 — Gradle wrapper 9.8.0 | `gradlew.bat wrapper --gradle-version 9.8.0` (duas vezes: a segunda, já no 9.8.0, troca jar e scripts); `git hash-object` contra o #337; `gradlew.bat help --warning-mode all`; `gradlew.bat allTests`; `gradlew.bat allTests -PtestForks=3 -Pcoverage koverXmlReport`; `generateAppVersionSource` com e sem `-PappVersion=v99.1.2` | Jar e `gradlew` idênticos aos do #337; `gradlew.bat` só difere em CRLF (o `autocrlf` normaliza); o `.properties` ganha `retries`/`retryBackOffMs` do 9.8. O Gradle 9.6 deprecou os delegates `val x by getting {}` e `by tasks.registering {}` (removidos no 10): os 4 source sets passaram a `getByName("…") {}` e `generateAppVersionSource` a `tasks.register("…") {}`. Resta 1 deprecação, `Configuration.setVisible`, que vem do Kover 0.9.9 (`PrepareKover.kt:26`), não do nosso script — remoção só no Gradle 11. `BUILD SUCCESSFUL in 3m 54s`, 2354 testes, 0 falhas; com forks + cobertura: `extractSkikoNative` roda antes, `BUILD SUCCESSFUL in 1m 27s`, `report.xml` de 3,3 MB, 2354/0; `AppVersion.kt` sai `99.1.2` com a propriedade e `41.3.1` (última tag) sem ela. O aviso `Deprecated Gradle Version` do KGP sumiu. Comentário do `release-linux.yml` que dizia "já fixa 8.6" corrigido |
