# Migração do jline 3.30.17 para 4.5.0 — issue #574

Procedência: Claude Opus 5.5, Claude Code CLI no Windows 11, 2026-11-12, branch `chore/issue-574-jline-4`, commits
com a identidade `claude <claude@anthropic.com>`.

## Objetivo

O PR do Dependabot #546 (jline 3.30.17 → 4.5.0) falha no CI: `:compileKotlinDesktop` não resolve
`Terminal.stdoutEncoding()` em `TuiTerminal.kt:57`. Este plano faz a migração e substitui o #546.

## Fatos confirmados (2026-11-12, `9284dd4d`)

- `gh run view 42556374159 --log-failed`: o erro é o mesmo em `desktop-windows` e em `desktop-linux`,
  `Unresolved reference 'stdoutEncoding' on receiver of type 'Terminal'`. Nenhuma falha de resolução de dependência.
- `javap -cp jline-terminal-4.5.0.jar org.jline.terminal.Terminal`: só sobram `encoding()`, `inputEncoding()` e
  `outputEncoding()`. Em 3.30.17 existem também `stdinEncoding()`, `stdoutEncoding()` e `stderrEncoding()`.
- Fontes de 3.30.17 (`Terminal.java:454`): `default Charset stdoutEncoding() { return outputEncoding(); }`, e nenhuma
  implementação a sobrescreve (`grep -rn stdoutEncoding` nos fontes). Trocar por `outputEncoding()` preserva o comportamento.
- `MANIFEST.MF` do 4.5.0: `osgi.ee` JavaSE 11; o build usa `jvmToolchain(17)`.
- O único uso é em `TuiTerminal.kt:57`; `JLineTuiIoTest` já afirma `assertEquals("UTF-8", io.encoding)`.

## Decisões

- Migrar, em vez de ignorar o major: a quebra é uma chamada só, com um substituto equivalente comprovado.
- O #546 é fechado em favor deste PR. Não houve vermelho por asserção possível: o bump sozinho é erro de compilação.
  A rede de segurança é o `JLineTuiIoTest`, que já existe.

## Atividades

- A01: este plano.
- A02: bump em `gradle/libs.versions.toml` e `stdoutEncoding()` → `outputEncoding()` em `TuiTerminal.kt`.

## Ponto de situação

| Atividade | Comando | Resultado |
|---|---|---|
| A01 | sem harness (plano) | revisado no diff |

## Problemas em aberto e riscos

- Mudanças de comportamento do jline 4 que não quebram a compilação nem os testes em memória (terminal real no
  Windows/Linux) não são exercitadas pela suíte; o modo terminal precisa de verificação manual em terminal real.
