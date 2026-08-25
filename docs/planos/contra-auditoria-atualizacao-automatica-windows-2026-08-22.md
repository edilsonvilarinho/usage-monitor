# Contra-análise da auditoria de atualização automática (Windows)

## 1. Identificação

| Campo | Valor |
|---|---|
| Data desta análise | 2026-08-22 |
| Repositório | `C:\Users\edils\workspace\usage-monitor` |
| Branch | `feat/auto-update-windows-75` |
| HEAD analisado | `36bf529` |
| Documento confrontado | `docs/planos/auditoria-atualizacao-automatica-windows-2026-08-22.md` |
| Snapshot auditado por aquele documento | `31bd8507baf3578eae3316d1e3637390a0444a74` |
| Modelo desta contra-análise | `Claude Opus 5 (1M context)` — id `claude-opus-5[1m]` |
| Nível de esforço desta contra-análise | `max` |
| Modelo da auditoria confrontada | `gpt-5.6-sol`, esforço `ultra` (declarado por ela) |

**O código não mudou desde o snapshot auditado.** `git diff --stat 31bd850..HEAD` devolve um único
arquivo — `docs/planos/auditoria-atualizacao-automatica-windows-2026-08-22.md`, adicionado pelo
commit `36bf529 doc: auditado`. Logo os achados da auditoria descrevem o código de hoje, e as duas
análises são comparáveis linha a linha.

**A auditoria foi revisada no working tree durante esta análise.** Comecei pela versão commitada em
`36bf529` e, ao conferir o estado do repositório no fim do trabalho, `git status --short` acusou
`M docs/planos/auditoria-atualizacao-automatica-windows-2026-08-22.md` — 30 inserções e 9 remoções
não commitadas, feitas por outro agente enquanto esta análise corria. A revisão acrescenta dois
achados (**P2-15** sobre redirects e **P2-16** sobre a origem no app-image), renumera o antigo P2-15
para **P2-17**, reescreve as linhas A13 e A15 da matriz de aderência, ajusta um item da §9 e expande
a §11 de 11 para 13 critérios. **Esta contra-análise cobre as duas versões** e usa a numeração da
revisão não commitada. Onde um achado só existe em um dos dois estados, digo qual.

Este documento avalia **a auditoria**, não a branch. Nenhum arquivo de código, teste, workflow ou
plano foi alterado.

## 2. Veredito sobre a auditoria

> **A auditoria é tecnicamente sólida e o veredito "REPROVADA para ativação" se sustenta — mas por
> menos motivos, com severidades diferentes das que ela atribuiu, e com seis achados que ela não
> viu.**

Confrontei os 34 achados da revisão (32 na versão commitada) com os arquivos citados. Resultado:

| Julgamento | Quantidade | Itens |
|---|---|---|
| Confirmado sem ressalva | 24 | P0-01, P1-02, P1-03, P1-04, P1-07, P1-08, P1-10, P2-01 a P2-10, P2-12, P2-16, P2-17, P3-01, P3-02, P3-03, P3-05 |
| Mecanismo correto, classificação ou impacto errado | 9 | P0-02, P1-01, P1-05, P1-06, P1-09, P2-11, P2-13, P2-14, P2-15 |
| Erro factual | 1 | P3-04 |
| Não visto pela auditoria | 6 | N1 a N6, §7 |

O que a auditoria acerta e é o núcleo do veredito: o ciclo `preparar → encerrar → instalar →
relançar → confirmar saúde → remover rollback → refletir resultado` **não fecha**. O instalador
declara sucesso e destrói o backup antes de qualquer prova de que a versão nova sobe (P0-01), falhas
controladas deixam o usuário sem aplicação aberta (P1-02), falha de extração não deixa rastro
(P1-03) e o resultado do `schedule()` é descartado (P1-04). Esses quatro, juntos, bastam para
reprovar a ativação. Nada nesta contra-análise os enfraquece.

O que a auditoria erra é **calibragem**, e o erro tem uma direção só: ela infla. Dois P0 onde há um;
dez P1 onde há seis; e nenhuma distinção entre o que é redesenho transacional e o que é correção de
três linhas. Um documento que classifica "o instalador apaga o rollback sem prova de saúde" e "o
teste do portão aceita `0.0.0`" no mesmo patamar de bloqueio não ajuda a decidir o que corrigir
primeiro.

## 3. Método

Li os arquivos citados por cada achado e conferi linha por linha. Onde a auditoria afirmou ter
reproduzido um comportamento, reproduzi por conta própria em vez de aceitar a afirmação. Comandos e
saídas literais estão nas seções em que sustentam a conclusão.

Fontes conferidas: `src/installer/UsageMonitor.nsi`,
`src/installer/test/Invoke-UpdateScenarios.ps1`, `.github/workflows/ci.yml`,
`.github/workflows/release-linux.yml`, todo o pacote `src/desktopMain/.../update/`,
`AutoStartManager.kt`, `Main.kt`, `AppUpdateRepositoryImpl.kt`, `RemoteApiDataSource.kt`,
`DashboardViewModel.kt`, `SettingsDialogContent.kt`, `UpdateReceiptReader.kt`,
`AutoUpdateWiringTest.kt`, `docs/planos/atualizacao-automatica-windows-execucao.md`, `CLAUDE.md` e
`AGENTS.md`.

