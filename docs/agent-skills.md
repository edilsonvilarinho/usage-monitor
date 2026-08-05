# Skills de agente do usage-monitor

Este documento descreve como as skills operacionais deste repositório funcionam, onde vivem e como a versão Claude foi derivada da versão Codex.

## Diretórios de agente no repositório

| Diretório | Agente | Conteúdo | Estado |
|---|---|---|---|
| `.codex/` | Codex | 3 skills em formato Codex + `install-repo-skills.ps1` | Fonte de verdade original |
| `.claude/` | Claude Code | As mesmas 3 skills em formato Claude Code | Derivado de `.codex/` |
| `.agents/` | Gemini e outros | Slash commands em markdown livre (`commit-push.md`, `release.md`) | Histórico, sem formato padronizado |

`AGENTS.md`, `CLAUDE.md` e `GEMINI.md` na raiz continuam sendo as instruções gerais por agente. As skills cobrem procedimentos operacionais específicos: commit/push, release e instalador NSIS.

## As três skills

| Skill | Objetivo |
|---|---|
| `usage-monitor-commit-push` | Staging explícito, verificação estreita, commit com identidade de agente temporária, push e restauração da identidade original |
| `usage-monitor-release` | Bump de versão, verificação, tag anotada `vX.Y.Z` e acompanhamento do workflow de CI que publica os artefatos |
| `usage-monitor-nsis-installer` | Build e diagnóstico do instalador Windows, incluindo as lições de freeze já documentadas |

## Como cada agente carrega as skills

### Codex

Exige uma etapa de instalação. `.codex/install-repo-skills.ps1` copia cada diretório de `.codex/skills/` para `$CODEX_HOME/skills` (ou `~/.codex/skills` quando `CODEX_HOME` não está definido), sobrescrevendo o destino:

```powershell
powershell -ExecutionPolicy Bypass -File .codex\install-repo-skills.ps1
```

É preciso reiniciar o Codex ou abrir uma nova sessão para recarregar as skills instaladas.

### Claude Code

Não há etapa de instalação. O Claude Code lê `.claude/skills/<nome>/SKILL.md` diretamente do repositório e registra a skill pelo campo `name` do frontmatter. O campo `description` é o que decide quando a skill é acionada, então ele precisa dizer *o que a skill faz* e *quando usá-la*.

Layout obrigatório:

```
.claude/skills/
├── usage-monitor-commit-push/
│   ├── SKILL.md
│   └── scripts/commit_and_push.ps1
├── usage-monitor-release/
│   └── SKILL.md
└── usage-monitor-nsis-installer/
    └── SKILL.md
```

Um markdown solto em `.claude/skills/*.md` **não** é carregado como skill.

## Regras de conversão Codex → Claude

| Item Codex | Tratamento no Claude |
|---|---|
| Frontmatter `name` + `description` | Mantido. É o mesmo contrato; só o nome do agente muda dentro do texto da `description` |
| `agents/openai.yaml` (`display_name`, `short_description`, `default_prompt`) | Não convertido. Sem equivalente no Claude Code — a `description` do frontmatter cumpre o papel de roteamento |
| Links relativos `../../../AGENTS.md` | Mantidos. `.claude/skills/<nome>/` tem a mesma profundidade que `.codex/skills/<nome>/`, então os caminhos continuam resolvendo para a raiz do repositório |
| `scripts/commit_and_push.ps1` | Copiado sem alteração. Não tem nada específico de Codex: a identidade entra por `-TempUserName` / `-TempUserEmail` |
| Exemplo de invocação dentro do `SKILL.md` | Ajustado para o caminho `.claude\skills\...` e para a identidade `claude` / `claude@anthropic.com` |
| Guardrails | Mantidos integralmente |

## `commit_and_push.ps1`

O script é compartilhado por conteúdo entre os dois agentes. Comportamento relevante:

- Roda todo comando git com `-c safe.directory=<repo>` e `-C <repo>`, então independe do diretório de trabalho atual.
- Faz `git add -- <arquivos>` apenas com os paths recebidos em `-Files`. Nunca `git add .`.
- Aborta com erro se, após o staging, `git diff --cached --stat` vier vazio.
- Grava a identidade temporária em `.git/config` (escopo local, nunca global).
- Restaura `user.name` / `user.email` no bloco `finally` — a restauração acontece mesmo se o commit ou o push falhar. Se o valor original não existia, o campo é removido com `--unset`.
- `-SkipPush` faz commit local sem push. `-DryRun` mostra o plano sem tocar no índice.

## Manutenção

`.codex/` é a origem. Quando o fluxo operacional mudar (novo comando de verificação, mudança no processo de release, novo guardrail), aplicar a mudança **nas duas cópias**. Divergência silenciosa entre `.codex/skills/` e `.claude/skills/` é o modo de falha mais provável deste arranjo.

Antecedente concreto: o `.claude/skills/slash/release.md` legado descrevia build local seguido de `gh release create`. Depois que `.github/workflows/release-linux.yml` passou a publicar os artefatos a partir da tag `v*`, esse texto ficou errado e permaneceu no repositório. Ele foi removido nesta conversão.
