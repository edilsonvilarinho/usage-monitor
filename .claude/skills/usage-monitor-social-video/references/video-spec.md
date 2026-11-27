# Contrato `video.json`

Um arquivo por vídeo, em `docs/planos/video-<slug>/video.json`. É a única entrada: nenhum tema altera cena, voz ou trilha.

```json
{
  "slug": "fluxo-agentico",
  "link": "https://github.com/edilsonvilarinho/usage-monitor",
  "facts": ["24 testes novos", "3961", "212/212"],
  "beats": [
    {
      "id": "abertura",
      "kicker": { "pt": "Fluxo", "en": "Workflow" },
      "title": { "pt": "Do pedido ao merge", "en": "From request to merge" },
      "items": [
        { "pt": "235 PRs", "en": "235 PRs", "short": true },
        { "pt": "gh pr merge --auto", "en": "gh pr merge --auto", "mono": true },
        { "pt": "FALHA", "en": "FAIL", "status": "crit" }
      ],
      "image": "img/hud.png",
      "climax": false,
      "short": { "pt": "Texto de 60 s.", "en": "60 s text." },
      "long": { "pt": "Texto de 5 minutos.", "en": "5 minute text.", "say": { "pt": "forma falada, se diferir" } }
    }
  ]
}
```

## Regras de validação (`validateSpec`)

Cada regra devolve uma mensagem que nomeia o beat e o campo. A validação junta todos os erros, não para no primeiro.

1. `slug`: minúsculas, dígitos e hífen. `link`: URL `https://`.
2. `beats`: de 3 a 20 (30 com `longSeconds` 600); `id` único, minúsculas e hífen. O último beat é o fechamento: sem `items`, sem `image`, sem `code`.
3. `kicker` e `title` têm `pt` e `en`. `title` até 34 caracteres.
4. `long` obrigatório em todo beat, com `pt` e `en`. `short` opcional: beat sem `short` não entra no vídeo curto. Ao menos 3 beats têm `short`, e o último também.
5. Teto de texto falado por idioma: curto até 650 caracteres, longo até 3500 em `longSeconds` 300, e `3500 × longSeconds / 300` em 600 (7000). Passou, a mensagem diz quantos cortar. Nunca se acelera a voz.
6. `items`: até 8; `pt` e `en` até 90 caracteres quando `mono`, 44 nos demais. `status`: `ok`, `crit` ou ausente.
7. Com `image`, até 3 itens, e o arquivo existe e é `.png`, `.jpg` ou `.jpeg`. O caminho é relativo ao `video.json`; sem arquivo ali, à raiz do repositório.
8. No máximo um beat com `climax: true`. Sem nenhum, o clímax é o penúltimo.
9. **Sem número inventado:** todo número com 2 ou mais dígitos em `title`, `items`, `short` e `long` precisa aparecer em algum texto de `facts`.
10. Nenhum texto contém `sk-or-`.

## Código (`code`)

Beat opcional `code: { file, from, to, caption? }`, no lugar de `image` (os dois juntos são erro) e proibido no fechamento.

- `file`: caminho relativo à raiz do repositório; tem que existir.
- `from` e `to`: inteiros com `1 ≤ from ≤ to ≤` total de linhas do arquivo.
- No máximo 16 linhas no trecho; a linha mais longa tem até 110 colunas (tab conta 4).
- `caption`: `{ pt, en }` opcional; sem `items`, vira o primeiro item do beat.
- Staging renderiza o trecho num PNG de 1500 px: fonte 26 px, descendo até 18 px se a linha mais longa não couber; indentação comum ao trecho é removida. O PNG entra no lugar de `image`.

## Cena explica (`scene`, `markers`, `diagram`, `code.highlight`, `at`)

Design (composições, tokens, movimento e recusas do dono): [`visual-explica.md`](visual-explica.md).

Raiz opcional `"scene": "explica"`: a cena explicativa (`assets/scene-explica.js`) só vale no corte longo; o curto segue com a cena atual. Ausente = cena atual. Outro valor é erro (`scene: use "explica" ou ausente`).

Campos opcionais de beat, todos com texto `{pt, en}`:

- `markers`: `[{ "n": 1, "x": 12.5, "y": 30, "label": {pt,en}, "at": {pt,en} }]`. Só em beat com `image`; no máximo 6. `n` é 1, 2, 3… sem repetir. `x` e `y` são % da imagem, de 0 a 100.
- `diagram`: `{ "type": "flow", "nodes": [{ "label": {pt,en}, "tone": "crit|ok|null" }], "back": { "label": {pt,en} } }`, ou `{ "type": "compare", "nodes": [2 nós] }`. `flow` tem de 2 a 4 nós; `back` é opcional, só em `flow`, e liga o último nó ao primeiro, por baixo. `compare` tem exatamente 2 nós e não tem `back`. Rótulo de nó e de `back` com no máximo 28 caracteres.
- `code.highlight`: `[{ "lines": [76], "tone": "info|crit|ok", "note": {pt,en}, "at": {pt,en} }]`, dentro de `code`. É uma lista. `lines` ficam dentro de `from..to`. `note` tem no máximo 40 caracteres.
- `at` (em `markers` e `code.highlight`): trecho literal do `long[lang]` do mesmo beat. Não existir no texto é erro do `check`, com o beat e o campo.

