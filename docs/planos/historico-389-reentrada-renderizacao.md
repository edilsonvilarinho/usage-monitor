# Issue #389 — histórico falha ao abrir

Data: 06/10/2026. Status: proteção implementada no checkout; causa iniciadora e confirmação na execução afetada pendentes.

Issue: https://github.com/edilsonvilarinho/usage-monitor/issues/389

## 1. O que dá para afirmar com segurança

- A issue registra app 41.7.2, Windows 11, JVM 17.0.20.1, escala 115%, tela 1280×800. A trilha registra a primeira pintura do histórico Codex imediatamente antes da exceção na EDT.
- O diagnóstico local `~/.usage-monitor/diagnostics/pending-crash.json` confirma `IllegalStateException: Reentry into ignoringRedrawRequests is not allowed`.
- Os cinco frames registrados começam em `SwingInteropContainer.postponingExecutingScheduledUpdates`, `ComposeSceneMediator.onRender`, `SkiaLayer.update` e `AWTRedrawer.update`.
- O código-fonte da dependência Compose UI Desktop 1.12.1 confirma que essa mensagem protege contra reentrada no bloco que adia atualizações de interoperabilidade durante a renderização.
- `AppDialogWindow` esperava quadros com `withFrameNanos` e, na continuação, ativava a janela. As animações também atualizavam a opacidade nativa a partir de callbacks de quadro. Essa sequência existia no código; ela não comprova, sozinha, qual operação iniciou o crash registrado.

## 2. O que ainda é hipótese

Ativação, foco ou alteração de opacidade durante a pintura podem iniciar outra atualização nativa antes de a pintura anterior retornar. A proteção remove essa possibilidade nos pontos citados. Não foi reproduzida a exceção original nas sondas locais, inclusive antes da proteção; portanto a causa raiz e a resolução do incidente não estão confirmadas.

## 3. Evidência que falta

O chamador que provocou a segunda pintura está fora dos cinco frames armazenados. Também falta identificar se a execução afetada é a instalação 41.7.2 ou a execução do checkout e validar novamente o clique pela HUD nessa execução.

## 4. Como validar

1. Testes `AwtWindowDispatchTest`: operação nativa sai do callback corrente da EDT; cancelamento impede ativação tardia.
2. Testes existentes `AppDialogWindowTest` e `HistoryWindowActivationTest`, junto da suíte agregada `allTests`.
3. `probeHistoryWindow`: janela real com dados sintéticos, conta Codex longa, escala 115%, entrada/saída animada e cinco aberturas disparadas pelo relógio de quadros de outra janela. Sem carregar credenciais, banco ou preferências reais.
4. Executar com renderizadores software e Direct3D; repetir com pré-aquecimento. Resultados locais ficam em `build/issue389-*`.
5. Na execução afetada, abrir/fechar histórico cinco vezes pela HUD, repetir com a janela já aberta e ao trocar de conta; observar foco, opacidade e ausência da exceção. Confirmar cancelamento e gravação do PDF separadamente.

```powershell
.\gradlew.bat probeHistoryWindow '-PhistoryProbeRenderApi=SOFTWARE' '-PhistoryProbeOutputDir=build/issue389-software' --no-daemon --console=plain
.\gradlew.bat probeHistoryWindow '-PhistoryProbeRenderApi=DIRECT3D' '-PhistoryProbePrewarm=true' '-PhistoryProbeOutputDir=build/issue389-prewarm' --no-daemon --console=plain
.\gradlew.bat allTests --no-daemon --console=plain
```

## 5. Próximo passo seguro e alteração aplicada

- `AwtWindowDispatch.kt`: fronteira explícita com `EventQueue.invokeLater`; `Dispatchers.Main.immediate` não fornece essa fronteira quando a execução já está na EDT.
- `AppDialogWindow.kt`: espera a próxima passada da EDT antes de ativar a janela após os primeiros quadros; alterações de opacidade por quadro são enfileiradas; o pré-aquecimento restaura foco fora da pintura.
- `AppWindowStates.kt`: aplicação do tamanho mínimo também aguarda uma passada da EDT.
- Nenhuma atualização de Compose/Skiko, troca permanente de renderizador, alteração de banco ou mudança de credenciais.
- Impacto: host compartilhado pelos modais, com um despacho adicional para operações nativas. Risco: alteração de timing de foco/entrada; validar os outros modais. Rollback: retirar somente essa proteção e seus testes, preservando os ajustes independentes de layout e PDF.
- O pedido posterior “cria pr”, em 06/10/2026, autorizou commit, push e PR. Release e fechamento da issue não foram solicitados. Aprovação automatizada não substitui a confirmação na execução afetada.

## Evidências executadas no checkout

- `allTests`: **2463 testes, zero falhas, erros ou ignorados**. Log `build/issue383-389-final.log`.
- Depois da proteção: cinco aberturas em software (`build/issue389-after`), cinco em Direct3D (`build/issue389-after-direct3d`) e cinco em Direct3D com pré-aquecimento, conta Codex longa e escala 115% (`build/issue389-after-prewarm`): **zero exceções nas 15 aberturas**.
- Antes da proteção as sondas também não reproduziram a exceção. O resultado posterior verifica a abertura/fechamento na sonda, sem provar resolução do incidente original.
- `git diff --check` aprovado. Alteração preexistente em `server/package-lock.json` preservada.
