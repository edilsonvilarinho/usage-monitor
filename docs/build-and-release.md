# Build and release

## Local build

| Command | Result |
|---|---|
| `gradlew.bat run` | runs the desktop app |
| `gradlew.bat desktopJar` | executable JAR |
| `gradlew.bat createDistributable` | desktop image in `build/compose/binaries/main/app/Usage Monitor` |
| `gradlew.bat packageInstaller` | NSIS installer, when NSIS is installed |
| `gradlew.bat packageDmg` | macOS only — jpackage does not cross-compile |
| `gradlew.bat generateScreenshots` | regenerates `img/*.png` |
| `gradlew.bat generateTourGif` | regenerates `img/tour.gif` |

`build-with-icon.ps1` is a Windows helper flow: it builds the distributable, applies the icon with
`rcedit`, and calls NSIS by hand.

The app version comes from `build.gradle.kts` and is propagated into the generated
`CURRENT_APP_VERSION` constant. It is never written by hand.

## Distribution formats

`TargetFormat.Exe` (Windows), `Deb` and `Rpm` (Linux), `Dmg` (macOS).

**`Msi` was dropped.** Both Windows installers wrote to the same `%LOCALAPPDATA%\Usage Monitor`, and
the MSI one could never update itself — the auto-updater only accepts the NSIS artifact. The
`upgradeUuid` stays in `build.gradle.kts` because it is the UpgradeCode of the MSI installations that
already exist, and it is how `UsageMonitor.nsi` finds and removes them before installing.

The DMGs ship **without an Apple Developer ID signature**, so Gatekeeper needs a manual override on
first launch.

## CI

| Workflow | Runs |
|---|---|
| `.github/workflows/ci.yml` | Everything on pull request and push to `main`: desktop suite (Windows, plus Linux as advisory), installer and Linux updater scenarios, team server, pricing parity — each only when its paths changed; `ci-ok` aggregates |
| `.github/workflows/release-linux.yml` | on `v*` tags: publishes Windows, Linux and macOS artifacts |
| `.github/workflows/codeql.yml` | CodeQL on push to `main`, weekly and manually |

The `changes` job decides which areas the diff touches (`HEAD^1..HEAD` on a pull request, whose
checkout is the merge commit; `before..sha` on push; any git failure means "run everything") and
writes that table to the run summary. A job for an untouched area is skipped at job level, so no
runner starts. `ci-ok` runs always, treats `skipped` as success and `failure`/`cancelled` as failure,
and is the single check branch protection should require. Every test job that runs publishes counts
and slowest classes to `$GITHUB_STEP_SUMMARY`, and `--require` fails it when no XML was produced.

