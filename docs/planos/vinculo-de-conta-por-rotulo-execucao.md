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
| A2 | Tabela `team_blocked_accounts` e rotas de bloqueio | **Concluída.** `npx tsc --noEmit` sem saída; `npx vitest run` → 18 arquivos, 239 testes, todos verdes (7 novos). `DELETE /admin/v1/accounts/:accountKey` passou a devolver `blocked: true` e a escrever a decisão; `GET`/`DELETE /admin/v1/blocked-accounts` sob `x-admin-token`, com token de relatório e chave de time recusados (401). |
| A3 | As duas travas em `authorize()` + auditoria de arranque | **Concluída.** `npx tsc --noEmit` sem saída; `npx vitest run` → 20 arquivos, 258 testes, todos verdes (19 novos, em `test/api/admission.test.ts` e `test/unit/keyLabelAudit.test.ts`). Cobre: rótulo com um e com dois e-mails, recusa nomeando as duas pontas, vínculo que já existia, leitura pelo e-mail gravado, rótulo sem e-mail, cliente sem e-mail, chave legada fora do portão, `keyLabelMatch=off` e as quatro portas de volta da conta bloqueada (ingest, presença, leitura, chave legada). |
| A4 | `verify`/`claim` param de mentir (servidor + cliente) | **Concluída.** `npx tsc --noEmit` sem saída; `npx vitest run` → 20 arquivos, 264 testes, verdes. `gradlew.bat allTests` → `BUILD SUCCESSFUL in 2m 6s`. As duas rotas passaram a aplicar as mesmas travas do ingest, reusando `assertNotBlocked`/`assertAllowedByLabel`; `accountEmail` viaja na query do `verify` e no corpo do `claim`, e o `FakeRemoteTeamDataSource` passou a **afirmar** que ele chega — inclusive na queda para o `verify` contra servidor sem a rota. |
| A5 | Tela de chaves: e-mail por conta, "Remover do time", seção de bloqueadas | **Concluída.** `npx vitest run` → 20 arquivos, 268 testes, verdes (4 novos de `accountDetails`). `gradlew.bat allTests` → `BUILD SUCCESSFUL in 2m 8s`, com 4 testes de view model e 4 de componente novos. `ConfirmationDialog` saiu de privado na tela de presença e virou `AppConfirmationDialog`, adotada nas duas telas no mesmo commit; registrada em `docs/design-system/components/feedback/` e no índice do `readme.md`, e a §11 do protótipo passou a mostrar a linha divergente, a seção de bloqueadas e o texto novo da confirmação. |
| A6 | Documentação (`server/README.md`, `CLAUDE.md`, versão `0.11.0`) | **Concluída.** `server/package.json` em `0.11.0`; README com `TEAM_KEY_LABEL_MATCH`, o rótulo como relação do time no passo 2 do fluxo, `accountDetails`, as duas rotas de `blocked-accounts`, o novo significado de `DELETE /admin/v1/accounts/:accountKey` e o aviso de que a 0.11.0 corta vínculo divergente no redeploy — com o log de arranque como o que se lê antes. `CLAUDE.md` reescreveu as duas afirmações que deixaram de valer. |

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

**O e-mail da conta bloqueada é retrato, não junção.** `deleteAccount` apaga a linha de
`team_accounts`, então a leitura acontece **antes** do delete; do contrário a lista de bloqueadas
mostraria um UUID cru, que não identifica ninguém para quem vai decidir desbloquear.

**A ordem da remoção é dados, vínculo, bloqueio.** O bloqueio vai por último porque é o passo mais
barato e o único trivialmente reversível: falhar nele deixa a conta apagada e desvinculada, que é
exatamente o estado da versão anterior da rota. Escrever o bloqueio primeiro e falhar depois
deixaria uma conta barrada com o histórico inteiro no banco.

**Desbloquear não restaura dado nenhum.** O histórico foi apagado junto e o cliente daquela máquina
já marcou os turnos como enviados. O que volta é a possibilidade de reivindicar a conta de novo — e
daí em diante ela passa pelo portão do rótulo como qualquer outra.