Nenhum teste foi executado nesta análise, e nenhum resultado de execução é atribuído a ela além dos
três experimentos descritos em §5.4 e §5.1.

## 4. Achados confirmados sem ressalva

Não reescrevo o que já está certo. A tabela registra que foram conferidos e onde.

| Achado | Verificação |
|---|---|
| P0-01 | `UsageMonitor.nsi:371-376` grava `status=success` e faz `RMDir /r "$UpdateBackup"`; o `Exec` só existe em `.onInstSuccess`, `:186-192`, e o retorno não é lido. Confirmado. |
| P1-02 | `updateFailIntact` (`:328-334`) termina em `Abort`; `.onInstFailed` (`:199-206`) só restaura o backup. Não há `Exec` em nenhum caminho de falha. Confirmado. |
| P1-03 | `File /r` sem `/nonfatal` em `:281`; `.onInstFailed` não chama `WriteUpdateReceipt` nem limpa o staging parcial. Confirmado. |
| P1-04 | `DashboardViewModel.kt:889` chama `installer.schedule(preparation)` e descarta o `Result`. `AppUpdateFailureReason.SCHEDULE` só aparece em `DashboardScreenWarnings.kt:376` e em `AppUpdateBannerTest.kt:122` — nenhum caminho de produção o emite. Confirmado. |
| P1-07 | `Rename "$UpdateBackup" "$INSTDIR"` em `:324` e `:203` sem `ClearErrors`/`IfErrors`; `DetailPrint` em `:332` afirma instalação intacta sem prova. Nenhum cenário S1–S6 induz falha **entre** os dois renames. Confirmado. |
| P1-08 | `readUpdateReceipt()` é chamado uma vez em `AutoUpdateController.kt:97`; `Main.kt:1668` o entrega só ao diálogo de Configurações. O backoff nasce apenas em `DashboardViewModel.kt:833`, por falha de download. Confirmado. |
| P1-10 | `AUTO_UPDATE_SHIPPED = false`, mínimo em `999.0.0`, A19 e A20 marcadas pendentes na tabela do plano (`:470-471`). Confirmado. |
| P2-01 | `Invoke-UpdateScenarios.ps1:39` aponta para o recibo real e o remove em `:125`, `:179`, `:197`, `:214` e `:130`. Sem backup. Confirmado. |
| P2-02 | `$WorkDirectory` é parâmetro livre (`:24`) e sofre `Remove-Item -Recurse -Force` em `:146` sem sentinela. Confirmado. |
| P2-03 | `updateBackoff` é campo do ViewModel; nada o persiste. Confirmado. |
| P2-04 | O SHA é conferido em `UpdateArtifactDownloader.kt:145-151`; `schedule()` verifica só `file.isFile` (`WindowsAppUpdateInstaller.kt:124`). `AppUpdatePreparation` não carrega o digest. Confirmado. |
| P2-05 | Com `sizeBytes` nulo o descarte de parcial em `:56-58` não roda, o `Range` é enviado a partir do tamanho cheio e a resposta 416 cai fora dos casos 200/206 de `:114-119`. O `.part` **não** é apagado nesse caminho. Confirmado. |
| P2-06 | `AutoUpdateController.support` (`:45-46`) recalcula; `Main.kt:1667` o lê em composição; a cadeia chega a `AutoStartManager.runCommand` (`:314-327`), que faz `process.waitFor()` sem timeout. Confirmado. |
| P2-07 | `DashboardViewModel.kt:866` — `if (enabled) return@collect`. Ligar não reavalia o update anunciado. Confirmado. |
| P2-08 | `trackedVersion()` é lido duas vezes na mesma condição, em `DashboardViewModel.kt:744`; o job de download escreve `preparedUpdate`/`updateBackoff` fora do `updateMutex`, e `workerDispatcher` é `Dispatchers.Default` (`DashboardViewModelConfig.kt:11`) — multithread de verdade. Confirmado como risco estático. |
| P2-09 | O filtro cobre só `src/installer/*` e `src/desktopMain/.../update/*`; não cobre `build.gradle.kts`, `Main.kt`, contratos comuns nem o próprio `ci.yml`. Nenhum dos dois jobs tem `timeout-minutes`. Confirmado. |
| P2-10 | Os seis cenários não exercitam PID vivo, falha entre os dois renames, falha de extração, saúde pós-relaunch nem consumo do recibo pelo Kotlin. Confirmado. |
| P2-12 | SHA e binário vêm do mesmo canal; o Setup continua sem assinatura. Confirmado — e ver **N1**, que é a parte que faltou. |
| P2-16 (só na revisão) | O plano registra em `:424-429` que não foi medido qual fonte o runtime empacotado preenche; a A13 foi verificada por `gradlew run`, não pelo app-image. Confirmado — e é a lacuna que a A20 fecha, porque `support()` devolvendo `UNSUPPORTED_INSTALL_ORIGIN` numa instalação NSIS legítima desligaria o recurso em silêncio. |
| P2-17 (P2-15 na versão commitada) | O plano exclui Linux (`:55-61`); a issue #75 o exige; não há issue substituta. Confirmado. |
| P3-01 | `04fadb7 fix(ui): publish the switch state in AppSwitch semantics` traz código e teste; a linha A12a da tabela entrou em `e461f7c`, o commit seguinte. Viola `CLAUDE.md:368`. Confirmado. |
| P3-02 | `cefc8a5` registra 22 asserções; `Invoke-UpdateScenarios.ps1` só entra em `408d5d0`. Confirmado. |
| P3-03 | `gh pr list` vazio; todo o trabalho numa branch. Confirmado. |
| P3-05 | A02 e A15 registram medições sem versionar sondas. Confirmado. |

