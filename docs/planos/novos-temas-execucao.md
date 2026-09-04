# Novos temas (5 claros, 5 escuros)

Issue: [#227](https://github.com/edilsonvilarinho/usage-monitor/issues/227)

## Contexto

App tinha 16 presets em `AppThemePreset` (8 escuros, 8 claros), fonte única
de verdade das superfícies + acento estrutural de cada tema
(`src/commonMain/kotlin/com/usagemonitor/presentation/ui/theme/AppThemePresets.kt`).
Pedido: +5 escuros e +5 claros, total 26 (13/13).

`ThemePresetPicker` (`SettingsDialogContent.kt`) já renderiza `AppThemePreset.dark`
e `AppThemePreset.light` dinamicamente via `FlowRow` — nenhuma mudança de UI
necessária, só dados no enum. `AppThemePresetTest` valida contraste (WCAG AA)
de todo preset automaticamente, então os novos entram sob a mesma régua sem
teste novo.

## Presets novos

Hues escolhidos para não duplicar os já existentes (4 dos 8 escuros atuais já
são variações de azul).

**Escuros (5 novos):**
| Preset | Label PT/EN | Direção |
|---|---|---|
| `AMBAR_DARK` | Âmbar / Amber | fundo marrom escuro neutro, acento dourado (~45°) |
| `RUBI_DARK` | Rubi / Ruby | fundo bordô escuro, acento vermelho (~355°) |
| `JADE_DARK` | Jade / Jade | fundo verde-azulado escuro, acento verde-jade (~160°) |
| `ROSA_DARK` | Rosa / Rose | fundo neutro escuro, acento rosa-magenta (~320°) |
| `OLIVA_DARK` | Oliva / Olive | fundo verde-oliva escuro, acento amarelo-esverdeado (~75°) |

**Claros (5 novos):**
| Preset | Label PT/EN | Direção |
|---|---|---|
| `AMBAR_LIGHT` | Âmbar / Amber | fundo bege claro, acento dourado escurecido p/ contraste |
| `RUBI_LIGHT` | Rubi / Ruby | fundo rosado claro, acento vermelho escurecido |
| `JADE_LIGHT` | Jade / Jade | fundo verde-água claro, acento jade escurecido |
| `ROSA_LIGHT` | Rosa / Rose | fundo rosa claro, acento magenta escurecido |
| `OLIVA_LIGHT` | Oliva / Olive | fundo bege-esverdeado claro, acento oliva escurecido |

Critério de aceite por preset (o que `AppThemePresetTest` já cobra):
- `foreground` vs `surface` ≥ 4,5:1
- `muted` vs `surface` ≥ 3:1
- `primary` vs `surface` ≥ 3:1

Não mexe em `AppAccents` (paleta semântica/fontes) — ortogonal a presets de
superfície e já congelado.

## Arquivos alterados

- `src/commonMain/kotlin/com/usagemonitor/presentation/ui/theme/AppThemePresets.kt`
- `src/commonTest/kotlin/com/usagemonitor/presentation/AppThemePresetTest.kt`
- `src/desktopTest/kotlin/com/usagemonitor/ui/ThemePresetPickerTest.kt`

## Ponto de situação

| # | Atividade | Status | Commit | Resultado |
|---|---|---|---|---|
| 1 | Plano + issue | Concluída | (este commit) | doc criado, issue aberta |
| 2 | 5 temas escuros | Concluída | (este commit) | `AppThemePresetTest`/`AppAccentsContrastTest` verdes (21 entries, 13 dark, 8 light) |
| 3 | 5 temas claros | Concluída | (este commit) | `allTests` verde (26 entries, 13 dark, 13 light). Achado no caminho: fix de teste `ComponentTest > emits the cards only mode change` faltando `performScrollTo()` (a grade de temas ficou mais alta) e copy desatualizada "oito paletas" na aba Geral |
| 4 | Fechamento | Pendente | — | — |
