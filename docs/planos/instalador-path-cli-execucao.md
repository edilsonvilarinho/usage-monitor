# PATH do usuário para o comando de linha no Windows

## Objetivo

Depois de atualizar o Usage Monitor no Windows, `usage-monitor` não é reconhecido no PowerShell: o
instalador não acrescenta a pasta de instalação ao `PATH` do usuário, e a documentação de #418 diz isso
de propósito (R1 de [`comandos-cli-418-execucao.md`](comandos-cli-418-execucao.md)). Esta atividade troca
essa decisão: o instalador passa a registrar a pasta, uma vez, e a desinstalação a tira.

## Fatos confirmados (antes de qualquer código)

- `src/installer/UsageMonitor.nsi` não tem nenhuma escrita em `Environment\Path`. A única chave de ambiente
  que o instalador toca é a `Run` (autostart).
- O R1 registrou o risco: o NSIS pode truncar a variável numa string longa. Por isso a gravação tem teto.
- O roteiro `src/installer/test/Invoke-UpdateScenarios.ps1` roda no job `installer-windows` do CI, que
  compila o `.nsi` de produção e executa os cenários de instalação, reinstalação e desinstalação de verdade.
  O filtro `installer` do job `changes` cobre `src/installer/*`, então a mudança dispara o job.
- Não há NSIS, PowerShell nem Windows nesta máquina (Linux): a validação de runtime do instalador só existe
  no CI Windows. Esta atividade não é medida localmente.

## Decisões

- **Escopo: só o `PATH` do usuário** (`HKCU\Environment`, valor `Path`). O `PATH` do sistema não é tocado,
  porque exige privilégio de administrador e o instalador roda como usuário.
- **Acrescenta no fim, uma vez.** Reinstalar ou atualizar não repete a entrada: a presença é checada pelo
  fim do valor (`;<pasta>;`), a mesma invariante que a desinstalação usa. Checar substring exigiria macro do
  StrFunc, e a versão do NSIS do CI não aceitou a macro (incidência 1, abaixo).
- **Teto de 1900 caracteres.** Acima disso, o instalador não grava e avisa. Truncar o `PATH` do usuário é
  pior do que não acrescentar; o comando continua funcionando pelo caminho completo.
- **Remoção só do que a instalação grava** (entrada no fim do `PATH`). Entrada que o próprio usuário
  mexeu para o meio ou o começo fica como está: remover por posição arriscaria corromper o `PATH`.
- **Sem opção na tela de componentes.** O comando de linha é o mesmo produto; uma caixa a mais não tem
  consumidor hoje e entraria no caminho de atualização silenciosa.
- **Transmissão de `WM_SETTINGCHANGE`** para que terminais abertos vejam a mudança; quem já estava aberto
  continua sem a entrada até reabrir.

## Revisão do PR #456 (2026-10-23, Claude Opus 5.5)

Revisão pedida pelo usuário depois do merge, antes de qualquer release (`git tag --contains 8a18345b`: vazio).
O CI verde provou o caso feliz, mas três defeitos ficaram fora do S10:

- **D1 — PATH longo é apagado (grave).** O build oficial do NSIS, o que o CI e a release instalam por
  `choco install nsis`, guarda strings de até 1024 caracteres (`NSIS_MAX_STRLEN`; o build de 8192 é especial).
  Valor maior faz o `ReadRegStr` devolver vazio com erro, e o código mergeado lia isso como "Path ausente":
  a instalação gravaria `Path = <pasta>`, **apagando o PATH do usuário**, e a desinstalação gravaria um valor
  truncado. O teto de 1900 nunca disparava, porque a string composta já sai cortada em 1023.
- **D2 — instalador ANSI.** O `.nsi` não declara `Unicode true`. O `ReadRegStr` ANSI converte o PATH para a
  página de código do sistema: entrada com caractere fora dela volta com `?` ou trocada por "best-fit" e é
  regravada assim.
- **D3 — presença só pelo fim.** Outro programa que acrescente ao PATH depois da instalação tira a entrada do
  fim: a próxima atualização automática acrescenta uma segunda, e a desinstalação deixa a primeira apontando
  para uma pasta apagada.
- **Registro corrigido:** a incidência 1 da A02 não foi "versão do NSIS que não aceita a macro". O StrFunc se
  declara com `${StrLoc}` sozinho na linha, não com `!insertmacro StrLoc`. A nova implementação não usa StrFunc.
- **Fechamento que faltava na A02:** run `40020272886` verde (`installer-windows`: `Verificacoes: 54 Falhas: 0`),
  PR #456 mergeado em `8a18345b`.

### Decisões da correção

- **`Unicode true`** no instalador inteiro. O script e o `license.txt` são ASCII (verificado byte a byte), e os
  `languages/*.nsh` não são incluídos, então nenhum texto existente muda de codificação.
- **Leitura com guarda de tamanho.** O tamanho do valor sai de `RegGetValueW` (`RRF_NOEXPAND`) antes do
  `ReadRegStr`. Valor que não cabe inteiro no buffer, de tipo inesperado ou que dê erro de leitura: a instalação
  e a desinstalação **não tocam** o PATH. Valor final que não caiba: a instalação não grava.
- **Membro por entrada exata.** O PATH é varrido entrada a entrada (separador `;`, comparação sem caixa, como o
  Windows compara caminhos). A instalação não acrescenta se a pasta estiver em qualquer posição. A
  desinstalação tira todas as entradas iguais à pasta, e as demais, vazias inclusive, ficam byte a byte.
