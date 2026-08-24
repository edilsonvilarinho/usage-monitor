# Atualização automática (issue #75) — Windows, opt-in

| | |
|---|---|
| **Modelo** | Claude Opus 5 (1M context) — `claude-opus-5[1m]` |
| **Nível de esforço** | `max` |
| **Ferramenta** | Claude Code (CLI) |
| **Data** | 2026-08-22 |
| **Branch** | `feat/auto-update-windows-75` |
| **Autor dos commits** | `claude <claude@anthropic.com>` |

O modelo e o esforço ficam registrados porque este documento é o rastro de auditoria de um trabalho
feito por agente: o que decidiu, o que mediu e o que deixou por verificar depende de quem executou, e
uma auditoria posterior precisa saber sob quais condições as conclusões foram tiradas. Todas as
atividades desta tabela foram executadas nessas condições; qualquer continuação sob modelo ou esforço
diferente deve registrar a mudança aqui.

Plano de execução. A seção **Pontos de situação**, no fim, é atualizada a cada atividade, no mesmo
commit da atividade — é o registro de auditoria do trabalho.

## Contexto

Hoje o app só **avisa** que existe versão nova: `DashboardViewModel.checkForUpdate()` consulta
`GET /repos/edilsonvilarinho/usage-monitor/releases/latest` a cada 10 min e emite
`AppUpdateUiState.Available`; a faixa em `DashboardScreenWarnings.kt:243` abre a página de releases no
navegador. Daí em diante o usuário baixa 120 MB no navegador, fecha o app, executa o instalador e
responde ao diálogo "já está instalado, deseja remover?". A issue #75 pede o modelo Discord/VS Code:
baixar em segundo plano e aplicar sozinho.

Já houve uma tentativa: a branch `codex/issues-74-75-auto-update` (commit `bbc94b6`, base `511672a`).
Ela compila, empacota e passa nos testes — **e mesmo assim tem 40 defeitos catalogados, 7
bloqueadores**, nenhum coberto pela suíte. Os três que moldam este desenho:

- O `HttpClient` compartilhado tem `requestTimeoutMillis = 20_000` (`Main.kt:252`) e é o mesmo
  injetado no instalador. O Setup.exe do v37.0.0 tem **120.054.859 bytes** — o download nunca conclui.
- `startUpdateDownload` comparava só `preparedAppUpdate?.version`, que é `null` durante o download em
  voo: o poll de 10 min cancelava e reiniciava o download do zero, para sempre.
- No NSIS, o `Abort` do caminho de falha rodava com `$INSTDIR` **já renomeado**, e sem `ClearErrors`
  antes de nenhum dos três `IfErrors`. Resultado possível: `%LOCALAPPDATA%\Usage Monitor` deixa de
  existir, sem recuperação.

O catálogo completo (âncora `arquivo:linha`, evidência, quem achou) está fora do repositório, em
`resultado-analise/ranking-final-modelos.html`, regerável com `node ranking-final-dataset.js`.
**Este plano não reaproveita aquela branch**: parte de `main`, herda as ideias que se sustentam
(SHA-256 vindo do `digest` da API, `.part` + rename atômico, `processLauncher` injetável) e escreve o
resto com teste que falharia sem a correção.

Resultado pretendido: um interruptor **"Atualização automática", desmarcado por padrão**, nas
Configurações → Geral. Ligado, o app baixa a versão nova em segundo plano, valida o SHA-256, mostra
"pronta para instalar" e aplica quando o app fecha — com botão de "Reiniciar e atualizar agora" para
quem não quer esperar. Desligado, o comportamento é exatamente o de hoje.

## Escopo

**Entra:** Windows, instalação feita pelo `UsageMonitor-Setup-<v>.exe`.
A instalação é per-user em `$LOCALAPPDATA\Usage Monitor` com `RequestExecutionLevel user`
(`UsageMonitor.nsi:24,26`) — **não há UAC**, e é isso que torna a atualização silenciosa viável aqui e
não nas outras plataformas.

**Fica de fora, declarado na UI e não escondido:**

| Caso | Comportamento |
|---|---|
| Linux (`.deb`/`.rpm`) | Interruptor desabilitado com motivo. `.tar.gz` em `~/.local/share` vira issue própria — foi de lá que saíram 4 dos 7 bloqueadores. |
| macOS | Interruptor desabilitado. DMG sem Developer ID + Gatekeeper: remontar o bundle sob quarentena não fecha de forma confiável. |
| Instalação por MSI ou fora do instalador | Interruptor desabilitado. Atualizar um MSI com o NSIS cria cópia paralela e deixa um registro Windows Installer capaz de remover arquivos da versão nova. |

**Issue #74 (modal de release notes) fica em PR separado.** Ela consome o recibo que este trabalho
grava, mas misturar as duas foi parte do que produziu 40 defeitos numa entrega só.

## Alterações de tela e onde entram no protótipo

O `CLAUDE.md` passou a exigir que toda alteração de tela seja registrada em
[`prototipo-visual-opencode.html`](prototipo-visual-opencode.html) no mesmo commit da mudança.
Este trabalho toca três pontos:

| O quê | Onde no protótipo | Atividade |
|---|---|---|
| Quatro estados da faixa de atualização (disponível, baixando, pronta, falhou) | seção nova `<h2 id="atualizacao">` + link em `nav.index`, depois de `#dash-estados` | A10 |
| Interruptor "Atualização automática", variante desabilitada com motivo e linha do recibo | linhas no painel **Sistema** de `§12 #cfg-geral` | A12 |
| Marcador do feed sobrescrito por variável de ambiente | linha em `§15 #checklist` | A14 |

## Restrições do projeto que este trabalho não pode violar

- **`main()` está no limite do backend JVM** (2.162 linhas; `gradle.properties` documenta o
  `OutOfMemoryError` no ASM). Toda a fiação nova entra num arquivo próprio —
  `desktopMain/update/AutoUpdateController.kt` — e `main()` ganha **uma** chamada, não estado novo.
- Nenhuma animação infinita nova: o progresso é **texto** ("Baixando 42%"), não indicador animado —
  animação sem fim trava o `waitForIdle` dos testes de componente.
- `AppUpdateUiState` é `sealed interface`, não enum: valores novos ali são erro de compilação nos
  `when`, e portanto visíveis. Nenhum valor novo em enum existente.
- Preferência vai em `PreferencesSettings` (registro), ao lado de `CardsOnlyModePreferences.kt` —
  **não** em `~/.usage-monitor/`, onde moram os segredos do time.
- Teste nunca escreve no registro real da máquina: `MapSettings`, como o projeto já faz com
  `rememberClipboardWriter` e `DesktopUsageExportWriter`.

## Decisões de arquitetura

**1. Extrair-então-trocar, nunca sobrescrever.** O `/UPDATE` extrai para `$INSTDIR.new`, e só quando
a árvore nova está completa faz dois `Rename` no mesmo volume: `$INSTDIR` → `$INSTDIR.old` e
`$INSTDIR.new` → `$INSTDIR`. Qualquer falha antes do primeiro `Rename` aborta com `$INSTDIR`
**intacto**; falha no segundo desfaz o primeiro. A janela em que a instalação não está inteira é a
distância entre dois `MoveFile` no mesmo volume.

