# Timer da próxima coleta na barra HUD — plano de execução

Issue [#185](https://github.com/edilsonvilarinho/usage-monitor/issues/185): "mostrar o timer da
proxima att na direita".

## Problema

A issue não tem texto — só uma imagem. Ela é um recorte da **barra HUD** em uso real
(`● Crítico  INFORMATA2  ● 5h 45%  ● 7d 11%`), com uma seta vermelha apontando para o espaço **à
direita da linha**. O título fecha a leitura.

O dado já existe e já é exibido, mas só na janela normal. `DashboardViewModel.nextRefreshAt`
(`StateFlow<Instant>`) alimenta o `FooterBar`, que faz a contagem regressiva em `mm:ss`
(`formatRefreshCountdown`). Em modo HUD o rodapé **não é composto** — `DesktopWindowFrame` descarta
`content()` quando `hud = true` —, então quem trabalha com a barra flutuante não tem como saber
quanto falta para a próxima coleta sem sair do modo.

## Decisões

1. **Só na primeira linha.** O polling é um só (10 min), não é por conta: repetir o valor em cada
   linha do painel afirmaria que cada conta tem coleta própria. Parada, a barra mostra exatamente uma
   linha — que é onde a seta da issue aponta.
2. **Recolhida ao ponto continua sem texto.** Com tudo em `ON_TRACK` a barra vira um ponto de 6dp e o
   timer não aparece; o hover devolve o painel inteiro com ele. É a regra que já está registrada no
   `AppHudBar.prompt.md` — enquanto está tudo bem, a barra para de ocupar tela —, e abrir exceção
   para o timer desfaria o recolhimento em uso normal, que é o estado mais comum.
3. **Ícone de atualização mais `mm:ss`, nunca tooltip.** No HUD não cabe popup: no Compose Desktop
   ele é camada **dentro** da janela e sai recortado sobre o próprio alvo — decisão já paga uma vez
   na #164. O ícone é o único portador de significado disponível, e o `contentDescription` dele
   carrega a frase por extenso, que é o caminho do leitor de tela e dos asserts.
4. **Nenhum formato novo.** `formatRefreshCountdown` é reusada como está. Com o poll de 10 min o
   `%02d:%02d` dá sempre cinco caracteres, e a escala `label*` deste sistema é mono: a largura da
   coluna é constante e a janela **não** muda de tamanho a cada segundo — que seria o defeito óbvio
   de pôr um relógio numa janela dimensionada pelo conteúdo.
5. **O tique mora dentro do `HudBar`**, com `nowProvider`/`waitNextTick` injetáveis, exatamente como
   o `FooterBar` já faz. Mantê-lo em `Main.kt` recomporia `main()` inteiro a cada segundo — e
   `main()` já está no limite do backend JVM.
6. **A linha de carregamento também recebe o timer**: ela é a primeira linha, e enquanto nada foi
   coletado "quando é a próxima tentativa" é a informação mais útil que a barra tem para dar.

## O que foi verificado antes de decidir

- **A largura cabe, e a conta é a da geometria existente.** `hudSourceRowWidth` para a linha da
  imagem: `24 + 10 + 46,9 + 12 + 72 + 12 + 114,4 + 8 ≈ 299dp`, contra o teto
  `HUD_PILL_MAX_WIDTH = 420dp`. A coluna nova custa `md(12) + ícone(12) + xs(4) + 5 × 7,2 ≈ 64dp` →
  **~363dp**. O teto **não muda**.
- **`formatRefreshCountdown` é `internal` em `commonMain`** e visível de `desktopMain` (mesmo módulo);
  não há por que duplicar formato.
- **`FooterBarTest` já tem o molde do teste de contagem** — `nowProvider` fixo mais um `Channel` em
  `waitNextTick` —, e é ele que deixa o decremento ser afirmado sem esperar segundos reais.
- **O protótipo está atrasado** na seção da barra HUD: ele ainda desenha uma linha por *cota* com
  reset, desenho que o Kotlin abandonou quando a linha passou a ser por conta. O design system
  (`AppHudBar.prompt.md`) já descreve a versão vigente, e pela regra de precedência quem se corrige é
  o protótipo.

## Riscos declarados

1. **Nome de conta longo trunca mais.** A coluna nova come ~64dp que hoje sobram para o nome. É
   medido no app real em A08; se doer, a saída registrada é subir `HUD_PILL_MAX_WIDTH`, com o
   precedente exato de 320 → 420 pela mesma razão.
2. **A demo `window-modes.gif` da janela de ajuda mostra o HUD** e passa a descrever um desenho
   anterior. Regerar as doze demos por uma coluna de 64dp seria ~1 MB de churn binário sem informação
   nova; a saída é regerar e commitar **só** aquele arquivo.
3. **Um relógio numa janela dimensionada pelo conteúdo pode oscilar.** Mitigado pela decisão 4 — a
   largura é estimada sobre um placeholder de largura fixa, não sobre o texto corrente.

## Pontos de situação

| # | Atividade | Comando | Resultado |
|---|---|---|---|
| A01 | Plano e comentário-índice na issue | `gh issue comment 185` | plano publicado no comentário `5512207488`; comentário-índice `5512208545`, editado ao fim de cada atividade por `gh api -X PATCH repos/:owner/:repo/issues/comments/5512208545`. A leitura da issue veio da imagem, que é o único conteúdo dela: recorte da barra HUD com a seta à direita da linha |
| A02 | Coluna da contagem na geometria | `gradlew.bat desktopTest --tests "com.usagemonitor.HudWindowGeometryTest"` | `BUILD SUCCESSFUL`, **29 testes, 0 falhas** (23 antes, 6 novos). `showsCountdown` nasce `false` e portanto o commit é inerte em produção. **O teste que separa "uma coluna" de "uma por linha"** monta a lista com a linha estreita na frente e a larga atrás: com a coluna entrando em todas, a largura cresceria; entrando só na primeira, ela não muda. A largura é medida sobre o placeholder `"00:00"` e nunca sobre o relógio — a janela é dimensionada pelo conteúdo, e medir o texto corrente a faria mudar de tamanho a cada segundo |
