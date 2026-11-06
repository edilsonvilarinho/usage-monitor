# Roteiro dos vídeos do fluxo agêntico (#540)

Escolha do dono: modelo 01 Horizonte de eventos, voz PT Matilda, voz EN George, trilha Pulso de sonda.

Fonte de cada afirmação: `.claude/skills/usage-monitor-workflow/SKILL.md`, seção "Fluxo de trabalho" do `CLAUDE.md`, issue #537 e PR #539.
Números medidos em 2026-11-06. Em tela, cada etapa mostra o artefato real indicado na coluna "Em tela".

## Versão de 60 s (TikTok, Shorts, LinkedIn em PT; X em EN)

Medido com a voz escolhida: PT 629 caracteres e 51,8 s de fala; EN 644 caracteres e 51,9 s. Com 0,8 s entre falas, fecha em cerca de 59 s.

| # | Etapa | PT | EN | Em tela |
|---|---|---|---|---|
| 1 | Pedido | Tudo começa com um pedido. Daí em diante, nenhum passo espera aprovação. | It starts with one request. From there, no step waits for approval. | pedido em linguagem comum |
| 2 | Issue e planos | A skill cria a issue e escreve dois planos: um curto e um detalhado. | The skill opens the issue and writes two plans: one short, one detailed. | `gh issue create`, `docs/planos/` |
| 3 | Agentes | Cada tarefa ganha sua worktree. A orquestradora valida, os agentes implementam. | Each task gets its own worktree. The orchestrator validates, agents implement. | `git worktree add`, 5 agentes |
| 4 | TDD | TDD: o teste falha na asserção, depois fica verde, e tudo vai para o plano. | TDD: the test fails on the assertion, then goes green, all recorded in the plan. | linha `expected … but was …` real |
| 5 | PR | Testes locais e cobertura do diff. O pull request já abre com merge automático. | Local tests and diff coverage. The pull request opens with auto-merge on. | 212/212, `gh pr merge --auto` |
| 6 | CI | O CI roda só o que mudou, em paralelo. Um check decide: ci-ok. | CI runs only what changed, in parallel. One check decides: ci-ok. | 6 jobs reais do run 42230027000 |
| 7 | Parada | Falhou? O agente corrige. Na quarta falha, para e entrega o diagnóstico. | A failure? The agent fixes it. On the fourth, it stops and hands over the diagnosis. | contador de incidências |
| 8 | Comentário | Ao mesclar, a issue recebe os testes criados, do vermelho ao verde. | After the merge, the issue gets every test created, from red to green. | comentário real da #537 |
| 9 | Fim | Usage Monitor. Do pedido ao merge, com agentes de IA. | Usage Monitor. From request to merge, with AI agents. | lockup e URL |

## Versão de 5 minutos (YouTube, PT e EN)

### Abertura

**PT:** Este é o fluxo de desenvolvimento do Usage Monitor, um aplicativo de código aberto construído com agentes de inteligência artificial. Até agora, são duzentos e trinta e cinco pull requests mesclados. Vamos acompanhar um pedido de ponta a ponta, sem pular nenhuma etapa.

**EN:** This is the development workflow behind Usage Monitor, an open source app built with AI agents. So far, that adds up to two hundred and thirty-five merged pull requests. Let's follow one request from start to finish, without skipping a single step.

### 1. O pedido

**PT:** Tudo começa com um pedido em linguagem comum: ajusta, corrige, adiciona. A regra está no próprio repositório. Qualquer pedido que altera um arquivo versionado dispara o fluxo inteiro, sem ninguém precisar citá-lo, e nenhum passo espera aprovação.

**EN:** It all starts with a plain request: tweak this, fix that, add something. The rule lives in the repository itself. Any request that changes a versioned file triggers the whole workflow, nobody has to mention it, and no step waits for approval.

### 2. A skill

**PT:** Quem conduz é a skill usage-monitor-workflow. O arquivo CLAUDE.md define a regra. A skill é o roteiro executável, com os comandos de cada passo.

**EN:** The usage-monitor-workflow skill runs the show. The CLAUDE.md file sets the rule. The skill is the executable script, with the commands for every step.

### 3. Issue e dois planos

**PT:** Primeiro, a issue. O plano entra nela em no máximo quinze linhas: o quê, atividades, onde, como validar e o que fica de fora. O plano detalhado vai para a pasta docs planos, com os fatos confirmados, o comando que mediu cada um, as decisões e uma tabela de situação.

**EN:** First, the issue. The plan goes in it, in fifteen lines at most: what, activities, where, how to validate, and what stays out. The detailed plan goes in the docs folder, with confirmed facts, the command that measured each one, the decisions, and a status table.

### 4. Worktree

**PT:** Depois, isolamento. Cada trabalho nasce numa worktree e numa branch próprias, a partir da main remota. Nada é commitado na main: o ruleset do GitHub recusaria o push. E a regra é simples: uma atividade, um commit.

