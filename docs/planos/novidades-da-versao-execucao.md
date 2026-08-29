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

> A regra desta seção foi **substituída** na B03. O gatilho original — recibo de sucesso do instalador
> — está descrito na seção "Correção do gatilho (issue #127)", mais abaixo, junto do motivo de ter
> saído. O que continua valendo é a tabela dos desfechos da **busca**, no fim desta seção.

`releaseNotesDecision(currentVersion, seenVersion, hasUpdateReceipt)`, pura, devolve um de três
valores. **O recibo não decide**: ele entra como booleano de existência, só para separar instalação
nova de marca ausente por defeito.

| Situação | Decisão | Por quê |
|---|---|---|
| Sem marca e sem recibo | `MARK_SEEN_ONLY` | Instalação nova. "Novidades" para quem não tem versão anterior não descreve mudança nenhuma |
| Sem marca, com recibo no disco | `SHOW` | A máquina já atualizou alguma vez. É o estado de quem foi atingido pela #127 — sem este ramo a correção só apareceria uma versão depois de publicada |
| Marca igual à versão em execução | `SKIP` | Igualdade textual, exata e barata |
| Versão em execução mais nova que a marca | `SHOW` | O caso normal |
| Resto | `MARK_SEEN_ONLY` | Retrocesso, e "mesma versão escrita de outro jeito" (`38.0.2` × `38.0.02`), que a igualdade textual não pega — a marca é reescrita na forma canônica e a abertura seguinte cai em `SKIP` |

A marca é a preferência `releaseNotesSeenVersion` (`ReleaseNotesPreferences.kt`) — guarda a **versão**
e não um booleano, porque um booleano teria de ser limpo por alguém a cada troca de versão, e o único
candidato seria o instalador, que não conhece as preferências do app.

`MARK_SEEN_ONLY` **não vai à rede**: pedir ao GitHub a release de uma versão que não vamos anunciar é
requisição gasta por nada.

O subtítulo tem fonte própria, `releaseNotesPreviousVersion`: o recibo quando ele descreve a
atualização que trouxe o binário em execução — caminho automático do Windows, onde o instalador leu a
versão anterior do registro —, e a marca em todo o resto. O `?:` sobre `previousVersion`, e não um
`if` sobre o recibo inteiro, porque o campo é anulável e descartar a marca ali apagaria o subtítulo
em vez de completá-lo.

Os três desfechos da **busca** continuam tratados separadamente, e são coisa diferente da decisão
acima. **Tag inexistente entra no segundo**, não no terceiro (B04): a resposta é definitiva e
retentar não a mudaria.

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

## Correção do gatilho (issue #127)

A janela **não subiu** numa Bazzite depois da atualização automática para a v38.0.1. Não foi falha de
rede nem release vazia: a `v38.0.1` tem três itens `fix` no corpo. O defeito era do gatilho, e
escondia a janela em quase todo lugar.

### O recibo perde a corrida no Linux, sempre

O recibo é lido **uma vez**, no `remember` de `rememberAutoUpdateController`. As duas plataformas o
escrevem em ordens opostas:

| Plataforma | Onde | Quando |
|---|---|---|
| Windows | `UsageMonitor.nsi`, fim da `Section` | **antes** de relançar o app — o comentário no `.nsi` já dizia "Escrito depois disto, o recibo perderia a corrida" |
| Linux | `linux-updater.sh`, `write_receipt success ""` | **depois** do ACK, que é escrito pelo app novo já em execução |

No Linux, portanto, quando o app novo compõe o `main()` o arquivo ainda descreve a atualização
anterior — ou não existe. **A ordem do script está certa e não foi tocada**: antes do ACK ainda pode
haver rollback, e é o mesmo ponto que o dispara. Escrever o sucesso antes seria afirmar o que ainda
não se sabe.

### E não existia recibo nenhum fora da atualização automática

Instalação manual — `Setup.exe` sem `/UPDATE`, `.sh`, `.deb`, `.rpm` — não escreve recibo. No macOS
não existe instalador automático, então a janela **nunca apareceu**, em versão nenhuma.

