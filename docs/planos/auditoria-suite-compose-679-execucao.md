# Auditoria da suíte Compose por mutação — issue #679

Procedência: Claude Opus 5.5 (orquestração) com agentes de implementação, Claude Code CLI no Windows 11, 2026-11-25,
branch `test/issue-679-compose-mutation` (worktree `.claude/worktrees/compose-audit-679`, base `origin/main` `4f77314d`),
commits com a identidade `claude <claude@anthropic.com>`. Segunda etapa da
[#676](auditoria-testes-tela-676-execucao.md), parte da #644.

## Objetivo

Saber o que a suíte Compose prova nos arquivos de maior risco, pela mesma régua da #676: quebrar o código de produção de
propósito e ver se algum teste reprova. Corrigir em TDD o que sobreviver e dizer o que segue sem prova. Sem execução paga.

## Amostra (escolhida por risco, antes de qualquer medida)

| Grupo | Arquivo | Por que entra |
|---|---|---|
| HUD | `HudModel`, `HudNotchGeometry`, `HudNotch`, `HudBalloon`, `HudRingStrip` | único modo de visualização |
| Modais | `AppDialog`, `ModalWindowsDecisions`, `HudWindowDecisions` | diálogo, título da janela e faixa de atualização |
| Alertas | `AlertSettingsSection`, `UsageAlertMessages`, `DashboardScreenWarnings` (linha 389 em diante) | o que o usuário liga e lê |
| Formatação e card | `ApiUsageCardFormatting`, `HistoryScreenFormatting`, `ApiUsageCardHeader`, `ApiUsageCardQuotaRows` | número, data, moeda e risco |

Fora, de propósito: `AppDialogWindow` e `ModalWindowsHost` (cola fora do Kover, já declarada sem prova na #676),
`DashboardScreenWarnings` antes da linha 389 (`warningFor` só é chamado pelo `DashboardScreen`, que só teste compõe).

## Régua

- Cada mutante é uma troca: operador, constante, condição, texto, argumento de evento, elemento de `Modifier`, chamada.
- O arquivo mutado é recompilado sozinho contra as classes do build e posto na frente do classpath. `const val` e `inline`
  recompilam também quem os cita, senão o valor antigo continuaria embutido.
- 1ª passada: as classes de teste que citam o arquivo. Só o sobrevivente vai à suíte inteira, e só o que vive às duas é
  "sobrevivente".
- **Controles do mutador:** mutante identidade (código igual) tem de sobreviver, mesmo com a máquina carregada; defeito
  óbvio tem de morrer. Morte por teste fora da 1ª passada é confirmada numa segunda execução isolada.
- Compilação que falha sai da conta; tempo esgotado é contado à parte.
- O teste novo entra vermelho na asserção **com o mutante aplicado** e verde com o código bom. O comportamento já existe:
  a prova é a mutação, não um vermelho contra a `main`. Defeito real achado segue o TDD normal.

## Atividades

- **A01** plano. **A02** mutador Kotlin, controles e custo. **A03** medida. **A04** triagem. **A05** testes, um commit por
  arquivo. **A06** relatório e decisão sobre o resto da suíte.

## Ponto de situação

| Atividade | Comando | Resultado |
|---|---|---|
| A01 | leitura de `origin/main` `4f77314d`; `git grep` dos chamadores | amostra e régua acima; `warningFor`, `warningActionFor` e `AppUpdateBanner` só têm chamador no `DashboardScreen` |
