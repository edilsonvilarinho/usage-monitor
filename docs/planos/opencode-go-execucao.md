# OpenCode Go como fonte de cota — execução

Issue: [#124](https://github.com/edilsonvilarinho/usage-monitor/issues/124).

O planejamento veio no corpo da própria issue (pesquisa do upstream + checklist de cinco itens) e
no comentário com o teste real do endpoint. Este documento registra a execução e as decisões que a
issue deixou em aberto.

## O que entra e o que não entra

| | Fonte | Estado |
|---|---|---|
| **Entra** | Assinatura **Go** — `GET https://opencode.ai/zen/go/v1/usage` | Endpoint em produção, resposta confirmada por chamada real |
| **Não entra** | Saldo pago do **Zen** | Não existe endpoint. `/zen/v1/balance` responde 404 e a issue upstream [#10448](https://github.com/anomalyco/opencode/issues/10448) segue aberta, sem PR e sem ETA |

O item 5 do checklist da issue — acompanhar `#10448`/`#44189` — **continua aberto**, e é por isso que
a issue não fecha com esta entrega.

## Decisões

### Fonte nova, não cota a mais no card do Zen gratuito

`ApiSource.OPENCODE_GO` é valor novo no enum, ao lado de `OPENCODE`. A regra geral do repositório é
não criar valor em enum existente; a exceção aqui é declarada e tem o mesmo desenho da de
`AppUpdateSupport`: os `when` exaustivos sobre `ApiSource` são exatamente os pontos que uma fonte
nova **precisa** preencher, e o erro de compilação é o mecanismo que garante que nenhum ficou para
trás. Foram cinco — `displayName`, `sourceLabelFromKey`, `warningActionFor`, `accentColorFor` e
`fetchTarget`.

Reaproveitar `OPENCODE` não serviria por três motivos independentes:

1. `isObservedActivitySource()` inclui `OPENCODE` e desvia o card inteiro para `OpenCodeUsageSummary`,
   que não desenha barra nem projeção. O Go é percentual com reinício.
2. As unidades são diferentes: requisições observadas contra percentual de janela.
3. Uma máquina pode ter uma das duas sem a outra — quem assina o Go e nunca abriu o OpenCode local,
   e quem usa o gratuito sem assinar nada.

A posição no enum define a ordem padrão do card e da lista de Configurações. Nada persiste o
ordinal: as preferências gravam `ApiSource.name` e `normalizeCardOrder` tolera nome desconhecido,
então inserir no meio não invalida gravação anterior.

### O acento é reusado, não é um sétimo token

`accentColorFor` mapeia `OPENCODE` e `OPENCODE_GO` para `accents.opencode`. O design system fixa
seis identidades de fonte e diz que elas não mudam; o acento identifica o **fornecedor**, e Zen Free
e Go são planos do mesmo produto. Um sétimo tom teria de passar AA 4,5:1 nas duas superfícies e
manter 20° de separação dos outros seis — custo real, pago para distinguir dois planos da mesma
empresa. Quem separa os cards é o título, e "cor nunca informa sozinha" já garante que isso basta.
`AppAccentsContrastTest` não muda.

### 403 de direito de acesso não é erro de credencial

Chave válida numa conta sem o plano Go responde `403 EntitlementError`. Isso é o estado **normal** de
quem só usa o Zen pago: não há nada a corrigir na credencial. `OpenCodeGoRepositoryImpl` traduz esse
403 — e só ele — para uma mensagem própria, que `UiApiError.isOpenCodeGoSubscriptionIssue` reconhece
e `isConfigurationIssue` absorve. O efeito prático é duplo: não sai toast a cada coleta, e o banner
manda assinar ou desligar em vez de pedir novo login.

O banner **não oferece "Tentar novamente"**: repetir a chamada devolveria o mesmo 403.

O 401 continua sendo falha comum, e um 403 que não traga `EntitlementError` nem
`subscription required` — bloqueio de proxy corporativo, por exemplo — também. Traduzir todo 403
esconderia o único caso em que revisar a chave resolve.

A ordem de `warningFor` foi respeitada: 429 e 503 continuam sendo testados **antes** de qualquer
falha de configuração, então um limite de requisições no Go cai no banner de "aguarde".

### Nenhum número é inventado a partir do percentual

A Anthropic converte `utilization` numa capacidade estimada em tokens porque conhece o teto da
janela. A API do Go **não devolve valor gasto nem limite** — foi o que a issue confirmou lendo o
código do PR upstream e o que a chamada real reproduziu. Por isso as cotas saem com
`rawUsed = rawTotal = 0`: uma capacidade inventada apareceria na tooltip do card como um número de
tokens que a API nunca informou.

Pela mesma razão não existe linha de saldo neste card, e o saldo pago do Zen não aparece em lugar
nenhum da interface.

### `status: "rate-limited"` não vira aviso

O campo existe na resposta e **não é mapeado**, deliberadamente. Um `ApiUsageNotice` vive na fonte
inteira e não saberia dizer qual das três janelas está bloqueada; e a janela limitada já chega com o
percentual no teto, que é o que o card mostra. Isso evita um valor novo em `ApiUsageNotice`, cujo
`when` exaustivo no card obrigaria um texto para uma condição que a barra já comunica.

### Janela ausente e janela sem reinício degradam, não derrubam

O endpoint não é documentado publicamente e não declara versão — o mesmo risco já aceito no `usage`
da Anthropic. Os campos do DTO são todos opcionais:

- janela que não vier **some do card** e as outras duas continuam;
- `resetsAt` ausente ou ilegível vira `hasKnownResetAt = false`, o que tira só a projeção;
- resposta **sem nenhuma** das três janelas **falha**, e isso é intencional: é contrato mudado, não
  conta zerada, e falhar preserva o último valor em cache em vez de apagá-lo com uma leitura que não
  mediu nada.

### A chave é a mesma do Zen, e o campo diz o consumidor

`ApiKeySettings.openCodeGo` guarda a chave da API do OpenCode — a mesma usada no `chat/completions`
do Zen. O nome do campo é o do consumidor porque hoje só o Go a lê: o plano gratuito vem do SQLite
local, sem credencial. `LocalApiKeyDataSourceTest` afirma que `forSource(OPENCODE)` continua nulo com
a chave do Go gravada.

O conjunto de fontes que dependem de chave virou `API_KEY_DEPENDENT_SOURCES` em `Main.kt`. Ele já
tinha dois donos (o filtro de arranque e `requiresApiKey` nas Configurações); um terceiro literal era
onde a próxima fonte ia ser esquecida.

## Pontos de situação

| # | Atividade | Comando | Resultado |
|---|---|---|---|
| A01 | Enum, rótulos, acento e os cinco `when` exaustivos | `gradlew.bat compileKotlinDesktop` | Compilou sem erro |
| A02 | DTO, mapper, chamada HTTP e repositório com o 403 traduzido | `gradlew.bat compileKotlinDesktop` | Compilou sem erro |
| A03 | Chave em `api-keys.json`, diálogo nas Configurações e fiação no `Main.kt` | `gradlew.bat compileTestKotlinDesktop` | Compilou sem erro |
| A04 | Testes de mapper, repositório, HTTP, chave e banners | `gradlew.bat desktopTest --tests "com.usagemonitor.data.OpenCodeGo*" --tests "com.usagemonitor.data.RemoteApiDataSourceHttpTest" --tests "com.usagemonitor.data.LocalApiKeyDataSourceTest" --tests "com.usagemonitor.presentation.ui.DashboardScreenWarningsTest"` | `BUILD SUCCESSFUL` |
| A05 | Testes de componente (card, aba APIs, diálogo de chave) | `gradlew.bat desktopTest --tests "com.usagemonitor.ui.ComponentTest"` | Falhou na primeira passada: o teste do card afirmava o rótulo da cota (`Go 5h`), mas o card monta o título por `expandedQuotaTitle` a partir do `periodType` (`Sessão 5h` / `Semanal` / `Mensal`). Corrigido o teste; `BUILD SUCCESSFUL` |
| A06 | Suíte completa | `gradlew.bat allTests` | `BUILD SUCCESSFUL` |
| A07 | Protótipo, design system e UI kit | — | Card do Go e o estado "sem assinatura" no §4; duas linhas de OpenCode na aba APIs; `readme.md` com "seis acentos, sete fontes"; `Dashboard.jsx` e `Settings.jsx` atualizados |

## O que ficou de fora, e por quê

- **Saldo pago do Zen.** Sem endpoint. Bloqueado no upstream.
- **Valores em dinheiro do Go.** A API não os devolve.
- **Grade por hora e ferramentas.** Não se aplica: a fonte é de cota, não de sessão indexada.
- **Fechamento da issue #124.** O item de acompanhamento do saldo Zen continua válido.
