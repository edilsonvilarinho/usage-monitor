# Piloto: agente de IA usa a beta e aponta suspeitas — issue #653

Procedência: Claude Opus 5.5 (orquestração) com agentes de implementação, Claude Code CLI no Windows 11, 2026-11-24,
branch `feat/issue-653-ai-pilot` (worktree `.claude/worktrees/ai-pilot-653`, base `origin/main` `35285e34`), commits com a
identidade `claude <claude@anthropic.com>`. Parte da #644. Este plano cobre só o piloto (fase 1 da issue).

## Decisão do dono (2026-11-24)

Piloto aprovado com teto de **US$ 5**, pelo OpenRouter, segredo `OPENROUTER_API_KEY` no GitHub. Haiku 5.5 navega; Sonnet 5.5
confirma só as suspeitas; nada abre issue sozinho.

## Fatos confirmados (2026-11-24)

- `GET https://openrouter.ai/api/v1/models`: `anthropic/claude-haiku-5.5` aceita imagem e ferramentas; US$ 0,10 / 0,50 por
  milhão (cache lido 0,01, escrito 0,125); Sonnet 5.5, 2 / 10 (0,10; 2,50).
- Captura 1024×768 = 1.036 tokens (⌈largura/28⌉ × ⌈altura/28⌉, documentação da Anthropic). Acima de 100 mil tokens no
  prompt o Haiku custa 5×.
- `GET /api/v1/key` com a chave criada: `limit: null`. **A chave não tem limite de gasto.**
- #657: `SendInput` chega ao app no runner; `record.ps1` e `seed.ps1` isolam dados e bloqueiam a rede.
- Defeito de animação (piscada de 500 ms) não é alvo: o modelo vê fotos espaçadas. É da #652 e da #657.

## Decisões

- **Ferramentas próprias** por cima do `SendInput`: `screenshot`, `click`, `drag`, `move`, `type`, `key`, `wait`, `report`,
  `done`. Não depende da ferramenta de controle de tela da Anthropic no OpenRouter, que não foi verificada.
- **Teto em três lugares:** 60 passos e 100 mil tokens por roteiro; US$ 0,30 por roteiro e **US$ 5 no piloto**, somados do
  `usage.cost` de cada resposta e conferidos contra `/api/v1/key` antes de cada roteiro; e o limite na própria chave.
  **Nenhuma execução paga antes de a chave ter limite.**
- **Só `workflow_dispatch`, na `main`.** O segredo nunca entra em `pull_request`. Nenhuma chave em log, artefato ou prompt.
- **Verdade conhecida:** pares versão com defeito × versão corrigida, com o defeito visível em tela parada, sem rede e com
  dado sintético. O agente não recebe o nome do defeito, só o roteiro e as regras escritas.
- **Critério:** 70% dos casos com defeito apontados e no máximo 20% de alarme falso nas versões boas, 3 rodadas cada. Abaixo
  disso, mesmo piloto com o Sonnet navegando, se couber no teto.

## Atividades

- **A01** — plano e mapa dos casos. **A02** — laço, ferramentas e orçamento em TDD, com cliente falso (sem gasto).
- **A03** — driver de tela no runner e workflow. **A04** — rodada paga e medição. **A05** — relatório e decisão.

## Ponto de situação

| Atividade | Comando | Resultado |
|---|---|---|
| A01 (plano) | catálogo e `/api/v1/key` do OpenRouter | fatos acima; mapa dos casos pendente |

## Problemas em aberto e riscos

- A chave foi colada no chat e fica no histórico local da sessão: trocar depois do piloto.
- Texto na tela do app é dado sintético; o agente não navega na internet, e a rede do app fica bloqueada.
- Pode não haver 10 casos que atendam à regra da verdade conhecida; o critério é percentual por isso.