Com isso o caminho de recuperação **não depende de `.onInstFailed`** — não porque o callback não
funcione, mas porque não precisa existir para o `$INSTDIR` sobreviver. A A02 mediu: `Abort` dentro de
`Section` sob `/S` **roda `.onInstFailed`**, sai com exit code 2 e não roda `.onInstSuccess`. A
suposição herdada da auditoria anterior — de que o callback nunca rodava — está **errada**, e o
`.onInstFailed` entra como segunda linha de defesa para o caso de `File /r` abortar a instalação por
conta própria.

**2. O `Rename` é a sonda de liveness, não o `taskkill /F`.** No Windows não se renomeia diretório
que contém imagem de executável em uso: `Rename` que **funciona** prova que o processo saiu. O
instalador espera o PID (caminho rápido) e depois tenta o `Rename` em laço curto. Se não conseguir,
**desiste sem tocar em nada** e tenta de novo na próxima vez. Matar o processo à força durante a
escrita do SQLite é pior que não atualizar.

**3. Atualização silenciosa não reimpõe escolha do usuário.** Em `/UPDATE`, `SEC_DESKTOP` e
`SEC_AUTO_START` são desmarcadas em `.onInit`. Sem isso, toda atualização recriaria o atalho do
desktop e a chave `Run` que o usuário tinha removido.

**4. O SHA-256 é o portão de integridade, não a allowlist de host.** `github.com` redireciona para
`objects.githubusercontent.com`, e validar só a URL inicial protege menos do que aparenta. O digest
vem da API do GitHub por TLS (`digest: "sha256:…"`, presente nos 7 assets do v37.0.0) e é o que de
fato barra artefato trocado. A allowlist fica, com comentário dizendo o que ela cobre e o que não.

**5. Cliente HTTP com teto próprio.** `downloadArtifact` sobrescreve o timeout por requisição
(`requestTimeoutMillis` infinito, `socketTimeoutMillis` de 60 s, `connectTimeoutMillis` de 15 s). Um
cliente só continua servindo o app inteiro; o que muda é a requisição de 120 MB.

**6. Download retomável.** O `.part` não é apagado na falha: a tentativa seguinte manda
`Range: bytes=<tamanho do .part>-`. Resposta **206** anexa; resposta **200** trunca e reescreve do
zero — servidor que ignora o `Range` não pode produzir arquivo remendado. Sem retomada, 120 MB numa
conexão ruim é exatamente o caso em que a funcionalidade não serve.

**7. Backoff por versão, e nunca dois downloads da mesma versão.** A dedup compara a versão **em
voo**, não só a preparada. Falha entra em backoff (30 min → 2 h → 6 h, teto de 3 tentativas por
versão), zerado quando a versão disponível muda. Sem os dois, o defeito anterior era ~17 GB/dia.

**8. Nunca passar `/UPDATE` para um instalador que não o conhece.** O NSIS ignora parâmetro que não
reconhece: um Setup.exe compilado antes da atividade A16, recebendo `/S /UPDATE`, faz uma **instalação
silenciosa normal** — o que cai no `MessageBox` de "já instalado" e no `ExecWait` do desinstalador
(`.onInit:62-73`), que faz `RMDir /r "$INSTDIR"`. Ou trava invisível, ou apaga a instalação. Quem
decide não é a versão instalada, é a **versão baixada**: por isso existe
`MIN_UPDATABLE_TARGET_VERSION`, e o download só é agendado quando a versão de destino é maior ou
igual a ela. A constante recebe o número da primeira release cujo instalador foi construído com o
`/UPDATE`, e é o que impede a primeira atualização automática de ser a destrutiva.

**9. Recibo antes do relançamento.** O `/UPDATE` grava
`%USERPROFILE%\.usage-monitor\update-receipt.properties` (`version`, `previousVersion`, `status`,
`reason`) **antes** do `Exec` da versão nova, e também nos caminhos de falha. Falha silenciosa é o que
fez o episódio anterior não deixar rastro. As Configurações passam a mostrar "Última atualização: …".

## Execução — uma atividade, um commit

### PR 1 — app, com a funcionalidade inalcançável

| # | Atividade | Entrega |
|---|---|---|
| **A00** | Linha de base: suíte em `main` sem edição nenhuma | total anotado nos Pontos de situação (sem commit de código) |
| **A01** | Duas regras novas no `CLAUDE.md` + este plano em `docs/planos/` | `CLAUDE.md`, este arquivo |
| **A02** | Medir as três semânticas do NSIS (abaixo) e registrar | comentário no `.nsi` + Pontos de situação |
| **A03** | `size` e `digest` no `GitHubReleaseAssetDto` | `data/dto/GitHubReleaseDto.kt` + teste de desserialização |
| **A04** | `AppUpdateArtifact` + três enums novos; `AppUpdateInfo.artifacts` substitui os dois campos mortos; `mapArtifacts` | `domain/entity/AppUpdateInfo.kt`, `data/repository/AppUpdateRepositoryImpl.kt` + regressão com os nomes reais do v37.0.0 |
| **A05** | Contrato `AppUpdateInstaller` / `AppUpdatePreparation` no domain (puro) | `domain/repository/AppUpdateInstaller.kt` |
| **A06** | `WindowsInstallOrigin` — NSIS / MSI / não gerenciada; reaproveita `AutoStartManager.readWindowsInstallLocation()` | `desktopMain/update/WindowsInstallOrigin.kt` + teste com leitores injetados |
| **A07** | `UpdateArtifactDownloader`: download retomável, timeout por requisição, tamanho + SHA-256, `Files.move` atômico, poda de `~/.usage-monitor/updates/` | `desktopMain/update/UpdateArtifactDownloader.kt` + testes MockEngine |
| **A08** | `WindowsAppUpdateInstaller` implementando o contrato (`support`, `prepare`, `schedule`) + `MIN_UPDATABLE_TARGET_VERSION` num valor inalcançável | comando exato `[setup, /S, /UPDATE, /PID=<pid>]` afirmado por teste |
| **A09** | `AutoUpdatePreferences` (chave `autoUpdateEnabled`, default `false`) | `desktopMain/AutoUpdatePreferences.kt` + teste com `MapSettings` |
| **A10** | `AppUpdateUiState` ganha `Downloading`/`Ready`/`Failed`; `updateBannerContent` com quatro ramos | + **seção nova no protótipo** + `ComponentTest` dos quatro estados |
| **A11** | `DashboardViewModel`: dedup por versão em voo, backoff, `@Volatile`, cancelamento ao desligar, `prepareUpdateOnExit()`, `restartAndUpdateNow()` | `DashboardViewModelAutoUpdateTest` |
| **A12** | Interruptor nas Configurações → Geral, PT/EN, desabilitado com motivo; linha do recibo | + **linhas no `§12` do protótipo** + `ComponentTest` |
| **A13** | `AutoUpdateController` e **uma** chamada em `Main.kt`; agendamento como última instrução do shutdown hook | `desktopMain/update/AutoUpdateController.kt` |
| **A14** | `USAGE_MONITOR_UPDATE_FEED_URL` com marcador visível na UI | + **linha no `§15` do protótipo** |