### O que mudou

O gatilho passou a ser a marca: versão em execução diferente de `releaseNotesSeenVersion`. O recibo
continua sendo lido e continua alimentando a linha "Última atualização" das Configurações
(`lastUpdateReceiptLine`) e a poda do artefato aplicado (`shouldDiscardUpdateArtifacts`) — só deixou
de ser condição da janela.

Duas escolhas travadas com o autor do repositório antes de escrever código:

| Pergunta | Escolha |
|---|---|
| Instalação nova (marca ausente, sem recibo) | **Não abre**, marca em silêncio |
| Marca ausente **com** recibo no disco | **Abre** — a máquina já atualizou alguma vez, e sem este ramo quem foi atingido pela #127 só veria a janela uma versão depois de a correção sair |

O retrocesso também marca em silêncio, e não é caso hipotético: no `health-timeout` do
`linux-updater.sh` o app novo chega a abrir a janela e a gravar a marca antes de o script desistir e
restaurar a versão anterior. Sem reescrever a marca para baixo, as novidades daquela versão ficariam
perdidas para sempre.

## Comandos de verificação

```bat
gradlew.bat allTests
```

Diálogo em execução. **Precisa do app instalado fechado**, senão o `SingleInstanceGuard` derruba o
processo de desenvolvimento antes do `main()`, e o `gradlew run` termina em segundos com
`BUILD SUCCESSFUL` sem ter composto nada. A tag da versão em `build.gradle.kts` precisa existir no
GitHub — a `v38.0.2` tem um `feat` e serve de fixture sem publicar release nenhuma:

```bash
gh release view v38.0.2 --json body -q .body
```

**Forjar recibo deixou de ser necessário**, e passou a ser enganoso: ele não é mais o gatilho.

### Windows

Preferência em `HKCU\Software\JavaSoft\Prefs\com.usagemonitor`. As Java Preferences escapam
maiúsculas com barra, então `releaseNotesSeenVersion` vira `release/Notes/Seen/Version`. Guardar
marca e recibo antes; restaurar ao fim.

```powershell
$k = 'HKCU:\Software\JavaSoft\Prefs\com.usagemonitor'
$n = 'release/Notes/Seen/Version'
$r = "$env:USERPROFILE\.usage-monitor\update-receipt.properties"
$backupMark    = (Get-ItemProperty -Path $k -Name $n -ErrorAction SilentlyContinue).$n
$backupReceipt = if (Test-Path $r) { Get-Content $r } else { $null }
```

| # | Cenário | Passos | Esperado |
|---|---|---|---|
| W1 | A prova da #127 | apagar o recibo, marca em `38.0.1`, `gradlew.bat run` | janela abre, subtítulo `Atualizado de 38.0.1` (vindo da **marca**); ao fechar, marca em `38.0.2` |
| W2 | Não reabre | rodar de novo | sem janela |
| W3 | Instalação nova | remover marca **e** recibo, `USAGE_MONITOR_UPDATE_FEED_URL` para endereço morto, rodar | sem janela e marca em `38.0.2`. A marca presente **é** a prova de que não houve busca: com o feed morto, qualquer requisição falharia e a marca não seria gravada |
| W4 | Marca ausente com recibo | remover a marca, deixar um recibo qualquer no disco, rodar | janela abre |
| W5 | Retrocesso | marca em `99.0.0`, rodar | sem janela, marca reescrita para `38.0.2` |
| W6 | O recibo ainda manda no subtítulo | recibo `version=38.0.2 previousVersion=36.0.0 status=success`, marca em `38.0.1` | subtítulo `Atualizado de 36.0.0`, e Configurações → Geral continua com a linha "Última atualização" |

Limpar `USAGE_MONITOR_UPDATE_FEED_URL` depois do W3 — a UI mostra aviso enquanto ela estiver ativa.

### Linux