Duas correções de detalhe, sem consequência para o julgamento:

- A auditoria diz "os 23 commits". Conferido: `git log --format='%an' 8aa6e73..31bd850 | sort | uniq -c`
  devolve exatamente 23 de `claude`. Correto para o snapshot dela. No HEAD atual há 24, porque
  `36bf529` é do próprio usuário.
- A §2 diz que o risco está contido "exclusivamente" pelos três guardas. Na prática
  `AUTO_UPDATE_SHIPPED = false` **sozinho** basta: com ele, `rememberAutoUpdateController` deixa
  `installer` nulo, `canDownloadAutomatically()` devolve `false` na primeira linha e
  `scheduleUpdateOnExit()` retorna antes de tudo. Os outros dois são redundância — o que é uma
  virtude do desenho, não um co-requisito.

## 5. Achados reclassificados

### 5.1 P0-02 — "o smoke test A20 é inexequível": conclusão errada

**O que a auditoria afirma.** O plano exige feed JSON local por HTTP e `Setup.exe` local
(`atualizacao-automatica-windows-execucao.md:297-300`); `UpdateArtifactDownloader.kt:199-204` só
aceita HTTPS em `github.com`. Portanto A20 não pode ser executada. P0, confiança 100%.

**O que a evidência mostra.** As duas metades da frase são verdadeiras e a conclusão não segue.
`RemoteApiDataSource.fetchLatestGitHubRelease` (`:198-216`) usa o `feedUrlOverride` **sem validação
nenhuma**:

```kotlin
val url = feedUrlOverride?.takeIf { it.isNotBlank() }
    ?: "https://api.github.com/repos/$owner/$repository/releases/latest"
```

Qualquer esquema, qualquer host, HTTP em claro incluído. A restrição de `TRUSTED_DOWNLOAD_HOSTS`
alcança **apenas a URL do asset**. O feed local, que é a parte do A20 que dá trabalho montar,
funciona hoje.

A20 é executável assim: o JSON local declara `tag_name` maior que a versão instalada e um
`browser_download_url` apontando para um asset **real** do GitHub, com o `size` e o `digest`
verdadeiros daquele asset. Publicar um pré-release com um `Setup.exe` de teste resolve o resto. O
único passo que a redação atual do plano impede é servir o binário do `localhost` — e ele não é
necessário para exercitar download, verificação, agendamento, troca e relançamento.

**Severidade proposta:** P3 (erro de redação do plano). O texto do A20 precisa de uma frase: "o
`browser_download_url` do feed local aponta para um asset real do GitHub; só o feed é local".

**Custo da correção:** uma linha no plano. Zero em código.

### 5.2 P1-09 — "o filtro do CI pode falhar aberto": subestimado no mecanismo, superestimado no impacto

**O que a auditoria afirma.** `$ErrorActionPreference = 'Stop'` não cobre falha de comando nativo;
uma falha do `fetch`/`diff` "pode" deixar `$changed` vazio e produzir `relevant=false`. P1.

**Metade um: o mecanismo é pior do que "pode".** Reproduzi as duas pontas.

A premissa do PowerShell confere. Com `pwsh 7.6.4`:

```
$ErrorActionPreference = "Stop"
cmd /c "exit 128"
Write-Host "CONTINUOU-APOS-FALHA-NATIVA"
```

Saída: `CONTINUOU-APOS-FALHA-NATIVA`, exit 0. `$PSNativeCommandUseErrorActionPreference` é `False`.
Falha de comando nativo não vira erro terminante.

A parte que a auditoria não investigou é **por que** o `git diff` sai 128. Reproduzi o estado de um
checkout raso:

```
git clone --depth=1 --branch feat/auto-update-windows-75 file://.../usage-monitor repo
cd repo
git fetch --no-tags --depth=1 origin main
git diff --name-only "origin/main...HEAD"
→ fatal: ambiguous argument 'origin/main...HEAD': unknown revision or path not in the working tree.
→ EXITCODE=128
```

Causa: `git fetch origin main` sem refspec escreve `FETCH_HEAD`, **não** cria
`refs/remotes/origin/main`. E mesmo criando o ref à mão o diff continua falhando, por outro motivo:

```
git fetch --no-tags --depth=1 origin "main:refs/remotes/origin/main"
git diff --name-only "origin/main...HEAD"
→ fatal: origin/main...HEAD: no merge base

git diff --name-only "origin/main" "HEAD"
→ .github/workflows/ci.yml
→ .github/workflows/release-linux.yml
→ CLAUDE.md
```

