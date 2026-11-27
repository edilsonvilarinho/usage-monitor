# Roteiro — jornada de confiança em 10 min (#686, #644)

Gerado do `video.json`. Os números têm origem na lista `facts`. A fala conta a história e a tela mostra os números.

## Longo (10 min, 16:9): LinkedIn PT e X EN

### PT

**1. O app — Isto é o Usage Monitor**

Isto é o Usage Monitor. É um app de desktop que fica numa barra no topo da tela, a HUD, e mostra quanto você já gastou da cota de cada assistente de IA, como Claude e Codex.

Imagem: `img/hud-barra.png`

Na tela: `a HUD: barra no topo da tela`

**2. O app — Um clique abre o detalhe**

Um clique num anel abre o detalhe da conta: a sessão de cinco horas, o limite da semana e quando cada um reinicia. É uma janela pequena, sem moldura, desenhada por cima da sua área de trabalho. Guarde essa janela: ela volta mais adiante.

Imagem: `img/hud-detalhe.png`

Na tela: `sessão de 5 h e limite semanal`

**3. Motivação — Escrito por agentes de IA**

O detalhe que importa: quase todo o código desse app é escrito por agentes de IA. O pedido vira uma issue, a issue vira um plano, o agente escreve o teste, depois o código, e abre o PR. O merge acontece sozinho quando o CI fica verde.

Na tela: `issue → plano → teste → código → PR` · `merge sozinho com o CI verde`

**4. Motivação — Dá para confiar?**

Então assistimos à palestra All Code Is Slop, Except Mine, de Lauren Tan. A pergunta dela é direta: se ninguém revisa cada linha, por que confiar nesse código? Um teste verde diz que o teste passou. Não diz que ele olha a coisa certa. Abrimos uma issue para responder com números.

Na tela: `All Code Is Slop (Except Mine)` · `Lauren Tan · AI Engineer` · `issue #644`

**5. Antes — O que já funcionava**

O ponto de partida não era ruim. Todo pedido passava pelo mesmo fluxo, o teste vinha antes do código, e uma catraca impedia a cobertura de linhas de cair abaixo de noventa e cinco por cento.

Na tela: `um fluxo para todo pedido` · `teste antes do código` · `95% das linhas cobertas`

**6. Antes — O que ninguém sabia**

Mas havia buracos. Muitas regras do projeto existiam só em texto, no CLAUDE.md, e dependiam de alguém lembrar. O build do Linux podia falhar e só avisava. E a pergunta mais simples não tinha resposta: quantos defeitos passavam pela revisão e chegavam à main?

Na tela: `regras só em texto` · `Linux só avisava` · `defeitos que escapam: ninguém media`

**7. A palestra — Primeiro, o determinístico**

A palestra propõe uma ordem. Na base, o que é determinístico: regras de arquitetura que uma máquina confere em todo PR, sem depender de ninguém. Se uma regra pode virar teste, ela não deveria ficar só escrita num documento.

Na tela: `1. regras que a máquina cobra` · `2. verificação` · `3. prosa, por último`

**8. A palestra — Depois, verificação**

No meio, verificação. Não basta testar três exemplos escolhidos à mão: um teste de propriedade cobra uma regra para qualquer entrada. E é preciso ver o produto rodando de verdade, do jeito que o usuário recebe.

Na tela: `regra que vale para qualquer entrada` · `o produto rodando de verdade` · `quebrar o código de propósito`

**9. A palestra — Por último, prosa**

No topo, e por último, prosa: instruções escritas para o agente. Ela ajuda, mas o agente pode ignorar ou entender errado. No nosso app a ordem estava invertida: muita prosa, e quase nada no meio da pirâmide.

Na tela: `texto: o agente pode ignorar` · `21,5% do Kotlin era comentário`

**10. Como aplicamos — Uma issue por lacuna**

Cada lacuna virou uma issue, na ordem da pirâmide: primeiro medir, depois as regras, depois a verificação. E definimos o que é chegar lá: passar trinta dias seguidos com no máximo um por cento dos PRs deixando um defeito escapar.

Na tela: `#645 medir` · `#646 #647 #648 #649 regras` · `#650 #651 #652 verificação` · `meta: 30 dias com até 1% de escape`

**11. O levantamento — O que é defeito que escapa**

Primeiro, medir. Mas medir o quê? Um defeito que escapa é assim: um PR entra na main, e dias depois outro PR precisa corrigir algo que o primeiro quebrou. O primeiro deixou o defeito passar pela revisão e pelos testes.