Instante de um `at`: começo do trecho na fala, proporcional aos caracteres dentro de `speechStart..speechEnd`, com o mesmo critério de `timeCaptions` (`phraseTime` em `scripts/timeline.mjs`). `null` se o trecho não existe.

Saída de `buildScript`, por beat, além de `kicker`, `title`, `items`, `id`, `start`, `end`, `speechStart`, `speechEnd`:

- `markers: [{n, x, y, label, at}]` (`at` em segundos), só se o beat tem `markers`;
- `diagram: {type, nodes: [{label, tone}], back: {label} | null}`, só se o beat tem `diagram`;
- `code: {file, from, to, lines, highlight: [{lines, tone, note, at}]}`, só se o beat tem `code`. `lines` vêm do arquivo real, lidas pelo `make.mjs` (`codeLinesOf`) e passadas em `buildScript(..., { codeLines })`, sem a indentação comum. `at` em segundos. No curto, `at` é `null`.

Raiz do `WF_SCRIPT`: `scene` (`"explica"` ou `null`). `make.mjs preview`, `voice` e `render` usam a mesma leitura de código; `render` lê o `WF_SCRIPT` gravado por `voice`.

## Forma falada (`toSpoken`)

`say[lang]` explícito vence. Sem ele, a regra padrão: `ci-ok` → `C I OK`, `CI` → `C I`, `TDD` → `T D D`, `PR` → `P R`, `CLAUDE.md` → `Claude ponto M D` (PT) e `Claude dot M D` (EN), `usage-monitor-workflow` → `Usage Monitor Workflow`. Palavra inteira, nunca dentro de outra.
A legenda usa sempre o texto escrito.

## Variantes e plataformas

| Variante | Formato | Arquivos |
|---|---|---|
| `short-pt` | 9:16 | `<slug>-tiktok-pt.mp4`, `<slug>-shorts-pt.mp4` (mesmo conteúdo) |
| `long-pt` | 16:9 | `<slug>-linkedin-pt.mp4`, `<slug>-youtube-pt.mp4` |
| `long-en` | 16:9 | `<slug>-x-en.mp4`, `<slug>-youtube-en.mp4` |

Vozes: `elevenlabs/eleven-v4`, `matilda` (PT), `george` (EN). Pasta de trabalho: `build/social-video/<slug>/`. Saída: `~/Videos/usage-monitor/<slug>/`.

## Prévia jogável (`qa/player-<variante>.html`)

`make.mjs preview` também grava `qa/player-<variante>.html` para cada variante preparada: a cena em tempo real (iframe de `assets/index.html`, `renderAt(t)`), tocar/pausar, barra de tempo arrastável, anterior/próximo por beat e a lista de beats (título, texto falado inteiro, início). Clicar num beat pula para ele. Não há áudio; o tempo é o estimado. Como `file://` bloqueia o `renderAt` do iframe, sirva o repositório com `make.mjs serve <video.json> [--port 8765]` (só GET, dentro da raiz) e abra a URL impressa. Lógica em `scripts/player.mjs`; teste em `scripts/test/player.test.mjs`.

## Linha do tempo (`buildTimeline`)

Entrada: lista `{ id, durationSec }` de falas, alvo e cauda. Primeira fala em 1,0 s. `gap` igual entre falas, o maior entre 0,8 e 3,0 s que caiba no alvo, deixando a cauda depois da última fala. Abaixo de 0,8 s lança erro com os segundos que sobram. Alvo e cauda: curto 60,0 e 0,6; longo `longSeconds` (300,0 ou 600,0) e 3,0. `longSeconds` é opcional na raiz do `video.json`; ausente vale 300; outro valor é erro do `check`.
`duration` é o alvo, ou menos se o `gap` chegou a 3,0 s. Beat: `start = speechStart - 0.4` (o primeiro em 0), `end` é o início do próximo, o último termina em `duration`.
`items[j].at`: distribuídos por igual entre `speechStart + 0.3` e `speechEnd - 0.6`. No curto, só itens com `short`; se nenhum tiver, entram todos, até 4.

Legendas: frases cortadas em pontuação, depois em vírgula, depois em espaço, até 72 caracteres; tempo proporcional aos caracteres, contíguas dentro da fala.
Cues: por item, `tick`, ou `red` com `crit`, ou `green` com `ok`; `impact` em `start + 0.6` do último beat. Ordenados por tempo. Sem som de abertura (pedido do dono no #684): a música entra com fade-in de 2 s. Não há som de transição entre beats (pedido do dono no #684): a transição é só visual.

Saída `window.WF_SCRIPT = { variant, lang, duration, beats, cues, captions, link, scene }`, com `kicker`, `title` e `items` traduzidos para o idioma da variante. `scene` é `"explica"` só no longo com a raiz `scene`; nos demais, `null`. Campos de beat da cena explica: ver "Cena explica" acima.
