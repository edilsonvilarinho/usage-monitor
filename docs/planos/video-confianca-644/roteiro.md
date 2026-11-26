# Roteiro — jornada de confiança (#682, #644)

Gerado do `video.json`. Números com origem na lista `facts`.

## Curto (60 s): LinkedIn PT e X EN

### PT

**Dá para confiar no agente?** — Uma palestra nos perguntou: dá para confiar no código que agentes de IA escrevem?

**Medir antes de mudar** — Primeiro medimos: 15,5% dos PRs de produto deixaram um defeito escapar.

**Regra em prosa vira teste** — Depois, regras escritas em texto viraram teste, e o Linux passou a bloquear o merge.

**O app real no CI** — O app real passou a abrir no CI, gravado. A gravação reprova a versão com defeito.

**Cobertura não é prova** — Então quebramos o código de propósito. Os defeitos pegos subiram de 54,5 para 69,5%, e a cobertura de linhas nem se mexeu.

**Verde não é prova** — A lição: linha executada não é comportamento verificado.

**Confiança se constrói** — Usage Monitor. Confiança no agente se constrói no ambiente.

### EN

**Can we trust the agent?** — A talk left us with one question: can we trust the code AI agents write?

**Measure before changing** — First we measured: 15.5% of product PRs let a defect slip through.

**Prose rules become tests** — Then rules written as text became tests, and Linux started blocking the merge.

**The real app in CI** — For verification, the real app opens in CI and gets recorded. The recording fails the broken build.

**Coverage is not proof** — So we broke the code on purpose. In the Compose suite, caught defects rose from 54.5 to 69.5%. Line coverage barely moved.

**Green is not proof** — The lesson: an executed line is not verified behavior.

**Trust is built** — Usage Monitor. Trust in agents is built into the environment.

## Longo (5 min, não renderizado neste pedido)

### PT

**Dá para confiar no agente?** — O Usage Monitor é escrito por agentes de IA, do pedido ao merge. Uma palestra de Lauren Tan, All Code Is Slop, Except Mine, nos fez uma pergunta: dá para confiar nesse código? Abrimos a issue seiscentos e quarenta e quatro para medir a distância até lá.

**Pirâmide invertida** — A palestra propõe investir em confiança nesta ordem: primeiro o que é determinístico, código, arquitetura e regras que o compilador cobra. Depois, verificação. Por último, prosa: arquivos de regras e instruções para o agente. Usamos essa ordem como mapa.

**Medir antes de mudar** — Antes de mudar qualquer coisa, medimos. Um script percorreu os PRs e procurou cada defeito que passou pela revisão e voltou como correção. Resultado: 15,5% dos PRs de produto, 11 de 71. Contar reverts teria escondido isso.

**Regra em prosa vira teste** — Regra que só existe em texto o agente esquece. Cada regra do CLAUDE.md que um teste consegue verificar virou teste: ProseArchitectureRulesTest. O script conferiu 193 identificadores citados no arquivo, e nenhum estava faltando no código. O job do Linux, que antes só avisava, passou a bloquear o merge.

**Catraca e lint** — Comentário longo é onde o agente justifica gambiarra. Medimos 21,5% do Kotlin de produção em comentário e pusemos uma catraca: o teto só desce. Uma regra nova pegou cancelamento de corrotina engolido em 7 pontos do código, e todos foram corrigidos.

**O app real no CI** — Teste de componente não vê a janela de verdade. Então o CI instala o app, abre, grava a tela e compara. Na prova, a gravação separou a versão com defeito em 15 de 15 vezes, e depois deu 155 de 155 vereditos exatos. A geometria da HUD ganhou testes de propriedade, que mataram 10 de 10 mutações.

**O flash que sumiu** — Com o app real medido no CI, um defeito antigo apareceu em número: no Windows, a janela pré-aquecida piscava opaca em 4,1% das partidas. Depois da correção, zero em 960 partidas.

**Quebrar de propósito** — Teste verde não prova que o teste olha alguma coisa. Então quebramos o código de propósito, um defeito por vez, e contamos quantos os testes reprovam. Nos testes de tela, os defeitos pegos foram de 67,1% para 96%.

