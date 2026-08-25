# Atualização automática (issue #100) — Linux em user-space, opt-in

| | |
|---|---|
| **Modelo** | Claude Opus 5 — `claude-opus-5` |
| **Nível de esforço** | `max` |
| **Ferramenta** | Claude Code (CLI) |
| **Data** | 2026-08-25 |
| **Branch** | `feat/linux-auto-update-100` |
| **Autor dos commits** | `claude <claude@anthropic.com>` |

O modelo e o esforço ficam registrados porque este documento é o rastro de auditoria de um trabalho
feito por agente: o que decidiu, o que mediu e o que deixou por verificar depende de quem executou, e
uma auditoria posterior precisa saber sob quais condições as conclusões foram tiradas. Todas as
atividades desta tabela foram executadas nessas condições; qualquer continuação sob modelo ou esforço
diferente deve registrar a mudança aqui.

Plano de execução. A seção **Pontos de situação**, no fim, é atualizada a cada atividade, no mesmo
commit da atividade — é o registro de auditoria do trabalho.

## Contexto

A issue #75 pediu, com texto explícito, atualização Linux **exclusivamente em user space** via
`.tar.gz` em `~/.local/share`, sem `sudo`. Ela foi fechada pelo PR #86, que entregou **somente
Windows** (NSIS). Verificado antes de escrever este plano: nenhum dos 51 arquivos do PR #86 e nenhum
dos 11 commits citados na #100 toca código Linux, e `grep -rn LinuxAppUpdateInstaller src/` devolve
zero ocorrências.

O `.tar.gz` **já é publicado** — `usage-monitor_<versão>_linux_x64.tar.gz`, gerado em
`release-linux.yml:183`, 125.314.536 bytes na v37.0.0, raiz única `Usage Monitor/` — e **já é
classificado** como `LINUX_TARBALL` em `AppUpdateRepositoryImpl.kt:123`, com plataforma `LINUX` em
`:136`. Ninguém o consome: `WindowsAppUpdateInstaller.selectArtifact` filtra por `WINDOWS_NSIS`, e
`rememberAutoUpdateController` (`AutoUpdateController.kt:96`) constrói o instalador do Windows
incondicionalmente.

Resultado pretendido: numa instalação XDG gerenciada em Linux x86_64, o mesmo interruptor
**"Atualização automática", desmarcado por padrão**, passa a funcionar — baixa o `.tar.gz` em segundo
plano, valida o SHA-256 que vem do `digest` da API do GitHub, extrai para staging e, ao fechar o app,
troca a versão ativa por `rename(2)` e relança. Falhou, volta para a versão anterior sozinho. Em
qualquer outro Linux o interruptor continua desabilitado, **com o motivo na tela**.

## Veredito sobre o plano que estava no corpo da issue

O plano que a issue trazia é direcionalmente correto — XDG gerenciado, diretórios versionados, swap
atômico, rollback, reuso de `UpdateArtifactDownloader` e do contrato de duas fases. **Não é seguido à
risca**: dez problemas medidos contra o código deste repositório.

