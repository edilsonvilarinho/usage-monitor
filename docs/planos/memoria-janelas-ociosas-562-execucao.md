# Memória crescente: janelas modais escondidas — issue #562

Procedência: Claude Opus 5.5 (orquestração), Claude Code CLI no Windows 11, 2026-11-12, branch
`fix/issue-562-release-idle-modal-windows`, base `3c47298c`, commits com a identidade `claude <claude@anthropic.com>`.
Implementação pelo agente `frontend-dev` (modelo do frontmatter em `.claude/agents/`).

## Objetivo

A issue mostra o Usage Monitor com 783 MB no Gerenciador de Tarefas, crescendo com o uso. Medir onde está a memória
e devolver a que as janelas modais fechadas seguram para sempre.

## Fatos confirmados (2026-11-12, app instalado 42.3.0-beta.1, Windows 11, Direct3D)

- Instância aberta havia 4 h (PID 39996): `Get-Process` → privada 1.744 MB, working set 1.044 MB; `jcmd GC.heap_info`
  → heap 112 MB comprometida, 69 MB usada; metaspace 83 MB; code cache ~70 MB. A heap não é o problema.
- Sem `-Xmx`: `jcmd VM.flags` → `MaxHeapSize=17163091968`, `InitialHeapSize=1073741824` (ergonomia de 64 GB).
- Relançado com `JAVA_TOOL_OPTIONS=-XX:NativeMemoryTracking=summary`, amostra a cada 15 s
  (`jcmd VM.native_memory summary` + `GC.class_histogram`):

| Momento | Janelas vivas (`Direct3DRedrawer`) | Privada | JVM (NMT) | Fora da JVM |
|---|---|---|---|---|
| arranque, 15:16–15:23 | 4 (HUD + pré-aquecidas) | 998–1.031 MB | 272–285 MB | ~748 MB |
| abriu e fechou 2 janelas, 15:25:13 | 6 | 1.493 MB | 384 MB | ~1.109 MB |
| abriu e fechou mais 1, 15:26:00 | 7 | 1.757 MB | 394 MB | ~1.363 MB |
| depois de `jcmd GC.run`, 15:26:30 | 7 | 1.839 MB | 476 MB | ~1.363 MB |

- Cada janela aberta pela primeira vez soma 180–250 MB **fora da JVM** e não devolve ao fechar: a contagem de
  `ComposeWindow`/`ComposeDialog`/`Direct3DRedrawer` nunca cai, nem depois do GC. É a regra "a janela nasce na
  primeira abertura e depois só se esconde" (`AppDialogWindow.kt:79`, `rememberModalWindowHost` em `:246`).
- Renderização por software no arranque (`-Dskiko.renderApi=SOFTWARE`): privada 778 MB, JVM 415 MB, fora 363 MB,
  com as mesmas 4 janelas. O Direct3D custa ~90 MB por janela só no arranque.

## Decisões

- Escolha do usuário (2026-11-12): **janela escondida há mais de 60 s sai da composição** e libera a janela nativa;
  o pré-aquecimento continua.
- A liberação vale para janela que já foi aberta e fechada. A pré-aquecida que nunca abriu continua viva: liberá-la
  aos 60 s anularia o pré-aquecimento que o usuário manteve.
- Liberada, a janela não é pré-aquecida de novo; a próxima abertura paga a criação (200–460 ms medidos no
  `compose-implementation.md`).
- Decisão em função pura em `AppDialogWindowDecisions.kt`, testada em `ModalRequestActionTest.kt`; o host só agenda.
- Vale para todas as plataformas: sair da composição é o comportamento anterior das modais, já exercido em todas.

## Atividades

| Id | O quê | Onde |
|---|---|---|
| A01 | Este plano | `docs/planos/memoria-janelas-ociosas-562-execucao.md` |
| A02 | Decisão pura de liberação + host que tira a janela da composição após 60 s escondida (TDD) | `AppDialogWindowDecisions.kt`, `AppDialogWindow.kt`, `ModalRequestActionTest.kt`, docs da regra das modais |
| A03 | Medir no app real (`gradlew.bat run` com NMT) que a memória privada cai depois dos 60 s | este plano |

## Ponto de situação

| Id | Execução | Resultado |
|---|---|---|
| A01 | plano escrito com as medições acima | — |
| A02 | vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.ModalRequestActionTest"` → 4 falhas de asserção (`ModalRequestActionTest.kt:47`, `:72`, `:77`, `:87`, `AssertionError`) com as funções em stub; verde: o mesmo comando → 14 testes, 0 falhas; `desktopTest --tests "com.usagemonitor.architecture.*"` → 17 testes, 0 falhas. `MODAL_IDLE_RELEASE_MILLIS`, `shouldReleaseHiddenModalWindow`, `shouldCreateModalWindow`; holder observável em `rememberModalWindowHost` | verde |

## Problemas em aberto e riscos

- **Não provado ainda** que tirar a janela da composição devolve a memória nativa (Skiko libera o `Direct3DRedrawer`
  no `dispose`?). A03 mede; se não cair, a mudança volta e o plano é revisto.
- Estado de interface guardado só na composição da janela (aba, rolagem) se perde após 60 s fechada.
- Reabrir depois da liberação volta a criar o dispositivo Direct3D; o `app.log` registrou um caso de 5,9 s na
  criação (`Direct3DRedrawer.createDirectXDevice`, 2026-11-12T13:04Z).
- Os ~750 MB fora da JVM no arranque (4 janelas, DLLs, Skia) ficam fora desta issue.
