# Piloto: agente de IA usa a beta e aponta suspeitas — issue #653

Procedência: Claude Opus 5.5 (orquestração) com agentes de implementação, Claude Code CLI no Windows 11, 2026-11-24,
branch `feat/issue-653-ai-pilot` (worktree `.claude/worktrees/ai-pilot-653`, base `origin/main` `35285e34`), commits com a
identidade `claude <claude@anthropic.com>`. Parte da #644. Este plano cobre só o piloto (fase 1 da issue).

## Decisão do dono (2026-11-24)

Piloto aprovado com teto de **US$ 5**, pelo OpenRouter, segredo `OPENROUTER_API_KEY` no GitHub. Haiku 5.5 navega; Sonnet 5.5
confirma só as suspeitas; nada abre issue sozinho.

## Decisões do dono (2026-11-25), que trocam as de cima

- **Chave:** a já configurada, sem limite no OpenRouter, por decisão do dono. O teto fica no código e no `usage` da própria chave: a chave é só do
  piloto (uso zero em 2026-11-25), então `usage` é o gasto acumulado, e nenhum roteiro começa se `usage` mais a reserva passar de US$ 5.
- **Modelo:** sempre Haiku 5.5, também na confirmação e na segunda tentativa.
- **Destino:** o dono analisa o relatório junto. Se valer a pena, o agente passa a criar a issue; a correção segue o workflow. Fora desta atividade.
- **Aviso ao dono antes do primeiro disparo pago.**

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
- **Decisões da A02:** `tools/ci/ai-pilot.mjs` não importa nada nem lê ambiente ou disco: cliente e tela entram por injeção. 100 mil
  tokens é o tamanho do **prompt**, não a soma. Resposta sem `usage.cost` bloqueia o piloto inteiro (falha fechada, nunca estimada).
  Ferramentas do turno que esgota o orçamento ainda rodam: já foram pagas. A confirmação pelo Sonnet é um roteiro próprio no orçamento
  (quem chama faz `startScript()` antes); `null` é "não medido". Resposta HTTP 200 sem JSON pode ter custado e não ser somada: a
  conferência de `/api/v1/key` antes de cada roteiro cobre.

## Mapa dos casos (A01)

Versões medidas por `git tag --contains` do merge da correção; a ruim é a tag imediatamente anterior, com `.exe` publicado.

| Caso | O que se vê | Ruim × boa | Dado |
|---|---|---|---|
| #623 | balão da engrenagem longe dela, no modo flutuante | `v42.3.0-beta.12` × `beta.13` | cache do painel |
| #631 | minimizar pelo balão o deixa aberto | `v42.3.0-beta.16` × `beta.17` | cache, 2+ contas |
| #596 | barra lateral fina com 7 contas, sem fundo nem estrelas | `v42.3.0-beta.6` × `beta.7` | cache, 7 contas |
| #474 | reinício semanal e mensal sem data | `v42.0.0-beta.4` × `beta.5` | cache, cotas semanal e mensal |
| #577 | ordenar esconde a primeira linha | `v42.3.0-beta.1` × `beta.2` | sessões CLI sintéticas |
| #556 | Análise desenha só 3 janelas anteriores | `v42.2.0` × `v42.3.0-beta.1` | banco de histórico |
| #607 | sem janela aberta, a diagonal some | `v42.3.0-beta.9` × `beta.10` | banco de histórico |
| #477 | rótulos da diagonal se sobrepõem | `v42.0.0-beta.5` × `beta.6` | banco de histórico |
| #535 | Haiku 5.5 "sem tarifa" na sessão | `v42.1.1` × `v42.1.2-beta.1` | sessões CLI sintéticas |

**1ª rodada paga: os 4 primeiros**, que só precisam do cache. Os 5 últimos exigem semear banco ou sessões, e só entram se a
1ª rodada passar do critério. São pares consecutivos, mas outras mudanças da mesma versão podem confundir o agente: alarme
na versão boa é conferido à mão antes de contar como falso.

## Atividades

- **A01** — plano e mapa dos casos. **A02** — laço, ferramentas e orçamento em TDD, com cliente falso (sem gasto).
- **A03** — driver de tela no runner e workflow, em três partes: **A03a** `ai-pilot.mjs` (chave sem limite, gasto acumulado, só Haiku) e
  `ai-pilot-driver.mjs`; **A03b** `screen-driver.ps1` e sementes dos 4 casos; **A03c** `ai-pilot-run.mjs`, catálogo de casos, regras e
  `ai-pilot.yml`. Prova sem IA (`dry`): cada defeito visto na tela da versão ruim e ausente na boa. **A04** — rodada paga e medição. **A05** — relatório e decisão.

## Ponto de situação

| Atividade | Comando | Resultado |
|---|---|---|
| A01 | catálogo e `/api/v1/key` do OpenRouter; `git tag --contains` por caso | fatos acima; 9 casos, 4 na 1ª rodada. Nenhum foi visto na tela ainda: a A03 confirma que cada defeito aparece no runner antes de gastar |
| A02 | `node --test tools/ci/test/ai-pilot.test.mjs` | vermelho (stub) 81 de 92, 1ª asserção `actual: { ok: true }, expected: { ok: false, reason: 'invalid-response' }`; verde 92/92. Devolvida 1x (descartava as ferramentas do último turno pago): vermelho `:315 actual: 0, expected: 1`, `:323 actual: 'script-budget', expected: 'done'`, `:333`, `:350` e `:474 actual: { confirmed: true, why: 'ok' }`; verde **95/95**, em execução independente. Nenhuma chamada paga, nenhuma rede. Cobertura do diff: `tools/ci` não é medido por Kover nem pelo lcov do servidor |
| A03a | `node --test "tools/ci/test/*.test.mjs"` | vermelho (stub): `actual: { ok: false, reason: 'no-limit' }, expected: { ok: true, usage: 1.25 }`; `actual: null, expected: { width: 1920, height: 1080 }`; `actual: [], expected: [ { id: 1, tool: 'screenshot' } ]`; `Missing expected rejection`. Verde 254/254 na pasta (21 novos). Decisões do dono trocaram o contrato: `limit: null` aceito, `limit-too-high` some, `usage` da chave vira o gasto acumulado (`pilot-budget`), e o cliente recusa modelo que não seja `anthropic/claude-haiku-5.5` |

## Problemas em aberto e riscos

- A chave foi colada no chat, não tem limite e o dono decidiu mantê-la: se o código errar a conta, nada no OpenRouter interrompe antes do saldo.
- Texto na tela do app é dado sintético; o agente não navega na internet, e a rede do app fica bloqueada.
- Pode não haver 10 casos que atendam à regra da verdade conhecida; o critério é percentual por isso.