Os dois lados são rasos: o diff de **três pontos** precisa de base de merge e não tem; o de **dois
pontos** funciona. Ou seja, o passo não falha "às vezes" — ele falha **sempre** num checkout raso, e
`actions/checkout@v4` é raso por default (`fetch-depth: 1`). Consequência: `$changed` é `$null`,
`$relevant` fica `false`, o passo sai 0 e o job `installer-scenarios` **nunca roda em PR nenhuma**.

Hipótese não confirmada, declarada como tal: que o runner do GitHub reproduza exatamente este estado
de refs. A auditoria registra que nenhuma execução de Actions existe nesta branch, então a prova
definitiva é a primeira PR. Os dois modos de falha reproduzidos localmente cobrem as duas
configurações plausíveis de ref, o que torna a hipótese forte.

**Metade dois: o impacto é menor do que a auditoria diz.** Ela não olhou o outro workflow.
`release-linux.yml` roda `Invoke-UpdateScenarios.ps1` no job `verify` **sem filtro nenhum** (`:50-55`),
e `publish-release` tem `needs: verify` (`:318-323`). Além disso `ci.yml` só dispara em `push` para
`main` (`:4-8`), onde o passo devolve `relevant=true` incondicional antes de tocar no `git`.

Então: uma regressão no `/UPDATE` **não** passa para uma release por causa deste defeito. O que ele
custa é feedback — o cenário deixa de rodar na PR e só roda depois do merge, no `push` para `main`, e
de novo no gate de release.

**Severidade proposta:** P2 — defeito determinístico de feedback de CI, não de segurança de release.

**Custo da correção:** duas linhas no `ci.yml`:

```powershell
git fetch --no-tags --depth=1 origin "${{ github.event.pull_request.base.ref }}:refs/remotes/origin/${{ github.event.pull_request.base.ref }}"
if ($LASTEXITCODE -ne 0) { throw "fetch falhou" }
$changed = git diff --name-only "origin/${{ github.event.pull_request.base.ref }}" HEAD
if ($LASTEXITCODE -ne 0) { throw "diff falhou" }
```

Dois pontos em vez de três, ref explícito no refspec, e `$LASTEXITCODE` conferido — o que faz o passo
falhar **fechado**. Junto disso, o recorte de path da P2-09.

### 5.3 P1-01 — "o PID é recebido, mas nunca é aguardado": o defeito é do documento, não do desenho

**O que a auditoria afirma.** O launcher envia `/PID=<pid>` (`WindowsAppUpdateInstaller.kt:138`), o
NSIS só o grava no recibo (`:65`), e a espera real são 30 tentativas com `Sleep 500` — ~15 s contra
os ~30 s que o plano exige. "Shutdowns mais lentos podem resultar em `reason=locked`". P1, defeito de
aderência.

**O que confere.** Tudo, na letra. E mais do que a auditoria disse: o plano **não registra este
desvio em lugar nenhum**. A linha A08 da tabela de Pontos de situação ainda afirma "O PID vai junto
para o instalador **esperar** o processo sair em vez de matá-lo", e a linha A16 lista dois defeitos
encontrados sem mencionar que o passo 1 do fluxo desenhado — "esperar `$UpdatePid` sair (laço com
teto de ~30 s)" — nunca foi implementado. A seção "Desvios do plano" não o cobre. Três documentos
afirmam uma garantia que o código não tem.

**O que a auditoria mede errado.** Ela compara 15 s de `Sleep` contra 30 s de contrato e conclui
risco de `locked`. Mas o `Rename` só acontece **depois** de `File /r` extrair ~120 MB para
`$INSTDIR.new` (`:281`), e essa extração corre em paralelo com o encerramento do app, que começou
antes do instalador subir. O orçamento efetivo é "tempo de extração + ~15 s", não 15 s.

E o desenho é defensável na sua própria lógica, registrada em `:291-295`: no Windows não se renomeia
diretório que contém imagem de executável em uso, então um `Rename` que funciona **prova** que o
processo saiu. Um PID pode terminar com um processo filho ainda segurando handle; o `Rename` não tem
esse buraco. Trocar a sonda por espera de PID seria trocar uma garantia forte por uma fraca.

**Severidade proposta:** P2 para o orçamento de 15 s (número escolhido sem medida registrada) e P3
para a divergência documental, que é onde está o defeito real — só que em três arquivos.

**Custo da correção:** subir o teto do laço, se houver medida que justifique; e corrigir o plano em
três pontos (fluxo A16, linha A08 da tabela e seção de desvios). Nada em desenho.

### 5.4 P1-05 — "a versão da release não está vinculada à versão do asset": sem caminho vivo

**O que a auditoria afirma.** `artifactKindOf` (`AppUpdateRepositoryImpl.kt:87`) classifica como NSIS
qualquer `.exe` com `setup` no nome; o gate compara `update.version` (a tag); `selectArtifact` pega o
`firstOrNull`. Uma release `v40.0.0` contendo primeiro um `UsageMonitor-Setup-37.0.0.exe` passaria o
gate e mandaria `/UPDATE` para um instalador legado. P1.

**O que confere.** O mecanismo, inteiro. Nada compara a versão da tag com a do nome do asset, e nada
recusa múltiplos candidatos NSIS.

