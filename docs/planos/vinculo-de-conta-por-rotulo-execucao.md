# Vínculo de conta ao time por rótulo da chave — plano de execução (issue #179)

## Problema

A conta `879df04a-ce80-4908-b8b3-cc230376402c` estava vinculada à chave rotulada
`helio.sales@informata.com.br`, mas o e-mail que o cliente reporta para ela é
`ronac2007@gmail.com` — a máquina `NOTE-LAT-015` com um perfil Anthropic pessoal marcado como
participante do time, somando 178,2M de tokens e US$ 102,11 aos totais da empresa.

O servidor não tinha critério de admissão: `authorize()` (`server/src/http/access.ts`) só testava
`resolved.accounts.length < resolved.maxAccounts`, e a chave em questão tinha `maxAccounts = 10`.
O rótulo — onde a central de times já declara de quem é a chave — nunca foi lido por nada, e o
`accountEmail` reportado pelo cliente já chegava ao servidor sem nunca ser comparado com ele.

Somam-se duas ausências: falta o critério de admissão e falta lugar onde a decisão explícita do
admin ("esta conta não faz parte do time") fique registrada. `DELETE /admin/v1/accounts/:accountKey`
apagava os dados, mas o ingest seguinte recriava a conta em 30s.

## Decisões

1. **Portão do rótulo.** Rótulo que declara e-mail passa a ser a relação do time: só a conta cujo
   e-mail reportado está nele pode usar a chave. Rótulo sem e-mail mantém o comportamento anterior.
2. **Vale retroativamente.** O critério é aplicado em toda requisição, inclusive nos vínculos que já
   existiam.
3. **Expulsão explícita.** Remover a conta, com modal de confirmação, a manda para uma tabela de
   contas que não fazem parte do time — de onde ela não volta sozinha.

Ordem de avaliação: **bloqueio antes do portão**. Decisão explícita do humano vence regra derivada
de texto.

## Pontos de situação

| # | Atividade | Situação |
|---|---|---|
| A1 | Regra pura do rótulo, `TEAM_KEY_LABEL_MATCH`, `label` em `ResolvedTeamKey`, `accountEmailOf` | **Concluída.** `npx tsc --noEmit` sem saída; `npx vitest run` → 18 arquivos, 232 testes, todos verdes. `teamKeyLabel.test.ts` cobre rótulo vazio, nome sem e-mail, e-mail único, caixa/espaço, vários separadores, malformado no meio de válidos e os dois casos que aceitam de propósito (rótulo sem e-mail, e-mail desconhecido). |
| A2 | Tabela `team_blocked_accounts` e rotas de bloqueio | Pendente |
| A3 | As duas travas em `authorize()` + auditoria de arranque | Pendente |
| A4 | `verify`/`claim` param de mentir (servidor + cliente) | Pendente |
| A5 | Tela de chaves: e-mail por conta, "Remover do time", seção de bloqueadas | Pendente |
| A6 | Documentação (`server/README.md`, `CLAUDE.md`, versão `0.11.0`) | Pendente |

## Notas de engenharia

**`parseKeyLabelEmails` devolve conjunto, não e-mail único.** A mesma chave cobre a máquina logada
em duas contas da empresa (`maxAccounts` maior que 1), cenário já documentado e em uso; com um
e-mail só no rótulo ele morreria.

**Duas recusas viram `true` de propósito.** Rótulo sem e-mail não declara relação nenhuma, e
inventar uma barraria quem rotula a chave com o nome da pessoa. E-mail da conta desconhecido é
cliente anterior ao campo `accountEmail`, e recusar ali derrubaria máquina que a mudança não
pretende atingir. O segundo é buraco assumido — quem quisesse burlar bastaria omitir o campo, e o
modelo inteiro de chave de time é autodeclarado. Contra isso existe a lista de bloqueio, que é por
`accountKey`.

**`label` entra no `SELECT` da resolução por hash**, não numa busca posterior: a autorização precisa
dele em toda requisição, e `findById` é a consulta mais cara das duas porque decifra a chave crua.

**`accountEmailOf` é a irmã de uma consulta que já existia.** `SELECT_ACCOUNT_EMAILS_SQL` lê a
tabela inteira porque monta uma tela; responder sobre uma conta varrendo todas cresceria com o time
a cada chamada. A memória é confiável porque `upsertAccountEmail` nunca sobrescreve com nulo.

**`TEAM_KEY_LABEL_MATCH` nasce `strict`**, ao contrário de `TEAM_LEGACY_KEY_MODE`, que nasce
`open`. Aquele preservava clientes existentes numa mudança de autenticação; este é a correção de um
defeito, e um default frouxo deixaria o portão desligado em todo deploy que não soubesse da
variável. `off` é a válvula de rollback sem redeploy de código, e desliga **só** o portão.
