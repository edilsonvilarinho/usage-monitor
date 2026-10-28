# Issues #477 e #478 — execução em branch única

Branch `fix/issues-477-478`, worktree `../usage-monitor-477-478`. Executor: Claude Opus 5.5.

## Ponto de situação

| # | Commit | Atividade | O que mudou | Verificação (comando → resultado) |
|---|---|---|---|---|
| 1 | (este) | A01 | Plano com diagnóstico, decisões do usuário e atividades | `git diff --cached --stat` → só este arquivo |

## Diagnóstico (evidência)

### #477 — textos sobrepostos e escala

- **Sobreposição** (capturas 1–3 da issue, janela "Análise"): `PaceDiagonalChart.kt` desenha a legenda
  "acima da linha: esgota antes" fixa no canto superior esquerdo e o rótulo do ponto ("100% · 87% do tempo")
  à esquerda do ponto; com o ponto perto de 100% os dois caem na mesma faixa de 4–18dp do topo e se cruzam.
  As legendas são desenhadas **antes** das linhas: projeção tracejada, janelas anteriores e diagonal passam
  por cima das letras (semanal da captura 2: a projeção vermelha corta "line", a janela anterior corta "first").
- **Escala**: `AppTheme` multiplica a densidade do sistema pela escala do app; o padrão persistido é
  `DEFAULT_UI_SCALE_PERCENT = 115` (`UserPreferences.kt`). Com o Windows em 225% (captura 4) a densidade
  efetiva é 2,25 × 1,15 ≈ 2,59 — 15% acima de qualquer outro app. No registro desta máquina
  (`HKCU\Software\JavaSoft\Prefs\com.usagemonitor`, `ui/Scale/Percent`) o valor é 115: o
  `UiScalePersistence` grava o padrão na primeira execução, então toda instalação tem 115 gravado.

### #478 — OpenCode Go a 5 min em uso

- A cadência de 60 s vem só do `cliBusy` (`CollectorEngine.kt`), alimentado por
  `SessionPulseViewModel.activeTargets`: índice do Claude CLI e sonda do Codex. Nenhuma outra fonte tem
  sinal local — OpenCode Go, OpenCode, MiniMax, DeepSeek, Kilo, Gemini, OpenRouter, Cursor e Antigravity
  ficam em 5 min sempre que Claude/Codex estão parados (captura 1 da issue: "Next fetch in 04:12").
- O único sinal dessas fontes é a cota subir (`QuotaActivityTracker`, #385), e a regra da #385 proibia esse
  sinal de acelerar o polling ("realimentaria"). O laço é limitado: a cota só sobe com uso real; parado o uso,
  o alvo volta a 5 min em `USAGE_DETECTED_WINDOW_MILLIS` (10 min) — no máximo ~10 coletas extras.
- Sinal local do OpenCode CLI foi descartado: `~/.local/share/opencode` nesta máquina tem só
  `opencode.db-wal`/`-shm`, sem o `.db`; formato não documentado; não cobre uso fora do CLI.

## Decisões do usuário (2026-10-27)

1. Escala: padrão de fábrica 100% (= escala do sistema); instalações com 115 gravado migram **uma vez** para 100;
   outro valor fica intacto.
2. Cadência: uso detectado pela cota acelera **só o próprio alvo** para 60 s, enquanto o sinal durar; nunca entra no
   `cliBusy` global.

## Atividades

- **A01** `docs(plan)`: este plano; comentário nas issues #477 e #478.
- **A02** `fix(analysis)`: posicionamento do rótulo do ponto por função pura que evita a legenda (candidatos
  acima/abaixo × esquerda/direita, primeiro sem colisão e dentro da área); legendas e rótulo desenhados
  **depois** das linhas, sobre placa da cor da superfície. Teste em `commonTest`. Protótipo e `docs/presentation.md`
  (#463) no mesmo commit.
- **A03** `fix(scale)`: `DEFAULT_UI_SCALE_PERCENT = 100`; migração única (`uiScaleSystemDefaultMigrated`) de 115 → 100.
  Testes em `UiScalePreferencesTest`. `docs/presentation.md` e protótipo.
- **A04** `refactor(dashboard)`: extrair o sinal de cadência do `DashboardViewModel` (751 linhas; a regra manda
  extrair antes de crescer) para `DashboardCadence`. Sem mudança de comportamento.
- **A05** `feat(cadence)`: `DashboardViewModel` recebe os alvos com uso detectado; alvo detectado é devido a 60 s,
  os demais seguem o `cliBusy`. `CollectorEngine` liga `quotaActivity.detectedTargets` por ponte. Teste de cadência
  em tempo virtual. `CLAUDE.md`, `docs/presentation.md` (#385) e KDoc do `QuotaActivityTracker` atualizados.

## Riscos

- Migração 115 → 100 também reverte quem escolheu 115 de propósito: indistinguível no registro; uma vez só.
- Janela persistida mantém o tamanho (é escolha do usuário) e passa a mostrar mais conteúdo a 100%.
- 60 s em fontes sem medição de limite (OpenCode Go, Cursor): 429 já sai tipado em `RemoteApiDataSource.requireSuccess`
  e arma backoff persistido; Antigravity tem TTL de 5 min no repositório, então 60 s não abre processo a cada minuto.
- Detecção tem latência de até 5 min (precisa de duas leituras com avanço).
- Plataformas: suíte só no Windows; mudança em `PaceDiagonalChart` é desenho em `Canvas`, sem host de janela.

## Verificação

`gradlew.bat allTests`; relatório `node .claude/skills/usage-monitor-test-report/scripts/build-test-report.mjs`.
