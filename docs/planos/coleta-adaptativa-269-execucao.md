# Coleta adaptativa por alvo (#269) — execução

| | |
|---|---|
| **Modelo** | Claude Opus 5.5 — `claude-opus-5-5` |
| **Nível de esforço** | não exposto ao agente nesta sessão |
| **Ferramenta** | Claude Code (CLI) |
| **Data** | 2026-09-27 |
| **Issue** | [#269](https://github.com/edilsonvilarinho/usage-monitor/issues/269) |
| **Branch** | `feat/269-adaptive-polling`, criada de `main` (`adf1884`) |
| **PR alvo** | `main` |

## Objetivo

Trocar o poll global fixo de 10 min (`DashboardViewModelConfig.pollInterval = 600.seconds`) por coleta
adaptativa **por alvo**, no modelo do Codenotch:

- 60 s com sessão CLI ativa, 5 min sem nenhuma;
- coleta extra quando um reset vence ou o PC volta do sleep;
- backoff persistido no 429 (`min(15min, max(60s·2^min(n,4), Retry-After))`);
- a última leitura boa continua na tela quando a coleta falha (até 7 dias).

O desenho completo, as duas referências (Codenotch e ai-usagebar) e a justificativa de cada decisão estão na
descrição da issue. Este documento registra o que diverge dela e o que foi executado.

**Acréscimo pedido pelo usuário:** com cadência de 60 s e prazos por alvo, a contagem `05:42` + relógio na faixa do
notch da HUD (#185/#293) vira um número que reinicia a cada minuto. Ela **sai da faixa** e continua só no balão da
engrenagem, que já a mostrava. O rodapé do modo padrão não muda.

## Divergências entre a issue e o código atual

Verificadas em `adf1884`:

| Ponto da issue | Estado real |
|---|---|
| Linhas citadas de `DashboardViewModel.kt` (L419-449, L619-650, L685-713, L774-837) | Defasadas pelo refactor #298/#302–#309. Reais: `startCountdown` L309, `performFetch` L424, `refresh(*)` L530–573, `handleTargetFailure` L625, `scheduleNextRefresh` L784 |
| Fiação em `Main.kt` | O VM é montado em `AppViewModels.kt`; `persistedNextRefreshAt` é lido em `AppGraph.kt` |
| `canPreserveCodexCache` em `performFetch` | Mora em `statsRetainedAfterFailure`, `DashboardViewModelSupport.kt` |
| Espaço no VM | `DashboardViewModel.kt` tem **790** linhas e o teto do `ArchitectureRulesTest` é 800: o agendamento vai para arquivo próprio e o VM só delega |

## Atividades

| # | Atividade |
|---|---|
| C0 | Este plano |
| C1 | Fase 1 — 429 tipado com `Retry-After`, backoff por alvo persistido, 429 do endpoint de token, última leitura para toda fonte com `fetchedAt` e teto de 7 dias |
| C2 | Fase 2 — agendamento por alvo: 60 s ativo / 5 min ocioso, reset só dos alvos vencidos, volta do sleep, contas Anthropic espaçadas, `refresh(target)` isolado |
| C3 | Contagem fora da faixa do notch, só no balão da engrenagem |
| C4 | Fase 3 — publicação incremental, timeouts separados da Anthropic, Antigravity interrompível |

## Pontos de situação

| # | Atividade | Comando | Resultado |
|---|---|---|---|
| C0 | Plano no repositório | `git checkout -b feat/269-adaptive-polling` | branch criada de `adf1884`; só documentação |