Ao fim do PR 1 o interruptor existe, aparece desabilitado com o motivo e **não há caminho de código
que lance um instalador** — é o que impede a decisão 8 de virar acidente enquanto o `/UPDATE` não
existe.

### PR 2 — instalador e ligação

| # | Atividade | Entrega |
|---|---|---|
| **A15** | Parametrizar o `.nsi`: `!ifndef APP_FILES_DIR` e `!ifndef OUTPUT_FILE`, defaults iguais aos de hoje | `src/installer/UsageMonitor.nsi`, `build.gradle.kts:210` intacto |
| **A16** | Modo `/UPDATE` (fluxo abaixo) | `src/installer/UsageMonitor.nsi` |
| **A17** | `Invoke-UpdateScenarios.ps1` com os seis cenários | `src/installer/test/` |
| **A18** | Job no `ci.yml` + passo no `verify` do `release-linux.yml`, ambos com `choco install nsis -y` | CI |
| **A19** | `MIN_UPDATABLE_TARGET_VERSION` recebe a versão desta release; README alinhado ao comportamento real | ligação da funcionalidade |
| **A20** | Smoke test empacotado nesta máquina, roteiro abaixo | registro nos Pontos de situação |

## A02 — o que foi medido no NSIS

Seis instaladores-sonda compilados com o `makensis` da máquina e **executados de verdade**. O bloco de
comentário no topo de `src/installer/UsageMonitor.nsi` guarda o resumo; os resultados completos:

| # | Pergunta | Resultado medido |
|---|---|---|
| 1 | `MessageBox` sem `/SD` sob `/S` | **Exibe e bloqueia.** Sonda morta por timeout de 12 s com o log parado antes do desvio. O `MessageBox` do `.onInit` atual **trava para sempre** qualquer execução silenciosa deste instalador. |
| 2 | Comando bem-sucedido limpa a flag de erro? | **Não.** `CreateDirectory` e `Rename` com sucesso deixaram um `SetErrors` anterior intacto. A flag é sticky. |
| 3 | `IfErrors` limpa a flag ao ler? | **Sim.** Duas leituras seguidas de um `SetErrors` deram `SET` e depois `clean`. |
| 4 | `RMDir /r` sobre diretório inexistente seta a flag? | **Não.** |
| 5 | `${GetOptions}` com opção ausente seta a flag? | **Sim** — daí o `ClearErrors` obrigatório antes de cada leitura de parâmetro. |
| 6 | `${GetOptions} "/UPDATE"` casa com `/UPDATEPID=123`? | **Sim, e devolve `PID=123`.** Prefixo é ambíguo: `/UPDATE` + `/PID=` servem, `/UPDATE` + `/UPDATEPID=` não. |
| 7 | `Abort` dentro de `Section` sob `/S` | **Exit code 2, `.onInstFailed` roda**, `.onInstSuccess` não, Sections seguintes não rodam, sem travar. |
| 8 | `SetErrorLevel n` + `Quit` dentro de `Section` | Sai com exit `n` e **não** roda nenhum dos dois callbacks. |
| 9 | `SectionSetFlags` em `.onInit` | Só compila com a `Function` declarada **depois** das `Section` — índice de seção é resolvido em tempo de compilação. Guardar o corpo da `Section` por variável tem o mesmo efeito sem reordenar o arquivo; as duas formas foram medidas e funcionam. |

**Duas medições corrigem o plano:**

- (1) eleva o item 8 das decisões de arquitetura de "risco" a **certeza**: passar `/S /UPDATE` a um
  instalador que não conhece a opção não é "pode travar", é uma execução silenciosa que **para no
  `MessageBox` e nunca mais sai**. `MIN_UPDATABLE_TARGET_VERSION` deixa de ser precaução e vira
  requisito.
- (7) desmente a auditoria anterior, que afirmava que `.onInstFailed` nunca rodava no caminho de
  `Abort`. Ele roda; o desenho continua não dependendo dele, mas ele entra como segunda linha de
  defesa.

Um efeito colateral fica registrado e **não é corrigido aqui**: o `MessageBox` do `.onInit` continua
sem `/SD`, o que é inerte hoje (ninguém roda este instalador em silêncio) e passa a importar na A16.

## A16 — fluxo do `/UPDATE`

`.onInit`, quando `${GetOptions}` encontra `/UPDATE`: `StrCpy $UpdateMode 1`; `/PID=<n>` opcional em
`$UpdatePid`; **pula** o `MessageBox` de "já instalado"; `SectionSetFlags ${SEC_DESKTOP} 0` e
`SectionSetFlags ${SEC_AUTO_START} 0`.

`Section SEC_APP`, ramo de update, nesta ordem:

1. esperar `$UpdatePid` sair (laço com teto de ~30 s) — nada foi tocado;
2. `RMDir /r "$INSTDIR.new"`; `SetOutPath "$INSTDIR.new"`; `File /r "${APP_FILES_DIR}\*.*"`;
   `WriteUninstaller "$INSTDIR.new\Uninstall.exe"`;
3. `Rename "$INSTDIR" "$INSTDIR.old"` em laço curto (sonda de liveness). Falhou → limpa `.new`,
   grava recibo `status=failed reason=locked`, `Abort` com `$INSTDIR` intacto;
4. `Rename "$INSTDIR.new" "$INSTDIR"`. Falhou → `Rename "$INSTDIR.old" "$INSTDIR"`, recibo, `Abort`;
5. registro (`DisplayVersion`, `InstallLocation`, `UninstallString`) e atalho do Menu Iniciar —
   **fora** de qualquer caminho de rollback, para não anunciar versão que não está instalada;
6. `RMDir /r "$INSTDIR.old"`;
7. recibo `status=success`;
8. `Exec "$INSTDIR\Usage Monitor.exe"` — não bloqueante; `ExecWait` no fluxo de sucesso é o
   congelamento na tela final documentado na skill do instalador.

`ClearErrors` **imediatamente antes** de cada `IfErrors`, com a forma final decidida pela A02. Nenhum
`taskkill` no caminho `/UPDATE`.

## Testes

### `commonTest`

`AppUpdateRepositoryImplTest` (estender): `sha256:` removido do digest; asset sem digest não é
elegível; `size` mapeado. **Regressão com os nomes reais do v37.0.0** —
`UsageMonitor-Setup-37.0.0.exe`, `Usage.Monitor-37.0.0.msi`, `usage-monitor_37.0.0_linux_x64.tar.gz`,
`usage-monitor_37.0.0_macos_arm64.dmg` — porque renomear um asset no workflow desliga o auto-update em
silêncio.

