# Modais: abertura rápida, animação e um host só — execução

## Contexto

Pedido do usuário: "os modais da aplicação ficam lentos, sem animação, a abertura é muito brusca —
otimize todos os modais e organize".

O que o código mostrava:

1. **Lentidão.** As nove janelas modais (Histórico, Sessões CLI, Sessões Codex, Uso do time,
   Presença, Chaves, Configurações, Ajuda, Novidades) eram `Window`/`DialogWindow` compostas sob
   `if (aberto)`. Fechar destruía a janela; reabrir recriava peer nativo, contexto do Skia e a
   composição inteira.
2. **Brusquidão.** `DesktopDialogFrame` já animava escala 0,94 → 1, mas dentro de uma janela que o
   sistema mostrava de uma vez e opaca, começando ao compor — antes do primeiro quadro pintado. Só o ×
   da barra esmaecia ao fechar; Alt+F4, Esc e os botões "Fechar" do conteúdo fechavam secos.
3. **Desorganização.** O mesmo bloco copiado nove vezes em `Main.kt`, `HelpWindow.kt` e
   `ReleaseNotesWindow.kt`, com divergências (Chaves e Ajuda não ativavam a janela).
4. **Diálogos internos.** Seis `AlertDialog` do Material surgiam num quadro, com superfície própria.

## Decisões do usuário

- **Manter a janela viva** depois da primeira abertura (esconder/mostrar em vez de recriar).
- Animação **fade + escala sutil**.

## Atividades

| # | Atividade | Estado |
|---|---|---|
| M1 | Linha de base: criar janela × reexibir janela escondida | feito |
| M2 | `AppDialog` no lugar dos seis `AlertDialog` | feito |
| M3 | `AppDialogWindow`: janela viva, entrada após o primeiro quadro, fechamento único | feito |
| M4 | Migração das nove janelas modais | feito |
| M5 | Documentação: design system, protótipo, CLAUDE.md | feito |
| M6 | Verificação: suíte, sonda da janela real | feito |

## Pontos de situação

| Data | Atividade | Modelo | Comando | Resultado |
|---|---|---|---|---|
| 2026-09-24 | M1 | Claude Opus 5.5 | teste descartável `ModalLatencyProbe` (`ComposeWindow` 900×700, 120 linhas de texto, `AppTheme`) via `gradlew.bat desktopTest --tests "com.usagemonitor.ModalLatencyProbe"` | Até o segundo quadro pintado, quatro rodadas: criar do zero 7290 ms (JVM fria, carga do Skiko), depois **461, 304 e 200 ms**; reexibir a mesma janela escondida **46, 36, 36 e 30 ms**. A sonda não foi commitada. |
| 2026-09-24 | M2 | Claude Opus 5.5 | `gradlew.bat desktopTest --tests "com.usagemonitor.ui.AppDialogTest" --tests "com.usagemonitor.AppDialogWindowTest" --tests "com.usagemonitor.ui.BugReportDialogTest"` | Primeira passada: 2 falhas em `BugReportDialogTest` (prévia e status não achados por tag). O `clickable` do cartão fundia a semântica dos descendentes; trocado por toque cru no fundo, testando a caixa do cartão. Segunda passada verde. |
| 2026-09-24 | M3–M4 | Claude Opus 5.5 | `gradlew.bat compileKotlinDesktop compileTestKotlinDesktop` | Verde. `DesktopDialogFrame` perdeu a entrada e o fade próprios e recebe a escala do host. |
| 2026-09-24 | M6 | Claude Opus 5.5 | `gradlew.bat allTests` | Verde: 2134 testes, 0 falhas (depois da correção da reabertura e com a sonda já removida). |
| 2026-09-24 | M6 | Claude Opus 5.5 | sonda descartável com o `AppDialogWindow` real num `awaitApplication`, amostrando a opacidade da janela AWT a cada 8 ms | Primeira passada: abrir e fechar funcionavam, **reabrir não** — a janela ficava escondida. Janela escondida não recompõe, e o `LaunchedEffect(visible)` dentro dela nunca via o pedido. O pedido passou a chegar por `StateFlow` coletado por uma corrotina de vida longa. Segunda passada: 1ª abertura pintada em 202 ms e fade 0→1 em ~240 ms; saída 1→0 em ~150 ms e só então esconde; **reabertura pintada em 13 ms**; reabrir no meio da saída reverte de 0,74 a 1 sem esconder. (A sonda precisou de uma janela principal sempre visível: sem nenhuma janela o gerenciador global de snapshots do Compose não arranca.) Sonda não commitada. |
