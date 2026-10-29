# Teste de link simbólico pulado sem privilégio — issue #519

Procedência: Claude Opus 5.5 (esforço padrão), Claude Code CLI no Windows 11, 2026-10-28, branch
`test/issue-519-symlink-skip`, commits com a identidade `claude <claude@anthropic.com>`.

## Objetivo

`ClaudeCodeStatusInputTest` › "a symbolic link to the stored directory still finds the account" deixa o `allTests`
local vermelho em todo Windows sem `SeCreateSymbolicLinkPrivilege`. Sem o privilégio o caso tem de aparecer como
**ignorado**, nunca como falha e nunca como verde que nada provou.

## Fatos confirmados (2026-10-28, `465fc203`)

- `gradlew.bat allTests` nesta máquina: 3864 testes, 1 falha — `java.nio.file.FileSystemException:
  C:\Users\edils\AppData\Local\Temp\usage-monitor-link-28822009530500: O cliente não tem o privilégio necessário`, em
  `Files.createSymbolicLink` (`ClaudeCodeStatusInputTest.kt:56`), antes da asserção.
- Modo de Desenvolvedor desligado: `(Get-ItemProperty 'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\AppModelUnlock').AllowDevelopmentWithoutDevLicense` → vazio.
- O mesmo commit passou no CI (run 40792616889: `desktop-windows` e `desktop-linux` verdes) — o runner tem o privilégio.
- A suíte roda sobre JUnit 4: `desktopTest` depende de `libs.kotlin.test` sem `useJUnitPlatform`; cache do Gradle com
  `org.jetbrains.kotlin/kotlin-test-junit` e `junit/junit`. `createSymbolicLink` é a única chamada do tipo em `src/`.

## Decisões

- **Pular por `org.junit.Assume.assumeNoException`**, que o JUnit 4 reporta como ignorado. `return` silencioso foi
  recusado: o caso passaria verde sem exercer o link.
- **Só pula o que é ambiente:** `FileSystemException` (privilégio negado) e `UnsupportedOperationException` (sistema de
  arquivos sem link). `FileAlreadyExistsException` é defeito do próprio teste e propaga.
- Helper `createSymbolicLinkOrSkip` em `terminal/SymbolicLinks.kt` (`desktopTest`), com a criação injetável para
  o teste do helper simular a recusa.

## Atividades

| Atividade | O que faz |
|---|---|
| A01 | Este plano |
| A02 | TDD: `SymbolicLinksTest` (recusa → ignorado; arquivo existente → propaga; sucesso → link); teste afetado usa o helper |

## Ponto de situação

Cada linha é escrita no commit da atividade que descreve, com o comando executado e o resultado.

| Atividade | Alteração | Comando e resultado |
|---|---|---|
| A01 | Plano com os fatos medidos na beta `v42.1.0-beta.1` | `gh issue create` → #519 |

## Problemas em aberto e riscos

| Risco | Estado |
|---|---|
| Se um runner do CI perder o privilégio, o caso passa a ser ignorado em silêncio no `ci-ok` | aceito; o relatório de testes lista ignorados |