`DashboardViewModelAutoUpdateTest` (novo): interruptor desligado → `prepare` nunca chamado; ligado →
`Ready`; segunda emissão da mesma versão durante o download → `prepare` **não** chamado de novo;
falha → `Failed` e nova tentativa **só** depois do backoff; versão maior → backoff zerado; desligar no
meio → job cancelado; `Ready` + saída → `schedule` chamado exatamente uma vez; `schedule` falhando →
`Failed` sem derrubar nada.

### `desktopTest`

`WindowsAppUpdateInstallerDownloadTest` (MockEngine): caminho feliz; **corpo entregue com atraso maior
que o `requestTimeoutMillis` do cliente** — falha sem a sobrescrita, que é a regressão do defeito que
matava a funcionalidade; retomada com `Range` e resposta 206; resposta 200 reescrevendo do zero;
SHA-256 divergente; tamanho divergente; arquivo final válido → zero requisições; host fora da
allowlist e `http://` rejeitados antes de qualquer requisição; poda apagando versões antigas.

`WindowsAppUpdateInstallerScheduleTest`: comando exato; sem arquivo preparado → `Result.failure` e
launcher não invocado; versão de destino abaixo de `MIN_UPDATABLE_TARGET_VERSION` → nada agendado.

`WindowsInstallOriginTest`: `InstallLocation` igual ao diretório do executável → `NSIS`; diferente →
`UNMANAGED`; ausente → `UNMANAGED`.

`AutoUpdatePreferencesTest` com `MapSettings`. `ComponentTest`: os quatro estados da faixa e a linha
desabilitada das Configurações com o motivo.

### A17 — cenários reais do instalador (`src/installer/test/Invoke-UpdateScenarios.ps1`)

Compila dois mini-instaladores do **`.nsi` real** com `-DPRODUCT_VERSION` e `-DAPP_FILES_DIR`
apontando para um payload de scratch, e roda cada cenário contra um `$INSTDIR` descartável via `/D=`.
O falso `Usage Monitor.exe` é uma cópia de `%WINDIR%\System32\where.exe`: executável de console real,
que sobe e sai sozinho — `Exec` só comprova que o `CreateProcess` funcionou.

| # | Cenário | Asserção |
|---|---|---|
| S1 | instalação limpa da v1 | arquivos, chaves HKCU, atalho do Menu Iniciar |
| S2 | usuário apagou o atalho do desktop e a chave `Run`; roda v2 `/S /UPDATE` | v2 no disco, `DisplayVersion=2.0.0`, atalho e `Run` **continuam ausentes**, recibo `success`, sem `.old`/`.new` |
| S3 | handle aberto em arquivo dentro de `$INSTDIR` | `$INSTDIR` ainda com a v1, `.new` limpo, recibo `failed reason=locked`, exit code ≠ 0 |
| S4 | `$INSTDIR.new` pré-criado como **arquivo** | falha antes da troca, `$INSTDIR` intacto |
| S5 | `/S /UPDATE` duas vezes seguidas | idempotente, sem resíduo |
| S6 | instalador **sem** `/UPDATE` sobre instalação existente | comportamento de hoje inalterado — portão de não-regressão do caminho manual |

Roda em `ci.yml` e no `verify` de `release-linux.yml`. **Fora do `allTests`**: é lento e mexe no
registro da máquina.

### A20 — smoke test empacotado (o único que fecha a issue)

Com `USAGE_MONITOR_UPDATE_FEED_URL` apontando para um JSON local servido por HTTP e um Setup.exe
local de versão maior:

1. instalar a v37 pelo Setup real; ligar o interruptor; confirmar download, SHA-256 e "pronta";
2. fechar o app → conferir substituição, relançamento, recibo, atalhos, chave `Run` e
   `~/.usage-monitor/` intacto (o `.db` de histórico e o `team.json` são o que não pode se perder);
3. repetir com o botão "Reiniciar e atualizar agora";
4. repetir com um arquivo de `$INSTDIR` travado → app volta na v37, recibo `failed`, banner com o
   motivo;
5. desligar o interruptor → nenhum download em duas janelas de poll.

## Comandos de verificação

```bat
gradlew.bat allTests                                          :: suite inteira
gradlew.bat desktopTest --rerun                               :: forca a execucao; allTests cacheia
gradlew.bat desktopTest --tests "com.usagemonitor.update.*"
gradlew.bat desktopTest --tests "com.usagemonitor.presentation.*"
gradlew.bat packageInstaller                                  :: Setup.exe local para os cenarios
powershell -ExecutionPolicy Bypass -File src\installer\test\Invoke-UpdateScenarios.ps1
```

## Riscos aceitos, registrados por escrito

- **O Setup.exe não é assinado.** SmartScreen e antivírus podem barrar um executável sem assinatura
  lançando outro em silêncio. Arquivo baixado pelo cliente HTTP do app não recebe Mark-of-the-Web, o
  que reduz a chance, **mas isso precisa ser confirmado na A20** — não é conclusão de análise. Falha
  aqui cai no banner "Baixar manualmente", que é o comportamento de hoje.
- **120 MB por versão.** Não há atualização delta e nenhum artefato publicado é menor. É o motivo do
  interruptor vir desmarcado e do subtítulo dizer o tamanho.
- **Instalação por MSI não é atualizada.** Detectada e desabilitada, nunca convertida em silêncio.

## Desvios do plano e achados da execução

O que a execução descobriu e o plano não previa. A tabela de Pontos de situação registra isto linha a
linha, na atividade em que apareceu; esta seção é a leitura consolidada — o que mudou, e por quê.

### Medições que contradisseram premissas herdadas

**`MessageBox` sem `/SD` exibe e bloqueia mesmo sob `/S`.** A sonda foi morta por timeout de 12 s com o
log parado antes do desvio. Isso reclassificou a decisão 8: passar `/S /UPDATE` a um instalador que não
conhece a opção não é "pode travar", é uma execução silenciosa que para no `MessageBox` do `.onInit` e
**nunca mais sai**. `MIN_UPDATABLE_TARGET_VERSION` deixou de ser precaução e virou requisito, com teste
guardando o valor.

**`Abort` dentro de `Section` sob `/S` roda `.onInstFailed`.** A auditoria da tentativa anterior
afirmava que o callback nunca rodava, e o plano herdou essa afirmação na justificativa da decisão 1. A
medição a desmentiu: exit code 2, `.onInstFailed` roda, `.onInstSuccess` não. O desenho continua não
dependendo do callback — mas agora por escolha, não por impossibilidade, e ele entrou como segunda
linha de defesa para o caso de `File /r` abortar por conta própria.

**`${GetOptions} "/UPDATE"` casa com `/UPDATEPID=123` e devolve `PID=123`.** A ambiguidade de prefixo
tinha sido prevista no plano e agora está medida. `/UPDATE` + `/PID=` são nomes que se distinguem;
`/UPDATE` + `/UPDATEPID=` não.

### Defeitos que só apareceram ao executar

