# Instalador único de Windows (issue #78) — plano de execução

| | |
|---|---|
| **Modelo** | Claude Opus 5 — `claude-opus-5` |
| **Nível de esforço** | não exposto ao agente nesta sessão |
| **Ferramenta** | Claude Code (CLI) |
| **Data** | 2026-08-24 |
| **Branch** | `fix/single-windows-installer-78`, criada de `origin/feat/auto-update-windows-75` |
| **PR alvo** | `feat/auto-update-windows-75` — **não** `main` |
| **Autor dos commits** | `claude <claude@anthropic.com>` |

O modelo e o esforço ficam registrados porque este documento é rastro de auditoria de trabalho feito
por agente: o que foi decidido, o que foi medido e o que ficou por verificar depende de quem
executou. Continuação sob modelo ou esforço diferente registra a mudança aqui.

A seção **Pontos de situação** é atualizada a cada atividade, **no mesmo commit da atividade**, e a
seção **Problemas em aberto e riscos** recebe toda descoberta que aparecer no caminho. As duas juntas
são o que sustenta a auditoria final da A08.

## Contexto

O release publica **dois** instaladores de Windows que gravam no **mesmo**
`%LOCALAPPDATA%\Usage Monitor`: o `UsageMonitor-Setup-<v>.exe` (NSIS, `RequestExecutionLevel user`) e
o `Usage Monitor-<v>.msi` (jpackage/WiX, `perUserInstall = true`). Não é redundância inofensiva:

1. **Quem instala pelo MSI fica permanentemente fora da atualização automática da #75.**
   `artifactKindOf` classifica o `.msi` como `WINDOWS_MSI` e `selectArtifact` só aceita
   `WINDOWS_NSIS`.
2. **Instalar o `.exe` sobre uma instalação MSI é destrutivo e silencioso.** Sem a chave HKCU do NSIS
   o `.onInit` pula direto para `done` — nenhum aviso —, o `File /r` grava por cima, os jars
   versionados do jpackage sobram (o nome carrega versão + hash, então a v38 não substitui o jar da
   v37, acompanha), e o produto MSI segue registrado sobre caminhos que o NSIS sobrescreveu.
3. **O portão de origem da #75 dá falso positivo.** `WindowsInstallOriginResolver.resolve()` autoriza
   a atualização quando o `InstallLocation` da chave NSIS é o diretório do executável em execução — e
   como os dois instaladores usam o mesmo diretório, uma instalação MSI passa como `NSIS_PER_USER`.
   Com a A19 da #75 ligada, essa máquina rodaria o `Setup.exe /S /UPDATE` sobre uma árvore gerenciada
   pelo Windows Installer.

O MSI só se justificaria para deploy em massa (Intune/GPO), que `perUserInstall = true` não atende.

**Nenhum dado de usuário corre risco em nenhum desses cenários**, e isto foi verificado e não
deduzido: `~/.usage-monitor/` (`usage-history.db`, `team.json`, `dashboard-cache.json`, backups) e as
preferências em `HKCU\Software\JavaSoft\Prefs\com.usagemonitor` ficam fora de `$INSTDIR`.

## Requisito que manda no plano

**O usuário final não faz nada.** Baixa o `UsageMonitor-Setup-<v>.exe`, executa, e funciona — com MSI
por baixo, com chave NSIS órfã, com as duas ou com nenhuma. Nenhum comando, nenhuma desinstalação
prévia, nenhuma pergunta que ele não tenha como responder.

Daí duas restrições sobre tudo o que vem abaixo:

1. **As atividades A03 a A07 saem na mesma release.** Commits atômicos, mas **nenhuma tag entre
   elas**. Parar de publicar o `.msi` sem a remoção automática é **pior que o estado atual**: empurra
   todo usuário de MSI para o `.exe` que grava por cima sem aviso.
2. **Toda pergunta no `.onInit` é candidata a defeito.** O `MessageBox` de "já está instalado, deseja
   remover?" sai: ele nunca ofereceu escolha real.

## Estado medido na máquina de desenvolvimento (2026-08-24)

Levantado por `reg query`, `Get-ItemProperty` e inspeção de disco.

| Chave | Versão | Origem | Observação |
|---|---|---|---|
| `HKCU\...\Uninstall\Usage Monitor` | 23.0.0 | NSIS (órfã) | `UninstallString` → `...\Usage Monitor\Uninstall.exe` — **o arquivo não existe** |
| `HKCU\...\Uninstall\{845948FC-4664-31DD-92E1-4261C88FE6BF}` | 37.0.0 | MSI (ativa) | `MsiExec.exe /X{845948FC-…}`, `InstallLocation` na mesma pasta |