**O que falta na análise.** A pergunta que a auditoria não fez é como uma release chega a ter dois
`*setup*.exe` de versões diferentes. `release-linux.yml:401-407` publica com
`files: dist-build/*` e `fail_on_unmatched_files: true`, e `dist-build` recebe exatamente o que os
jobs de build produziram — um Setup por release, nomeado pela tag. Republicar a mesma tag sobrescreve
arquivos de mesmo nome. Para o cenário existir é preciso um upload manual de asset de outra versão
numa release já publicada.

Isso não anula o achado: é justamente o tipo de barreira que existe para o dia em que alguém errar à
mão. Mas P1 — "pode provocar perda do rollback, falso sucesso, app fechado ou gate ineficaz",
conforme a régua da própria auditoria — pressupõe um caminho alcançável pelo fluxo normal, e não há.

**Severidade proposta:** P2, defesa em profundidade.

**Custo da correção:** duas guardas em `selectArtifact`: recusar quando houver mais de um candidato
NSIS compatível, e exigir que `assetName` contenha `update.version`. Cinco linhas e um teste.

### 5.5 P1-06 — o portão A19 aceita qualquer mínimo: a própria auditoria diz que não há exposição

O mecanismo está certo: `AutoUpdateWiringTest.kt:26-30` só verifica `!= "999.0.0"`, e `0.0.0`
satisfaz o teste enquanto seria destrutivo em produção. Nada a contestar.

Mas a auditoria escreve, no mesmo achado, "sem exposição atual porque o recurso permanece desligado".
Um achado sem caminho de exposição, num teste, com o recurso desligado por dois guardas
independentes, não é P1 pela régua que ela mesma definiu.

**Severidade proposta:** P2 — lacuna de validação a fechar **antes** da A19, e a A19 é justamente a
atividade que o teste guarda.

**Custo da correção:** trocar a asserção por um piso real, do tipo
`assertTrue(isVersionNewer(MIN_UPDATABLE_TARGET_VERSION, "37.0.0"))` — o mínimo tem de ser posterior
à última release sem `/UPDATE`. Uma linha.

### 5.6 P2-11 — S6 aprova comportamento novo como "não regressão": certo, e mais forte do que ela diz

Conferi a base: `git show 8aa6e73:src/installer/UsageMonitor.nsi` traz o `MessageBox` do `.onInit`
**sem** `/SD`. Sob `/S`, pela medição da própria A02, ele exibe e bloqueia para sempre.

Então a afirmação de S6 — "comportamento de hoje inalterado" — não é imprecisa, é o oposto do que
aconteceu: o comportamento anterior era **travar**, e o novo é **prosseguir**. Não havia
comportamento a preservar.

A auditoria para aí. A consequência prática que falta: com `/SD IDNO`, uma execução `/S` sobre
instalação existente responde "não" ao "deseja remover a versão anterior?", pula o `RMDir /r` e cai
direto em `SetOutPath "$INSTDIR"` + `File /r`. Isto é **sobreposição**, não substituição: arquivos
que só existiam na versão anterior permanecem. S6 assere `marker == 'v1'` e passa, porque
`version.txt` é sobrescrito — mas não observa o resíduo. É isso que a asserção deveria dizer.

O caminho não é de produção (a instalação manual do release é interativa), o que mantém a severidade
em P2. A mudança em si é correta e necessária; o que está errado é o rótulo do cenário.

### 5.7 P2-13 — URL do feed exposta na UI: é aviso deliberado, não vazamento

`SettingsDialogContent.kt:904-917` de fato imprime o valor integral. Mas o bloco é um aviso com
`AppTone.WARNING`, e o texto diz "Só para teste" / "Testing only". O valor vem de uma variável de
ambiente que o próprio operador definiu na máquina dele, para um recurso que a UI declara como de
teste.

A auditoria reconhece que "não foi encontrado segredo real versionado". O risco descrito — alguém
colocar token na query string do feed de teste e depois publicar uma captura de tela — é
hipotético e periférico.

**Severidade proposta:** P3.

### 5.8 P2-14 — parciais acumulam: mais estreito do que a auditoria afirma, e há um caso pior que ela não viu

A auditoria diz que downloads incompletos de várias versões podem acumular. O caminho existe, mas é
menor do que soa: parcial reprovado no SHA **é apagado** em `UpdateArtifactDownloader.kt:67`, com
comentário explicando por quê. Só sobra `.part` de download interrompido por rede cuja versão foi
depois superada — e cada um deles é fração de 120 MB, não 120 MB.

O caso que realmente ocupa disco é outro, e a auditoria não o registra: ver **N2**.

### 5.9 P2-15 (só na revisão) — redirects não revalidados: correto, mas é decisão documentada, não lacuna

**O que a revisão afirma.** `validate()` roda antes do `httpClient.get()`; o cliente de `Main.kt`
não desabilita redirects; Ktor 3.0.3 segue redirect e aceita troca de autoridade HTTPS. Logo o corpo
final não tem garantia de origem GitHub. Classificado como "lacuna de validação de origem do
artefato após redirect", confiança 100%.

**O que confere.** O mecanismo, inteiro. O `HttpClient(OkHttp)` de `Main.kt:245-261` instala
`ContentNegotiation`, `HttpTimeout` e `Logging`, e não toca em `followRedirects`; não há ocorrência
de `followRedirects` nem de `HttpRedirect` em `src/` (`grep -rn` vazio). O plugin de redirect do Ktor
é instalado por default e segue a cadeia.

