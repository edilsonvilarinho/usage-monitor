# HUD no COSMIC: o compositor ignora a posição pedida (#430) — execução

Issue: [#430](https://github.com/edilsonvilarinho/usage-monitor/issues/430), relatada na [#416](https://github.com/edilsonvilarinho/usage-monitor/issues/416), continuação da [#426](https://github.com/edilsonvilarinho/usage-monitor/issues/426).
O texto abaixo é o corpo da issue no momento da abertura; a fonte viva do andamento é a tabela
de pontos de situação no fim deste arquivo.

## Resumo

A correção da #426 (publicada em `v41.11.0-beta.8`) **não funciona no COSMIC**. Ela parte do princípio de que a janela da HUD aparece onde o app pede, e foi medida no KWin, que respeita a posição (`pedido == obtido`). **O COSMIC ignora a posição pedida na abertura**: ele coloca a janela no monitor que escolheu. Quando escolhe o outro monitor, o AWT troca a janela para a configuração de 24 bits daquele monitor e o alfa cai. O renascimento tenta de novo **no mesmo monitor de antes**, o COSMIC a coloca de novo no outro, e o orçamento de 3 tentativas acaba em meio segundo. A barra fica sem alfa, que é o retângulo preto da #416.

Esta issue registra o diagnóstico tirado do pacote `--diagnostics` do relator da #416 e o plano da correção.

## Ambiente do relator

Lido do `environment.txt` e do `startup.jsonl` do pacote de 2026-10-20 17:57:

| Campo | Valor |
|---|---|
| Sistema | **Pop!_OS** (kernel `7.2.6-76070206-generic`, padrão de nome da System76; o relator já tinha informado Pop!_OS na #416) |
| Desktop | **COSMIC**, sessão **Wayland**, app por **XWayland** (`DISPLAY` presente) |
| Monitores | **dois** (`:0.0` e `:0.1` no `app.log`). O pacote **não** traz a lista com os limites; é uma das lacunas que este plano fecha |
| Java / render | 17.0.15 Adoptium · `skiko.renderApi=SOFTWARE` (`render=SOFTWARE_FAST`) |
| Transparência | `translucency.perpixel=true`, `translucency.shaped=true`, `translucency.uniform=false` |
| Bandeja | `tray.supported=false` |

Linha do tempo do pacote: seis aberturas da `41.11.0-beta.8` entre 17:24 e 17:33; às 17:42 o app volta para a **`41.10.0`** e fica nela até o fim. Desligar o beta não faz downgrade, então foi uma reinstalação manual da estável. É o sinal de que a beta não resolveu para ele.

## O que o log mostra

### A falha (pid 98584, 17:30:08)

```
08.971 hud-alfa estado :0.0 capaz=true  profundidade=32bpp em 404,0 459x368     ← nasce certo, no monitor salvo
09.279 hud-alfa estado :0.0 capaz=false profundidade=24bpp em 2651,163 459x368  ← o COSMIC pôs a janela no 2º monitor
09.300 hud-alfa renascer :0.0 capaz=false profundidade=24bpp tentativa=1
09.363 hud-alfa estado :0.0 capaz=true  profundidade=32bpp em 404,0 459x368     ← renasce na config do :0.0 de novo
09.498 hud-alfa estado :0.1 capaz=false profundidade=24bpp em 2699,211 459x368  ← 2º monitor de novo (+48,+48: cascata)
09.498 hud-alfa renascer :0.1 capaz=false profundidade=24bpp tentativa=2
09.533 hud-alfa estado :0.0 capaz=true  profundidade=32bpp em 404,0 459x368
09.665 hud-alfa estado :0.1 capaz=false profundidade=24bpp em 2651,163 459x368
09.666 hud-alfa renascer :0.1 capaz=false profundidade=24bpp tentativa=3
09.703 hud-alfa estado :0.0 capaz=true  profundidade=32bpp em 404,0 459x368
09.830 hud-alfa estado :0.0 capaz=false profundidade=24bpp em 2699,211 459x368  ← sem orçamento: fica assim
11.720 hud-window pedido 404,0 458x368 obtido 2699,211 459x368 (diferente); … device=:0.0 24bpp
```

Quatro janelas em 0,86 s, todas pedidas em `404,0` no `:0.0` e todas colocadas pelo COSMIC no segundo monitor. Resultado: **24 bits no segundo monitor**, sem alfa, sem mais tentativas.

### A posição pedida é ignorada em toda abertura

| Aberturas | Pedido | Obtido |
|---|---|---|
| 12 de 13 | `404,0` ou `831,0` | `731,163`, sempre o mesmo ponto |
| pid 98584 | `404,0` | `2651,163` e `2699,211` (outro monitor, em cascata) |

`731` é o centro horizontal de um monitor de 1920 px para uma janela de 459 px, e `2651 = 1920 + 731`. A leitura mais provável é que o COSMIC centraliza a janela no monitor que escolheu, e que o segundo monitor começa em `x=1920`. Fica como **dedução**, porque o pacote não lista os monitores.

No KWin (Bazzite, onde a #426 foi medida) o log diz `pedido 2547,430 … obtido 2547,430 (igual)`. A suposição da #426 vale lá e não vale aqui.

Isso também corrige a conclusão L11 de `docs/planos/log-passivo-416-execucao.md` ("posição pedida respeitada na abertura", com `2674,163 … (igual)`). O `y=163` é o mesmo ponto escolhido pelo COSMIC. O mais provável é que aquela posição salva já tivesse vindo do próprio compositor, e não que ele tenha respeitado um pedido.

## A causa

```
HudWindow.kt:200        screenId vem do monitor salvo (e do ponteiro ao soltar um arrasto)
HudWindowFrame.kt:213   a GraphicsConfiguration da janela nova é a de screenId
HudWindowFrame.kt:216   o orçamento é por screenId e zera quando ele muda
```

1. O app escolhe a configuração de 32 bits **do monitor salvo** (`:0.0`) e pede a posição encaixada nele.
2. O COSMIC ignora a posição e mapeia a janela no monitor que ele escolheu. Às 17:30 era o segundo, provavelmente o do ponteiro ou do foco.
3. O AWT vê a janela fora do device da configuração dela e a troca pela **padrão** do destino, que é de 24 bits.
4. A sondagem vê `capaz=false` e faz a janela renascer, de novo com a configuração de `screenId`. **Nada no caminho atualiza `screenId` para o monitor onde a janela realmente está.**
5. O COSMIC repete o passo 2. Em três voltas o orçamento acaba.

Nas outras cinco aberturas da beta a janela caiu no monitor principal e ficou com 32 bits. Por isso o defeito parece intermitente: depende de onde estavam o ponteiro e o foco quando o app abriu.

## Outros achados do pacote

1. **A HUD nunca encosta na borda ao abrir no COSMIC.** O app pede `y=0` e a janela fica em `y=163`. Não é a #429: lá o KWin empurra a janela 48 px por causa do painel. Aqui a posição é descartada por inteiro.
2. **Na `41.10.0`, todo arrasto começa em `731,163`.** Vale mesmo quando o arrasto anterior terminou em `327,0` ou em `853,82`. Ou o COSMIC devolve a janela ao ponto dele depois do encaixe, ou o AWT só acredita nisso. O log de hoje não separa os dois casos.
3. **O pacote não lista os monitores** (limites, margens, profundidade padrão, se há configuração de 32 bits), e **o orçamento esgotado não deixa linha no log**. Hoje esse estado só se percebe pela ausência de uma quarta tentativa.
4. **Nenhum `arrasto hud` na beta.8.** Os cliques chegaram: às 17:24:37 há um `atualização de ANTHROPIC pedida` pelo anel. Não dá para saber se ele não tentou arrastar ou se não conseguiu.

## Plano

Branch `fix/issue-430-cosmic-compositor-placement`, numa worktree própria. Plano versionado em `docs/planos/hud-cosmic-compositor-430-execucao.md`, com a tabela de pontos de situação atualizada **no mesmo commit** de cada atividade. Uma atividade, um commit.

| # | Atividade | Muda comportamento? |
|---|---|---|
| A1 | Esta issue, a worktree, o plano em `docs/planos/` e os comentários na #416 e na #426 apontando para cá | não |
| A2 | Diagnóstico que faltou neste pacote | não, só leitura e log |
| A3 | A correção: renascer no monitor onde o compositor pôs a janela, **só no COSMIC** | sim, só no COSMIC |
| A4 | Medição aqui (Bazzite, KDE, dois monitores): não-regressão e simulação do COSMIC | não |
| A5 | Beta, pacote do relator, PR | não |
| A6 | Posição ignorada na abertura (achados 1 e 2), **condicionada aos dados da A2/A5** | a decidir com medida |

### A2: o diagnóstico que faltou

- **Lista de monitores no `environment.txt`**, uma linha por `GraphicsDevice`: id, limites, margens (`getScreenInsets`), profundidade da configuração padrão e se existe configuração de 32 bits com alfa. Isso transforma a dedução "2º monitor em `x=1920`" em medida e diz se o segundo monitor do COSMIC sequer oferece 32 bits.
- **Linha de orçamento esgotado**: `hud-alfa sem orçamento <monitor> … tentativas=3; recorte de emergência assume`, uma por ciclo de orçamento.
- **Movimento feito pelo sistema**, só no caminho Linux da HUD (`HudWindowFrame`): quando a janela aparece numa posição diferente da última que o app pediu, uma linha `hud-movida pedido X obtido Y`, com teto de linhas por janela. É a medida que falta para o achado 2: o COSMIC devolve a janela ao ponto dele depois do encaixe?
- Formatação em funções puras com teste, como `hudTranslucencyStateMessage`, e conferida contra o saneamento do log.

### A3: a correção

Função pura nova, testada:

```kotlin
/** O monitor em que a janela deve renascer, quando não é o configurado; `null` mantém. */
internal fun hudRebirthScreen(
    followsCompositor: Boolean,
    configuredScreenId: String?,
    actualBounds: Rectangle?,
    screens: List<ScreenInfo>
): ScreenInfo?
```

- Quando o alfa cai **fora de um arrasto**, a janela está majoritariamente noutro monitor (`screenForRect`, que já é a regra de "monitor de uma janela") e a adoção vale para o ambiente, a HUD **adota esse monitor**. `screenId` e `screenArea` passam a ser os dele e o monitor é gravado com `persistHudScreen`. A janela renasce com a configuração de 32 bits **daquele** monitor e com a posição encaixada nele, nunca com a posição antiga noutro monitor (a armadilha da A8 da #426).
- **Gravar é obrigatório**: o hover relê o monitor gravado (`HudWindow.kt:218`). Sem gravar, cada hover devolveria a HUD ao monitor antigo e o COSMIC a jogaria de volta, num laço a cada hover.
- **A adoção não zera o orçamento.** Hoje ele zera a cada troca de `screenId`. Se a adoção zerasse, um compositor que alternasse monitores viraria um pisca eterno. Só a troca vinda do usuário (soltar um arrasto) zera o orçamento.
- Linha nova no log: `hud-alfa adotar :0.0->:0.1 (a janela está ali) tentativa=N`.
- **Restrição de ambiente** (regra da #340): `hudFollowsCompositorScreen(platform, desktop)` é verdadeira **só** com Linux **e** `COSMIC` entre os itens de `XDG_CURRENT_DESKTOP` (lista separada por `:`, comparação sem caixa, lida por `linuxGraphicsEnvironment()`). Fora disso, `hudRebirthScreen` devolve sempre `null` e o renascimento é **byte a byte o de hoje**. Liberar para outro compositor que também ignore a posição (Sway, Hyprland…) é decisão nova, com medição.

### A4: medição aqui

Bazzite 44 · KDE Plasma · Wayland/XWayland · `DP-1 0,1440 3440x1440` e `DP-2 421,0 2560x1440`.

| # | Cenário | Esperado |
|---|---|---|
| K1 | KDE, abre no primário | 32bpp, nenhum `renascer`, nenhum `adotar` |
| K2 | KDE, abre no secundário | 32bpp, nenhum `adotar` |
| K3 | KDE, arrastar pela alça cruzando e voltando | igual à #426: 32bpp, sem `adotar` |
| K4 | `sh gradlew probeHudAlpha` | `RESULTADO: ok` |
| K5 | KDE **sem** a variável de COSMIC, janela movida por fora (`xdotool windowmove`) para o outro monitor | comportamento de hoje: renasce no monitor salvo, **sem** `adotar` |
| S1 | KDE **com** `XDG_CURRENT_DESKTOP=COSMIC`, janela movida por fora para o outro monitor (simula o COSMIC) | um `adotar`, um renascimento, 32bpp no monitor novo, sem laço; o hover seguinte não devolve a barra |
| S2 | S1 repetido, alternando os monitores | o orçamento acaba e a linha `sem orçamento` aparece; nada de pisca eterno |

S1 e S2 exercitam o mecanismo, não o COSMIC. Quem fecha a issue é o relator (A5).

### A5: beta e relator

- Beta nova pela skill `usage-monitor-release-beta`.
- Pedido ao relator na #416: abrir o app duas vezes, com o ponteiro no monitor principal e depois no secundário, gerar um pacote `--diagnostics` de cada e dizer o que viu (retângulo preto, barra no meio da tela, barra que não arrasta).
- Critério de sucesso: `adotar` seguido de `estado … capaz=true 32bpp` no monitor onde a janela está, e nenhuma linha `sem orçamento`.
- PR com a lista de plataformas testadas ("Platform reality", #342).

### A6: posição na abertura (condicionada)

O COSMIC centraliza a HUD em vez de encostá-la na borda. A A2 diz se um pedido de posição **depois** de a janela estar mapeada é respeitado e se dura. Os arrastos da `41.10.0` sugerem que é respeitado na hora, e o `731,163` no início de todo arrasto sugere que pode não durar. Com o dado na mão:

- se o pedido depois do mapeamento for respeitado e estável, reencaixar uma vez depois de a janela aparecer, só no COSMIC e com teto;
- se não for, registrar a limitação em `docs/hud-notch.md` e no texto da ajuda.

Nada aqui entra sem a medida.

## Como isto não quebra os outros sistemas

| Plataforma | O que muda | Garantia |
|---|---|---|
| Windows | nada | a HUD segue pelo `Window` de alto nível (`hudPicksGraphicsConfiguration` é só Linux); o renascimento nem é composto |
| macOS | nada | mesmo caminho do Windows |
| Linux não-COSMIC (KDE/Bazzite, GNOME, elementary) | só linhas de log novas | `hudFollowsCompositorScreen` devolve `false`, com teste por desktop (`KDE`, `GNOME`, `ubuntu:GNOME`, `Pantheon`, ausente). Medido em K1–K5 |
| Linux COSMIC | a adoção | o renascimento passa a convergir em vez de esgotar; teto de 3 mantido e sem reinício pela adoção |

Regras do `CLAUDE.md` que este plano respeita:

- comportamento nativo nasce restrito à plataforma medida, atrás de função pura com teste (#340);
- `window.shape` não é tocado;
- nenhuma `ComposeWindow(` fora de `HudWindowFrame.kt` e nenhuma atribuição de `background` (guardas da #428);
- `HudWindowHost` abaixo de 300 linhas e arquivos abaixo de 800 (o `HudWindow.kt` está em 632; a lógica nova vai para `HudWindowFrame.kt`/`HudWindowTranslucency.kt`, e não para o host).

## Relacionadas

- #416, o relato original (@tassianoalencar, Pop!_OS + COSMIC)
- #426, a perda de alfa ao cruzar monitores (corrigida para o KWin; este é o caso que ela não cobriu)
- #429, a HUD não encosta no topo no KDE (causa diferente: margens zero)
- #340, recorte e regra de restrição por plataforma
- #342, "Platform reality"

## Pontos de situação

| # | Atividade | Comando | Resultado |
|---|---|---|---|
| A1 | Issue #430 aberta com o diagnóstico do pacote `--diagnostics` do relator e o plano; worktree `../usage-monitor-430` na branch `fix/issue-430-cosmic-compositor-placement`; este plano; comentários na #416 e na #426 apontando para a #430 | `gh issue create`; `git worktree add ../usage-monitor-430 -b fix/issue-430-cosmic-compositor-placement origin/main` | issue #430 aberta; worktree criada a partir de `2d1c6ac1` |
| A2 | Diagnóstico, sem mudança de comportamento: linha por monitor no `environment.txt` (`screenEnvironmentLines`: id, limites, margens, profundidade padrão, se há configuração com alfa); `hud-alfa sem orçamento …` uma vez por ciclo de orçamento; `HudSystemMoveProbe` no caminho Linux da HUD (`HudWindowFrame`), que registra `hud movida pelo sistema: pedido … obtido …` quando a janela aparece fora do último pedido, fora do arrasto, com teto de 20 linhas por janela. `docs/hud-notch.md` atualizado | `sh gradlew desktopTest --tests "com.usagemonitor.HudWindowFactsTest" --tests "com.usagemonitor.HudWindowTranslucencyTest" --tests "com.usagemonitor.DiagnosticsExportTest"`; `sh gradlew desktopTest --tests "com.usagemonitor.architecture.*"` | 7 + 9 + 8 testes, 0 falhas (8 novos); regras de arquitetura 16, 0 falhas |
| A3 | A correção: `hudFollowsCompositorScreen` (Linux **e** `COSMIC` em `XDG_CURRENT_DESKTOP`) e `hudRebirthScreen` (o monitor com a maior parte da janela, quando não é o configurado), funções puras; `HudWindowRebirth.onAlphaLost` adota esse monitor, registra `hud-alfa adotar …`, e a troca causada pela adoção não zera o orçamento (`onScreenChanged`). A adoção estourou `HudWindowHost` (301 linhas, limite 300), então o monitor do notch saiu para `HudScreenSelection.kt` (`reload` no hover, `moveTo` no arrasto e na adoção, que grava). Os lambdas do gesto passaram a ler `screen.area` na hora, como liam o `var` antes. `docs/hud-notch.md`, tabela de plataformas e `CLAUDE.md` atualizados | `sh gradlew desktopTest --tests "com.usagemonitor.HudWindow*" --tests "com.usagemonitor.architecture.*"` | verde: `HudWindowRebirthTest` novo com 9 testes (desktops que **não** seguem: `KDE`, `GNOME`, `ubuntu:GNOME`, `Pantheon`, `X-Cinnamon`, vazio, ausente; Windows/macOS nunca; orçamento que não reabastece na adoção; esgotamento registrado uma vez). Primeira passada pegou o limite de 300 linhas do host, corrigido pela extração |
| A4 | Medição aqui (Bazzite 44, KDE Plasma, Wayland/XWayland, `DP-1 0,1440 3440x1440` e `DP-2 421,0 2560x1440`), com o app da branch em `--user-data-dir` de rascunho (nó de preferências e instância isolados; o app instalado seguiu aberto). **K1** abre no primário: `estado :0.0 capaz=true 32bpp`, nenhum `renascer`/`adotar`, `xwininfo` `depth=32`. **K2** abre com a HUD gravada no secundário: `:0.1 32bpp`, nenhum `renascer`/`adotar`. **K4** sonda: `RESULTADO: ok`. **K5** KDE, janela movida por fora para o outro monitor: `renascer :0.1 … tentativa=1` e volta ao monitor gravado com 32bpp, **sem** `adotar` — o caminho de antes intacto. **S1** `XDG_CURRENT_DESKTOP=COSMIC` (o `startup.jsonl` registrou `desktop=COSMIC`), janela movida por fora: `adotar :0.0->:0.1 (a janela está em 1200,300) tentativa=1` → `estado :0.1 capaz=true 32bpp em 2308,0`, encaixada no topo do secundário, monitor gravado no nó isolado (`hudScreenId=:0.1`). **S2** alternando: `adotar` tentativas 2 e 3, depois `sem orçamento :0.0 … tentativas=3` **uma** vez e nada mais. A sonda nova já mostrou o KWin empurrando a janela 48 px no topo (`hud movida pelo sistema: pedido 2308,0 obtido 2308,48`), o caso da #429. `--diagnostics` trouxe `screens=2` e as duas linhas `screen.N`. **K3 não medido**: arrasto pela alça por mão humana, cruzando os monitores — o `xdotool` não entrega ponteiro confiável a janela XWayland no KWin | `sh gradlew allTests`; `sh gradlew probeHudAlpha`; `sh gradlew run --args="--user-data-dir …"` (com e sem `XDG_CURRENT_DESKTOP=COSMIC`) + `xdotool windowmove` + `xwininfo`; `--diagnostics` | **2939 testes, 0 falhas, 0 erros, 0 ignorados**; K1, K2, K4, K5, S1 e S2 como esperado; K3 pendente |