| # | Problema no texto da issue | Evidência no código |
|---|---|---|
| 1 | Manda "substituir a construção fixa de `WindowsAppUpdateInstaller` … em `Main.kt`" | A construção está em `AutoUpdateController.kt:96-100`; `Main.kt:458` só chama `rememberAutoUpdateController`. E `main()` está no limite do backend JVM — a análise de fluxo de controle sobre o método inteiro já estourou em `OutOfMemoryError` dentro do ASM |
| 2 | **Nenhuma feature flag** | Não cita `AUTO_UPDATE_SHIPPED` (`AutoUpdateController.kt:36`), que é o gate de build que manteve o caminho do Windows inerte até o instalador entender `/UPDATE`, nem piso de versão-alvo |
| 3 | **O ACK exige piso de versão-alvo, e o plano não tem** | O ACK é emitido pela versão **nova**. `StartupOrigin.from()` (`StartupDiagnostics.kt:28-31`) ignora argumento desconhecido: uma versão anterior ao ACK, relançada com o argumento privado, **sobe normalmente e nunca confirma** — e o script então faz rollback de um update que deu certo |
| 4 | Não diz **como** o ACK trafega | O precedente do repositório é `FocusRequestChannel`: arquivo, não socket. Socket em loopback dispara o prompt de firewall, e o carimbo vai no **conteúdo**, não em `lastModified` |
| 5 | Ignora que `launchDetachedProcess` é Windows-only | `WindowsAppUpdateInstaller.kt:210` usa `Redirect.from(File("NUL"))` — o dispositivo nulo do Windows não existe no Linux |
| 6 | Trata o SHA-256 calculado no workflow como necessário para o update | O update usa o campo `digest` da API do GitHub (`AppUpdateRepositoryImpl.normalizedSha256`), que já cobre o `.tar.gz`. O hash no workflow serve **só** ao instalador `.sh` inicial, que não fala com a API |
| 7 | Log em `$XDG_STATE_HOME/usage-monitor/update.log` | Terceiro dono de diagnóstico. `~/.usage-monitor/diagnostics/startup.jsonl` já existe, é sempre ligado e é onde alguém procura |
| 8 | Manda o script apagar o archive baixado | Duplica `shouldDiscardUpdateArtifacts` + `pruneUpdateArtifacts`, que é a correção da issue #87 (commit `751f705`) e já é agnóstica de plataforma |
| 9 | Não registra que o texto de `UNSUPPORTED_PLATFORM` fica falso | `SettingsDialogContent.kt:907-911`, PT e EN: "no Linux a instalação passa pelo gerenciador de pacotes" deixa de ser verdade |
| 10 | Sem protótipo visual e sem tabela de Pontos de situação | Os dois são obrigatórios pelo `CLAUDE.md` |

Mais um, fora da tabela porque é consequência do #8: `updates/` sob a raiz XDG, como a issue propõe,
tiraria o download de `~/.usage-monitor/updates`, que é justamente o diretório que a poda da #87
governa.

## Correções de desenho

1. **`current` é arquivo de texto com a versão, não symlink.** Com symlink, o swap atômico exige
   `mv -T` (extensão GNU, fora do POSIX): `mv current.next current`, com `current` sendo symlink para
   diretório, move o arquivo **para dentro** do diretório apontado. Arquivo regular faz `rename(2)`
   puro — atômico, POSIX, e o rollback é reescrever a versão anterior. O launcher estável
   (`~/.local/bin/usage-monitor`) vira um `sh` de quatro linhas que lê `current` e faz `exec`.
2. **Download e staging em lugares diferentes, de propósito.** O tarball continua em
   `~/.usage-monitor/updates` — onde `UpdateArtifactDownloader` baixa e onde a poda da #87 já governa;
   a extração vai para `<raizXDG>/updates/<versão>.staging`, porque a promoção para
   `versions/<versão>` é um `rename` e ele exige o **mesmo filesystem**, que os dois homes não são
   garantidamente.
3. **Log em `~/.usage-monitor/diagnostics/linux-update.log`**, ao lado do `startup.jsonl`.
4. **O script não apaga o archive** — limpa o staging e a si mesmo. Quem descarta os ~125 MB é
   `shouldDiscardUpdateArtifacts` no `LaunchedEffect` de `rememberAutoUpdateController`.
5. **`AppUpdateSupport` ganha `UNSUPPORTED_ARCHITECTURE`.** É exceção deliberada à regra "nenhum valor
   novo em enum existente" do `CLAUDE.md`: há **um** `when` exaustivo sobre ele (`autoUpdateHint`), e o
   erro de compilação é o portão que garante que o texto novo existe. Junto, `autoUpdateHint` passa a
   receber `AppUpdatePlatform` — sem isso os textos de `UNSUPPORTED_PLATFORM` e
   `UNSUPPORTED_INSTALL_ORIGIN` continuam afirmando coisas falsas no Linux.

