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
- **Acrescenta no fim, uma vez.** Reinstalar ou atualizar não repete a entrada: a checagem é pela lista
  delimitada por `;`.
- **Teto de 1900 caracteres.** Acima disso, o instalador não grava e avisa. Truncar o `PATH` do usuário é
  pior do que não acrescentar; o comando continua funcionando pelo caminho completo.
- **Remoção só do que a instalação grava** (entrada no fim do `PATH`). Entrada que o próprio usuário
  mexeu para o meio ou o começo fica como está: remover por posição arriscaria corromper o `PATH`.
- **Sem opção na tela de componentes.** O comando de linha é o mesmo produto; uma caixa a mais não tem
  consumidor hoje e entraria no caminho de atualização silenciosa.
- **Transmissão de `WM_SETTINGCHANGE`** para que terminais abertos vejam a mudança; quem já estava aberto
  continua sem a entrada até reabrir.

## Atividades

| ID | Atividade | Commit esperado |
|---|---|---|
| A01 | Plano de execução e worktree própria | `docs(installer): plan the user PATH entry for the CLI` |
| A02 | NSIS grava e remove a entrada do `PATH` do usuário; cenário S10 no roteiro do CI; documentação atualizada | `feat(installer): add the install folder to the user PATH` |

## Pontos de situação

| Ativ. | Data | Commit | Estado | Evidência (comando e resultado) |
|---|---|---|---|---|
| A01 | 2026-10-23 | este commit | concluída | `git worktree add ../usage-monitor-cli-path -b fix/instalador-path-cli main`. Plano criado. Sem código. |
| A02 | — | — | pendente | — |

## Riscos

- **R1 — NSIS sem runtime local.** A sintaxe e o comportamento de `ReadRegStr`, `WriteRegExpandStr`,
  `StrLoc` e `System::Call` só são medidos no `installer-windows`. Se o CI reprovar, o diagnóstico vai para
  o PR e a branch recebe a correção.
- **R2 — `StrLoc` sem posição.** A checagem de presença usa `StrLoc` com `!= ""`. Se ele devolver outro valor
  para "não encontrado", a reinstalação repete a entrada, e o cenário S10 reprova essa contagem.
- **R3 — teste mexe no `PATH` do usuário da máquina.** O S10 salva o valor bruto (`DoNotExpandEnvironmentVariables`)
  e restaura no `finally`. No CI o runner é descartável.
- **R4 — instalações antigas.** A entrada só existe depois de instalar a versão que traz esta mudança. Quem
  atualizou para uma versão anterior precisa de uma passada da nova (a atualização automática já faz isso).
