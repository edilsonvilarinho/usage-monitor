# HUD como único modo de visualização — plano de execução

Pedido do usuário (setembro de 2026), com três capturas: o menu "Modo de janela" do balão da
engrenagem riscado, "Padrão" e "Somente os cards" riscados, e o interruptor "Somente os cards" das
Configurações riscado — "consolidar como padrão a visualização apenas via barra HUD".

## Decisões

Tomadas com o usuário antes da execução:

1. **A HUD é o único modo.** Somem o modo "Somente os cards", a janela Padrão como modo de
   visualização e todo seletor de modo (menu do rodapé, faixa de título, linhas do balão).
2. **Quem tinha `cardsOnlyMode=true` migra para a HUD** — o usuário escolheu a moldura mais discreta
   que existia, e a equivalente é a HUD. A migração roda uma vez e apaga a chave.
3. **Perdas aceitas** com a janela padrão: reordenar cards por arrasto, minimizar card, o card
   completo com "tentar de novo" e a tela vazia "Abrir configurações". A HUD lê o `cardOrder` já
   gravado e recoleta por clique no anel.

Execução em dois commits: A1 remove o modo somente cards (o app continua com Padrão ↔ HUD e compila
sozinho); A2 torna a HUD o único modo e remove a janela principal.

## Pontos de situação

| # | Atividade | Comando | Resultado |
|---|---|---|---|
| A1 | Remove o modo somente cards: estado, interruptor, item da bandeja, `Ctrl+Shift+M`, botão direito da HUD (engolido sem ação), `WindowMode` e o menu de modos do rodapé, da faixa de título e do balão da engrenagem; migração `migrateCardsOnlyModeToHud` com `CardsOnlyModeMigrationTest`; testes do menu e do modo apagados; ajuda PT/EN, `presentation.md`, `hud-notch.md`, `CLAUDE.md`, protótipo e design system (`CardsOnly.jsx` removido) | `gradlew.bat allTests` | 2404 testes, 0 falhas, 0 ignorados. A demo `help/window-modes.gif` ainda não foi regenerada — fica para A2, que reescreve o tópico |
| A2 | HUD como único modo: `MainWindowHost`, `DesktopWindowFrame`, geometria persistida da janela principal, `hudMode` (com a troca automática da instalação nova e a migração de A1), "Manter sempre visível", interruptor "Barra HUD", item da bandeja, `Ctrl+Shift+H` e `isAppVisible` removidos; `anchorAppWindow` faz a HUD âncora do app (diálogos, captura, ACK de atualização, `focusHud` na bandeja e na segunda instância); relatório de bug em `BugReportWindow`; Configurações abrem sozinhas sem API; ajuda PT/EN reescrita e `window-modes.gif`/`appearance.gif` regeneradas; `CLAUDE.md`, `presentation.md`, `hud-notch.md`, protótipo e kit | `gradlew.bat allTests`; `gradlew.bat generateHelpMedia` | 2378 testes, 0 falhas, 0 ignorados (26 a menos: testes do menu, do modo e da geometria da janela principal apagados). As outras GIFs que a regeneração tocou foram revertidas. **Não aberto no app**: a instância instalada segura o `SingleInstanceGuard`, e o arranque de desenvolvimento sairia como segunda instância |
