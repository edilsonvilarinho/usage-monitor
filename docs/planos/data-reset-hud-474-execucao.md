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

- **`WEEKLY` e `MONTHLY` passam a `"$dia $data $hora"`** ("Ter 27/10 21h00", "Tue 10/27 21:00"), as mesmas
  partes e a mesma ordem da linha do card: nenhum formato novo de data.
- **`REPORTED` não muda.** Fora do pedido da issue, e é a única cota cujo título longo ("Current usage", 13
  caracteres) não cabe ao lado da legenda com data a 240dp de coluna: estimativa pelo avanço de 0,6em da Plex Mono
  dá ~256dp com o glifo do anel. A medida real entra no teste de encaixe.
- **Teste de encaixe novo** em `HudNotchTextFitTest`: título de cada período (PT/EN, `labelMedium` SemiBold) +
  legenda do pior caso de hora (`23h59`/`23:59`) + glifo do anel + vãos, em todas as escalas, cabem na coluna do
  balão sem cortar o título.

## Atividades

| ID | Atividade | Commit esperado |
|---|---|---|
| A01 | Plano de execução e worktree própria | `docs(hud): plan the reset date in the balloon (#474)` |
| A02 | `resetShortLabel` com data em `WEEKLY`/`MONTHLY`; teste unitário e de encaixe; protótipo, design system e `hud-notch.md` | `fix(hud): show the reset date for weekly and monthly quotas` |

## Pontos de situação

| Ativ. | Data | Commit | Estado | Evidência (comando e resultado) |
|---|---|---|---|---|
| A01 | 2026-10-27 | este commit | concluída | `git worktree add -b fix/hud-reset-date-474 ../usage-monitor-474 origin/main`. Leitura de `resetShortLabel`, `HudModel.kt`, `HudBalloon.kt`. Sem código. |

## Riscos

- **R1 — título cortado.** A legenda ganha 6 caracteres; o título é quem cede. Coberto pelo teste de encaixe.
- **R2 — sistema de janelas.** Nenhum host de janela muda; só texto dentro do balão.
