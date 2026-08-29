# Abertura do repositório (issue #131) — licença, metadados e README no padrão de repos grandes

| | |
|---|---|
| **Modelo** | Claude Opus 5 — `claude-opus-5[1m]` |
| **Ferramenta** | Claude Code (CLI) |
| **Data** | 2026-08-29 |
| **Branch** | `main` |
| **Autor dos commits** | `claude <claude@anthropic.com>` |

O modelo fica registrado porque este documento é o rastro de auditoria de um trabalho feito por
agente. A seção **Pontos de situação**, no fim, é atualizada a cada atividade, no mesmo commit da
atividade.

## Contexto

O repositório é público desde 2026-04-25, tem 38 releases e 252 downloads acumulados, e não tem
nenhum dos três sinais que um visitante procura primeiro. Medido em 2026-08-29 com
`gh repo view --json description,repositoryTopics,licenseInfo`:

| Sinal | Estado |
|---|---|
| `description` | `""` |
| `topics` | `null` |
| Licença detectada | `null` |

Há texto MIT no repositório, mas só em `src/installer/license.txt`, com
`Copyright (c) 2024 Usage Monitor` — ano anterior ao primeiro commit (2026) e titular genérico. O
GitHub não lê aquele caminho. Sem licença explícita na raiz, o padrão legal é *todos os direitos
reservados*: ninguém pode usar, redistribuir ou contribuir.

O `README.md` tinha **582 linhas** e falhava no trabalho básico:

1. **A instrução de instalação estava na linha 471 de 582**, dentro de `Build e distribuicao`. O que
   estava no topo (`Como rodar`, L233) era `gradlew.bat` — fluxo de desenvolvedor.
2. Zero badges.
3. `Visao geral` eram 48 bullets (L11–58) — changelog disfarçado de visão geral.
4. ~130 linhas de documentação de contribuidor misturadas com a de usuário.
5. A última seção do README público mandava rodar `git config user.name "codex"` (L569) — regra
   interna de agente, já registrada em `AGENTS.md`.
6. Sem índice, e acentuação inconsistente no mesmo arquivo.
7. Só em português. Os quatro repositórios citados na issue como referência — `claude-hud` (27k),
   `CodexBar` (20k), `codeburn` (9,7k), `Claude-Code-Usage-Monitor` (8,6k) — são todos MIT e todos
   em inglês.

Resultado pretendido: o visitante entende o que é, vê a tela, baixa o instalador certo para o SO
dele e sabe sob qual licença, sem rolar 470 linhas.

## Decisões

| Decisão | Razão |
|---|---|
| `README.md` em inglês, canônico; `README.pt-BR.md` espelhado | É o `README.md` que o GitHub renderiza, e é o inglês que dá alcance. A nota no topo do PT declara que a tradução pode atrasar — não se promete paridade que nenhum teste verifica |
| MIT, `Copyright (c) 2026 Edilson Vilarinho` | Autor real do repositório, ano do primeiro commit. O texto vai **não reflowado**: o GitHub só detecta a licença comparando com o corpo canônico |
| Documentação técnica sai do README | README é para quem usa; arquitetura, DI e build são para quem contribui, e têm ciclo de vida próprio |
| Badge de downloads fica de fora | Medido: 252 downloads totais (`gh api releases --jq '[.[].assets[].download_count]|add'`). Ao lado de 0 estrelas o número desencoraja em vez de informar |
| Nenhuma imagem é apagada | As quatro não referenciadas são **geradas por código** (`ScreenshotGenerator.kt:101,108-110`). `rm` é no-op: a próxima passada de `generateScreenshots` as recria e suja o worktree. Elas passam a ser referenciadas |
| `CHANGELOG.md` sem backfill | As 67 tags já têm notas geradas pelo workflow a partir de assuntos de commit. Transcrevê-las produziria ~2.000 linhas duplicando a página de Releases verbatim. O arquivo aponta para lá e passa a receber curadoria a partir da próxima versão |
| Nenhum passo de CI escreve no `CHANGELOG.md` | Seria commit de bot sobre um commit já tagueado, brigando com o fluxo disparado por tag |
| `CODE_OF_CONDUCT.md` fica de fora | Não foi pedido, e um projeto de um mantenedor sem contribuição externa ainda não tem a quem aplicá-lo |

