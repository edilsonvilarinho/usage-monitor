# API para consumo externo (#106) e correção do preço do Sonnet 5 (#105)

Plano de execução consolidado das issues [#105](https://github.com/edilsonvilarinho/usage-monitor/issues/105)
e [#106](https://github.com/edilsonvilarinho/usage-monitor/issues/106), rastreadas pela
[#107](https://github.com/edilsonvilarinho/usage-monitor/issues/107). Elas nascem do mesmo
levantamento e uma justifica a outra: o [auditoria-ia](https://github.com/edilsonvilarinho/auditoria-ia)
vai consumir o servidor de time em vez de parsear o PDF exportado à mão.

A tabela de **pontos de situação** no fim é atualizada no mesmo commit da atividade que ela descreve —
cada entrada carrega o comando que rodou e o resultado, nunca a intenção. A issue #107 carrega a mesma
tabela.

---

## Contexto

**#105 — o preço do `claude-sonnet-5` está errado.** `ModelPricingTable.kt` mapeava
`claude-sonnet-5` para a tarifa `SONNET` de $3/$15. **Confirmado contra a tabela oficial de preços**:
Sonnet 5 é **$2/$10**; $3/$15 é a tarifa do Sonnet 4.6 e do 4.5.

O erro superestima toda sessão em Sonnet 5 por um fator exato de 3/2 — os cinco componentes de preço
derivam do input (`ModelPricing.kt:26-36`) e a razão output/input é 5× nos dois preços, então não há
mistura de proporções. Custo não é persistido: `cli_turns` e `team_turns` guardam tokens e a
precificação acontece na leitura, então a correção reprecifica o histórico inteiro sem migração.

**#106 — o consumidor externo precisa de quatro coisas.** Ao ler o `server/`, quase nada falta:
`SELECT_USAGE_SQL` (`teamRepository.ts:298`) já devolve `cwd`, `gitBranch`, `firstTs`, `lastTs`,
`sessionId` íntegro e os cinco contadores por `(deviceId, sessionId, model)`, e
`SELECT_SESSION_ACTIVITY_SQL` (`:338`) dá `activeMillis`. Falta (a) a tabela de preços publicada como
dado, (b) o lado direito da janela, (c) um teto de resposta e (d) uma credencial que leia sem poder
apagar.

A #105 é a prova de que (a) é necessário: uma tabela duplicada à mão diverge na próxima mudança de
preço, e o painel e o PDF passam a mostrar custos diferentes do mesmo período.

**#102 e #104 ficam de fora** — são UI do modal do time, independentes desta linha.

---

## Decisões travadas

| Ponto | Decisão | Motivo |
|---|---|---|
| Dono da tabela de preços | TS em `server/src/domain/modelPricing.ts` + guarda de paridade no CI | Domain Kotlin continua puro (sem serialização, sem IO). O servidor **continua não precificando** — publica a tabela, o consumidor aplica a aritmética |
| Paginação | Rota nova plana `/api/v1/report/*`, `/admin/v1/overview` intacto | `flattenAccounts`/`toUsageBreakdown` do admin assumem resposta completa; uma página parcial subestimaria os totais da tela sem erro nenhum |
| Credencial de leitura | `TEAM_REPORT_TOKEN` em variável de ambiente, header `x-report-key` | Mesmo desenho de `TEAM_ADMIN_TOKEN`. Sem schema, sem UI no desktop, sem rota de emissão. Rotação = redeploy |
| `unpricedTurnCount` na resposta | **Não é acrescentado** | As linhas já são `(device, session, model)` com `turnCount` e `model`. Com `/api/v1/pricing` publicando a regra de match, o consumidor deriva o valor. Campo novo seria um segundo dono da mesma conta |
| `until` | Semiaberto: `since <= ts < until` | `since` já é `>=`. Com `until` inclusivo, duas janelas adjacentes contariam duas vezes o turno da fronteira |
| Cliente desktop | Não muda, exceto `ModelPricingTable.kt` | `until` é opcional, as rotas são aditivas e o token de relatório é novo |
| Protótipo visual | Não é atualizado | Nenhuma superfície visível muda. A #105 muda números exibidos, não layout |

---

## Atividades

Uma atividade, um commit. Cada uma fecha sozinha: código, teste e documentação juntos.

### A01 — Corrigir a tarifa do Sonnet 5 (#105)

`src/commonMain/kotlin/com/usagemonitor/domain/entity/ModelPricingTable.kt`: constante `SONNET_5` de
2/10 ao lado da `SONNET` de 3/15, e `"claude-sonnet-5"` passa a apontar para ela. O match por prefixo
com fronteira em `-` já cobre o resto: `claude-sonnet-5` não é prefixo de `claude-sonnet-4-6`, e
`matchesModelPrefix` rejeitaria de qualquer forma.

Em `ModelPricingTableTest.kt`, dois testes novos: `derives the five prices for sonnet 5` (2/10 →
cache read 200_000, write 5m 2_500_000, write 1h 4_000_000) e
`sonnet 5 does not share pricing with sonnet 4-6` — é exatamente a regressão que passou despercebida.
O teste `derives the five prices for sonnet` já usa `claude-sonnet-4-6` e continua válido.

Fecha a #105. Independente de todo o resto.

### A02 — Tabela de preços no servidor + guarda de paridade

`server/src/domain/modelPricing.ts` — módulo TS (não JSON: o `Dockerfile.dokploy` copia só
`server/tsconfig.json` e `server/src`, e o `tsc` não emite `.json` para o `dist/` sem
`resolveJsonModule` + import). Forma estável, pensada para o parser de paridade: `PRICING_VERSION`,
`MODEL_PRICING` com uma linha por entrada de `ModelPricingTable.kt` na mesma ordem, e
`CACHE_MULTIPLIERS` em **razões inteiras, não decimais** — a aritmética de `ModelPricing.kt:26-36` é
inteira em micros e não trunca; mandar `0.1` e `1.25` como float convidaria o consumidor a introduzir
erro que o original não tem.

`tools/ci/check-pricing-parity.mjs` — script Node sem dependência, no molde de
`tools/ci/test-summary.mjs` (o job do desktop não roda `npm ci`). Lê os dois arquivos por regex,
compara prefixo a prefixo e falha com a lista das divergências. Cobre também a existência de
`<synthetic>` nos dois lados e os três multiplicadores.

**Roda nos dois workflows.** Os filtros de path são disjuntos hoje — `ci.yml` ignora `server/**`,
`ci-server.yml` só dispara com `server/**` —, então mudar um lado só passaria batido.

### A03 — `GET /api/v1/pricing` + credencial de leitura global

`server/src/config.ts`: `reportToken: string | null` lido de `TEAM_REPORT_TOKEN`, com
`requireStrongSecret` como os demais. **Não entra na validação de "pelo menos um segredo"** — um
servidor que só publica relatório e não aceita cliente nenhum é inútil.

`server/src/http/access.ts`: `TeamAccess.kind` ganha `'report'`; em `authorize()` o token de relatório
é aceito no mesmo ponto do `x-admin-token` — dentro do `if (!allowClaim)`, o que já o recusa em ingest
e presença; `requireGlobalRead(config, handler)` novo aceita `x-admin-token` **ou** `x-report-key`, sem
escopo de conta. `requireAdminToken` **não é tocado** — é ele que protege todos os `DELETE` do
`admin.ts`, então o token de relatório nunca alcança rota destrutiva.

`server/src/http/routes/report.ts` (novo roteador, montado incondicionalmente em `app.ts`) publica
`version`, `models`, `cacheMultipliers`, `matchRule` e `syntheticModelId`. Dois contratos vão
explícitos na resposta e no README: **`<synthetic>` é preço zero conhecido, não desconhecido**, e
**modelo não reconhecido devolve indisponível, nunca zero**.

`server/test/support/harness.ts` monta um `Config` literal completo — o campo novo tem de entrar lá
também, ou a suíte inteira quebra na tipagem.

### A04 — Filtro `until` nas três leituras

`server/src/http/dto.ts`: `until` opcional em `teamQuerySchema` e `overviewQuerySchema`, com refino
`until > since` → 400. `teamRepository.ts`: cinco consultas ganham `AND (@until IS NULL OR t.ts < @until)`
— `SELECT_USAGE_SQL`, `SELECT_ALL_USAGE_SQL`, `SELECT_TREND_SQL` e, **dentro da subconsulta, antes do
`LAG`**, `SELECT_SESSION_ACTIVITY_SQL` e `SELECT_ALL_SESSION_ACTIVITY_SQL`.

Filtrar dentro da subconsulta de atividade exclui o intervalo que cruza a fronteira, que é o correto:
aquele tempo pertence à janela seguinte. É o mesmo comportamento que `since` já tem.

`/v1/team/trend` passa a aceitar `since`/`until` absolutos, mantendo `days` como fallback — é o que o
desktop manda hoje (`RemoteTeamDataSource.kt:136-152`) e ele não pode quebrar.

### A05 — Rotas de relatório paginadas

Três rotas em `report.ts` sob `requireGlobalRead`: `/api/v1/report/usage` (linhas planas com
`accountKey`, cursor), `/api/v1/report/activity` (cursor) e `/api/v1/report/members` (sem paginação,
limitada pelo tamanho do time).

**A ordenação é a própria chave de agrupamento**, não `MAX(t.ts) DESC`: as quatro colunas
`account_key`, `device_id`, `session_id` e o modelo normalizado saem do `GROUP BY`, formam ordem total
e o predicado do cursor cabe no `WHERE`, não num `HAVING` sobre agregado. O modelo entra normalizado
com `IFNULL` porque é anulável e NULL não compara em row-value. Cursor = base64url, opaco, `limit`
default 500 e teto 5000; cursor ilegível → 400, não 500.

`emailSource` sai sempre junto de `accountEmail`: `reported` é o e-mail real da conta Anthropic,
`label` é texto que o admin digitou sem verificação nenhuma.

### A06 — Documentação e versão

`server/package.json` 0.9.0 → 0.10.0; `server/README.md` com `x-report-key`, `TEAM_REPORT_TOKEN`,
as seções das rotas novas, `until` nas tabelas de query e o que o token de relatório *não* pode;
`CLAUDE.md` na seção "Integração com time"; e este documento com a tabela de pontos de situação
fechada.

---

## Riscos conhecidos

1. **A guarda de paridade é regex sobre dois arquivos.** Reformatar qualquer um dos dois quebra o
   script. É o comportamento desejado — falha ruidosa em vez de divergência silenciosa —, mas o
   formato das duas listas passa a ser contrato e precisa do comentário dizendo isso nos dois arquivos.
2. **`/api/v1/report/usage` agrupa a tabela inteira quando `since` é omitido.** O
   `idx_team_turns_window` é `(account_key, ts DESC)` e a consulta global não filtra por conta — o
   mesmo custo que `/admin/v1/overview` já paga hoje. Se virar problema, o conserto é um índice em
   `team_turns(ts)`; não entra agora sem medida que o justifique.
3. **`TEAM_RETENTION_DAYS` continua em 45.** A #106 não pede mudança e o auditoria-ia mantém o próprio
   histórico — mas o que passar da janela antes da primeira coleta é irrecuperável.
4. **Rotação do token de relatório é redeploy.** É o preço aceito da variável de ambiente contra a
   chave no banco. Se o consumidor virar mais de um, a chave com `scope` no painel volta à mesa.
5. **A correção da A01 muda as capturas de tela.** `ScreenshotFixtures.kt:62` usa `claude-sonnet-5`,
   então `gradlew.bat generateScreenshots` produz custos diferentes em `cli-sessions` e
   `cli-breakdown`. O gerador é manual e não roda no CI; as imagens do README não foram regeneradas.

---

## Pontos de situação

A coluna `Commit` guarda o **assunto** do commit, não o hash: um commit não pode conter o próprio
hash, e preencher o hash depois quebraria a regra de escrever a linha no mesmo commit da atividade.

| # | Data | Commit | Atividade | Estado | Evidência |
|---|---|---|---|---|---|
| A01 | 2026-08-25 | `fix(pricing): charge sonnet 5 at its own rate` | Tarifa do Sonnet 5 (#105) | concluída | `SONNET_5` de 2/10 separada da `SONNET` de 3/15; só `"claude-sonnet-5"` muda de constante. Dois testes novos em `ModelPricingTableTest`, um deles afirmando que as duas tarifas **não** são iguais — é a regressão que passou despercebida, e sem ele ela volta na próxima família de modelos. **Duas asserções existentes reprovaram e foram corrigidas**, não contornadas: `CliSessionAnalyticsTest > cost is summed with the model of each turn` (40M → 35M micros) e `LocalCliSessionDataSourceTest > cost uses the model of each turn` (65M → 60M). Os dois fixtures continuam em `claude-sonnet-5` de propósito: o contraste com Opus permanece e agora exercitam a tarifa nova. `docs/plano-sessoes-cli.md:209` publicava 3,00/15,00 para o Sonnet 5 e foi corrigido — tabela de preço errada em documento é armadilha, mesmo em plano fechado. `gradlew.bat desktopTest --tests "…ModelPricingTableTest" --tests "…CliSessionAnalyticsTest" --tests "…LocalCliSessionDataSourceTest"`: BUILD SUCCESSFUL em 1m20s. `gradlew.bat allTests`: BUILD SUCCESSFUL em 1m24s, **1420 testes / 0 falhas / 0 erros / 0 pulados** |
| A02 | — | — | Tabela TS + guarda de paridade no CI | pendente | — |
| A03 | — | — | `/api/v1/pricing` + `TEAM_REPORT_TOKEN` | pendente | — |
| A04 | — | — | Filtro `until` nas três leituras | pendente | — |
| A05 | — | — | Rotas `/api/v1/report/*` paginadas | pendente | — |
| A06 | — | — | README, CLAUDE.md, versão 0.10.0 | pendente | — |

### Achados

| Data | Achado | Efeito no plano |
|---|---|---|
| 2026-08-26 | Preço do Sonnet 5 ($2/$10) confirmado contra a tabela oficial, independente da #105 | Nenhum — confirma A01 |
| 2026-08-26 | `ci.yml` e `ci-server.yml` têm filtros de path disjuntos | A guarda de paridade precisa de step nos **dois** workflows |
| 2026-08-26 | `Dockerfile.dokploy` copia só `server/tsconfig.json` e `server/src` | A tabela do servidor é módulo TS, não JSON compartilhado na raiz |
| 2026-08-26 | `test/support/harness.ts` monta um `Config` literal completo | Campo `reportToken` novo tem de entrar lá, ou a suíte quebra na tipagem |
| 2026-08-26 | Duas asserções de custo fixavam o Sonnet em 15,00/MTok de output e reprovaram com a correção: `CliSessionAnalyticsTest > cost is summed with the model of each turn` (40M → 35M) e `LocalCliSessionDataSourceTest > cost uses the model of each turn` (65M → 60M) | Entraram na A01. Os fixtures continuam em `claude-sonnet-5` de propósito: o contraste com Opus permanece e os dois testes passam a exercitar a tarifa nova |
| 2026-08-26 | `docs/plano-sessoes-cli.md:209` publicava a tabela de preços com `claude-sonnet-5` a 3,00/15,00 | Linha corrigida na A01 — tabela de preço errada em documento é armadilha, mesmo em plano já fechado |
