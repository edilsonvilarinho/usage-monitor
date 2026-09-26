# Tempo do `allTests` no CI (#295) — execução

## Contexto

[Issue #295](https://github.com/edilsonvilarinho/usage-monitor/issues/295): o job `CI / tests` leva
~10 min por PR. Medido no run `36277450509` (PR #293), passo `Run tests` = 10m23s:

| Fase | Tempo |
|---|---|
| Daemon + configuração | ~26 s |
| `compileKotlinDesktop` | ~95 s |
| `compileTestKotlinDesktop` | ~35 s |
| `desktopTest` | **~7m15s** (soma dos XML: 461 s, 2210 testes, 216 classes) |

- 89% do tempo de teste é `com.usagemonitor.ui.*` (409 s); 2070 dos 2210 testes levam menos de 0,5 s.
- Mais lentos: `ComponentTest` 189 s/103, `BugReportDialogTest` 51 s/12, `ApiKeyDialogTest` 36 s/6,
  `HelpMediaResourcesTest` 35 s num teste só, `BugReportHostTest` 34 s/5, `HudNotchTest` 24 s,
  `AppDialogTest` 12 s/4.
- **Regressão datada:** soma dos testes 261 s (run `35944636113`, 24/09) → 425 s (run `36081114394`,
  PR #270, profundidade/movimento/HUD) → 461 s hoje. No #270 `ComponentTest` foi de 103 s a 176 s e
  `BugReportDialogTest` de 16 s a 42 s, com piso de ~2,7 s por teste.
- A suíte rodava com **um fork**. Forks já tinham sido medidos (1m24s serial → 52 s com 4) e ficaram
  desligados só pela corrida de extração da nativa do Skiko em runner limpo.

## Hipóteses (a confirmar antes de corrigir)

- **H1:** o salto do #270 vem das transições finitas (molas e tweens, `appDepth` com duas sombras,
  `appSheen`) — mais quadros até o `waitForIdle` e quadro mais caro no raster de CPU.
- **H2:** o `Dialog` de plataforma sob `runDesktopComposeUiTest` tem custo fixo de ~2,7 s.
- **H3:** `HelpMediaResourcesTest` gasta em copiar e converter o quadro inteiro (1000×420) a cada passo.

## Fora de escopo

- `compileKotlinDesktop` (~95 s): a causa é `main()` gigante em `Main.kt`, já documentada; outra issue.
- Build cache não evita recompilar em PR, porque as fontes mudam sempre.

## Pontos de situação

| # | Atividade | Comando | Resultado |
|---|---|---|---|
| A01 | Linha de base local, serial (16 processadores) | `gradlew.bat desktopTest --rerun-tasks` | 7m48s de parede com compilação; ~342 s de testes (`ComponentTest` 142 s, `BugReportDialogTest` 33 s, `HelpMediaResourcesTest` 28 s, `BugReportHostTest` 27 s, `ApiKeyDialogTest` 24 s) |
| A02 | Forks paralelos seguros: `extractSkikoNative` (`SkikoWarmup.kt`, `Library.load()` num processo só) pendurada antes do `desktopTest` quando `testForks > 1`; CI com `-PtestForks=3` | `~/.skiko` renomeado (runner frio), `gradlew.bat allTests -PtestForks=4` | verde, 2211 testes, `extractSkikoNative` executada antes do `desktopTest`, 2m10s de parede (com A03 e A04 aplicados). Pendente: 3 runs de CI |
| A03a | Diagnóstico H1/H2 com teste descartável (removido) | `gradlew.bat desktopTest --tests TmpDialogProfile` | `Dialog` cru 0,36 s; `AppDialog` em `Static` 2,13 s; em `Reduced` 0,41 s. **H1 confirmada, H2 refutada** |
| A03b | Teto do ganho: default do `AppTheme` trocado para `Reduced` localmente (revertido) | `gradlew.bat desktopTest --continue` | 342 s → 224 s de testes; 1 falha, `AppStatesTest` (teste de movimento proposital) |
| A03c | `ScreenTestTheme` (`AppTheme` + `Reduced`) em 19 arquivos de teste de tela; primitivas que animam continuam no `AppTheme` | incluso na passada de A02 | verde; `ComponentTest` 142 → 97 s, `BugReportDialogTest` 33 → 10 s, `ApiKeyDialogTest` 24 → 16 s, `BugReportHostTest` 27 → 13 s |
| A04 | H3 medida: decodificação 311 quadros ~0,2 s; `frameAt` 18,4 s e `toPixelMap` 9,1 s — o custo era `Image.makeFromBitmap(...).toComposeImageBitmap()` (redesenho por `Canvas`). `makeClone()` testado e **recusado**: compartilha pixels (teste novo `a published frame keeps its pixels...` reprova). Cópia de bytes (`readPixels` → `installPixels`) | `gradlew.bat desktopTest --tests "com.usagemonitor.help.*"` | verde, 9 testes; `plays every frame...` 35 s → 0,9 s. Pendente: olhar a demo na janela de Ajuda (`gradlew.bat run`, F1) |
| A05 | Gargalo remanescente: `ComponentTest` roda inteiro num fork (97 s local) | — | não iniciado; dividir a classe é o próximo passo se o CI ainda passar de ~5 min |
