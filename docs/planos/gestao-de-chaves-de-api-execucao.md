# Gestão das chaves de API — execução

Issue: [#125](https://github.com/edilsonvilarinho/usage-monitor/issues/125) — "Remover as key já
cadastradas e poder cadastrar novas".

Branch `feat/manage-api-keys`, worktree `../usage-monitor-125`.

## O defeito

Duas camadas, cada uma com uma lacuna própria.

**Na UI**, o diálogo de chave só abre no caminho de **habilitar** uma fonte que ainda não tem chave:

```kotlin
// SettingsDialogContent.kt:519
if (checked && api.requiresApiKey() && api !in configuredApiKeys) { pendingApiKeySource = api }
```

Cadastrada uma vez, a chave é definitiva pela interface: não há caminho para trocá-la nem para
apagá-la. Desligar o interruptor não mexe no arquivo — a linha continua dizendo "Chave configurada"
e a chave antiga volta a ser usada ao religar.

**Na camada de dados**, `LocalApiKeyDataSource.save` tem `require(apiKey.isNotBlank())` e não existe
nenhum `clear`. Mesmo que a UI quisesse apagar, não havia o que chamar.

## Decisões

### Um ícone de lápis por linha, só nas fontes que dependem de chave

`Icons.Rounded.Edit` no fim da linha das três fontes com chave local (MiniMax, DeepSeek,
OpenCode Go), abrindo o `ApiKeyDialog` que já existe. É o mesmo padrão da aba Contas, onde
`AnthropicProfileRow` já põe lápis na linha ao lado do interruptor. Fonte sem chave não ganha ícone:
`onEditApiKey` nulo não desenha nada, e um lápis que abre um diálogo vazio seria pior que ícone
nenhum.

**O ícone vem antes do interruptor**, e não depois: assim o interruptor continua sendo o último
elemento das sete linhas e fica no mesmo x, com ou sem lápis. Com ele por último, as três linhas com
chave empurrariam o interruptor para a esquerda e a coluna quebraria. É também a ordem que o
protótipo desenha na aba Contas (`§12d`), a outra linha do app com switch e lápis juntos.

**O rótulo acessível carrega o nome da fonte** (achado da auditoria). São três lápis no mesmo painel,
e três `contentDescription` iguais anunciam ao leitor de tela três ações indistinguíveis — o que as
separa está num nó irmão, que não é lido junto com a ação. Em português a identidade vem depois do
travessão, como em `UiApiError.targetLabel` ("Anthropic — <perfil>"), e não numa frase montada: o
artigo muda com o nome da fonte ("da MiniMax", "do DeepSeek") e a concordância erraria em parte
delas. Em inglês o nome entra no meio ("Manage MiniMax key"), onde não há esse problema. As `testTag`
continuam sendo o caminho dos testes; o que mudou é o rótulo.

**Nenhuma captura do README é afetada** (verificado na auditoria): `ScreenshotGenerator.settings()`
chama `SettingsDialogContent` **sem** `initialTab`, e o default é `SettingsTab.GENERAL`. A aba APIs
não é capturada por gerador nenhum, então o lápis não chega a imagem alguma e não há captura a
regenerar.

### O campo nunca é pré-preenchido com a chave guardada

A linha já diz "Chave configurada". Trazer o segredo para dentro da composição não ajuda a trocá-la
— para trocar digita-se a nova — e amplia a superfície de exposição sem contrapartida. Para apagar
há o botão dedicado.

### Remover a chave desliga a fonte

É o mesmo estado que o filtro de arranque `API_KEY_DEPENDENT_SOURCES` (`Main.kt`) já produziria no
boot seguinte. Deixá-la ligada faria a coleta falhar com 401 a cada 10 minutos até o próximo
reinício.

### "Remover chave" é `GHOST` e vive no rodapé do diálogo

`PRIMARY` é uma por tela, e no `ApiKeyDialog` ela é o "Salvar" — o que o diálogo propõe. Remover fica
`GHOST` e não `DANGER`: o realce forte disputaria com o "Salvar" numa fileira de três botões.

Os dois botões secundários moram no mesmo slot `dismissButton` do `AlertDialog`, que é o que os
mantém na fileira do rodapé à esquerda do `PRIMARY`. Sem chave guardada o botão **não é desenhado** —
o mesmo diálogo abre no caminho de ligar uma fonte nunca configurada, e ali não haveria o que
remover.

`onApiKeyRemove` devolve `Boolean` e espelha `onApiKeySave`: o diálogo só fecha quando a camada de
dados confirma. Remoção recusada mantém a tela aberta, com o aviso de falha vindo do toast.

### O campo do diálogo no protótipo estava pré-preenchido

`§12c` desenhava o `input` com `value="sk-minimax-exemplo-nao-use"`, o que contradiz a decisão de
nunca trazer o segredo guardado para dentro da composição. Divergência entre a decisão registrada e
o protótipo se resolve corrigindo o protótipo, no mesmo commit: o campo ficou vazio, com
`placeholder="Cole a chave nova"`.

### Trocar a chave não reafirma o interruptor

O caminho de salvar chamava `onApiToggle(source, true)` incondicionalmente, porque até aqui ele só
existia para o caso de **ligar** uma fonte desligada. Pelo lápis, com a fonte já ligada, isso
regravaria o mesmo conjunto em `enabledApis`, dispararia uma segunda coleta e — o efeito visível —
trocaria o aviso de "chave de API salva" pelo de "APIs monitoradas", que não é o que o usuário fez.

A correção mora em `SettingsDialogContent`, não em `Main.kt` como o plano supunha: quem sabe se a
fonte já está ligada é a tela, que recebe `enabledApis`; o handler do `Main.kt` só executa a ordem
que ela dá.

### A remoção não tem teste unitário próprio, e isso é deliberado

`onApiKeyRemove` é uma lambda de fiação dentro de `main()`, exatamente como `onApiKeySave` logo
acima dela, que também não tem teste. Extraí-la para função testável exigiria passar seis capturas
(`apiKeyDataSource`, `apiKeySettings`, `enabledApis`, `settings`, `viewModel`, `showSettingsToast`)
e abriria um desenho divergente do vizinho para a mesma coisa. O que a lambda decide está coberto
pelas duas pontas: `LocalApiKeyDataSourceTest` prova a gravação, e o teste de componente prova o
contrato do `Boolean` — fecha no `true`, mantém aberto no `false`.

### Sem valor novo em `SettingsField`

A remoção reusa `SettingsField.API_KEY`: é a mesma coisa sendo gravada, e um valor novo obrigaria
ramo em cada `when` de mensagem sem dizer nada que o usuário não veja na tela.

### `clear` apaga uma chave, nunca o arquivo

`clear` grava string vazia no modelo e reescreve o arquivo inteiro. Medido ao escrever o teste: o
`Json` da classe está com `encodeDefaults` desligado, então na prática **o campo sai do JSON** em vez
de ficar `""` — que é exatamente o estado que uma fonte nunca configurada já tem hoje no mesmo
arquivo. `ApiKeySettingsDto` tem default `""` em todos os campos, então versão anterior do app lê
"sem chave" em vez de falhar no parse.

Apagar a chave e apagar o arquivo não são a mesma coisa: as outras duas chaves continuam lá.

### O `toggleable` sai da linha e fica só no interruptor

`ApiCheckboxRow` aplicava `Modifier.toggleable` na **linha inteira**, e `toggleable` traz
`mergeDescendants = true`. Com um `AppIconButton` dentro dela o `contentDescription` do ícone seria
mesclado no nó do pai: `onNodeWithContentDescription` encontraria a **linha**, e `performClick()`
alternaria o interruptor em vez de abrir o diálogo. É a armadilha 3 do `CLAUDE.md` na versão de
botão de ícone.

O `AppSwitch` já publica `ToggleableState` com `Role.Switch` (`AppControls.kt`), então
`assertIsOn`/`assertIsOff` continuam funcionando, agora contra o nó do interruptor — que é
exatamente o que `AnthropicProfileRow` já faz no mesmo arquivo, com switch e botão de ícone
convivendo. O hover da linha não depende do `toggleable`: ele vem do `hoverable` interno do
`AppDataRow`.

Foram **quatro** testes redirecionados, não três: além dos três de `SettingsDialogContent` que
clicavam na linha (`ComponentTest.kt`), `ApiCheckboxRow triggers onCheckedChange on click` clicava no
**texto** "MiniMax" e só passava porque o `toggleable` da linha capturava o clique. Ele virou
`ApiCheckboxRow triggers onCheckedChange from the switch` e mira o interruptor, com `assertIsOff`
antes do clique — que é a afirmação que a linha nunca chegou a fazer.

### Nenhuma primitiva nova

`AppIconButton`, `AppSwitch` e `AppDataRow` cobrem tudo. Nada a acrescentar em `docs/design-system/`
além do que o protótipo registra em `§12c #cfg-apis`.

## Pontos de situação

Uma linha por micro-atividade, escrita **no mesmo commit** da atividade que ela descreve. A coluna
de verificação carrega o comando que rodou e o **resultado real**, nunca a intenção.

| # | Micro-atividade | Situação | Verificação |
|---|---|---|---|
| C01 | Este plano, com a tabela de pontos de situação | ✅ Concluída | — (documento) |
| C02 | Comentário vivo criado na issue #125 | ✅ Concluída | `gh issue comment 125` → comentário `5462955570` publicado |
| C03 | `ApiKeySettings.withoutKey(source)` | ✅ Concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.data.LocalApiKeyDataSourceTest"` → `BUILD SUCCESSFUL`, 6 testes, 0 falhas |
| C04 | `LocalApiKeyDataSource.clear(source)` | ✅ Concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.data.LocalApiKeyDataSourceTest"` → `BUILD SUCCESSFUL`, 8 testes, 0 falhas |
| C05 | `toggleable` movido da linha para o `AppSwitch` + `apiSelectorSwitchTestTag` | ✅ Concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.ui.*"` → `BUILD SUCCESSFUL`, 294 testes, 0 falhas |
| C06 | `onEditApiKey` + `AppIconButton` de lápis em `ApiCheckboxRow` | ✅ Concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.ui.*"` → `BUILD SUCCESSFUL`, 296 testes, 0 falhas |
| C07 | `MonitoredApisTab` abre o diálogo pelo lápis | ✅ Concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.ui.*"` → `BUILD SUCCESSFUL`, 298 testes, 0 falhas |
| C08 | Botão "Remover chave" (`GHOST`) no `ApiKeyDialog` | ✅ Concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.ui.*"` → `BUILD SUCCESSFUL`, 301 testes, 0 falhas |
| C09 | `onApiKeyRemove` fiado em `Main.kt` | ✅ Concluída | `gradlew.bat desktopJar` → `BUILD SUCCESSFUL`; `gradlew.bat desktopTest --tests "com.usagemonitor.ui.*" --tests "com.usagemonitor.data.LocalApiKeyDataSourceTest"` → `BUILD SUCCESSFUL`, 0 falhas. `allTests` fica para a auditoria final, que roda as três branches em série |
| C10 | Troca de chave pelo lápis com a fonte já ligada | ✅ Concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.ui.*"` → `BUILD SUCCESSFUL`, 302 testes, 0 falhas |
| C11 | Protótipo `§12c #cfg-apis` com o ícone de edição | ✅ Concluída | Inspeção da seção `#cfg-apis`: lápis nas três linhas com chave, "Remover chave" no rodapé do diálogo, campo esvaziado, duas notas novas |
| F1 | Nome da fonte no `contentDescription` do lápis (achado da auditoria) | ✅ Concluída | `gradlew.bat desktopTest --tests "com.usagemonitor.ui.*"` → `BUILD SUCCESSFUL`, 303 testes, 0 falhas |
| C12 | `allTests` verde + QA manual | 🚧 Entregue à auditoria | `gradlew.bat desktopTest --tests "com.usagemonitor.ui.*" --tests "com.usagemonitor.data.*" --tests "com.usagemonitor.presentation.*"` → `BUILD SUCCESSFUL`, 88 classes, 1013 testes, 0 falhas. `allTests` e o QA manual **não** foram executados aqui — ver abaixo |

### O que C12 não fechou, e por quê

`gradlew.bat allTests` e o QA manual ficaram para a auditoria final, por instrução da execução: três
branches foram desenvolvidas em paralelo em worktrees separadas, e a suíte completa roda de uma
worktree por vez, em série. O que rodou aqui foram os três filtros acima, no estado final da árvore.

Roteiro do QA manual que falta rodar, na ordem:

1. Ligar MiniMax sem chave — o diálogo abre, "Remover chave" **não** aparece, salvar liga a fonte.
2. Clicar no lápis da mesma linha — o diálogo abre com o campo **vazio**; salvar uma chave nova
   mantém o interruptor ligado e o aviso é "chave de API salva", não "APIs monitoradas".
3. Clicar no lápis e usar "Remover chave" — o interruptor desliga, o card some do dashboard e
   `~/.usage-monitor/api-keys.json` fica sem o campo daquela fonte, com as outras duas intactas.
4. Conferir que Anthropic, Codex, OpenCode Zen Free e Kilo Free **não** têm lápis.
5. Repetir em tema claro e em inglês (`Manage key` / `Remove key`).
