---
name: usage-monitor-visual-options
description: Generate N animated HTML options side by side for a visual or animation change in Usage Monitor (HUD Gargantua, balloons, cards, data, screens) so the user picks a direction before any Kotlin is written. Use when the user asks for "opções", "modelos", "exemplos em HTML", "me dá N versões" of a visual change, or invokes /usage-monitor-visual-options with what to change and how many examples.
---

# Usage Monitor — opções visuais em HTML

O usuário escolhe mudança visual **vendo**, nunca no escuro: duas rodadas de visual feito direto no
Kotlin foram recusadas ("muito 2D", "disco chamativo"), e todas as rodadas com opções lado a lado
saíram escolhidas de primeira. O objetivo dele é a HUD "mais bonita, tema espacial", sem perder a
leitura do dado. Esta skill recebe **o que mudar** e
**quantas opções** (ex.: `/usage-monitor-visual-options animação do balão 5`) e entrega N protótipos
animados lado a lado, no tema vigente, para ele escolher — e só depois disso vem o Compose.

Sem número no pedido, faça **5**. Com menos de 3, faça 3.

## 1. Antes de desenhar

1. Ler o tema vigente e o histórico do que já foi escolhido e recusado — a tabela no fim desta skill
   é a fonte. **Não reproponha uma opção já recusada com o mesmo nome ou o mesmo gesto.**
2. Ler a seção da tela em [`docs/hud-notch.md`](../../../docs/hud-notch.md) (HUD) ou em
   [`docs/presentation.md`](../../../docs/presentation.md) (demais telas), e o contrato da primitiva em
   `docs/design-system/components/**/*.prompt.md`. A skill `usage-monitor-design` tem a precedência.
3. Olhar o que existe hoje no código (tempo, curva, o que anima) — vira o card **"Hoje
   (referência)"**, tracejado, quando a mudança substitui algo.
4. Se o usuário mandou print, reproduzir o conteúdo real dele (textos, contas, valores) no mockup.

## 2. Regras de criação (valem para toda opção)

- **Vocabulário Gargantua**: luz quente (`--gargantua-gold #E8AE63`, `--gargantua-hot #FFF0CB`,
  `--gargantua-ember #B4501E`), núcleo escuro (`#030508`), plasma com núcleo claro, clarão, ondas
  finas, lente, disco com Doppler (quente de um lado, brasa do outro). Cada opção nomeada por um
  fenômeno ("B3 · Jato relativístico", "D5 · Horizonte de eventos") com prefixo de letra da rodada.
- **Dado nunca anima errado**: comprimento de barra/arco não cresce por efeito, número não passa do
  valor (sem overshoot/rebote), cor nunca informa sozinha. Efeito é por cima do dado, não no lugar dele.
- **Cenário discreto**: o usuário recusou efeito "chamativo" (disco forte, 6 faixas). Efeito não
  compete com o dado nem com o cometa azul de sessão.
- **Finito por padrão**: transição tem duração em ms (tipicamente 180–800). Animação infinita só se o
  pedido for de estado contínuo, e ela mora atrás de `AppMotionPolicy.continuous` no app.
- **"Reduzir animações" = corte seco** em todas; diga isso no texto.
- **Geometria não muda por quadro**: nada de janela ou notch redimensionando; revelar é recorte ou
  desenho por cima.
- Opções **distintas entre si** no gesto (não cinco variações de fade). Se duas ficarem parecidas,
  troque uma.
- Mesma animação aplicada a todas as superfícies pedidas (ex.: balão da conta **e** da engrenagem).

## 3. Como montar o HTML

- Um arquivo em `build/gargantua-preview/<tema>-options.html` (pasta ignorada pelo git), fundo
  escuro, IBM Plex Mono/Consolas, um card por opção com: nome curto, 1–3 frases do que acontece em
  cada fase, tempos em ms. Card "Hoje (referência)" tracejado primeiro.
- **Anel real**: colar [`assets/gargantua-ring.js`](assets/gargantua-ring.js) inline (é o mesmo
  desenho do `AppGargantuaRing`; `drawRing(ctx, acc, phase, overrides)`), anel de 64 px (1× o app) —
  ou 1,5× quando o foco é o próprio anel.
- **Loop sozinho**: cada palco roda fechado → animando → parado → saindo, com a fase escrita no canto
  ("abrindo · 520 ms"). Botão/checkbox **"câmera lenta ×3"** e, quando fizer sentido, "Atualizar agora".
- Um hook de depuração `window.FREEZE = t` que congela todos os palcos no quadro `t` ajuda a conferir.
- Exemplo completo que funcionou: [`assets/example-balloon-options.html`](assets/example-balloon-options.html)
  (5 aberturas de balão + referência, dois palcos por opção).

## 4. Conferir e mostrar

1. Abrir o arquivo no painel do navegador (`mcp__Claude_Browser__*`), ler o console (sem erro) e
   tirar 2–3 screenshots no meio das animações (`FREEZE`) para ver que cada efeito aparece de fato.
2. **Mostrar no chat**: o usuário prefere ver ali. Publicar como widget (`show_widget`, largura
   680 px — reorganizar em grade ou um palco com seletor de opção) e também mandar o arquivo com
   `SendUserFile` (display render).
3. Na resposta: uma linha por opção com o gesto e o tempo, e perguntar a direção (pode combinar
   opções). Não comece o Kotlin antes da escolha.

## 5. Depois da escolha

- Registrar a escolhida e as não escolhidas na tabela abaixo, **no mesmo commit** da implementação
  (nada disso vai para a memória: a skill é a fonte).
