# Acesso rápido aos modos de janela — plano de execução

Issue [#187](https://github.com/edilsonvilarinho/usage-monitor/issues/187): "Adicionar um acesso
rapido aos modos de tela que temos".

## Problema

O app tem três molduras — **padrão**, **somente os cards** (issue #83) e **barra HUD** (issue #164)
— e nenhuma porta rápida entre elas. Ligar qualquer uma exige abrir Configurações → Geral e achar
dois interruptores no meio da seção "Sistema" (`CardsOnlyModeToggle` e `HudModeToggle`,
`SettingsDialogContent.kt`), ou lembrar de `Ctrl+Shift+M` / `Ctrl+Shift+H`, ou abrir o menu da
bandeja. A segunda imagem da issue é justamente essa seção de Configurações, o que fecha a leitura:
o pedido é uma porta de **um clique** na própria janela.

O rodapé é o lugar. É ali que o app já concentra as ações de janela — atualizar, configurações,
ajuda — e ele é composto **só no modo padrão** (`DashboardScreen(showFooter)`), que é exatamente de
onde se sai para os outros dois.

## Decisões

1. **Um ícone que abre um menu com os três modos**, com o corrente marcado — escolha do usuário
   entre as três formas oferecidas (a alternativa era um segmentado de ícones ou dois botões
   soltos). O menu diz **quais** modos existem, que é metade da queixa: os dois modos só eram
   descobertos por acidente.
2. **Só no rodapé.** Voltar continua pelos caminhos que já existem — `Ctrl+Shift+M`/`Ctrl+Shift+H`,
   a bandeja, a faixa de hover do modo cards e o clique na pílula do HUD. A faixa do modo somente
   cards **não** ganha o controle: ela existe para devolver a moldura, e um menu dentro de uma faixa
   que só existe durante o hover seria um alvo que some quando o ponteiro sai para escolher.
3. **`AppMenu` é primitiva nova do design system**, porque o sistema não tem nenhuma — e o commit
   que a cria é o **mesmo** que a consome, pela regra do repositório (primitiva construída e não
   adotada não conserta nada; foi assim que `AppWindowScaffold` e `AppToolbar` ficaram meses com
   adoção zero). Ela veste o `DropdownMenu` do Material 3 com os tokens do sistema e reusa o
   tratamento de seleção do `AppSettingsNavItem` — contêiner `surfaceVariant` e texto `onSurface` —,
   em vez de inventar um segundo desenho de item selecionado.
4. **`WindowMode` é enum novo**, não valor a mais em enum existente. As preferências continuam sendo
   **dois booleanos** (`cardsOnlyMode`, `hudMode`) e a exclusão mútua continua sendo regra dos
   setters em `Main.kt`: o enum descreve o que o **controle** oferece, não como o estado é guardado.
5. **Os dois parâmetros novos do `FooterBar` são opcionais**, com `onWindowModeChange = null`
   escondendo o controle — mesmo padrão de `onOpenAdminOverview`/`onOpenTeamPresence`. É isso que
   mantém os três geradores de captura (`ScreenshotGenerator`, `TourGifGenerator`,
   `HelpMediaGenerator`) montando o rodapé sem despachar nada.
6. **Nenhuma composable nova em `main()`.** A fiação entra como parâmetro do `DashboardScreen`, que
   já repassa cinco outros. `main()` está no limite do backend JVM.

## O que foi verificado antes de decidir

- **Popup é alcançável nos testes de componente deste projeto.** `ComponentTest` já move o mouse
  para abrir tooltips e afirma o texto **dentro** delas (`onNodeWithText("Uso atual")` depois do
  hover). O que a #164 mediu é outra coisa: numa janela de **24dp de altura** o balão não cabia e
  era recortado sobre o próprio alvo. O rodapé vive numa janela de no mínimo 320dp.
- **`materialIconsExtended` já é dependência** (`build.gradle.kts`), então o ícone do gatilho e os
  dos itens não exigem recurso novo.
- **O rodapé já tem cinco alvos de 28dp** e dois deles são condicionais; o sexto cabe no mesmo
  grupo.
- **A escala de elevação já prevê menu**: "elevação é de janela, diálogo, **menu** e overlay" está
  escrito no design system desde a refatoração de agosto, e nenhum menu existia para usá-la.

## Riscos declarados

1. **Popup no Compose Desktop é camada dentro da janela e recorta.** O provedor de posição do
   Material vira o menu para cima quando não cabe abaixo; o teste de A02 monta a cena no piso de
   arrasto da janela principal (240×320dp) e é ele que afirma isso, com A07 confirmando no app real.
   Se recortar mesmo assim, a saída registrada é trocar o menu por ícones soltos no rodapé — desenho
   que não depende de popup.
2. **O menu só existe no modo padrão**, porque o rodapé só é composto ali. É consequência aceita da
   decisão 2, e o texto do tópico de ajuda diz isso em vez de deixar o usuário descobrir.
3. **Primitiva nova é dívida se ninguém mais a usar.** Mitigada pela regra do repositório: A02 cria
   e consome no mesmo commit.

## Pontos de situação

| # | Atividade | Comando | Resultado |
|---|---|---|---|
| A01 | Plano e comentário-índice na issue | `gh issue comment 187` | plano publicado no comentário `5513468163`; comentário-índice `5513470027`, editado ao fim de cada atividade por `gh api -X PATCH repos/:owner/:repo/issues/comments/5513470027`. A leitura veio das duas imagens: a primeira é a janela do app, a segunda é a seção de Configurações onde os dois modos moram hoje — é ela que diz que o pedido é um atalho na janela, e não um controle novo nas Configurações. A forma (ícone + menu, só no rodapé) foi escolhida pelo usuário entre três alternativas apresentadas |
| A02 | `AppMenu` **e** o seletor de modos no rodapé | `gradlew.bat desktopTest --tests "com.usagemonitor.ui.FooterBarTest"` | `BUILD SUCCESSFUL`, **13 testes, 0 falhas** (9 antes, 4 novos). A primitiva e o consumidor no **mesmo commit**, pela regra do repositório. **`AppMenu` é `Popup` com a superfície deste sistema, e não o `DropdownMenu` do Material**: aquele traz a própria superfície, o próprio raio, a própria animação de entrada e a própria altura de item, e nenhum dos quatro é o deste sistema — vesti-lo por fora deixaria dois desenhos de menu no mesmo app. A seleção reusa o tratamento do item de navegação das Configurações (`surfaceVariant` + `onSurface`) e carrega **marca além do realce**, com o espaço da marca reservado em todas as linhas para o rótulo não andar ao trocar de opção. **O risco 1 ficou afirmado por teste, não por leitura**: numa cena de 240×320dp — o piso de arrasto da janela principal — com o rodapé na borda de baixo, o teste compara `boundsInRoot` do menu com o topo do rodapé e prova que ele abriu **para cima**. `WindowMode` é enum novo e os rótulos são os **mesmos** das Configurações: dois nomes para a mesma moldura fariam o passo da ajuda apontar para um controle que a tela chama de outra coisa. O ícone é `Icons.Rounded.Wysiwyg`, do `materialIconsExtended` que já era dependência |
