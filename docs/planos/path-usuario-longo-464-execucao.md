# PATH do usuário acima de 1024 caracteres (#464)

## Objetivo

Com a 42.0.0-beta.3 instalada, `usage-monitor.exe tui` só roda de dentro da pasta de instalação: o instalador
não acrescentou `%LOCALAPPDATA%\Usage Monitor` ao `PATH` do usuário. Esta atividade tira o teto que causa isso,
sem reabrir o risco de apagar ou truncar o `PATH` que a revisão do PR #456 fechou
([`instalador-path-cli-execucao.md`](instalador-path-cli-execucao.md), D1).

## Fatos confirmados (medidos na máquina do relato, 2026-10-27)

- `git tag --contains 8b14a931` (guarda de tamanho do PR #457): `v42.0.0-beta.3`. A versão do relato já tem a
  entrada no `PATH` **e** a guarda.
- `HKCU\Environment\Path` lido bruto (`DoNotExpandEnvironmentNames`): `ExpandString`, **1051 caracteres**, 21
  entradas. `RegGetValueW` devolve `(1051 + 1) * 2 = 2104` bytes; o `.nsi` divide por 2 (1052) e compara com
  `NSIS_MAX_STRLEN`: 1052 > 1024, logo `$R6 = "unsafe"` e `AddInstallDirToUserPath` sai sem gravar.
  É a "limitação aceita" registrada na revisão do #456, não um defeito novo.
- `makensis -HDRINFO` desta máquina (NSIS 3.12, o mesmo que o `choco install nsis` instala): `NSIS_MAX_STRLEN=1024`,
  `NSIS_UNICODE`, `NSIS_PTR_SIZE=4`.
- `install.log` da instalação do relato tem 38 bytes, só `Installation completed successfully!`: o instalador
  chegou pelo `/UPDATE` (que não grava a linha de início) e o resultado do `PATH` só vai para o `DetailPrint`,
  que não fica em lugar nenhum. Nada no disco dizia por que a entrada faltou.
- `HKLM\...\Session Manager\Environment\Path` e `HKCU\Environment\Path` sem nenhuma entrada `Usage Monitor`
  depois de o usuário remover a que inseriu à mão no `PATH` do sistema.
- A atualização automática passa por `updateDone`, que chama `AddInstallDirToUserPath`: quem já instalou recebe
  a entrada na próxima atualização, sem ação manual.

## Decisões

- **O valor do `PATH` nunca passa por uma string do NSIS.** `RegGetValueW` lê para um buffer do plugin System
  (`System::Alloc`) dimensionado pela primeira chamada, com folga para `;<pasta>;`. A varredura por entrada
  (`StrChrW` até o `;`), a comparação (`CompareStringOrdinal` sem caixa) e a cópia das entradas que ficam
  (`RtlMoveMemory`) são feitas sobre o buffer; a gravação é `RegSetKeyValueW` com `REG_EXPAND_SZ`, o tipo que o
  instalador já gravava. O teto de 1024 deixa de existir sem trocar de build do NSIS.
- **Recusado: build "large strings" do NSIS.** Só sobe o teto para 8192, e o CI e a release passariam a baixar
  um zip fora do `choco`. O PATH de ~9800 caracteres do S13 continuaria sem a entrada.
- **Recusado: PowerShell chamado pelo instalador.** Política de execução e antivírus corporativos bloqueiam o
  processo filho, e a leitura padrão do .NET expande `%VAR%`.
- **Teto novo: 32767 caracteres**, o limite do Windows para o valor de uma variável de ambiente. Valor final
  acima disso não é gravado; leitura que falha, tipo inesperado ou valor que cresceu entre as duas leituras
  (`ERROR_MORE_DATA`) continuam sem tocar o `PATH`, na instalação e na desinstalação.
- **Comportamento preservado byte a byte:** entrada em qualquer posição não é repetida; a desinstalação tira
  toda entrada igual e deixa as demais, vazias inclusive, na ordem; `;` final continua `;` final; valor vazio
  depois da remoção apaga o `Path`.
- **Resultado no `install.log`:** uma linha `User PATH: ...` com o desfecho (`added`, `already present`,
  `unchanged (...)`), para que o próximo relato traga a causa no próprio arquivo.

## Atividades

| ID | Atividade | Commit esperado |
|---|---|---|
| A01 | Plano de execução e worktree própria | `docs(installer): plan the long user PATH fix (#464)` |
| A02 | `PATH` do usuário lido e regravado em buffer nativo; teto de 32767; S13 passa a exigir a entrada; S15 cobre o teto novo; docs | `fix(installer): add the install folder to a user PATH of any length` |
| A03 | Desfecho do `PATH` no `install.log`; asserção no S10 | `feat(installer): record the user PATH outcome in install.log` |

## Pontos de situação

| Ativ. | Data | Commit | Estado | Evidência (comando e resultado) |
|---|---|---|---|---|
| A01 | 2026-10-27 | este commit | concluída | `git worktree add ../usage-monitor-path-464 -b fix/installer-long-user-path-464 origin/main`. Medição do `PATH` por `Get-Item HKCU:\Environment` (1051 caracteres) e `makensis -HDRINFO` (`NSIS_MAX_STRLEN=1024`). Sem código. |
| A02 | 2026-10-27 | este commit | concluída localmente | `makensis` 3.12 sobre o `.nsi` da branch e o da `main`: exit 0 nos dois, os mesmos dois warnings 6030 pré-existentes (linhas 148/149). `pwsh -File src/installer/test/Invoke-UpdateScenarios.ps1` (Windows 11, sem WiX: S7 pulado): `Verificacoes: 77   Falhas: 0`; S13 (PATH de ~9800 caracteres com `%SystemRoot%`) recebe a pasta, não a repete, desinstala de volta byte a byte, e tira a pasta em caixa alta do meio com `;` final; S15 aceita o valor de 32767 exatos e não toca o de 32768. PATH real restaurado: `-ceq` contra a cópia do scratchpad = `True`, `ExpandString`. |
| A03 | 2026-10-27 | este commit | concluída localmente | `Var UserPathOutcome` gravado em cada saída de `AddInstallDirToUserPath` e escrito como `User PATH: ...` antes de `Installation completed successfully!`. Primeira rodada: `Verificacoes: 80   Falhas: 2` — as asserções estavam no S10, onde S1–S8 já deixaram a pasta no PATH (`already present` na 1ª instalação) e a reinstalação passa pelo desinstalador (`added`): comportamento correto, expectativa errada. Movidas para S11 (`added`), S12 (`already present`) e S15 (`unchanged (over 32767 characters)`). Segunda rodada: `Verificacoes: 80   Falhas: 0`; PATH real `-ceq` à cópia = `True`; `update-receipt.properties` restaurado da cópia. |

## Riscos

- **R1 — o roteiro de cenários mexe no `PATH` real.** O `finally` restaura o valor; localmente o valor e o
  `update-receipt.properties` são copiados para o scratchpad antes da rodada.
- **R2 — plugin System em x86.** O instalador é 32 bits (`NSIS_PTR_SIZE=4`); aritmética de ponteiro por
  `IntPtrOp`, não `IntOp`, para não depender disso.