- Pasta contém só `app\`, `runtime\`, `Usage Monitor.exe`; jar `usage-monitor-desktop-37.0.0-8ba13211….jar`. Sem `Uninstall.exe`.
- `HKCU\Software\Microsoft\Installer\UpgradeCodes\97B4C62DB2F95EC49BE42E6E2A9A4E4A` → ProductCode
  empacotado `CF8495484664DD13291E24168CF86EFB` = `{845948FC-…}`. **A detecção por
  `MsiEnumRelatedProductsW` da A05 funciona nesta máquina.**
- NSIS 3.x com `Plugins\x86-unicode\System.dll` presente; `msi.dll` em `System32`.
- **WiX não está instalado** (`$env:WIX` vazio, nenhum `candle.exe`) — ver risco **R1**.
- `C:\Program Files\Claude Usage Monitor` também existe, mas é **outro produto** (app Electron de
  190 MB). Fora do escopo, e não confundir numa auditoria futura.

## Base da branch

A issue #78 foi escrita contra `origin/feat/auto-update-windows-75` (issue #75, aberta, não
mergeada). O `main` **não tem** `WindowsInstallOrigin.kt`, `WindowsAppUpdateInstaller.kt`,
`AppUpdateArtifactKind` nem `src/installer/test/Invoke-UpdateScenarios.ps1`; os números de linha
citados na issue (`UsageMonitor.nsi:96,178,283,354`) são da branch, não do `main`, onde o `.nsi` tem
**um** `!ifndef` e nenhum modo `/UPDATE`.

O rebase da #75 sobre o `main` (que avançou ~10 commits visuais) é trabalho da #75 e **não entra
aqui** — ver risco **R5**.

---

## Execução — uma atividade, um commit

| # | Atividade | Entrega | Commit |
|---|---|---|---|
| **A00** | Linha de base da suíte na branch base, sem edição nenhuma | total anotado nos Pontos de situação | — (sem commit) |
| **A01** | Este plano em `docs/planos/instalador-unico-windows-execucao.md` | o documento | `docs(plan): add the single windows installer plan` |
| **A02** | Limpar a máquina de desenvolvimento e **medir** a remoção do MSI | seção *Medições* preenchida + Pontos de situação | `docs(installer): record the measured msi removal semantics` |
| **A03** | Parar de publicar o MSI | `build.gradle.kts`, workflow, skills, `CLAUDE.md`, `README.md`; apagar `patch-msi-launch.ps1` | `build(packaging): ship a single Windows installer` |
| **A04** | Portão de origem passa a exigir o `Uninstall.exe` | `WindowsInstallOrigin.kt` + teste | `fix(update): require the NSIS uninstaller to authorize automatic updates` |
| **A05** | Remoção do MSI por baixo no `.onInit` + quinto `!ifndef` | `src/installer/UsageMonitor.nsi` | `feat(installer): remove a previous MSI install before installing` |
| **A06** | Fim do `MessageBox` e guarda da chave NSIS órfã | `src/installer/UsageMonitor.nsi` | `fix(installer): stop asking about a stale previous install` |
| **A07** | Cenários S7 e S8 + `.wxs` mínimo + WiX no CI | `src/installer/test/`, `.github/workflows/ci.yml` | `test(installer): cover the msi takeover scenarios` |
| **A08** | Verificação final consolidada e leitura de auditoria | Pontos de situação + *Desvios e achados* | `docs(plan): close the single installer execution` |

A ordem **A05 antes de A06** não é arbitrária: a A06 remove o `RMDir /r` do caminho da chave órfã, e
esse `RMDir` só pode sair depois que a A05 garantir que os arquivos já foram removidos pelo
`msiexec`. Invertida, a A06 sozinha deixaria a árvore do MSI intacta debaixo do `File /r`.

---

## A02 — as quatro medições

Rodam **antes** do código da A05, e enquanto `TargetFormat.Msi` ainda existe na branch. O sujeito é o
artefato real: `Usage.Monitor-37.0.0.msi` do release `v37.0.0`
(`gh release download v37.0.0 -p '*.msi'`). WiX não está instalado aqui, então não há como gerar um
MSI sintético localmente — usar o publicado é o caminho fiel e disponível, e com a máquina limpa ele
é descartável.

### A02.0 — limpeza da máquina de desenvolvimento

**Não faz parte do produto e nenhum usuário faz isto.** Existe porque esta máquina está com a
instalação dupla descrita acima, e é o que zera o terreno para as medições. Ação destrutiva sobre a
instalação atual; dados preservados.

```
taskkill /IM "Usage Monitor.exe" /F
msiexec /x {845948FC-4664-31DD-92E1-4261C88FE6BF} /qb
reg delete "HKCU\Software\Microsoft\Windows\CurrentVersion\Uninstall\Usage Monitor" /f
rmdir /s /q "%LOCALAPPDATA%\Usage Monitor"
```

Conferir depois: `~/.usage-monitor/usage-history.db` intacto e
`HKCU\Software\JavaSoft\Prefs\com.usagemonitor` com os valores.

### As sondas

Este repositório já pagou caro por presumir semântica de instalador — a A02 da #75 existe por isso.
Nenhum dos quatro pontos foi medido, e nenhum pode ser deduzido.

| # | Sonda | O que decide |
|---|---|---|
| **M1** | Instalar o MSI, **abrir o app**, rodar `msiexec /x <pc> /qn REBOOT=ReallySuppress` | Se o Restart Manager fecha o app sob `/qn`. Retorno `3010` = agendou para o reboot e os arquivos ficaram. **É esta sonda que decide se a A05 precisa de passo próprio para fechar o app** antes do `File /r`: a seção de instalação não tem `taskkill` (só a de desinstalação tem), e a #75 registra que `taskkill /F` durante escrita do SQLite é pior que não atualizar. Sem M1 não há como escolher entre "o Restart Manager já resolveu", "pedir fechamento gracioso e esperar" e "abortar com recibo" |
| **M2** | Cronometrar o `/x /qn`; repetir com o mutex `_MSIExecute` tomado por outra instalação | `ExecWait` não tem timeout: mede o risco de pendurar o instalador |
| **M3** | Listar `%LOCALAPPDATA%\Usage Monitor` depois do `/x` | Se sobra resíduo, a A05 precisa de `RMDir /r "$INSTDIR"` entre a remoção e o `File /r`, senão volta o problema dos jars órfãos |
| **M4** | Sentinelas em `~/.usage-monitor/probe.txt` e num valor novo sob `HKCU\Software\JavaSoft\Prefs\com.usagemonitor`, conferidas após o `/x` | Prova que o dado do usuário sobrevive. O raciocínio diz que sim — o MSI só remove componentes que registrou —, mas é a afirmação que mais importa e merece medição |

Resultados vão numa seção **Medições (A02)** deste documento, no formato da A02 da #75: pergunta,
resultado medido, e o comando que rodou.

`docs/planos/atualizacao-automatica-windows-execucao.md` **não é tocado** — é registro histórico da
#75.

---

## Medições (A02) — o que foi medido, 2026-08-24

Sujeito: `Usage.Monitor-37.0.0.msi` do release `v37.0.0` (121.155.584 bytes), instalado e removido
nesta máquina. ProductCode `{845948FC-4664-31DD-92E1-4261C88FE6BF}`.

### Resultados

| # | Pergunta | Resultado medido |
|---|---|---|
| **M1** | O Restart Manager fecha o app sob `msiexec /x /qn`? | **Não.** Com o app rodando: exit **3010**, 6,2 s, os **dois** processos continuaram vivos e **69 arquivos ficaram** na pasta. Com o app fechado, duas passadas: exit **0**, 2,4 s e 2,1 s, **pasta removida por completo**. A diferença é o desenho da A05 |
| **M1b** | `taskkill` gracioso (sem `/F`) fecha o app? | **Não.** Sinal enviado aos dois processos, `taskkill` devolveu 0, e **1 dos 2 sobreviveu mais de 20 s**. `taskkill /F /IM "Usage Monitor.exe"` encerrou em **0,1 s** |
| **M2** | `ExecWait` pendura com o mutex `_MSIExecute` tomado? | **Não trava.** Com uma instalação MSI em curso, a segunda operação **esperou 1,98 s e completou** (exit 1605). A espera é limitada pela duração da operação concorrente — que não é limitada em geral, mas não é espera infinita |
| **M3** | O que sobra na pasta depois do `/x`? | **Nada**, quando o app está fechado (duas passadas). **69 arquivos**, quando o `/x` devolve 3010 |
| **M4** | `~/.usage-monitor/` e as preferências sobrevivem? | **Sim, provado.** Sentinelas de arquivo e de registro intactas após o `/x`; `usage-history.db` em 10.702.848 bytes antes e depois; `team.json` com o mesmo SHA-256 (`C9894D19…6DB6CF`); 13 arquivos e 38 valores de preferência antes e depois |
| **M5** | Estado do registro depois de um 3010 | A entrada de ARP do MSI **some** e o vínculo em `HKCU\Software\Microsoft\Installer\UpgradeCodes\…` é **apagado** — com os 69 arquivos ainda no disco. Sonda não prevista, e é a que mais muda o plano |

### O que isso muda na A05

1. **`taskkill /F /IM "Usage Monitor.exe"` antes do `msiexec`.** O Restart Manager não fecha o app e o
   fechamento gracioso também não; só o forçado fecha, e em 0,1 s. Há precedente no próprio arquivo:
   a seção de desinstalação já faz isso. **Não replicar** o `taskkill /F /IM java.exe` que está ao
   lado dele — aquele mata toda JVM da máquina, incluindo daemons do Gradle e IDEs de quem instala.
2. **`3010` deixa de ser código aceitável.** O plano original o aceitava junto de `0` e `1605`; medido,
   ele significa "removi o registro e deixei os arquivos", que é exatamente o estado que o `File /r`
   não pode encontrar. Aceitos: `0` e `1605`.
3. **Detecção por UpgradeCode sozinha não basta** (M5). Depois de um 3010 o produto está
   desregistrado e `MsiEnumRelatedProductsW` devolve 259, com 69 arquivos ainda na pasta — o
   instalador concluiria "não há MSI" e gravaria por cima do lixo. A A05 ganha uma **segunda guarda**:
   se `$INSTDIR` existe e **não** contém `Uninstall.exe`, aquela árvore não foi escrita pelo NSIS e é
   apagada antes do `File /r`. É o mesmo sinal que a A04 usa para o portão de origem, e cobre também
   a cópia manual de pasta.

### Armadilha de ambiente encontrada

A primeira passada de M1 foi **inválida** e não por causa do MSI: o app subia e morria em ~5 s, com
exit 0 e sem log. Causa medida — **um daemon do Gradle retinha `~/.usage-monitor/app.lock`**, e o
`SingleInstanceGuard` derruba a instância nova em silêncio. `gradlew.bat --stop` liberou o lock e o
app passou a ficar de pé. Consequência prática, registrada como **R11**: rodar a suíte deixa o app
instalado impossível de abrir até o daemon morrer.

---

## A03 — parar de publicar o MSI

| Arquivo | Mudança |
|---|---|
| `build.gradle.kts` | remover `TargetFormat.Msi` de `targetFormats`; remover a task `patchMsiLaunch` e o comentário dela. **Manter `upgradeUuid`**, com comentário novo: não gera mais MSI, o GUID fica como referência do UpgradeCode das instalações legadas que o `.nsi` remove na A05 |
| `patch-msi-launch.ps1` | apagar — só existe para o `patchMsiLaunch` |
| `.github/workflows/release-linux.yml` | build step → `.\gradlew.bat packageInstaller`; remover o gate `-VerifyOnly`; remover o `Copy-Item` dos `*.msi`; remover `$version` se ficar órfã no passo |
| `.claude/skills/usage-monitor-release/SKILL.md` | tirar `packageMsi patchMsiLaunch` do comando e o `.msi` da lista de artefatos esperados |
| `.codex/skills/usage-monitor-release/SKILL.md` | tirar o `.msi` da lista |
| `CLAUDE.md` (§Empacotamento) | `TargetFormat.Exe`/`Msi` → só `Exe`, **com o motivo registrado**: os dois gravavam no mesmo `%LOCALAPPDATA%\Usage Monitor` e o MSI nunca poderia se auto-atualizar |
| `README.md` (§Build e distribuicao) | lista de `TargetFormat` atualizada + seção nova **Instalação no Windows** (instalador único e migração de quem está no MSI) |

**O que NÃO muda, e por quê**

- **`AppUpdateArtifactKind.WINDOWS_MSI` fica.** As releases já publicadas carregam `.msi`, e é esse
  valor que faz `artifactKindOf` reconhecê-lo para `selectArtifact` **descartá-lo**. Remover valor de
  enum quebra o `when` exaustivo de `platformOf`.
- **Os testes que citam MSI ficam** (`AppUpdateRepositoryImplTest`, `WindowsAppUpdateInstallerTest`):
  são a regressão que garante que um `.msi` nunca vira candidato a instalador.
- **`UNSUPPORTED_INSTALL_ORIGIN`** em `SettingsDialogContent.kt` continua correto — instalações MSI
  existentes não somem por deixarmos de publicar o formato.
- **`atualizacao-automatica-windows-execucao.md` não é reescrito.**
- **Nada no protótipo visual:** não há tela envolvida.

---

## A04 — fechar o falso positivo do portão de origem

Arquivo: `src/desktopMain/kotlin/com/usagemonitor/update/WindowsInstallOrigin.kt`.

Parar de publicar o MSI **não** apaga as instalações MSI que já existem. Enquanto o portão puder
dizer `NSIS_PER_USER` para uma delas, a A19 da #75 continua com o caminho destrutivo aberto.

- `resolve()` ganha um quarto parâmetro `hasNsisUninstaller: Boolean`. `NSIS_PER_USER` passa a exigir
  **três** condições: chave presente, `installLocation` == diretório do executável, e o desinstalador
  presente.
- `current()` calcula o parâmetro com `File(installLocation, "Uninstall.exe").isFile`. Checagem de
  **sistema de arquivos**, não de registro: não abre processo `reg.exe`, preservando a razão da A06
  da #75 para não varrer o banco do Windows Installer ("lento e frágil"). `resolve()` continua pura —
  o resultado do `isFile` entra como parâmetro, no mesmo desenho de `installLocation` e
  `executableCandidates`.
- O app-image do jpackage não tem `Uninstall.exe`; desinstalação de MSI é por `msiexec`.

Teste em `src/desktopTest/kotlin/com/usagemonitor/update/WindowsInstallOriginTest.kt`: chave
presente, executável no diretório certo, **sem** desinstalador → `UNMANAGED`. É o caso desta máquina,
que hoje passaria como `NSIS_PER_USER`. **Confirmar que o teste reprova** com a condição removida,
para não passar por acaso.

---

## A05 — remover o MSI por baixo, sem interação

Arquivo: `src/installer/UsageMonitor.nsi`.

**Por que A03 e A04 não bastam.** Parar de publicar o `.msi` só afeta releases futuras; quem já
instalou por ele — todas as versões até a v37 — continua com aquele registro vivo. E o README não
intercepta quem clica duas vezes no instalador. O tratamento tem de estar no instalador.

### Quinto `!ifndef`

```nsis
!ifndef MSI_UPGRADE_CODE
!define MSI_UPGRADE_CODE "{D26C4B79-9F2B-4CE5-B94E-E2E6A2A9E4A4}"
!endif
```

Ao lado de `PRODUCT_NAME` e `AUTO_START_VALUE_NAME`. Sem ele, um cenário de teste rodando com o
UpgradeCode de produção **desinstalaria o MSI real da máquina de quem roda a suíte** — o acidente já
ocorrido duas vezes na A16 da #75, com a chave `Run` e com o atalho do Menu Iniciar.

### Onde

No `.onInit`, no ramo que **não** é `/UPDATE` (o `${If} $UpdateMode == 1 … Return` continua saindo
antes) e **antes** de qualquer escrita. O caminho de atualização nunca deve encontrar um MSI — o
portão da A04 garante isso — e manter `msiexec` fora do fluxo silencioso evita um `ExecWait` novo
ali. A ordem importa porque os dois instaladores usam a mesma pasta: instalar primeiro e desinstalar
depois faria o `msiexec /x` apagar o que o NSIS acabou de gravar.

### Detecção, sem adivinhar GUID

```nsis
System::Call 'msi::MsiEnumRelatedProductsW(w "${MSI_UPGRADE_CODE}", i 0, i $R2, w .r1) i .r0'
; $0 = 0   -> $1 = ProductCode
; $0 = 259 (ERROR_NO_MORE_ITEMS) -> nao ha MSI instalado
```

Índice em laço — pode haver mais de um produto relacionado.

### Fechar o app primeiro — decidido por M1/M1b, não presumido

```nsis
ExecWait '"$SYSDIR\taskkill.exe" /F /IM "Usage Monitor.exe"' $3
```

O Restart Manager **não** fecha o app sob `/qn` (M1: exit 3010, app vivo, 69 arquivos no lugar) e o
`taskkill` gracioso também não (M1b: 1 de 2 processos sobrevive a 20 s). Só o forçado fecha, em 0,1 s.
A seção de desinstalação deste mesmo arquivo já usa esse comando — o precedente existe. **Não
replicar** o `taskkill /F /IM java.exe` que está ao lado dele: aquele mata toda JVM da máquina.
Código de retorno não é falha: `128` significa apenas que não havia processo.

### Remoção

```nsis
ExecWait '"$SYSDIR\msiexec.exe" /x $1 /qn REBOOT=ReallySuppress' $2
```

`/qn` é silêncio total, e o MSI é per-user, então não há UAC (ver risco **R7**). Aceitos **`0`** e
**`1605`** (produto ausente). **`3010` é falha**, ao contrário do que a issue propunha: medido, ele
significa "registro removido, arquivos mantidos" — exatamente o estado que o `File /r` não pode
encontrar.

### Segunda guarda: árvore estranha sem registro (M5)

Detectar pelo UpgradeCode não basta. Depois de um 3010 o produto fica desregistrado e o vínculo de
UpgradeCode é apagado, com os arquivos ainda no disco: `MsiEnumRelatedProductsW` devolve 259 e o
instalador concluiria que não há nada a remover.

Por isso, **independentemente de ter achado MSI**: se `$INSTDIR` existe e **não** contém
`Uninstall.exe`, aquela árvore não foi escrita pelo NSIS e é apagada antes do `File /r`. É o mesmo
sinal que a A04 usa no portão de origem, e cobre de quebra a cópia manual de pasta.

### Política de falha

A instalação **para**. Continuar produziria exatamente a instalação dupla que o tratamento existe
para evitar. Isso não devolve decisão ao usuário — ele não escolhe nada, só fica sabendo que não deu.
Falhar visível é melhor que corromper invisível.

Padrão de parada, já medido e documentado no cabeçalho do próprio `.nsi` (linhas 32-34):
`MessageBox … /SD IDOK` (auto-responde sob `/S`, não bloqueia) → `Call WriteUpdateReceipt` com
`reason=msi-removal-failed` → `SetErrorLevel 4` → `Quit`. **Nunca** `MessageBox` bloqueante sob `/S`:
é a armadilha medida na A02 da #75.

O desenho do "app rodando" saiu de M1/M1b e está acima, não é mais uma pendência.

---

## A06 — fim do `MessageBox` e guarda da chave órfã

Arquivo: `src/installer/UsageMonitor.nsi`, bloco `notdone:`/`uninst:` do `.onInit`.

Ordem final dentro do `.onInit`, e é ela o coração do requisito:

```
1. remover MSI relacionado (silencioso, sem perguntar)   <- A05
2. tratar a chave NSIS anterior                          <- A06
3. instalar
```

| Estado | Hoje | Depois |
|---|---|---|
| Sem chave NSIS (usuário só de MSI — a maioria) | pula para `done` **sem aviso**, grava por cima do MSI | o passo 1 já removeu o MSI; instala limpo |
| Chave NSIS com `Uninstall.exe` **existente** | pergunta | `ExecWait '$0'`, `DeleteRegKey`, `RMDir /r`, sem perguntar |
| Chave NSIS com `Uninstall.exe` **inexistente** (Caso B — esta máquina) | pergunta; "Sim" roda `ExecWait` num arquivo que não existe, falha em silêncio, e o `RMDir /r` apaga a árvore que o Windows Installer registra | `IfFileExists` reconhece a chave como lixo: `DeleteRegKey`, **sem** `ExecWait` e **sem** `RMDir` — o passo 1 já cuidou dos arquivos |

O `MessageBox` sai. Ele nunca ofereceu escolha real: "Não" produz instalação dupla, e "Sim" só é
seguro quando o desinstalador existe — coisa que o instalador sabe checar melhor que o usuário.

---

## A07 — cenários S7 e S8

Em `src/installer/test/Invoke-UpdateScenarios.ps1`. São os dois casos do §2.1 da issue, e nenhum
deles pode exigir interação — asserção implícita, já que o roteiro roda tudo com `/S`.

- **S7 — Caso A (só MSI):** instala um MSI de teste, roda o `.exe`, confere **uma** entrada em ARP,
  pasta sem resíduo (nenhum arquivo da versão anterior) e sentinelas de dados preservadas.
- **S8 — Caso B (chave NSIS órfã):** instala o MSI de teste, escreve à mão a chave HKCU do cenário
  apontando para um `Uninstall.exe` inexistente, roda o `.exe`. Espera: **uma** entrada em ARP com a
  `DisplayVersion` nova e a pasta com o payload novo.

Detalhes:

- MSI de teste gerado de um `.wxs` mínimo novo em `src/installer/test/` — um componente, per-user,
  **UpgradeCode próprio** —, compilado com `candle`/`light`. Sem WiX o cenário **pula com aviso
  alto**, em vez de falhar (risco **R1**).
- `.github/workflows/ci.yml`: acrescentar `choco install wixtoolset -y` ao lado do
  `choco install nsis -y`, para o S7/S8 rodarem de fato no CI.
- O `Build-Installer` do roteiro passa `/DMSI_UPGRADE_CODE=<código do cenário>` junto dos quatro
  `/D` que já passa.
- Conferir **depois da execução** que o MSI real da máquina continua intacto: é o que prova que o
  quinto `!ifndef` isolou o cenário.

---

## Migração manual (documentar no README como alternativa)

**Não é o caminho do usuário.** Com a A05 e a A06 no lugar, isto é o que se faz quando a remoção
automática falhar (o instalador diz que falhou, com o motivo), ou para quem prefere limpar à mão. Se
este procedimento virar o caminho normal, a A05 não cumpriu o objetivo.

1. Fechar o Usage Monitor.
2. `msiexec /x {ProductCode}` — a entrada cujo desinstalador é `MsiExec.exe`.
3. `reg delete "HKCU\Software\Microsoft\Windows\CurrentVersion\Uninstall\Usage Monitor" /f` — é a
   chave órfã que produz o falso positivo.
4. Conferir que `%LOCALAPPDATA%\Usage Monitor` sumiu; se sobrou, apagar à mão.
5. Instalar `UsageMonitor-Setup-<versão>.exe`.
6. Reconferir "Iniciar com o Windows" nas Configurações.

Os dados não se perdem em nenhum passo.

---

## Verificação

1. `gradlew.bat allTests`. O teste novo de `WindowsInstallOriginTest` discrimina a A04; confirmar que
   ele **reprova** com a condição do desinstalador removida.
2. `gradlew.bat tasks --all | findstr /i msi` — nenhuma task de MSI restante.
3. `gradlew.bat packageInstaller` — o NSIS continua saindo em
   `build/installer/UsageMonitor-Setup-<versão>.exe`.
4. As quatro medições da A02, registradas **antes** do código da A05.
5. `powershell -File src\installer\test\Invoke-UpdateScenarios.ps1` — os seis cenários existentes
   verdes, mais S7 e S8 (ou o SKIP explícito sem WiX). Conferir depois que nenhum MSI real da máquina
   foi tocado.
6. Parser YAML sobre `.github/workflows/release-linux.yml` e `ci.yml` após as edições.
7. Por leitura: o `dist-build` do job `build-windows` recebe **um** arquivo Windows e o
   `fail_on_unmatched_files: true` do `publish-release` continua satisfeito.
8. A prova real do pipeline só sai na primeira tag publicada depois da mudança. **Registrar isso**,
   em vez de afirmar que o release foi validado.

---

## Pontos de situação

Uma linha por atividade, escrita **no mesmo commit** da atividade. `Evidência` é o comando que rodou
e o resultado, não a intenção. Atividade que descobrir algo que muda o plano acrescenta a descoberta
aqui, ajusta a tabela de execução e registra o risco na seção seguinte — tudo no mesmo commit.

A coluna `Commit` guarda o **assunto** do commit, não o hash: um commit não pode conter o próprio
hash. `git log --grep` recupera o commit pelo assunto.

| # | Data | Commit | Atividade | Estado | Evidência |
|---|---|---|---|---|---|
| A00 | 2026-08-24 | — | Linha de base da suíte na branch base (`3792192`) | concluída | `gradlew.bat desktopTest --rerun`: BUILD SUCCESSFUL em 2m46s. Agregado dos XML de `build/test-results/desktopTest`: **114 classes / 1221 testes / 0 falhas / 0 erros / 0 pulados** — o mesmo total registrado na A14 da #75, o que confirma que a branch está no estado esperado |
| A01 | 2026-08-24 | `docs(plan): add the single windows installer plan` | Este plano em `docs/planos/` | concluída | Este arquivo. Nove atividades atômicas, tabela de Pontos de situação e tabela de Problemas em aberto com 10 riscos já identificados. **Divergência deliberada da issue #78**, que pedia "sem tabela de pontos de situação em `docs/planos/`": o registro por atividade foi exigido explicitamente pelo autor do repositório, para viabilizar auditoria completa ao final |
| A02 | 2026-08-24 | `docs(installer): record the measured msi removal semantics` | Limpeza da máquina + medições do MSI | concluída | Seção *Medições (A02)*. **Três premissas do plano caíram.** (1) O Restart Manager **não** fecha o app sob `/qn`: com o app rodando, `msiexec /x` devolveu **3010** em 6,2 s, os dois processos sobreviveram e **69 arquivos ficaram** na pasta; com o app fechado, duas passadas deram exit 0 em 2,4 s e 2,1 s com a pasta **removida por completo**. (2) `taskkill` gracioso **não** fecha — 1 de 2 processos sobreviveu a 20 s; `taskkill /F` encerrou em 0,1 s. (3) Sonda não prevista (**M5**): depois de um 3010 a entrada de ARP **some** e o vínculo de UpgradeCode é **apagado** com os arquivos no disco, então detecção por UpgradeCode sozinha não vê o lixo — daí a segunda guarda por ausência de `Uninstall.exe`. `ExecWait` **não** trava com o mutex tomado: a segunda operação esperou 1,98 s e completou (1605). Dados preservados e **provados**: `usage-history.db` em 10.702.848 bytes antes e depois, `team.json` com o mesmo SHA-256, 13 arquivos e 38 valores de preferência antes e depois. Primeira passada de M1 foi **inválida** por um daemon do Gradle reter `~/.usage-monitor/app.lock` (R11). Máquina deixada limpa: nenhuma entrada em ARP, nenhuma pasta de instalação |
| A03 | 2026-08-24 | `build(packaging): ship a single Windows installer` | Parar de publicar o MSI | concluída | `TargetFormat.Msi` fora, task `patchMsiLaunch` e `patch-msi-launch.ps1` apagados, workflow reduzido a `.\gradlew.bat packageInstaller`. **O passo `Extract version` do `build-windows` saiu junto**: o único leitor dele naquele job era o gate `-VerifyOnly` do MSI, e o nome do `Setup.exe` vem da versão do Gradle. **`upgradeUuid` ficou**, com comentário dizendo por quê — é o UpgradeCode que a A05 usa para achar as instalações MSI existentes. Verificações: `gradlew.bat tasks --all` não lista **nenhuma** task com "msi"; `yaml.safe_load` passou nos dois workflows; `gradlew.bat packageInstaller` gerou `UsageMonitor-Setup-37.0.0.exe` com 122.308.160 bytes e **não** criou `build/compose/binaries/main/msi`. Uma falha intermediária do `createDistributable` (`Unable to delete directory … app`) era arquivo travado de build anterior, não a mudança — resolvida apagando o diretório |
| A04 | 2026-08-24 | `fix(update): require the NSIS uninstaller to authorize automatic updates` | Portão de origem exige o `Uninstall.exe` | concluída | `resolve()` ganhou `hasNsisUninstaller` **sem valor default**, de propósito: com default os dez testes existentes continuariam compilando sem dizer nada, e o novo passaria por acaso. `current()` calcula por `File(installLocation, "Uninstall.exe").isFile` — sistema de arquivos, não `reg.exe`, preservando a razão da A06 da #75. **Falsificação:** com a guarda `if (!hasNsisUninstaller)` removida, `gradlew.bat desktopTest --tests "…WindowsInstallOriginTest"` deu **11 testes, 1 falha**, e a falha foi exatamente `the registered location without the NSIS uninstaller is unmanaged` — o teste discrimina. Restaurada a guarda: `gradlew.bat desktopTest --rerun` deu **114 classes / 1222 testes / 0 falhas** (linha de base 1221 + 1) |
| A05 | — | — | Remoção do MSI por baixo no `.onInit` | pendente | — |
| A06 | — | — | Fim do `MessageBox` e guarda da chave órfã | pendente | — |
| A07 | — | — | Cenários S7 e S8 + WiX no CI | pendente | — |
| A08 | — | — | Verificação final e leitura de auditoria | pendente | — |

---

## Problemas em aberto e riscos

Preenchida na A01 com o que já se sabe, e **acrescida a cada atividade** que descobrir algo. `Estado`
é `aberto`, `medido`, `fechado` ou `aceito`. É esta tabela, junto dos Pontos de situação, que sustenta
a auditoria final da A08.

| # | Risco / problema | Evidência hoje | Fecha em | Estado |
|---|---|---|---|---|
| **R1** | WiX não está instalado nesta máquina (`$env:WIX` vazio, nenhum `candle.exe`) — S7/S8 não rodam localmente | verificado por `Test-Path` em 2026-08-24 | A07 (SKIP local + `choco install wixtoolset` no CI) | aberto |
| **R2** | `msiexec /x /qn` pode devolver `3010` e deixar os arquivos para o reboot, com o `File /r` gravando por cima deles | **medido em 2026-08-24: acontece.** Exit 3010, 69 arquivos mantidos | A05 (`3010` vira falha; app fechado antes) | medido |
| **R3** | `ExecWait` não tem timeout; com o mutex `_MSIExecute` tomado o instalador fica pendurado | **medido: não trava.** Segunda operação esperou 1,98 s e completou (1605). A espera é a duração da operação concorrente, que não é limitada em geral | — | aceito |
| **R4** | App em execução durante a remoção e durante o `File /r`; a seção de instalação não tem `taskkill`, e `taskkill /F` durante escrita do SQLite é pior que não atualizar | **medido:** Restart Manager não fecha; `taskkill` gracioso não fecha (1 de 2 sobrevive a 20 s); `taskkill /F` fecha em 0,1 s | A05 (`taskkill /F` só do executável do app, nunca de `java.exe`) | medido |
| **R5** | A branch base está ~10 commits atrás do `main`, com conflito garantido em arquivos visuais | `git log main..origin/feat/auto-update-windows-75` | fora deste escopo — é da #75 | aceito |
| **R6** | O pipeline de release só é provado na primeira tag publicada após a mudança | por construção | — | aceito |
| **R7** | Se algum usuário instalou o MSI elevado (per-machine em vez de per-user), o `/x` exigiria UAC e o `/qn` falharia | não medido; o `build.gradle.kts` sempre usou `perUserInstall = true`, mas isso não prova o que há na máquina alheia | A02 (registrar) / A05 (código de erro vira falha visível) | aberto |
| **R8** | Sintaxe do `System::Call` para `MsiEnumRelatedProductsW` (buffer de saída em `w .r1`) só é validada ao compilar e executar | plugin `System.dll` presente; chamada não exercitada | A05 | aberto |
| **R9** | Quantos arquivos o `msiexec /x` remove depois de o NSIS ter sobrescrito os mesmos caminhos — a "bomba-relógio" do §2.1 da issue | **parcialmente medido:** com o app fechado a remoção é total (zero resíduo), então o `File /r` posterior escreve numa pasta limpa. A ordem "remover, depois instalar" desarma a bomba por construção | A05 (ordem) + segunda guarda | medido |
| **R10** | A A03 sozinha numa release é uma regressão; depende de disciplina de tag, não de código | — | A08 (conferir que A03–A07 estão na mesma tag) | aberto |
| **R11** | Um daemon do Gradle retém `~/.usage-monitor/app.lock`; com ele preso o app instalado sobe e morre em ~5 s, com exit 0 e sem log — o `SingleInstanceGuard` derruba a instância nova em silêncio | **medido em 2026-08-24**: `gradlew.bat --stop` libera o lock e o app passa a ficar de pé. Invalidou a primeira passada de M1 | fora deste escopo — defeito próprio, merece issue | aberto |
| **R12** | Depois de um `3010` o produto MSI fica **desregistrado** e o vínculo de UpgradeCode é apagado, com os arquivos ainda no disco: detecção por `MsiEnumRelatedProductsW` devolve 259 e não vê o resíduo | **medido (M5)** | A05 (segunda guarda: `$INSTDIR` sem `Uninstall.exe` é apagado antes do `File /r`) | medido |

---

## Desvios do plano e achados da execução

Preenchida na A08, no molde da seção equivalente da #75: o que a execução descobriu e o plano não
previa, com as medições que contradisseram premissas, os defeitos que só apareceram ao rodar, os
ajustes de estrutura e o que ficou por verificar.