## Escopo

**Entra:** Linux x86_64 com glibc, instalação XDG gerenciada pelo
`install-usage-monitor_<versão>_linux_x64.sh`.

**Fica de fora, declarado na UI e não escondido:**

| Caso | Comportamento |
|---|---|
| Instalação `.deb`/`.rpm` | Interruptor desabilitado com motivo (`UNSUPPORTED_INSTALL_ORIGIN`). O script detecta e **para**, sem tocar em `/opt`, `/usr` ou arquivos do gerenciador de pacotes |
| Cópia manual da pasta, `gradlew run` | Idem: sem o marcador `.usage-monitor-managed` e sem o executável rodando de dentro de `versions/`, a origem é `UNMANAGED` |
| ARM64 | Interruptor desabilitado (`UNSUPPORTED_ARCHITECTURE`) — não há artefato Linux ARM64 publicado |
| musl/Alpine, Flatpak, AppImage | Fora do escopo; caem em `UNMANAGED` |
| macOS | Continua `UNSUPPORTED_PLATFORM`, sem mudança |

**Windows não muda de comportamento.** A única alteração no caminho dele é `launchDetachedProcess`
resolvendo o dispositivo nulo por SO em vez de `NUL` fixo — e a suíte inteira é o portão disso.

## Feature flag e entrega

- **Dois interruptores, e nenhum é dispensável.** O do usuário é o `autoUpdateEnabled` que já existe
  em `PreferencesSettings` (default `false`). O de build é `LINUX_AUTO_UPDATE_SHIPPED`, que **sai em
  `false`**: com ele falso, `installer` é nulo no Linux e não existe caminho de código que extraia
  tarball ou troque diretório.
- **Entrega em PR único**, commits atômicos, um por atividade.
- A flag é ligada na **A14**, depois do aceite em máquina real. A máquina do usuário é Windows 11, e
  esse aceite fica **pendente e registrado** — não é dívida escondida.

## Arquivos

**Reuso sem mudança de comportamento:** `update/UpdateArtifactDownloader.kt` (download retomável,
SHA-256, allowlist `github.com`, timeouts por requisição, `prune`);
`domain/repository/AppUpdateInstaller.kt` (contrato de duas fases);
`presentation/viewmodel/DashboardViewModel.kt` (**zero mudança** — só consome `AppUpdateInstaller`);
`domain/entity/AppUpdateReceipt.kt` + `update/UpdateReceiptReader.kt`; `FocusRequestChannel.kt` (molde
do canal de ACK).

**Alteração:** `update/AutoUpdateController.kt`; `update/WindowsAppUpdateInstaller.kt` (só
`launchDetachedProcess`); `domain/repository/AppUpdateInstaller.kt` (valor novo em `AppUpdateSupport`);
`presentation/ui/components/SettingsDialogContent.kt`; `AutoStartManager.kt`;
`.github/workflows/release-linux.yml`; `build.gradle.kts`; `gradle/libs.versions.toml`; `README.md`;
`docs/planos/prototipo-visual-opencode.html`.

**Novo** em `src/desktopMain/kotlin/com/usagemonitor/update/`: `LinuxInstallLayout.kt`,
`LinuxInstallOrigin.kt`, `TarballExtractor.kt`, `UpdateAckChannel.kt`, `LinuxAppUpdateInstaller.kt`;
recurso `src/desktopMain/resources/update/linux-updater.sh`; instalador inicial gerado pelo workflow.

## Execução — uma atividade, um commit