**O que a revisão não registra.** Isso não é uma lacuna descoberta: é uma decisão declarada no
próprio arquivo, em `UpdateArtifactDownloader.kt:215-222`, no KDoc da constante:

> "Só a URL **inicial** passa por aqui: o GitHub redireciona o download para
> `objects.githubusercontent.com`, e o Ktor segue o redirecionamento sozinho. Acrescentar o host de
> destino a esta lista faria a allowlist parecer cobrir o que ela não cobre. Quem barra artefato
> trocado é o SHA-256, que vem da API por TLS — a allowlist só garante que a cadeia começa no
> GitHub."

A auditoria descreve o comportamento exatamente como o código o descreve, sem citar que ele foi
escolhido, sem discutir o controle compensatório nomeado ali e sem dizer por que ele seria
insuficiente. Um achado que repete a documentação do alvo não acrescenta informação.

**E o controle compensatório se sustenta.** O digest chega por `api.github.com` sobre TLS, e
`verifies()` (`:145-151`) recusa o arquivo que não bate. Um redirect para host hostil não consegue
substituir o artefato: ele teria de entregar bytes com o SHA-256 que a API do GitHub publicou. O
único caminho que quebra isso é controlar **o feed** — e aí o problema não é o redirect, é a origem
do digest. Isto é, a P2-15 é **subsumida** pela questão que a P2-12 levanta e que o **N1** desta
análise fecha.

**Severidade proposta:** P3, risco residual documentado. Fechá-lo em separado — pinar o host final —
tem o efeito colateral que o comentário prevê: passa a parecer que a allowlist cobre a cadeia, e ela
não cobre, porque o host de destino do GitHub não é contratual.

## 6. Erro factual

### P3-04 — a auditoria cita o arquivo de instruções errado

**O que ela afirma.** Os 23 commits usam autor `claude <claude@anthropic.com>`, enquanto
`AGENTS.md:207-219` exige a identidade temporária `codex <codex@openai.com>`; `CLAUDE.md:366`
registra "outra convenção de trailer".

**O que confere.** `AGENTS.md:206-219` de fato manda configurar `codex <codex@openai.com>` antes de
commitar. Mas `AGENTS.md` é o arquivo de instruções do agente **Codex** — o mesmo agente que assina
a auditoria. Este desenvolvimento foi executado pelo Claude Code, governado por `CLAUDE.md`. Aplicar
a regra do arquivo do Codex a uma execução do Claude é ler o arquivo errado; sob essa lógica, a
própria auditoria estaria violando `CLAUDE.md`.

**A divergência real, que ela encostou mas não formulou.** `CLAUDE.md:366` exige o trailer
`Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>`. Conferido nos 23 commits:

```
git log --format='%B' 8aa6e73..HEAD | grep -i "Co-Authored-By" | sort -u
→ Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
```

Há divergência com a convenção que **de fato governa** a execução. Vale registrar que a divergência é
no sentido honesto: o trailer nomeia o modelo realmente usado, e é a linha do `CLAUDE.md` que está
presa a um modelo antigo. A dívida é do arquivo de convenção, não do commit.

**Severidade:** P3, como a auditoria classificou. Só o alvo está trocado.

## 7. Achados omitidos pela auditoria

### N1 — a allowlist de download prende o host, não o repositório

`UpdateArtifactDownloader.kt:202-223` valida esquema e host. `TRUSTED_DOWNLOAD_HOSTS` é
`{"github.com", "www.github.com"}`, e nada confere o **caminho**. Portanto
`https://github.com/<qualquer-dono>/<qualquer-repo>/releases/download/<qualquer-coisa>/Setup.exe`
passa a validação.

Somado ao override de feed sem validação nenhuma (§5.1), a superfície é: quem controla
`USAGE_MONITOR_UPDATE_FEED_URL` faz o app baixar e **executar em silêncio** um `Setup.exe` arbitrário
hospedado no github.com, com o SHA-256 conferindo — porque o digest vem do mesmo feed que escolheu o
binário.

Não é explorável por rede na configuração default: o feed é `api.github.com` por HTTPS. Quem
consegue definir variável de ambiente na máquina alheia já tem outros caminhos. Mas a P2-12 descreve
o risco como "SHA e binário no mesmo canal" sem notar que o canal, hoje, é qualquer repositório do
GitHub — o que é bem mais largo do que "a conta de release do projeto".

**Distinção da P2-15 da revisão.** Não é o mesmo achado. A P2-15 é sobre o **fim** da cadeia — o
host de destino depois do redirect —, e ali o SHA-256 vindo por TLS da API é controle suficiente
(§5.8). O N1 é sobre o **começo** dela: quem escolhe a URL e quem publica o digest. Quando os dois
saem do mesmo feed não validado, não sobra controle compensatório nenhum.

**Correção mínima:** exigir prefixo de caminho
`/edilsonvilarinho/usage-monitor/releases/download/` em `validate()`, e exigir HTTPS no
`feedUrlOverride`. Duas condições e dois testes.

### N2 — o instalador baixado nunca é apagado depois de aplicado

