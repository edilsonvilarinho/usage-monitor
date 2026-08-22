# Atualização automática (issue #75) — Windows, opt-in

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
| **A07** | `prepare()`: download retomável, timeout por requisição, tamanho + SHA-256, `Files.move` atômico, poda de `~/.usage-monitor/updates/` | `desktopMain/update/WindowsAppUpdateInstaller.kt` + testes MockEngine |
| **A08** | `schedule()` + `MIN_UPDATABLE_TARGET_VERSION` num valor inalcançável | comando exato `[setup, /S, /UPDATE, /PID=<pid>]` afirmado por teste |
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
| A06 | — | — | `WindowsInstallOrigin` | pendente | — |
| A07 | — | — | Download verificado e retomável | pendente | — |
| A08 | — | — | `schedule()` e `MIN_UPDATABLE_TARGET_VERSION` | pendente | — |
| A09 | — | — | Preferência `autoUpdateEnabled` | pendente | — |
| A10 | — | — | Estados da faixa + seção no protótipo | pendente | — |
| A11 | — | — | Dedup, backoff e ciclo de vida no ViewModel | pendente | — |
| A12 | — | — | Interruptor nas Configurações + protótipo | pendente | — |
| A13 | — | — | `AutoUpdateController` e fiação no `Main.kt` | pendente | — |
| A14 | — | — | Feed sobrescrevível para teste | pendente | — |
| A15 | — | — | `.nsi` parametrizado | pendente | — |
| A16 | — | — | Modo `/UPDATE` no instalador | pendente | — |
| A17 | — | — | Seis cenários do instalador | pendente | — |
| A18 | — | — | Cenários no CI e no gate de release | pendente | — |
| A19 | — | — | Ligação da funcionalidade | pendente | — |
| A20 | — | — | Smoke test empacotado | pendente | — |