- Implementar como **função pura de quadro** em `commonMain` (padrão `GargantuaTransition.kt`,
  `GargantuaBalloonJet.kt`, `GargantuaRoll.kt`) com teste em `commonTest`; durações em
  `AppGargantuaTokens` com espelho em `docs/design-system/tokens/motion.css`; tween via
  `appTweenSpec` (vira `snap()` com animação reduzida).
- Teste de pixel da tela no arquivo de teste dela; GIF em `GargantuaPreviewGenerator`
  (`gradlew.bat generateGargantuaPreview`), conferido quadro a quadro.
- Docs no mesmo commit: `docs/hud-notch.md` (ou `presentation.md`), o `*.prompt.md` da primitiva,
  o protótipo `docs/planos/prototipo-visual-opencode.html` e o kit `.jsx` quando houver.
- `gradlew.bat allTests`, e **perguntar antes de commit/push** (skill `usage-monitor-commit-push`).

## Histórico das rodadas (escolhidas e recusadas)

| Rodada | Escolhida | Não escolhidas / recusadas |
|---|---|---|
| Arcos de cota | C · vidro com plasma | tubo iluminado, órbitas 3D inclinadas |
| Cenário | disco discreto (1 faixa, Doppler) | disco forte de 6 faixas ("chamativo") |
| Ativar API / abrir HUD | S1 · onda de choque (1100 ms) | S2 supernova, S3 acreção |
| Desativar API | C2 · colapso com clarão (480 ms) | C1 espaguetificação, C3 evaporação de Hawking, C4 horizonte engole |
| Coleta | R1 · ondas gravitacionais (1300/800 ms) | varredura de sonda, recarga de plasma, tique-taque |
| Abrir balão (conta e engrenagem, e troca de anel) | B3 · jato relativístico (520/240 ms) | B1 onda de choque, B2 lente gravitacional, B4 luz de acreção no contorno, B5 ondas gravitacionais |
| Dado novo (percentuais e pílula) | D5 · horizonte de eventos (480 ms, cascata 50 ms) | D1 desvio para o vermelho, D2 lente, D3 plasma que conta, D4 onda gravitacional |
| Modais: abrir, fechar e apresentar dados (todas as janelas e o `AppDialog`) | E9 · filamentos de plasma (680/220 ms) | E1 jato relativístico, E2 horizonte de eventos, E3 varredura do disco, E4 condensação da nebulosa, E5 captura orbital, E6 farol do pulsar, E7 buraco de minhoca, E8 malha do espaço-tempo, E10 estrela de nêutrons |
| Balão da conta, indicador de execução e balão da engrenagem | F10 · cometa com cauda de íons (restante em destaque, cauda de íons, versão e contagem em linha própria; o botão dentro do aviso não coube e ficou abaixo) | F1 telemetria de sonda, F2 jatos polares, F3 companheira binária, F4 anel de fótons, F5 acreção de partículas, F6 anel de detritos, F7 sinal de rádio, F8 espiral de lente, F9 coroa de plasma |
| Banner do README (`img/banner.svg`, SVG+CSS que toca uma vez; o GitHub não roda JS) | G5 · cometa com cauda de íons (1500 ms; a linha parada virou `img/divider.svg`) | G1 horizonte de eventos, G2 jato relativístico, G3 varredura do disco, G4 anel de Einstein |
| Ícone do app, só tema (estático; 128/48/32/16 px e barras de tarefas clara e escura) | nenhuma — o usuário pediu uma rodada com o nome | H1 horizonte de eventos, H2 anel de cota, H3 monograma no disco, H4 anel de Einstein, H5 jato relativístico, H6 cometa de íons, H7 o notch, H8 medidor do espaço-tempo, H9 eclipse, H10 órbitas em U |
| Ícone do app com o nome (`tools/brand/render_icons.py`) | I10 · nome por tamanho (≥ 96 px USAGE MONITOR, 32–64 px U·M, ≤ 24 px só o núcleo) | I1 U de acreção, I2 M de horizonte, I3 UM entrelaçado, I4 U medidor, I5 "um" no horizonte, I6 U·M (virou a faixa do meio da I10), I7 U de cometa, I8 notch em U, I9 M de sinal |
| Trilha de cota sem projeção (era o tracejado cinza; laço contínuo atrás da política) | J7 · anel de detritos (órbitas de 16 s na linha central, laço de 128 s com voltas inteiras) | J1 poeira de acreção, J2 contas de plasma, J3 tracejado Doppler, J4 tubo oco, J5 graduação com radar, J6 onda gravitacional, J8 rastro que se perde, J9 névoa de nebulosa, J10 sonda em órbita; a primeira rodada, estática, foi recusada ("queria com animação") |
| Desligar uma API: o anel sai e o notch acompanha (antes: C2 480 ms e o notch saltando no fim) | K1 · colapso lento, depois assenta (850 + 450 ms; notch desenhado recolhendo, janela só no fim) | K2 maré (tudo junto, 950 ms), K3 onda gravitacional que fecha, K4 as vizinhas ocupam a vaga |
| Muitas contas: quando a faixa completa não cabe na borda | L1 · borda lateral pode mais (80%; em cima e embaixo continua 45%) | L2 compacta só quem está em dia, L3 degrau sem pílula, L4 duas colunas na lateral |

Próxima rodada usa a próxima letra livre (M, N, …). Trilha, fundo ou estado contínuo: as opções
já nascem animadas (laço atrás da política), nunca só o quadro parado. Ícone de sistema não anima: nessa rodada o
card mostra os tamanhos reais (lupa ×4 no 16 px) sobre barra clara e escura, e não um laço.