Os dois vieram dos cenários do instalador, na atividade A16. Nenhum deles seria pego lendo o código, e
é essa a razão de os cenários existirem.

**`SetOutPath "$INSTDIR.new"` deixa o diretório de trabalho do próprio instalador dentro do staging.**
O Windows não renomeia nem apaga o CWD de um processo vivo, então o segundo `Rename` falhava
**sempre**: o cenário S2 reprovava com `reason=swap-failed` e um `$INSTDIR.new` órfão que o `RMDir`
também não removia. Corrigido com `SetOutPath "$TEMP"` antes da troca.

**O nome do valor na chave `Run` é literal e não deriva de `PRODUCT_NAME`.** A instalação de cenário
sobrescreveu a entrada de inicialização **real da máquina de desenvolvimento**, apontando-a para o
diretório descartável do teste. O valor foi restaurado à mão para o executável instalado — a
preferência do app (`auto/Start = true`) confirmou qual era o estado correto — e o `.nsi` ganhou
`AUTO_START_VALUE_NAME` com `!ifndef`, mantendo o default de produção intacto porque o
`AutoStartManager` lê exatamente aquela string.

**Consequência para o plano:** ele previa dois `!ifndef` (`APP_FILES_DIR` e `OUTPUT_FILE`). Foram
necessários **quatro**. Sem `PRODUCT_NAME` próprio os cenários apagam o atalho do Menu Iniciar da
instalação real; sem `AUTO_START_VALUE_NAME` próprio sobrescrevem a chave `Run`. Isolamento de cenário
não era detalhe de implementação — era requisito, e o plano não o enxergou.

### Ajustes na estrutura das atividades

**A07 e A08 foram redivididas.** `prepare()` e `schedule()` são membros da mesma interface, e
entregá-los em commits separados obrigaria a um `schedule()` stub — um commit que mente sobre o que
faz. O download saiu como classe própria (`UpdateArtifactDownloader`), que não tem nada de Windows, e a
A08 passou a entregar o `WindowsAppUpdateInstaller` inteiro.

**A12a não estava no plano.** Ao escrever o teste do interruptor, o `AppSwitch` mostrou não publicar
`ToggleableState`: usava `clickable` em vez de `toggleable`, e o estado era invisível para leitor de
tela e para teste, que só conseguia afirmar o clique. Corrigir uma primitiva compartilhada é decisão
própria e virou commit próprio, antes do que dependia dela.

**A A09 seguiu o padrão do projeto, não o do plano.** O plano dizia `MapSettings`; as funções de
preferência do projeto recebem `PreferencesSettings` concreto, e o padrão estabelecido é um nó
`Preferences` descartável com `removeNode()` no `finally`. O defeito D24 da auditoria era outra coisa —
nó de **nome fixo**, sem remoção. Fica registrada a dívida: `withTestSettings` está duplicado em seis
arquivos de `desktopTest` e merece extração num commit próprio.

**A coluna `Commit` da tabela guarda o assunto, não o hash.** Um commit não pode conter o próprio hash,
e preencher depois quebraria a regra de escrever a linha no mesmo commit da atividade.

### Armadilhas do ambiente, pagas uma vez cada

**O Git Bash converte `/S` em caminho POSIX.** A primeira rodada inteira de medições do NSIS foi
**inválida**: as sondas rodaram sem silêncio nenhum e `${Silent}` respondia `NO`. Refeitas com
`MSYS2_ARG_CONV_EXCL='*'`. Vale para qualquer argumento iniciado por barra, `taskkill /F` incluído.

**`gradlew.bat allTests` cacheia e não serve de linha de base.** Ele voltou `UP-TO-DATE` sem executar
teste nenhum — um "BUILD SUCCESSFUL" que não prova nada. A linha de base real exigiu
`gradlew.bat desktopTest --rerun`. O `CLAUDE.md` e o `AGENTS.md` recomendam `allTests`, o que é
correto para verificar, mas não para medir.

**O `SingleInstanceGuard` invalida `gradlew.bat run` com o app instalado aberto.** A primeira tentativa
de verificar a fiação do `main()` terminou em 2 s com `BUILD SUCCESSFUL` sem compor nada. Só com o
`Usage Monitor.exe` instalado encerrado o `run` sustentou 45 s sem exceção.

**Salto relativo do NSIS não atravessa macro.** `IfSilent 0 +2` com uma macro de quatro instruções no
caminho aterrissa no meio dela. As sondas passaram a usar LogicLib, e o `.nsi` usa rótulos nomeados em
todo desvio que importa.

### Uma restrição do plano que a execução relativizou

O plano lista "teste nunca escreve no registro real da máquina". Os cenários do instalador **escrevem**
— não há como exercitar um instalador NSIS sem que ele instale. O que os torna aceitáveis é serem
explicitamente **fora do `allTests`**, usarem nomes isolados nas quatro superfícies que tocam
(diretório, chave de desinstalação, atalhos e chave `Run`) e limparem tudo em bloco `finally`. A
restrição continua valendo para a suíte unitária, que é para onde foi escrita.

### Dois defeitos que só o smoke test empacotado encontrou (A20)

Nenhum dos dois aparecia em teste, e os dois falhavam em **100%** das tentativas, em silêncio.

1. **O instalador nunca era lançado.** `launchDetachedProcess` passava
   `ProcessBuilder.Redirect.DISCARD` para `redirectInput`, e `DISCARD` é um redirect de **escrita**:
   a chamada lançava `IllegalArgumentException("Redirect invalid for reading: WRITE")` **antes** de
   `start()`. O app fechava, nada acontecia, o app não voltava e o disco não registrava nada.
   Escapou porque `processLauncher` é injetável para o teste de `schedule` afirmar o comando exato
   sem criar processo — e **toda** a suíte usava a versão falsa, de modo que a única implementação
   que roda em produção nunca era executada. *A costura que torna uma função testável não testa a
   função que ela substitui.* Fechado por `LaunchDetachedProcessTest`, que chama o lançador real.
2. **Ligar o interruptor não fazia nada.** O observador do interruptor tratava só o desligar
   (`if (enabled) return@collect`). Quem o ligava com a faixa de "nova versão" na tela ficava
   olhando para ela até o poll seguinte, a até 10 minutos de distância, sem nenhum sinal. Foi o
   usuário quem relatou, olhando a tela. Agora ligar reavalia pelo mesmo `onUpdateAnnounced` do
   poll, que já carrega as guardas de download em voo, artefato pronto e backoff.

O que tornou o primeiro diagnosticável foi ter parado de descartar o `Result` de `schedule()`: a
falha virou recibo com o motivo, e o motivo nomeou a causa na primeira reprodução.

### Achados menores da A20

- **O artefato de 122 MB não é podado depois de aplicado** — segue em `~/.usage-monitor/updates/`.
  A contra-auditoria já suspeitava; agora está medido. Não corrigido aqui.
- **`CloseMainWindow()` não fecha este app**: o launcher do jpackage não expõe janela principal ao
  Windows. Fechar por script não exercita o caminho real de encerramento, e as duas primeiras
  reproduções por script foram inválidas por isso.
