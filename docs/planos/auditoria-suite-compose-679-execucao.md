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
| A05-T3 | `node replay.mjs` (o mutante de cada furo compilado contra a produção e rodado contra as classes de teste novas; o código bom passa antes); Gradle `AlertSettingsSectionTest` | 40 testes; 53/53 verdes; **28 de 29 furos mortos**: teto mensal salvo ao sair do campo (`expected:<300> but was:<null>`), silêncio e lembrete desligados mandam `null`, avisos dependentes do alerta de cota. Sem teste: a dica `200` (o campo limpa a semântica, só imagem a vê) |
| A05-T4 | `node replay.mjs` (o mutante de cada furo compilado contra a produção e rodado contra as classes de teste novas; o código bom passa antes); Gradle `AppDialogTest`, `ModalWindowsDecisionsTest`, `ComponentTest` | 6 testes; 96/96 verdes; **6 de 9 mortos**: botão desabilitado não aceita clique (`expected:<0> but was:<1>`), largura do cartão 528 dp (`Actual width is 960`), créditos só do perfil pedido. Fora: `onDismissRequest` do `Dialog` e clique fora (no Compose 1.12.2 o Esc fecha pelo evento de voltar, que o harness não gera: o teste do Esc deu 0 com e sem foco e saiu; verificação manual), largura mínima equivalente (`widthIn` com máximo maior) |
| A05-T5/T6 | `node replay.mjs` (o mutante de cada furo compilado contra a produção e rodado contra as classes de teste novas; o código bom passa antes); Gradle `HudModelTest`, `HudNotchTest`, `HudNotchGeometryTest`, `HudBlackHoleCellTest` (274 e 93 testes, 0 falha) | 30 testes; **26 de 40 mortos**: barra de progresso do balão por imagem (`0 pixels diferem`), Esc só fecha o balão do buraco (`a tecla A nao deveria fechar`), anel em 0% (`expected:<0.0> but was:<1.0>`), alturas e vãos literais do notch. Equivalentes: L179, L395, L472 do modelo, L554 do anel. Sem teste: posição da cauda e âncora da engrenagem (L391, L498), alpha e posição do anel (L495, L541), quatro medidas da geometria (L166, L361, L402, L566), unidade do 1º modelo observado (L160); L172 sem decisão |
| A06 | 2º lote do mesmo sorteio (`--offset 60 --max 60`), nunca visto pelos agentes, em 9 arquivos: `a06.sh` com `AUDIT_TAG=antes` na worktree em `3fbaccfd` (sem os testes novos) e `AUDIT_TAG=depois` na branch em `8f00a33d`; comparação por id do mutante | **Generalização: 307 → 352 mortos de 500 válidos (61,4% → 70,4%)**; 45 vivos viraram mortos, 0 regressão, ids 500/500 iguais nas duas rodadas, 27 identidades vivas. Base: 4812 → 4898 testes, 0 falha. Por arquivo, antes → depois: `AlertSettingsSection` 42,1 → 68,4, `DashboardScreenWarnings` 57,9 → 73,7, `ApiUsageCardFormatting` 70,7 → 79,3, `HudModel` 84,5 → 91,4, `HudNotchGeometry` 70,7 → 75,9, `HudNotch` 48,1 → 53,7, `HudBalloon` 34,0 → 40,0, `HistoryScreenFormatting` 83,9 → 87,5, `HudRingStrip` 55,8 → 57,7. No lote visto, 110 de 128 furos (86%); no não visto, 45 de 193 vivos (23%): o teste escrito contra o mutante fecha o mutante, e pouco além do comportamento vizinho. Os 148 vivos não foram triados (aparência, equivalente e código só do `DashboardScreen` entram neles). **Perda:** a 1ª rodada "antes" morreu no desligamento da máquina com `HudNotchGeometry` em 56/60; 8 `error` e 12 `noise` eram a JVM de confirmação encerrada (`exit 3221226091`/`1073807364`), não veredito: zerados e refeitos, 0 ruído |
| Decisão | — | Estender ao resto da suíte **só por risco e com triagem**: a mutação acha furo onde a cobertura diz 96–100% de linhas (os 15 arquivos), mas teste escrito por mutante generaliza pouco (23%). Próximo lote, se o dono mandar: arquivos de decisão pura (`*Decisions`, formatação), onde a morte foi mais barata, e nunca aparência |

## Problemas em aberto e riscos

- Código morto contado como produto: `ApiUsageCard` (cabeçalho, barra de ações, linhas de cota), `warningFor` e `AppUpdateBanner`
  só têm chamador no `DashboardScreen` de teste. A cobertura de linhas os conta; remover ou ligar é decisão do dono.
- Esc do `AppDialog` sem prova automática (no Compose 1.12.2 fecha pelo evento de voltar, que o harness não gera): verificação manual.
- `TelegramBotServiceTest` estourou a espera de 5 s com a máquina cheia na A03: possível intermitência no CI.
- Sem teste, por limite do teste de componente: posição na tela (cauda e âncora do balão, alpha e posição do anel), quatro medidas da
  geometria do notch e a dica `200` do teto mensal. L172 do `HudModel` sem decisão.
- O mutador vive fora do repositório (`~/.claude/audit-679/scripts`); repetir a medida exige trazê-lo para `tools/` antes.
