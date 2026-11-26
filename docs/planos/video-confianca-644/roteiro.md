# Roteiro — jornada de confiança v2 (#684, #644)

Gerado do `video.json`. Os números têm origem na lista `facts`. A fala conta a história e a tela mostra os números.

## Longo (5 min, 16:9): LinkedIn PT e X EN

### PT

**1. Motivação — Dá para confiar no agente?**

O Usage Monitor é escrito por agentes de IA, do pedido ao merge. Mas a palestra All Code Is Slop, Except Mine, de Lauren Tan, nos fez uma pergunta incômoda: dá para confiar nesse código? Teste verde não era resposta. Então fomos medir.

Na tela: `All Code Is Slop (Except Mine)` · `Lauren Tan · AI Engineer` · `issue #644`

**2. Antes — Como o app era feito**

O ponto de partida já era automatizado. Cada pedido virava issue, plano e teste escrito antes do código, e a cobertura de linhas tinha piso alto. Mas muitas regras viviam só em texto, o Linux apenas avisava, e ninguém sabia quantos defeitos passavam pela revisão.

Na tela: `issue → plano → teste → PR` · `95% das linhas cobertas` · `Linux só avisava` · `regras só em texto`

**3. A palestra — Pirâmide invertida**

A palestra propõe uma ordem para a confiança. Primeiro, o determinístico: arquitetura e regras que uma máquina cobra. Depois, verificação: propriedades e o app rodando de verdade. Por último, prosa. No nosso app faltava o meio: nenhum teste de propriedade, e a janela real só era vista à mão.

Na tela: `1. arquitetura e regras que a máquina cobra` · `2. verificação: propriedade, app real` · `3. por último, prosa` · `21,5% do Kotlin era comentário`

**4. Como aplicar — Cada lacuna virou uma issue**

Cada lacuna virou uma issue, na ordem da pirâmide: medir, depois regras, depois verificação. E definimos o que é chegar lá: 30 dias com no máximo um por cento de PRs com defeito que escapa.

Na tela: `#645 medir` · `#646 #647 #648 #649 regras` · `#650 #651 #652 verificação` · `meta: 30 dias com até 1% de escape`

**5. Levantamento — Medir antes de mudar**

Primeiro, medimos. Um script percorreu 33 dias de PRs e ligou cada correção ao PR que criou o defeito, pela citação ou pelo blame da linha. Cada caso foi conferido à mão. Resultado: 11 de 71 PRs de produto deixaram defeito escapar. Só um virou revert.

Na tela: `fix → PR de origem: citação ou blame` · `veredito conferido à mão` · `11 de 71 PRs de produto · 15,5%` · `só 1 virou revert`

**6. Camada 1 — Regra em prosa vira teste**

Com o número na mão, atacamos a base. Regras que só existiam no CLAUDE.md viraram teste. O Linux passou a bloquear o merge, uma catraca impede o comentário de crescer, e uma regra nova achou cancelamento de corrotina engolido em 7 pontos.

Na tela: `ProseArchitectureRulesTest` · `desktop-linux bloqueia o merge` · `catraca: comentário só desce` · `CancellationArchitectureRulesTest` · `7 pontos corrigidos`

**7. Camada 2 — Teste de propriedade**

Na verificação, começamos pela geometria do notch da HUD. Em vez de exemplos fixos, um gerador sorteia entradas, e o teste cobra regras que valem para qualquer caso. Quebramos o código dez vezes. O teste pegou as dez.

Na tela: `HudGeometryPropertyTest` · `Random(seed): a falha se repete` · `10 de 10 mutações pegas`

**8. O desafio — Teste de tela não vê a janela**

Então veio o maior desafio. O teste de tela do Compose desenha fora da tela. Ele não vê a janela nativa: o recorte do notch, o instante em que ela aparece. E o levantamento achou defeito escapando justamente na janela da HUD.

Na tela: `cena fora da tela: 1024 × 768` · `janela nativa: invisível ao teste` · `janela da HUD: 3 de 16 PRs · 18,8%`

**9. Camada 2 — O app real no CI**

A saída: testar o produto como o usuário recebe. O CI roda o instalador real, abre o app sem rede e grava a tela. Um script compara os pixels quadro a quadro. Com defeitos plantados, acertou todos os vereditos.

Na tela: `instalador real, conta sintética, sem rede` · `grava a tela a cada ~50 ms` · `15 de 15 gravações` · `155/155 vereditos exatos` · `270 gravações sem alarme falso`

