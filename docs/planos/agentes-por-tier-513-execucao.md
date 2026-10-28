# Orquestradora Neferpitou e agentes por tier — issue #513

Procedência: Claude Opus 5.5 (effort high), Claude Code CLI 2.1.312 no Windows 11, 2026-10-28, branch
`chore/issue-513-agents-tier`, commits com a identidade `claude <claude@anthropic.com>`.

## Objetivo

Toda a sessão roda em Opus, inclusive trabalho longo e verboso (suíte de testes, acompanhar CI, varredura ampla) que
infla o contexto principal e consome as cotas de 5h e 7d. A sessão principal passa a ser a orquestradora **Neferpitou**
(`opus`, effort high) e delega esse trabalho a agentes de tier mais barato, sem clonar contexto para eles.

## Fatos confirmados (2026-10-28, `cf0b3c52`)

Documentação oficial (`code.claude.com/docs/en/sub-agents`, `settings`, `settings-reference`), lida nesta data:

- Frontmatter de `.claude/agents/*.md`: `model` aceita alias (`sonnet`, `opus`, `haiku`, `fable`), ID completo ou
  `inherit`; `effort` (`low`…`max`); `tools`/`disallowedTools`; `omitClaudeMd`; `maxTurns`.
- Subagente que não é fork começa do zero, mas **carrega toda a hierarquia de `CLAUDE.md`** (exceto Explore e Plan),
  a menos que `omitClaudeMd: true`. A memória automática da sessão principal não é carregada.
- Subagente cria subagente até 3 níveis por padrão; tirar `Agent` de `tools` impede.
- Fork herda a conversa inteira e vem **ligado por padrão**; `CLAUDE_CODE_FORK_SUBAGENT=0` desliga.
- Agente como sessão principal (`agent` no settings ou `--agent`): o corpo do arquivo **substitui** o system prompt
  do Claude Code, salvo corpo vazio. `omitClaudeMd` é ignorado nesse modo. `model` do agente vale para a sessão.
- `effortLevel` em settings de projeto vale para todo modelo (no settings do usuário, o Opus 5.5 ignora essa chave).

Medições locais (`claude -p --output-format json`, campo `modelUsage`):

- Subagente `haiku` que só responde `ok`, chamado de sessão `sonnet`: entrada de **18.792** tokens com `CLAUDE.md` e
  **6.077** com `omitClaudeMd: true` (−12.715, −68% por chamada). `CLAUDE.md` do projeto: 421 linhas, 31.448 bytes.
- `claude -p --agent neferpitou` com corpo vazio: modelo `claude-opus-5-5`; Bash, Edit e Agent presentes; system
  prompt do Claude Code preservado.
- `.claude/settings.json` com `"agent": "neferpitou"` e o agente temporariamente em `model: haiku`: `claude -p` sem
  flag rodou em `claude-haiku-5-5` (a chave vale no arquivo do repositório). Com `CLAUDE_CODE_FORK_SUBAGENT=0` no
  `env`, a lista de `subagent_type` não tem `fork`.

## Decisões

- **Modelos só por alias** (`opus`, `sonnet`, `haiku`): acompanham a família quando a Anthropic atualiza, sem commit.
- **Neferpitou com corpo vazio:** corpo com texto substituiria o system prompt do Claude Code. O papel de orquestradora
  e as regras de delegação ficam no `CLAUDE.md`, que a sessão principal carrega.
- **Effort em `effortLevel` do `.claude/settings.json`**, não só no frontmatter: a documentação garante a chave de
  projeto para todo modelo; o `effort` do frontmatter não tem garantia documentada para a sessão principal.
- **Subagentes com `omitClaudeMd: true`**, sem `Agent`, sem `Edit`/`Write`: o corpo de cada um é autossuficiente
  (comandos, limites, formato de retorno), porque não recebem o `CLAUDE.md`.
- **Tiers:** `test-runner` (`sonnet`, effort medium) roda testes, relatório e acompanha CI; `explorer` (`haiku`) faz
  varredura ampla só de leitura. Plano, TDD, diagnóstico e decisão ficam na Neferpitou.
- **Fonte única:** modelo e ferramentas só em `.claude/agents/`; `CLAUDE.md` e a skill apontam, não repetem.
- **Contra vazamento:** sem fork, prompt de delegação mínimo (comando, caminhos, formato), retorno de no máximo 20
  linhas, sem log cru; busca pontual continua direta (criar agente custa ~6k tokens de entrada).

## Atividades

- A01: este plano.
- A02: `.claude/agents/neferpitou.md`, `test-runner.md`, `explorer.md`.
- A03: `.claude/settings.json` (`agent`, `effortLevel`, `CLAUDE_CODE_FORK_SUBAGENT=0`).
- A04: `CLAUDE.md`, seção curta "Orquestração e agentes".
- A05: skill `usage-monitor-workflow`, tier por passo.

## Ponto de situação

Cada linha é escrita no commit da atividade que descreve, com o comando executado e o resultado.

| Atividade | Alteração | Comando e resultado |
|---|---|---|
| A01 | Plano com fatos da documentação e medições | `gh issue create` → #513; `claude -p` com sondas `probe-with`/`probe-without` → 18.792 × 6.077 tokens de entrada (`haiku`) |

## Problemas em aberto e riscos

- **Piloto não medido:** a economia nas cotas de 5h/7d só se confirma comparando 2 issues com delegação contra
  equivalentes anteriores no próprio Usage Monitor. Follow-up depois do merge.
- **`.claude/settings.json` é versionado:** quem clonar o repositório roda como Neferpitou em Opus high. Sobrepor por
  `.claude/settings.local.json` ou `--agent`/`--model` na sessão.
- **Effort da sessão principal** não foi medido diretamente (o `-p` não expõe o nível); vale a garantia documentada.
- **Sem harness de teste:** arquivos de configuração e texto; validação por execução real (`claude -p`) e parse de JSON.
