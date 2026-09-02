# Exportação de métricas em OpenMetrics (#167)

Plano de execução da [issue #167](https://github.com/edilsonvilarinho/usage-monitor/issues/167).

A tabela de **pontos de situação** no fim é atualizada no mesmo commit da atividade que ela descreve —
cada entrada carrega o comando que rodou e o resultado, nunca a intenção.

---

## Contexto

O `server/` já é o ponto que agrega várias máquinas e já publica leitura externa: `/api/v1/report/*`
devolve linhas planas paginadas e `/api/v1/pricing` publica a tabela de preços, para o consumidor
aplicar a aritmética (plano [`api-consumo-externo-execucao.md`](api-consumo-externo-execucao.md)).
Mas esse consumo só chega a quem escreve um cliente HTTP próprio. Quem já paga e já opera
Prometheus/Grafana não tem porta nenhuma: para ver consumo de IA precisa abrir a janela do app ou o
modal do time.

A issue #160 citada no corpo da #167 — servidor HTTP local no app desktop — **não existe neste
repositório** (`gh issue view 160` responde *Could not resolve to an issue*). Não há a alternativa que
o texto sugere; a entrega é no `server/`, que é onde a própria issue diz fazer mais sentido.

---

## Decisões travadas

| Ponto | Decisão | Motivo |
|---|---|---|
| Custo em dólar | O servidor **passa a precificar**, e só aqui | O Prometheus ingere números; ele não aplica tabela de preço. Sem custo na exposição, o operador manteria uma cópia da tabela à mão — exatamente o que a #105 provou divergir. `/v1/report/*` e `/v1/pricing` ficam como estão |
| Aritmética em TS | **BigInt** na soma ponderada | `number` é double e perde exatidão acima de 2^53. 10^11 tokens de cache read × 5×10^6 micros/milhão = 5×10^17, e um time real chega lá em sete dias. Sem BigInt o custo sai errado **sem erro nenhum** |
| Forma das séries | Gauges de janela deslizante, 24h e 7d | Counter cumulativo cairia na poda de retenção (45 dias), e queda parcial é lida como reset: a taxa sairia inflacionada em silêncio. O preço — `rate()` não se aplica — está declarado no README |
| Cardinalidade | Rótulos de valor: `account`, `member` (= `device_id`), `model`, `kind`, `window`. **Nunca** `session`, `cwd` ou `branch` | Toda linha vira uma série; `session_id` é ilimitado e cresceria para sempre. Consulta nova agregando por `(conta, máquina, modelo)` — `readReportUsage` é por sessão e não serve |
| Nome de pessoa | `usage_monitor_member_info{...} 1`, fora das séries de valor | `alias` é texto digitado e mutável: como rótulo de valor, renomear a máquina cria série nova e quebra o gráfico no meio. Idioma do Prometheus, junção no PromQL |
| Tipo `info` | Gauge com sufixo `_info` e valor 1 | O tipo `info` do OpenMetrics não existe no formato 0.0.4. A convenção do gauge (`node_uname_info`) é lida pelas duas versões sem ramo especial |
| Rota | `/metrics` na **raiz**, montada incondicionalmente | Caminho convencional de scrape. Incondicional pela razão do `report.ts`: rota ausente faria "credencial errada" e "variável não definida" chegarem como o mesmo 404 |
| Credencial | `requireGlobalRead` + `Authorization: Bearer` | `scrape_config` manda `authorization` nativamente; header próprio não é garantido em todo agente. Mesmo segredo, outro transporte. `requireAdminToken` **não é tocado** |
| Biblioteca | Nenhuma; exposição escrita à mão | `prom-client` traz registro **global e mutável**, e as séries daqui são derivadas de uma consulta por scrape — não há contador de processo a acumular. O registro global também atravessaria os testes, que sobem várias `app` no mesmo processo |
| Fuso | Nenhum; janelas são `now - 24h` / `now - 7d` | O servidor não conhece o fuso de quem consulta — mesma razão pela qual `/v1/team/trend` devolve dia UTC cru |
| Excesso de séries | Degrada tirando o rótulo `model`, com sinal em `usage_monitor_metrics_model_label_dropped` | Truncar em silêncio esconde dado; responder 500 quebra o scrape inteiro por causa de uma dimensão |

---

## Riscos declarados

1. **O invariante "o servidor não precifica" deixa de ser absoluto.** Ele passa a valer para
   `/v1/report/*` e `/admin/*`, e a exceção está escrita no cabeçalho do `modelPricing.ts`, no
   `usageCost.ts`, no README e no `CLAUDE.md`. A tabela continua tendo **um** dono — o Kotlin, com a
   guarda de paridade no CI; o que este módulo acrescenta é a aritmética, afirmada por teste com os
   mesmos números do `ModelPricingTableTest`.
2. **Gauge de janela não permite `rate()`.** Quem espera o idioma normal do Prometheus vai estranhar.
   A alternativa era pior: counter que cai na poda de retenção mente sem avisar.
3. **Uma consulta por scrape.** Com o intervalo padrão de 15s, são quatro `GROUP BY` por minuto sobre
   `team_turns` (duas janelas × uso e atividade), com filtro por `ts`. Não há cache: a alternativa
   seria cache com TTL, que devolveria número velho sem dizer.

---

## Pontos de situação

| # | Atividade | Comando | Resultado |
|---|---|---|---|
| B01 | Aritmética de preço em TS | `npm test -- test/unit/usageCost.test.ts` | **10 testes, 0 falhas.** `server/src/domain/usageCost.ts` espelha `ModelPricing.kt`, e os números do teste vêm do `ModelPricingTableTest` — refazer a conta aqui não provaria que o espelho está certo. O caso que importa é `não perde exatidão acima de 2^53`: 10^11 tokens de cache read a 200.000 micros/milhão dá 2×10^16 na soma ponderada, e o próprio teste afirma que o produto equivalente em `number` passa de `MAX_SAFE_INTEGER`. A ordem de match é **reordenada aqui** (prefixo mais longo primeiro), porque a ordem declarada em `MODEL_PRICING` é a do arquivo Kotlin, para o parser de paridade comparar posição a posição — é o equivalente do `sortedByDescending` que o Kotlin faz em runtime |
| B02 | Consulta agregada | `npm run typecheck` | Limpo. `SELECT_METRICS_USAGE_SQL` agrupa por `(conta, máquina, modelo)` e `SELECT_METRICS_ACTIVITY_SQL` por `(conta, máquina)`. **A ausência da sessão é o ponto**, não uma simplificação: cada linha vira uma série. A contagem de sessões distintas entra na consulta porque, sem a coluna de sessão, ela deixaria de ser derivável. No `LAG` o `PARTITION BY` continua sendo por **sessão** — particionar por máquina misturaria sessões paralelas num intervalo só. Janela fechada dos dois lados e **obrigatória**, ao contrário das rotas de relatório: sem `since`, cada scrape varreria a tabela inteira a cada 15s |
| B03 + B04 + B05 | Rota, credencial e degradação | `npm test -- test/api/metrics.test.ts` | **19 testes, 0 falhas.** Renderizador em `domain/openMetrics.ts`, separado da rota — os dois formatos são o mesmo texto e o OpenMetrics só acrescenta `# EOF`, então não há dois renderizadores. `Authorization: Bearer` entrou em `authorizeGlobalRead` e vale para toda a família de leitura global; `requireAdminToken` não foi tocado. Quatro comportamentos afirmados que não são dedutíveis do código: chave de time é **recusada** (é por conta, e a leitura é global); sem `TEAM_REPORT_TOKEN` a rota responde **401 e não 404**; apelido com aspa e barra é escapado, porque uma aspa invalida o documento **inteiro** e não a linha; e acima do teto o rótulo `model` sai **mantendo os totais corretos** — o que se perde é a dimensão, não o dado |
| B06 | Documentação e versão | `npm test` + `npm run typecheck` | **299 testes, 23 arquivos, 0 falhas** (eram 268). Servidor em `0.12.0`. `SERVER_VERSION` é constante em código e portanto um **segundo dono** do número — o `Dockerfile.dokploy` copia só `tsconfig.json` e `src`, e ler o `package.json` do `dist/` exigiria resolver caminho para fora dele; o preço da duplicação é `test/unit/version.test.ts`, que compara os dois. README com a tabela de séries, o `scrape_config` de exemplo, a fórmula de cardinalidade e as limitações declaradas; `.env.example` e `CLAUDE.md` atualizados. **Não verificado contra um Prometheus real**: a validação de formato por `promtool` não foi executada |