Na tela: `PR A entra na main` · `PR B corrige algo que A quebrou` · `A deixou o defeito escapar`

**12. O levantamento — Da correção até a origem**

Um script percorreu trinta e três dias de PRs. Para cada correção, ele procura a origem de duas formas: o número de PR citado no texto da correção, como neste trecho, ou o blame das linhas que ela mudou. Cada caso foi conferido à mão antes de contar.

Código na tela: `tools/ci/escaped-defects.mjs:71-80`

Na tela: `origem: número citado ou blame` · `cada caso conferido à mão`

**13. O levantamento — 11 de 71 PRs**

O resultado: onze de setenta e um PRs de produto deixaram um defeito escapar. Só um deles virou revert, então contar reverts teria escondido quase tudo. E parte dos escapes estava justamente na janela da HUD, aquela do começo.

Na tela: `15,5% dos PRs de produto` · `só 1 virou revert` · `janela da HUD: 3 de 16 PRs`

**14. Camada 1 · regras — Regra em texto vira teste**

Com o número na mão, atacamos a base. Regras que viviam só no CLAUDE.md viraram testes. Este proíbe o AlertDialog e aponta o diálogo do próprio app. E o teste de baixo prova que o padrão pega o uso real e ignora um comentário.

Código na tela: `src/desktopTest/kotlin/com/usagemonitor/architecture/ProseArchitectureRulesTest.kt:16-29`

Na tela: `10 regras do CLAUDE.md viraram teste`

**15. Camada 1 · regras — O cancelamento não some**

Uma regra nova olhou para corrotinas. Quando uma tarefa do Kotlin é cancelada, ela precisa parar. Mas um catch genérico engole o cancelamento, e a tarefa segue viva. Aqui, o cancelamento é relançado antes do catch genérico. A regra achou sete pontos com o problema, todos corrigidos.

Código na tela: `src/commonMain/kotlin/com/usagemonitor/data/repository/SuspendRunCatching.kt:12-20`

Na tela: `7 pontos corrigidos`

**16. Camada 1 · regras — Uma catraca que só desce**

Comentário demais também é prosa: era mais de um quinto do Kotlin de produção. Agora um teste conta os blocos longos e reprova se o número subir. O teto só desce. E o build do Linux, que só avisava, passou a bloquear o merge.

Código na tela: `src/desktopTest/kotlin/com/usagemonitor/architecture/ArchitectureRulesTest.kt:617-627`

Na tela: `Linux bloqueia o merge`

**17. Camada 2 · verificação — Entradas sorteadas**

No meio da pirâmide, começamos pela geometria da HUD: onde a janela fica em cada borda, em cada monitor. Em vez de três exemplos, um gerador sorteia cenários: escala, tamanho e posição da tela, número de contas. A semente é fixa, então uma falha se repete igual.

Código na tela: `src/desktopTest/kotlin/com/usagemonitor/HudPropertySupport.kt:105-116`

Na tela: `semente fixa: a falha se repete`

**18. Camada 2 · verificação — Uma regra para todo caso**

E o teste cobra uma regra que vale para todo cenário: onde quer que a janela solta pare, ela continua inteira dentro da área útil da tela. Para saber se isso pega erro, quebramos o código dez vezes, de propósito. O teste pegou as dez.

Código na tela: `src/desktopTest/kotlin/com/usagemonitor/HudGeometryPropertyTest.kt:125-133`

Na tela: `10 de 10 mutações pegas`

**19. O desafio — O que o teste de tela vê**

Então veio o maior desafio. O teste de tela do Compose desenha a interface numa cena fora da tela. A imagem do detalhe, lá do começo, foi desenhada assim. O teste confere textos, botões e estados. Mas é só o desenho. Não existe janela de verdade ali.

Imagem: `img/hud-detalhe.png`

Na tela: `cena fora da tela: 1024 × 768`

**20. O desafio — O que ele não vê**

E a janela de verdade é onde a HUD mora. A posição no monitor, o recorte do notch, o instante em que ela aparece: nada disso existe no teste de tela. Foi exatamente ali que o levantamento achou defeitos escapando.

Na tela: `a janela nativa do sistema` · `a posição no monitor` · `o recorte do notch` · `o instante em que a janela aparece`

**21. O desafio — Testar o app como ele é**

