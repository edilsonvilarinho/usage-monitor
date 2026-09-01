# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bat
# Rodar a aplicação Desktop
gradlew.bat run

# Compilar sem rodar
gradlew.bat desktopJar

# Todos os testes (domain + data + ViewModel + UI)
# A task raiz `test` não existe neste projeto KMP — use `allTests`.
gradlew.bat allTests

# Apenas testes do commonTest (domain, mappers, ViewModel)
gradlew.bat desktopTest --tests "com.usagemonitor.domain.*"
gradlew.bat desktopTest --tests "com.usagemonitor.data.*"
gradlew.bat desktopTest --tests "com.usagemonitor.presentation.*"

# Apenas testes de componente UI (desktopTest)
gradlew.bat desktopTest --tests "com.usagemonitor.ui.*"

# Forks paralelos: o default e 1. So ligue numa maquina com `~/.skiko` ja
# populado -- num cache frio os forks se atropelam extraindo a nativa do Skiko.
gradlew.bat allTests -PtestForks=4

# Cobertura: a instrumentacao do Kover e opt-in, senao custa 6-7s por passada
# para produzir um numero que ninguem le. So o push na `main` liga isto no CI.
gradlew.bat allTests -Pcoverage
gradlew.bat koverHtmlReport -Pcoverage

# Limpar build cache
gradlew.bat clean
```

Servidor de time (`server/`, opcional — só quem usa a integração com time):
```bash
cd server
npm install
npm test        # vitest + supertest
npm run dev     # http://localhost:3000
```

## Arquitetura

KMP Desktop (JVM único alvo). Código organizado em três camadas com dependências unidirecionais: `presentation → domain ← data`.

### Source sets

| Source set | Conteúdo |
|---|---|
| `commonMain` | domain + data (exceto leitura de ficheiros) + presentation/UI |
| `desktopMain` | `LocalCredentialDataSource` (usa `java.io.File`) + `Main.kt` (DI + janela) |
| `commonTest` | Testes unitários de domain, mappers e ViewModel |
| `desktopTest` | Testes de componente Compose (`runDesktopComposeUiTest`) |

### Camada domain (`commonMain/domain/`)

Núcleo puro — **zero imports de Ktor, Compose ou bibliotecas externas**.

- `QuotaInfo`: entidade com `percentageUsed` e `remaining` calculados. `UsageUnit` diferencia `TOKENS`, `REQUESTS` (MiniMax), `PERCENTAGE` (Anthropic/Codex) e `CURRENCY_USD` (saldo DeepSeek). `currencyCode` (default `"USD"`) diz em que moeda os valores monetários estão. **Não crie valores novos em `UsageUnit`**: os `when` exaustivos do card, do histórico e do gráfico quebram; campo novo com default é retrocompatível, valor de enum novo não é.
- `ApiUsageStats`: agrega lista de `QuotaInfo` por API.
- Interfaces `AnthropicRepository` / `MiniMaxRepository`: o domain define o contrato; `data` implementa.
- Use cases usam `operator fun invoke()` — chamados como `useCase()`.

### Camada data (`commonMain/data/` + `desktopMain/data/`)

- DTOs com `@Serializable` + `@SerialName` para mapear snake_case do JSON.
- `LocalCredentialDataSource` e `LocalApiKeyDataSource` ficam em **desktopMain** (usam `java.io.File`). O primeiro lê `~/.claude/.credentials.json` → `claudeAiOauth.accessToken`; o segundo persiste as chaves MiniMax/DeepSeek em `~/.usage-monitor/api-keys.json`, com escrita atômica e acesso restrito ao usuário. A origem Anthropic sai de `AnthropicCredentialStore`: o ficheiro tem prioridade sempre; **no macOS**, quando ele não existe, cai na entrada `Claude Code-credentials` do Keychain via `security` (só para o perfil padrão — perfis de `CLAUDE_CONFIG_DIR` não têm entrada lá). A gravação pós-refresh volta para a mesma origem.
- **Renovação do token OAuth** (`LocalCredentialDataSource.refreshToken`): o corpo do `POST` precisa de `client_id` e `scope` além de `grant_type`/`refresh_token`. O endpoint valida o **formato** antes de olhar o grant: sem `client_id` responde `HTTP 400 "Invalid request format"` com qualquer refresh token, e era por isso que a renovação nunca acontecia — o app dependia do CLI para renovar e a tela pedia login a cada ~8h (issue #64). `OAUTH_REFRESH_URL` (`platform.claude.com/v1/oauth/token`), o client id e os escopos default são **espelhados da configuração de produção do binário do CLI**; o client id é público, vem embutido no binário distribuído. O `scope` sai dos escopos do próprio ficheiro quando existem — pedir a lista fixa numa conta com menos escopos seria pedir permissão que ela não concedeu.
  - **O status HTTP é checado antes de desserializar.** `TokenRefreshResponse` tem todos os campos opcionais e o `HttpClient` não liga `expectSuccess`: o corpo de erro `{"type":"error",...}` desserializa **sem lançar**, e a falha chegava à tela como "sem access_token", sem status nem motivo. Foi por isso que o episódio de abril de 2026 (commit `198a0a7`, logs depois removidos) não deixou rastro.
  - **A regravação é patch de `JsonObject`, não `encodeToString(CredentialsFileDto)`.** O ficheiro tem nós que o app não declara — `mcpOAuth` (autenticação dos MCP servers) e `refreshTokenExpiresAt` —, e o roundtrip pelo DTO com `ignoreUnknownKeys` os apagava em silêncio. Só `accessToken`/`refreshToken`/`expiresAt` são substituídos. Ao acrescentar campo ao `OAuthCredentialsDto`, pergunte se a escrita ainda preserva o que ele não conhece.
- `RemoteApiDataSource`: Anthropic faz `GET /api/oauth/usage` (headers `anthropic-beta: oauth-2025-04-20`, `User-Agent: claude-code/1.0.0`) e lê a utilização das janelas direto do corpo JSON. MiniMax faz `GET /v1/token_plan/remains`.
- **Créditos de uso (Anthropic)** (`AnthropicCreditsResolution.kt`): `extra_usage` é a fonte primária — `monthly_limit` e `used_credits` vêm em unidades menores da moeda (55000 = R$ 550,00) e `currency` traz a moeda real da conta, que **não é sempre USD**. O rótulo `AnthropicQuotaLabels.EXTRA_CREDITS` é chave da série histórica e não pode ser renomeado.
  - **`spend` é fonte secundária, não só reforço.** Ele descreve o mesmo gasto (`amount_minor`/`currency`/`exponent`) e cobre quando `extra_usage` vem sem `monthly_limit` ou não vem. Em agosto de 2026 a resposta parou de trazer os créditos por cinco dias e a linha sumiu da tela, do cache e do histórico sem deixar rastro — o `return null` único não distinguia "conta sem créditos" de "contrato mudou". O `percent` do `spend` continua fora do cálculo: chega arredondado, e o `utilization` do `extra_usage` não.
  - **`is_enabled` falso continua escondendo a linha em silêncio** — é o estado normal de quem não contratou créditos, e avisar ali viraria alerta permanente. Só `LIMIT_ABSENT` e `UNSUPPORTED_EXPONENT` (`AnthropicCreditsOutcome.signalsFailure`) viram `ApiUsageNotice.EXTRA_CREDITS_UNAVAILABLE`, que a `ApiUsageCard` mostra **também com o card minimizado**: foi o card fechado que escondeu o episódio. O aviso vive no **cabeçalho**, que é composto nos dois estados — ver `CardNoticeHint`.
  - **Expoente monetário diferente de 2 não vira cota.** `formatCents` assume duas casas; aceitar outro expoente mostraria o valor errado por um fator de dez, o que é pior que omitir a linha.
  - **Diagnóstico opt-in** (`USAGE_MONITOR_DEBUG_ANTHROPIC_CREDITS=1` → `~/.usage-monitor/diagnostics/anthropic-credits.jsonl`, mesmo desenho do recorder do Codex): guarda os nós `extra_usage` e `spend` **crus**, porque campo derivado não revela campo renomeado. Com o registro desligado o corpo continua sendo lido uma vez só, pelo `ContentNegotiation`.
- `MiniMaxRepositoryImpl`, `DeepSeekRepositoryImpl` e `OpenCodeGoRepositoryImpl` recebem o leitor da chave por injeção; no desktop ele lê exclusivamente `LocalApiKeyDataSource`. Nunca hardcode credenciais nem leia variáveis de ambiente para essas integrações. O conjunto das fontes que dependem de chave é `API_KEY_DEPENDENT_SOURCES` (`Main.kt`), e não um literal repetido: ele já tinha dois donos — o filtro de arranque e `requiresApiKey` das Configurações —, e o terceiro seria onde a fonte seguinte ficaria esquecida.
- Ambos os repos usam `Result.runCatching { }` para encapsular falhas.
- **OpenCode Go** (`OpenCodeGoRepositoryImpl` + `OpenCodeGoMapper`; plano [`opencode-go-execucao.md`](docs/planos/opencode-go-execucao.md), issue #124): a assinatura paga, lida de `GET /zen/go/v1/usage` com a chave da API do OpenCode. É **fonte própria** (`ApiSource.OPENCODE_GO`), ao lado de `OPENCODE`, que é o plano gratuito do Zen lido do SQLite local — exceção declarada à regra de não criar valor em enum existente, pela mesma razão de `AppUpdateSupport`: os cinco `when` exaustivos sobre `ApiSource` são justamente os pontos que a fonte nova precisa preencher, e o erro de compilação garante que nenhum ficou para trás. Reaproveitar `OPENCODE` faria `isObservedActivitySource()` desviar o card para o resumo sem barras, misturaria requisições com percentual, e ignoraria que uma máquina pode ter uma das duas sem a outra.
  - **O acento é reusado, não é um sétimo token.** `accentColorFor` manda `OPENCODE` e `OPENCODE_GO` para `accents.opencode`: o acento identifica o **fornecedor**, o sistema visual fixa seis identidades e diz que elas não mudam, e um sétimo tom teria de passar AA 4,5:1 nas duas superfícies mantendo 20° dos outros seis. Quem separa os cards é o título.
  - **`403 EntitlementError` não é erro de credencial.** É o estado normal de quem só usa o Zen pago, e o repositório o traduz para mensagem própria que `isConfigurationIssue` absorve — sem toast a cada coleta, e com banner que manda assinar ou desligar. O banner **não oferece "Tentar novamente"**: repetir devolve o mesmo 403. O 401 e o 403 sem `EntitlementError`/`subscription required` (proxy corporativo) continuam falha comum, senão o único caso em que revisar a chave resolve ficaria escondido.
  - **Nenhum número é derivado do percentual.** A API não devolve valor gasto nem limite, então `rawUsed`/`rawTotal` ficam em zero — ao contrário da Anthropic, que converte `utilization` numa capacidade porque conhece o teto. Capacidade inventada apareceria na tooltip como tokens que a API nunca informou.
  - **`status: "rate-limited"` não é mapeado**, deliberadamente: `ApiUsageNotice` vive na fonte inteira e não diria **qual** das três janelas está bloqueada, e a janela limitada já chega com o percentual no teto.
  - **Janela ausente degrada, resposta vazia falha.** O endpoint não é documentado nem versionado, então todo campo do DTO é opcional: janela que não vier some do card, `resetsAt` ilegível vira `hasKnownResetAt = false`. Mas resposta **sem nenhuma** das três é contrato mudado e não conta zerada — falhar preserva o cache em vez de apagá-lo com uma leitura que não mediu nada.
  - **O saldo pago do Zen continua fora**: não existe endpoint (`/zen/v1/balance` responde 404; issue upstream anomalyco/opencode#10448 aberta). É por isso que a #124 não fecha com esta entrega.
- **Proxy HTTP corporativo** (`ProxySettings.kt` + `ProxyResolution.kt` + `LocalProxySettingsDataSource.kt` + `HttpClientFactory.kt`; issue #174): configuração de proxy para o `HttpClient` único do app, que até então não tinha nenhuma. Precedência resolvida por `resolveEffectiveProxy`: manual explicitamente ligado (`ProxySettings.useEnvironmentProxy = false`) vence sobre `HTTPS_PROXY`/`HTTP_PROXY` do ambiente (`parseProxyEnvironmentValue`, convenção de shell — curl, npm, pip —, não uma API documentada), que vence sobre nenhum proxy. `NO_PROXY` fica fora do escopo.
  - **Só Basic auth.** NTLM exigiria dependência própria que o OkHttp não traz nativamente, e a própria issue trata como caso de borda a validar só com proxy corporativo real. Documentado como limitação na própria aba, não escondido.
  - **A configuração só vale depois de reiniciar o app.** O `httpClient` compartilhado (`Main.kt`, `buildHttpClient`) é montado uma única vez no arranque, com o proxy já resolvido, e é usado por 5+ consumidores com laços próprios (dashboard, sincronização de time, atualização automática) — recriar o engine em runtime arriscaria `ClosedException` numa requisição in-flight de qualquer um deles. Por isso `LocalProxySettingsDataSource` é lido **antes** do bloco do `httpClient`, não depois como os demais data sources de configuração.
  - **"Testar conexão" nunca usa o client compartilhado.** Monta um `HttpClient` efêmero com o valor corrente de `proxySettingsFlow` (já commitado pelos campos da seção, mesmo sem reiniciar) contra um endpoint leve e sem credencial (`https://api.github.com/zen`), e fecha o client depois — é a única forma de dar feedback imediato sem esperar o reinício.
  - **O `Authenticator` do proxy verifica se a requisição já carrega `Proxy-Authorization`** antes de responder ao desafio 407 de novo: sem esse guard, uma senha errada faz o OkHttp reenviar a mesma credencial recusada para sempre. HTTP 407 chega como resposta HTTP normal (`RemoteApiDataSource.requireSuccess` já vira `IllegalStateException`) e cai no mesmo mecanismo de marcador por substring dos demais status (`isProxyAuthIssue`, conta como `isConfigurationIssue` — é credencial errada, só que do proxy).
  - **Falha de conectividade (DNS, timeout de conexão, proxy inalcançável) é classificada por TIPO de exceção, não por substring da mensagem** — texto de `ConnectException`/`SocketTimeoutException` varia por JVM e SO. A checagem mora em `DashboardViewModel.handleTargetFailure`, o funil único de toda falha de coleta, e embute um marcador fixo (`NETWORK_CONNECTIVITY_MARKER`, mesmo desenho de `HTTP_RATE_LIMIT_MARKER`) que `UiApiError.isConnectivityIssue`/`warningFor` consomem por substring — sem precisar de um enum de erro novo. Categoria própria, **fora** de `isConfigurationIssue`: a causa não é credencial errada, e classificar como configuração orientaria a revisar login em vez de proxy.
  - **O banner de conectividade força retry universal** (`DashboardWarning.forcesUniversalRetry`): sem essa marca, só a Anthropic tem botão de retry em `warningActionFor`, e o usuário atrás de proxy corporativo — cujas 8 fontes falham juntas por conectividade — perderia o "Tentar novamente" que o erro genérico já oferecia antes da classificação existir.