**EN:** Next, isolation. Every job starts in its own worktree and branch, created from the remote main. Nothing is committed to main: the GitHub ruleset would reject the push. And the rule is simple: one activity, one commit.

### 5. Os agentes

**PT:** Aqui entram os agentes. A sessão principal é a orquestradora: ela planeja, decide, valida e faz os commits. O explorer varre o código. Os agentes de backend, frontend e protótipo implementam. O test runner roda as suítes e acompanha o CI. Cada um recebe um briefing curto, nunca a conversa inteira.

**EN:** This is where the agents come in. The main session is the orchestrator: it plans, decides, validates and commits. The explorer searches the code. The backend, frontend and prototype agents implement. The test runner runs the suites and watches CI. Each one gets a short briefing, never the whole conversation.

### 6. Validação

**PT:** Nenhuma entrega é aceita pelo relatório do agente. A orquestradora confere cada critério com evidência própria: diff, execução, captura de tela. Se reprova, a tarefa volta ao mesmo agente, com o defeito apontado. Na terceira reprovação, ela sobe para um modelo mais forte.

**EN:** No delivery is accepted on the agent's own report. The orchestrator checks every criterion with its own evidence: a diff, a run, a screenshot. A rejected task goes back to the same agent, with the defect spelled out. On the third rejection, it moves up to a stronger model.

### 7. TDD

**PT:** O desenvolvimento é em TDD. Primeiro o teste, e ele precisa falhar na asserção, pelo motivo esperado. Erro de compilação não conta como vermelho. Depois, o código mínimo até ficar verde, e só então a refatoração. A linha do plano registra a execução vermelha e a verde. Entrega sem isso é devolvida.

**EN:** Development follows TDD. The test comes first, and it has to fail on the assertion, for the expected reason. A compile error doesn't count as red. Then the least code that turns it green, and only then the refactor. The plan records both the red run and the green run. A delivery without them goes back.

### 8. Testes locais

**PT:** Antes do pull request, o test runner roda o recorte do que mudou, gera o relatório de testes e mede a cobertura do diff: as linhas que o PR altera, com piso de noventa por cento.

**EN:** Before the pull request, the test runner runs the slice that changed, builds the test report, and measures diff coverage: the lines the PR touches, with a ninety percent floor.

### 9. Pull request

**PT:** O pull request abre dizendo em que plataformas foi testado, e já com o merge automático ligado. Ninguém clica em mesclar: o GitHub mescla sozinho quando o check exigido passa.

**EN:** The pull request opens stating which platforms it was tested on, with auto-merge already on. Nobody clicks merge: GitHub merges on its own once the required check passes.

### 10. Integração contínua

**PT:** No CI, um job chamado changes recorta por caminho, e só o que mudou roda, em paralelo. Tudo converge num único check, o ci-ok, exigido pelo ruleset da main. A cobertura de linhas tem catraca: o piso só sobe, com meta de noventa e cinco por cento.

**EN:** In CI, a job called changes filters by path, and only what changed runs, in parallel. Everything converges on a single check, ci-ok, required by the main ruleset. Line coverage has a ratchet: the floor only goes up, toward a ninety-five percent target.

### 11. Parada de segurança

**PT:** E se falhar? O agente reproduz a falha com um teste e corrige. Um script conta as incidências, nunca a memória. Na quarta, o merge automático é desligado, ninguém mexe mais na branch, e o usuário recebe o diagnóstico de cada falha. Autonomia, com limite.

**EN:** And if it fails? The agent reproduces the failure with a test and fixes it. A script counts the incidents, never memory. On the fourth, auto-merge is turned off, the branch is left alone, and the user gets a diagnosis of every failure. Autonomy, with a limit.

### 12. Merge e comentário na issue

**PT:** Com o ci-ok verde, o merge acontece. O agente limpa a worktree, atualiza a main local e faz a parte que vira documentação: um comentário na issue com os testes criados. O que cada caso afirma, o vermelho e o verde, os totais, a cobertura do diff, e o que ficou sem teste, e por quê.

**EN:** With ci-ok green, the merge happens. The agent cleans up the worktree, updates the local main, and writes the part that becomes documentation: a comment on the issue with the tests created. What each case asserts, red and green, the totals, diff coverage, and what stayed untested, and why.

### 13. Um caso real

**PT:** Um caso real: a issue quinhentos e trinta e sete. Vinte e quatro testes novos. Três mil novecentos e sessenta e um testes na suíte, zero falhas. Duzentas e doze de duzentas e doze linhas cobertas no diff. E zero incidências.

**EN:** A real case: issue five thirty-seven. Twenty-four new tests. Three thousand nine hundred and sixty-one tests in the suite, zero failures. Two hundred and twelve of two hundred and twelve diff lines covered. And zero incidents.

### Fechamento

**PT:** Usage Monitor. Do pedido ao merge, com agentes de IA, e cada decisão documentada.

**EN:** Usage Monitor. From request to merge, with AI agents, and every decision documented.