A saída foi testar o produto como o usuário recebe. O CI roda o instalador real, abre o app com uma conta falsa e sem rede, e grava a tela. Este é um quadro dessa gravação: a HUD recolhida, no topo de uma tela vazia.

Imagem: `img/app-real-ci.jpg`

Na tela: `instalador real, conta falsa, sem rede` · `um quadro a cada ~50 ms`

**22. O desafio — Um defeito plantado**

Para provar que a gravação enxerga defeito, plantamos defeitos conhecidos. Em cima, o app certo. Embaixo, a HUD deslocada quarenta pixels. Um script mede a posição quadro a quadro, e reprovou. Em todas as provas, acertou todos os vereditos, sem alarme falso.

Imagem: `img/notch-shift.jpg`

Na tela: `esperado x 394, medido x 434` · `155/155 vereditos exatos` · `270 gravações sem alarme falso`

**23. O desafio — O flash no Windows**

A gravação também transformou um defeito antigo em número. No Windows, a janela pré-aquecida piscava na tela em trinta e nove de novecentas e sessenta partidas. A correção esconde a janela no compositor do sistema antes de mostrá-la. Depois dela: zero em novecentas e sessenta.

Código na tela: `src/desktopMain/kotlin/com/usagemonitor/presentation/ui/WindowsDwmCloak.kt:36-44`

Na tela: `39 de 960 partidas piscavam` · `depois: 0 em 960`

**24. Mutação — Os testes olham algo?**

Faltava a pergunta mais dura: os testes olham alguma coisa? O teste de mutação responde. Um script muda um detalhe do código, um operador, uma constante, uma condição, e roda os testes. Se nenhum reprovar, achamos um furo. E cada furo vira um teste novo.

Na tela: `muda um operador, constante ou condição` · `nenhum teste reprovou = furo` · `cada furo vira um teste`

**25. Mutação — Nas ferramentas da gravação**

Um exemplo, nas ferramentas que julgam a gravação: trocamos o limiar que detecta um pulso da janela, de um e meio para dois e meio. Nenhum teste percebeu. Depois de fechar os furos, a parte dos mutantes pegos subiu de dois terços para quase todos.

Na tela: `limiar de pulso 1,5 → 2,5` · `nenhum teste percebeu` · `mutantes mortos 67,1% → 96,0%`

**26. Mutação — Cobertura não é prova**

Na suíte de telas do Compose, os arquivos de risco tinham 95% das linhas cobertas. Mesmo assim, os testes pegavam só 54,5% dos defeitos plantados. Os agentes fecharam 110 de 128 furos, e os pegos subiram para 69,5%. A cobertura de linhas quase não se mexeu.

Na tela: `95% das linhas cobertas` · `mutantes mortos 54,5% → 69,5%` · `linhas cobertas 95,39% → 95,40%` · `110 de 128 furos fechados`

**27. O que deu errado — Relato não é prova**

Nem tudo saiu certo. Um registro salvo dizia que os testes novos pegavam 4 de 24 defeitos. Repetimos a medida: eram 14. E uma rodada longa, cortada quando a máquina desligou, deixou 20 vereditos falsos. Foram apagados e refeitos.

Na tela: `registro salvo: 4 de 24` · `medida repetida: 14 de 24` · `20 vereditos falsos, refeitos`

**28. O que aprendemos — Três lições**

O que mudou: o Linux bloqueia o merge, regras viraram testes, a janela real é gravada, o flash sumiu, e os testes pegam mais defeitos. O que aprendemos: medir antes de mudar. Linha executada não é comportamento verificado. E nenhum relato de agente vale sem conferência.

Na tela: `medir antes de mudar` · `linha executada ≠ comportamento verificado` · `conferir todo relato de agente`

**29. Ainda não chegamos — Confiança se constrói**

Ainda não chegamos. Falta passar trinta dias com no máximo um por cento de escape, e isso só o tempo mede. O Usage Monitor é código aberto. A confiança no agente não vem do modelo. Ela se constrói no ambiente em que ele trabalha.

### EN

**1. The app — This is Usage Monitor**

This is Usage Monitor. It is a desktop app that sits in a bar at the top of the screen, the HUD, and shows how much of each AI assistant quota you have used, like Claude and Codex.

Imagem: `img/hud-barra.png`

Na tela: `the HUD: a bar at the top of the screen`

**2. The app — One click opens the detail**

One click on a ring opens the account detail: the five-hour session, the weekly limit and when each one resets. It is a small frameless window drawn over your desktop. Keep that window in mind: it comes back later.

