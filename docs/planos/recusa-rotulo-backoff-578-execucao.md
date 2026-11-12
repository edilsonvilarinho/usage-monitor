# Recusa por rótulo da chave vira laço de 30 s no cliente e no log do servidor (#578)

- **Modelo:** Claude Opus 5.5 (orquestração); implementação pelos agentes de `.claude/agents/`.
- **Ferramenta:** Claude Code.
- **Data:** 2026-11-12.
- **Branch:** `fix/issue-578-label-rejection-backoff`.
- **Identidade de commit:** `claude <claude@anthropic.com>`, por comando.

## Objetivo

Uma conta recusada com `403` pelo servidor de time (conta fora do rótulo da chave, fora do time, de outra chave, limite
cheio) deixa de ser reenviada a cada 30 s até a configuração dela mudar ou o prazo de nova tentativa vencer. O servidor
para de registrar um `warn` por requisição repetida da mesma recusa.

## Fatos confirmados

| Fato | Como foi medido |
|---|---|
| O log da issue: mesma `keyId` (rótulo de outra pessoa) e mesma `accountEmail`, um `warn` a cada ~30-60 s | screenshot da issue #578 |
| `assertAllowedByLabel` loga `warn` e lança `ForbiddenError` (403) a cada requisição recusada, sem dedup | `server/src/http/access.ts:285-305`; status em `errorHandler.ts:33` |
| O portão roda em ingest, presença, leituras e verify | `routes/ingest.ts:30`, `routes/presence.ts:41`, `routes/team.ts:41,79,114`, `routes/verify.ts:173` |
| Laço do cliente a cada 30 s, sem backoff | `TeamSyncService.kt:229-233`; `DEFAULT_INTERVAL_MILLIS = 30_000L` |
| Lote recusado não avança o marcador e volta na passada seguinte; presença também sai a cada passada | `TeamSyncService.kt:395-451` |
| `403` já vira `rejectedProfiles` por perfil e aparece na tela | `admissionRejection` (`TeamSyncService.kt:82-85`), `TeamIntegrationSteps.kt:305`, `:477-479` |
| O portão está correto: o defeito é a repetição, não a recusa | `docs/team-integration.md:13`; decisão do usuário nesta sessão |

## Decisões

1. **Cliente:** recusa `403` de um perfil num time suspende presença, identidade e lotes daquele perfil naquele time.
   A suspensão é lembrada em memória pela impressão (chave do perfil + `accountKey` + `accountEmail`).
2. A suspensão cai quando: a impressão muda (chave trocada, conta trocada), o prazo de **30 min** vence (o admin pode ter
   corrigido o rótulo no servidor), ou o app reinicia.
3. Durante a suspensão o perfil continua em `rejectedProfiles` com o motivo do servidor: a tela não pode dar a recusa
   por resolvida só porque o cliente parou de perguntar.
4. `401` e `5xx` não suspendem: não apontam para uma conta (comentário de `admissionRejection`).
5. **Servidor:** `warn` da recusa por rótulo deduplicado por `(keyId, accountKey)`: o primeiro sai, as repetições dentro
   de 1 h não, e a primeira depois do prazo sai com a contagem suprimida. A resposta 403 não muda.
6. Mapa de dedup limitado em tamanho (sem crescimento sem teto num servidor de longa duração).

## Atividades

| ID | O quê |
|---|---|
| A01 | Este plano. |
| A02 | Cliente: teste vermelho (2ª passada após 403 não chama o servidor; troca de chave ou prazo vencido chama) e correção no `TeamSyncService`. |
| A03 | Servidor: teste vermelho (duas recusas iguais = um `warn`; após o prazo, `warn` com contagem) e correção em `access.ts`; registro em `docs/team-integration.md`. |

## Ponto de situação

| ID | Estado | Comando e resultado |
|---|---|---|
| A01 | Concluída | Sem harness de teste: documento. Validado pelas leituras de "Fatos confirmados". |
| A02 | Concluída | Vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.TeamSyncServiceTest"`, falha na asserção em `TeamSyncServiceTest.kt:682` (suspensão após 403) e `:758` (prazo de 30 min); o texto expected/actual não foi capturado pelo agente. Verde: mesmo comando, 43/43. Teste antigo "desmarcar a conta recusada limpa o aviso" passou a desmarcar de fato (`participatingProfileIds`), porque zerar a falha com a mesma configuração agora mantém a suspensão (decisão 3). Passada só com contas suspensas publica `lastSuccessAt` com `rejectedProfiles` preenchido. |
| A03 | Concluída | Vermelho: `npx vitest run test/unit/labelRefusalLog.test.ts test/unit/labelRefusalWarn.test.ts`, 7 falhas, ex.: `labelRefusalWarn.test.ts:47: expected [ … ] to have a length of 1 but got 3` e `labelRefusalLog.test.ts:18: expected { warn: true, suppressed: +0 } to deeply equal { warn: false }`. Verde: mesmo comando 10/10; `npm run test:coverage` 26 arquivos, 337/337, linhas 96,05%; `npm run typecheck` exit 0. Teto do mapa: 1024 entradas. |

## Problemas em aberto e riscos

- Abrir a janela do time (`requestImmediateSync`) não fura a suspensão; quem corrigir o rótulo no servidor espera até
  30 min ou reinicia o app. Aceito para não reabrir o laço por cliques.