| # | Atividade | Entrega |
|---|---|---|
| **A00** | Linha de base da suíte | `gradlew.bat desktopTest --rerun` (não `allTests`: cacheia e devolve `UP-TO-DATE` sem executar). Total anotado, sem commit de código |
| **A01** | Este plano + corpo da issue #100 reescrito + comentário com o diff das dez correções | doc + issue |
| **A02** | `AppUpdateSupport.UNSUPPORTED_ARCHITECTURE`; `autoUpdateHint(support, isPt, platform)`; textos PT/EN dos três motivos | + protótipo `§12 #cfg-geral` + `AutoUpdateToggleTest` |
| **A03** | `LinuxInstallLayout`: resolução XDG (**variável relativa é ignorada**, conforme a spec), raiz, `versions/`, `current`, `updates/`, marcador `.usage-monitor-managed`, launcher estável | testes: var ausente, absoluta, relativa, diretório sem permissão |
| **A04** | `LinuxInstallOrigin`: `MANAGED_XDG` / `UNMANAGED`. Gerenciada exige marcador **e** executável em execução dentro de `versions/`. Detecta caminho de `dpkg`/`rpm` | testes com leitores injetados, molde de `WindowsInstallOriginTest` |
| **A05** | Apache Commons Compress em `desktopMain` (o JDK não traz leitor de tar) + `TarballExtractor` com todas as guardas | testes: tar válido, `..`, caminho absoluto, symlink, hardlink, duplicata, fifo/device, raiz múltipla, launcher ausente, teto de 10.000 entradas, teto de 1 GiB, espaço livre, preservação de modo |
| **A06** | `UpdateAckChannel` (arquivo, carimbo no conteúdo) + o app grava o ACK **depois** do `SingleInstanceGuard`, dos recursos críticos e da composição da janela; argumento privado parseado **fora** de `StartupOrigin` | testes: ack novo, ack sobrado de sessão anterior ignorado, argumento desconhecido não altera origem |
| **A07** | `linux-updater.sh` como recurso versionado, materializado em modo `0700`, executado por `/bin/sh` com argumentos separados no `ProcessBuilder` — nenhum caminho interpolado no script | teste de materialização, modo e comando exato |
| **A08** | `LinuxAppUpdateInstaller` (`support`/`prepare`/`schedule`) + `MIN_LINUX_UPDATABLE_TARGET_VERSION` num sentinela inalcançável; `launchDetachedProcess` resolve o dispositivo nulo por SO | testes de seleção (`LINUX`+`X64`+`LINUX_TARBALL`+digest; recusa ARM64, `.deb`, `.rpm`, sem digest), staging ausente, alvo abaixo do piso, `LaunchDetachedProcessTest` com caso Linux |
| **A09** | Seleção do installer por plataforma em `rememberAutoUpdateController` + `LINUX_AUTO_UPDATE_SHIPPED = false` | `AutoUpdateWiringTest` reprovando a combinação inconsistente flag×piso, nos dois sentidos |
| **A10** | Autostart Linux aponta para o launcher estável; `ensureAutoStartCommandCurrent()` migra entrada que aponta para `versions/`. **Entrada ausente não é criada** | testes de geração e de migração |
| **A11** | `install-usage-monitor_<versão>_linux_x64.sh` gerado pelo workflow com versão, nome do tar e SHA-256 injetados; validação do tar (raiz única, tipos, `bin/Usage Monitor` em `0755`, sem links); publicação como asset | `release-linux.yml`. `artifactKindOf` devolve `null` para `.sh` e o asset é descartado — nenhuma mudança de código |
| **A12** | Harness POSIX no CI: `ubuntu-latest` + containers Arch/Fedora | job novo, com recorte por path, molde de `installer-scenarios` |
| **A13** | README (seção de atualização automática) + protótipo `§15 #checklist` com os riscos | docs |
| **A14** | **Depois do aceite real:** liga `LINUX_AUTO_UPDATE_SHIPPED` e fixa o piso na versão que sair | commit de uma linha + teste trocando de lado |