**10. O que mudou no app — O flash que sumiu**

E um defeito antigo virou número: no Windows, a janela pré-aquecida piscava em 39 de 960 partidas. A correção esconde a janela no compositor antes de mostrá-la. Depois, em 960 partidas: zero.

Na tela: `39 de 960 partidas · 4,1%` · `janela escondida no compositor` · `0 em 960 partidas`

**11. Testar os testes — Quebrar de propósito**

Faltava a pergunta mais dura: os testes olham alguma coisa? O teste de mutação responde. Um script planta um defeito por vez, num operador ou numa condição, e roda os testes. Se nada reprova, é um furo, e o furo vira teste.

Na tela: `troca operador, constante, condição` · `recompila só o arquivo e roda` · `nada reprovou = furo` · `ferramentas de tela: 67,1% → 96,0%`

**12. Testar os testes — Cobertura não é prova**

Na suíte Compose, com 95% das linhas cobertas, os testes pegavam só 54,5% dos defeitos. Os agentes fecharam 110 de 128 furos, e os pegos subiram para 69,5%. A cobertura de linhas quase não se mexeu.

Na tela: `mutantes mortos 54,5% → 69,5%` · `linhas cobertas 95,39% → 95,40%` · `110 de 128 furos fechados` · `nunca visto: 61,4% → 70,4%`

**13. O que deu errado — Relato não é prova**

Nem tudo saiu certo. Um registro salvo dizia que os testes novos pegavam 4 de 24 defeitos. Repetida a medida, eram 14. E uma rodada longa, cortada quando a máquina desligou, deixou 20 vereditos falsos.

Na tela: `registro salvo: 4 de 24` · `repetido: 14 de 24` · `20 vereditos falsos, refeitos`

**14. O que aprendemos — Verde não é prova**

O que melhorou: o Linux bloqueia o merge, a janela real é gravada, o flash sumiu, e os testes pegam mais defeito. O que aprendemos: medir antes de mudar. Linha executada não é comportamento verificado. E nenhum relato de agente vale sem conferência.

Na tela: `medir antes de mudar` · `linha executada ≠ verificada` · `teste contra um defeito pega pouco além` · `conferir, nunca confiar no relato`

**15. Ainda não chegamos — Confiança se constrói**

Ainda não chegamos: faltam 30 dias com no máximo um por cento de escape. Usage Monitor, código aberto. Confiança no agente não vem do modelo. Se constrói no ambiente em que ele trabalha.

### EN

**1. Why — Can we trust the agent?**

Usage Monitor is written by AI agents, from request to merge. Then a talk by Lauren Tan, All Code Is Slop, Except Mine, left us with an uncomfortable question: can we trust that code? A green test was not an answer. So we went to measure.

Na tela: `All Code Is Slop (Except Mine)` · `Lauren Tan · AI Engineer` · `issue #644`

**2. Before — How the app was built**

The starting point was already automated. Every request became an issue, a plan and a test written before the code, and line coverage had a high floor. But many rules lived only in text, Linux only warned, and nobody knew how many defects slipped past review.

Na tela: `issue → plan → test → PR` · `95% of lines covered` · `Linux only warned` · `rules only in text`

**3. The talk — Inverted pyramid**

The talk proposes an order for trust. First, what is deterministic: architecture and rules a machine enforces. Then verification: properties and the app actually running. Last, prose. Our app was missing the middle: no property tests, and the real window was only checked by hand.

Na tela: `1. architecture and machine-enforced rules` · `2. verification: properties, real app` · `3. last, prose` · `21.5% of Kotlin was comments`

**4. The plan — Every gap became an issue**

Every gap became an issue, in pyramid order: measure, then rules, then verification. And we defined what arriving means: 30 days with at most one percent of PRs letting a defect escape.

Na tela: `#645 measure` · `#646 #647 #648 #649 rules` · `#650 #651 #652 verification` · `goal: 30 days, at most 1% escaping`

**5. The survey — Measure before changing**

First, we measured. A script went through 33 days of PRs and linked each fix to the PR that caused the defect, by reference or by blaming the line. Every case was checked by hand. Result: 11 of 71 product PRs let a defect escape. Only one became a revert.

Na tela: `fix → source PR: reference or blame` · `every verdict checked by hand` · `11 of 71 product PRs · 15.5%` · `only 1 became a revert`