`WindowsAppUpdateInstaller.kt:99` chama `downloader.prune(artifact.assetName)`, e `prune` (`:82-87`)
apaga tudo **exceto** o artefato recém-baixado. Depois que a atualização é aplicada, a versão nova
sobe, `getLatestAvailableUpdate` compara a tag com a versão em execução, devolve `null`, e nada volta
a chamar `prune`.

Resultado: ~120 MB permanentes em `~/.usage-monitor/updates/`, para sempre, por instalação. É maior
que o problema descrito em P2-14 e tem o mesmo dono.

**Correção mínima:** apagar o artefato quando o recibo do arranque disser `status=success` e
`version` bater — o app já lê o recibo em `AutoUpdateController.kt:97`.

### N3 — o recibo não tem carimbo de tempo e nunca é consumido

`WriteUpdateReceipt` (`UsageMonitor.nsi:211-223`) grava `version`, `previousVersion`, `status`,
`reason` e `pid`. Não grava data. `readUpdateReceipt` (`UpdateReceiptReader.kt:22-51`) lê e não
apaga, não renomeia, não marca.

Isso não é só a P1-08 ("o recibo não governa backoff"): é o que torna a P1-08 **impossível de
corrigir só na fiação**. Sem data e sem consumo, um recibo `failed` de três meses atrás é
indistinguível do da última tentativa; ligar o backoff nele bloquearia aquela versão para sempre.

**Correção mínima:** acrescentar `timestamp` ao recibo (`${__TIMESTAMP__}` não serve — é de
compilação; o valor tem de vir do `System::Call` de hora ou de um `FileWrite` do próprio app), e
apagar ou renomear o arquivo depois de lido. Isso muda o formato, o que precisa entrar antes da A19.

### N4 — o orçamento de espera é maior do que a auditoria calculou

Já descrito em §5.3. Registrado aqui como achado próprio porque muda a conclusão de P1-01: o risco de
`reason=locked` por encerramento lento é menor que o descrito, e a extração de ~120 MB é a folga que
a análise ignorou.

### N5 — pico de disco de ~360 MB, sem verificação prévia

No caminho `/UPDATE` coexistem: `$INSTDIR` com a instalação atual (~120 MB), `$INSTDIR.new` com a
árvore nova (~120 MB) e o `Setup.exe` baixado em `~/.usage-monitor/updates` (~120 MB). Nada consulta
espaço livre antes de começar.

Disco cheio no meio do `File /r` cai exatamente na P1-03: `Abort` sem recibo, sem limpeza do staging
parcial, sem relançamento. Os dois achados se somam, e a auditoria menciona "falta de espaço" como
gatilho da P1-03 sem registrar que o próprio fluxo triplica a necessidade.

**Correção mínima:** `${DriveSpace}` antes do `RMDir /r "$UpdateStaging"`, com
`reason=insufficient-space` no recibo. É o caminho mais barato de todos, porque falha **antes** de
qualquer operação.

### N6 — a contagem da suíte diverge entre plano e auditoria

O plano registra, na linha A13, "**114 classes / 1217 testes / 0 falhas**". A auditoria registra
"1.221 testes em 114 classes". Mesmo número de classes, quatro testes de diferença.

`git diff --stat be6e2cd..HEAD -- '*Test.kt'` é **vazio**: nenhum arquivo de teste mudou entre a
verificação da A13 e o HEAD. Um dos dois números está errado, e a tabela de Pontos de situação é
justamente o registro que serve para auditoria.

**Verificação:** `gradlew.bat desktopTest --rerun` e conferir contra as duas contagens.

## 8. Calibragem do veredito

A auditoria conclui que "o fluxo atual não fecha de forma transacional" e reprova. Concordo com a
conclusão. Discordo de como ela distribui o peso, porque isso decide o que se corrige primeiro.

**Um único achado é redesenho: P0-01.** E mesmo ele tem uma correção mais barata do que a §11 da
auditoria sugere. Ela pede "sucesso somente após confirmação observável de inicialização/saúde,
mantendo rollback até esse ponto", o que dentro do instalador exigiria bloquear à espera do app —
isto é, o `ExecWait` que o plano rejeita explicitamente por causa do congelamento na tela final.

A alternativa é inverter quem confirma: **o instalador deixa `$INSTDIR.old` no disco; quem o apaga é
o app novo, no primeiro arranque bem-sucedido.** O app já lê o recibo no arranque
(`readUpdateReceipt` chamado por `rememberAutoUpdateController`, `AutoUpdateController.kt:97`); basta
que, com `status=success` e `version` batendo, ele apague o backup — e, de quebra, o `Setup.exe` do
N2. O instalador não precisa esperar ninguém, e o backup deixa de ser destruído sem prova. Continua
faltando o rollback **automático** quando o app não sobe, mas o material para fazê-lo à mão passa a
existir, que é o que hoje não existe.

**Três achados são correção de poucas linhas no `.nsi` e no ViewModel**, e a auditoria os classifica
no mesmo P1 do parágrafo acima:

| Achado | Correção |
|---|---|
| P1-02 (falha não relança) | `Exec '"$INSTDIR\Usage Monitor.exe"'` em `.onInstFailed`, guardado por `$UpdateMode == 1` e `${FileExists} "$INSTDIR\*.*"`. O bloco já existe em `:199-206`. |
| P1-03 (falha de extração sem recibo) | `StrCpy $R6 "extract-failed"` + `Call WriteUpdateReceipt` + `RMDir /r "$UpdateStaging"` no mesmo `.onInstFailed`. |
| P1-04 (`schedule()` descartado) | `installer.schedule(preparation)` em `DashboardViewModel.kt:889` já devolve `Result`, e o valor de enum `SCHEDULE` e o texto da faixa **já existem sem produtor**. Um `.onFailure` que publique `Failed` fecha o caminho de `restartAndUpdateNow()`. **Não fecha o de saída**: ali a janela está sumindo, e um `StateFlow` que ninguém vai renderizar não é registro. Esse caso precisa de persistência — o mesmo arquivo do N3. |

**O restante é dívida a fechar antes da A19, não bloqueio de mérito.** P1-05, P1-06, P1-07, P2-04,
P2-06, P2-07, P2-08 e os seis novos são correções localizadas. Quais delas têm teste dentro da suíte
atual não foi levantado item a item nesta análise, e não afirmo um número.

Consequência prática: a distância entre "REPROVADA" e "aprovável" é menor do que o documento faz
parecer. Não é uma reescrita do mecanismo; é P0-01 com a inversão descrita acima, mais três
correções pequenas, mais o formato do recibo (N3), mais o filtro do CI (§5.2). O que **não** encolhe
é a lista de evidência faltante: nada disso vale sem a A20 executada, e a A20 está executável hoje —
o que é a única correção de rumo que esta contra-análise entrega em relação ao documento original.

## 9. Lista de reauditoria, corrigida

Os critérios da §11 da auditoria — onze na versão commitada, treze na revisão — com o que muda:

1. **Sucesso só após confirmação de saúde** — mantido. Forma sugerida: o app novo confirma, não o
   instalador (§8).
2. **Restauração comprovada quando o segundo rename falha** — mantido, sem alteração.
3. **Recibo em todas as falhas, extração inclusive** — mantido; acrescentar `insufficient-space`
   (N5).
4. **Relançamento da versão anterior após falha controlada** — mantido.
5. **Falha de `schedule()` em estado observável** — mantido; o enum e o texto já existem sem
   produtor. Registrar que no caminho de saída "observável" tem de significar **persistido**, não um
   `StateFlow` emitido com a janela fechando.
6. **Vínculo entre versão alvo, asset e mínimo** — mantido, com severidade P2 (§5.4).
7. **Backoff pós-installer governado pelo recibo** — mantido, **e depende de mudar o formato do
   recibo** (N3). A auditoria não registra essa dependência.
8. **E2E isolado de recibo, registro, atalhos e diretório** — mantido.
9. **Filtro de CI que falha fechado** — mantido, com a correção concreta de §5.2. Registrar que o
   gate de release **já** é incondicional.
10. **Smoke empacotado A20** — mantido, e **executável hoje**: o feed local não é validado; só a URL
    do asset precisa ser um endereço real do GitHub (§5.1).
11. **Origem NSIS reconhecida no app-image real** (só na revisão) — mantido. É o critério certo, e a
    A20 é onde ele cabe: o plano já o registra como pendente em `:424-429`.
12. **Host final validado depois de cada redirect** (só na revisão) — **discordo como critério de
    reauditoria**. É risco residual documentado, e pinar o host de destino cria a aparência de
    cobertura que o comentário de `UpdateArtifactDownloader.kt:215-222` recusa de propósito (§5.8).
    O critério útil no lugar dele é o N1: prender a URL ao repositório e exigir HTTPS no override.
13. **Rastreamento separado do requisito Linux** — mantido, sem alteração.

Acrescentar:

14. Allowlist de download presa ao repositório, e HTTPS obrigatório no override de feed (N1) — em
    lugar do critério 12 acima.
15. Limpeza do artefato baixado após a aplicação (N2).
16. Recibo com carimbo de tempo e consumo, sem o que o critério 7 não é implementável (N3).
17. Verificação de espaço livre antes do staging (N5).
18. Reconciliar a contagem da suíte entre plano e auditoria (N6).
19. Corrigir os três pontos do plano que afirmam espera pelo PID (§5.3).

## 10. Integridade desta contra-análise

- Nenhum arquivo de código, teste, configuração, workflow ou plano foi alterado.
- Nenhum commit, push, PR ou release foi criado.
- Nenhum teste da suíte foi executado; nenhuma afirmação aqui depende de execução da suíte.
- Os três experimentos de §5.2 rodaram em diretório temporário, sobre um clone descartável, sem tocar
  o repositório de trabalho.
- Este documento é o único artefato criado. As 30 inserções e 9 remoções pendentes em
  `docs/planos/auditoria-atualizacao-automatica-windows-2026-08-22.md` **não são minhas**: são
  revisão de outro agente, feita no working tree durante esta análise (§1). Não as toquei.
- O julgamento está vinculado ao HEAD `36bf529`, cujo código é idêntico ao snapshot `31bd850`
  auditado, e à revisão não commitada da auditoria descrita em §1. Se aquela revisão for alterada de
  novo antes do commit, os itens P2-15, P2-16 e P2-17 desta análise precisam ser reconferidos.