- **Ponto e vírgula final preservado.** `A;` vira `A;<pasta>;`, e a desinstalação devolve `A;`: nem entrada
  vazia no meio (que ferramentas do MSYS leem como diretório corrente), nem perda do formato original.
- **Limitação aceita:** com o build de 1024, um PATH de usuário acima de ~1000 caracteres não recebe a entrada.
  O comando continua pelo caminho completo (README). Remover essa limitação exige o build especial do NSIS ou
  escrever o PATH fora do NSIS. Isso fica como decisão separada.

## Atividades

| ID | Atividade | Commit esperado |
|---|---|---|
| A01 | Plano de execução e worktree própria | `docs(installer): plan the user PATH entry for the CLI` |
| A02 | NSIS grava e remove a entrada do `PATH` do usuário; cenário S10 no roteiro do CI; documentação atualizada | `feat(installer): add the install folder to the user PATH` |
| A03 | Revisão do PR #456 registrada neste plano (D1–D3) | `docs(installer): record the review of the user PATH entry` |
| A04 | Instalador em Unicode; cenário com entrada fora da página de código | `fix(installer): build the installer as Unicode` |
| A05 | Leitura do PATH com guarda de tamanho, membro e remoção por entrada exata; cenários de PATH longo, entrada no meio e valor ausente; docs | `fix(installer): never rewrite a user PATH the installer cannot hold` |

## Pontos de situação

| Ativ. | Data | Commit | Estado | Evidência (comando e resultado) |
|---|---|---|---|---|
| A01 | 2026-10-23 | este commit | concluída | `git worktree add ../usage-monitor-cli-path -b fix/instalador-path-cli main`. Plano criado. Sem código. |
| A02 | 2026-10-23 | `26c8d3f6` | incidência 1 no `installer-windows` (run `40018581344`): `!insertmacro StrLoc` → `macro named "StrLoc" not found` na linha 418 do `.nsi`. Correção: sem StrFunc, presença checada pelo fim do valor; incidência 2 (run `40018973520`): S1–S8 passaram, S10 reprovou em `GetValue` com a string `DoNotExpandEnvironmentVariables` (o enum é `RegistryValueOptions`). O mesmo bloco marcava o restauro antes da leitura: uma falha ali faria o `finally` apagar o `Path`. Correção: enum e flag depois da leitura; incidência 3 (run `40019893379`): S10 passou no install/reinstall/uninstall, mas o "antes" foi lido no próprio S10, depois de S1–S8 deixarem a pasta no PATH (eles não desinstalam). Correção: foto do PATH movida para antes do S1, dentro do `try`, então o `finally` restaura o valor da rodada | `Invoke-UpdateScenarios.ps1` ganha S10 (PATH uma vez, reinstalação sem repetir, desinstalação devolve o valor bruto, restaurado no `finally`). Sem NSIS/PowerShell/Windows nesta máquina: nada compilado nem executado localmente. |
| A03 | 2026-10-23 | este commit | concluída | `git worktree add ../usage-monitor-path-review -b fix/instalador-path-revisao origin/main`. Revisão do diff de `6574fdff..8a18345b`; limite de 1024 do build oficial confirmado em documentação do NSIS e em relatos de PATH corrompido (Red Hat #1090075, OpenVPN). Sem código. |
| A04 | 2026-10-23 | este commit | código pronto; validação no `installer-windows` do PR | `Unicode true` no topo do `.nsi`; `LC_ALL=C grep -cP '[^\x00-\x7F]'` no `.nsi` e no `license.txt`: 0 linhas. S11 semeia `C:\scenario\日本;%SystemRoot%\scenario` e exige o valor byte a byte (`-ceq`, helper `Assert-UserPathExactly`) depois de instalar e de desinstalar; S10 passa a comparar o baseline com `-ceq`. Sem NSIS/PowerShell nesta máquina. |
| A05 | 2026-10-23 | este commit | código pronto; validação no `installer-windows` do PR | `.nsi`: `ReadUserPath` (tamanho por `RegGetValueW` com `RRF_NOEXPAND`, recusa acima de `NSIS_MAX_STRLEN`, `ReadRegStr` com `ClearErrors`), `UserPathWithoutInstallDir` (varredura por entrada, sem caixa), ambas geradas para instalador e desinstalador por macro. Cenários S12 (pasta no meio, `;` final), S13 (PATH de ~9800 caracteres, com e sem a pasta, intacto) e S14 (sem valor `Path`). README EN/PT e `build-and-release.md` dizem o limite de ~1000 caracteres. Sem NSIS/PowerShell nesta máquina. |

## Riscos

- **R1 — NSIS sem runtime local.** A sintaxe e o comportamento de `ReadRegStr`, `WriteRegExpandStr`,
  `StrLoc` e `System::Call` só são medidos no `installer-windows`. Se o CI reprovar, o diagnóstico vai para
  o PR e a branch recebe a correção.
- **R2 — presença só pelo fim.** Uma entrada que o usuário levou para o meio do `PATH` não é reconhecida, e a
  reinstalação acrescenta outra. O S10 cobre o caso que a instalação grava (no fim).
- **R3 — teste mexe no `PATH` do usuário da máquina.** O S10 salva o valor bruto (`DoNotExpandEnvironmentVariables`)
  e restaura no `finally`. No CI o runner é descartável.
- **R4 — instalações antigas.** A entrada só existe depois de instalar a versão que traz esta mudança. Quem
  atualizou para uma versão anterior precisa de uma passada da nova (a atualização automática já faz isso).
