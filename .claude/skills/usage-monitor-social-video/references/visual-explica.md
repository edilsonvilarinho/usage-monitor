# Design da cena "explica" (corte longo)

Aprovada pelo dono no #686, depois de três rodadas de protótipo HTML. Implementação em `assets/scene-explica.js`, escolhida por
`"scene": "explica"` no `video.json`; o contrato dos campos está em [`video-spec.md`](video-spec.md), seção "Cena explica". O curto (9:16)
e os vídeos sem `scene` continuam na cena "Horizonte de eventos" (`assets/scene.js`).

Quadros de referência, tirados da cena real: [`explica/imagem-com-marcadores.jpg`](explica/imagem-com-marcadores.jpg),
[`explica/cartoes.jpg`](explica/cartoes.jpg), [`explica/codigo-com-fluxo.jpg`](explica/codigo-com-fluxo.jpg) e
[`explica/codigo-com-comparacao.jpg`](explica/codigo-com-comparacao.jpg).

## Princípio

O vídeo **mostra o que a fala cita**, no momento em que ela cita. Conceito sem nada na tela foi recusado ("informações
jogadas"). Cada beat tem uma das três composições abaixo, e o `at` de cada marcador, destaque ou item amarra a entrada a um
trecho literal da fala.

## Composições (16:9, 1920×1080)

| Beat | Composição |
|---|---|
| `image` + `markers` | Imagem inteira (`contain`, nunca cortada) numa moldura à esquerda; legenda numerada à direita. Cada marcador e a sua linha da legenda entram no `at` |
| `code` (+ `diagram`) | Esquema do conceito na faixa de cima (`flow`: 2 a 4 nós, com seta de volta opcional; `compare`: ✗ crit ao lado de ✓ ok). Embaixo, o painel de código real com caminho e linhas no cabeçalho, o chip do item na mesma linha, cor de sintaxe, linhas fora do destaque a 35% e a destacada sublinhada (zoom de 1,04), e o balão do `note` numa faixa própria à direita |
| só `items` | Cartões centralizados, todos visíveis desde o início a 35%; cada um acende no seu `at`. Com 4 ou mais, os cartões encolhem para ficar acima da legenda |
| último beat | Lockup "Usage Monitor" com o link |

Título e kicker ficam sempre no canto superior esquerdo; a legenda, na faixa de baixo. Nenhum elemento cruza outro.

## Tokens

- Superfícies `#131010`/`#1B1818`/`#211E1E`, borda `#3D3838`, texto `#F2EDED`, muted `#B8B2B2`, dim `#4A4444`
  (`docs/design-system/tokens/colors.css`).
- Ouro `#E8AE63`, lens `#F4CF97`, hot `#FFF0CB`, ember `#B4501E` (`AppGargantuaTokens.kt`); ok `#4CAF50`, crit `#E86A6A`, info `#4C8DFF`.
- Sintaxe, só com cores existentes:
  - palavra-chave em ouro;
  - string em lens;
  - função em info;
  - número em warn `#FFA726`;
  - comentário em dim, em itálico.
- Plex Mono em título, rótulo e código; Plex Sans em legenda e texto corrido. Raio de no máximo 10.
- Código: fonte de 26 px, que encolhe para caber em largura e altura, com piso de 22 px. Nenhuma linha quebra.
  A largura é medida nas linhas reais, com a Plex Mono 400 já carregada, e reserva a faixa do balão mais 24 px.

## Movimento

- O estado de cada quadro é função de `t`: sem `transition`, animação do CSS, relógio ou `Math.random`. O render tira uma captura
  por quadro.
- O conteúdo entra em `start + 0,3 s`, com fade e deslize de no máximo 24 px em 380 ms, e **não se mexe mais**. Sai em fade
  de 300 ms antes do `end`.
- O buraco negro segue a geometria do modo flutuante do app (`HudFloatingHole.kt`):
  - núcleo `#030508`;
  - anel de fótons em lens, que "respira" num ciclo de 6 s;
  - disco inclinado (rx = 0,95·room, ry = 0,29·rx, −12°), em 5 faixas finas que giram (as internas mais rápido), com o lado que se
    aproxima mais quente;
  - arco de lente contínuo sobre o núcleo e halo suave.

  Ele é **grande só na abertura** e desliza uma única vez para o canto superior direito, entre 0,6 e 1,8 s. Daí em diante fica
  no canto e anima sempre. Nunca cresce de novo.

## Recusas do dono (não repetir)

- Rótulo de item preso por linha a ponto do anel, ou que se mexe depois de entrar.
- Ponto dentro do núcleo; poeira de partículas; buraco negro "desenhado" ou parado.
- Rótulo ou painel por cima do título; cartão sobre a legenda.
- Código como imagem estática, sem cor, com linha quebrada ou cortada.
- Buraco negro que cresce e encolhe a cada tópico; tópico que começa com a tela vazia.
- Som de transição ou de abertura (#684).

## Mudança de visual

Mudança de visual é mudança da skill: primeiro protótipo HTML com direções diferentes, aberto no navegador e aprovado pelo dono;
só então código, com a folha de quadros da prévia conferida antes de voz e render.
