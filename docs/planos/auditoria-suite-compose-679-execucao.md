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
- **A07** (pedido do dono em 2026-11-25) issue de encerramento, criada depois do merge, com tudo: o que foi feito, os ganhos
  medidos, os problemas (inclusive os da condução) e o que falta, com a evidência de cada número. E o mesmo em HTML, com
  gráficos: mutação antes e depois por arquivo, testes adicionados, cobertura de linhas antes e depois, o que falta.

## Ponto de situação

| Atividade | Comando | Resultado |
|---|---|---|
| A01 | leitura de `origin/main` `4f77314d`; `git grep` dos chamadores | amostra e régua acima; `warningFor`, `warningActionFor` e `AppUpdateBanner` só têm chamador no `DashboardScreen` |
| A02 | `controls.mjs`; `loadmap.mjs`; `mutate-kt.mjs` em `~/.claude/audit-679/scripts` | **Mutador confiável.** Suíte fora do Gradle = Gradle: 4812 testes, 520 classes, 0 falha, 0 exclusão. Identidade em `HudModel` contra a suíte inteira: 4812 = base. Defeitos óbvios `b1` a `b5` mortos (`abbreviate`, `hudSourceOrigin`, `hudResetCaption`, `AppDialog` sem texto, alerta em inglês). Constante propagada ao arquivo dependente: `77.0f` no bytecode de `HudNotchMeasureKt$hudNotchMeasurePolicy$1`. 4, 6 e 8 processos: os mesmos 7 mortos e 5 vivos, 0 ruído. Dois controles vermelhos por escolha ruim do controle: `b6` (constante que o teste usa dos dois lados, sobrevivente real) e `c` (lia a 1ª classe carregada; a carga do overlay está na linha). Custo: ~2 s de compilação quente, 69 min para a amostra com 12 processos. **Perda:** uma rodada de 30 min inválida (`-Xfriend-paths` com `;`), solta sem ensaio de um mutante |
| A03 | `a03.sh`: 15 arquivos, até 60 mutantes cada (sorteio fixo), 12 processos, 17:40 às 18:49; `ApiUsageCardQuotaRows` à parte (`--tests`, nenhum teste cita o arquivo) | 819 mutantes, 82 não compilam, **402 mortos e 335 vivos de 737: 54,5%**. 43 identidades, todas `survived`. Um `noise` (`TelegramBotServiceTest`, espera de 5 s estourada com a máquina cheia, passou ao repetir com e sem o mutante) refeito: vivo. Por arquivo: `HudWindowDecisions` 100, `HistoryScreenFormatting` 84,2, `HudModel` 78,6, `ModalWindowsDecisions` 77,3, `HudNotchGeometry` 73,3, `UsageAlertMessages` 69,5, `ApiUsageCardFormatting` 67,2, `HudRingStrip` 50,9, `HudNotch` 46,9, `AlertSettingsSection` 39,3, `DashboardScreenWarnings` 37,3, `HudBalloon` 35,8, `AppDialog` 26,8, `ApiUsageCardQuotaRows` 26,5, `ApiUsageCardHeader` 22,2 |
| A04 | `triage.mjs`; `git grep` dos chamadores de cada símbolo | 335 vivos: **128 furos de comportamento**, 102 aparência ou animação (teste de componente não vê), 16 equivalentes, **89 em código que só o `DashboardScreen` de teste compõe**. Só código vivo: 375 de 621 mortos (60,4%); sem os de aparência e equivalentes, 375 de 503 (74,6%, generoso: os mortos incluem aparência). **Achado:** o composable `ApiUsageCard` e o que só ele usa (cabeçalho, barra de ações, linhas de cota), `warningFor` e `AppUpdateBanner` não têm chamador no produto; 53 chamadas deles em `ComponentTest`, e a cobertura de linhas os conta como produto |
| A05-T1 | `node replay.mjs` (o mutante de cada furo compilado contra a produção e rodado contra as classes de teste novas; o código bom passa antes); Gradle `UsageAlertMessagesTest`, `HistoryScreenFormattingTest`, `ApiUsageCardFormattingTest` | 12 testes; base 46/46 verdes; **31 de 31 furos mortos**, ex.: `expected:<[Uma sessão] encheu…>` (L140), `expected:<[Atualizando]> but was:<[Refreshing]>` (L116). Os testes nasceram verdes: a prova é a morte do mutante |
| A05-T2 | `node replay.mjs` (o mutante de cada furo compilado contra a produção e rodado contra as classes de teste novas; o código bom passa antes); Gradle `AppUpdateBannerTest` | 2 testes (todos os estados, estável e beta, PT e EN, conteúdo inteiro); 15/15 verdes; **19 de 19 furos mortos** (tom, título, cabeçalho, detalhe) |