**Cobertura não é prova** — Na suíte Compose, em quinze arquivos de risco, os testes pegavam 54,5% dos defeitos com 95% das linhas cobertas. Escrevemos testes só onde havia furo, cada um provado pelo defeito que passa a reprovar: 110 de 128 furos fechados, e 69,5% pegos. A cobertura de linhas foi de 95,39 para 95,40%.

**Defeitos nunca vistos** — Faltava saber se os testes novos valiam além dos defeitos que os agentes viram. Num segundo lote, nunca mostrado a eles, os pegos foram de 61,4% para 70,4%. Ajuda, mas só 23% dos sobreviventes morreram: teste escrito contra um defeito pega aquele defeito, e pouco além.

**Verde não é prova** — Três lições. Medir antes de mudar. Linha executada não é comportamento verificado. E nenhum relato de agente vale sem conferência: um registro salvo dizia 4 de 24, e a repetição mostrou 14 de 24.

**Ainda não chegamos** — Ainda não chegamos. A meta é passar 30 dias com no máximo um por cento de PRs com defeito que escapa. Falta também um agente que use cada versão beta e abra issue do que achar.

**Confiança se constrói** — Usage Monitor, código aberto. Confiança no agente não vem do modelo: se constrói no ambiente em que ele trabalha.

### EN

**Can we trust the agent?** — Usage Monitor is written by AI agents, from request to merge. A talk by Lauren Tan, All Code Is Slop, Except Mine, left us with an uncomfortable question: can we trust that code? We opened issue six forty-four to measure how far we were.

**Inverted pyramid** — The talk ranks where trust comes from: first what is deterministic, code, architecture and rules a compiler enforces. Then verification. Last, prose: rule files and instructions for the agent. We used that order as our map.

**Measure before changing** — Before changing anything, we measured. A script went through the PRs and looked for every defect that passed review and came back as a fix. Result: 15.5% of product PRs, 11 of 71. Counting reverts would have hidden it.

**Prose rules become tests** — A rule that only lives in text, the agent forgets. Every rule in CLAUDE.md that a test can check became a test: ProseArchitectureRulesTest. A script checked 193 identifiers the file cites, and none were missing from the code. The Linux job, which used to only warn, now blocks the merge.

**Ratchets and lint** — Long comments are where an agent justifies a hack. We measured 21.5% of production Kotlin as comments and added a ratchet: the ceiling only goes down. A new rule caught swallowed coroutine cancellation in 7 places, and all of them were fixed.

**The real app in CI** — A component test cannot see the real window. So CI installs the app, opens it, records the screen and compares. In the trial, the recording caught the broken build 15 out of 15 times, then gave 155 of 155 exact verdicts. The HUD geometry got property tests, which killed 10 of 10 mutations.

**The flash that vanished** — With the real app measured in CI, an old defect showed up as a number: on Windows, the prewarmed window flashed opaque in 4.1% of starts. After the fix, zero in 960 starts.

**Break it on purpose** — A green test does not prove the test is looking at anything. So we broke the code on purpose, one defect at a time, and counted how many the tests caught. In the screen tests, caught defects went from 67.1% to 96%.

**Coverage is not proof** — In the Compose suite, across fifteen high-risk files, the tests caught 54.5% of defects with 95% of lines covered. We wrote tests only where there was a gap, each proven by the defect it now fails: 110 of 128 gaps closed, and 69.5% caught. Line coverage went from 95.39 to 95.40%.

**Defects never seen** — We still had to know whether the new tests worked beyond the defects the agents saw. On a second batch, never shown to them, caught defects went from 61.4% to 70.4%. It helps, but only 23% of survivors died: a test written against one defect catches that defect, and little more.

**Green is not proof** — Three lessons. Measure before changing. An executed line is not verified behavior. And no agent report counts without a check: one saved record said 4 of 24, and the rerun showed 14 of 24.

**Not there yet** — We are not there yet. The goal is 30 days with at most one percent of PRs letting a defect escape. We also still need an agent that uses every beta and files an issue for what it finds.

**Trust is built** — Usage Monitor, open source. Trust in agents does not come from the model: it is built into the environment they work in.

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
