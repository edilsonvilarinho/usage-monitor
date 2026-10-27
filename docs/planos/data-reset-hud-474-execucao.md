# Data no reinício das cotas semanal e mensal do balão da HUD (#474)

## Objetivo

O balão da HUD imprime o reinício das cotas semanal e mensal só com dia da semana e hora
("Resets Tue 0:59", "Resets Thu 15:47"). Na mensal o dia da semana não identifica o dia: "Thu" pode ser
qualquer quinta das próximas quatro. A issue pede a data nessas duas janelas.

## Fatos confirmados (código da `main` em `c9cb1a55`)

- O texto vem de `resetShortLabel` (`ApiUsageCardFormatting.kt`): `INTERVAL` → hora; `WEEKLY`, `MONTHLY` e
  `REPORTED` → `"$dia $hora"`. A data já é calculada por `formatBrtDateTimeParts` e descartada (`_`).
- Único consumidor: `HudModel.kt` (`HudQuota.resetText`), desenhado em `HudBalloon.kt` por `hudResetCaption`
  ("Reinicia …"/"Resets …"), `labelSmall`, `maxLines = 1`, sem reticências. O título ao lado é `weight(1f)` com
  reticências: o que crescer na legenda sai do título.
- A linha do card (`resetLabel`) já imprime `"$dia $data $hora BRT"` para `WEEKLY`, `MONTHLY` e `REPORTED`.
- Título da cota no balão (`hudQuotaTitle`): o do período sem o prefixo de grupo — "Semanal"/"Weekly",
  "Mensal"/"Monthly", "Uso atual"/"Current usage" (`REPORTED`). Créditos: "mensal", sem data; saldo: nada.

## Decisões

- **`WEEKLY` e `MONTHLY` passam a `"$data $hora"`** ("27/10 21h00", "10/27 21:00"): a data **no lugar** do dia
  da semana, partes de `formatBrtDateTimeParts`, nenhum formato novo. A data identifica o dia; o dia da semana
  sozinho não identificava, e junto da data não cabe (medição abaixo).
- **Recusado: dia, data e hora juntos** ("Reinicia Sex 30/10 23h59"). Medido no `HudNotchTextFitTest`: a legenda
  desenha 161dp em PT e 148dp em EN; dentro da caixa de grupo (224dp de coluna), com o glifo do anel e os dois
  vãos de 8dp, sobram 33dp para o título, e "Mensal" mede 43dp, "Semanal" 51dp — título cortado em todas as
  escalas de 100% a 200%. Fora do grupo "Semanal" também estoura.
- **Recusado: legenda em duas linhas.** A altura da linha (`HUD_BALLOON_QUOTA_TITLE`) entra na geometria fixa do
  balão (`hudNotchSizes`); mudar a altura por cota abre a regra da janela de tamanho fixo.
- **`REPORTED` não muda.** Fora do pedido, e o título "Current usage" (13 caracteres) é o mais largo.
- **Teste de encaixe novo** em `HudNotchTextFitTest`: título e legenda saem dos produtores reais (`hudQuotaTitle`,
  `resetShortLabel`, `hudResetCaption`), PT/EN, semanal e mensal, hora 23h59, dentro da caixa de grupo e com o
  glifo do anel (`RING_LEGEND_SIZE` vira `internal` para o teste), em todas as escalas.

## Atividades

| ID | Atividade | Commit esperado |
|---|---|---|
| A01 | Plano de execução e worktree própria | `docs(hud): plan the reset date in the balloon (#474)` |
| A02 | `resetShortLabel` com data no lugar do dia em `WEEKLY`/`MONTHLY`; teste unitário e de encaixe; protótipo, design system, `hud-notch.md` e fixtures | `fix(hud): show the reset date for weekly and monthly quotas` |

## Pontos de situação

| Ativ. | Data | Commit | Estado | Evidência (comando e resultado) |
|---|---|---|---|---|
| A01 | 2026-10-27 | `c2f08846` | concluída | `git worktree add -b fix/hud-reset-date-474 ../usage-monitor-474 origin/main`. Leitura de `resetShortLabel`, `HudModel.kt`, `HudBalloon.kt`. Sem código. |
| A02 | 2026-10-27 | `9fa6a8ad` | concluída: PR #475, run `40553425679` verde (`desktop-windows`, `desktop-linux`, `pricing-parity`, `test-report`; `ci-ok` verde), 0 incidências | 1ª rodada com dia+data+hora: `gradlew.bat desktopTest --tests "com.usagemonitor.ui.HudNotchTextFitTest"` falhou em todas as escalas ("Mensal" 43dp > 33dp de sobra) — formato trocado para data no lugar do dia. 2ª rodada: `desktopTest --tests "*ApiUsageCardFormattingTest" --tests "*HudNotchTextFitTest"` exit 0. `gradlew.bat allTests`: exit 0, 3296 testes, 0 falhas. `build-test-report.mjs`: 17 regras de arquitetura ok, 3296 `@Test` declarados = executados; servidor não medido (sem mudança em `server/`). Windows 11; sem mudança em host de janela. |

## Riscos

- **R1 — título cortado.** A legenda ganha 2 caracteres ("Ter" → "27/10"); o título é quem cede. Coberto pelo
  teste de encaixe.
- **R2 — sistema de janelas.** Nenhum host de janela muda; só texto dentro do balão.
- **R3 — perda do dia da semana.** Quem lia "Ter" passa a ler "27/10". A linha do card continua com os dois.
