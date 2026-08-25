# Arranque no logon: foco de instância, diagnóstico e latência — plano de execução

| | |
|---|---|
| **Modelo** | Claude Opus 5 — `claude-opus-5` |
| **Nível de esforço** | não exposto ao agente nesta sessão |
| **Ferramenta** | Claude Code (CLI) |
| **Data** | 2026-08-25 |
| **Branch** | `fix/startup-focus-and-diagnostics`, criada de `main` (`833c10a`) |
| **PR alvo** | `main` |
| **Autor dos commits** | `claude <claude@anthropic.com>` |

A seção **Pontos de situação** é atualizada a cada atividade, **no mesmo commit da atividade**, e a
seção **Problemas em aberto e riscos** recebe toda descoberta que aparecer no caminho.

## Contexto

O relato foi "instalei a 37.0.0 via `.exe` e o *iniciar com o sistema* não está funcionando". A
medição na máquina **desmente a parte do autostart** e revela outra coisa.

### Estado medido (2026-08-25, máquina de desenvolvimento)

| Verificação | Resultado |
|---|---|
| `HKCU\...\CurrentVersion\Run` → `UsageMonitor` | presente, `"C:\Users\edils\AppData\Local\Usage Monitor\Usage Monitor.exe"`, e o arquivo existe |
| `Explorer\StartupApproved\Run` → `UsageMonitor` | `02` = **habilitado**. As entradas `Usage Monitor` (`03`, desde 2026-04-27) e `Claude Usage Monitor` (`03`, desde 2026-04-29) são órfãs de nomes de valor que não existem mais na chave `Run` |
| `Microsoft-Windows-Shell-Core/Operational` ID 9707 | o Explorer executou o comando em **todos** os seis boots dos últimos seis dias: 21/08 08:07:52, 22/08 14:30:02, 23/08 12:36:42, 24/08 08:00:34, 25/08 08:04:29 e 25/08 08:10:55 |
| Processo lançado às 08:10:55 (PID 25952, filho 18564) | **vivo**, `explorer.exe` (PID 9592) como pai, janela `Usage Monitor` com `IsWindowVisible=True`, `IsIconic=False`, rect L=3071 T=751 R=3368 B=1352 no monitor de 3440×1440 |
| Log `Application`, últimos 6 dias | nenhum crash do app; só entradas do `MsiInstaller` dos cenários de teste de 24/08 |
| Preferência `auto/Start` em `HKCU\Software\JavaSoft\Prefs\com.usagemonitor` | `true` |

Ou seja: a chave `Run`, o `AutoStartManager` e a Section `SEC_AUTO_START` do `UsageMonitor.nsi` estão
corretos e dispararam. **O autostart nunca deixou de funcionar.** O que produziu a percepção de falha
são duas coisas distintas:

1. **Latência de ~1 min.** O Explorer subiu às 08:10:13 e serializa a fila de inicialização; o Usage
   Monitor é o 13º da lista e só foi lançado às 08:10:55 — 59 s depois do boot, 42 s depois do
   Explorer. É comportamento do SO, não do app.
2. **Defeito de código, confirmado pelo usuário.** Com a instância do autostart já de pé, clicar no
   atalho faz `SingleInstanceGuard.tryAcquire()` devolver `null`, o app chamar `exitApplication()` e
   **morrer em silêncio**: não traz a janela existente para a frente e não avisa nada. Do lado de
   quem clicou, nada acontece — o que confirma a conclusão de que o autostart falhou.

O resultado pretendido: clicar no ícone sempre produz janela; o próximo boot deixa prova própria no
disco de que o autostart rodou, sem depender do Event Log do Windows; e a decisão sobre trocar a
chave `Run` pelo Agendador de Tarefas passa a ser tomada com número medido.

### Fora de escopo

- **O mecanismo de autostart em si.** `AutoStartManager.setWindowsAutoStart`, a Section
  `SEC_AUTO_START` e a chave `Run` estão comprovadamente corretos; mexer neles seria trocar o que
  funciona por suposição.
- **O jar órfão da instalação.** `app\usage-monitor-desktop-37.0.0-22b8e471….jar` (4,4 MB, 24/08)
  ficou na pasta e o `Usage Monitor.cfg` **não** o referencia — o classpath aponta para o
  `…8ba1321…` de 22/08, que é o do release. É lixo sem efeito funcional, e o fix `3a93456`
  (`fix(packaging): clear the installer payload before staging it`) já está na `main`, então sai
  sozinho na próxima release.

## Atividades