- **O roteiro da A20 estava errado**: pedia um `Setup.exe` local servido por HTTP, que
  `TRUSTED_DOWNLOAD_HOSTS` recusa.

### O que ficou por verificar

- **Estado visível do interruptor na tela.** O app sobe, mas confirmar que a linha aparece desabilitada
  com o texto certo exige interação com a janela. Vai junto da A20.
- **Qual fonte o runtime preenche** — `jpackage.app-path` ou `ProcessHandle` — no app-image real. O
  resolvedor aceita as duas justamente por isso, mas qual delas responde não foi medido.
- **Execução real dos jobs de CI.** O YAML foi validado por parser; a primeira execução de verdade só
  acontece na primeira PR.
- **Comportamento do SmartScreen e do antivírus** diante do `Setup.exe` sem assinatura lançado em
  silêncio. Está na lista de riscos aceitos e é hipótese não confirmada até a A20.

## Pontos de situação

Uma linha por atividade, escrita **no mesmo commit** da atividade. `Evidência` é o comando que rodou e
o resultado, não a intenção. Atividade que descobrir algo que muda o plano acrescenta a descoberta
aqui e ajusta a tabela de execução no mesmo commit.

A coluna `Commit` guarda o **assunto** do commit, não o hash: um commit não pode conter o próprio
hash, e preencher o hash depois quebraria a regra de escrever a linha no mesmo commit da atividade.
`git log --grep` recupera o commit pelo assunto.