## Fluxo do `linux-updater.sh`

1. valida marcador, formato da versão, diretórios e limites — sem marcador, **aborta sem tocar nada**;
2. espera o PID anterior desaparecer, teto de 60 s; estourou → aborta com `current` intacto;
3. promove `updates/<v>.staging` → `versions/<v>` por `rename` no mesmo filesystem;
4. guarda o valor anterior de `current`;
5. escreve `current.next` e faz `mv current.next current` (arquivo regular → `rename` atômico);
6. lança o launcher estável com o argumento privado de health check;
7. espera até 60 s pelo arquivo de ACK;
8. grava recibo `status=success` **somente depois** do ACK;
9. em falha: restaura `current`, grava recibo com motivo sanitizado, relança a versão anterior;
10. remove staging e a si mesmo; **não** remove o archive baixado;
11. poda `versions/`, mantendo a atual e **uma** anterior para rollback.

Entrada de `/dev/null`, saída e erro para `~/.usage-monitor/diagnostics/linux-update.log`. O processo
filho não é esperado.

## Cenários do harness

Swap com ACK bem-sucedido; processo anterior demorando para sair; timeout de PID sem alterar
`current`; falha de launch com rollback; health timeout com rollback e relançamento da versão
anterior; diretórios com espaços; staging incompleto; falta de espaço; tentativa fora da raiz;
marcador ausente; retenção de uma versão anterior, poda e autolimpeza do script.

## Verificação

```bat
gradlew.bat desktopTest --rerun
gradlew.bat desktopTest --tests "com.usagemonitor.update.*"
gradlew.bat desktopTest --tests "com.usagemonitor.ui.*"
gradlew.bat allTests
```

No CI: harness POSIX em `ubuntu-latest` e nos containers Arch/Fedora; suíte Windows inteira verde como
portão de não-regressão do caminho NSIS.

**Roteiro de aceite real** (molde da A20 do Windows), a rodar quando houver máquina Linux — é ele que
autoriza a A14:

1. instalar pelo `install-usage-monitor_<v>_linux_x64.sh`;
2. ligar o interruptor e conferir download, SHA-256 e o estado "pronta para instalar";
3. fechar o app e conferir swap, relançamento, recibo, `.desktop` e `~/.usage-monitor/` intacto;
4. repetir pelo botão "Reiniciar e atualizar agora";
5. induzir falha de launch e conferir rollback com recibo;
6. desligar o interruptor e conferir zero downloads em duas janelas de poll.

## Riscos aceitos

- **Sem aceite em máquina real, a funcionalidade fica desligada** (`LINUX_AUTO_UPDATE_SHIPPED = false`).
  CI e containers cobrem script e extração; não cobrem sessão gráfica, `.desktop` real nem `rpm-ostree`.
- **~125 MB por versão**, ~600 MB em disco com duas versões retidas em `~/.local/share`.
- **Escopo é x86_64 com glibc e instalação XDG gerenciada.** Fora: musl/Alpine, ARM64 (sem artefato
  publicado), Flatpak, AppImage e instalações `.deb`/`.rpm`, que seguem manuais com o interruptor
  desabilitado com motivo.
- **Instalação `.deb`/`.rpm` existente exige migração manual** antes do primeiro uso.
- **O tarball não é assinado.** A integridade vem do `digest` da API do GitHub por TLS, o mesmo portão
  do Windows.

## Pontos de situação

Uma linha por atividade, escrita **no mesmo commit** da atividade. `Evidência` é o comando que rodou e
o resultado, não a intenção. Atividade que descobrir algo que muda o plano acrescenta a descoberta
aqui e ajusta a tabela de execução no mesmo commit.

A coluna `Commit` guarda o **assunto** do commit, não o hash: um commit não pode conter o próprio
hash, e preencher o hash depois quebraria a regra de escrever a linha no mesmo commit da atividade.

