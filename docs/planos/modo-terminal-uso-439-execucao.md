# Modo terminal: ver o uso pelo terminal — issue #439

## Objetivo

Primeira das três issues do modo terminal (#439, #440, #441). Ver o uso sem abrir o app: comandos
que respondem e saem (`status`, `status --json`, `check`, `wait`) e uma linha de uso dentro do
Claude Code e do tmux (`statusline`). Partes 1, 2, 6.1 e 7 do protótipo
[`prototipo-modo-cli.html`](prototipo-modo-cli.html); regras e primitivas em
[`docs/design-system/cli/readme.md`](../design-system/cli/readme.md), que vence o protótipo.

Nesta issue o terminal **só lê**: quem coleta continua sendo o app. Coletar sem o app é a #440.

## Fatos confirmados (antes de qualquer código)

- **Arranque.** `~/.local/bin/usage-monitor --version` (launcher instalado, 41.11.0, Linux): 0,14 s e
  46 MB de RSS nas três execuções (`/usr/bin/time -f "%e s %M KB"`). A JVM empacotada é rápida o
  bastante para a linha de status, que dispensa um leitor separado.
- **A leitura pronta já existe.** `DashboardViewModel.persistDashboardCache` grava
  `~/.usage-monitor/dashboard-cache.json` a cada coleta (`LocalDashboardCacheDataSource`, gravação
  atômica por arquivo temporário e `rename`), com o `fetchedAt` de cada conta. É o mesmo arquivo que
  reidrata a HUD no arranque, então a última leitura em falha (`statsRetainedAfterFailure`) já está
  nele. Nenhum arquivo novo é preciso.
- **App aberto** se detecta pela trava `app.lock` (`SingleInstanceGuard`, `FileLock`): se a trava
  está tomada, há coletor vivo.
- **Limiares.** `UsageAlertSettings.quotaPercentsFor(storageKey)` dá o limiar próprio da conta ou o
  geral; padrão `DEFAULT_QUOTA_ALERT_PERCENTS = 75, 90, 100`. `readPersistedAlertSettings(settings)`
  lê das preferências sem interface.
- **Preferências** em `java.util.prefs`, nó `AppDataDirectory.preferencesNodeName` (por raiz de
  dados, #419). O idioma efetivo é da `LanguageSelection` (#418).
- **Conta do Claude Code.** Cada perfil Anthropic guarda `configDirectory`
  (`AnthropicProfileRecord`). O Claude Code passa à statusline, pela entrada, um JSON com
  `session_id` e `transcript_path` (que fica dentro do diretório de configuração), e o processo herda
  `CLAUDE_CONFIG_DIR` quando ele existe.
- **Sessão por id.** `CliSessionRepository.getSessionDetail(sessionId)` lê o índice da #381.
- **Parser.** Hoje um argumento posicional (`status`) vira opção desconhecida: aviso e o app abre.
- **JDK 17.** Não há `stdout.encoding` (JDK 19+); no Windows com console, a JVM expõe a página do
  console em `sun.stdout.encoding`.
- **Ordem.** `buildDashboardUiState` ordena os alvos por `source.ordinal` e depois `profileId`, num
  comparador escrito duas vezes no mesmo arquivo. O terminal precisa da mesma ordem: o comparador vira
  um só, nomeado, e os três o usam (A05).

## Decisões

- **D1 · Idioma (dono, 2026-10-21):** toda saída de terminal segue o idioma do app (`LanguageSelection`,
  com `--locale`). Console que não é UTF-8 recebe glifos ASCII e texto sem acento. Revoga a regra
  "inglês/ASCII" do #418 no commit que muda a ajuda (A11); até lá, o `CLAUDE.md` mantém a regra antiga.
- **D2 · Leitura:** o terminal lê o `dashboard-cache.json` pelo mesmo mapper do app. Ele não faz
  requisição, não renova OAuth e não toma a trava.
- **D3 · Estado:** a palavra sai dos limiares da conta, pela regra de zonas do Telegram (AE7).
- **D4 · Camadas:** as primitivas do design system são funções puras em
  `presentation/terminal/` (`commonMain`), sem Compose, com teste em `commonTest`. Ler arquivo,
  preferências e `stdin` fica em `desktopMain`. Um grafo leve próprio do terminal, sem `HttpClient`
  e sem view models: subir o `AppGraph` para imprimir uma linha seria pagar a interface inteira.
- **D5 · Textos** por objeto de rótulos (`TerminalLabels`), nunca `== AppLanguage.PT` inline: a catraca
  `INLINE_LANGUAGE_CHECKS_CEILING` não sobe.

## Atividades

| Item | O que faz |
|---|---|
| A01 | Este plano, a worktree e as correções do design system: limiares reais (75/90/100 por conta, zonas da AE7), idioma decidido (D1), leitura pelo `dashboard-cache.json` (D2). Plano publicado como comentário na #439 |
| A02 | `parseCommandLine` reconhece os subcomandos (`status`, `check`, `wait`, `statusline`) e as opções deles; opção desconhecida fora de subcomando continua só avisando. No Windows, subcomando é ação: o launcher de console imprime e não relança o exe GUI |
| A03 | Perfil do terminal, em função pura: paleta (NO_COLOR, `TERM=dumb`, `COLORTERM`, `COLORFGBG`, saída que não é terminal), glifos (UTF-8 ou ASCII) e largura. Renderização de linhas com estilo para ANSI truecolor, 16 cores, sem cor e para o formato do tmux. Transliteração sem acento |
| A04 | Primitivas do design system: barra de cota, palavra de estado, linha de cota, rodapé de leitura, com rótulos PT/EN em `TerminalLabels` |
| A05 | Leitura do terminal (`desktopMain`): cache, limiares, idioma, perfis e se há coletor vivo, num grafo leve. Contas na ordem do dashboard |
| A06 | `usage-monitor status` (2.2) e o caso "nada coletando" com `--max-age` (6.1) |
| A07 | `status --json` (campos estáveis em inglês, sem segredo, documentados como contrato) e `check --max <n>` (sai com 0, 1, 2 ou 3) |
| A08 | `wait <cota> --below <n>`: relê o cache a cada coleta, sem requisição; sai quando a cota cai abaixo do valor, com `--timeout` |
| A09 | `statusline claude-code` (1.1, 1.2): JSON da entrada, conta pelo diretório de configuração, custo e vazão da sessão pelo índice da #381 (sem a sessão no índice, não mostra custo, porque não medido não vira zero). Tempo medido |
| A10 | `statusline tmux` (1.3): siglas por conta e cor no formato `#[fg=…]` do tmux, que não aceita ANSI na barra |
| A11 | `--help` com os comandos novos (2.1) e D1 aplicado à ajuda, à versão e ao diagnóstico. `CLAUDE.md` e `docs/build-and-release.md` registram a revogação do "inglês/ASCII" |
| A12 | Documentação de uso (`README`, `docs/build-and-release.md`), conferência das telas contra o protótipo nas quatro paletas e PR |

## Riscos

- **R1 · Windows não medido aqui** (#342): a suíte roda no Windows, mas o console real (página de
  código, launcher de console) só se mede lá. O PR diz o risco se não for medido.
- **R2 · Formato do cache:** o `dashboard-cache.json` foi feito para reidratar a interface, e não como
  contrato externo. Como o leitor é o mesmo binário e usa o mesmo mapper, mudar o formato muda os dois
  juntos. O contrato externo é só o `--json` (A07).
- **R3 · Sessão fora do índice:** o índice do Claude é sincronizado pelo app de tempos em tempos; uma
  sessão nova pode não estar lá. A linha de status mostra a conta e as cotas mesmo assim.

## Pontos de situação

| Item | Data | Commit | Resultado | Comando e evidência |
|---|---|---|---|---|
| A01 | 2026-10-21 | este commit | concluída | Worktree `../usage-monitor-439`, branch `feat/issue-439-terminal-usage` sobre `docs/cli-design-system` (PR #442 ainda aberto). Medição do arranque: `/usr/bin/time -f "%e s %M KB" ~/.local/bin/usage-monitor --version`, três vezes: 0,14 s, cerca de 46 MB. Design system corrigido: a palavra de estado sai de `quotaPercentsFor` com as zonas da AE7, o idioma segue o app com fallback ASCII sem acento, e a leitura pronta é o `dashboard-cache.json`. No protótipo, o assistente mostra o padrão `[75,90,100]` e o exemplo digita `70,90`, o que mantém as palavras das outras telas coerentes. Só documentação; nenhum teste rodado |
