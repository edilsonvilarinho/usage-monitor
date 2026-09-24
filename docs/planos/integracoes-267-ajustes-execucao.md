# Ajustes da issue #267 — Gemini, Cursor e Antigravity

Plano de execução das correções sobre o commit `2d4f23a` (branch
`issue/267-gemini-cursor-antigravity`), que entregou as três integrações num commit só. No teste
real o card do Antigravity mostrava a cada coleta *"did not show the official /usage panel before
timeout (terminal bytes captured=888…)"*, e o Gemini aparecia como falha por "sem histórico local".
A revisão da branch achou, além disso, contagem errada no Gemini, formatos recusados no Cursor e o
Antigravity fora de histórico, alertas e HUD.

**Estratégia de commits:** correções atômicas **por cima** de `2d4f23a`, sem reescrever o histórico
já publicado. A issue pedia cinco commits, um por integração; o desvio fica registrado aqui e na
issue.

## Antigravity: causa raiz, medida e não deduzida

### O veto a `--print` partia de uma premissa errada

A issue vetou `agy -p` porque "esse modo envia um prompt e consome uso". Para comandos de barra
respondidos pelo próprio CLI, isso não vale. A página oficial do modo headless
(`antigravity.google/docs/cli/headless`) diz:

> Slash commands that the CLI can respond to directly (such as `/model` and `/usage`) produce a
> text report rather than a standard event stream.

e, no erro que devolve quando alguém tenta usá-los num stream:

> /model is answered by the CLI itself and is unavailable with --input-format stream-json; run it
> as its own --print /model invocation

