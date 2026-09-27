# Engrenagem no hover (#317), verificação da #269 (#318) e skill de limpeza de contexto (#319) — execução

| | |
|---|---|
| **Modelo** | Claude Opus 5.5 — `claude-opus-5-5` |
| **Nível de esforço** | não exposto ao agente nesta sessão |
| **Ferramenta** | Claude Code (CLI) |
| **Data** | 2026-09-27 |
| **Issues** | [#317](https://github.com/edilsonvilarinho/usage-monitor/issues/317), [#318](https://github.com/edilsonvilarinho/usage-monitor/issues/318), [#319](https://github.com/edilsonvilarinho/usage-monitor/issues/319) |
| **Branch** | `feat/317-319-gear-hover-token-cleanup`, criada de `main` (`8082afc`) |
| **PR alvo** | `main` |

## Contexto

- **#317** — Na barra HUD o balão da engrenagem só abria por clique, enquanto os anéis abrem o balão
  da conta no hover. Causa: `HudNotch.kt` só mudava `balloonIndex` no `onClick` da engrenagem, com
  alternância; o hover dela (`gearHover`) só alimentava `onHoverChange`.
- **#318** — Pendências da #269 que a suíte não cobre: conferência visual da contagem (60 s / 5 min)
  e observação de 429 da Anthropic a 60 s. Estado medido no início: `breadcrumbs.jsonl` sem nenhuma
  linha `429`, última escrita 11:20, **antes** do merge `8082afc` (11:41) — a instância que gerou a
  trilha não rodava a #269, então não há evidência ainda.
- **#319** — Skill que mede o contexto carregado por sessão, propõe limpeza e só executa o que o
  usuário aprovar. Medido no início: `CLAUDE.md` do repo = 1.252 linhas / 165.640 chars (dominante,
  carregado em toda sessão); memória do projeto = 14 arquivos / ~21k chars; `~/.claude/CLAUDE.md` =
  2,5k chars; quatro `SKILL.md` = ~14k chars.

Decisões do usuário: a skill mora em `.claude/skills` do repo; os alvos são o `CLAUDE.md` do
projeto, a memória, o `CLAUDE.md` global e os `SKILL.md` do repo; a engrenagem abre no hover e o
clique **só abre**, nunca fecha — com hover abrindo, um clique que alterna fecharia o balão que o
próprio ponteiro acabou de abrir.

## Atividades

- **A0** — este plano.
- **A1 (#317)** — hover na engrenagem abre o balão dela, pelo mesmo modelo dos anéis
  (`onRingHovered`); clique idempotente. Fechar continua pelos caminhos que já existem: sair do
  notch ou passar num anel. O reinício do app continua exigindo o clique no botão do balão, então a
  invariante de `HudUpdateIndicator` ("nunca um clique de rotina reiniciando o app") não muda.
  Testes, ajuda PT/EN, `CLAUDE.md`, design system e protótipo no mesmo commit.
- **A2 (#319)** — `.claude/skills/usage-monitor-token-cleanup/` com `SKILL.md` e
  `scripts/measure_context.ps1` (somente leitura): medir, analisar com evidência, apresentar tabela,
  pedir permissão item a item, aplicar só o aprovado, medir de novo.
- **A3 (#318)** — verificação manual no app com o build da A1; resultado comentado na issue.
- **A4 (#318)** — observação de 429 durante um dia de trabalho, pelo usuário. Zero 429 fecha a
  issue; algum 429 sobe `activePollInterval` para `300.seconds` depois de registrar as linhas na
  issue. Fora deste lote até haver dado.

## Pontos de situação

| # | Atividade | Comando | Resultado |
|---|---|---|---|
| A0 | Plano no repositório | `git checkout -b feat/317-319-gear-hover-token-cleanup` | branch criada de `8082afc`; só documentação |
| A1 | #317: `LaunchedEffect(gearHovered, open)` grava `APP_BALLOON` no hover da engrenagem; clique idempotente (sem alternância). Ajuda PT/EN ("passe o ponteiro sobre a engrenagem"), `CLAUDE.md`, `AppHudBar.prompt.md`/`.jsx`, `Hud.jsx` e protótipo atualizados. Demos da Ajuda não encenam a engrenagem (`grep` em `HelpMediaGenerator`/`ScreenshotGenerator`): sem regeneração | `gradlew.bat desktopTest --tests "com.usagemonitor.ui.HudNotchTest"`; o mesmo contra o `HudNotch.kt` de `HEAD`; `gradlew.bat allTests`; `DashboardViewModelRefreshPersistenceTest` isolado com `--rerun-tasks` (3×) | `HudNotchTest` 44/44. Contra o código anterior, 2 falhas — `o ponteiro sobre a engrenagem abre o balao dela` e `...o segundo clique nao fecha` —, então os testes provam a mudança. `allTests`: 2252 testes, 1 falha em `refresh persists the new scheduled time via callback` (`Condition not met within real-time timeout`), teste de ViewModel que não toca arquivo alterado; isolado, 3 de 3 verdes. É a race do C2b da #269 reaparecendo sob carga — não corrigida neste lote |
| A2 | #319: skill `usage-monitor-token-cleanup` (`SKILL.md` + `scripts/measure_context.ps1`, somente leitura, gravado com BOM para rodar no Windows PowerShell 5.1). Validação em modo relatório, **nada aplicado** | `powershell -NoProfile -ExecutionPolicy Bypass -File .claude\skills\usage-monitor-token-cleanup\scripts\measure_context.ps1`; `gh issue view N --json state` para as 16 issues/PRs citadas na memória; `git status --short` | Linha de base (estimativa chars/3,5): `CLAUDE.md` do projeto 166.000 chars ≈ 47,4k tokens por sessão; global ≈ 0,7k; `MEMORY.md` ≈ 0,6k; descriptions das skills ≈ 0,4k; arquivos de memória ≈ 5,2k sob demanda. Maior bloco do `CLAUDE.md`: de `### Design system — precedência` (l. 517) até `## CI e testes`, 53,7k chars ≈ 15,3k tokens — é o `Sistema visual` inteiro, porque HUD, motion e profundidade são parágrafos em negrito e não cabeçalhos; depois `Camada presentation` (32,2k) e `Integração com time` (27,2k). 29 bullets passam de 1.500 chars. Memória: nenhum ponteiro quebrado nem órfão. Candidatas: `project_269_observacao.md` repete o procedimento que já está no corpo da #318, aberta; `project_294_298_state.md` cita só issues fechadas, mas a verificação manual que guarda não está registrada em outro lugar — julgamento do usuário; `project_team_report_token_risk.md` cita a #107 fechada, mas o risco nunca foi confirmado como corrigido — manter, o estado da issue não diz nada sobre ele. `git status` depois da medição: só a pasta nova da skill |