## Riscos verificados antes de executar

| Risco | Estado |
|---|---|
| Link âncora para seção do README em código, workflow ou doc | **Nenhum.** `grep` por `README.md#` e `(README` em `*.md`, `*.yml`, `*.kt`, `*.kts` devolve zero. Renomear e traduzir todos os headings não quebra nada interno |
| Detecção de licença pelo GitHub | Frágil: exige arquivo raiz `LICENSE` com o corpo MIT canônico. Verificado depois do push |
| `docs/design-system/readme.md` é minúsculo | Linkado pelo caminho exato — `README.md` daria 404 em sistema de arquivos sensível a caixa |
| `img/tour.gif` tem 1,18 MB acima da dobra | Custo já ponderado uma vez: `GifEncoder.kt:33` registra a rejeição de uma versão de 7 MB. Mantido |
| Deriva entre `README.md` e `README.pt-BR.md` | Sem teste de paridade. Mitigação é declarar o inglês canônico e avisar no topo do PT |

## Pontos de situação

Uma linha por atividade, escrita **no mesmo commit** da atividade. `Evidência` é o comando que rodou
e o resultado, não a intenção. A coluna `Commit` guarda o **assunto**, não o hash: um commit não pode
conter o próprio hash.

| # | Data | Commit | Atividade | Estado | Evidência |
|---|---|---|---|---|---|
| A01 | 2026-08-29 | `docs: license the project under mit` | `LICENSE` na raiz + correção do titular no instalador | concluída | Corpo MIT canônico, **não reflowado** — é com ele que a detecção do GitHub compara, e reescrever um parágrafo derrubaria a detecção em silêncio. `src/installer/license.txt` passou de `Copyright (c) 2024 Usage Monitor` para `2026 Edilson Vilarinho`; é o texto exibido na página de licença do NSIS (`UsageMonitor.nsi:122`), mudança de texto sem efeito funcional. **Nenhum `THIRD-PARTY-NOTICES`**: as fontes IBM Plex já viajam com `OFL.txt` ao lado dos TTFs em `src/desktopMain/resources/fonts/`, que é o que a OFL 1.1 exige. Antes: `gh repo view --json licenseInfo` = `null` |
| A02 | 2026-08-29 | `docs: add issue and pull request templates` | `.github/ISSUE_TEMPLATE/` + `PULL_REQUEST_TEMPLATE.md` | concluída | Formulário de bug pedindo versão, SO, forma de instalação, área afetada e o arquivo de `~/.usage-monitor/diagnostics/`, com dois checkboxes obrigatórios — um deles afirmando que o relato não carrega chave nem token. Vem cedo na sequência porque é a única entrega que **nada referencia e que não referencia nada** do repositório. **Descoberta:** `gh api` devolve `has_discussions: false`, então o link para Discussions do `config.yml` daria 404 e foi trocado pela referência de integrações; o link para o `SECURITY.md` virou URL absoluta, porque caminho relativo em corpo de issue form não resolve a partir de `.github/ISSUE_TEMPLATE/`. Os três YAML validados com `yaml.safe_load` |

### Ações externas

Não são commit — mudam a configuração do repositório no GitHub — e por isso ficam registradas aqui
com o comando exato e o resultado medido.

| # | Data | Ação | Estado | Evidência |
|---|---|---|---|---|
| A08 | 2026-08-29 | Descrição e topics do repositório | concluída | `gh repo edit --description … --add-topic ×20`. Depois: `gh repo view --json description,repositoryTopics` devolve a descrição em inglês e os 20 topics, contra `""` e `null` antes. Os topics cruzam com os dos quatro repositórios de referência onde há sobreposição real — `claude-code` aparece nos quatro — e acrescentam os que descrevem esta stack. `cli` ficou de fora: este não é um CLI |
| A09 | 2026-08-29 | Reporte privado de vulnerabilidade | concluída | `gh api --method PUT repos/…/private-vulnerability-reporting`; depois, `gh api repos/…/private-vulnerability-reporting` devolve `{"enabled":true}`, contra `{"enabled":false}` antes. Sem isso o link do `SECURITY.md` levaria a uma página sem o formulário — o documento indicaria um canal que não existe |
