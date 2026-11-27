# Descrições para publicação (#686, a partir da #682/#684)

Publicação é manual. Números conferidos em 2026-11-26 nos planos de `docs/planos/` (lista em `video.json`, `facts`).
Link do repositório: `https://github.com/edilsonvilarinho/usage-monitor`. A imagem do infográfico da palestra não entra no post:
é de terceiro, sem licença.

Vídeo: corte longo de cerca de 10 min, 16:9 (`confianca-10min-linkedin-pt.mp4`, 9:53; `confianca-10min-x-en.mp4`, 9:33). O X só aceita vídeo acima de 2 min 20 s em conta Premium. Capítulos do YouTube pelos tempos reais de `build/social-video/confianca-10min/long-*.js`.

## LinkedIn (PT)

Dá para confiar no código que agentes de IA escrevem?

A palestra "All Code Is Slop (Except Mine)", de Lauren Tan (canal AI Engineer), me deixou com essa pergunta. O Usage Monitor, meu projeto de código aberto, é desenvolvido por agentes de IA do pedido ao merge. Então fui medir a distância até lá, seguindo a pirâmide invertida da palestra: primeiro o que é determinístico, depois verificação, por último prosa.

A linha do tempo:

→ Medir antes de mudar: 15,5% dos PRs de produto (11 de 71) deixaram um defeito escapar. Contar reverts teria escondido isso.
→ Código e arquitetura: regras escritas no CLAUDE.md viraram teste; o build do Linux passou a bloquear o merge; uma catraca impede o comentário de crescer (era 21,5% do Kotlin de produção); uma regra nova achou cancelamento de corrotina engolido em 7 pontos, todos corrigidos.
→ Verificação com o app real: o CI instala o app, abre e grava a janela. Na prova, separou a versão com defeito em 15 de 15 gravações. Um flash antigo da janela no arranque do Windows caiu de 4,1% das partidas para 0 em 960.
→ Quebrar o código de propósito (teste de mutação): nos testes de tela, os defeitos pegos foram de 67,1% para 96,0%. Na suíte Compose, de 54,5% para 69,5%, com 110 de 128 furos fechados.

O que aprendi:

• Cobertura de linhas não mede verificação. Na suíte Compose ela foi de 95,39% para 95,40% enquanto os defeitos pegos subiam 15 pontos.
• Teste escrito contra um defeito pega aquele defeito e pouco além: num lote que os agentes nunca viram, só 23% dos sobreviventes morreram.
• Nenhum relato de agente vale sem conferência. Um registro salvo dizia 4 de 24; a repetição mostrou 14 de 24.

O vídeo (10 min) conta a jornada em ordem e mostra cada passo na tela: o app real, o código de cada teste com a linha que importa, a gravação do app no CI e o defeito plantado que ela pegou.

Ainda não chegamos. A meta é passar 30 dias com no máximo 1% de PRs com defeito que escapa.

Código: https://github.com/edilsonvilarinho/usage-monitor

#InteligenciaArtificial #EngenhariaDeSoftware #Testes #ClaudeCode #AgentesDeIA

## X (EN)

Can you trust code written by AI agents? We measured: 15.5% of our PRs let a defect slip.

Then we broke our own code on purpose. Tests caught 54.5% → 69.5% of defects, while line coverage barely moved (95.39% → 95.40%).

https://github.com/edilsonvilarinho/usage-monitor

## YouTube (PT)

Dá para confiar no código que agentes de IA escrevem? Em 10 minutos, a jornada do Usage Monitor: medir os defeitos que escapam, transformar regras em teste, testar o app real e quebrar o código de propósito.

0:00 O app
0:36 Motivação
1:19 Antes
1:57 A palestra
2:52 Como aplicamos
3:11 O levantamento
4:11 Camada 1 · regras
5:13 Camada 2 · verificação
5:54 O desafio
7:38 Mutação
8:47 O que deu errado
9:09 O que aprendemos
9:33 Ainda não chegamos

Código: https://github.com/edilsonvilarinho/usage-monitor

## YouTube (EN)

Can you trust code written by AI agents? In 10 minutes, the Usage Monitor journey: measuring escaped defects, turning rules into tests, testing the real app and breaking the code on purpose.

0:00 The app
0:33 Why
1:17 Before
1:51 The talk
2:44 How we applied it
3:02 The survey
4:00 Layer 1 · rules
5:00 Layer 2 · verification
5:42 The challenge
7:21 Mutation
8:27 What went wrong
8:47 What we learned
9:13 Not there yet

Code: https://github.com/edilsonvilarinho/usage-monitor