**O portão é avaliado antes do teste de vínculo, não dentro do ramo que reivindica.** É isso que o
faz valer para os vínculos que já existiam — que é o caso da issue: a conta intrusa já estava
vinculada quando a regra foi escrita. Com a verificação só no caminho do `claim`, ela continuaria
sincronizando para sempre.

**O bloqueio vem antes do portão, e antes até da credencial no caminho de escrita.** A chave legada
em modo aberto retorna cedo em `authorize()` e escreveria sem passar por conta nenhuma — era por ela
que a conta removida voltaria num deploy que ainda não migrou para chaves por pessoa. Nas leituras,
o bloqueio só alcança a chave de time: admin e token de relatório continuam lendo, porque
administrar não é participar.

**As leituras conferem pelo e-mail gravado.** Elas não carregam corpo, então não há e-mail no
pedido; o gravado é a mesma informação vinda da escrita anterior. Nas escritas o e-mail do pedido
vence o gravado — numa máquina que trocou de conta, o gravado descreve a anterior.

**A auditoria de arranque usa a mesma função pura da recusa.** Duplicar a regra no diagnóstico daria
duas respostas para a mesma pergunta, e o diagnóstico existe justamente para prever a recusa.

**Dois testes existentes descreviam o contrato antigo e foram reescritos, não afrouxados.** O de
precedência de e-mail na visão global roda com `keyLabelMatch: 'off'` porque a combinação que ele
monta — rótulo de um e-mail, conta reportando outro — é exatamente a que o portão recusa; o `off`
isola a pergunta dele da admissão em vez de escondê-la. E o de liberar a conta para outra chave
passou a desbloquear antes de reivindicar, que é o contrato novo.

**O "Testar conexão" precisa mandar o e-mail, senão ele mente.** Sem o campo, `verify`/`claim`
responderiam pelo e-mail **gravado**, e numa máquina que nunca enviou nada não há nada gravado: o
botão aprovaria a conta e o envio seguinte a recusaria — a sincronia parada em silêncio que aquelas
duas rotas existem para evitar. O campo é opcional nos dois schemas, então servidor e cliente
antigos continuam se entendendo.

**As duas rotas reusam os asserts de `access.ts`.** Uma segunda cópia da regra daria duas respostas
para a mesma pergunta, e a rota existe justamente para antecipar o veredito do envio. A verificação
vale também para a conta **já vinculada**: no `GET`, um "autorizada" para quem o ingest recusa é a
mentira mais cara que esta rota pode contar.

**`accountDetails` é campo novo ao lado de `accounts`, não no lugar dela.** Mudar a forma da lista
antiga quebraria app anterior; vazia contra servidor mais velho, e aí `accountEntries` cai em
`accounts` e a tela mostra só o UUID, como sempre mostrou.

**O `authorized` respeita `TEAM_KEY_LABEL_MATCH`.** Com o portão desligado toda conta vinculada está
autorizada, e marcar divergência ali anunciaria uma recusa que não vai acontecer.

**As duas listas da tela vêm na mesma carga.** Remover uma conta muda as duas — tira o vínculo de
uma e cria a linha na outra —, e lê-las em momentos diferentes mostraria a tela pela metade depois
de uma remoção. A falha ao ler as bloqueadas **não derruba** a lista de chaves: a seção é acessória,
e contra servidor anterior à 0.11.0 ela é legitimamente vazia.

**A confirmação virou primitiva porque a segunda tela precisou dela.** Ela nasceu privada em
`TeamPresenceScreen`; nenhuma tela reimplementa primitiva, e duas cópias divergiriam no primeiro
ajuste de tom ou de posição dos botões. O commit que a cria e os que a consomem são o mesmo.

**Desvincular continua existindo ao lado de "Remover do time".** São decisões diferentes: a primeira
solta o vínculo e deixa os dados, e o envio seguinte daquela máquina refaz tudo; a segunda encerra.
Fundir as duas faria a ação branda carregar o risco da destrutiva.

**`TEAM_KEY_LABEL_MATCH` nasce `strict`**, ao contrário de `TEAM_LEGACY_KEY_MODE`, que nasce
`open`. Aquele preservava clientes existentes numa mudança de autenticação; este é a correção de um
defeito, e um default frouxo deixaria o portão desligado em todo deploy que não soubesse da
variável. `off` é a válvula de rollback sem redeploy de código, e desliga **só** o portão.