| # | Atividade | Toca |
|---|---|---|
| A01 | Registro de arranque em `~/.usage-monitor/diagnostics/startup.jsonl` | `StartupDiagnostics.kt` (novo), `Main.kt` |
| A02 | Segunda instância traz a janela existente para a frente | `FocusRequestChannel.kt` (novo), `Main.kt` |
| A03 | `activateWindow` vence o *foreground lock* do Windows | `HistoryWindowActivation.kt` |
| A04 | Origem do arranque marcada com `--autostart` | `AutoStartManager.kt`, `UsageMonitor.nsi`, `Main.kt` |
| A05 | Medir o Agendador de Tarefas antes de migrar (experimento, sem código) | — |
| A06 | Migração para o Agendador de Tarefas — **condicional à A05** | `AutoStartManager.kt`, `UsageMonitor.nsi` |

### Decisões travadas

- **O registro de arranque é sempre ligado**, ao contrário do recorder de créditos da Anthropic e do
  recorder do Codex, que exigem variável de ambiente. Aqueles gravam corpo de resposta a cada coleta;
  este grava uma linha por arranque. Diagnóstico que depende de variável configurada **antes** do
  fato não serve para investigar o boot que já passou — que é literalmente o caso que originou este
  plano.
- **O canal de foco é arquivo, não socket.** Socket em loopback dispara o prompt do Firewall do
  Windows no primeiro arranque, e pedir permissão de rede para focar a própria janela é pior que o
  defeito.
- **Polling, não `WatchService`.** A pasta `~/.usage-monitor` tem o SQLite escrevendo `-wal`/`-shm` o
  tempo todo; um `lastModified()` a cada 500 ms é mais barato de raciocinar do que filtrar eventos de
  diretório, e é testável sem depender da semântica de notificação do sistema de arquivos.
- **A restauração reusa `restoreMainWindow`**, o mesmo caminho do item "Abrir" da bandeja. Um segundo
  caminho de restauração seria um segundo lugar para esquecer de desminimizar.
- **`fun main(args: Array<String>) = application {`** — forma de expressão de propósito: expõe `args`
  ao corpo sem reindentar as mil linhas abaixo, e mantém a regra de não criar composable nova ali.
- **A A06 é condicional e o critério é anterior à medida:** ganho abaixo de ~15 s → não migra. O
  ganho não pagaria a superfície nova (tarefa a criar, remover no desinstalador, migrar instalações
  existentes, e ambientes corporativos que bloqueiam criação de tarefas).

## Pontos de situação

Uma linha por atividade, escrita **no mesmo commit** da atividade. `Evidência` é o comando que rodou
e o resultado, não a intenção. A coluna `Commit` guarda o **assunto** do commit, não o hash: um
commit não pode conter o próprio hash.

| # | Data | Commit | Atividade | Estado | Evidência |
|---|---|---|---|---|---|
| A00 | 2026-08-25 | — | Diagnóstico na máquina antes de escrever código | concluída | Tabela *Estado medido* acima. Seis boots com o comando da chave `Run` executado pelo Explorer, processo do autostart vivo com janela visível, nenhum crash. **A premissa do relato caiu**: o autostart funciona, e o defeito é a segunda instância morrer calada |
| A01 | 2026-08-25 | `feat(startup): record every launch in a startup diagnostics file` | Registro de arranque | concluída | `StartupDiagnostics.kt` novo, chamado nos **dois** ramos do guard em `Main.kt`. `gradlew.bat desktopTest --tests "com.usagemonitor.StartupDiagnosticsTest"`: BUILD SUCCESSFUL, e o XML de resultado traz `tests="4" skipped="0" failures="0" errors="0"`. O corte do arquivo é provado com 250 linhas pré-existentes virando `KEPT_LINES + 1` |

---

## Problemas em aberto e riscos

| # | Risco | Estado |
|---|---|---|
| R1 | O laço de 500 ms da A02 é o primeiro laço de I/O em disco desse período no app. É um `lastModified()` num arquivo de 0 byte; se aparecer custo, o período sobe para 1 s sem mudar o desenho | aberto |
| R2 | A A03 alterna `isAlwaysOnTop` momentaneamente e isso pode fazer a janela piscar acima das outras para quem tem a preferência desligada. É o preço de a restauração funcionar | aberto |
| R3 | O argumento `--autostart` da A04 só chega às instalações já feitas pela migração no arranque; se ela falhar, o campo `origin` fica errado e nada mais quebra | aberto |
| R4 | Entre a A01 e a A04 o campo `origin` registra `manual` **também** para arranque por autostart, porque a chave `Run` ainda não carrega o argumento. As duas atividades saem no mesmo lote, sem release entre elas | aberto |
| R5 | A A05 pode descobrir que `schtasks /SC ONLOGON` sem `/RU` exige elevação, ou que o NSIS com `RequestExecutionLevel user` não consegue criar a tarefa. É o motivo de a A05 vir antes e de a A06 ser condicional | aberto |
| R6 | O `bootDeltaMs` do rascunho **não existe**: a JVM não expõe o instante de boot do SO de forma portátil, e um processo externo só para medi-lo custaria mais do que informa. O arquivo grava `processStartedAt` e `ts`; o delta contra o boot se calcula com `LastBootUpTime` na análise | fechado — decidido na A01 |
