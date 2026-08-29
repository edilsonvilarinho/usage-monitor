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

### O campo nunca é pré-preenchido com a chave guardada

A linha já diz "Chave configurada". Trazer o segredo para dentro da composição não ajuda a trocá-la
— para trocar digita-se a nova — e amplia a superfície de exposição sem contrapartida. Para apagar
há o botão dedicado.

### Remover a chave desliga a fonte

É o mesmo estado que o filtro de arranque `API_KEY_DEPENDENT_SOURCES` (`Main.kt`) já produziria no
boot seguinte. Deixá-la ligada faria a coleta falhar com 401 a cada 10 minutos até o próximo
reinício.

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
| C05 | `toggleable` movido da linha para o `AppSwitch` + `apiSelectorSwitchTestTag` | ⏳ Pendente | — |
| C06 | `onEditApiKey` + `AppIconButton` de lápis em `ApiCheckboxRow` | ⏳ Pendente | — |
| C07 | `MonitoredApisTab` abre o diálogo pelo lápis | ⏳ Pendente | — |
| C08 | Botão "Remover chave" (`GHOST`) no `ApiKeyDialog` | ⏳ Pendente | — |
| C09 | `onApiKeyRemove` fiado em `Main.kt` | ⏳ Pendente | — |
| C10 | Troca de chave pelo lápis com a fonte já ligada | ⏳ Pendente | — |
| C11 | Protótipo `§12c #cfg-apis` com o ícone de edição | ⏳ Pendente | — |
| C12 | `allTests` verde + QA manual | ⏳ Pendente | — |
