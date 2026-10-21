# Ícone do menu Linux acompanha a troca do ícone do app — issue #432

**Issue:** [#432](https://github.com/edilsonvilarinho/usage-monitor/issues/432)
**Branch:** `fix/issue-432-linux-menu-icon-refresh` · **Worktree:** `../usage-monitor-432`
**Antecedente:** [#133](https://github.com/edilsonvilarinho/usage-monitor/issues/133),
[`linux-icone-menu-estavel-execucao.md`](linux-icone-menu-estavel-execucao.md)

## Contexto

Na Bazzite (KDE Plasma), depois da troca de marca da #367, o menu de aplicativos seguiu mostrando o
ícone antigo. A versão instalada (`41.11.0-beta.8`) já carregava o ícone novo, byte a byte igual ao
`src/desktopMain/resources/icons/app_icon.png`, tanto no jar quanto no `Usage Monitor.png` do
jpackage. O que estava velho era a cópia estável criada na #133:

```
~/.local/share/usage-monitor/icon.png   29/ago   19 816 bytes   (ícone atual: 89 318 bytes)
```

## Fatos confirmados

- `ensureStableIconFile` (`LinuxMenuIcon.kt`) só grava a cópia quando ela **não existe**. A intenção
  era não mexer no `mtime` a cada abertura; o efeito é que nenhum ícone novo chega a quem já instalou.
- Regravar os bytes no **mesmo** caminho não basta para o KDE: o `Icon=` não muda, então a entrada de
  menu não é reconstruída, e o pixmap fica guardado por caminho no `icon-cache.kcache`, mapeado em
  memória pelo `plasmashell`. Na máquina afetada a recuperação manual precisou de
  `kbuildsycoca6 --noincremental` mais a remoção do `kcache`.
- Só o app (`ensureLinuxMenuIconCurrent`) conhece o caminho estável. O instalador `.sh` continua
  escrevendo `Icon=` para dentro de `versions/`, e o primeiro lançamento corrige; o
  `linux-updater.sh` não toca no ícone.

## Decisão

Nome da cópia **endereçado por conteúdo**: `icon-<12 hex do SHA-256 dos bytes embutidos>.png`.

- Ícone novo ⇒ nome novo ⇒ `linuxMenuIconNeedsRepair` já detecta o `Icon=` diferente e reescreve a
  linha ⇒ o KDE reconstrói o menu sozinho, e nenhum cache tem a chave nova.
- Mesmo ícone ⇒ mesmo nome ⇒ arquivo já existe ⇒ nenhuma escrita: preserva a regra original de não
  mexer no `mtime` a cada abertura.
- Cópias antigas (`icon.png` da #133 e `icon-<hash>.png` anteriores) são apagadas **depois** que o
  `.desktop` aponta para a nova; se a reescrita falhar, a antiga fica, e o menu não perde o ícone.
- Nome e seleção do que apagar são funções puras com teste; o IO segue sem lançar.

Alternativa recusada: manter `icon.png` e regravar quando os bytes diferirem, mais um `touch` no
`.desktop`. Corrige o arquivo, mas depende de cada ambiente invalidar o cache por caminho, que é
exatamente o que o KDE não faz.

## Micro-atividades

| # | Micro-atividade | Toca |
|---|---|---|
| A01 | Branch/worktree + este plano + comentário na issue #432 | `docs/planos/` |
| A02 | Nome por conteúdo, reescrita do `Icon=` e limpeza das cópias antigas + testes | `LinuxMenuIcon.kt`, `LinuxInstallLayout.kt`, testes |
| A03 | Verificação ao vivo na Bazzite com o binário real numa pasta `versions/` descartável | — |
| A04 | PR para `main` + comentário de fechamento na issue | — |

## Pontos de situação

Uma linha por micro-atividade, escrita **no mesmo commit** da micro-atividade.

| # | Data | Commit | Micro-atividade | Estado | Evidência |
|---|---|---|---|---|---|
| A01 | 2026-10-20 | `docs(plan): open the execution plan for the linux menu icon refresh (#432)` | Este plano | concluída | `git worktree add ../usage-monitor-432 -b fix/issue-432-linux-menu-icon-refresh main`. Plano publicado como comentário na issue #432 |
| A02 | 2026-10-20 | `fix(update): give the linux menu icon a content-addressed name (#432)` | Nome por conteúdo, reescrita do `Icon=` e limpeza das cópias antigas | concluída | `linuxMenuIconFileName` (`icon-<12 hex do SHA-256>.png`; teste fixa `"abc"` → `icon-ba7816bf8f01.png`, conferido com `printf abc \| sha256sum`) e `staleLinuxMenuIconFileNames` (só `icon.png` e `icon-<hash>` de outro hash; `current`, marcador, `.next` e `.png` alheio ficam). `LinuxInstallLayout.iconPath` virou `iconPath(fileName)`; `LINUX_MENU_ICON_FILE_NAME` saiu em favor de prefixo/sufixo + `LINUX_LEGACY_MENU_ICON_FILE_NAME`. `ensureLinuxMenuIconCurrent` lê os bytes do classpath uma vez, grava só se o arquivo com aquele nome não existe, reescreve o `Icon=` pelo reparo já existente e apaga as cópias antigas **só** depois que o `.desktop` foi gravado (falha na gravação mantém a antiga). `sh ./gradlew desktopTest --tests "com.usagemonitor.update.LinuxMenuIconTest" --tests "com.usagemonitor.update.LinuxInstallLayoutTest" --tests "com.usagemonitor.architecture.*"`: `LinuxMenuIconTest` `tests="11" failures="0"` (6 anteriores + 5 novos), `LinuxInstallLayoutTest` `tests="20" failures="0"`, `ArchitectureRulesTest` `tests="16" failures="0"` |
| A03 | 2026-10-20 | `docs(plan): record the live check of the menu icon refresh (#432)` | Verificação ao vivo na Bazzite | concluída | Binário real (`sh ./gradlew createDistributable -PappVersion=99.0.0`) em `versions/99.0.0-test/`, fora de `current`, com a instância padrão fechada. **(1) `icon.png` legado:** `Icon=` passou para `icon-a553f80f0f54.png` (hash igual ao `sha256sum` do `Usage Monitor.png` da build), `icon.png` apagado, demais linhas do `.desktop` byte a byte iguais, `desktop-file-validate` sem erro. **(2) ícone trocado** (cópia renomeada para `icon-ffffffffffff.png` e `Icon=` apontando para ela): cópia nova gravada, `Icon=` reescrito, `icon-ffffffffffff.png` apagado. **(3) abertura repetida:** `mtime` do ícone e do `.desktop` inalterados (21:50:04 antes e depois). **Armadilhas medidas:** (a) `--user-data-dir` não serve para o teste, o reparo é pulado de propósito fora da raiz padrão (#419); (b) build com a versão padrão (`41.10.0`) se **autoatualizou** para a beta instalada pelo `linux-updater.sh` (`OK promoted 41.11.0-beta.9`), e a beta relançada, com o código antigo, recriou `icon.png` e devolveu o `Icon=`; por isso o `-PappVersion=99.0.0` (`999.0.0` é recusado pela validação do `Exe`, MAJOR ≤ 255). Esse vaivém só ocorre com o binário antigo rodando depois do novo, o que o fluxo normal de atualização não faz. Pasta de teste removida e a beta.9 reaberta pelo launcher estável. `sh ./gradlew allTests` no Linux: `2944` testes, `0` falhas, `0` erros |