Imagem: `img/hud-detalhe.png`

Na tela: `5 h session and weekly limit`

**3. Why — Written by AI agents**

Here is the detail that matters: almost all of this app code is written by AI agents. A request becomes an issue, the issue becomes a plan, the agent writes the test, then the code, and opens the PR. The merge happens on its own when CI goes green.

Na tela: `issue → plan → test → code → PR` · `merges itself when CI is green`

**4. Why — Can we trust it?**

Then we watched the talk All Code Is Slop, Except Mine, by Lauren Tan. Her question is blunt: if nobody reviews every line, why trust that code? A green test says the test passed. It does not say it looks at the right thing. We opened an issue to answer with numbers.

Na tela: `All Code Is Slop (Except Mine)` · `Lauren Tan · AI Engineer` · `issue #644`

**5. Before — What already worked**

The starting point was not bad. Every request went through the same flow, tests came before code, and a ratchet kept line coverage from dropping below ninety-five percent.

Na tela: `one flow for every request` · `test before code` · `95% of lines covered`

**6. Before — What nobody knew**

But there were holes. Many project rules lived only as text, in CLAUDE.md, and depended on someone remembering. The Linux build could fail and only warn. And the simplest question had no answer: how many defects got past review and reached main?

Na tela: `rules only as text` · `Linux only warned` · `escaped defects: never measured`

**7. The talk — First, what is deterministic**

The talk proposes an order. At the base, what is deterministic: architecture rules a machine checks on every PR, without depending on anyone. If a rule can become a test, it should not live only in a document.

Na tela: `1. rules a machine enforces` · `2. verification` · `3. prose, last`

**8. The talk — Then, verification**

In the middle, verification. Three hand-picked examples are not enough: a property test enforces a rule for any input. And you need to see the product actually running, the way users get it.

Na tela: `a rule that holds for any input` · `the product actually running` · `breaking the code on purpose`

**9. The talk — Last, prose**

At the top, and last, prose: written instructions for the agent. It helps, but the agent can ignore it or misread it. In our app the order was upside down: lots of prose, and almost nothing in the middle of the pyramid.

Na tela: `text: the agent can ignore it` · `21.5% of Kotlin was comments`

**10. How we applied it — One issue per gap**

Each gap became an issue, in pyramid order: first measure, then rules, then verification. And we defined what arriving means: thirty days in a row with at most one percent of PRs letting a defect escape.

Na tela: `#645 measure` · `#646 #647 #648 #649 rules` · `#650 #651 #652 verification` · `goal: 30 days with at most 1% escaping`

**11. The survey — What an escaped defect is**

First, measure. But measure what? An escaped defect looks like this: a PR reaches main, and days later another PR has to fix something the first one broke. The first one let the defect slip past review and tests.

Na tela: `PR A reaches main` · `PR B fixes something A broke` · `A let the defect escape`

**12. The survey — From the fix to its origin**

A script went through thirty-three days of PRs. For each fix, it looks for the origin in two ways: the PR number cited in the fix text, as in this excerpt, or the blame of the lines it changed. Every case was checked by hand before it counted.

Código na tela: `tools/ci/escaped-defects.mjs:71-80`

Na tela: `origin: cited number or blame` · `every case checked by hand`

**13. The survey — 11 of 71 PRs**

The result: eleven of seventy-one product PRs let a defect escape. Only one became a revert, so counting reverts would have hidden almost everything. And some of the escapes were right in the HUD window, the one from the start.

Na tela: `15.5% of product PRs` · `only 1 became a revert` · `HUD window: 3 of 16 PRs`

**14. Layer 1 · rules — A text rule becomes a test**

With the number in hand, we went after the base. Rules that lived only in CLAUDE.md became tests. This one forbids AlertDialog and points to the app own dialog. And the test below proves the pattern catches real use and ignores a comment.

Código na tela: `src/desktopTest/kotlin/com/usagemonitor/architecture/ProseArchitectureRulesTest.kt:16-29`

Na tela: `10 CLAUDE.md rules became tests`

**15. Layer 1 · rules — Cancellation is not lost**

A new rule looked at coroutines. When a Kotlin task is cancelled, it has to stop. But a generic catch swallows the cancellation, and the task keeps running. Here, the cancellation is rethrown before the generic catch. The rule found seven places with the problem, all fixed.