### Camada presentation (`commonMain/presentation/`)

- `UiState`: `sealed interface` com `Loading`, `Success(data)`, `Error(message)`. Se uma API falhar e outra tiver sucesso, emite `Success` com dados parciais.
- `DashboardViewModel`: `StateFlow<UiState>` + polling silencioso via `while(true) + delay(10 * 60 * 1_000L)`. Escopo com `SupervisorJob` — falha de uma coroutine não cancela as outras. Chamar `onDestroy()` ao fechar janela.
- Componentes UI: **todos stateless** (recebem dados via parâmetros, emitem eventos via lambdas). `DashboardScreen` é o único stateful.
- Timezone de reset: sempre `TimeZone.of("America/Sao_Paulo")` com label `BRT`.
- **Copiar sessão** (`CopySessionCommandButton`): a tela mostra o id truncado em 8 (`shortSessionId`), que **não** retoma nada — `claude --resume` só volta direto para a conversa com o session ID inteiro; com um prefixo ele cai no seletor interativo. Por isso o botão copia `claude --resume <uuid completo>` (`resumeSessionCommand`). No modal do time o transcript é de outra máquina, então ali `isLocalSession = false` copia só o identificador — comando que cairia num seletor vazio seria pior que botão nenhum. A escrita no clipboard passa por `rememberClipboardWriter()`, injetável, para o teste de componente não apagar o clipboard de quem roda a suíte.
- **Semáforo de sessão** (`SessionPulseViewModel`, laço de 30s): faz os botões de Sessões CLI e de time do card piscarem quando há sessão com turno nos últimos 5 min (`ACTIVE_SESSION_WINDOW_MILLIS`) e veredito `ATTENTION`/`SATURATED`. O corte de 5 min é `sinceEpochMillis` na consulta, **nunca** um valor novo em `CliSessionRange` — os `when` exaustivos dos chips quebrariam. O laço indexa antes de ler, senão a latência seria a do laço de background (10min). Com a janela minimizada (`isAppVisible` falso) a passada **local continua** — é ela que alimenta o alerta de sessão saturada, cujo destinatário é justamente quem não está olhando a tela; o que fica suspenso é a leitura do time (`refreshOnce(includeTeam = false)`), uma requisição por conta a cada 30s sem ninguém para ver o pisca. Os pulsos de time guardados envelhecem mesmo assim, senão voltariam acesos ao restaurar a janela. Leitura que falha **mantém** o pulso anterior; quem o apaga é `SessionPulse.prunedAt`, pela idade dos alertas — sem isso um servidor de time fora do ar deixaria o botão piscando indefinidamente. `sessionPulseFrame` é função pura (fase → severidade + alpha) para o pisca ser testável sem Compose, e `rememberSessionPulseFrame` **não cria transição infinita** sem pulso: uma animação sem fim trava o `waitForIdle` dos testes de componente.

- **Resumo por eixo** (`CliUsageBreakdown.kt` + `GetCliUsageBreakdownUseCase` + aba na tela de Sessões CLI): consumo da janela recortado por projeto (`cwd`), branch e modelo, mais a economia agregada do cache. As colunas já existiam no índice e nunca eram somadas.
  - **A query nova é a irmã de `SELECT_SESSIONS_SINCE_SQL`**: mesmo `GROUP BY (session_id, model)` e mesmo corte. É isso que garante que o total do resumo bata com o do cabeçalho da lista; divergência ali significa que alguém abriu um segundo caminho de precificação.
  - **O custo é recalculado dos tokens com `ModelPricingTable`**, nunca rateado a partir de `cli_sessions.cost_micros` — o índice só guarda custo por sessão, e ratear entre modelos é justamente o que o resumo existe para evitar. Somar tokens antes de precificar é exato: `costMicros` soma os produtos e divide uma vez.
  - **As três listas descrevem os mesmos turnos**; somar baldes de listas diferentes contaria o mesmo gasto três vezes, e a tela diz isso em texto.
  - Ordem total e determinística (custo desc, tokens, rótulo) pela razão de sempre: duas leituras iguais têm de dar listas iguais, ou o `StateFlow` reemite e a tela recompõe a cada tique.
  - **Modelo sem tarifa não vira custo zero silencioso**: `unpricedTurnCount` sobe e o valor exibido leva `+`, marcando piso e não total.
  - A aba só é lida quando o usuário a abre, e o laço ao vivo só a recalcula com ela aberta — um `GROUP BY` sobre a tabela de turnos a cada 5s sem ninguém olhando seria desperdício. Leitura que falha **mantém** os números anteriores e publica só a mensagem.
  - **Grade de atividade** (`CliActivityHeatmap`): a query agrupa por `ts / 3_600_000`, que é hora **UTC**; quem traduz para hora local é o domain, com `America/Sao_Paulo`. Agrupar em SQL deslocaria tudo em três horas — o bastante para trocar a madrugada pela noite anterior. A intensidade é do **custo** e relativa ao pico da própria janela: escala fixa apagaria o padrão tanto num dia calmo quanto numa semana cheia. Falha na grade **não derruba** o resumo; ela é acessória.
  - **Ritmo de queima** (`burnRateOf`): divide pelo **tempo decorrido** desde o início da janela, nunca pela duração nominal — cinco horas fixas subestimariam o ritmo em toda a primeira hora, justamente quando o aviso serviria. Abaixo de `MIN_BURN_RATE_ELAPSED_MILLIS` (5 min) não há ritmo: um turno caro num minuto daria "US$ 60/h", verdadeiro na aritmética e falso como previsão. Sem `resets_at` conhecido não há projeção. Mede tokens e dinheiro reais dos turnos — grandeza **diferente** de `UsageHistorySeries.averageDisplayConsumptionPerHour`, que é percentual de quota sobre snapshots; a UI rotula as duas separadamente.
  - `projectNameFromCwd` é compartilhada com `CliSessionSummary.projectName`: duas derivações do mesmo caminho divergiriam em algum caso de borda e a tela mostraria dois rótulos para um projeto só.
  - **Um eixo por aba, com filtro, ordem e paginação** (`CliUsageBreakdownPaging.kt` + `CliUsageBreakdownPane`): as seções empilhadas somavam projeto + modelo + branch + ferramentas + grade numa coluna só, e em produção isso é rolagem sem fim. `BreakdownAxis` é enum próprio — `CliSessionsView`/`TeamUsageView` escolhem a tela, este escolhe o recorte dentro dela, e um enum só carregaria combinações que não existem. **Só entram as abas com dado**: no modal do time não há ferramenta nem grade. `ACTIVITY` não tem filtro nem paginador porque não é lista — controle desligado é pior que controle ausente. `BreakdownSort` tem três valores e não seis: baldes e ferramentas respondem à mesma pergunta com números diferentes, e o rótulo da opção muda com o eixo (a ferramenta não tem custo nem tokens).
  - **O filtro casa por trecho e contra o texto exibido**, não contra o rótulo cru: o que identifica um caminho está no meio, e filtrar pelo `null` esconderia a linha que a tela chama "Sem branch".
  - **Preso na tela fica só o cromo pequeno** — abas e, numa faixa só, filtro, ordem e paginação. Os três escolhem parâmetros do mesmo conteúdo, e o paginador no rodapé era a primeira coisa a sair da tela numa janela baixa, justamente quando a lista é longa e ele serve para alguma coisa. Os totais entram na área rolável, pela mesma razão invertida: ~200dp de altura fixa no topo empurram a lista para fora. Filtro sem resultado **não apaga os totais**, que continuam verdadeiros.
  - **As linhas do eixo são tabela, não card**: uma faixa de legendas e `AppDataRow` por balde, com as colunas de `CliUsageBreakdownPane`. A coluna de tempo ativo aparece **uma vez para a lista inteira** ou não aparece — modelo e ferramenta não têm hora, e coluna que existe em algumas linhas e some em outras desloca tudo o que vem depois.
  - **A página se prende ao intervalo que existe** (`paginate`): a lista encolhe a cada tique do laço ao vivo, e a página que sumiu mostraria vazio em vez do fim dos dados. Lista vazia continua tendo uma página — "página 1 de 0" não é uma frase. Trocar eixo, filtro, ordem ou tamanho volta para a primeira página, via `remember(chaves)`.
  - **Filtro, ordem e página moram num `remember` da pane, não no ViewModel.** Ali só ficam as escolhas que a carga precisa conhecer; estas não mudam o que é lido do índice nem do servidor, e o `remember` sobrevive às emissões do laço ao vivo porque a pane não sai da composição entre elas.

