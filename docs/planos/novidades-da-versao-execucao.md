# Novidades da versão (issue #74) — plano de execução

| | |
|---|---|
| **Modelo** | Claude Opus 5 — `claude-opus-5` |
| **Nível de esforço** | não exposto ao agente nesta sessão |
| **Ferramenta** | Claude Code (CLI) |
| **Data** | 2026-08-24 |
| **Branch** | `feat/release-notes-dialog-74`, criada de `main` |
| **Autor dos commits** | `claude <claude@anthropic.com>` |

O modelo e o esforço ficam registrados porque este documento é rastro de auditoria de trabalho feito
por agente: o que foi decidido, o que foi medido e o que ficou por verificar depende de quem
executou.

## Contexto

A atualização automática do Windows (issue #75) é **silenciosa por construção**: o usuário fecha o
app, o instalador roda sem tela e o app volta com outro número no rodapé. Hoje a única pista de que
algo mudou é a linha "Última atualização: 36.0.0 → 37.0.0, concluída." em Configurações → Geral —
uma tela que ninguém abre para descobrir o que mudou.

O recibo que o instalador grava em `~/.usage-monitor/update-receipt.properties` já é lido uma vez na
abertura (`AutoUpdateController.kt`), e até aqui **não era consumido**: não era comparado com
`CURRENT_APP_VERSION` e não disparava nada. Este trabalho é o segundo consumidor desse sinal — o
primeiro é a poda do artefato aplicado (issue #87, A21 do plano de atualização automática).

## Decisões travadas com o autor do repositório

A issue tem título e imagem que **divergem**: o título diz "mostrar o release notes após a
instalação", e a imagem de referência é a folha do Google Play, que mostra as novidades **antes** de
atualizar. As duas leituras foram apresentadas e a escolha foi registrada antes de escrever código:

| Pergunta | Escolha |
|---|---|
| Gatilho | **Depois** — primeira execução após a troca, não no anúncio da versão disponível |
| Conteúdo | **Filtrar `feat`/`fix`**, sem o prefixo do tipo e sem o hash — contra exibir o corpo cru e contra notas curadas à mão |

## O que foi construído

### Origem do conteúdo — a tag, não `latest`

`RemoteApiDataSource.fetchGitHubReleaseByTag` consulta
`/repos/{owner}/{repo}/releases/tags/v<versão>`. **Não pode ser `latest`**: quem atualizou 37 → 39
enquanto a 40 já saiu leria as notas erradas. A tag leva o `v` que `getLatestAvailableUpdate` remove
ao ler — o caminho inverso.

`GitHubReleaseDto` ganhou `body` e `published_at`, os dois anuláveis. Já vinham na resposta e eram
descartados pelo `ignoreUnknownKeys`.

Com `USAGE_MONITOR_UPDATE_FEED_URL` definida a URL é usada **tal como veio**: o servidor do smoke
test serve uma release só, e montar um caminho de tag em cima dele daria 404.

### O filtro, e por que ele é do domain

`parseReleaseNoteItems` é função pura em `domain/entity/ReleaseNotes.kt`. O corpo é gerado pelo CI
(`release-linux.yml`) a partir dos assuntos de commit, e tem forma conhecida: `## Changes`, uma linha
`- Compare: […]`, e um item por commit no formato `` - <assunto> (`<sha>`) ``.

Regras, na ordem:

1. Só linhas de lista (`- ` / `* `).
2. Descarta as linhas do gerador: `Compare:`, `Initial release`, `No user-facing commits…`.
3. Remove o sufixo `` (`<sha>`) ``.
4. `feat` e `fix` entram **sem o prefixo**; qualquer outro tipo Conventional (`chore`, `docs`, `ci`,
   `test`, `build`, `refactor`, `style`, `perf`) é descartado.
5. Linha que **não** casa Conventional passa como está — release editada à mão não pode virar tela
   vazia, que é o oposto do que o filtro existe para fazer.
6. Ordem preservada, itens idênticos deduplicados.

Os itens ficam no idioma do commit, que é inglês: são texto de commit, e o app não o traduz. Só o
cromo é PT/EN.

### Quando abre, e quando marca

`shouldShowReleaseNotes(receipt, currentVersion, seenVersion)`, também pura, exige as três: recibo de
**sucesso**, `receipt.version == currentVersion` e versão **ainda não vista**.

A terceira não é redundante. O recibo só é sobrescrito na atualização **seguinte**, ou seja,
sobrevive a todas as aberturas até lá; sem a marca a janela abriria toda vez. A marca é a preferência
`releaseNotesSeenVersion` (`ReleaseNotesPreferences.kt`) — guarda a **versão** e não um booleano,
porque um booleano precisaria ser limpo por quem escreve o recibo, que é o instalador NSIS, e ele não
conhece as preferências do app.

Os três desfechos da busca são tratados separadamente, e é aí que está a decisão:

| Desfecho | Janela | Marca |
|---|---|---|
| Notas com ≥ 1 item | abre | **sim, ao abrir** — marcar ao fechar perderia a marca num encerramento anormal |
| Release sem nada a mostrar (só `chore`/`docs`) | não abre | **sim** — retentar a cada abertura seria requisição perpétua por resposta que não muda |
| Falha de rede | não abre | **não** — a abertura seguinte tenta de novo |

Para o repositório os dois primeiros colapsam: `getReleaseNotes` devolve sucesso com `null` quando
não há item, e **sucesso com lista vazia não existe**. Quem chama trata um caso só.

### UI

`ReleaseNotesContent` (commonMain, stateless) com as primitivas existentes: `AppSectionHeader`,
`AppDataSurfaceFlush` + `AppDataRow` por item, e dois `AppButton`. `ReleaseNotesWindow` (desktopMain)
é o `DialogWindow` no padrão do bloco de "Chaves das contas": `undecorated`, `DesktopDialogFrame`,
`AppTheme` reaplicado, tamanho literal multiplicado por `uiScaleFactor` e preso por `fitWindowSize`.

**O cabeçalho não repete o título da moldura.** A barra da janela já diz "Novidades da versão X"; o
cabeçalho interno diz só "Novidades", com o subtítulo de origem e data. O mesmo texto duas vezes a
40dp de distância gasta a altura que a lista precisa — visto em execução.

**O `Box` estica, a superfície não.** A caixa da lista cresce só até onde os itens vão, e os botões
ficam ancorados no rodapé. Sem essa separação há dois estados errados, e os dois apareceram em
execução: com a superfície esticando, uma caixa vazia sob seis itens; com ela encolhendo sozinha, os
botões flutuando no meio da janela. O teto de altura continua sendo o do `Box`, que é o que faz a
lista longa rolar. **Nenhum dos dois aparece em teste de componente**, que mede uma cena de altura
fixa.

**Fechar é a primária.** A tela informa, não propõe trabalho; "Ver no GitHub" leva para fora do app,
e é `GHOST`. Uma primária por tela.

**Sem itens não há janela**, e não uma janela vazia: lista vazia numa tela de novidades afirma que a
versão não trouxe nada.

O `main()` ganha **duas** chamadas e nenhum estado novo — `rememberReleaseNotesController` e
`ReleaseNotesWindow` —, pela mesma restrição de tamanho do backend JVM que já criou o
`AutoUpdateController`.

### Protótipo

Seção `4d · Novidades da versão` em `prototipo-visual-opencode.html`, com o link em `nav.index`, no
mesmo commit da mudança.

## Comandos de verificação

```bat
gradlew.bat desktopTest --rerun
```

Diálogo em execução — forja o recibo, limpa a marca e sobe o app. **Precisa do app instalado
fechado**, senão o `SingleInstanceGuard` derruba o processo de desenvolvimento antes do `main()`, e o
`gradlew run` termina em segundos com `BUILD SUCCESSFUL` sem ter composto nada:

```powershell
$r = "$env:USERPROFILE\.usage-monitor\update-receipt.properties"
Set-Content -Path $r -Encoding ASCII -Value @('version=37.0.0','previousVersion=36.0.0','status=success','reason=')
# As Java Preferences escapam maiúsculas com barra: releaseNotesSeenVersion vira release/Notes/Seen/Version
Remove-ItemProperty -Path 'HKCU:\Software\JavaSoft\Prefs\com.usagemonitor' -Name 'release/Notes/Seen/Version'
.\gradlew.bat run
```

A tag `v37.0.0` existe de verdade e tem 6 commits `feat`/`fix` mais 1 `chore`, então serve de fixture
sem precisar publicar release nenhuma. Restaurar o recibo e a marca ao fim.

## Pontos de situação

Uma linha por atividade, escrita **no mesmo commit** da atividade. `Evidência` é o comando que rodou
e o resultado, não a intenção.

| # | Data | Commit | Atividade | Estado | Evidência |
|---|---|---|---|---|---|
| B01 | 2026-08-24 | `feat(update): show what changed after an automatic update` | A funcionalidade inteira, mais este plano | concluída | `gradlew.bat desktopTest --rerun`: **119 classes / 1266 testes / 0 falhas** (base do `main` era 115/1234; as quatro classes novas somam 32 casos). **Verificado em execução três vezes**, com recibo forjado `status=success version=37.0.0` e a release **real** `v37.0.0`, que tem 6 commits `feat`/`fix` e 1 `chore`: a janela abriu com os **6** itens, sem prefixo e sem hash, e o `chore(release): bump version` ficou de fora; a preferência foi gravada (`release/Notes/Seen/Version = 37.0.0` no registro, que é como as Java Preferences escapam maiúsculas); e na passada seguinte, com a marca presente, **a janela não reabriu**. **Dois defeitos de layout só apareceram em execução e foram corrigidos:** (1) o título vinha duas vezes — na barra da moldura e no cabeçalho —, e o cabeçalho passou a dizer só "Novidades"; (2) a superfície da lista esticava até o rodapé e deixava uma caixa vazia sob 6 itens, e ao encolhê-la os botões subiram para o meio da janela. A correção separa as duas coisas: o `Box` estica, a superfície dentro dele cresce até onde a lista vai, e os botões ficam ancorados no rodapé. Nenhum dos dois aparece em teste de componente, que mede uma cena de altura fixa |

## Riscos aceitos, registrados por escrito

| # | Risco | Estado |
|---|---|---|
| 1 | Os itens são texto de commit em inglês: a tela mostra frases como `stop killing every JVM on the machine`. Foi a opção escolhida contra notas curadas à mão, que criariam trabalho manual a cada release | aceito por decisão |
| 2 | Uma requisição a mais ao GitHub por atualização aplicada — uma só, na primeira abertura, e nunca repetida em caso de sucesso ou de release sem itens | aceito |
| 3 | Release publicada com o corpo editado à mão passa pelo filtro sem nenhum controle de tamanho: uma nota longa rola dentro do diálogo, mas não há teto de itens | aceito |
| 4 | A janela nunca foi exercitada depois de uma atualização **real** — a verificação usa recibo forjado sobre a release v37.0.0, que existe de verdade. O caminho real fecha junto do próximo smoke test empacotado | aberto |