Código na tela: `src/commonMain/kotlin/com/usagemonitor/data/repository/SuspendRunCatching.kt:12-20`

Na tela: `7 places fixed`

**16. Layer 1 · rules — A ratchet that only goes down**

Too many comments is prose too: it was more than a fifth of production Kotlin. Now a test counts the long blocks and fails if the number goes up. The ceiling only goes down. And the Linux build, which only warned, started blocking the merge.

Código na tela: `src/desktopTest/kotlin/com/usagemonitor/architecture/ArchitectureRulesTest.kt:617-627`

Na tela: `Linux blocks the merge`

**17. Layer 2 · verification — Random inputs**

In the middle of the pyramid, we started with the HUD geometry: where the window sits on each edge, on each monitor. Instead of three examples, a generator draws scenarios: scale, screen size and position, number of accounts. The seed is fixed, so a failure repeats exactly.

Código na tela: `src/desktopTest/kotlin/com/usagemonitor/HudPropertySupport.kt:105-116`

Na tela: `fixed seed: a failure repeats`

**18. Layer 2 · verification — One rule for every case**

And the test enforces a rule that holds for every scenario: wherever the floating window stops, it stays fully inside the screen work area. To see whether it catches mistakes, we broke the code ten times, on purpose. The test caught all ten.

Código na tela: `src/desktopTest/kotlin/com/usagemonitor/HudGeometryPropertyTest.kt:125-133`

Na tela: `10 of 10 mutations caught`

**19. The challenge — What a screen test sees**

Then came the hardest part. A Compose screen test draws the interface in an offscreen scene. The detail image from the start was drawn that way. The test checks text, buttons and states. But it is only the drawing. There is no real window there.

Imagem: `img/hud-detalhe.png`

Na tela: `offscreen scene: 1024 × 768`

**20. The challenge — What it misses**

And the real window is where the HUD lives. Its position on the monitor, the notch cutout, the moment it appears: none of that exists in a screen test. That is exactly where the survey found defects escaping.

Na tela: `the native system window` · `its position on the monitor` · `the notch cutout` · `the moment the window appears`

**21. The challenge — Test the app as it ships**

The way out was to test the product the way users get it. CI runs the real installer, opens the app with a fake account and no network, and records the screen. This is a frame from that recording: the HUD collapsed at the top of an empty screen.

Imagem: `img/app-real-ci.jpg`

Na tela: `real installer, fake account, no network` · `one frame every ~50 ms`

**22. The challenge — A planted defect**

To prove the recording sees defects, we planted known ones. On top, the correct app. Below, the HUD shifted forty pixels. A script measures the position frame by frame, and it failed. Across every trial, it got every verdict right, with no false alarm.

Imagem: `img/notch-shift.jpg`

Na tela: `expected x 394, measured x 434` · `155/155 exact verdicts` · `270 recordings, no false alarm`

**23. The challenge — The Windows flash**

The recording also turned an old defect into a number. On Windows, the prewarmed window flashed on screen in thirty-nine of nine hundred sixty starts. The fix hides the window in the system compositor before showing it. After it: zero in nine hundred sixty.

Código na tela: `src/desktopMain/kotlin/com/usagemonitor/presentation/ui/WindowsDwmCloak.kt:36-44`

Na tela: `39 of 960 starts flashed` · `after: 0 in 960`

**24. Mutation — Do tests look at anything?**

The hardest question was left: do the tests look at anything? Mutation testing answers it. A script changes one detail of the code, an operator, a constant, a condition, and runs the tests. If none fails, we found a gap. And every gap becomes a new test.

Na tela: `change an operator, constant or condition` · `no test failed = a gap` · `every gap becomes a test`

**25. Mutation — In the recording tools**

One example, in the tools that judge the recording: we changed the threshold that detects a window pulse from one and a half to two and a half. No test noticed. After closing the gaps, the share of caught mutants rose from two thirds to almost all.

Na tela: `pulse threshold 1.5 → 2.5` · `no test noticed` · `killed mutants 67.1% → 96.0%`

**26. Mutation — Coverage is not proof**

In the Compose screen suite, the risky files had 95% of lines covered. Even so, the tests caught only 54.5% of planted defects. The agents closed 110 of 128 gaps, and caught defects rose to 69.5%. Line coverage barely moved.

Na tela: `95% of lines covered` · `killed mutants 54.5% → 69.5%` · `lines covered 95.39% → 95.40%` · `110 of 128 gaps closed`