- **Configurações em abas** (`SettingsTab` em `SettingsDialogContent.kt`): as seções (Geral, Alertas, APIs, Contas, Time, Rede — esta última da issue #174) eram uma coluna única de cartões empilhados, e achar qualquer uma exigia rolagem. Enum próprio e **navegação lateral**, com a coluna de seções fixa à esquerda — ela é o controle, e o conteúdo rolando não pode tirá-la da vista. **Só a aba escolhida entra na composição**, e não apenas fica fora da vista: com todas montadas as abas seriam decoração sobre a mesma coluna. A escolha mora num `remember` do próprio diálogo, que é uma janela separada e cujo estado nenhuma outra parte do app precisa conhecer; `initialTab` existe para os geradores de captura escolherem a seção. Cada aba tem seu próprio `ScrollState` começando no topo — reaproveitar um só faria a aba curta abrir rolada pela posição que a longa deixou.
  - **Cada aba monta o próprio painel** (`AppDataSurfaceFlush` + `AppSectionHeader`), com uma linha de dados por opção: rótulo em mono, descrição em sans, controle à direita, divisória entre elas. Não existe mais um cartão genérico envolvendo tudo — sete controles empilhados sem divisória e com todo rótulo em sans obrigavam a ler a lista inteira para achar uma opção.
  - **Ação que age sobre a lista inteira vai no `trailing` do cabeçalho** — "Redetectar" e "Adicionar" na aba Contas, o interruptor da integração na aba Time. No corpo elas competiam com as linhas.
  - **`PRIMARY` é uma por tela.** A aba Time tinha três botões primários ao mesmo tempo; primária é a ação que a tela propõe, e três delas não propõem nada.
  - **Tema é segmentado, não interruptor.** Interruptor diz ligado/desligado, e tema é escolha entre duas alternativas — a mesma pergunta que o seletor de idioma logo abaixo já respondia com um segmentado. O rótulo com emoji que existia ali era o único emoji da interface.

- **Banner de erro por alvo** (`DashboardWarning.target` + `warningTargetLabel`): o título usa `UiApiError.targetLabel` ("Anthropic — <perfil>") e cai no rótulo da fonte só quando não há alvo nomeado. Com várias contas Anthropic o título fixo produzia dois banners textualmente idênticos e ninguém sabia qual conta falhou. A ação recarrega **só o alvo do banner** (`refresh(target)`), não a fonte inteira — refazer a coleta dos perfis saudáveis é justamente o custo que um botão por banner evita. A ordem dos testes em `warningFor` importa: 429 e 503 são avaliados **antes** de credencial, então renovação que falha por limite ou indisponibilidade continua no banner de "aguarde" em vez de pedir login.

- **Aviso de recarga** (`isRefreshing` nos dois `Success` + `REFRESHING_NOTICE_TAG`): trocar a janela não passa por `Loading` — apagar a tela a cada clique é o pisca que o laço ao vivo existe para evitar. Mas sem aviso os números da janela **anterior** ficam na tela durante a ida ao servidor, e quem clicou em "30 dias" lê o total de "5h" como resposta. Só a ação do usuário liga a bandeira; o tique de 5s não, senão o aviso piscaria de cinco em cinco segundos e viraria ruído. É **texto, não indicador animado**: animação infinita trava o `waitForIdle` dos testes de componente. Na tela da máquina lista e resumo são duas leituras, e `breakdownReloadPending` segura o aviso até as duas chegarem — sem ele a primeira a terminar apagaria o aviso com o resumo antigo ainda na tela. Falha também apaga o aviso: a espera acabou, e o que resta é o erro, que tem linha própria.

- **Métricas por ferramenta** (`cli_turn_tools` + `CliToolUsage`): `ClaudeTranscriptMessageDto.content` é lido como `JsonElement` **cru** porque o campo é polimórfico — array de blocos nas linhas do assistente, string em outras; um tipo fixo faria o parse da linha inteira falhar num dos dois casos. Só o **nome** da ferramenta é extraído, nunca o `input` nem o texto. A tabela nasceu vazia para tudo que já estava indexado, e `syncIndex` só reprocessa arquivo alterado: por isso existe `INDEX_SCHEMA_VERSION` — subir o número força a releitura completa. O reset não apaga sessões nem turnos, que voltam pelos mesmos `INSERT OR IGNORE`/`OR REPLACE` e não duplicam. **O contador mora em `cli_index_meta`, não no `PRAGMA user_version`**, e o reset **apaga as linhas de `cli_session_files`**, não os offsets: as duas coisas juntas eram o motivo de a versão 1 nunca ter chegado a rodar. O `user_version` é um valor único por arquivo e o histórico de uso, que vive no mesmo `.db`, grava `3` ali a cada abertura — o índice lia esse número alheio como seu e concluía que já havia migrado. E zerar `last_offset` era inerte porque `syncIndex` pula por tamanho + data de modificação **antes** de consultar o offset; sem a linha do arquivo a varredura o trata como novo. **Ferramenta não entra em custo**: um turno que chama `Read` e `Bash` gastou tokens uma vez só, e ratear o custo entre as duas contaria o mesmo gasto duas vezes — a tela diz isso.

- **Conta da sessão** (`cli_sessions.profile_id`): os transcripts não carregam identidade, então a conta vem da raiz de onde o arquivo foi lido. Ela é gravada em **dois** lugares e nenhum é dispensável. `recomputeSession` só roda para sessão com turno novo, e um arquivo reprocessado só porque mudou de conta não traz turno nenhum: por isso `indexFile` carimba as sessões do arquivo **fora** do laço de `touchedSessions`. E `backfillSessionProfile` roda em **toda abertura**, não só quando a coluna é criada, porque foi exatamente assim que 58 sessões ficaram com `profile_id` nulo — o arquivo foi carimbado, a sessão não, e a partir daí a varredura passou a pulá-lo. Conta nula não é "todas as contas": `profile_id = ?` não casa com nulo e a sessão some de toda tela, sem erro nenhum. Ao acrescentar coluna nova em `cli_sessions`, pergunte por quem a preenche num arquivo que nunca mais vai mudar.

- **Tempo ativo de sessão** (`activeTimeMillisOf`): soma só os intervalos entre turnos consecutivos **menores** que `TURN_GAP_CUTOFF_MILLIS`, que é o mesmo `ACTIVE_SESSION_WINDOW_MILLIS` de 5 min já usado pelo semáforo — um segundo corte para a mesma pergunta daria duas respostas. Sem o corte, "duração" seria a distância entre o primeiro e o último turno e uma sessão retomada no dia seguinte "duraria" vinte horas. Só a thread principal: o subagente roda em paralelo e somar os intervalos dele contaria em dobro. Sessão de um turno não tem intervalo para medir e a métrica **não aparece** — "0min" seria lido como sessão instantânea.

- **Tempo ativo agregado** (`CliSessionSummary.activeMillis` + `CliUsageBucket.activeMillis`): a mesma definição de `activeTimeMillisOf` — soma dos intervalos entre turnos consecutivos da thread principal menores que `TURN_GAP_CUTOFF_MILLIS` — agora também por sessão da janela, por projeto, por branch e por integrante do time.
  - **`null` é "não medido" e zero é "medido e sem intervalo".** Colapsar os dois faria a tela afirmar "não trabalhou" onde a resposta certa é "não se sabe": sessão de um turno só dá zero, servidor de time anterior à 0.7.0 dá nulo.
  - **A consulta é `activeTimeMillisOf` escrita em SQL** (`SELECT_SESSION_ACTIVE_TIME_SQL`, com `LAG` sobre `cli_turns`), e a equivalência entre as duas é afirmada por teste, não deduzida da semelhança. O corte de 5 min vai **ligado como parâmetro**, nunca literal no SQL: a constante continua morando no domain. `is_sidechain = 0` porque o subagente roda em paralelo e somar os intervalos dele contaria o mesmo tempo duas vezes.
  - **Os eixos de modelo e de ferramenta ficam sempre nulos.** O intervalo entre dois turnos não pertence a um modelo; ratear inventaria número. Só projeto, branch e integrante — eixos em que a sessão inteira cai num balde só — têm hora. Por isso `toUsageBreakdown` recebe o mapa `sessão → millis` e o soma dentro do mesmo conjunto de sessões distintas que já alimenta `sessionCount`: uma sessão com três modelos entra com a hora dela uma vez só.
  - **No time o cálculo é do servidor** (`activity` em `GET /v1/team`, 0.7.0+), porque ele agrega por `(máquina, sessão, modelo)` e nunca mandou carimbo de turno. Lista **separada** de `rows`, nunca coluna dela, pelo mesmo motivo do parágrafo acima. O corte viaja do cliente em `gapCutoffMs` — o servidor não pode ser um segundo dono da constante, pelo mesmo princípio que já o mantém sem tabela de preços. Servidor antigo ignora o parâmetro e omite o campo; o cliente lê isso como hora não medida, sem gate por 404: é campo novo em rota existente, não rota nova.

- **Relatório PDF** (`presentation/ui/report/` + `desktopMain/PdfUsageReportRenderer.kt`): o recorte que está na tela em PDF, nas duas telas de sessões. **O documento é montado em `commonMain` e o desenho fica no desktop** — o PDFBox é JVM-only, e a divisão faz o *conteúdo* do relatório ser testável sem gerar um byte de PDF; o que o renderizador pode errar é só desenho.
  - **Não é um valor a mais em `UsageExportFormat`.** Os `when` daquele enum são sobre formato de texto, e um `PDF` ali obrigaria um ramo impossível em cada um. O PDF entra como ação própria (`exportReport`), e `UsageExportRequest` passa a carregar um `UsageExportPayload` — `Text` para CSV/JSON, `Report` para o documento.
  - **O relatório não segue a aba**, ao contrário do CSV: ele é a janela inteira, com totais, eixos e sessões juntos. Na tela da máquina isso obriga `exportReport` a **carregar o resumo se ele ainda não foi lido** — um PDF sem a seção de projetos surpreenderia mais que a espera. No time o resumo já vem na mesma resposta da lista, então não há o que carregar.
  - **Todo texto passa por um saneamento único** (`UsageReportDocument.sanitized`): o Helvetica base-14 só escreve WinAnsi, e um caractere fora dele faz o PDFBox lançar no meio da escrita — o relatório inteiro morreria por causa de um emoji num nome de pasta. Acento português passa direto; o resto vira `?`.
  - **`java.logging` entra na lista do jpackage** por causa do `commons-logging` que o PDFBox traz. Módulo faltando no runtime image **só aparece no app empacotado**, nunca no `gradlew run`.
  - Sem grade de atividade e sem ferramentas no relatório do time, pela mesma razão que a aba de resumo dele já as pula: o servidor não agrega por hora nem por ferramenta, e seção vazia sugere que não houve atividade.

- **Exportação** (`data/export/UsageExporter.kt` + `UsageExportWriter`): CSV e JSON de sessões, turnos e resumo. Mora em `data` porque o JSON usa `kotlinx.serialization`, que o domain não pode importar. O CSV escapa pelo RFC 4180 — nome de projeto e branch são texto livre, e uma vírgula sem escape deslocaria todas as colunas seguintes. Turno sem tarifa exporta célula **vazia**, não zero: zero afirmaria que não custou nada. O writer é injetável (`DesktopUsageExportWriter`) pelo mesmo motivo de `rememberClipboardWriter`: teste de componente não abre diálogo nem escreve no disco de quem roda a suíte. Cancelar o diálogo devolve `null` e **não** publica resultado — não é sucesso nem erro. Só metadados de uso saem daqui, nunca conteúdo de prompt ou resposta.

- **Orçamento mensal** (`MonthlyBudget.kt` + `GetMonthlyBudgetStatusUseCase`): teto em USD contra o custo estimado do índice CLI no mês corrente. **Independe do chip de janela** — orçamento é mensal, e amarrá-lo às 5h daria um número sem significado. O mês é o do fuso da apresentação, não UTC: às 22h do dia 31 em BRT já é dia 1 em UTC e o gasto cairia no mês seguinte. **Os créditos de uso da Anthropic ficam numa linha separada, com a moeda explícita** (`AccountCreditUsage`), e nunca somados: eles vêm na moeda real da conta — que pode ser BRL — e o custo do índice é sempre USD. Somar sem taxa de câmbio produziria um número inventado. Os créditos chegam do dashboard via `setAccountCredits`, porque a origem é a API e esta janela só conhece o índice local.

- **Comparativo período a período** (`UsagePeriodComparison`): a leitura do histórico passou a começar em `HistoryRange.previousWindowStart`, e os pontos anteriores **não entram no gráfico** — só no delta. Compara o **delta** de cada janela, nunca o acumulado: o acumulado zera no reset e a comparação viraria função de quando o reset caiu. Sem ponto na janela anterior não há comparação (zero ali significaria "não consumiu", quando foi "não havia dado"), e `changeRatio` é `null` com anterior zerado em vez de "infinito por cento". `TOTAL` não tem janela anterior. Série que só existe na janela anterior é descartada, e `lastUpdatedAt` continua sendo o carimbo da janela **corrente**.

- **Alertas na bandeja** (`evaluateUsageAlerts` em `domain/entity/UsageAlert.kt` + `UsageAlertViewModel` + `Tray` no `Main.kt`): notificação nativa quando uma cota cruza um limiar (default 75/90/100) ou uma sessão CLI satura. A decisão é **função pura** — entra `UiState.Success` (que já traz `riskSummaries`), o `SessionPulse` mesclado e o estado anterior; sai a lista a emitir e o estado novo. O view model **não tem laço próprio**: reage às emissões do polling de 10min e da passada de 30s que já existem.
  - **`UsageAlertState` é a dedup**, e sem ela o mesmo alerta sairia a cada coleta. A chave da janela é `QuotaAlertScope` (alvo + rótulo + `periodType`), **sem** o `periodEndAt`: o reset entra como valor guardado e a comparação passa por `isSamePeriod`, a mesma tolerância de 5 min que o histórico usa contra o jitter de ~1s do `resets_at`. Comparar o reset por igualdade rearmaria o alerta a cada poll.
  - **Cota vencida não alerta** (`isExpiredAt`): a janela descreve um período que já não existe.
  - **O limiar é piso**: o percentual é truncado, não arredondado — 89,9% não cruzou 90%.
  - **Silêncio adia, não consome.** No período silenciado o limiar cruzado **não** é marcado como disparado: dentro de uma janela o consumo só cresce, então o aviso é reavaliado e sai quando o silêncio terminar. Marcá-lo ali perderia o alerta para sempre.
  - **Desligar o alerta zera o estado**, para religá-lo voltar a avisar sobre a janela corrente em vez de herdar disparos antigos.
  - Só `SATURATED` vira notificação de sessão. `ATTENTION` apareceu em 7 das 70 sessões medidas (`CliSessionHealthThresholds`): notificar nesse patamar tornaria o alerta rotina.
  - O ícone da bandeja é o do app **com um ponto** de risco no canto (`TrayRiskIconPainter`, com `equals` sobrescrito para o `Tray` não reconstruir a imagem AWT a cada recomposição). `ON_TRACK` não acende nada — ponto verde permanente vira decoração.
  - As preferências vão em `PreferencesSettings` (`UsageAlertPreferences.kt`), não em `~/.usage-monitor/`: ali moram os segredos do time, e limiar não é segredo. **`UserPreferences` (domain) é código morto** — nenhuma leitura o referencia; não use aquele caminho.
  - Fechar a janela continua encerrando o app: não existe "minimizar para a bandeja".

- **Sessão CLI sem resposta** (`CliStalledSession.kt` + `GetStalledCliSessionsUseCase` + `readSessionTails`;
  issue #177, plano [`sessao-cli-travada-execucao.md`](docs/planos/sessao-cli-travada-execucao.md)): aviso quando
  o último pedido de uma sessão fica sem resposta acima do limiar (default 2h, segmentado de 30min a 4h na aba
  Alertas). Sai na bandeja e marca a linha da tela de Sessões CLI.
  - **A regra sugerida na issue não sobrevive à medição.** "Sem turno novo há X" marcaria **as 323 sessões** dos
    transcripts reais das três contas: pelo `last_ts` do índice, sessão encerrada e sessão travada são idênticas —
    as duas param de produzir turno. O discriminador está no `.jsonl`, não no índice.
  - **O marcador é `{"type":"system","subtype":"turn_duration"}`**, escrito pelo próprio CLI ao fechar um turno —
    não por hook do usuário (66 dos 181 arquivos que o trazem não têm `stop_hook_summary` nenhum). Pedido `user`
    posterior ao último marcador significa turno aberto que nunca fechou; com essa regra a marcação cai para **4 de
    181** sessões avaliáveis, todas abandonadas há mais de 500h.
  - **Ausência de marcador é `NOT_EVALUATED`, nunca "sem resposta"** — mesma recusa de `withKnownWindow()`. Entre as
    sessões que o app indexa a cobertura é ~96%; ficam de fora os transcripts de subagente (0 de 102) e um punhado
    de sessões conduzidas por harness de agente.
  - **Teto de 24h** (`STALLED_SESSION_MAX_AGE_MILLIS`): terminal fechado no meio de um turno deixa a cauda pendente
    para sempre, e sem o teto essas sessões virariam alerta a cada arranque — a dedup de `UsageAlertState` vive em
    memória. Processo que não existe há um dia também não queima cota, que é o que a detecção existe para flagrar.
  - **Só os últimos 256 KB do arquivo são lidos** (`SESSION_TAIL_WINDOW_BYTES`), e isso reproduz o veredito do
    arquivo inteiro nos 323 casos medidos; com 64 KB um deles degrada para `NOT_EVALUATED`. A primeira linha da
    janela vem cortada ao meio e é descartada.
  - **A cauda usa `ClaudeTranscriptTailLineDto`, um DTO próprio.** `ClaudeTranscriptLineDto` materializa
    `message.content` como `JsonElement`, e a cauda é justamente onde moram os `tool_result` de centenas de KB. O
    efeito colateral é a garantia de privacidade: esta leitura **não tem como** enxergar texto de prompt ou resposta.
  - **O caminho do transcript é derivado, não lido de `cli_sessions.file_path`.** O subagente vive em
    `<sessionId>/subagents/agent-*.jsonl`, carrega o `sessionId` do pai e é varrido junto — e o `UPSERT` grava
    `file_path = excluded.file_path`, então a coluna pode apontar para ele. A cauda do subagente responderia sobre
    o subagente.
  - **Nada disso entra no laço de indexação e não há bump de `INDEX_SCHEMA_VERSION`**: decodificar toda linha ali
    custaria uma releitura completa de 426 MB. A leitura é sob demanda, sobre um conjunto candidato de tipicamente
    0–5 arquivos, com cache por `(caminho, tamanho, data de modificação)` — sessão travada não escreve mais nada.
  - **Mora no laço do `SessionPulseViewModel`**, cuja passada local continua com a janela minimizada — que é o
    destinatário do aviso: quem deixou automação rodando e não está olhando a tela.
  - **O texto diz "sem resposta desde o último pedido", nunca "travou"**, e há teste afirmando isso nos dois
    idiomas: a evidência é o transcript, e o app não olha o sistema operacional.

### Empacotamento

`TargetFormat.Exe` (Windows), `Deb`/`Rpm` (Linux) e `Dmg` (macOS). **O `Msi` saiu**: os dois instaladores de Windows gravavam no mesmo `%LOCALAPPDATA%\Usage Monitor`, e o do MSI nunca poderia se atualizar sozinho — `selectArtifact` só aceita `WINDOWS_NSIS`. O `upgradeUuid` continua no `build.gradle.kts` porque é o UpgradeCode das instalações MSI que já existem, e é por ele que o `UsageMonitor.nsi` as encontra e remove antes de instalar. O jpackage **não faz cross-compile**: o `.dmg` só sai rodando em macOS, por isso o release depende do job `build-macos` (`macos-latest` arm64 + `macos-15-intel` x64) em `.github/workflows/release-linux.yml`. Os DMGs vão sem assinatura Apple — o Gatekeeper exige liberação manual, documentada no README.

Auto-start (`AutoStartManager`): registro `Run` no Windows, `.desktop` no Linux, LaunchAgent (`~/Library/LaunchAgents/com.usagemonitor.app.plist` + `launchctl`) no macOS. O enum `Platform` é exaustivo em três `when` do arquivo — valor novo quebra a compilação nos três.
- **A entrada carrega `--autostart`** (`StartupOrigin`), porque o processo lançado pela chave `Run` e o lançado pelo atalho têm o mesmo pai — o Explorer — e sem o argumento são indistinguíveis. O **nome** do valor não muda: é por ele que `isWindowsAutoStartEnabled` decide se a inicialização está ligada. `ensureAutoStartCommandCurrent()` migra por baixo quem já tinha a entrada sem o argumento; entrada **ausente não migra**, senão ligaria a inicialização de quem a desligou.
- **O Agendador de Tarefas foi medido e recusado** (`docs/planos/arranque-no-logon-execucao.md`, A05): `schtasks /Create /SC ONLOGON` devolve `Acesso negado` a processo não elevado, com e sem `/RU`. O instalador roda com `RequestExecutionLevel user` e o app roda não elevado — nenhum dos dois criaria a tarefa. Os ~40 s entre o logon e a janela são a fila que o Explorer serializa, não custo do app.

Arranque e segunda instância (`SingleInstanceGuard`, `FocusRequestChannel`, `StartupDiagnostics`): com o app já de pé, a segunda instância **não pode sair calada** — clicar no atalho sem nada acontecer é indistinguível de "o app não abre", e foi o que fez o autostart ser dado como quebrado numa máquina em que ele nunca deixou de disparar. Ela deixa um pedido em `~/.usage-monitor/focus.request` e a instância viva o atende por `restoreMainWindow`, o **mesmo** caminho do item "Abrir" da bandeja.
- **Arquivo e não socket:** socket em loopback dispara o prompt do Firewall no primeiro arranque, e pedir permissão de rede para focar a própria janela é pior que o defeito. O carimbo vai no **conteúdo**, não em `lastModified`, que depende da granularidade do sistema de arquivos. Pedido sobrado de sessão anterior não é atendido — a janela saltaria sozinha no arranque.
- **`activateWindow` alterna `alwaysOnTop` (`false → true → valor anterior`), e não "liga se estiver desligado".** Medido: com o sinalizador já ligado, atribuir `true` de novo não reordena nada e a janela continua atrás da que a cobre — exatamente o caso de quem usa "manter sempre visível". `toFront()` sozinho não vence o bloqueio de primeiro plano do Windows: ele só pisca o botão na barra.
- **O registro de arranque é sempre ligado** (`~/.usage-monitor/diagnostics/startup.jsonl`), ao contrário dos recorders de créditos e do Codex, que são opt-in por variável de ambiente. Aqueles gravam corpo de resposta a cada coleta; este grava uma linha por arranque, com corte por contagem. Diagnóstico que exige variável configurada **antes** do fato não serve para investigar o boot que já passou. `FOCUS_REQUEST_SERVED` existe para separar "o pedido nunca foi lido" de "foi lido e a janela não subiu", que são defeitos em lugares diferentes.

**Atualização automática** (`desktopMain/update/`; planos
[`atualizacao-automatica-windows-execucao.md`](docs/planos/atualizacao-automatica-windows-execucao.md)
e [`atualizacao-automatica-linux-execucao.md`](docs/planos/atualizacao-automatica-linux-execucao.md)):
interruptor "Atualização automática" nas Configurações → Geral, desmarcado por padrão
(`autoUpdateEnabled` em `PreferencesSettings`). Ligado, baixa a release em segundo plano, valida o
SHA-256 contra o `digest` da API do GitHub (não o hash publicado no workflow, que serve só ao
instalador inicial) e troca ao fechar o app — ou pelo botão "Reiniciar e atualizar agora".
`rememberAutoUpdateController` (`AutoUpdateController.kt`) escolhe **um** instalador por plataforma —
`WindowsAppUpdateInstaller` ou `LinuxAppUpdateInstaller` — cada um atrás da própria flag de build
(`AUTO_UPDATE_SHIPPED`, `LINUX_AUTO_UPDATE_SHIPPED`, **as duas em `true` desde a v38.0.1**) e de um
piso de versão-alvo (`MIN_UPDATABLE_TARGET_VERSION` / `MIN_LINUX_UPDATABLE_TARGET_VERSION`): abaixo do
piso a versão instalada não reconhece o mecanismo de confirmação, e o instalador desfaria uma
atualização que funcionou. macOS fica em `UNSUPPORTED_PLATFORM` (sem Developer ID, sem caminho
confiável de remontar o bundle sob quarentena) e Linux ARM64 em `UNSUPPORTED_ARCHITECTURE` — exceção
declarada à regra de não criar valor novo em enum existente, porque há **um** `when` exaustivo sobre
`AppUpdateSupport` e o erro de compilação garante que o texto novo existe.
- **Windows**: só a instalação pelo NSIS per-user, sem UAC (`RequestExecutionLevel user`) — é o que
  torna a troca silenciosa viável; MSI e cópia manual ficam com o interruptor desabilitado. O
  instalador extrai para `$INSTDIR.new` e só troca por dois `Rename` no mesmo volume quando a árvore
  nova está completa: falha antes do primeiro deixa `$INSTDIR` intacto, e o `Rename` bem-sucedido **é**
  a prova de que o processo anterior saiu — não `taskkill /F`, que mataria no meio de uma escrita do
  SQLite.
- **Linux**: só a instalação `.sh` user-space em árvore XDG gerenciada (marcador
  `.usage-monitor-managed` **e** executável em execução dentro de `versions/`); `.deb`/`.rpm` e cópia
  manual ficam com o motivo na tela. `current` é arquivo de texto com a versão, não symlink — `mv -T`
  não é POSIX —, o script promove por `rename(2)` puro e relança o launcher estável
  (`~/.local/bin/usage-monitor`), que espera um ACK em arquivo (token gerado pelo script, não carimbo
  de tempo) antes de gravar `status=success`; sem ACK em 60s, desfaz. Log sempre em
  `~/.usage-monitor/diagnostics/linux-update.log`. A ativação real (A14) só veio depois de dois
  defeitos achados numa Bazzite/rpm-ostree real e não previstos no plano: origem `UNMANAGED` por
  comparar caminho não canonicalizado contra symlink do ostree, e o processo relançado herdando o
  `LD_LIBRARY_PATH` da versão anterior e morrendo antes de `main()` — os dois só apareceram medindo ao
  vivo, não lendo o código.
- Nenhuma animação infinita para o progresso — é **texto** ("Baixando 42%"), pelo motivo de sempre
  (`waitForIdle`). `AppUpdateUiState` é `sealed interface`, não enum: valor novo ali é erro de
  compilação nos `when`, e portanto visível.

**Novidades da versão** (`ReleaseNotes.kt` + `ReleaseNotesController.kt`; issues #74 e #127): a janela
que diz o que mudou depois de uma troca de versão. **O gatilho é `CURRENT_APP_VERSION` diferente da
marca `releaseNotesSeenVersion`, nunca o recibo do instalador.**
- **O recibo perde a corrida no Linux, sempre.** No Windows o NSIS o grava **antes** de relançar o
  app; no Linux o `linux-updater.sh` só o grava **depois do ACK**, que é escrito pelo app novo já em
  execução — quando ele lê o arquivo, ele ainda descreve a atualização anterior. A ordem do script é a
  correta: antes do ACK ainda pode haver rollback. Com o recibo como condição, a janela nunca aparecia
  no Linux, em instalação manual (`.exe` sem `/UPDATE`, `.sh`, `.deb`, `.rpm`) nem no macOS, que não
  tem instalador automático e portanto nunca teve recibo.
- **Marca ausente não é uma situação só.** Sem recibo no disco é instalação nova e fica em silêncio —
  "novidades" para quem não tem versão anterior não descreve mudança nenhuma. **Com** recibo é máquina
  que já atualizou alguma vez, e abre: sem esse ramo, quem foi atingido pela #127 (e que por definição
  nunca chegou a marcar nada) só veria a janela uma versão depois de a correção sair.
- **Retrocesso marca em silêncio**, e não é caso hipotético: no `health-timeout` do updater do Linux o
  app novo chega a abrir a janela e a gravar a marca antes de o script desistir e restaurar a versão
  anterior. É esse ramo que reescreve a marca para baixo; sem ele as novidades daquela versão ficariam
  perdidas para sempre. Ele cobre também "mesma versão escrita de outro jeito" (`38.0.2` × `38.0.02`),
  que a igualdade textual não pega.
- **`MARK_SEEN_ONLY` não vai à rede.** Pedir ao GitHub a release de uma versão que não vamos anunciar é
  requisição gasta por nada — e é o contador de chamadas, não a janela ausente, que o teste afirma.
- A ordenação de versões tem **um dono**, `domain/entity/AppVersionComparison.kt`, e expõe o **sinal**:
  é ele que separa atualização de retrocesso, e retrocesso não é "não atualizou".
- O recibo continua vivo para outras duas coisas: a linha "Última atualização" das Configurações e a
  poda do artefato aplicado (`shouldDiscardUpdateArtifacts`).

### Injeção de dependências

Manual, em `Main.kt` (desktopMain). Sem framework.

**`main()` está no limite do backend JVM.** É um composable único de mais de mil linhas, e a análise de fluxo de controle sobre o método inteiro estourou em `OutOfMemoryError` dentro do ASM (`Back-end (JVM) Internal error: Couldn't transform method node`). O `gradle.properties` dá folga de heap ao daemon, mas isso é paliativo: a correção real é quebrar `main()` em composables menores. Antes de acrescentar mais estado ali, extraia. Sequência: `HttpClient(OkHttp)` → datasources → repos → use cases → `DashboardViewModel` → `DashboardScreen`.

## Integração com time (`server/`)

Recurso opcional, desligado por default. Servidor Node.js **self-hosted pela empresa** (Express 4 + TypeScript + SQLite) que recebe os turnos indexados de cada máquina e devolve a visão agregada por conta Anthropic. Contrato da API e passo a passo de deploy no Dokploy em [`server/README.md`](server/README.md).

- **Chave de agrupamento:** o `accountUuid` da conta (`UsageAccountKey.providerAccountId`), sem o `organizationUuid` — este é nulo em parte das instalações e usá-lo na chave quebraria o agrupamento entre máquinas da mesma conta.
- **Chave de time é por pessoa, emitida pelo app do admin** (`server/src/repositories/teamKeyRepository.ts`). Ela nasce sem conta e se amarra a um `accountUuid` por `POST /v1/claim` (o botão "Testar conexão") ou dentro de um ingest. **Nenhum `GET` reivindica** — senão bastaria varrer uuid para adotar conta alheia. Depender só do ingest travou o time em produção: numa máquina que já enviou todo o histórico, trocar a chave não gera requisição nenhuma, e a conta fica sem dona com a leitura recusada.
- **O marcador de identidade do `TeamSyncService` inclui a chave de time**, não só o apelido. É o que faz trocar a chave forçar um envio mesmo sem turno pendente. Índice único em `team_key_accounts(account_key)`: uma conta pertence a no máximo uma chave, e é ele — não uma checagem prévia — que decide a corrida entre dois ingests. `maxAccounts` (default 1) cobre a máquina logada em duas contas da empresa, que usa a **mesma** chave para as duas.
- **`label` da chave é a relação do time daquela chave** (servidor 0.11.0+, issue #179). Continua sendo texto livre, mas deixou de ser decoração: quando declara e-mail, só a conta que reporta um dos e-mails listados ali pode usar a chave. Antes nada o lia, e uma conta pessoal ocupou um dos dez slots da chave de outra pessoa — 178,2M de tokens da empresa. `parseKeyLabelEmails` devolve **conjunto**, não e-mail único, porque a mesma chave já cobria a máquina logada em duas contas da empresa; rótulo **sem e-mail** não declara relação nenhuma e desliga o portão, que é o que preserva quem rotula pelo nome da pessoa.
  - **O portão é avaliado antes do teste de vínculo**, e não dentro do ramo que reivindica: é isso que o faz valer para os vínculos que já existiam — o caso da issue. `TEAM_KEY_LABEL_MATCH=off` é a válvula de rollback, mesmo desenho de `TEAM_LEGACY_KEY_MODE`, mas **nasce `strict`**: aquele preservava clientes numa mudança de autenticação, este corrige um defeito.
  - **Conta que nunca reportou e-mail passa**, e é buraco assumido: cliente anterior ao campo não o manda, e recusá-lo derrubaria instalação que a mudança não pretende atingir. Quem quisesse burlar bastaria omiti-lo — o modelo inteiro de chave de time é autodeclarado. Contra decisão deliberada existe a lista de bloqueio, que é por `accountKey`.
  - **As leituras conferem pelo e-mail gravado** (`team_accounts.account_email`), porque não carregam corpo; nas escritas o do pedido vence, já que numa máquina que trocou de conta o gravado descreve a anterior. `upsertAccountEmail` nunca sobrescreve com nulo, e é isso que torna a memória confiável.
  - **`verify`/`claim` aplicam as mesmas travas e recebem `accountEmail`.** Sem o campo eles responderiam pelo gravado, e numa máquina que nunca enviou nada não há nada gravado: o "Testar conexão" aprovaria a conta e o envio seguinte a recusaria — a sincronia parada em silêncio que aquelas rotas existem para evitar.
  - **O arranque varre os vínculos e avisa quem o portão vai recusar** (`db/keyLabelAudit.ts`), com a mesma função pura da recusa. Alcance retroativo sem esse log seria quebra silenciosa no deploy.
- **`TEAM_API_KEY` virou legado.** Com `TEAM_LEGACY_KEY_MODE=open` (default) ela continua lendo tudo, que é o comportamento anterior; `off` a rejeita e é o passo que efetiva o isolamento. `TEAM_ADMIN_TOKEN` monta `/api/admin/*` e vale como credencial **de leitura** em `/v1/team`, `/v1/session` e `/v1/member` — é o que evita uma família de rotas admin paralela. No `/v1/ingest` ele é recusado: admin lê, não escreve consumo em nome de ninguém.
- **Modo admin é independente de conta.** `TeamIntegrationSettings.isAdminMode` não entra em `isConfigured`/`isActive`: administrar não exige chave de time, apelido nem conta marcada. O botão da barra inferior (`FooterBar.onOpenAdminOverview`, `null` esconde) abre a mesma `TeamUsageScreen` em modo global.
- **A ordem da lista global é alfabética pela conta, e o consumo ordena dentro dela**
  (`TeamUsageViewModel.flattenAccounts`). Com o consumo como chave primária, a faixa de uma conta
  aparecia onde o integrante que mais gastou a levasse, e a mesma conta subia e descia entre dois
  tiques do laço de 5s. O rótulo é o e-mail que o admin digitou ao emitir a chave e não muda
  sozinho. Conta sem rótulo vai para o fim, por um degrau próprio do comparador e não por sentinela
  de texto. `memberGroups` agrupa por ordem de primeira aparição, então ordenar os integrantes já
  ordena as faixas — uma segunda ordenação lá seria um segundo dono da mesma decisão.
- **A hierarquia da lista é uma escada de três superfícies neutras**: faixa da conta em
  `surfaceVariant` com marcador de 2dp, a palavra "Conta" e divisória; linha do integrante
  transparente sobre o fundo da janela; bloco de sessões em `surface`, recuado. O bloco aninhado
  fica **um degrau de distância do fundo da lista** e **nunca** em `surfaceVariant`: aquele é o
  realce de hover do `AppDataRow`, e com ele ali passar o mouse numa sessão deixa de dar retorno.
  E a lista **não tem vão entre itens** — cada linha traz a própria divisória, e o vão de 8dp era
  o que desfazia a leitura de tabela.
- **A tela de presença global usa a mesma faixa** (`TeamPresenceAccountHeader`), com dois níveis em
  vez de três: ali não há bloco de sessões. Ela ficou de fora da passada da issue #69 e continuava
  entregando um e-mail e um uuid sobre fundo transparente — que é exatamente o que a linha do
  integrante também tem.
- **A coluna de ação mora fora do `Row` das colunas**, nas duas listas de presença. Dentro dele o
  botão destrutivo é o último item e portanto o primeiro a quebrar: numa janela estreita ele descia
  para uma linha própria e virava um ícone vermelho solto, sem coluna e sem dizer a que linha
  pertence. O orçamento de largura (`PRESENCE_COLUMN_*`) virou piso de janela em
  `TEAM_PRESENCE_MIN_WINDOW_WIDTH_DP` — comentário não impede o usuário de arrastar a borda.
- **A legenda pertence à coluna, não à célula** (issue #81). As quatro listas tabulares — sessões da
  máquina, sessões do time, integrantes e presença — têm uma faixa `AppColumnHeaderRow` acima da
  `LazyColumn`, e a célula carrega só o valor. Isso só se sustenta se a linha **não quebrar**: por
  isso elas são `Row` e não `FlowRow`, cada arquivo carrega o orçamento de largura no comentário das
  constantes `*_COLUMN_*`, e as três janelas de lista têm piso de arrasto (`*_MIN_WINDOW_WIDTH_DP`,
  aplicado por `ApplyWindowMinimumSize` em `Main.kt`). Faixa de legendas sobre linha quebrada promete
  um alinhamento que o conteúdo não cumpre.
  - O que liberou a linha de sessão foi o **veredito de saturação sair do fluxo de colunas**: ele
    media 210dp e desceu para uma segunda linha da própria linha, junto da razão que o gerou. Foi ele
    que forçou a concessão do "rótulo por célula" registrada em agosto, e é ele que a desfaz.
  - Na presença, o Estado virou **uma** coluna de três palavras — trabalhar implica estar online, e
    as três combinações que existem cabem numa coluna só. Continuam duas camadas e não uma escala:
    colapsá-las em duas é que esconderia quem está com o app aberto e parado.
- **`TeamMemberUsage.memberKey`** (`accountKey/deviceId`, ou só `deviceId` fora do modo global) é a identidade da linha na tela. O `deviceId` sozinho não serve: na visão global a mesma máquina em duas contas expandiria as duas juntas e a remoção acertaria a conta errada.
- **Na visão global o recorte de 5h é deslizante.** Cada conta reseta a quota numa hora, e ancorar numa delas daria um número que não corresponde a nenhuma. `GetAdminTeamOverviewUseCase` resolve a janela com `CliQuotaWindows()` vazio e a tela avisa.
- **Configuração** em `~/.usage-monitor/team.json` (`LocalTeamSettingsDataSource`), com escrita atômica e `restrictToOwnerReadWrite`. Nunca em `PreferencesSettings`: a chave do servidor é segredo e as preferências vão em claro para o registro.
- **Envio:** `TeamSyncService`, laço de 30s, roda com a janela fechada. Marcador em `team_sync_state` (mesmo `usage-history.db`), pela conexão **compartilhada** do `LocalCliSessionDataSource` — duas conexões para o mesmo arquivo dariam `SQLITE_BUSY`.
- **Cada passada indexa antes de enviar** (`ensureIndexFresh`, ligado ao mesmo `SyncCliSessionIndexUseCase` da tela de Sessões CLI). O serviço só enxerga turno que já está no índice: sem isso a latência seria a do laço de background (10min), não a dos 30s. Falha na indexação não cancela o envio do que já está indexado. Abrir o modal do time chama `requestImmediateSync()` e antecipa uma passada.
- **Identidade (apelido) viaja no ingest, não numa rota própria.** O servidor grava o `alias` no upsert de `team_members`, dentro do `POST /v1/ingest`. Por isso `TeamSyncService` envia um payload **só com o membro** (`sessions`/`turns` vazios, `PushTeamUsageUseCase(force = true)`) sempre que o apelido difere do último confirmado — marcador em memória, por conta. Sem isso, quem renomeia e para de usar o CLI fica com o nome velho na tela do time indefinidamente. O commit do campo nas Configurações chama `requestImmediateSync()` para não esperar os 30s.
- **Renomear nunca duplica integrante**: a chave é `(account_key, device_id)`. Duas linhas para a mesma máquina só aparecem quando o `deviceId` muda, e o único caminho que o gera é `LocalTeamSettingsDataSource` com `team.json` ausente ou ilegível — daí o arquivo corrompido ir para `.corrupt` em vez de ser sobrescrito. Para limpar um fantasma já criado existe `DELETE /api/v1/member` e o botão de remover no modal, bloqueado para o próprio `deviceId`.
- **Desvincular não apaga nada, e não faz a conta sumir.** `DELETE /api/admin/v1/keys/:id/accounts/:accountKey` mexe em uma linha de `team_key_accounts`; a visão global é derivada de `team_members` ∪ `team_turns`, então a conta desvinculada continua na lista, agora com `label: null` — pior do que antes. Quem a remove é `DELETE /api/admin/v1/accounts/:accountKey` (servidor 0.5.0+), que apaga integrantes, sessões, turnos e o vínculo. Na rota a **ordem é dados primeiro, vínculo depois**: falhando o segundo passo sobra um vínculo sem dados, inócuo; o inverso deixaria a conta órfã e adotável por outra chave com o histórico ainda no banco. **Apagar passou a impedir a conta de voltar** (servidor 0.11.0+): a rota também grava a conta em `team_blocked_accounts`, e a **ordem virou dados, vínculo, bloqueio** — o último é o passo mais barato e o único trivialmente reversível, e falhar nele deixa exatamente o estado da versão anterior. Antes disso apagar era gesto sem efeito: ingest e presença reivindicam sozinhos, e a máquina que ainda participasse da conta a recriava na batida de 30s seguinte, com quem administra achando que tinha resolvido.
  - **O bloqueio é checado antes da credencial no caminho de escrita**, senão a chave legada em modo aberto — que retorna cedo em `authorize()` — escreveria sem passar por conta nenhuma. Nas leituras ele só alcança a chave de time: administrar não é participar.
  - **`TEAM_KEY_LABEL_MATCH=off` não o desliga.** Aquilo é válvula do portão; desfazer a decisão do admin por variável de ambiente seria outra pessoa decidindo.
  - **O e-mail guardado é retrato do momento do bloqueio**, lido **antes** do delete: `deleteAccount` apaga a linha de `team_accounts`, e a lista ficaria com um UUID cru que não identifica ninguém para quem vai decidir devolver a conta. Desbloquear (`DELETE /api/admin/v1/blocked-accounts/:accountKey`) **não restaura dado nenhum** — o que volta é a possibilidade de reivindicar.
- **A tela de presença deixou de ser só leitura em modo admin.** `TeamPresenceViewModel` ganhou `removeTeamMember`/`deleteTeamAccount` **opcionais** (`null` = instalação sem administração, mesmo tratamento de `getAdminTeamPresence`) e um segundo flow, `actionError`, fora do `Success` pelo motivo de sempre: o laço de 5s republica o estado e apagaria a mensagem antes de ela ser lida. Os dois botões são bloqueados pelo `localDeviceId` — a própria máquina e a conta de que ela participa voltam no envio seguinte, então removê-las só apagaria histórico à toa.
- **Tempo ativo do time é campo novo em rota existente** (`activity` em `GET /v1/team` e no `overview` do admin, servidor 0.7.0+), e não rota nova: por isso não há gate por 404 como o da tendência — servidor antigo simplesmente omite o campo, o DTO cai no default vazio e o cliente lê "não medido". O corte entre turnos viaja na query (`gapCutoffMs`), pelo mesmo motivo pelo qual o servidor não precifica: `TURN_GAP_CUTOFF_MILLIS` é do domínio do app, e um segundo dono do valor daria duas respostas. `teamQuerySchema` é `z.object` não-estrito, então o parâmetro enviado a um servidor anterior é ignorado sem 400.
- **Tendência é rota própria** (`GET /api/v1/team/trend`, servidor 0.6.0+), da mesma família de leitura de `/v1/team`: `requireTeamAccess` com a conta na query, nenhum `GET` reivindica. Devolve linhas cruas por `(máquina, dia UTC, modelo)` — o servidor continua **sem precificar**, e o cliente aplica `ModelPricingTable`, como já faz com `/v1/team` e `/v1/session`. O dia sai em **UTC** porque o servidor não conhece o fuso de quem consulta; quem traduz é o cliente, igual à grade de atividade local. Contra servidor anterior a rota dá 404 e a leitura devolve `null` — "indisponível", não erro —, lembrado **por URL** (`trendRouteMissingFor`, precedente exato do `presenceRouteMissingFor`); só o 404 cai aí, senão chave errada viraria "servidor antigo". O eixo de dias é montado no cliente e **todo integrante ganha ponto em todo dia**: série com buracos desenharia uma linha que pula dias e sugeriria continuidade onde houve silêncio. A leitura é **uma por abertura**, fora do laço de 5s: a série é de dias e recarregá-la a cada tique seria uma consulta por segundo para redesenhar o mesmo gráfico. As barras de todos os integrantes usam **uma escala só** — normalizar cada um pelo próprio pico faria quem gasta centavos parecer igual a quem gasta dezenas de dólares. O desenho é **um grupo de barras por dia, uma cor por integrante**, com legenda e três linhas de grade (`TeamTrendChart`): a cor identificando o integrante é a **única exceção** à regra de que acento é identidade de fonte e não de valor, porque num gráfico agrupado ela é o único jeito de dizer de quem é a barra. A paleta reusa os acentos de fonte e cicla — do sétimo integrante em diante duas séries repetem o tom, e quem as separa é a legenda. A largura da barra tem piso e teto: sem piso, 30 dias × 5 pessoas viram 150 barras num borrão, e abaixo dele a área rola na horizontal; sem teto, sete dias numa janela larga dão barras de 40dp que leem como bloco e comem o vão entre os dias.
- **O modal do time tem três abas** (`TeamUsageView`: `MEMBERS`, `BREAKDOWN`, `TREND`), no mesmo lugar e na mesma ordem dos chips do modal da máquina — a janela vale para as três, então trocá-la é a escolha de fora e a aba é a de dentro. A tendência **era um painel fixo** acima da lista: comia metade do modal para mostrar dias, que nem obedecem ao filtro de janela logo acima deles. Enum próprio, não valor a mais em `CliSessionRange`. A aba escolhida mora no `Success` e `loadTeam` a carrega do estado anterior, senão o tique de 5s devolveria à lista quem está lendo o resumo. **O chip de tendência não existe na visão global** — a série é por conta e ali a janela mistura várias —, e `effectiveView` derruba para `MEMBERS` a escolha guardada que deixou de existir, em vez de desenhar painel nenhum. O aviso de janela deslizante só sai nas abas que respeitam o filtro. O erro de remoção fica **acima** do despacho de aba: é retorno de uma ação do usuário, e trocar de aba não pode escondê-lo — mesmo motivo pelo qual ele não mora no `uiState`.
- **O resumo por eixo do time reusa `toUsageBreakdown`**, o dobrador do resumo local, sobre as linhas cruas que o servidor já mandou. `TeamUsageRowDto` é `(deviceId, sessionId, cwd, gitBranch, model, tokens)` — a mesma forma de `CliUsageGroupRow` —, e o mapper as guarda em `TeamMemberUsage.groupRows`. Sair das sessões já dobradas **não serviria**: `toSummary()` colapsa os modelos num `primaryModel` só e o eixo "por modelo" sumiria. Vem na **mesma resposta** que a lista, então é entregue por `GetTeamUsageUseCase`/`GetAdminTeamOverviewUseCase` e não por um caso de uso próprio, que pediria ao servidor o que já está em memória. `CliUsageBreakdown.byMember` é o quarto eixo, vazio no índice local onde a máquina é uma só; o balde de cada pessoa é o `totals` do resumo dela, para não abrir um segundo caminho de soma. **Nos outros três eixos o rótulo é a chave da agregação e portanto único; neste não** — o apelido é texto digitado, e a máquina que perdeu o `team.json` volta com outro `deviceId` e o mesmo apelido. Quem repete apelido ganha o começo do `deviceId` como sufixo (o `hostName` também repete no caso real), e a linha do `LazyColumn` é chaveada pela **posição**, não pelo rótulo: chave repetida ali não desenha item errado, derruba a janela. **Grade de atividade e ferramentas ficam vazias**: o servidor agrega por sessão e modelo, nunca por hora nem por ferramenta, e a pane já pula seção vazia. O teste que importa é o do total batendo com `snapshot.totalCostMicros` — divergência ali significa dois caminhos de precificação.
- **Leitura:** `TeamUsageViewModel`, laço ao vivo de 5s, só com a janela aberta. Mesmas restrições anti-flicker do `CliSessionsViewModel`.
- **Precificação no cliente:** o servidor devolve tokens por `(deviceId, sessionId, model)` e não calcula custo. `WindowedSessionAccumulator` (domain) aplica `ModelPricingTable` — a mesma classe que o índice local usa, para os dois modais não divergirem.
- **O servidor publica a tabela de preços e continua não precificando** (`GET /api/v1/pricing`, servidor 0.10.0+). A tabela vive em `server/src/domain/modelPricing.ts` — TS e não JSON, porque o `Dockerfile.dokploy` copia só `server/tsconfig.json` e `server/src` —, e `tools/ci/check-pricing-parity.mjs` a compara com `ModelPricingTable.kt` nos **dois** workflows: os filtros de path de `ci.yml` e `ci-server.yml` são disjuntos, e um step só deixaria metade das edições passar batida. O **formato das duas listas é contrato**, porque o parser lê por regex; reformatar quebra o script, e isso é o desejado. Os multiplicadores de cache viajam como **razões inteiras**: a aritmética do domain é inteira em micros, e publicar `0.1`/`1.25` convidaria o consumidor a erro de ponto flutuante que o original não tem.
- **A credencial de relatório é leitura global sem poder destrutivo** (`TEAM_REPORT_TOKEN`, header `x-report-key`, servidor 0.10.0+). `requireAdminToken` **não foi tocado** e segue sendo o portão de todos os `DELETE`; o token entra em `authorize()` no mesmo ponto do de admin, dentro do `if (!allowClaim)`, que já o recusa em ingest e presença. `requireGlobalRead` é o par dele para rota sem conta — chave de time não serve ali, porque é por conta. O token **não conta** como o segredo obrigatório do boot: servidor que só publica relatório e não aceita cliente nenhum não tem o que relatar. E as rotas são montadas **incondicionalmente**: sem a variável elas respondem 401, porque rota ausente faria "credencial errada" e "variável não definida" chegarem como o mesmo 404.
- **`until` é semiaberto** (`since <= ts < until`, servidor 0.10.0+): `since` sempre foi inclusivo, e com os dois inclusivos duas janelas adjacentes contariam duas vezes o turno da fronteira. O intervalo entre turnos que cruza a fronteira **não é contado em nenhuma das duas janelas** — é o que `since` já fazia na borda esquerda. `until <= since` responde 400, e o filtro entra **dentro** da subconsulta de atividade, antes do `LAG`.
- **As rotas de relatório são planas e paginadas por chave de agrupamento** (`/api/v1/report/{usage,activity,members}`, servidor 0.10.0+), e `/admin/v1/overview` fica **intacto**: `flattenAccounts`/`toUsageBreakdown` assumem resposta completa, e uma página parcial subestimaria os totais da tela sem erro nenhum. A ordem é a chave (`conta, máquina, sessão, modelo`), nunca recência, que não dá ordem total sem desempate; a consulta pede `limit + 1` porque é a linha extra, e não a página cheia, que prova haver próxima. `unpricedTurnCount` **não virou campo** na resposta: as linhas já são `(máquina, sessão, modelo)` com `turnCount`, e com a tabela publicada o consumidor o deriva — campo novo seria um segundo dono da mesma conta.
- **Não trafega conteúdo de prompt ou resposta**, só metadados de uso.
- **Presença é rota própria** (`POST /api/v1/presence`, servidor 0.4.0+), e não uma leitura de `team.ts`: é escrita, com a conta no **corpo**, `x-admin-token` recusado (401) e `allowClaim: true` — o oposto da família de leitura nos três eixos. Ela escreve na `team_members.last_seen_at` que já existia: **nenhuma coluna nova, nenhuma migração**. Contra servidor anterior a rota dá 404 e `TeamUsageRepositoryImpl` cai num ingest só-membro, que carimba o mesmo campo; o 404 é lembrado **por URL** (`presenceRouteMissingFor`), senão seriam 404 a cada 30s para sempre. Só o 404 cai no fallback — 401/403/500 continuam falha, senão chave errada viraria batida "bem-sucedida".
- **Duas camadas de estado, não uma escala.** *Online* = heartbeat dentro de `PRESENCE_ONLINE_WINDOW_MILLIS` (90s = três batidas de 30s do `TeamSyncService`); *trabalhando agora* = turno dentro de `ACTIVE_SESSION_WINDOW_MILLIS` (5 min). Colapsá-las esconderia o caso que a tela existe para mostrar: quem está com o app aberto e parado. `TeamMemberPresence` deriva de `TeamMemberUsage` e usa **dois booleanos, não um enum** — enum novo obrigaria `when` exaustivos na tela. O corte da consulta vai como `cutoffMillis` cru, **nunca** um valor novo em `CliSessionRange`.
- **`last_seen_at` é o relógio do servidor**, e a rota o devolve na resposta justamente para o cliente medir o desvio (`TeamServerClockOffset`). Comparar o carimbo do servidor com `Clock.System.now()` local deixaria um cliente atrasado vendo o time inteiro online **para sempre** — falha silenciosa. `clockSkewSuspected` denuncia carimbo no futuro; sem medida (admin puro, servidor antigo) o offset fica em zero. Instalação em modo admin puro **não bate presença** e isso é intencional: admin não é integrante.
- **A ordem de `toTeamPresence` é total, determinística e sem carimbo de tempo nenhum**: conta (rótulo, com a conta sem rótulo por último e o `accountKey` desempatando), depois trabalhando, depois online, e o alias como desempate. Não é estética: duas leituras iguais têm de produzir listas iguais, ou o `StateFlow` reemite e a tela recompõe a cada 5s. **A conta é a chave primária pelo mesmo motivo de `flattenAccounts`** — `presenceGroups` agrupa por ordem de primeira aparição, então quem ordena os integrantes ordena as faixas de conta, e com o estado no topo as três contas trocavam de lugar entre dois tiques. **`lastSeenAt` e `lastActivityAt` saíram do comparador**: o primeiro é o heartbeat de 30s e entre dois online não informa nada — os dois estão dentro dos mesmos 90s —, e o segundo anda a cada turno de quem trabalha; qualquer um deles como critério reordena a lista sem nada ter mudado. As duas horas continuam impressas nas colunas. Pelo mesmo motivo o estado não guarda tempo decorrido — a tela mostra hora absoluta, e o desvio entra arredondado em minutos.
- **O ponto de estado da linha não pisca.** Animação infinita numa lista trava o `waitForIdle` dos testes de componente, e o botão do card de presença também não recebe `pulse`: o pisca significa uma coisa só neste app — sessão em atenção — e o botão de time ao lado já a carrega.

## Sistema visual

Refatoração de agosto de 2026, inspirada na linguagem do OpenCode. O plano de execução com o
histórico das decisões está em [`docs/planos/refatoracao-visual-opencode-execucao.md`](docs/planos/refatoracao-visual-opencode-execucao.md).

### Design system — precedência

**A fonte de verdade visual é [`docs/design-system/`](docs/design-system/).** Ali moram os tokens
(`tokens/*.css`), as primitivas publicadas — cada uma com o contrato escrito em
`components/**/*.prompt.md` — e as regras de conteúdo, iconografia e fundação do
[`readme.md`](docs/design-system/readme.md). O protótipo aprovado,
[`docs/planos/prototipo-visual-opencode.html`](docs/planos/prototipo-visual-opencode.html), continua
sendo o mockup obrigatório de **cada tela** — layout, colunas, estados —, mas deixou de ser a
especificação de token, primitiva e copy: o design system foi derivado dele e o descreve com mais
precisão.

| Divergência | Quem vence | O que fazer |
|---|---|---|
| Compose × design system | design system | corrigir o Compose |
| Compose × protótipo (layout de tela) | protótipo | corrigir o Compose |
| Design system × protótipo | design system | corrigir o protótipo, no mesmo commit |
| Design system tecnicamente errado | o Kotlin | corrigir `docs/design-system/`, com a decisão registrada |

**Nenhuma tela reimplementa uma primitiva.** Antes de escrever `Surface`, `Card`, `Modifier.border`,
`.background` com cor de superfície ou `RoundedCornerShape`, procure em
`presentation/ui/components/AppStructure.kt`, `AppControls.kt` e `AppStates.kt`. Se a primitiva não
existir, o commit que a cria e o commit que a consome são o mesmo — primitiva construída e não
adotada não conserta nada, e é exatamente assim que `AppWindowScaffold`, `AppToolbar`, `AppTooltip` e
`AppEmptyState` ficaram meses com adoção zero.

**Cor de acento sai de `AppAccents.current` e de `AppTone`**, nunca de `darkAppAccents` ou
`lightAppAccents` diretamente. Um `val` de topo de arquivo é resolvido uma vez por processo e não lê
o tema em vigor: congelar a variante escura faz o valor cair a 2,64:1 contra a `surface` clara, que é
o que `AppAccentsContrastTest` existe para impedir.

**Toda alteração de tela é registrada nos dois documentos, no mesmo commit da mudança.** A regra de
precedência acima só se sustenta enquanto os dois descreverem o app inteiro; desatualizado, cada um
vira ponteiro para um documento que não descreve mais o produto.

- **No protótipo:** tela nova ou estado que ainda não existe ganha seção `<h2 id="…">N · …</h2>`
  própria mais o link em `nav.index`; controle, coluna ou texto novo dentro de tela já desenhada vira
  linha no mockup dela; risco conhecido e decisão pendente vão para `§15 #checklist`.
- **No design system:** primitiva nova ou contrato alterado vira `components/<grupo>/<Nome>.prompt.md`
  mais a entrada no índice do `readme.md`; tela que tem kit em `ui_kits/desktop-app/` tem o `.jsx`
  correspondente atualizado.

Vale para qualquer superfície visível — janela, diálogo, faixa, bandeja e relatório PDF.

**Tokens** (`presentation/ui/theme/AppTheme.kt`): quatro superfícies neutras dentro de ~14% de
luminância (`AppSurfaces`), raios 4/6/8/10 com **teto de 10** (`AppShapes`), elevação 0/2/8 —
`card` é **zero**, e sombra só em diálogo e overlay —, espaçamento 4/8/12/16/24/32 (`AppSpacing`) e
motion 120/180/240 (`AppMotion`). A profundidade vem da borda de 1dp e do espaçamento; foi o
gradiente de acento em toda superfície que fazia a tela ler como pilha de blocos de mesmo peso.

**Tipografia**: IBM Plex Mono e Sans, carregadas do classpath por `appFontFamilies`
(`expect`/`actual`, TTFs em `desktopMain/resources/fonts/`). `label*`, `title*`, `headline*` e
`display*` são **mono** — rótulo, número, cabeçalho de coluna, onde a largura fixa do dígito alinha a
coluna; `body*` é **sans**, onde mora o texto corrido. **Não** usar `composeResources`: a carga é
assíncrona e o `ScreenshotGenerator` renderiza offscreen com relógio manual — captura com fonte de
fallback é falha silenciosa.

**Primitivas** (`presentation/ui/components/AppStructure.kt`, `AppControls.kt`, `AppStates.kt`):
todas stateless. Corpo de janela com barra de estado, barra de controles, superfície de dados,
cabeçalho de seção com marcador de 2dp, linha de dados com divisória própria, bloco de métrica,
faixa de legendas de coluna com valor de célula, abas sublinhadas, controle segmentado, chip de
alternância, botão, botão de ícone, campo, interruptor, tooltip, aviso, vazio, carregando, erro,
indicador de estado e barra de progresso.
Antes de desenhar um retângulo novo, procure aqui.

- **Primitiva construída e não adotada não conserta nada.** A refatoração de agosto fechou com
  `AppWindowScaffold` e `AppToolbar` em **zero** telas e `AppStatusIndicator` em uma — a fundação
  existia e cada tela continuava montando o próprio retângulo. A passada de conformidade de
  2026-08-23 fez a adoção; ao criar primitiva nova, o commit que a cria e o que a consome andam
  juntos.
- **Bloco de métrica** (`AppMetricBlock`): rótulo em cima, valor embaixo, borda em volta, largura
  fixa e igual entre os blocos da mesma fileira. A ordem não é estética — numa fileira de quatro, o
  olho varre os rótulos para achar o que procura, não os números. Qualificação longa fica **fora**
  do bloco: dentro dele, um rodapé de quatro medidas mede três vezes a largura do bloco vizinho e a
  fileira perde o alinhamento que a grade existe para dar.
- **Número é `label*`, não `body*`.** A divisão entre as duas famílias é por papel: `body*` é sans e
  existe para texto corrido, e número em fonte proporcional não alinha coluna — que é a razão de a
  mono estar na escala. Vale para célula de tabela, valor de métrica e rótulo de controle.
- **Controle deslizante** usa os slots `track`/`thumb` do `Slider` do Material com o desenho do
  sistema (trilha de 4dp, polegar de 12dp). Não é um controle próprio: a semântica de progresso, que
  é o que `SetProgress` dos testes exercita, tem de continuar vindo do `Slider`.
- **`AppSwitch` ligado é verde**, não azul: ligado é um estado, o mesmo "ok" do indicador e da barra
  saudável. O azul deste sistema é informação — linha de gráfico e realce de seleção —, e com ele
  ali um interruptor ligado lia como item selecionado.

- **Aba × segmentado × chip**: aba troca **o que** a tela mostra, segmentado troca **como** (janela,
  ordem, tamanho de página), chip de alternância liga ou desliga **uma** restrição. Desenhá-los igual
  foi o que fez o app usar a mesma pílula para as três coisas.
- **Cor nunca informa sozinha**: todo estado carrega ponto e palavra (`AppStatusIndicator`), e o tom
  sai de `AppTone`, que lê de `AppAccents` e do `ColorScheme` — nunca de um literal novo. Foi assim
  que o âmbar do semáforo de risco passou anos abaixo de 3:1 contra a superfície clara.
- **Acento é identidade de fonte, não de valor**: ele vive no marcador de 2dp e na linha do gráfico.
  Custo em azul e tempo em verde na mesma tabela sugerem categorias que não existem.

**Armadilhas pagas uma vez cada** — todas custaram uma suíte vermelha:

1. `weight` dentro de `FlowRow` não tem referência de largura: o Compose deixa o filho **sem
   posicionar** e o sintoma é `assertIsDisplayed` falhando com `boundsInRoot` válido.
2. Ação que virou ícone precisa de `contentDescription` na **semântica**, não só de `onClickLabel` —
   é `onNodeWithContentDescription` que as suítes usam. `AppIconButton` já traz os dois.
3. `BasicTextField` mescla descendentes: o placeholder precisa de `clearAndSetSemantics`, ou o campo
   vazio passa a "conter" o texto de exemplo e duplica nós para o `onNodeWithText`.
4. Tela que ficou mais alta obriga a subir a altura da **cena** do teste de componente (1024 × 768
   por padrão), nunca a do `Box` interno — o `Box` não é o que limita o `LazyColumn`.
5. O `modifier` de um campo composto desce até o `BasicTextField`, não fica na coluna: ele carrega a
   `testTag`, e `performTextInput` exige o `RequestFocus` que só o campo tem.
6. `Modifier.border` arredonda o traço **para cima** (`ceil(width.toPx())`, `Border.kt`) e o pinta
   **depois** do conteúdo. Numa caixa de 4dp o anel de 1dp vira 2px a partir de densidade 1,05 e come
   a caixa inteira: a barra de cota ficava cinza com a cota em 37% nas escalas de 105% e 110%
   (issue #83). Borda que precisa ocupar layout é **fundo mais padding** — o `roundToPx` do padding
   acompanha a altura, e é o `box-sizing: border-box` que o protótipo já especificava. O defeito é de
   **pintura**: `boundsInRoot` devolvia a altura cheia nas duas escalas, então só bitmap
   (`captureToImage`) o pega.

**Escala da interface** (`AppTheme(uiScalePercent = …)` + `UiScalePreferences.kt` + slider na aba
Geral): a escala troca a **densidade** da composição, nunca a escala tipográfica. Subir só a
tipografia deixaria ícone, padding, altura de linha e alvo de clique — todos `Dp` fixos — do tamanho
anterior, e o rodapé continuaria pequeno ao lado de um texto maior; densidade é o único ponto em que
dp e sp crescem juntos e as proporções do protótipo permanecem. Só `density` é multiplicado:
multiplicar `fontScale` junto aplicaria a escala duas vezes ao texto.

- **O padrão persistido é 115 e o default do parâmetro é 100.** O neutro existe para o
  `ScreenshotGenerator`, o `TourGifGenerator` e os testes de componente manterem a geometria de
  referência — as capturas do README **não** são geradas na escala do app.
- **Cada janela precisa receber o valor.** `Window`/`DialogWindow` do Compose Desktop têm composição
  própria e a plataforma reprovisiona `LocalDensity` na raiz de cada uma: provisionar na janela pai
  não atravessa para a filha, e a janela esquecida renderiza a 100% sem erro nenhum.
- **A moldura não escala sozinha.** Densidade maior mostra o mesmo conteúdo maior dentro da mesma
  janela, ou seja, menos conteúdo. `scaledWindowSize` corrige a janela principal pela razão entre a
  escala aplicada e a nova — nunca contra 100, ou duas mudanças seguidas multiplicariam duas vezes —
  e nos tamanhos default das outras janelas o fator entra na criação. Tamanho **persistido** é
  escolha do usuário e não é reescalado, com uma exceção de uma vez só: quem já tinha janela salva
  antes desta versão a recebe corrigida de 100 para o padrão novo, e é `hasPersistedUiScale` — chave
  presente, não valor igual ao default — que fecha essa porta depois.
- O redimensionamento acontece no commit do coletor com debounce, não no callback do slider: janela
  AWT reposicionada por pixel arrastado é inutilizável. O conteúdo, esse, escala ao vivo.
- O teste que prova a fiação (`AppThemeScaleTest`) mede **pixels** (`boundsInRoot`), não `Dp`: a
  conversão para `Dp` usa a densidade do próprio nó, que é a que está sendo alterada, e devolveria
  100dp nos dois casos — um teste que passa sem medir nada.

**Densidade do dashboard** (`DashboardScreen.SuccessContent` + `ResponsiveDashboardCardGrid`): a
janela principal usa o **corpo denso** do protótipo — `AppSpacing.md` na horizontal, `AppSpacing.sm`
na vertical e `AppSpacing.md` entre cards —, e não o `AppSpacing.lg` das outras cinco. É a única
janela que o usuário deixa estreita ao lado do editor, e ali 16dp de margem mais 16dp de vão eram
largura que faltava dentro do card. A coluna rolável **não** reserva folga para a barra de rolagem:
ela flutua sobre o padding direito da grade. Somadas, as duas davam 28dp à direita contra 16 à
esquerda.

**Modo somente cards** (`DesktopWindowFrame(compact)` + `DashboardScreen(showFooter)` +
`CardsOnlyModePreferences.kt`): a janela sem barra de título e sem rodapé. **Não é valor novo em
enum nenhum** — são dois booleanos, um por moldura, e a preferência é um `Boolean` em
`PreferencesSettings`, ao lado de "manter sempre visível".
- **A faixa de título só é composta durante o hover.** Ela carrega a `WindowDraggableArea`, que usa
  arrasto **imediato**; o card usa `detectDragGesturesAfterLongPress`. Com a faixa presente o tempo
  todo, o arrasto da janela venceria a pressão longa e reordenar o primeiro card seria impossível.
  Invisível ela também não pode ser clicável: um botão de fechar transparente é pior que nenhum.
- **Três saídas, e nenhuma é dispensável**: a faixa, o item na bandeja e `Ctrl+Shift+M`. O modo
  esconde o botão de fechar e a engrenagem; com a janela coberta por outra, só o teclado resta. A
  bandeja também passou a abrir as Configurações, que só existiam no rodapé.
- A escala neutra dos geradores de captura não conhece o modo: `showFooter` é `true` por default, e
  as capturas do README continuam com a moldura inteira.

**Barra HUD** (`DesktopWindowFrame(hud)` + `HudBar` + `HudModePreferences.kt`; issue #164): terceiro
chrome, ainda mais discreto que o modo somente cards — a mesma janela principal encolhida a uma
pílula de 320×24dp (`HUD_PILL_WIDTH_DP` + `AppChrome.hud`), ancorada no **canto superior direito** da
tela, sempre no topo. Não mostra cards, só `AppStatusIndicator` com o pior risco entre todas as
cotas, a fonte que o determinou
(`UsageAlertViewModel.worstSnapshot`, ao lado de `worstRisk` — a bandeja continua lendo só o nível) e
o tempo até o reset (`resetLabel`, a mesma função do cabeçalho expandido do card — nenhum formato de
data novo). **Também não é valor novo em enum nenhum**: `hud` é um terceiro booleano de
`DesktopWindowFrame`, irmão de `compact`, e a exclusão mútua entre os dois é regra de negócio dos
setters em `Main.kt` (ligar um desliga o outro), não do tipo.
- **A janela muda de tamanho de verdade — não é overlay como o modo somente cards.** `alwaysOnTop`
  vira `alwaysOnTopEnabled || hudMode` (expressão recomposta a cada leitura, nunca uma gravação: a
  preferência do usuário não é sobrescrita) e `resizable = false`. Sair restaura tamanho, posição e
  `placement` de antes, guardados num `remember` local — não em `MainWindowSnapshot`, que nunca
  carregou posição porque a janela normal não precisava dela.
- **Duas armadilhas de geometria, as duas medidas, não deduzidas.** (1) O coletor que persiste
  tamanho/posição da janela (`LaunchedEffect(mainWindowState, settings)`, debounce de 250ms) ignora
  toda mudança enquanto `hudMode=true` — sem o guard, a pílula seria gravada como "tamanho normal" e
  o app nasceria nela na próxima abertura. (2) `ApplyWindowMinimumSize` usa um piso bem menor em HUD
  (`HUD_MIN_WINDOW_WIDTH_DP` + `AppChrome.hud`), chamado **antes** do efeito que redimensiona, na
  mesma ordem textual dentro do `Window { ... }`: os dois reagem a `hudMode` na mesma recomposição, e
  é a ordem — não o tipo — que decide qual dos dois o AWT aplica primeiro. Sem isso o piso normal
  (240×320dp) impediria a pílula de existir, e a janela ficaria presa no tamanho antigo por baixo do
  que `mainWindowState.size` pede.
- **Largura total foi a primeira versão, e estava errada — achado testando ao vivo, não antecipado
  no plano original.** Sempre no topo (`alwaysOnTop`) mais largura da tela inteira cobria os
  controles de qualquer outra janela que também tivesse algo nos primeiros 24dp do topo: barra de
  menu de IDE, atalhos de editor. O Compose Desktop não tem click-through parcial numa `Window`
  comum — a região inteira captura o clique, visível ou não —, então a única correção viável nesta
  arquitetura (mesma janela, sem hack de shape nativo) é reduzir a área: `HUD_PILL_WIDTH_DP` (320,
  mesma ordem de grandeza de `NarrowCardWidthThreshold` — não o mesmo token, responde a outra
  pergunta) ancorada só no canto superior direito via `fitWindowPosition`, que já existia para prender
  posição persistida dentro da área útil. `HudBar` não sabe a própria largura — preenche o que
  recebe —, e por isso `sourceLabel`/`resetLabel` truncam com reticências (`TextOverflow.Ellipsis`)
  em vez de estourar o container.
- **Três saídas, mesmo padrão do modo somente cards**: clique em qualquer ponto da pílula (não há
  botão próprio — a semântica vai no container inteiro, não só em `onClickLabel`), o item na bandeja
  e `Ctrl+Shift+H`, combinação própria sem colidir com o `Ctrl+Shift+M` do modo somente cards.
- **`HudBar` não usa `WindowScope` nem `WindowDraggableArea`.** Decisão tomada antes de escrever
  código: a pílula não tem cards para reordenar, então o argumento que mantém a área de arrasto fora
  do `CompactTitleBarOverlay` (colidir com a pressão longa do drag de card) não se aplica aqui — mas
  arrasto sempre presente também não protege nada nesta pílula. Ancoragem é geometria decidida por
  `Main.kt`, nunca gesto do usuário.
- **Não é primitiva de risco nova.** `AppHudBar`/`HudBar` reusa `AppStatusIndicator` por dentro —
  mesma relação de `AppUpdateStrip` com `AppButton` no design system.

**Piso de largura da tooltip de cota** (`shouldShowQuotaTooltip` em `ApiUsageCardDensity.kt`):
abaixo de 320dp de card o popup não abre. Ele tem piso de 180dp e cinco a seis linhas de métrica, e
a janela do modo somente cards tem ~230dp úteis — ali a tooltip cobre o card inteiro, escondendo
justamente o número que o ponteiro apontava. Constante **própria** e não reuso de
`NarrowCardWidthThreshold`, que coincide no valor mas responde a outra pergunta: uma é sobre apertar
padding, a outra é sobre o popup caber. O preço está aceito: em card estreito não há caminho visual
para a projeção de uso, e ela volta abrindo a janela. Só as tooltips de **cota** caem — as de uma
linha (nome truncado da API, botão de sessão) ficam, porque não cobrem nada.
- **A `testTag` do bloco de cota mora no conteúdo, não no `HoverTooltipBox`.** Presa à tooltip, ela
  desapareceria da árvore junto com ela em card estreito, e os testes que buscam `quotaBlockTag`
  passariam a não encontrar nó nenhum.
- **A explicação do semáforo é o `footnote` da tooltip da cota.** O ponto colorido nunca teve
  tooltip própria — os dois usos de `RiskSemaphoreDot` passam `showTooltip = false`, porque dois
  `TooltipBox` aninhados disputam o mesmo hover —, e por isso a frase de `riskDotTooltipSubtitle`
  não chegava à tela em tamanho nenhum de janela. A métrica `Projeção de uso` continua ao lado: ela
  diz qual é o estado, o rodapé diz o que ele significa.
- **O estado da fonte tem ponto e palavra no cabeçalho** (`API_USAGE_CARD_STATUS_TAG` +
  `worstQuotaRisk`): o `RiskSemaphoreDot` de cada cota é só ponto, e sozinho ele deixava a cor
  informando o estado — que é exatamente o que este sistema visual não faz. O badge resume o **pior**
  risco entre as cotas pela ordem do enum, não pelo percentual: 40% às onze da manhã pode ser pior
  que 80% dez minutos antes do reinício. Cota vencida não entra, e sem projeção conhecida não há
  badge — "Normal" ali seria uma garantia que ninguém deu. Os rótulos saem de `riskLevelLabel`, que
  já existia. O badge inteiro também abre um `HoverTooltipBox` persistente: ele informa a cota que
  determinou o pior estado e reutiliza a explicação da projeção, inclusive para `Normal` — a cota
  deve resetar antes de esgotar — e para `Atenção`/`Crítico` — a cota deve esgotar antes do reset.
- **Saldo pré-pago não usa a régua de razão** (`riskSummary` com `hasKnownResetAt = false`): aquela
  régua pergunta "quanto do tempo até o reset a cota aguenta", e um saldo não reseta. O DeepSeek
  grava `periodEndAt = Instant.DISTANT_FUTURE`, o que dava razão de ~0,002 e fazia **qualquer**
  consumo maior que zero virar `WILL_EXCEED` — card em Crítico permanente, ponto de risco da bandeja
  aceso para sempre e a tooltip prometendo um reset uma linha abaixo de "Saldo não expira"
  (issue #109). Sem reset a pergunta é absoluta: **tempo de autonomia**, com
  `BALANCE_CRITICAL_RUNWAY_MILLIS` (7 dias) e `BALANCE_WARNING_RUNWAY_MILLIS` (14 dias), e a data
  prevista continua preenchida **inclusive em `ON_TRACK`** — ali ela é a resposta a "quando acaba",
  não o aviso. A conta em si já estava certa e não mudou: para `CURRENCY_USD`,
  `calculatePositiveDelta` soma as **quedas** do saldo e `remaining` é o saldo atual.
  - **`hasKnownResetAt` passou a ser persistido** (`usage_snapshots.has_known_reset_at`, `DEFAULT 1`,
    migração por `hasColumn` como a de `account_id`). Sem a coluna o histórico só via `periodEndAt`,
    e ele não distingue "reset distante" de "não existe reset": DeepSeek grava `DISTANT_FUTURE`,
    Kilo e OpenCode gravam o próprio `capturedAt`, os créditos da Anthropic gravam outra sentinela.
    A decisão lê o **último** ponto da série, então a primeira coleta depois da migração já corrige a
    cota — não é preciso reescrever histórico.
  - **`currentSegment` não foi tocado**, e é por isso que Kilo e OpenCode continuam sem projeção
    nenhuma: com `periodEndAt = capturedAt` cada coleta parece um reset, o segmento fica com um ponto
    e o forecast devolve `InsufficientData`. Mesma raiz, sintoma oposto, issue própria — ligá-la aqui
    acenderia projeção em duas fontes sem verificação delas.

**Aviso de fonte é hint, não banner** (`CardNoticeHint` em `ApiUsageCard.kt`): os
`ApiUsageNotice` saem como uma exclamação âmbar (`Icons.Rounded.ErrorOutline`) no cabeçalho, ao
lado do badge de status, e o texto vive na tooltip. Eram `AppBanner` empilhados abaixo das cotas,
e como o texto deles não muda entre coletas, na janela estreita os dois avisos do Codex ocupavam
mais altura que o `39%` que o card existe para mostrar (issue #76). Um ícone por card, não um por
aviso: a tooltip lista todos, com bullet só a partir do segundo.
- **Este hint não tem piso de largura**, ao contrário da tooltip de cota: aquele piso existe porque
  o popup cobre o número que o ponteiro apontava, e este não aponta número nenhum. Sem tooltip o
  aviso ficaria inacessível justamente na janela estreita, que é onde ele mais atrapalhava.
- **As frases inteiras vão no `contentDescription` do ícone.** Sem hover a tooltip não existe na
  árvore, então é por ela que leitor de tela e testes chegam ao aviso — os dois asserts de notice
  em `ComponentTest` usam `onNodeWithContentDescription(..., substring = true)`.

**Regras que continuam valendo**: nenhuma animação infinita nova (trava o `waitForIdle`);
`ShimmerBox` existe mas não se replica; nenhuma composable nova em `main()`; nenhum
`Column + verticalScroll` vira `LazyColumn`; nenhum valor novo em enum existente.

**Marca**: `tools/brand/render_icons.py` gera PNG, ICO e ICNS a partir do monograma descrito em
código — `monogram.svg` ao lado é referência e não é lido. O `.icns` só é validado no job
`build-macos` do release.

**Relatório PDF**: a IBM Plex Mono vai embutida (`PDType0Font.load` com subconjunto) e o
saneamento WinAnsi de `UsageReportDocument.sanitized` **permanece** — a fonte tem os acentos, mas
trocar o contrato de caracteres é outra migração. Sem o recurso no classpath, o relatório cai para
Helvetica em vez de falhar.

## CI e testes

Dois workflows: `ci.yml` (suíte desktop no Windows + cenários do instalador) e `ci-server.yml`
(suíte do servidor no Ubuntu). O plano com as medições está em
[`docs/planos/ci-testes-detalhe-e-velocidade-execucao.md`](docs/planos/ci-testes-detalhe-e-velocidade-execucao.md).

- **O cache do Gradle é da `gradle/actions/setup-gradle`, não do `cache: 'gradle'` do `setup-java`.**
  O post-step daquele arquiva o `~/.gradle` com o daemon vivo e no Windows o `tar` morre nos `.lock`
  (`Device or resource busy` → `exit code 2`). O efeito era total e silencioso: todo run começava com
  `gradle cache is not found` e o repositório não tinha **uma** entrada Windows em `gh cache list`. Os
  ~57 s gastos antes da primeira tarefa eram **download** — a mesma fase custa 0,44 s numa máquina com
  o `~/.gradle` quente. **Só a `main` escreve o cache** (`cache-read-only` fora dela): cache gravado
  num run de PR fica com o escopo daquele PR e nenhum outro run consegue lê-lo.
- **A suíte roda em um fork só, e forks paralelos são opt-in por `-PtestForks=N`.** O ganho é real —
  1m24s serial, 52 s com 4 forks, resultado idêntico —, mas o **Skiko** impede ligá-lo por default:
  `Library.unpackIfNeeded` extrai `skiko-windows-x64.dll` para `~/.skiko/<hash>/` com um `Files.move`,
  e no Windows esse move falha com `AccessDeniedException` quando outro processo já abriu o destino.
  Numa máquina de desenvolvimento o cache está quente desde sempre e não há extração nem corrida; num
  runner limpo, todo fork tenta extrair ao mesmo tempo. Passou local, passou no primeiro run do CI e
  derrubou o segundo com 41 testes de UI em `ExceptionInInitializerError`. **Divergência entre verde
  local e vermelho no CI em teste de UI: olhe o `~/.skiko` antes de olhar o teste.**
- **O filtro por path continua, e um job que pulou a suíte tem de dizer que pulou.** Rodar 5 min de
  Windows por um typo no README é a lentidão que a issue #93 reclama; mas um `Successful in 5s` que
  não executou teste nenhum é indistinguível de um que executou, e foi ele que abriu a issue. Os dois
  jobs publicam no `$GITHUB_STEP_SUMMARY` — contagem e classes mais lentas quando rodam, **NAO
  EXECUTADA** com motivo e contagem de arquivos quando não. O `--require` do
  `tools/ci/test-summary.mjs` derruba o job quando a suíte devia rodar e não produziu XML: é o que faz
  "passou sem executar" ficar vermelho.
- **Um parser de JUnit XML para os dois jobs** (`tools/ci/test-summary.mjs`), e por isso o `vitest`
  escreve no mesmo formato (`npm run test:ci`). Duas implementações divergiriam justamente na
  contagem, que é o número que o resumo existe para dar. Sem dependência externa: no job do desktop
  não há `npm ci`.
- **`delay` dentro de `runTest` avança tempo VIRTUAL e não espera trabalho de fundo.** Os view models rodam em `Dispatchers.Default`; uma espera escrita com `delay` volta na hora, e um laço de 200 tentativas gira em tempo zero e devolve o primeiro estado que encontrar. Era assim que
  `HistoryViewModelTest > emits Empty state when enabledApis is empty` observava `Loading` num runner
  carregado depois de anos passando (run `32855876748`), e era assim que um `delay(100)` escrito para
  provar que *nada* aconteceu passava sem esperar nada. Espera de estado de view model usa
  `yield()` + `Thread.sleep`, como `pauseForBackgroundWork` em `DashboardViewModelTestSupport`.
- **Cobertura é relatório, não trava.** O Kover estava aplicado desde sempre instrumentando toda
  passada — 6 a 7 s medidos — sem que nenhuma tarefa de relatório rodasse em lugar nenhum. Agora a
  instrumentação é **opt-in** por `-Pcoverage`, que só o push na `main` usa, e a mesma passada serve
  suíte e relatório. Sem `koverVerify` e sem piso: limiar calibrado antes de a linha de base existir é
  limiar calibrado no escuro. Linha de base de 2026-08-25: **82,7% de linhas**, 52,3% de ramos.
  `MainKt` fica fora do relatório por filtro — é o grafo de DI mais a janela, e contá-lo afunda o
  número sem apontar lacuna que se possa fechar.
- **Cobertura alta não é a mesma coisa que costura certa.** `RemoteTeamDataSource` está em 1,9%
  porque os testes **herdam da classe real** e sobrescrevem os 20 métodos: o nome aparece em três
  arquivos de teste e nenhuma linha de HTTP executa (issue #94). Ao ver uma classe `open` com todo
  método `open`, pergunte o que sobra dela quando o teste a substitui.
- **`choco install` detecta antes de instalar e tem retry.** Um 504 da `community.chocolatey.org`
  derrubou a `main` em 25/08 sem nenhum defeito de código. O WiX não lança ao fim: sem ele o roteiro
  pula o cenário S7 com aviso, e derrubar o job custaria os outros seis.

## Convenções de código

- **Nomes em inglês**, comentários em português.
- Evitar scope functions aninhadas (`let`, `apply`, `run`). Preferir fluxo explícito.
- Commits: Conventional Commits em inglês + `Co-Authored-By: Claude <nome do modelo> <noreply@anthropic.com>`
  — o nome do modelo que gerou o commit (ex.: `Claude Sonnet 5`, `Claude Opus 5`), não um valor fixo:
  planos em `docs/planos/` já registram qual modelo executou cada atividade, e o trailer do commit é a
  mesma informação em outro lugar.
- **Uma atividade, um commit.** Cada unidade de trabalho fecha sozinha: código, teste e documentação
  da mesma decisão entram juntos. Commit que só compila com o próximo não é atômico, e commit que
  junta duas decisões impede reverter uma sem perder a outra.
- **Issue ou comentário criado no GitHub abre com `🤖 Escrito por Claude Code, a pedido de
  @<usuário>`.** O `gh` CLI fica autenticado com a conta pessoal do usuário, não com uma conta ou bot
  próprio de Claude — sem a linha, o texto aparece publicamente como se o usuário tivesse escrito, o
  que já causou confusão (issue #124). Vale para `gh issue create`/`gh issue comment`/`gh api` sobre
  `issues/comments`; não muda a autoria de commit, que já tem o trailer acima.
- **Trabalho com plano em `docs/planos/` mantém ali a tabela de pontos de situação**, uma linha por
  atividade, escrita **no mesmo commit** da atividade que ela descreve — em commit separado a linha
  pode existir sem a mudança e vice-versa, e o registro deixa de servir para auditoria. Cada entrada
  carrega o comando que rodou e o resultado, nunca a intenção.

## Endpoints externos

| API | Endpoint | Auth |
|---|---|---|
| Anthropic | `GET https://api.anthropic.com/api/oauth/usage` | `Authorization: Bearer {accessToken}` do credentials.json + `anthropic-beta: oauth-2025-04-20` |
| Anthropic (renovação) | `POST https://platform.claude.com/v1/oauth/token` | Corpo JSON com `grant_type`, `refresh_token`, `client_id` e `scope` — sem `client_id` responde 400 `Invalid request format` |
| MiniMax | `GET https://www.minimax.io/v1/token_plan/remains` | `Authorization: Bearer {apiKey}` lida de `~/.usage-monitor/api-keys.json` |
| OpenCode Go | `GET https://opencode.ai/zen/go/v1/usage` | `Authorization: Bearer {apiKey}` lida de `~/.usage-monitor/api-keys.json` — a mesma chave do `chat/completions` do Zen |

Response Anthropic retorna `five_hour`/`seven_day` com `utilization` em **percentual** (0–100) e `resets_at` em ISO 8601 (pode ser nulo), mais `extra_usage`/`spend` com os créditos de uso em unidades menores da moeda da conta.

Response MiniMax retorna `model_remains[]` com cotas em **requests** (não tokens), timestamps em epoch milliseconds.

Response OpenCode Go retorna `usage.{rolling,weekly,monthly}`, cada uma com `status` (`ok` ou `rate-limited`), `percent` (0–100) e `resetsAt` em ISO 8601. **Não devolve valor em dinheiro** — nem gasto, nem limite. O endpoint **não está documentado publicamente** (PR anomalyco/opencode#16513, merged em 2026-08-11) e não declara versão; sem `Authorization` responde `401 AuthError`, e com chave válida sem plano Go responde `403 EntitlementError`. O saldo pago do Zen **não tem endpoint**: `/zen/v1/balance` responde 404.