**6. Layer 1 — Prose rules become tests**

With the number in hand, we went after the base. Rules that only lived in CLAUDE.md became tests. Linux started blocking the merge, a ratchet stops comments from growing, and a new rule found swallowed coroutine cancellation in 7 places.

Na tela: `ProseArchitectureRulesTest` · `desktop-linux blocks the merge` · `ratchet: comments only go down` · `CancellationArchitectureRulesTest` · `7 places fixed`

**7. Layer 2 — Property tests**

For verification, we started with the HUD notch geometry. Instead of fixed examples, a generator draws inputs, and the test enforces rules that hold for any case. We broke the code ten times. The test caught all ten.

Na tela: `HudGeometryPropertyTest` · `Random(seed): failures replay` · `10 of 10 mutations caught`

**8. The challenge — Screen tests miss the window**

Then came the hardest part. A Compose screen test draws offscreen. It never sees the native window: the notch cutout, the moment it appears. And the survey found defects escaping right there, in the HUD window.

Na tela: `offscreen scene: 1024 × 768` · `native window: invisible to tests` · `HUD window: 3 of 16 PRs · 18.8%`

**9. Layer 2 — The real app in CI**

The way out: test the product the way users get it. CI runs the real installer, opens the app offline and records the screen. A script compares the pixels frame by frame. With planted defects, it got every verdict right.

Na tela: `real installer, fake account, no network` · `records the screen every ~50 ms` · `15 of 15 recordings` · `155/155 exact verdicts` · `270 recordings, no false alarm`

**10. What changed — The flash that vanished**

And an old defect became a number: on Windows, the prewarmed window flashed in 39 of 960 starts. The fix cloaks the window in the compositor before showing it. After it, over 960 starts: zero.

Na tela: `39 of 960 starts · 4.1%` · `window cloaked in the compositor` · `0 in 960 starts`

**11. Testing the tests — Break it on purpose**

The hardest question was left: do the tests look at anything? Mutation testing answers it. A script plants one defect at a time, in an operator or a condition, and runs the tests. If nothing fails, that is a gap, and the gap becomes a test.

Na tela: `swap an operator, constant, condition` · `recompile one file and run` · `nothing failed = a gap` · `screen tools: 67.1% → 96.0%`

**12. Testing the tests — Coverage is not proof**

In the Compose suite, with 95% of lines covered, the tests caught only 54.5% of defects. The agents closed 110 of 128 gaps, and caught defects rose to 69.5%. Line coverage barely moved.

Na tela: `mutants killed 54.5% → 69.5%` · `lines covered 95.39% → 95.40%` · `110 of 128 gaps closed` · `never seen: 61.4% → 70.4%`

**13. What went wrong — A report is not proof**

Not everything went right. A saved record said the new tests caught 4 of 24 defects. Rerun, it was 14. And a long run, cut off when the machine shut down, left 20 false verdicts.

Na tela: `saved record: 4 of 24` · `rerun: 14 of 24` · `20 false verdicts, redone`

**14. What we learned — Green is not proof**

What improved: Linux blocks the merge, the real window is recorded, the flash is gone, and the tests catch more defects. What we learned: measure before changing. An executed line is not verified behavior. And no agent report counts without a check.

Na tela: `measure before changing` · `executed line ≠ verified` · `a test for one defect catches little else` · `check, never trust the report`

**15. Not there yet — Trust is built**

We are not there yet: we still need 30 days with at most one percent escaping. Usage Monitor, open source. Trust in agents does not come from the model. It is built into the environment they work in.

## Curto (60 s, 9:16): TikTok e Shorts PT, igual à #682

**Dá para confiar no agente?**: Uma palestra nos perguntou: dá para confiar no código que agentes de IA escrevem?

**Medir antes de mudar**: Primeiro medimos: 15,5% dos PRs de produto deixaram um defeito escapar.

**Regra em prosa vira teste**: Depois, regras escritas em texto viraram teste, e o Linux passou a bloquear o merge.

**O app real no CI**: O app real passou a abrir no CI, gravado. A gravação reprova a versão com defeito.

**Cobertura não é prova**: Então quebramos o código de propósito. Os defeitos pegos subiram de 54,5 para 69,5%, e a cobertura de linhas nem se mexeu.

**Verde não é prova**: A lição: linha executada não é comportamento verificado.

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