**27. What went wrong — A report is not proof**

Not everything went right. A saved record said the new tests caught 4 of 24 defects. We measured again: it was 14. And a long run, cut off when the machine shut down, left 20 false verdicts. They were wiped and redone.

Na tela: `saved record: 4 of 24` · `measured again: 14 of 24` · `20 false verdicts, redone`

**28. What we learned — Three lessons**

What changed: Linux blocks the merge, rules became tests, the real window is recorded, the flash is gone, and the tests catch more defects. What we learned: measure before changing. An executed line is not verified behavior. And no agent report counts without a check.

Na tela: `measure before changing` · `executed line ≠ verified behavior` · `check every agent report`

**29. Not there yet — Trust is built**

We are not there yet. We still need thirty days with at most one percent escaping, and only time can measure that. Usage Monitor is open source. Trust in agents does not come from the model. It is built into the environment they work in.

## Curto (60 s, 9:16): TikTok e Shorts PT, igual à #684

**Dá para confiar?**: Uma palestra nos perguntou: dá para confiar no código que agentes de IA escrevem?

**11 de 71 PRs**: Primeiro medimos: 15,5% dos PRs de produto deixaram um defeito escapar.

**Regra em texto vira teste**: Depois, regras escritas em texto viraram teste, e o Linux passou a bloquear o merge.

**Testar o app como ele é**: O app real passou a abrir no CI, gravado. A gravação reprova a versão com defeito.

**Cobertura não é prova**: Então quebramos o código de propósito. Os defeitos pegos subiram de 54,5 para 69,5%, e a cobertura de linhas nem se mexeu.

**Três lições**: A lição: linha executada não é comportamento verificado.

**Confiança se constrói**: Usage Monitor. Confiança no agente se constrói no ambiente.

## Fatos

- issue #644, aberta em 2026-11-24; #679 fechada em 2026-11-26
- #645: 15,5% dos PRs de produto, 11 de 71, com defeito que escapou
- #646: 193 identificadores do CLAUDE.md conferidos, nenhum ausente
- #647: 23.426 linhas de comentário, 21,5% do Kotlin de produção
- #648: regra de cancelamento acusou 7 pontos em 7 arquivos
- #649: desktop-linux em 150 runs: 128 verdes, 3 vermelhos
- #650: testes de propriedade da HUD, 10/10 mutações mortas
- #652: app real no CI separou a versão com defeito em 15 de 15 gravações
- #657: 155/155 vereditos exatos
- #669: flash da janela no arranque, 4,1% (39/960) para 0 em 960 partidas
- #676: mutantes mortos nos testes de tela, 67,1% para 96,0% (309/322)
- #679: suíte Compose, 54,5% para 69,5% (402 para 512 de 737); nunca visto 61,4% para 70,4%; 110 de 128 furos
- #679: cobertura de linhas 95,39% para 95,40%; testes 4812 para 4898; 23% dos vivos no lote nunca visto (45 de 193)
- #513: 18.792 para 6.077 tokens de entrada por chamada de agente, -68%
- #644: meta de 30 dias com até 1% de PRs com defeito que escapa
- #679: registro salvo do replay T6 dizia 4 de 24; a repetição mostrou 14 de 24
- #679: 15 arquivos de risco, 95% das linhas cobertas
- #645: janela de 33 dias (2026-10-22 a 2026-11-23); origem por citação ou blame; 1 das 11 origens virou revert
- #645: janela da HUD (hud-window) 3 de 16 PRs, 18,8%
- #646: 10 regras (R1 a R10), exemplo que reprova e que passa
- meta de 95% de linhas (CLAUDE.md, cobertura com catraca)
- compose-implementation.md: cena do teste de componente 1024 × 768
- #651: screen-diff, antes e depois das telas do PR
- #652: gravação a cada ~50 ms, conta sintética, sem rede
- #657: 9 defeitos plantados; 270 gravações sem alarme falso (270/270)
- #669: 39 de 960 partidas (4,1%); prova 0 em 960
- #679: rodada interrompida no desligamento: 8 error e 12 noise, 20 vereditos falsos, zerados e refeitos
- #676: defeito plantado hud-misplaced, mediana de x 434 (esperado 394 ± 2 px), deslocamento de 40 px
- #676: mutante sobrevivente, limiar de pulso 1,5 → 2,5 (pulse threshold 1.5 → 2.5)
- #644: abertura; 29 beats no corte de 10 min (#686)