| # | Data | Commit | Atividade | Estado | Evidência |
|---|---|---|---|---|---|
| A00 | 2026-08-22 | — | Linha de base da suíte em `main` (`8aa6e73`) | concluída | `gradlew.bat allTests` voltou `UP-TO-DATE` **sem executar nada** — cache do Gradle; `allTests` sozinho não serve de linha de base. `gradlew.bat desktopTest --rerun`: BUILD SUCCESSFUL em 1m25s, **105 classes / 1126 testes / 0 falhas / 0 erros / 0 pulados** |
| A01 | 2026-08-22 | `docs: require prototype updates and atomic commits…` | Duas regras novas no `CLAUDE.md` + este plano | concluída | `CLAUDE.md` §Sistema visual (protótipo) e §Convenções de código (commit atômico + pontos de situação); este arquivo |
| A02 | 2026-08-22 | `docs(installer): record the measured NSIS semantics…` | Medição das semânticas do NSIS | concluída | Seis sondas compiladas e executadas; resultados na seção A02. Primeira rodada foi **inválida** — o Git Bash converte `/S` em caminho POSIX e as sondas rodaram sem silêncio; refeitas com `MSYS2_ARG_CONV_EXCL='*'`. `MessageBox` sob `/S` trava (timeout 12 s); `Abort` em Section dá exit 2 **e roda `.onInstFailed`**, contra o que a auditoria anterior afirmava; `/UPDATE` casa com `/UPDATEPID=`. `.nsi` recompilado com `makensis /DPRODUCT_VERSION=37.0.0`: OK, 2 warnings pré-existentes de `MUI_TEXT_FINISH_RUN` |
| A03 | 2026-08-22 | `feat(update): read asset size and sha-256 digest…` | `size` e `digest` no DTO de asset | concluída | `GitHubReleaseDtoTest` novo, 4 casos sobre o corpo real do v37.0.0 com os campos que o app não declara mantidos, para exercitar o `ignoreUnknownKeys`. Os dois campos são **anuláveis**: zero afirmaria artefato vazio e reprovaria o download. `gradlew.bat desktopTest --tests "…GitHubReleaseDtoTest" --tests "…AppUpdateRepositoryImplTest"`: 4 + 11 testes, 0 falhas |
| A04 | 2026-08-22 | `feat(update): describe release artifacts by platform…` | `AppUpdateArtifact` e seleção por plataforma | concluída | Confirmado antes de remover: `windowsInstallerDownloadUrl`/`linuxDebInstallerDownloadUrl` só tinham leitor no próprio repositório e nos testes (defeito D28). Três enums **novos**, nenhum valor acrescentado a enum existente. Asset não reconhecido é **descartado**, não vira tipo "desconhecido". Regressão sobre os sete nomes reais do v37.0.0. `gradlew.bat desktopTest --rerun`: **106 classes / 1132 testes / 0 falhas** (linha de base 1126 + 4 do DTO + 2 líquidos aqui) |
| A05 | 2026-08-22 | `feat(update): define the installer contract in the domain` | Contrato `AppUpdateInstaller` no domain | concluída | Contrato de duas fases (`prepare` enquanto o app roda, `schedule` no encerramento) porque baixar 120 MB no caminho de saída não teria nem tela nem tempo. `support()` devolve **motivo**, não booleano: interruptor desabilitado sem explicação é pior que interruptor nenhum. Zero imports de Ktor/Compose. `gradlew.bat compileKotlinDesktop`: BUILD SUCCESSFUL |
| A06 | 2026-08-22 | `feat(update): resolve whether Windows was installed…` | `WindowsInstallOrigin` | concluída | Chave HKCU real desta máquina conferida por `reg query`: `InstallLocation = C:\Users\edils\AppData\Local\Usage Monitor`, com o `Usage Monitor.exe` na raiz. Duas origens: `NSIS_PER_USER` e `UNMANAGED` — MSI **não** é distinguido, porque varrer o Windows Installer por `DisplayName` é lento e frágil e afirmar "instalado pelo MSI" sem prova seria pior. A chave sozinha não basta: ela sobrevive a uma instalação removida à mão. `AutoStartManager.readWindowsInstallLocationOrNull()` exposta em vez de um segundo leitor. **Pendente para a A20:** qual das duas fontes (`jpackage.app-path` ou `ProcessHandle`) o runtime preenche no app-image — o resolvedor aceita as duas justamente por isso. `gradlew.bat desktopTest --tests "…update.*"`: 10 testes, 0 falhas |
| A07 | 2026-08-22 | `feat(update): download and verify the release artifact…` | Download verificado e retomável | concluída | **Ajuste de plano:** `prepare()` e `schedule()` são membros da mesma interface, e entregá-los em commits separados obrigaria a um `schedule()` stub — um commit que mente. O download saiu como classe própria, `UpdateArtifactDownloader`, que não tem nada de Windows (rede, disco e checksum); a A08 passa a entregar o `WindowsAppUpdateInstaller` inteiro. **Falsificação do teste de timeout:** com o bloco `timeout {}` comentado, o caso quebra com `HttpRequestTimeoutException` — o teste discrimina, não passa por acaso. 16 testes, 0 falhas |
| A08 | 2026-08-22 | `feat(update): schedule the silent Windows installer…` | `WindowsAppUpdateInstaller` e `MIN_UPDATABLE_TARGET_VERSION` | concluída | Comando exato afirmado: `[<setup>, /S, /UPDATE, /PID=<pid>]`. O PID vai junto para o instalador **esperar** o processo sair em vez de matá-lo. Um teste guarda a constante em `999.0.0`: baixá-la antes de o `.nsi` entender `/UPDATE` reabriria o caminho destrutivo, e o teste vira o portão disso. Poda só **depois** do download bem-sucedido — apagar a versão anterior por uma tentativa que a rede derrubou deixaria o usuário sem as duas. `processLauncher` devolve `Unit`, não `Process`: ninguém espera nem inspeciona o processo, e o retorno inútil obrigava o teste a criar processo de verdade. `gradlew.bat desktopTest --tests "…update.*"`: 16 + 10 + 17 testes, 0 falhas |
| A09 | 2026-08-22 | `feat(update): persist the automatic update opt-in` | Preferência `autoUpdateEnabled` | concluída | **Correção do plano:** o plano dizia `MapSettings`, mas as funções de preferência do projeto recebem `PreferencesSettings` concreto, e o padrão estabelecido (`UiScalePreferencesTest` e mais quatro) é um nó `Preferences` descartável com `removeNode()` no `finally` — que não suja o registro de quem roda a suíte. O defeito D24 da auditoria era outra coisa: nó de **nome fixo**, sem remoção. Segui o padrão. **Dívida registrada:** `withTestSettings` está duplicado em 6 arquivos de `desktopTest`; extraí-lo é limpeza legítima e não entra num commit sobre a preferência. 3 testes, 0 falhas |
| A10 | 2026-08-22 | `feat(update): give the update banner its downloading…` | Estados da faixa + seção no protótipo | concluída | Primeira mudança de tela sob a regra nova: seção `4c #atualizacao` no protótipo com os quatro estados, mais o link em `nav.index`. Progresso é **texto**, nunca indicador animado. `Downloading` **não** é clicável — faixa clicável sem rótulo de ação é alvo de clique invisível. Falha devolve o caminho manual, que é o comportamento que o app sempre teve. `AppUpdateBannerTest` novo com 8 casos, exercitando a faixa fora do `DashboardScreen`. `gradlew.bat desktopTest --rerun`: **111 classes / 1186 testes / 0 falhas** |
| A11 | 2026-08-22 | `feat(update): drive the background download from the…` | Dedup, backoff e ciclo de vida no ViewModel | concluída | **Falsificação da dedup (defeito D06):** desligada a guarda de download em voo, `a second check for the same version does not restart the download` quebra — o teste discrimina. Percentual publicado só quando o inteiro muda: 120 MB em blocos de 64 KB são ~1900 emissões, e a tela recomporia duas mil vezes para mostrar a mesma dezena. Desligar o interruptor **descarta o artefato pronto**, senão ele seria aplicado no encerramento — exatamente o que o usuário acabou de recusar. `@Volatile` em `preparedUpdate` e `downloadingVersion`, lidos pela thread do shutdown hook. 16 casos novos; `gradlew.bat desktopTest --rerun`: **112 classes / 1202 testes / 0 falhas** |
| A12a | 2026-08-22 | `fix(ui): publish the switch state in AppSwitch semantics` | Semântica do `AppSwitch` | concluída | **Atividade não prevista, descoberta ao escrever o teste da A12.** O `AppSwitch` usava `clickable` e não publicava `ToggleableState`: o estado do interruptor era invisível para leitor de tela e para teste, que só afirmava o clique. Mesma família da armadilha #2 do `CLAUDE.md`. Correção da primitiva é decisão própria e virou commit próprio, antes do que dependia dela. `toggleable` com `Role.Switch` preserva o clique. Suíte inteira verde depois da troca, que é o que importa numa primitiva usada por todos os interruptores |
| A12 | 2026-08-22 | `feat(settings): add the automatic update switch, off…` | Interruptor nas Configurações + protótipo | concluída | Texto de apoio diz **o tamanho e o momento** — os dois surpreendem, e interruptor que não avisa liga algo que ninguém escolheu. Desabilitado carrega **o motivo**, um por valor de `AppUpdateSupport`. Sem suporte o interruptor **mostra desligado**, não o valor guardado: ligado-mas-inerte é promessa falsa. Recibo com `status` desconhecido é tratado como **falha** — anunciar sucesso de uma atualização que não se sabe se aconteceu é pior. Protótipo `§12` com as duas variantes. `AutoUpdateToggleTest` com 11 casos; suíte: **113 classes / 1215 testes / 0 falhas** |
| A13 | 2026-08-22 | `feat(update): wire the automatic update controller…` | `AutoUpdateController` e fiação no `Main.kt` | concluída | `main()` ganhou **uma** chamada e nenhum estado novo — `isEnabled()` é função de extensão composable justamente para o `collectAsState` não morar lá. `AUTO_UPDATE_SHIPPED = false` deixa `installer` nulo: no PR 1 **não existe caminho de código que lance instalador**. `AutoUpdateWiringTest` reprova a combinação inconsistente dos dois interruptores de build, nos dois sentidos — é o portão que a A19 tem de atravessar de propósito. `scheduleUpdateOnExit()` é a **última** instrução dos três caminhos de saída. "Reiniciar agora" reusa `shutdownApplication`, e não um segundo caminho de encerramento. `gradlew.bat desktopTest --rerun`: **114 classes / 1217 testes / 0 falhas** |
| — | 2026-08-22 | `docs(plan): close the runtime verification of the desktop wiring` | Verificação em execução da A13 | concluída | Primeira tentativa foi **inválida**: `gradlew.bat run` terminou em 2 s sem compor nada porque o `Usage Monitor.exe` instalado estava em execução e o `SingleInstanceGuard` derrubou o processo de desenvolvimento antes do `main()` — um "BUILD SUCCESSFUL" que não provava nada. Com o app instalado encerrado, o `run` **sustentou 45 s sem sair e sem exceção**, e o processo do app desapareceu ao ser interrompido, sem órfão. Fica **não verificado** o estado visível do interruptor na tela: isso exige interação com a janela, e vai junto da A20 |
| A14 | 2026-08-22 | `feat(update): allow overriding the release feed for…` | Feed sobrescrevível para teste | concluída | `USAGE_MONITOR_UPDATE_FEED_URL` troca a URL da API, com leitor injetável no molde do `MiniMaxRepositoryImpl`. **Aviso em tom âmbar na aba Geral enquanto a variável está ativa**: o SHA-256 que barra artefato adulterado vem do mesmo feed, e quem esquecer de desligá-la precisa esbarrar nisso. Protótipo `§15` com este risco e com o do `Setup.exe` sem assinatura. `gradlew.bat desktopTest --rerun`: **114 classes / 1221 testes / 0 falhas** — PR 1 fechado |
| A15 | 2026-08-22 | `build(installer): parameterize the payload and output paths` | `.nsi` parametrizado | concluída | `APP_FILES_DIR` e `OUTPUT_FILE` com `!ifndef`, defaults iguais aos caminhos que o `buildNsisInstaller` já usa. Build de release conferido **byte a byte**: `122.307.638` bytes, o mesmo de antes da mudança. Com as sobrescritas, o mesmo `.nsi` compilou um instalador de 115 KB sobre payload de teste — que é o ponto: cenário que exercita um `.nsi` paralelo não testa o instalador que sai no release |
| A16 | 2026-08-22 | `feat(installer): apply updates silently with an extract-then-swap flow` | Modo `/UPDATE` no instalador | concluída | **Dois defeitos encontrados por rodar, não por ler.** (1) `SetOutPath "$INSTDIR.new"` deixa o diretório de trabalho do próprio instalador dentro do staging, e o Windows não renomeia nem apaga o CWD de um processo vivo: o cenário S2 reprovava com `reason=swap-failed` e um `.new` órfão que o `RMDir` também não removia. Corrigido com `SetOutPath "$TEMP"` antes da troca. (2) O nome do valor na chave `Run` é **literal**, não derivado de `PRODUCT_NAME`: a instalação de cenário sobrescreveu a entrada de inicialização **real** desta máquina, apontando-a para o diretório descartável do teste. Restaurada à mão para o executável instalado (a preferência do app dizia `auto/Start = true`), e o `.nsi` ganhou `AUTO_START_VALUE_NAME` com `!ifndef` para o cenário poder se isolar. `MessageBox` do `.onInit` ganhou `/SD IDNO` — inerte no fluxo interativo, e a diferença entre travar e não travar numa execução silenciosa. **22 asserções em S1/S2/S3/S6, 0 falhas.** Build de produção recompilado: 122.309.088 bytes, 1.450 a mais que antes — o código do `/UPDATE` |
| A17 | 2026-08-22 | `test(installer): cover the silent update scenarios end to end` | Seis cenários do instalador | concluída | `src/installer/test/Invoke-UpdateScenarios.ps1`, **33 verificações nos seis cenários, 0 falhas**. Compila o `UsageMonitor.nsi` de produção — não uma cópia — com os quatro `!ifndef` sobrescritos. O isolamento por `PRODUCT_NAME` e `AUTO_START_VALUE_NAME` não é detalhe: sem eles os cenários apagam o atalho do Menu Iniciar e sobrescrevem a chave `Run` da instalação real, o que **aconteceu de verdade** na A16. Limpeza em bloco `finally`, e o ambiente real foi conferido depois da execução: chave `Run` no executável instalado, atalhos intactos, nenhum recibo deixado para trás |
| A18 | 2026-08-22 | `ci: run the installer update scenarios on windows` | Cenários no CI e no gate de release | concluída | Job **separado** no `ci.yml` (`installer-scenarios`), com recorte por path próprio: o roteiro instala e desinstala de verdade e leva minutos, e amarrá-lo ao job rápido faria toda mudança de Kotlin esperar por ele. Também no `verify` do `release-linux.yml` — ali é o gate que decide se um release sai, e release com o `/UPDATE` quebrado atualiza a máquina de quem instalou. YAML dos dois arquivos validado (`yaml.safe_load`); a execução real só na primeira PR |
| — | 2026-08-22 | `docs(plan): record the model and effort level used` | Cabeçalho de procedência | concluída | Modelo, nível de esforço, ferramenta, branch e autor dos commits no topo do documento. O rastro de auditoria de um trabalho feito por agente precisa dizer sob quais condições as conclusões foram tiradas — sem isso, "medido" e "verificado" não têm dono |
| — | 2026-08-22 | `docs(plan): consolidate the deviations found while executing` | Seção **Desvios do plano e achados da execução** | concluída | Fecha o PR 1 e o PR 2 com a leitura consolidada do que a execução descobriu e o plano não previa: 3 medições no NSIS — duas delas contradizendo premissas herdadas da auditoria anterior —, 2 defeitos que só apareceram ao executar, 4 ajustes na estrutura das atividades, 4 armadilhas de ambiente, 1 restrição relativizada e 4 itens por verificar. O plano previa 2 `!ifndef` no `.nsi`; foram necessários **4** — isolamento de cenário era requisito, não detalhe |
| A19 | 2026-08-24 | `feat(update): turn the automatic update on for the next release` | Ligação da funcionalidade | concluída | `AUTO_UPDATE_SHIPPED` para `true` e `MIN_UPDATABLE_TARGET_VERSION` de `999.0.0` para **`38.0.0`**, a primeira release que sai com o `/UPDATE` no `.nsi`. **Pré-requisito que não estava nesta lista:** a #78 tinha de fechar antes — enquanto o portão de origem dava `NSIS_PER_USER` para uma instalação MSI, ligar isto rodaria o `Setup.exe /S /UPDATE` sobre uma árvore do Windows Installer, que é o cenário que o KDoc do enum descreve como razão de ele existir. Dois testes trocaram de lado: o de `WindowsAppUpdateInstallerTest` que afirmava o sentinela `999.0.0` agora afirma que **nenhuma release anterior ao `/UPDATE` é alvo aceito** (`23.0.0` e `37.0.0` recusadas, `38.0.0` aceita), e `AutoUpdateWiringTest` ganhou um caso novo com a constante `LAST_VERSION_WITHOUT_UPDATE_MODE`: só a direção **para baixo** é destrutiva, então o portão afirma o piso e não a igualdade com a tag — amarrar à tag exigiria que o teste soubesse um número decidido depois. README com a seção de atualização automática: o que é, os dois casos em que o interruptor vem desabilitado com motivo, os ~120 MB, o executável sem assinatura e o piso de 38.0.0. `gradlew.bat desktopTest --rerun`: **114 classes / 1223 testes / 0 falhas** |
| A20 | 2026-08-24 | `fix(update): actually launch the installer and react to the switch` | Smoke test empacotado | concluída | Feed local servindo o prerelease real `smoke-a20-38` — o roteiro pedia um `Setup.exe` local, e `TRUSTED_DOWNLOAD_HOSTS` só aceita `github.com`, então o asset é publicado de verdade. **Passo 1 verde:** download em <10 s, 122.309.429 bytes exatos, SHA-256 igual ao `digest` da API. **Passo 2 verde depois de duas correções:** 37.0.0 → **39.0.0**, app relançado sozinho, recibo `status=success previousVersion=37.0.0`, **um** jar na pasta (sem órfão), sem `INSTDIR.new`/`.old`, `Uninstall.exe`, atalho e chave `Run` no lugar, e dados intactos (`team.json` com o mesmo SHA-256, 39 preferências). **Passo 3 verde:** o botão "Reiniciar e atualizar agora" foi exercitado pelo usuário e é o caminho que fechou o ciclo. **Passo 5 verde:** interruptor desligado, zero downloads em 120 s, faixa no caminho manual. **Passo 4 não exercitado ponta a ponta**: o lado do instalador já é o cenário S3 (exit 3, recibo `locked`, instalação intacta) e o lado da faixa é `AppUpdateBannerTest`; falta só a integração dos dois. **Dois defeitos encontrados, ambos invisíveis e ambos de 100% de incidência** — ver *Desvios*. `gradlew.bat desktopTest --rerun`: **115 classes / 1226 testes / 0 falhas** |