Preferência em `~/.java/.userPrefs/com/usagemonitor/prefs.xml`, com a chave **literal**. **Fechar o
app antes de editar**: a JVM mantém as preferências em memória e o flush de saída sobrescreveria a
edição. **Nunca apagar o diretório do nó** para simular instalação nova — ele carrega tema, escala,
opacidade e o interruptor de atualização automática; remover só a entrada.

```bash
pkill -f 'Usage Monitor'; sleep 2
P=~/.java/.userPrefs/com/usagemonitor/prefs.xml
cp "$P" "$P.bak"
```

Repetir W1, W3 e W5 com o launcher `~/.local/bin/usage-monitor`, editando a entrada com `sed`. E
então o que fecha o risco 4:

**L4 — ciclo real.** Numa instalação `.sh` gerenciada, com a atualização automática ligada e uma
release-alvo publicada, deixar o updater rodar de verdade. No instante em que a janela aparecer, e
**antes de fechá-la**, noutro terminal:

```bash
cat ~/.usage-monitor/update-receipt.properties
tail -5 ~/.usage-monitor/diagnostics/linux-update.log
```

A evidência que vale registrar: o recibo ainda descreve a versão **anterior** (ou não existe) e o log
ainda **não** tem a linha `OK promoted` — ou seja, a janela abriu no exato estado em que o gatilho
antigo a suprimia. Segundos depois, com o ACK entregue, o recibo vira `status=success` e
Configurações → Geral passa a mostrar "Última atualização". Restaurar com `mv "$P.bak" "$P"`, com o
app fechado.

## Pontos de situação

Uma linha por atividade, escrita **no mesmo commit** da atividade. `Evidência` é o comando que rodou
e o resultado, não a intenção.

