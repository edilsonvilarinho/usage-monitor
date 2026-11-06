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
2. `beats`: de 3 a 20; `id` único, minúsculas e hífen. O último beat é o fechamento: sem `items`, sem `image`.
3. `kicker` e `title` têm `pt` e `en`. `title` até 34 caracteres.
4. `long` obrigatório em todo beat, com `pt` e `en`. `short` opcional: beat sem `short` não entra no vídeo curto. Ao menos 3 beats têm `short`, e o último também.
5. Teto de texto falado por idioma: curto até 650 caracteres, longo até 3500. Passou, a mensagem diz quantos cortar. Nunca se acelera a voz.
6. `items`: até 8; `pt` e `en` até 90 caracteres quando `mono`, 44 nos demais. `status`: `ok`, `crit` ou ausente.
7. Com `image`, até 3 itens, e o arquivo existe e é `.png`, `.jpg` ou `.jpeg`. O caminho é relativo ao `video.json`; sem arquivo ali, à raiz do repositório.
8. No máximo um beat com `climax: true`. Sem nenhum, o clímax é o penúltimo.
9. **Sem número inventado:** todo número com 2 ou mais dígitos em `title`, `items`, `short` e `long` precisa aparecer em algum texto de `facts`.
10. Nenhum texto contém `sk-or-`.

## Forma falada (`toSpoken`)

`say[lang]` explícito vence. Sem ele, a regra padrão: `ci-ok` → `C I OK`, `CI` → `C I`, `TDD` → `T D D`, `PR` → `P R`, `CLAUDE.md` → `Claude ponto M D` (PT) e `Claude dot M D` (EN), `usage-monitor-workflow` → `Usage Monitor Workflow`. Palavra inteira, nunca dentro de outra.
A legenda usa sempre o texto escrito.

## Variantes e plataformas

| Variante | Formato | Arquivos |
|---|---|---|
| `short-pt` | 9:16 | `<slug>-tiktok-pt.mp4`, `<slug>-shorts-pt.mp4` (mesmo conteúdo) |
| `short-pt` | 16:9 | `<slug>-linkedin-pt.mp4` |
| `short-en` | 16:9 | `<slug>-x-en.mp4` |
| `long-pt` | 16:9 | `<slug>-youtube-pt.mp4` |
| `long-en` | 16:9 | `<slug>-youtube-en.mp4` |

Vozes: `elevenlabs/eleven-v4`, `matilda` (PT), `george` (EN). Pasta de trabalho: `build/social-video/<slug>/`. Saída: `~/Videos/usage-monitor/<slug>/`.

## Linha do tempo (`buildTimeline`)

Entrada: lista `{ id, durationSec }` de falas, alvo e cauda. Primeira fala em 1,0 s. `gap` igual entre falas, o maior entre 0,8 e 3,0 s que caiba no alvo, deixando a cauda depois da última fala. Abaixo de 0,8 s lança erro com os segundos que sobram. Alvo e cauda: curto 60,0 e 0,6; longo 300,0 e 3,0.
`duration` é o alvo, ou menos se o `gap` chegou a 3,0 s. Beat: `start = speechStart - 0.4` (o primeiro em 0), `end` é o início do próximo, o último termina em `duration`.
`items[j].at`: distribuídos por igual entre `speechStart + 0.3` e `speechEnd - 0.6`. No curto, só itens com `short`; se nenhum tiver, entram todos, até 4.

Legendas: frases cortadas em pontuação, depois em vírgula, depois em espaço, até 72 caracteres; tempo proporcional aos caracteres, contíguas dentro da fala.
Cues: `rise` em 0; `whoosh` em cada `start - 0.3` exceto o primeiro; por item, `tick`, ou `red` com `crit`, ou `green` com `ok`; `impact` em `start + 0.6` do último beat. Ordenados por tempo.

Saída `window.WF_SCRIPT = { variant, lang, duration, beats, cues, captions, link }`, com `kicker`, `title` e `items` traduzidos para o idioma da variante.
