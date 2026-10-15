# Sair do app pela HUD — issue #416

## Problema

O pacote `--diagnostics` do relator da #416 (Pop!_OS, Cosmic, Wayland via XWayland; linha L11 de
[`log-passivo-416-execucao.md`](log-passivo-416-execucao.md)) traz `tray.supported=false` no
`environment.txt`. Consequência no código:

- O único "Sair" da interface é o item do menu da bandeja (`AppTrayHost.kt`,
  `Item(text = if (pt) "Sair" else "Quit", onClick = onQuit)`), e o `AppTrayHost` só é composto com
  `isTraySupported` (`Main.kt`).
- Desde a #361 a HUD (`HudWindow.kt`) é a única janela, `undecorated = true`: não há botão de fechar.
- O balão da engrenagem (`HudWindowAppBalloon` → `HudAppBalloonContent` + `FooterActionGroup`)
  oferece atualizar, configurações, ajuda, comparar, exportar e time — nenhuma saída.

Sem bandeja, o usuário só fecha o app matando o processo.

## Decisões

### Onde: última linha do balão da engrenagem

- O balão da engrenagem é, por contrato, "tudo o que o rodapé oferece" para quem está na HUD
  (`AppHudBar.prompt.md`, "Handles"); é aberto de propósito (hover ou clique na engrenagem) e já
  hospeda a outra ação que encerra o processo, "Reiniciar o app e atualizar" (#225, #291), pelo
  mesmo motivo: o rótulo diz o que o clique faz, e no notch seria clique de rotina.
- **Linha própria, não um oitavo ícone na fileira do rodapé.** Medido no código: `HUD_BALLOON_WIDTH`
  264dp − 2 × `HUD_BALLOON_PADDING` 12dp = 240dp de coluna; `FooterActionGroup` com as sete ações
  (presença do time, visão do admin, comparar, exportar, atualizar, configurações, ajuda) mede
  7 × 28dp + 6 × 6dp = 232dp. Um oitavo ícone daria 266dp e estouraria a coluna.
- **`AppButton` com borda (`DEFAULT`), não `GHOST` nem `DANGER`.** O rótulo sem moldura no mesmo
  balão já foi rejeitado na #291 (só parecia clicável no hover). `DANGER` é para ação que destrói
  dado (`AppButton.prompt.md`: "Remover"); sair não apaga nada.
- Rótulo "Sair do Usage Monitor" / "Quit Usage Monitor" (`hudQuitLabel`, por `localized`): diz **o
  que** fecha — "Sair" sozinho, ao lado do notch, pode ser lido como sair do balão.

### Sempre visível, não só sem bandeja

- `isTraySupported == true` não prova que o usuário alcança o ícone: o Windows 11 põe ícone novo no
  menu de estouro da barra de tarefas por padrão.
- Altura do balão é soma fixa (`hudAppBalloonHeight`) que dimensiona a janela antes de qualquer
  composição (`docs/hud-notch.md`). Linha condicional à plataforma daria dois balões e um parâmetro a
  mais para a geometria, sem ganho de comportamento.
- A ajuda (`HelpCatalogPt`/`En`) cita um rótulo só, válido em toda plataforma.
- Sem decisão que dependa da bandeja, não há função pura de visibilidade para testar; o que se testa
  é a presença, o clique e a altura.

### Um caminho de encerramento

O botão chama o `onCloseRequest` do `HudWindowHost`, que o `Main.kt` já liga a
`shutdownApplication` — o mesmo lambda passado ao `AppTrayHost` como `onQuit` e ao
`autoUpdate.bindRestart`. `AppViewModels.shutdown()` continua com dono único; nenhuma segunda cópia.

## Risco de plataforma (#342)

`HudWindow.kt` é host de janela. A mudança não chama o sistema de janelas (só repassa o lambda
existente para o conteúdo do balão), mas **não foi aberta numa sessão gráfica Linux (X11 ou
Wayland)** nem no Windows real; cobrem só os testes de componente. Registrado no `§15 #checklist` do
protótipo.

## Atividades

| Item | O que faz |
|---|---|
| S1 | "Sair do Usage Monitor" no balão da engrenagem: `HudAppBalloonContent(onQuit)`, `hudAppBalloonHeight` com a linha, `HudWindowAppBalloon` ligado ao `onCloseRequest`; testes de tela, de altura e de caber o texto; protótipo (3b, 3c, nota "Alças", checklist), `AppHudBar` (prompt, `.d.ts`, `.jsx`), kit `Hud.jsx`, `hud-notch.md` e passo da ajuda |

## Ponto de situação

Cada linha é escrita no commit da atividade que descreve, com o comando executado e o resultado.

| Atividade | Alteração | Comando e resultado |
|---|---|---|
| S1 | Botão `AppButton` "Sair do Usage Monitor" (`HUD_APP_BALLOON_QUIT_TAG`) como última linha do balão da engrenagem, sempre; `hudAppBalloonHeight` soma `HUD_BALLOON_SECTION_GAP + AppChrome.control`. Testes novos em `HudNotchTest`: abrir pela engrenagem do notch e clicar chama `onQuit` uma vez; com atualização pronta a saída fica abaixo do botão de atualizar e não o dispara. `HudNotchTextFitTest` mede o rótulo nas duas línguas em todas as escalas; o teste existente de altura confere a coluna composta contra a geometria. Não aberto em janela real (#342) | `gradlew.bat desktopTest --tests "com.usagemonitor.ui.HudNotchTest" --tests "com.usagemonitor.ui.HudNotchTextFitTest" --tests "com.usagemonitor.HudNotchGeometryTest"`: 102 testes, 0 falhas. `gradlew.bat allTests`: primeira passada 2.826 testes, 1 falha (`ArchitectureRulesTest`: `HudWindowHost` com 301 linhas, teto 300 — comentário inline redundante com o KDoc de `onCloseRequest` removido); segunda passada 2.826 testes, 0 falhas |