| # | Data | Commit | Atividade | Estado | Evidência |
|---|---|---|---|---|---|
| B04 | 2026-08-29 | `fix(update): treat a missing release tag as no notes` | 404 da rota de tag vira `Result.success(null)` em vez de falha. Efeito colateral da B03: como o gatilho passou a ser toda troca de versão, uma build cujo `version` já subiu mas cuja tag ainda não foi publicada perguntava por uma release inexistente — e falha, por desenho, **não** marca, então a requisição se repetiria em toda abertura por uma resposta que não vai mudar | concluída | `gradlew.bat allTests`: **154 classes / 1632 testes / 0 falhas** (base da B03: 154/1630). Os dois casos novos são de naturezas diferentes de propósito: um no repositório com fake, outro **no data source com `MockEngine` real** — os demais casos daquele arquivo sobrescrevem `fetchGitHubReleaseByTag` e não executam uma linha de HTTP (issue #94), e a tradução do 404 acontece exatamente ali. O de baixo afirma também que 503 **continua** erro: disponibilidade passando por "release sem novidades" marcaria a versão como vista sem ninguém ver nada |
| B03 | 2026-08-29 | `fix(update): open the release notes on any version change` | O gatilho passa a ser a marca, e o recibo sai da decisão (issue #127). Enum `ReleaseNotesDecision`, `releaseNotesDecision`, `releaseNotesPreviousVersion`, controlador, os três blocos de KDoc que descreviam o gatilho antigo, a seção "Correção do gatilho" acima e o §4d do protótipo | concluída | `gradlew.bat allTests`: **154 classes / 1630 testes / 0 falhas** (base da B02: 153/1612). Os seis casos que exercitavam `shouldShowReleaseNotes` foram reescritos e a classe nova `ReleaseNotesControllerTest` cobre a costura, que é onde o defeito estava — as funções puras estavam certas para o contrato que descreviam, e o que estava errado era o sinal que o controlador lia. Os casos silenciosos afirmam **zero chamadas** ao repositório, e não só janela ausente: é o contador que prova que `MARK_SEEN_ONLY` não gasta requisição. Verificação em execução: pendente, roteiro em "Comandos de verificação" |
| B02 | 2026-08-29 | `refactor(update): move version comparison into the domain` | `compareAppVersions`/`isVersionNewer` saem de `data/repository/AppUpdateRepositoryImpl.kt` para `domain/entity/AppVersionComparison.kt`, com o **sinal** exposto. Preparação da B03: o ramo de retrocesso da decisão precisa distinguir "mais antiga" de "igual", e o domain não pode importar de `data` | concluída | `gradlew.bat allTests`: **153 classes / 1612 testes / 0 falhas** (base do `main`: 152/1607). Movimento puro — os cinco chamadores só trocam o import, e o teste do sufixo `-beta` migrou de `AppUpdateRepositoryImplTest` para a classe nova, que soma 6 casos |
| B01 | 2026-08-24 | `feat(update): show what changed after an automatic update` | A funcionalidade inteira, mais este plano | concluída | `gradlew.bat desktopTest --rerun`: **119 classes / 1266 testes / 0 falhas** (base do `main` era 115/1234; as quatro classes novas somam 32 casos). **Verificado em execução três vezes**, com recibo forjado `status=success version=37.0.0` e a release **real** `v37.0.0`, que tem 6 commits `feat`/`fix` e 1 `chore`: a janela abriu com os **6** itens, sem prefixo e sem hash, e o `chore(release): bump version` ficou de fora; a preferência foi gravada (`release/Notes/Seen/Version = 37.0.0` no registro, que é como as Java Preferences escapam maiúsculas); e na passada seguinte, com a marca presente, **a janela não reabriu**. **Dois defeitos de layout só apareceram em execução e foram corrigidos:** (1) o título vinha duas vezes — na barra da moldura e no cabeçalho —, e o cabeçalho passou a dizer só "Novidades"; (2) a superfície da lista esticava até o rodapé e deixava uma caixa vazia sob 6 itens, e ao encolhê-la os botões subiram para o meio da janela. A correção separa as duas coisas: o `Box` estica, a superfície dentro dele cresce até onde a lista vai, e os botões ficam ancorados no rodapé. Nenhum dos dois aparece em teste de componente, que mede uma cena de altura fixa |

## Riscos aceitos, registrados por escrito

| # | Risco | Estado |
|---|---|---|
| 1 | Os itens são texto de commit em inglês: a tela mostra frases como `stop killing every JVM on the machine`. Foi a opção escolhida contra notas curadas à mão, que criariam trabalho manual a cada release | aceito por decisão |
| 2 | Uma requisição a mais ao GitHub por **troca de versão** — uma só, na primeira abertura, e nunca repetida em caso de sucesso ou de release sem itens. Desde a B03 isso inclui a instalação manual e quem deixou "Atualização automática" desligada. **Sem portão pelo interruptor, de propósito**: quem atualizou merece saber o que mudou, e `CheckForAppUpdateUseCase` já consulta o feed de releases independentemente do interruptor — não há relação de rede nova | aceito por decisão |
| 3 | Release publicada com o corpo editado à mão passa pelo filtro sem nenhum controle de tamanho: uma nota longa rola dentro do diálogo, mas não há teto de itens | aceito |
| 4 | A janela nunca foi exercitada depois de uma atualização **real**. Fecha com o roteiro L4 de "Comandos de verificação", que agora existe — e que era exatamente o cenário em que o defeito da #127 vivia | aberto |
| 5 | Quem instalou manualmente e **nunca** usou atualização automática não tem marca nem recibo: fica com silêncio na primeira execução da versão que traz a B03, e passa a ver a janela a partir da seguinte. Foi o preço aceito para não abrir "Novidades" a quem acabou de instalar o app | aceito por decisão |
| 6 | No Linux as preferências vivem em `~/.java/.userPrefs/`, fora de `~/.usage-monitor/` e da árvore XDG gerenciada. Se o `FileSystemPreferences` não conseguir gravar, a marca vira no-op silencioso e a janela reabre **uma vez por arranque** — antes o recibo limitava o dano. Uma vez por arranque e não em laço: o `LaunchedEffect` não se repete dentro da mesma composição | aceito |