| # | Data | Commit | Atividade | Estado | Evidência |
|---|---|---|---|---|---|
| A00 | 2026-08-25 | — | Linha de base da suíte em `main` (`af29c4b`) | concluída | `gradlew.bat desktopTest --rerun`: BUILD SUCCESSFUL em 1m32s, **123 classes / 1310 testes / 0 falhas / 0 erros / 0 pulados**. `allTests` não serve de linha de base: cacheia e devolve `UP-TO-DATE` sem executar |
| A01 | 2026-08-25 | `docs(plan): plan the linux user-space auto update` | Este plano + issue #100 reescrita | concluída | Dez correções contra o texto original, cada uma com âncora `arquivo:linha` conferida neste worktree: `AutoUpdateController.kt:36,96`, `Main.kt:458`, `StartupDiagnostics.kt:28-31`, `WindowsAppUpdateInstaller.kt:210`, `SettingsDialogContent.kt:907-911`, `AppUpdateRepositoryImpl.kt:123,136`, `release-linux.yml:183`. `grep -rn LinuxAppUpdateInstaller src/` = 0 ocorrências. Corpo da issue substituído e comentário com o diff publicado |
| A02 | 2026-08-25 | `feat(update): tell the auto update reason per platform` | `UNSUPPORTED_ARCHITECTURE` + `autoUpdateHint` com plataforma | concluída | Valor novo em enum existente, **exceção declarada** no KDoc: há um `when` exaustivo sobre `AppUpdateSupport` (`autoUpdateHint`) e o erro de compilação é o portão. `platform` é `AppUpdatePlatform?` e `null` é *não reconhecida* — não um default em Windows: nomear o instalador errado é pior que não nomear nenhum. **Defeito encontrado pelo teste, não pela leitura:** `currentUpdatePlatform` classificava `Darwin` como Windows, porque `"darwin"` contém `"win"` e o ramo do Windows vinha primeiro; a ordem do `when` foi invertida. O `os.name` de um JDK em macOS é `Mac OS X`, então o defeito era latente — e `AutoStartManager.currentPlatform` tem a **mesma ordem** e o mesmo defeito latente, deixado onde está por ser outra decisão. `AutoUpdateToggleTest` com 3 casos novos (macOS, Linux, ARM64) e um caso dividido em dois; `CurrentUpdatePlatformTest` novo. Protótipo `§12 #cfg-geral` com as quatro variantes de motivo. `gradlew.bat desktopTest --rerun`: **124 classes / 1317 testes / 0 falhas** |
| A03 | 2026-08-25 | `feat(update): resolve the managed linux install layout` | `LinuxInstallLayout` e resolução XDG | concluída | **Os caminhos são texto POSIX, não `File(parent, child)`**: o separador do `File` é o da máquina que executa, e a suíte roda no Windows — montar com `File` produziria `C:\...\versions\39.0.0` e um contrato que nenhum teste consegue afirmar. Pela mesma razão, "absoluto" é `startsWith("/")` e não `File.isAbsolute`, que responde pela máquina errada. Variável relativa é **ignorada**, conforme a spec XDG — resolvê-la instalaria a árvore onde quer que o app tenha sido lançado. O conteúdo de `current` é validado porque vira segmento de caminho: `../..` ali apontaria a execução para fora da raiz. Permissão de diretório entra por **probe injetado** — `setWritable(false)` num diretório do Windows é inerte, e o teste real passaria sem medir nada. O launcher estável interpola a raiz na geração em vez de recalcular a regra XDG, para não haver um segundo dono dela, e passa por `quoteForPosixShell`: um apóstrofo num nome de conta basta para virar execução de comando. `gradlew.bat desktopTest --tests "…LinuxInstallLayoutTest"`: 15 testes, 0 falhas |
| A04 | 2026-08-25 | `feat(update): tell a managed linux install from any other` | `LinuxInstallOrigin` | concluída | Três condições, nenhuma bastando sozinha: marcador presente, executável em execução **dentro de `<raiz>/versions/`** e caminho fora de `/usr`//`opt`. A terceira é explícita apesar de parecer redundante — um `/opt` vindo de `.rpm` pode conviver com um marcador deixado por uma instalação XDG anterior, e nesse estado a recusa passaria a depender do formato exato do prefixo de `versions/`. **`normalizePosixPath` é textual e própria**: `File.normalize()` usa o separador da máquina que executa, e no Windows devolveria `/root/versions/../../etc` intacto — o teste que existe para provar que `..` não escapa passaria sem provar nada. A comparação é por prefixo **com barra**, então `versions-antigo/` não conta como estar dentro de `versions/`. `gradlew.bat desktopTest --tests "…LinuxInstallOriginTest"`: 13 testes, 0 falhas |
| A05 | 2026-08-25 | `feat(update): extract the linux tarball behind every guard` | Commons Compress + `TarballExtractor` | concluída | **Duas passadas, não uma**: a primeira lê o arquivo inteiro sem escrever nada e reprova tudo que não serve; só então a segunda extrai. Uma passada deixaria metade da árvore no disco antes de encontrar a entrada ruim, e staging pela metade é indistinguível de extração completa — há teste afirmando que o diretório **não existe** depois de uma recusa. Guardas: `..`, caminho absoluto, barra invertida, symlink, hardlink, FIFO, device, duplicata, segunda raiz, launcher ausente, arquivo vazio, teto de entradas, teto de expansão, espaço livre. **Descoberta que mudou o desenho:** a guarda de barra invertida não é exercitável pelo writer — o `TarArchiveEntry` do Commons Compress converte `\` em `/` ao ser construído no Windows, e o caso reprovava com a mensagem de `..`; a validação de nome virou função top-level (`normalizedTarEntryName`) atacada direto. Espaço livre e modo Unix entram por injeção: `Files.setPosixFilePermissions` **lança** no Windows, e encher o disco de quem roda a suíte não é opção. Zero de espaço livre é *não sei* e não bloqueia — `usableSpace` devolve zero para caminho inexistente e reprovaria toda primeira instalação. `gradlew.bat desktopTest --tests "…TarballExtractorTest"`: 22 testes, 0 falhas. `gradlew.bat createDistributable`: BUILD SUCCESSFUL, com `commons-compress`, `commons-io`, `commons-lang3` e `commons-codec` na imagem — módulo faltando no runtime image só aparece no app empacotado, nunca no `gradlew run` |
| A06 | 2026-08-25 | `feat(update): acknowledge a promoted linux version once it is up` | `UpdateAckChannel` e emissão do ACK | concluída | **Correção de desenho sobre o plano:** o conteúdo do ACK é o **token que o script gerou**, não um carimbo de tempo. O script inventa o token, apaga o arquivo, lança a versão nova e espera o arquivo aparecer com aquele token dentro — o ACK sobrado de outra sessão tem outro token, e nenhum token vale duas vezes. Com carimbo, o shell precisaria saber o instante do lançamento e tolerar a granularidade do sistema de arquivos. Arquivo e não socket, pelo motivo do `FocusRequestChannel`. O token vem de `argv` e vira conteúdo de arquivo: alfabeto restrito a `[A-Za-z0-9_-]{1,64}`, e token recusado **não é escrito**. O argumento é parseado **fora** de `StartupOrigin` — aquele enum responde *autostart ou manual*, e a versão promovida pode subir das duas formas; há teste afirmando que o argumento não altera a origem nos dois sentidos. O ACK sai de dentro do `Window`, depois do `SingleInstanceGuard`, dos recursos críticos e da composição: confirmar antes faria o script guardar como boa uma versão que ainda pode não abrir. `gradlew.bat desktopTest --tests "…UpdateAckChannelTest"`: 8 testes, 0 falhas |