O `agy --help` da 1.2.9 traz `--disable-slash-commands` ("Disable slash command and skill expansion
in print mode"), ou seja, a expansão em print mode é o comportamento padrão.

### Medição (Windows 11, agy 1.2.9, PowerShell, pipe comum, sem PTY)

| Comando | Resultado |
|---|---|
| `agy --print-timeout 30s --print /usage` | exit 0, ~5,7 s. TSV `grupo\tjanela Remaining\tN%\tRFC3339`, **sem** linha `Quota:` |
| `agy --print-timeout 30s --output-format json --print /usage` | exit 0. `status: SUCCESS`, `num_turns: 0`, `usage.total_tokens: 0`, `command.name: "usage"`, `command.data.groups[].buckets[{id, name, window, remaining_fraction, reset_time}]` |
| o mesmo com `--sandbox` e cwd vazio | exit 0, 3,8 s, saída idêntica |
| três execuções seguidas | `remaining_fraction` do Gemini idêntica (0,9592728…): a leitura não consome cota |

A execução confirma a documentação: com `/usage` o CLI não abre turno de modelo.

### Por que o caminho da branch nunca funcionou

- **winpty, não ConPTY.** No pty4j 0.13.13, `PtyProcessBuilder.myUseWinConPty` é `false` por
  default, e o runner não chamava `setUseWinConPty(true)` nem `setInitialColumns/Rows`. O Codenotch
  usa ConPTY 160×60.
- **Entrada às cegas.** `/usage⏎` ia depois de 900 ms fixos. Os 888 bytes capturados são o desenho
  inicial da TUI; o painel nunca chegava a abrir. O buffer guardava a **cabeça** de 64 KB, e não a
  cauda.
- **Formato adivinhado.** O parser exigia cabeçalhos ("model quotas", "quota & credits") que a
  saída real não tem, e só entendia "weekly limit remaining": a janela de 5h seria descartada.
- **Canal lateral.** As cotas iam para `ReportedModelQuota`, com `quotas = emptyList()`, e ficavam
  fora de histórico, limiar, anomalia, risco, bandeja e HUD. A fonte estava classificada como
  `OBSERVED_ACTIVITY`, mas não é atividade observada: é cota com janela e reset.

O parser do Codenotch (`agy_cli.rs`) também não serviria como está: exige a linha `Quota:`, que a
saída por pipe desta versão não imprime. O envelope JSON evita essa dependência de texto.

### Armadilhas medidas

- **MSYS reescreve o argumento.** Pelo Git Bash, `/usage` vira `C:/Program Files/Git/usage`, e o
  CLI recebe um **prompt de modelo** (o agente tentou rodar uma ferramenta). O argumento só pode
  chegar por `ProcessBuilder` com lista de argumentos, nunca por shell nem por shim `.cmd`/`.bat`.
- **Reset que anda com o relógio.** No grupo sem uso (`3p-weekly`, fração 1), `reset_time` foi
  00:38:35, 00:38:49, 00:38:53 e 00:45:04 em chamadas sucessivas: é "agora + 7 dias". Tomado como
  reset real, cada coleta pareceria um período novo para o histórico e para a dedup dos alertas
  (tolerância de 5 min). Com a fração em 1 o reset é tratado como desconhecido.

### Salvaguardas do caminho novo

- **Portão de versão:** só chama `/usage` com `agy --version` ≥ 1.2.9, a versão medida.
- **Disjuntor:** o envelope precisa trazer `command.name == "usage"`, `num_turns == 0` e
  `usage.total_tokens == 0`. Se não trouxer, o CLI tratou o comando como prompt; a fonte trava em
  memória até reiniciar o app, para não repetir o consumo a cada coleta.
- **TTL de 5 min** para o wake-up de reset de outras fontes não disparar o CLI. O refresh manual do
  próprio card ignora o TTL.

## Achados da revisão

| # | Sev. | Fonte | Achado |
|---|---|---|---|
| A1 | Crítica | Antigravity | Coleta real nunca funciona (seção acima) |
| A2 | Alta | Antigravity | Canal lateral `ReportedModelQuota`, fora de histórico, alertas e HUD |
| A3 | Alta | Antigravity | Processo disparado em todo poll, refresh e wake-up de reset, sem TTL; pior caso de ~18,8 s contra o `perSourceTimeout` de 20 s |
| G1 | Crítica | Gemini | `$set` sem `messages` limpava a contagem; o CLI grava `$set lastUpdated` depois de cada mensagem |
| G2 | Alta | Gemini | `$rewindTo` removia turnos já cobrados |
| G3 | Alta | Gemini | `.gemini/tmp` ausente virava banner de erro, com o texto falando em "este perfil" |
| G4 | Média | Gemini | Gravação sem tokens apagava a que tinha tokens; linha falsa "sessions" com `REQUESTS`; reparse completo a cada poll |
| C1 | Alta | Cursor | Resposta sem `individualUsage.plan` (enterprise/team) falhava sempre |
| C2 | Alta | Cursor | Percentual acima de 100 derrubava o card |
| C3 | Alta | Cursor | Sem `billingCycleEnd`, `periodEndAt = capturedAt` rearmava alerta e "reset" a cada poll |
| C4 | Média | Cursor | Falha de rede engolida; ausência e logout viravam toast a cada poll; arredondamento em vez de truncamento; total misto gerando alerta duplicado; token no `toString` |
| W1 | Média | App | Cache preservado sem `SOURCE_UNSTABLE`; literal de conjunto repetido |
| D1 | Média | Docs | CLAUDE.md sem nota; justificativa dos acentos apagada do design system; protótipo obrigatório não atualizado |

## Pontos de situação

| # | Atividade | Comando | Resultado |
|---|---|---|---|
| A01 | Plano de ajustes e medições do CLI | `agy --sandbox --print-timeout 30s --output-format json --print /usage` (PowerShell) | exit 0, `num_turns: 0`, `total_tokens: 0`, 2 buckets semanais |
| A02 | Antigravity por `agy --print /usage` JSON: runner sem PTY, portão de versão, disjuntor, TTL, cotas normalizadas, pty4j removido | `gradlew.bat desktopTest --tests "com.usagemonitor.data.*Antigravity*"` + teste temporário contra o agy 1.2.9 real (não versionado) | Suíte verde. Leitura real em 4,4 s: `Antigravity Gemini 7d` 5% com reset, `Antigravity Claude/GPT 7d` 0% sem reset; duas chamadas seguidas com fração idêntica (0,945244…) |
| A03 | Gemini: `$set` e `$rewindTo` sem apagar chamadas cobradas, gravação com tokens vence, raiz ausente como card vazio, pré-filtro por mtime e cache, sem a linha falsa de sessões | `gradlew.bat desktopTest --tests "com.usagemonitor.data.*Gemini*" --tests "com.usagemonitor.presentation.UiStateTest" --tests "com.usagemonitor.presentation.ui.DashboardScreenWarningsTest" --tests "com.usagemonitor.ui.ComponentTest"` | BUILD SUCCESSFUL |
