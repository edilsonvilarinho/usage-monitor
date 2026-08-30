# Ícone estável na entrada de menu do Linux — execução

**Issue:** [#133](https://github.com/edilsonvilarinho/usage-monitor/issues/133)
**Branch:** `fix/linux-menu-icon-stable` · **Worktree:** `../usage-monitor-133`

## Contexto

Ao investigar a issue #120 na Bazzite do reportante, o ícone do Usage Monitor sumiu do menu de
aplicativos do KDE. A causa imediata foi cache do Plasma (`kbuildsycoca6 --noincremental` resolveu).
Mas inspecionando o `.desktop` de **menu** (`~/.local/share/applications/usage-monitor.desktop`,
gerado só pelo instalador `.sh` — arquivo diferente do `.desktop` de **autostart**, já corrigido na
#120) apareceu um defeito estrutural real, registrado na issue #133:

```
Exec="/home/edi/.local/bin/usage-monitor"                                                      # launcher estável
Icon=/home/edi/.local/share/usage-monitor/versions/38.0.2/Usage Monitor/lib/Usage Monitor.png  # árvore versionada
```

`Exec=` usa o launcher estável, que sobrevive a atualizações. `Icon=` não — ele nomeia a pasta da
versão instalada no momento em que o `.sh` foi rodado. O `linux-updater.sh` retém só a versão atual
mais a imediatamente anterior (cenário S10 do harness já prova isso); toda pasta mais antiga é
apagada a cada atualização bem sucedida. Resultado: duas atualizações depois de instalado, o arquivo
que o `Icon=` nomeia deixa de existir e o ícone some do menu — silenciosamente, sem log, sem erro.

É a mesma classe de defeito do `Path=` entre aspas do autostart (#120), e a correção segue o mesmo
princípio já estabelecido naquele trabalho: **"correção que não chega a quem já está afetado não é
correção"**.

## Decisão de desenho

Investigação por dois agentes (exploração + validação de plano) convergiu numa opção estritamente
melhor que só corrigir o instalador `.sh`:

- **O app já carrega os próprios bytes do ícone no classpath** (`/icons/app_icon.png`, embutido pelo
  `build.gradle.kts`), e já os usa para o ícone da janela (`loadWindowIcon()` em `Main.kt`). Não há
  necessidade de ler o `.png` de dentro da árvore versionada — o app tem cópia própria, sempre
  correta, sempre disponível.
- **O padrão de auto-reparo já existe**, para o problema irmão: `AutoStartManager.
  ensureAutoStartCommandCurrent()` roda a cada abertura do app e conserta uma entrada de autostart
  já quebrada, sem exigir que o usuário desligue/religue nada.

Replicar esse padrão para a entrada de **menu** (arquivo diferente, dono diferente — autostart mora
em `~/.config/autostart/`, menu em `~/.local/share/applications/`) alcança quem já está afetado na
próxima abertura do app, não lê da árvore prunável (sem corrida com o `linux-updater.sh`), e é
testável no Windows do CI com a mesma disciplina de função pura + wrapper de IO já usada em
`AutoStartManager`/`LinuxInstallLayout`.

**Escopo: só Kotlin**, confirmado com o usuário. O instalador `.sh` continua escrevendo o caminho
antigo numa instalação nova; o primeiro lançamento do app já conserta — sem gap prático. Nenhuma
mudança em `linux-updater.sh`: ele já relança o app promovido (passo 6) *antes* de podar as versões
antigas (passo 9), e é esse relançamento que já dispara o reparo em Kotlin antes de qualquer coisa
ser apagada.

## Micro-atividades

| # | Micro-atividade | Toca |
|---|---|---|
| A01 | Branch/worktree + este plano + comentário na issue #133 com a tabela de atividades | `docs/planos/` |
| A02 | Constante + membros de `LinuxInstallLayout` + `resolveLinuxMenuDesktopFilePath` | `LinuxInstallLayout.kt` |
| A03 | Funções puras `linuxMenuIconNeedsRepair` / `rewriteLinuxMenuIconLine` + testes | `LinuxMenuIcon.kt` (novo) |
| A04 | `ensureLinuxMenuIconCurrent` (wrapper de IO) + testes injetados | `LinuxMenuIcon.kt` |
| A05 | Fiação em `Main.kt` (constante `internal` + chamada no `LaunchedEffect`) | `Main.kt` |
| A06 | Verificação ao vivo na Bazzite: build da branch, `.desktop` de menu quebrado à mão é reparado na próxima abertura | — |
| A07 | PR pra `main` + comentário de fechamento na issue #133 | — |

## Pontos de situação

Uma linha por micro-atividade, escrita **no mesmo commit** da micro-atividade.

| # | Data | Commit | Micro-atividade | Estado | Evidência |
|---|---|---|---|---|---|
| A01 | 2026-08-30 | `docs(plan): open the execution plan for the linux menu icon issue` | Este plano | concluída | Worktree criada com `git worktree add ../usage-monitor-133 -b fix/linux-menu-icon-stable main`. Plano publicado como comentário na issue #133 |
