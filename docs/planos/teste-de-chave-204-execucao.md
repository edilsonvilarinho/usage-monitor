# "Testar chave" por fonte na aba APIs — plano de execução

Issue [#204](https://github.com/edilsonvilarinho/usage-monitor/issues/204): botão "Testar chave" por
fonte na aba APIs, com diagnóstico do erro.

## Problema

Ao cadastrar uma chave de API nas Configurações → APIs, o usuário não recebia retorno nenhum. Chave
colada errada, chave expirada, assinatura ausente no plano ou proxy da empresa barrando: tudo isso só
aparecia na coleta seguinte, como banner no dashboard — outra tela, minutos depois, sem dizer o
motivo. Cadastro de chave é o primeiro contato de quem instala o app com cada fonte, e o retorno
desse primeiro contato chegava fora do lugar em que a pergunta nasce.

## O que foi verificado antes de decidir

- **A MiniMax responde `HTTP 200` com erro no corpo.** `MiniMaxRepositoryImpl.kt:30-46` testa
  `baseResp.statusCode != 0` e traduz `2062` em "sem plano/token ativo". Um teste que olhasse o
  status HTTP aprovaria uma chave que a coleta real recusa. **É este fato, sozinho, que decide o
  desenho**: o teste tem de passar pelo repositório.
- **Os quatro repositórios já recebem a chave por injeção** (`apiKeyReader: () -> String?`,
  `Main.kt:558-589`), o que permite montá-los com a chave candidata sem tocar em nada persistido.
- **`requireSuccess` embute o status na mensagem** (`RemoteApiDataSource.kt:311-321`,
  `"$sourceName HTTP $status: $body"`), então classificar 401/403 por marcador de substring é o
  mesmo mecanismo que 429 e 503 já usam — não um caminho novo.
- **O precedente de client efêmero está pronto**: `checkProxyConnection` (`Main.kt:1263-1294`) monta
  um `HttpClient` com o valor corrente da configuração, bate no endpoint e fecha o client.
- **`isConnectivityFailure` estava privado** no `DashboardViewModel` (`:793-810`). Reimplementar a
  checagem na UI daria dois donos da mesma decisão, e o usuário atrás de proxy corporativo seria
  mandado revisar uma credencial correta.

## Decisões

1. **O teste é a coleta real, pelo repositório da fonte.** Nada de endpoint próprio de teste, que
   poderia passar enquanto a coleta falha, nem de `GET` cru com o status examinado na UI, que erraria
   a MiniMax. `testApiKeyUsage` monta o repositório com `apiKeyReader = { chaveCandidata }` e devolve
   o mesmo `Result` que o dashboard receberia.
2. **`HttpClient` efêmero com o proxy corrente.** Nunca o compartilhado, que é montado uma vez no
   arranque e é usado por 5+ consumidores com laços próprios. O proxy sai de `resolveEffectiveProxy`
   **sem** forçar o modo manual, ao contrário do teste de proxy: aqui o teste tem de usar exatamente
   o que a coleta usaria, e ignorar um `HTTPS_PROXY` do ambiente daria falso negativo.
3. **A classificação é a mesma do dashboard, e a ordem é a de `warningFor`.** 429 e 503 vêm antes de
   credencial; a assinatura ausente do OpenCode Go vem antes do 403 genérico. Sem essa ordem, quem só
   usa o Zen pago seria mandado revisar uma chave correta.
4. **Três tons, não dois.** O teste de proxy tem OK e CRITICAL porque a conexão passa ou não passa.
   Aqui existe um terceiro veredito — chave **válida** com plano ou assinatura ausente, e também 429
   e 503, em que o texto diz que **não houve veredito sobre a chave**. Âmbar, não vermelho: marcar
   um limite temporário como falha mandaria trocar uma credencial que está certa.
5. **`isUnauthorizedIssue` fica fora de `isConfigurationIssue`.** Os marcadores existentes são
   condições que o app detecta antes de sair para a rede; 401/403 é veredito da API, e hoje o
   dashboard o trata como erro genérico. Incluí-lo mudaria o comportamento de sete fontes numa
   passada que existe para outra coisa.
6. **O botão no diálogo, não na linha da lista.** É onde a chave é digitada, e um controle só cobre
   os dois casos: cadastro novo (testa o texto digitado) e chave que expirou (campo vazio testa a
   guardada). `GHOST` encostado no "Salvar" — `PRIMARY` é uma por tela e continua sendo o Salvar.
7. **O veredito é texto junto do campo, e morre ao digitar.** Toast some antes de ser lido; rodapé
   afastaria a resposta da pergunta. E o veredito descreve o texto que estava no campo: no primeiro
   caractere novo, e ao trocar de fonte, ele deixa de valer. Ponto **e** palavra
   (`AppStatusIndicator`), como todo estado deste sistema.
8. **A chave candidata não é gravada nem registrada.** Só o `Salvar` persiste; nenhum breadcrumb
   recebe o valor, e `sanitizeUiErrorMessage` — que o veredito reusa — já redige `Bearer …` caso a
   API devolva a credencial no corpo do erro.

## O que os testes não pegariam, e por isso está escrito aqui

`testApiKeyUsage` tem `when` **exaustivo e sem `else`** sobre `ApiSource`: fonte nova obriga a
decidir se tem chave testável, em vez de cair calada num ramo genérico.

## Pontos de situação

| # | Atividade | Comando / evidência | Resultado |
|---|---|---|---|
| A01 | `isConnectivityFailure` extraído do `DashboardViewModel` para `presentation/viewmodel/NetworkFailure.kt`; imports de rede removidos do view model | `gradlew.bat compileKotlinDesktop` | `BUILD SUCCESSFUL` |
| A02 | `isUnauthorizedIssue` + `UNAUTHORIZED_MARKERS` em `UiState.kt`, fora de `isConfigurationIssue` | `gradlew.bat compileKotlinDesktop` | `BUILD SUCCESSFUL` |
| A03 | `ApiKeyCheck.kt`: `ApiKeyCheckStatus`, `ApiKeyCheckUiState`, `apiKeyCheckResult` | `gradlew.bat desktopTest --tests "com.usagemonitor.presentation.ApiKeyCheckTest"` | 13 testes, `BUILD SUCCESSFUL` |
| A04 | Botão e resultado no `ApiKeyDialog`; `apiKeyCheck`/`onApiKeyTest`/`onApiKeyCheckReset` plumados por `SettingsDialogContent` e `MonitoredApisTab` | `gradlew.bat desktopTest --tests "com.usagemonitor.ui.ApiKeyDialogTest"` | 6 testes, `BUILD SUCCESSFUL` |
| A05 | `checkApiKey` no `Main.kt` + `ApiKeyProbe.kt` (`testApiKeyUsage`) | `gradlew.bat compileKotlinDesktop` | `BUILD SUCCESSFUL` |
| A06 | Testes, protótipo (`§12c`), kit `Settings.jsx` e ajuda dentro do app (PT/EN) | `gradlew.bat allTests` | `BUILD SUCCESSFUL` em 3m04s |

### Defeitos encontrados durante a execução

| O que era | Como apareceu | Correção |
|---|---|---|
| `assertTextContains` no nó da `testTag` do resultado | `AssertionError: Node #412 ... Has 1 child` — o texto estava no filho | `AppStatusIndicator` é uma `Row` que **não** mescla descendentes: a tag localiza o contêiner e o texto se afirma por `onNodeWithText`, como `NetworkSettingsSectionTest` já fazia |
| Teste semeava o veredito antes de abrir o diálogo | `Expected exactly '1' node but could not find any` | Abrir o diálogo chama `onApiKeyCheckReset` — comportamento correto, teste errado. O estado passou a morar fora do `setContent` e é escrito depois da abertura |

## Verificação manual pendente

Com chave real, no app (`gradlew.bat run`): chave válida → verde; chave com um caractere trocado →
`HTTP 401`; chave do OpenCode sem plano Go → âmbar com "sem assinatura Go ativa"; rede desligada →
texto de conexão e não de credencial; MiniMax com chave inválida → a mensagem do repositório, não um
"HTTP 200 OK".