The release is tag-only (#344). The version comes from the tag: `build.gradle.kts` reads
`-PappVersion`, which the release workflow passes, and falls back to
`git describe --tags --abbrev=0` for local builds (`1.0.0` without git or tags, since the Compose
plugin rejects a `0` major at configuration time). There is no version bump commit, so cutting a
release pushes nothing to `main` and triggers no `CI` or `CodeQL` run there.

`verify-version` rejects a tag that is not `vX.Y.Z`, is lightweight, or points outside `main`.
`verify` (Windows `allTests` plus installer scenarios) runs in parallel with the four builds, and
`publish-release` requires both. A manual dispatch with `publish: false` builds and verifies an
existing tag without publishing.

### Gradle cache

The cache comes from `gradle/actions/setup-gradle`, **not** from `setup-java`'s `cache: 'gradle'`.
The latter archives `~/.gradle` with the daemon still alive, and on Windows `tar` dies on the `.lock`
files. Only `main` writes the cache: a cache written from a pull request run is scoped to that PR and
no other run can read it. Tag runs (release) and CodeQL only read: each tag is a new ref, so a cache
written there is never read again, and those writes pushed the repository past the 10 GB limit and
evicted `main`'s cache (#344).

### Parallel test forks

The suite runs in a single fork. `-PtestForks=N` is opt-in and must only be used on a machine with a
warm `~/.skiko`: Skiko unpacks `skiko-windows-x64.dll` with a `Files.move` that fails with
`AccessDeniedException` when another process has already opened the destination. On a clean runner
every fork tries to unpack at once.

**If UI tests are green locally and red in CI, look at `~/.skiko` before you look at the test.**

### Coverage

Kover instrumentation is opt-in via `-Pcoverage`, because it costs 6–7 s per run. CI turns it on in
every run of `desktop-windows`.

```bat
gradlew.bat allTests -Pcoverage
gradlew.bat koverHtmlReport -Pcoverage
```

There is no `koverVerify` and no floor: a threshold calibrated before the baseline existed is a
threshold calibrated in the dark. Baseline as of 2026-08-25: **82.7% of lines**, 52.3% of branches.

## NSIS installer

Lessons already paid for in this project:

- Use `SetCompressor zlib` at the top of the `.nsi`. A freeze at 99% usually means an LZMA
  compression problem.
- Avoid a blocking `ExecWait` launch in the success path. A freeze on the final screen usually means
  a blocking process.

The installer is per-user with `RequestExecutionLevel user` — no UAC prompt. That is what makes the
silent auto-update swap viable: it extracts to `$INSTDIR.new` and only swaps with two `Rename` calls
on the same volume once the new tree is complete. A successful `Rename` **is** the proof that the
previous process exited; `taskkill /F` is not, and could kill it mid-SQLite-write.

## Auto-update

Off by default. Enabled, it downloads the release in the background, validates the SHA-256 against
the `digest` field of the GitHub API — not the hash published by the workflow, which only serves the
initial installer — and swaps on app exit, or immediately via **Restart and update now**.

| Platform | Supported install | Notes |
|---|---|---|
| Windows | NSIS per-user (`UsageMonitor-Setup-*.exe`) | MSI and manual copies are excluded |
| Linux x64 | user-space `.sh` install in a managed XDG tree | `.deb`/`.rpm` belong to the package manager |
| Linux ARM64 | — | no ARM64 tarball is published |
| macOS | — | no Developer ID, no reliable way to remount the bundle under quarantine |

Each installer sits behind its own build flag and a **minimum target version**: below that floor, the
installed version does not understand the confirmation handshake and the updater would roll back an
update that actually worked.

On Linux, `current` is a **text file holding the version, not a symlink** — `mv -T` is not POSIX. The
script promotes by plain `rename(2)`, relaunches the stable launcher, and waits for a file-based ACK
carrying a token the script generated. Without the ACK in 60 s it rolls back. The log always lands in
`~/.usage-monitor/diagnostics/linux-update.log`.

Progress is reported as **text** ("Downloading 42%"), never an infinite animation — those hang
`waitForIdle` in the component tests.

**Beta channel** (issue #355): Settings → General → "Receive beta updates", off by default. Betas are
`vX.Y.Z-beta.N` tags published as GitHub **prereleases**, never `latest`, so only users in the channel
see them. Cut them with the `usage-monitor-release-beta` skill. Details in
"Decisões de empacotamento e atualização" below.

## Branding

`tools/brand/render_icons.py` generates the PNG, ICO and ICNS from a monogram described in code.
The `monogram.svg` beside it is reference material and is not read by the script. The `.icns` is only
validated in the `build-macos` release job.

## Decisões de empacotamento e atualização

> Movido do `CLAUDE.md` em 2026-09-27 pela skill `usage-monitor-token-cleanup` (#319). O `CLAUDE.md` guarda as regras curtas e aponta para cá; o texto abaixo é o original, com os links relativos ajustados a este diretório.

### Empacotamento

`TargetFormat.Exe` (Windows), `Deb`/`Rpm` (Linux) e `Dmg` (macOS). **O `Msi` saiu**: os dois instaladores de Windows gravavam no mesmo `%LOCALAPPDATA%\Usage Monitor`, e o do MSI nunca poderia se atualizar sozinho — `selectArtifact` só aceita `WINDOWS_NSIS`. O `upgradeUuid` continua no `build.gradle.kts` porque é o UpgradeCode das instalações MSI que já existem, e é por ele que o `UsageMonitor.nsi` as encontra e remove antes de instalar. O jpackage **não faz cross-compile**: o `.dmg` só sai rodando em macOS, por isso o release depende do job `build-macos` (`macos-latest` arm64 + `macos-15-intel` x64) em `.github/workflows/release-linux.yml`. Os DMGs vão sem assinatura Apple — o Gatekeeper exige liberação manual, documentada no README.

**Versão de pacote de uma beta** (issue #355): o plugin do Compose valida `packageVersion` por formato
e recusa `X.Y.Z-beta.N` em três deles — medido com `createDistributable -PappVersion=99.0.0-beta.1`,
que falha na **configuração**: Exe exige `MAJOR.MINOR.BUILD` numérico, Dmg exige versão e build
version numéricas, Rpm proíbe `-`; Deb aceita. Por isso `packageVersion` recebe só o número-base
(`packageBaseVersion`), e Deb/Rpm recebem `X.Y.Z~beta.N` (`linuxPackageVersion`) — o `~` é ordenado
**antes** da estável do mesmo número pelos dois gerenciadores de pacote. A string completa continua
onde é o app que lê: `CURRENT_APP_VERSION`, `/DPRODUCT_VERSION` do NSIS (nome do `Setup.exe`,
`DisplayVersion` e recibo), tarball e nome dos assets publicados. Conferido na imagem gerada:
`Usage Monitor.cfg` com `-Djpackage.app-version=99.0.0` e `CURRENT_APP_VERSION = "99.0.0-beta.1"`.
O `Setup.exe` beta não foi gerado nesta máquina (sem NSIS); quem o prova é o job `build-windows`.

**Confiança TLS do sistema** (`SystemTrustStore.kt`, issue #325): o `HttpClient(OkHttp)` usa um `X509TrustManager` composto — `cacerts` da JVM primeiro, repositório do sistema depois (`Windows-ROOT` no Windows, `KeychainStore` no macOS). Antivírus que inspecionam HTTPS (Kaspersky, ESET) reassinam o tráfego com uma CA instalada no repositório do Windows, e o runtime empacotado só conhecia o `cacerts` embarcado. **`jdk.crypto.mscapi` entra no `modules(...)` só no build Windows**: medido, o runtime da v41.1.0 instalado declarava `MODULES="… java.sql jdk.crypto.ec"`, sem ele — `Windows-ROOT` nem existia no app —, e o módulo só existe no JDK do Windows, então sem a condição o jlink do Linux/macOS falharia. Repositório do sistema que não carrega devolve `null`, e o cliente fica com o padrão da JVM. Linux continua só com o `cacerts`: não existe um repositório único entre distribuições. A validação de ponta a ponta exige máquina com antivírus inspecionando HTTPS e não foi feita.

Auto-start (`AutoStartManager`): registro `Run` no Windows, `.desktop` no Linux, LaunchAgent (`~/Library/LaunchAgents/com.usagemonitor.app.plist` + `launchctl`) no macOS. O enum `Platform` é exaustivo em três `when` do arquivo — valor novo quebra a compilação nos três.
- **A entrada carrega `--autostart`** (`StartupOrigin`), porque o processo lançado pela chave `Run` e o lançado pelo atalho têm o mesmo pai — o Explorer — e sem o argumento são indistinguíveis. O **nome** do valor não muda: é por ele que `isWindowsAutoStartEnabled` decide se a inicialização está ligada. `ensureAutoStartCommandCurrent()` migra por baixo quem já tinha a entrada sem o argumento; entrada **ausente não migra**, senão ligaria a inicialização de quem a desligou.
- **O Agendador de Tarefas foi medido e recusado** (`docs/planos/arranque-no-logon-execucao.md`, A05): `schtasks /Create /SC ONLOGON` devolve `Acesso negado` a processo não elevado, com e sem `/RU`. O instalador roda com `RequestExecutionLevel user` e o app roda não elevado — nenhum dos dois criaria a tarefa. Os ~40 s entre o logon e a janela são a fila que o Explorer serializa, não custo do app.

Arranque e segunda instância (`SingleInstanceGuard`, `FocusRequestChannel`, `StartupDiagnostics`): com o app já de pé, a segunda instância **não pode sair calada** — clicar no atalho sem nada acontecer é indistinguível de "o app não abre", e foi o que fez o autostart ser dado como quebrado numa máquina em que ele nunca deixou de disparar. Ela deixa um pedido em `~/.usage-monitor/focus.request` e a instância viva o atende por `restoreMainWindow`, o **mesmo** caminho do item "Abrir" da bandeja.
- **Arquivo e não socket:** socket em loopback dispara o prompt do Firewall no primeiro arranque, e pedir permissão de rede para focar a própria janela é pior que o defeito. O carimbo vai no **conteúdo**, não em `lastModified`, que depende da granularidade do sistema de arquivos. Pedido sobrado de sessão anterior não é atendido — a janela saltaria sozinha no arranque.
- **`activateWindow` alterna `alwaysOnTop` (`false → true → valor anterior`), e não "liga se estiver desligado".** Medido: com o sinalizador já ligado, atribuir `true` de novo não reordena nada e a janela continua atrás da que a cobre — exatamente o caso de quem usa "manter sempre visível". `toFront()` sozinho não vence o bloqueio de primeiro plano do Windows: ele só pisca o botão na barra.
- **O registro de arranque é sempre ligado** (`~/.usage-monitor/diagnostics/startup.jsonl`), ao contrário dos recorders de créditos e do Codex, que são opt-in por variável de ambiente. Aqueles gravam corpo de resposta a cada coleta; este grava uma linha por arranque, com corte por contagem. Diagnóstico que exige variável configurada **antes** do fato não serve para investigar o boot que já passou. `FOCUS_REQUEST_SERVED` existe para separar "o pedido nunca foi lido" de "foi lido e a janela não subiu", que são defeitos em lugares diferentes.

**Atualização automática** (`desktopMain/update/`; planos
[`atualizacao-automatica-windows-execucao.md`](planos/atualizacao-automatica-windows-execucao.md)
e [`atualizacao-automatica-linux-execucao.md`](planos/atualizacao-automatica-linux-execucao.md)):
interruptor "Atualização automática" nas Configurações → Geral, desmarcado por padrão
(`autoUpdateEnabled` em `PreferencesSettings`). Ligado, baixa a release em segundo plano, valida o
SHA-256 contra o `digest` da API do GitHub (não o hash publicado no workflow, que serve só ao
instalador inicial) e troca ao fechar o app — ou pelo botão "Reiniciar o app e atualizar".
**Todo texto que manda reiniciar diz o que reinicia** (issue #274): "Reiniciar e atualizar agora"
era lido como reiniciar o computador. Os avisos em prosa nomeiam o Usage Monitor; o **rótulo** da
ação diz "o app" (`UPDATE_RESTART_ACTION_PT`/`_EN`), porque a faixa é de uma linha e quem cede
espaço é o título — com o nome inteiro o rótulo ia de ~209dp a ~281dp e o título sumia numa janela
de 400dp. A ajuda cita a constante em vez de copiar o texto, e `HudNotchTextFitTest` mede frase e
ação contra as linhas que o balão da engrenagem reserva.
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

**Canal beta** (issue #355; plano [`releases-beta-355-execucao.md`](planos/releases-beta-355-execucao.md)):
interruptor "Receber versões beta" em Configurações → Geral, logo abaixo da atualização automática,
desmarcado por padrão (`receiveBetaUpdates` em `PreferencesSettings`). Independe da atualização
automática: sem ela a beta só é anunciada.
- **Quem não optou está protegido pelo GitHub, não pelo app.** A beta é a tag `vX.Y.Z-beta.N`,
  publicada pelo workflow com `prerelease: true` e `make_latest: false`. Fora do canal o app lê
  `/releases/latest`, que a API nunca responde com prerelease — e isso vale também para as versões do
  app anteriores ao canal, que não sabem que ele existe. Beta publicada como Latest é incidente: todo
  usuário passaria a recebê-la.
- **Dentro do canal**, `fetchGitHubReleases` lista `/releases?per_page=20`, descarta rascunho e oferece
  a maior versão acima da atual. Com o feed sobrescrito (`USAGE_MONITOR_UPDATE_FEED_URL`) a listagem não
  existe e o feed único vale para os dois canais.
- **Ordenação SemVer em `AppVersionComparison.kt`**, ainda dono único: `42.0.0-beta.1 < 42.0.0-beta.2 <
  42.0.0`. Antes o sufixo era descartado e as três comparavam iguais — a `beta.2` nunca seria oferecida a
  quem estava na `beta.1`, nem a estável a quem estava na beta, e as novidades da estável cairiam em
  marca silenciosa. O sufixo só conta com núcleo numérico legível: `sem-numero` continua comparando
  igual a uma versão vazia (falha fechado).
- **Desligar o canal não faz downgrade**: o repositório nunca oferece versão menor que a em execução, e
  quem está numa beta fica nela até sair uma estável maior. Alternar o interruptor reconsulta na hora
  (`startBetaChannelWatcher`), sem esperar o poll de 10 minutos.
- **O Linux aceita só o sufixo `-beta.N`** (`isValidLinuxVersionName` e `linux-updater.sh`): o valor vira
  nome de diretório, e abrir para qualquer identificador de pré-lançamento abriria caminho para `/` e
  `..`.
- **Destaque**: banner, balão da HUD, janela de novidades e rodapé dizem "beta" em **texto**; novidades e
  rodapé levam ainda o selo `BetaReleasePill`. No balão da HUD o número desce para o detalhe — a linha
  curta tem largura fixa e `42.10.10-beta.12` não cabe ao lado do estado (`HudNotchTextFitTest`).
- **Notas de release**: a estável difere da estável anterior (lista a série beta inteira); a beta difere
  da tag anterior, beta ou estável. Ordem com `versionsort.suffix=-` — sem ele o git põe a beta depois da
  estável do mesmo número.
- **Publicação**: skill `usage-monitor-release-beta`. A estável segue na `usage-monitor-release`, e a
  versão-base dela e de `lastReleaseTagVersion()` ignora tags beta (`--exclude "*-beta*"`).

**Ajuda dentro do app** (`presentation/ui/help/` + `desktopMain/help/HelpMediaPlayer.kt` +
`desktopMain/presentation/ui/HelpWindow.kt` + `src/desktopMain/resources/help/*.gif`; issue #184,
plano [`modal-de-ajuda-184-execucao.md`](planos/modal-de-ajuda-184-execucao.md)): doze tópicos
com o que cada funcionalidade faz, **como ativá-la** e uma demo animada dela. Fora do app o produto
já estava documentado no README; dentro dele não havia porta nenhuma, e as funcionalidades que
precisam ser **ligadas** (HUD, somente cards, alertas, atualização automática, time, orçamento) só
eram descobertas por acidente.
- **O catálogo é `CliSessionsGlossary` com outro assunto**: enum de tópicos, `readingOrder` e
  entradas PT/EN em `presentation/ui/`. Os passos de ativação citam o **rótulo real** do controle,
  lido do código; trocar o rótulo na tela sem trocar aqui manda o usuário procurar um botão que não
  existe. Os `when` exaustivos não pegam isso — pegam a entrada faltando, não a entrada errada —, e
  por isso os testes afirmam que todo tópico tem os dois idiomas e pelo menos um passo.
- **Compose não anima GIF; o `Codec` do Skia anima.** `frameCount`, `getFrameInfo(i).duration` e
  `readPixels(bitmap, frame, priorFrame)` já estão no classpath (skiko 0.8.18). `priorFrame` é
  **otimização, não correção**: sem ele o codec refaz a cadeia de quadros requeridos a cada tique, o
  que num GIF delta é trabalho quadrático. O quadro publicado é **cópia imutável dos bytes**
  (`readPixels` → `installPixels`), porque `Bitmap.asComposeImageBitmap()` embrulha o mesmo bitmap e
  escrever o quadro seguinte por cima mutaria a imagem que já está na tela, sem invalidar nada.
  **`Bitmap.makeClone()` compartilha os pixels** e reprova o teste de imutabilidade; e
  `Image.makeFromBitmap(...).toComposeImageBitmap()`, o caminho anterior, redesenha o quadro por um
  `Canvas` a ~59 ms por quadro — 35 s de suíte num teste só (issue #295).
- **O laço de quadros mora em `desktopMain`, nunca no composable de conteúdo.** É essa separação que
  deixa `HelpContent` exercitável: animação infinita trava o `waitForIdle` dos testes de componente.
  Pela mesma razão o tópico selecionado é hasteado — quem carrega a demo é o tocador, que precisa
  saber qual está na tela.
- **As demos são gravadas em 1000×420, a largura de uma janela real**, e não no tamanho da faixa do
  modal: as telas deste app têm orçamento de coluna de ~1000dp, e gravá-las estreitas mostraria um
  layout que o app não tem. Reduzir a gravação pela metade tornaria ilegível justamente o rótulo que
  ela aponta. Por isso a janela nasce em 1180×780dp. `gradlew.bat generateHelpMedia` regenera todas,
  pelo mesmo motor de `img/tour.gif` (`SceneRecorder`, extraído do gerador do tour).
- **A faixa da demo é teto, não altura fixa.** Medido no app, numa área útil de 1280×752 com a escala
  em 115%: os 420dp fixos deixavam a seção "Como ativar" abaixo da dobra — que é a pergunta que a
  tela existe para responder. Ela cede até 55% da área rolável, e quem encolhe é a demo, que o `Fit`
  mantém inteira; a seção que sai da vista não tem como se encolher.
- **Mídia ausente não esconde o texto.** Recurso que não veio na instalação vira estado vazio com a
  frase dizendo isso, e descrição e passos continuam: a demo ilustra o tópico, não é o tópico.
- **Três portas — rodapé, bandeja e `F1`** —, o mesmo desenho do modo somente cards e da barra HUD: o
  rodapé é a porta óbvia e é a primeira coisa que esses dois modos escondem.
- **O que os testes não pegam, o olho pegou.** Quatro defeitos vieram de olhar o quadro gerado e de
  abrir o app: o ponteiro sintético não estava sendo composto e as demos mostravam a tela reagindo
  sozinha; o deslocamento passava do fim do conteúdo; a demo de modos de janela desenhava o HUD por
  cima dos cards e as duas exibições se misturaram; e a grade de cards, fora de um contêiner rolável,
  é ancorada pelo centro e o quadro começava no meio de um card. `HelpMediaResourcesTest` cobre o que
  dá para afirmar por teste: todo `mediaId` resolve no classpath, decodifica, e **muda de um quadro
  para o outro** — foi ele que reprovou a demo de presença, que tinha saído parada.

## CI e testes — decisões

> Movido do `CLAUDE.md` em 2026-09-27 pela skill `usage-monitor-token-cleanup` (#319). O `CLAUDE.md` guarda as regras curtas e aponta para cá; o texto abaixo é o original, com os links relativos ajustados a este diretório.

## CI e testes

Um workflow, `ci.yml`, desde a #344 (antes eram `ci.yml` e `ci-server.yml`, cada um com o próprio
recorte por path). O plano com as medições está em
[`docs/planos/ci-testes-detalhe-e-velocidade-execucao.md`](planos/ci-testes-detalhe-e-velocidade-execucao.md).

- **O cache do Gradle é da `gradle/actions/setup-gradle`, não do `cache: 'gradle'` do `setup-java`.**
  O post-step daquele arquiva o `~/.gradle` com o daemon vivo e no Windows o `tar` morre nos `.lock`
  (`Device or resource busy` → `exit code 2`). O efeito era total e silencioso: todo run começava com
  `gradle cache is not found` e o repositório não tinha **uma** entrada Windows em `gh cache list`. Os
  ~57 s gastos antes da primeira tarefa eram **download** — a mesma fase custa 0,44 s numa máquina com
  o `~/.gradle` quente. **Só a `main` escreve o cache** (`cache-read-only` fora dela): cache gravado
  num run de PR fica com o escopo daquele PR e nenhum outro run consegue lê-lo.
- **O CI roda a suíte em três forks (`-PtestForks=3`); localmente o default continua um.** O
  **Skiko** impedia forks num runner limpo: `Library.unpackIfNeeded` extrai `skiko-windows-x64.dll`
  para `~/.skiko/<hash>/` com um `Files.move`, e no Windows esse move falha com
  `AccessDeniedException` quando outro processo já abriu o destino — todo fork tentava extrair ao
  mesmo tempo, e o segundo run do CI caiu com 41 testes de UI em `ExceptionInInitializerError`. Com
  `testForks > 1`, `extractSkikoNative` (`SkikoWarmup.kt`, `Library.load()` num processo só) roda
  **antes** do `desktopTest` e cada fork encontra o cache quente (issue #295; medido com `~/.skiko`
  apagado e 4 forks: verde). **Divergência entre verde local e vermelho no CI em teste de UI: olhe o
  `~/.skiko` antes de olhar o teste.**
  - **Teste de tela usa `ScreenTestTheme`, que é o `AppTheme` com `AppMotionPolicy.Reduced`.** Sob o
    relógio de teste toda transição finita é desenhada quadro a quadro, com as sombras de `appDepth`
    no raster de CPU: um `AppDialog` custa 2,1 s em `Static` e 0,4 s em `Reduced`, e a suíte caiu de
    ~342 s para ~224 s. Teste de **primitiva que anima** (`AppStatesTest`, `AppDialogTest`,
    `AppControlsTest`, `AppDepthTest`, `HudNotchTest`...) continua no `AppTheme` — com `Reduced` ele
    passaria sem exercitar a transição que existe para cobrir.
  - O Gradle distribui forks **por classe**, e uma classe pesada vira o caminho crítico da suíte
    paralela: `ComponentTest`, com 103 testes e 120 s no CI, terminava sozinho num fork enquanto os
    outros esperavam. Foi dividido em `ComponentTest`, `SettingsDialogContentTest` e `HistoryScreenTest`
    (~30–40 s cada). Teste de tela novo vai no arquivo da tela dele, não num arquivo genérico.
- **O recorte por path mora num job só (`changes`), e o check obrigatório é o `ci-ok`** (#344).
  Rodar 5 min de Windows por um typo no README é a lentidão que a issue #93 reclama; mas um check
  obrigatório que o filtro impede de disparar trava o PR em "Expected — Waiting for status", e por
  isso o recorte morava **dentro** de cada job, que subia runner só para anunciar **NAO EXECUTADA**
  — 9 a 10 jobs, dois deles Windows, num PR só de docs. Agora o job pulado nem sobe runner, e o
  `ci-ok` (`if: always()`) é o único check que precisa existir: `skipped` conta como sucesso. O
  `--require` do `tools/ci/test-summary.mjs` continua derrubando o job que devia rodar a suíte e não
  produziu XML — é o que faz "passou sem executar" ficar vermelho.
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
  instrumentação é **opt-in** por `-Pcoverage`, que o CI liga em todo run que executa a suíte — PR
  inclusive, desde a issue #299 —, e a mesma passada serve suíte e relatório. Sem `koverVerify` e sem piso: limiar calibrado antes de a linha de base existir é
  limiar calibrado no escuro. Linha de base de 2026-08-25: **82,7% de linhas**, 52,3% de ramos.
  `MainKt` fica fora do relatório por filtro — é o grafo de DI mais a janela, e contá-lo afunda o
  número sem apontar lacuna que se possa fechar.
- **O push na `main` roda a suíte de novo, e isso é aceito** (#344, revertendo a decisão da #299).
  A #299 pulava a suíte na `main` quando a árvore do squash batia com a de um PR verde (jobs `gate` e
  `verified-tree`, artifact `ci-verified-tree-<tree>`), e o release esperava um marcador do CI da
  `main` (`release-gate-marker` + `resolve-ci-gate`, polling de até 15 min). O conjunto somava ~350
  linhas de YAML, subia runners só para dizer "pulei" e amarrava o release a um run da `main`
  disparado pelo próprio commit de bump — o sintoma que abriu a #344. Com a versão vinda da tag e o
  `verify` próprio do release, ninguém espera pelo CI da `main`; repositório público não paga o
  minuto, e o run da `main` é o que grava o cache do Gradle.
- **Cobertura alta não é a mesma coisa que costura certa.** `RemoteTeamDataSource` está em 1,9%
  porque os testes **herdam da classe real** e sobrescrevem os 20 métodos: o nome aparece em três
  arquivos de teste e nenhuma linha de HTTP executa (issue #94). Ao ver uma classe `open` com todo
  método `open`, pergunte o que sobra dela quando o teste a substitui.
- **`choco install` detecta antes de instalar e tem retry.** Um 504 da `community.chocolatey.org`
  derrubou a `main` em 25/08 sem nenhum defeito de código. O WiX não lança ao fim: sem ele o roteiro
  pula o cenário S7 com aviso, e derrubar o job custaria os outros seis.
- **O `codeql.yml` resolvia o classpath do buildscript frio a cada run, e um 429 do Maven Central
  derrubou a `main` por isso.** Ele era o único workflow que invocava Gradle sem
  `gradle/actions/setup-gradle`: o log do run `33680756437` traz `Downloading gradle-8.6-bin.zip` e
  daemon novo, e a falha é de **configuração** — `Received status code 429` em
  `repo.maven.apache.org` para `kotlin-gradle-plugins-bom`, `kover-features-jvm` e companhia, antes
  de compilar uma linha. Não era defeito de código: o job `CI` passou no mesmo commit, com o cache do
  Gradle Home quente. A mitigação é dupla — a mesma action de cache dos outros dois workflows, mais
  retry com backoff no passo de build, pelo precedente do `choco install` acima. **O retry não cria
  "verde que não fez nada"**: há uma tarefa só, falha de configuração não compila nada e a tentativa
  seguinte compila do zero, falha de compilação é determinística, e um build up-to-date faria
  `Perform CodeQL Analysis` reprovar alto com *No source code was seen*. O preço aceito é o cache
  Linux disputar os 10 GB do repositório com o cache Windows do `ci.yml`; se aquele voltar a dizer
  `gradle cache is not found`, a saída é `cache-read-only: true` no CodeQL. **Foi aplicada na #344**:
  o repositório chegou a 11,96 GB, a maior parte gravada em ref de tag pelo `setup-java
  cache: 'gradle'` do `build-macos` (~0,5 GB por release, nunca relido). Tag e CodeQL só leem.
