# Aderência às issues da HUD (#265, #273–#278) — execução

## Contexto

Sete issues abertas, quase todas em volta da barra HUD. Juntas, pedem que ela vire a porta de entrada
do produto: modo padrão, destaque no README, anéis legíveis e sinais de sessão. Também pedem que ela
funcione em mais de um monitor e mostre cada conta com cor própria. O levantamento no código
(2026-09-25, base `edc1d53`) confirmou as sete e achou as causas:

| Issue | Diagnóstico |
|---|---|
| [#273](https://github.com/edilsonvilarinho/usage-monitor/issues/273) multi-monitor | Toda medida de tela lê o **monitor primário**. `fullScreenAreaDp()` usa `defaultScreenDevice`; `availableWindowAreaDp()` e `availableWindowSizeDp()` usam `maximumWindowBounds`. As três são lidas uma vez em `Main.kt`. Na HUD, o arrasto passa por `fitWindowPosition(..., workArea = hudScreenArea)` e o encaixe por `nearestHudPlacement(area = hudScreenArea)`: o notch não consegue sair do primário. `HudPlacement` guarda só borda + fração. As janelas com posição salva (Histórico, Sessões CLI, Uso e Presença do time) são presas ao primário ao restaurar, e a janela principal nem salva posição. |
| [#274](https://github.com/edilsonvilarinho/usage-monitor/issues/274) "reiniciar" | "reinicie o app" e "Reiniciar e atualizar agora" não dizem **o quê** reiniciar (`DashboardScreenWarnings.kt`, `NetworkSettingsSection.kt`, `HelpCatalog.kt`). Os textos de reset de cota ("Reinicia 22h59") são outro sentido e não mudam. |
| [#275](https://github.com/edilsonvilarinho/usage-monitor/issues/275) cor por conta | Só a Anthropic tem várias contas (`UsageTargetKey` só aceita `profileId` para ela). Nenhum modelo tem campo de cor, e o acento sai da fonte (`accentColorFor`). O design system diz que o acento é a identidade do **fornecedor**. |
| [#278](https://github.com/edilsonvilarinho/usage-monitor/issues/278) anéis | `HudAccount.rings = quotas.take(3)` segue a ordem da API, e o índice 0 é o anel de fora (`AppUsageRing`): a 5h fica por fora e a semanal por dentro. O pulso de atenção fica preso ao índice 0. |
| [#277](https://github.com/edilsonvilarinho/usage-monitor/issues/277) HUD padrão | `readPersistedHudMode` tem default `false`. A instalação nova sobe **sem API habilitada** (`DEFAULT_ENABLED_APIS = emptySet()`). Uma HUD logo no primeiro arranque ficaria em "Carregando" para sempre, sem ter o que configurar. |
| [#276](https://github.com/edilsonvilarinho/usage-monitor/issues/276) / [#265](https://github.com/edilsonvilarinho/usage-monitor/issues/265) README | Nenhum dos dois READMEs cita "HUD" nem "notch". Nenhum gerador de captura do README desenha a HUD; só o `HelpMediaGenerator` desenha. |
| [#265](https://github.com/edilsonvilarinho/usage-monitor/issues/265) sinais | A HUD recebe `activeTargets` e os pulsos só para piscar botões do balão, e `stalledSessions` não chega a ela. A palavra "Atenção" já é do risco de cota e colidiria com a saúde de contexto. Não existe estado "aguardando usuário": `PENDING_REQUEST` é o pedido do usuário esperando o **modelo**, o contrário disso. |

## Decisões do usuário

1. **#277: a HUD vira padrão só na instalação nova, e só depois da configuração.** O app abre no modo
   padrão e troca para a HUD **uma vez**, na primeira coleta bem-sucedida. Quem já usa o app não é
   afetado.
2. **#275: paleta fixa validada**, não seletor livre. Oito cores nomeadas, com uma variante clara e
   uma escura, contraste AA medido em teste, mais a opção "Padrão", que mantém o acento da fonte.
3. **#265 nesta rodada: README + demo da HUD e sinais de sessão na HUD.** A sondagem de integrações
   (Cursor, Copilot) e a notarização do DMG macOS ficam para depois.
4. **Um PR por issue**, na ordem de risco. O README fica por último, para as capturas já mostrarem
   tudo.

## Atividades

| # | Issue | Atividade | Branch | Estado |
|---|---|---|---|---|
| P0 | — | Plano de aderência | — | feito |
| P1 | #274 | Deixar claro que é reiniciar o Usage Monitor | `fix/274-restart-wording` | feito (automática); olhar no app pendente |
| P2 | #278 | Semanal por fora, 5h por dentro, legenda dos anéis | `feat/278-hud-ring-order` | feito; conferido no app |
| P3 | #273 | HUD e janelas em monitores secundários | `fix/273-multi-monitor` | feito (automática); dois monitores reais não executada |
| P4 | #275 | Cor por conta Claude | `feat/275-account-color` | feito (automática); olhar no app pendente |
| P5 | #265 | Sinais de sessão na HUD | `feat/265-hud-session-signals` | feito; conferido no app |
| P6 | #277 | HUD padrão na instalação nova | `feat/277-hud-default` | feito; instalação limpa simulada no app (notificação não confirmada) |
| P7 | #276, #265 | README com a HUD em destaque, captura e GIF | `docs/276-readme-hud` | feito |

Regra comum a todos os PRs:
- Conventional Commits em inglês com o trailer do modelo.
- A linha do PR nos Pontos de situação, no mesmo commit da atividade.
- Se a tela mudar, o PR também atualiza o protótipo e o `docs/design-system/`.
- Se alguma regra mudar, atualiza o `CLAUDE.md`.

## Pontos de situação

| Data | Atividade | Modelo | Comando | Resultado |
|---|---|---|---|---|
| 2026-09-25 | P0 | Claude Opus 5.5 | `gh issue view` 265, 273–278; leitura de `WindowScreenFit.kt`, `HudWindow.kt`, `HudModel.kt`, `AppUsageRing.kt`, `HudModePreferences.kt`, `AnthropicProfileRegistry.kt`, `AppAccents.kt`, `DashboardScreenWarnings.kt` e READMEs | Diagnóstico acima. Nenhum código alterado. |
| 2026-09-25 | P1 | Claude Opus 5.5 | `gradlew.bat desktopTest --tests "…HelpCatalogTest" --tests "…AppUpdateBannerTest" --tests "…HudNotch*" --tests "…HudNotchGeometryTest"`; depois `gradlew.bat allTests` | Primeira passada vermelha, e de propósito: o teste de encaixe novo reprovou o título em inglês "…it will be applied when Usage Monitor closes" (três linhas no balão, nas 23 escalas). Encurtado, 72 testes verdes. `allTests`: 214 classes, **2141 testes, 0 falhas** (11m36s). Pendente: olhar a faixa e o balão no `gradlew.bat run`. |
| 2026-09-25 | P2 | Claude Opus 5.5 | `gradlew.bat desktopTest --tests "…HudModelTest" --tests "…HudNotch*" --tests "…HudNotchGeometryTest" --tests "…ComponentTest"` | 5 classes, **178 testes, 0 falhas**. Entre eles, o arco de fora medido no bitmap com o tom da semanal crítica, o glifo de legenda por cota e a descrição "anel externo 7d 9% · anel interno 5h 28%". Depois `gradlew.bat allTests`: 214 classes, **2150 testes, 0 falhas** (9m28s). Pendente: olhar o notch e o balão no `gradlew.bat run`. |
| 2026-09-25 | P3 | Claude Opus 5.5 | `gradlew.bat desktopTest --tests "…ScreenLocatorTest" --tests "…MainWindowPreferencesTest" --tests "…HudWindowPreferencesTest" --tests "…WindowScreenFitTest" --tests "…HudNotchGeometryTest"` | Primeira passada: erro de compilação (`GraphicsDevice.idString` não existe em Kotlin, o getter Java é `getIDstring()`). Corrigido: 5 classes, **61 testes, 0 falhas**, com monitor à direita, à esquerda (x negativo), acima e renumerado. **Validação em dois monitores reais não executada**: esta máquina tem um só (`\\.\DISPLAY1`, 1366×768). Depois `gradlew.bat allTests`: 215 classes, **2164 testes, 0 falhas**. |
| 2026-09-25 | P4 | Claude Opus 5.5 | `gradlew.bat desktopTest --tests "…AppAccentsContrastTest" --tests "…HudModelTest" --tests "…AnthropicProfileRegistryTest" --tests "…ComponentTest" --tests "…HudNotch*"` | Primeira passada: erro de compilação no teste (inferência de `listOf` contra `Pair<String, AccountAccent?>`). Corrigido: 6 classes, **180 testes, 0 falhas**, cobrindo as 16 variantes AA, a matiz e a distância entre cores, a ida e volta no registro, a escolha na aba Contas e a dona única `accountAccentColor`. Depois `gradlew.bat allTests`: 215 classes, **2172 testes, 0 falhas**. |
| 2026-09-25 | P5 | Claude Opus 5.5 | `gradlew.bat desktopTest --tests "…HudSessionSignalsTest" --tests "…HudModelTest" --tests "…HelpCatalogTest" --tests "…HudNotch*" --tests "…HudNotchGeometryTest"` | Verde na primeira passada de compilação. A primeira versão do teste esperava "2h 10min", mas o app formata "2h10" (`formatActiveTime`), e o teste foi corrigido para o formato real antes de rodar. Focado: 6 classes, **97 testes, 0 falhas**. `allTests` **vermelho na primeira passada**: `HelpContentTest > shows the selected topic with its description and activation steps` — a descrição do tópico Modos de janela, com as frases novas sobre os anéis e as sessões, empurrou "Como ativar" para baixo da dobra, que é o que o teste guarda. As frases viraram passos no fim da lista, e a descrição voltou ao tamanho de antes. Segunda passada: 216 classes, **2181 testes, 0 falhas**. |
| 2026-09-25 | P6 | Claude Opus 5.5 | `gradlew.bat desktopTest --tests "…HudModePreferencesTest" --tests "…HudModelTest" --tests "…HelpCatalogTest" --tests "…HudNotch*"` | Verde na primeira passada: 5 classes, **82 testes, 0 falhas** — instalação nova, instalação existente, recibo de atualização, pendência apagada, a decisão pura, "Nenhuma API" nos dois idiomas e na varredura de encaixe. `allTests` antes do rebase: 1 falha, o mesmo `HelpContentTest` do P5, porque a frase da instalação nova também alongava a descrição. Depois de rebasear sobre a pilha mesclada, a frase virou o último passo do tópico e a suíte deu 216 classes, **2187 testes, 0 falhas**. |
| 2026-09-25 | P7 | Claude Opus 5.5 | `gradlew.bat generateScreenshots generateHelpMedia generateTourGif`; depois `gradlew.bat desktopTest --tests "…HelpMediaResourcesTest" --tests "…HelpMediaPlayerTest" --tests "…HelpContentTest" --tests "…HudNotch*" --tests "…ComponentTest"` | 16 capturas, `img/hud.gif` (29 quadros, 64 KB), 12 demos da ajuda e o tour regenerados. Conferidos a olho: `hud.png`, `hud-rest.png` e `dashboard.png`. Na primeira geração, `hud.png` e `hud-rest.png` sobravam altura e foram regerados. Testes focados: **149 testes em 4 classes, mais 8 nas 2 classes da mídia da ajuda, 0 falhas**. Mudança só de documentação, fixtures e mídia: sem `allTests`. |
| 2026-09-25 | Verificação no app | Claude Opus 5.5 | `gradlew.bat run` no worktree do P7 (a pilha inteira), com captura de tela por PowerShell; depois instalação limpa simulada: backup com `reg export`, app instalado fechado sem forçar, chave `com.usagemonitor` apagada e recriada só com `enabled/Apis=CODEX`, recibo `update-receipt.properties` fora do lugar, `gradlew.bat run`; no fim `reg import` do backup, recibo de volta e app instalado reaberto | **HUD real** (borda direita, contas reais): no Codex a semanal fica por fora e a janela curta por dentro; o balão da Anthropic mostra os glifos de legenda, "Semanal 70%" crítica no anel de fora e a seção **Sessões CLI — Contexto saturado · 1 sessão**, com dado real. **Instalação limpa:** o registro de arranque mostra `window-shown` (janela padrão primeiro); depois o registro tem `hud/Mode=true` e nenhum `hud/Default/Pending`, e o notch aparece no topo em 82%. A notificação da bandeja **não apareceu na captura**, e por isso não é afirmada aqui. Preferências e recibo restaurados; o app instalado voltou na HUD da borda direita. **Não verificado:** a faixa de atualização e o balão da engrenagem com atualização pendente (nenhuma atualização disponível), a cor por conta ao vivo (esta máquina tem uma conta Claude só) e os dois monitores (a máquina tem um só). A primeira leitura da instalação limpa foi descartada: o processo anterior ainda regravava o cache de preferências do Java ao sair, e a restauração passou a esperar o processo terminar. |

## P1 · #274 — Reiniciar o Usage Monitor, não o computador

- `updateBannerContent` (Ready):
  - ação: "Reiniciar o app e atualizar" / "Restart app and update", em
    `UPDATE_RESTART_ACTION_PT`/`_EN`;
  - título: "Versão X pronta — será aplicada ao fechar o Usage Monitor" / "Version X is ready —
    applies when Usage Monitor closes".
- **O rótulo diz "o app", e não "o Usage Monitor" como o plano previa.** Medido no desenho: a faixa
  do modo padrão é de uma linha, e quem cede espaço é o título (`AppBanner`, issue #67). Com o nome
  inteiro o rótulo passava de ~209dp para ~281dp, e numa janela de 400dp sobravam ~44dp de título.
  No balão da HUD ele exigia uma segunda linha. "o app" já tira a ambiguidade, e o nome vai no
  título. Com isso a geometria da HUD não muda.
- **O título em inglês foi encurtado por medição.** "…it will be applied when Usage Monitor closes"
  ocupava três linhas no balão, e a reprovação veio do teste de encaixe novo.
- A mudança chega sozinha à faixa do modo padrão e ao balão da engrenagem da HUD, porque os dois leem
  a mesma dona (`updateBannerContent`/`updateBannerAction`).
- Os avisos de conectividade e de proxy 407, o aviso do Antigravity e o texto da aba Rede dizem
  "reinicie o Usage Monitor". Onde há espaço, acrescentam "(não é preciso reiniciar o computador)".
- `HelpCatalog` cita o rótulo novo, porque o passo da ajuda precisa apontar para o texto real do
  botão.
- `HudNotchTextFitTest` ganhou um caso que mede a frase (duas linhas) e a ação (uma linha) do
  balão da engrenagem contra a geometria, nas escalas de 100% a 200% e nos dois idiomas.
- `AppUpdateStrip.prompt.md` atualiza o exemplo.
- Testes:
  - atualizar `AppUpdateBannerTest` e `HudNotchTest`;
  - novo caso em `HelpCatalogTest` afirmando que o passo cita o rótulo de `updateBannerContent`.

## P2 · #278 — Semanal por fora, 5h por dentro

- `HudAccount.rings` escolhe as mesmas até três cotas e as ordena **da janela mais longa para a mais
  curta**: MONTHLY > WEEKLY > INTERVAL. REPORTED (créditos e saldo, sem janela confiável) fica mais
  para dentro.
  - A ordenação é estável: os dois grupos WEEKLY do Antigravity mantêm a ordem.
  - `quotas`, `focusIndex` e o balão continuam na ordem do card.
- O pulso de atenção passa do índice 0 fixo para o anel da cota em foco (`attentionIndex`). Sem isso
  ele pulsaria a semanal mesmo com a 5h crítica.
- **Legenda no balão:** cada linha de cota ganha um glifo de 14dp com os anéis concêntricos e só o
  anel daquela cota aceso, na cor do texto (o estado já está na barra). Em 12dp, com três anéis, o de
  dentro sobrava com 0,75dp de raio. Conta de um anel só não tem glifo, porque não há posição a
  apontar. A altura da linha não muda.
- `HudQuota` ganhou `periodType`, porque a posição do anel depende da janela e o modelo da HUD não a
  guardava.
- A descrição de acessibilidade diz a posição de cada cota: "anel externo: 7d 40% · interno: 5h 68%".
- Testes:
  - `HudModelTest`: 5h/7d vira `rings` 7d, 5h; créditos ficam por dentro; o Antigravity é estável;
  - um teste de bitmap afirmando que o arco externo tem o tom da cota semanal;
  - glifo e geometria no `HudNotchTest`.
- Docs: `CLAUDE.md` (a seção HUD hoje diz "o de fora é a primeira cota da API"), o KDoc de
  `AppUsageRing`, o design system e o protótipo.

## P3 · #273 — Monitores secundários

- **Novo `ScreenLocator.kt`:**
  - lista `screenDevices` como `ScreenInfo(id, bounds, workArea)`; a área útil desconta
    `getScreenInsets`;
  - funções puras: `screenAt(point)`, `screenForRect(rect)` (a de maior interseção) e
    `resolveScreen(savedId, savedBounds)` (primeiro por id, depois por bounds, por fim o primário).
- **HUD:**
  - `HudPlacement` ganha o monitor, gravado em `hudScreenId`/`hudScreenBounds`. O bounds existe
    porque o Windows renumera `\\.\DISPLAYn` quando um monitor é reconectado;
  - no arrasto, o limite vem do monitor sob o ponteiro (`MouseInfo.getPointerInfo().device`);
  - no encaixe, `nearestHudPlacement` usa esse monitor e grava o id dele;
  - o monitor é resolvido de novo no arranque e a cada abertura por hover. Se ele sumiu, a HUD cai
    no primário sem apagar o que foi gravado, e volta quando ele reaparece. Posição antiga sem monitor
    continua valendo no primário.
- **Janelas com posição salva:** `fitWindowPosition` usa a área útil do monitor que contém o
  retângulo salvo, e não mais a do primário. O tamanho é ajustado a esse mesmo monitor.
- **Janela principal:**
  - passa a salvar `windowX`/`windowY`, com coordenadas negativas permitidas (monitor à esquerda);
  - grava só em FLOATING e fora do modo HUD, como o coletor atual;
  - o piso de tamanho e a correção de escala usam o monitor da janela (`graphicsConfiguration`).
- **O que ficou de fora:** o piso de tamanho (`ApplyWindowMinimumSize`) e a correção de escala
  (`availableWindowSizeDp`) continuam medindo o monitor padrão. Os dois só limitam o tamanho máximo,
  nunca a posição, e o pior caso é a janela ficar um pouco maior que o monitor secundário menor.
- **Risco de DPI misto.** No Windows cada monitor tem o próprio espaço de usuário escalado, e o
  código trata px como dp. Só uma medição em dois monitores reais, de preferência com escalas
  diferentes, valida isso; ela é registrada aqui. Os testes unitários não pegam.
- Testes:
  - `ScreenLocatorTest`: monitor à direita, à esquerda (x negativo) e acima; id que sumiu; bounds
    batendo com id trocado;
  - geometria e preferências da HUD com área de origem deslocada.

## P4 · #275 — Cor por conta Claude

- **Enum novo `AccountAccent`**, com oito cores (Azul, Ciano, Verde, Lima, Âmbar, Laranja, Rosa,
  Violeta), cada uma com variante clara e escura. `null` quer dizer "Padrão".
- **Persistência:**
  - chave `color` no nó do perfil em `AnthropicProfileRegistry`;
  - `setColor` pelo mesmo `update()` de `setEnabled`;
  - valor inválido lido do disco vira `null`.
- **Configurações → Contas:**
  - a parte expandida do perfil ganha a linha "Cor", com amostras rotuladas e marca de seleção além
    do realce, porque cor nunca informa sozinha;
  - a linha recolhida ganha o marcador de 2dp na cor da conta.
- **Onde a cor aparece.** A dona única é `accountAccentFor(targetKey, …)`, que devolve a cor da conta
  ou cai no `accentColorFor`. O mapa `profileId → AccountAccent` desce de `Main.kt` por parâmetro,
  sem passar por `ApiUsageStats`. Ela vale para:
  - o card: marcador de 2dp e marca do fornecedor no cabeçalho;
  - a HUD: um marcador de 2dp sob o anel no notch e a marca no cabeçalho do balão. A marca no miolo
    do anel **continua** na cor do texto, porque ali o acento competiria com a cor de risco dos
    arcos;
  - o cabeçalho do histórico aberto a partir daquela conta.
- **Design system:** regra nova no `readme.md`. A cor da conta substitui o acento do fornecedor só
  onde ele já aparece (marcador, marca, linha de gráfico) e nunca pinta superfície. O componente
  `AccountColorPicker.prompt.md` e o protótipo também são atualizados.
- Testes:
  - `AppAccentsContrastTest` cobre as oito cores: AA 4,5:1 nas duas superfícies, a mesma matiz
    (±30°) entre claro e escuro, e ≥ 20° de distância entre elas;
  - `AnthropicProfileRegistryTest`: roundtrip e valor inválido;
  - `ComponentTest`: seleção na aba Contas e cor no marcador do card (medida em bitmap);
  - `HudNotchTest`: geometria com o marcador.
- **Como ficou, e os desvios com o motivo:**
  - Paleta medida antes de entrar: escura com contraste ≥ 7:1 contra `#1B1818`, clara com ≥ 5,2:1
    contra `#FFFCFC`. Matizes 22° (laranja), 45° (âmbar), 88° (lima), 140° (verde), 188° (ciano),
    218° (azul), 268° (violeta) e 335° (rosa).
  - A dona única se chama `accountAccentColor`.
  - **Na HUD, o marcador de 2dp sob o anel virou a marca do miolo tingida, só com escolha.** O
    marcador mudaria `hudNotchSizes` e dividiria espaço com a órbita de sessão ativa. Sem escolha, o
    miolo continua na cor do texto. Por isso não há teste de geometria do marcador.
  - **O cabeçalho do Histórico não usa a cor.** A janela é aberta por `UsageAccountKey`, a conta do
    provedor, e não pelo perfil local que guarda a cor. Ligá-los pede uma ponte perfil ↔ conta que
    hoje não existe ali.
  - O marcador do card não foi medido em bitmap: o teste afirma a cor que `accountAccentColor`
    entrega ao card, e o card já usa esse valor nos dois lugares.
  - A primitiva é `AppSwatchChip` (`components/forms/`), e o seletor é a composição dela na aba
    Contas, em vez de um `AccountColorPicker.prompt.md`.

## P5 · #265 — Sinais de sessão na HUD

- `HudAccount` ganha dois campos, calculados por `buildHudAccounts`, que continua sendo função pura:
  - `contextHealth`: a contagem de sessões ativas em atenção ou saturadas, tirada do pulso daquele
    alvo;
  - `stalled`: a contagem e a espera mais longa, tiradas de `stalledSessions` filtrado por perfil
    (perfil nulo é o padrão).
- O balão ganha a seção "Sessões CLI" abaixo das cotas, só quando há sinal:
  - "Contexto: 1 saturada · 2 em atenção" (`healthTally`);
  - "Sem resposta há 2h 10min" (`stalledLabel`).
- Os prefixos "Contexto" e "Sem resposta" separam esses sinais da palavra de risco da cota. Um teste
  afirma que nenhum texto da HUD diz "aguardando você" ou "aguardando usuário".
- O notch em repouso não muda: o pulso âmbar continua sendo só do risco de cota, e a notificação de
  sessão já sai pela bandeja. A descrição do anel leva os mesmos sinais.
- A altura da seção entra em `hudBalloonHeight`.
- **Como ficou:** uma linha por sinal, com palavras próprias — "Contexto saturado · 1 sessão",
  "Contexto crescendo · 2 sessões", "Sem resposta há 2h10" (a frase de `CliSessionsLabels`), e com
  várias "3 sem resposta · até 3h20". Frases como "Contexto: 1 saturada · 2 em atenção" passavam de
  uma linha nos 240dp do balão, e "atenção" é a palavra do risco de cota. Sessão sem resposta com
  **perfil nulo não acende conta nenhuma**, em vez de cair no perfil padrão como o plano previa: conta
  nula não é "todas as contas". A ajuda (tópico Modos de janela) passa a descrever a ordem dos anéis,
  a seção nova e o arrasto entre monitores.
- Testes: agregação e perfil nulo no `HudModelTest`; textos PT/EN, altura e a ausência de
  "aguardando" no `HudNotchTest`; e um passo novo no tópico HUD da ajuda.

## P6 · #277 — HUD padrão na instalação nova

- **Detecção**, feita no topo de `main()`, antes de qualquer gravação: é instalação nova quando
  `hudMode` e `windowPlacement` estão ausentes e não há recibo de atualização (a mesma regra de
  `ReleaseNotesDecision`). Nesse caso o app grava `hudDefaultPending = true`.
- **Troca:**
  - na primeira coleta `Success` com ao menos uma cota para a HUD, e sem janela modal aberta, o app
    chama `setHudMode(true)` e apaga a pendência;
  - uma notificação da bandeja, uma vez só, diz como voltar: `Ctrl+Shift+H`, o menu da bandeja ou a
    engrenagem;
  - a decisão é uma função pura, `hudDefaultDecision(pending, state, modalOpen)`, com teste.
- Escolha manual de modo antes da troca apaga a pendência: a escolha do usuário vence.
- **Proteção:** com `NoApisEnabled`, a HUD deixa de dizer "Carregando" e passa a dizer "Nenhuma API".
  O balão da engrenagem já leva às Configurações.
- O KDoc de `HudModePreferences` e o `CLAUDE.md` passam a registrar a razão nova do default.
- **Como ficou:**
  - A decisão pura se chama `hudDefaultShouldSwitch(pending, hasHudAccounts, modalOpen)`: "ao menos
    uma conta" é a lista de cotas da HUD não vazia.
  - **A troca mora no bloco da bandeja**, e não solta em `main()`. A notificação é da bandeja, e a
    bandeja é um dos caminhos de volta; sem ela o app não troca sozinho.
  - O texto da notificação fica numa função fora de `main()`, que está no limite do backend JVM.
  - "Nenhuma API" sai de `hudFallbackLabel`, com teste, e entra na varredura de encaixe.
  - A instalação limpa **real** (nó de preferências vazio no registro) não foi executada. Ela apagaria
    as preferências desta máquina, e isso pede autorização. A regra é coberta pelos testes de
    preferência com nós isolados.

## P7 · #276 + #265 — README com a HUD em destaque

- **Mídia com dados sintéticos:**
  - `img/hud.png` (balão aberto) e `img/hud-rest.png` (notch recolhido), gerados pelo
    `ScreenshotGenerator`;
  - `img/hud.gif`, gerado pelo `SceneRecorder` a partir da cena de `recordWindowModes`.
  - Nas capturas entram uma conta colorida e uma com sinal de sessão.
- **`README.md` (canônico) e `README.pt-BR.md`, com a mesma estrutura:**
  1. a HUD no topo;
  2. um parágrafo "Barra HUD": anéis, balão, cor por conta, sinais de sessão, como alternar os modos
     e a troca automática na instalação nova;
  3. "Por que o Usage Monitor": HUD, custo por sessão, histórico e previsão, e time, cada um com
     captura;
  4. "Início rápido" com download por sistema. Os detalhes continuam nos `<details>`.
- Rodar `generateScreenshots`, `generateTourGif` e `generateHelpMedia`, e conferir as imagens a olho.
- **Como ficou:**
  - O `img/hud.gif` sai do próprio `ScreenshotGenerator`, pelo `SceneRecorder`: notch parado, balão da
    primeira conta, da segunda e notch de novo. Não há tarefa Gradle nova. A troca de conta recria o
    notch com `key`, porque `initialBalloonIndex` só vale na primeira composição.
  - Os fixtures da HUD ganharam um sinal de sessão na primeira conta e a cor violeta na segunda. O
    dashboard da captura recebe `accountColors`, então as duas contas Claude aparecem com cores
    diferentes.
  - **O olho pegou dois ajustes:** a primeira captura aberta tinha 430dp de altura, e o balão mais
    alto termina em ~316dp, o que deixava um terço de fundo vazio. O notch parado tinha 110dp para
    52dp de conteúdo. As alturas caíram para 332dp e 64dp.
  - **Risco anotado, sem correção neste PR:** o violeta de conta (268°) fica na mesma matiz do acento
    do DeepSeek (270°). A regra de distância de 20° vale **entre** as cores de conta e **entre** os
    acentos de fonte, não de uma lista para a outra. Quem separa as duas é o título, como já separa
    OpenCode Zen e Go.

## Verificação

- **Em cada PR:**
  - teste focado (`gradlew.bat desktopTest --tests "com.usagemonitor.Hud*"`, `…ui.*`,
    `…presentation.*`);
  - depois `gradlew.bat allTests` e `git diff --check`;
  - comando e resultado registrados nos Pontos de situação.
- **Manual** (`gradlew.bat run`):
  - P3 em dois monitores reais: arrastar a HUD ao secundário, reiniciar, desconectar e reconectar o
    monitor; abrir o Histórico no secundário e reiniciar;
  - P4 nos temas claro e escuro;
  - P6 com o nó de preferências limpo, só com autorização explícita, porque apagá-lo perde as
    preferências de quem testa.
- Linux e macOS não estão disponíveis na máquina de desenvolvimento. Essas validações ficam como
  "não executada", sem ser declaradas.
