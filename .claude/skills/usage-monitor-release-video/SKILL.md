---
name: usage-monitor-release-video
description: Make the usage-monitor release video for Instagram, TikTok and X — vertical 1080×1920, PT and EN, synthetic voice (Piper), burned-in captions plus .srt, the app's design system, unhurried pace, every change of the version — and the post text for X, Instagram and TikTok. Use for "vídeo da release/versão", "vídeo de lançamento", "vídeo para o insta/tiktok", "descrição para X/Instagram/TikTok".
---

# Usage Monitor — vídeo de lançamento

Gera, para uma versão estável, **dois vídeos verticais** (PT e EN), as **legendas `.srt`** e as **descrições para X,
Instagram e TikTok**. O combinado com o dono (issue #503) é regra, não sugestão:

| Regra | Como é garantida |
|---|---|
| Vertical 1080×1920, 30 fps, H.264 + AAC | `render.mjs` |
| Voz sintética nos dois idiomas | Piper, `en_US-ryan-high` e `pt_BR-faber-medium` (aprovadas na 41.11), `synthesize.py` |
| **Sem pressa** | `timeline.mjs` **falha** acima de 15 caracteres/s por fala ou de 90 s no total; 1,2 s de respiro depois de cada fala e 0,35 s entre frases. O conserto é cortar texto, **nunca** acelerar a voz |
| Legenda | queimada (2 linhas × 26 caracteres, área segura das redes) e `.srt`; entra 150 ms antes da fala, sai 300 ms depois |
| Todas as mudanças da versão | cada issue de `collect-changes.mjs` tem cena ou item; nada além do que está nos commits |
| Tema do app | tokens de `docs/design-system/tokens/`, Plex Mono/Sans do app, anel Gargantua só na abertura e no fechamento |
| Descrições | `social.mjs` recusa X acima de 280 (link = 23, emoji = 2) ou sem o link da release, link em Instagram/TikTok (não é clicável) e mais de 5 hashtags |

A skill **não publica** nada nas redes e **não** abre o vídeo para o dono aprovar quadro a quadro: ela entrega os
arquivos e diz onde estão.

## Passo a passo

Todo o trabalho segue o fluxo do `CLAUDE.md` (issue, worktree, PR): o roteiro, as cenas e as descrições são
versionados em `docs/planos/video-v<maior>-<menor>/`.

1. **Versão.** A pedida, ou a última estável (`git tag --list 'v*'` sem `-beta`).
2. **O que mudou.** `node .claude/skills/usage-monitor-release-video/scripts/collect-changes.mjs vX.Y.Z` — feat/fix
   agrupados por issue e os sem issue (`loose`). Leia o corpo da release (`gh release view vX.Y.Z`) e o plano de
   cada issue em `docs/planos/` para entender **o que a pessoa vê**; não descreva o que não está lá.
3. **Imagens reais.** Procure capturas da versão: `img/*.gif|png` (o `img/terminal.gif`/`terminal-pt.gif` são do
   próprio código de terminal), `docs/planos/prints-*`, `docs/design-system/screenshots/`. Prefira par EN/PT; GIF entra
   em `sources` e vira quadros `NNN.png` (`ffprobe -show_entries frame=duration_time` acha os quadros parados).
   Funcionalidade sem captura vira cena `steps`, `list` ou `commands` com os **rótulos reais** do app (procure o
   texto nos `*Labels.kt` / `CommandLineHelp.kt`), nunca número inventado.
4. **Cenas.** Escreva `docs/planos/video-v<M>-<m>/scenes.json` (formato abaixo): abertura, uma cena por assunto,
   uma `list` "E mais" para o miúdo, fechamento com o link do GitHub. Fala curta, uma ideia por cena. Número escrito
   na legenda (`42`) e por extenso no `say` ("quarenta e dois"); comando com `--` idem.
5. **Quadros de conferência.** `node .claude/skills/usage-monitor-release-video/scripts/make-video.mjs vX.Y.Z --stills`.
   Na primeira vez numa máquina, instala Piper, vozes (~185 MB) e puppeteer-core em `%LOCALAPPDATA%\usage-monitor-video`
   (`setup-tools.mjs`). Se a linha do tempo recusar, **corte o texto** da cena apontada e rode de novo. Depois **olhe**
   os PNG de `build/release-video/vX.Y.Z/check/` (monte uma folha com `ffmpeg … xstack` para ver todos de uma vez):
   texto da captura legível, nada encostando na legenda, recorte (`crop`) sem cortar coluna pela metade.
6. **Descrições.** `docs/planos/video-v<M>-<m>/social.json` (PT e EN para `x`, `instagram`, `tiktok`): o X leva o
   link da release; Instagram e TikTok dizem "link na bio" / "link in bio"; até 5 hashtags. Licença e plataformas
   só como estão no repositório (`LICENSE`, artefatos da release).
7. **Vídeos.** `node .claude/skills/usage-monitor-release-video/scripts/make-video.mjs vX.Y.Z` (≈ 10 min nos dois
   idiomas). Saída em `~/Videos/usage-monitor/vX.Y.Z/`: `usage-monitor-<label>-{en,pt}.mp4`, `.srt` e `social.md`.
   Confira o `ffprobe` que o script imprime (1080×1920, h264 + aac, duração) e extraia 2–3 quadros do MP4 final.
8. **Roteiro.** `docs/planos/video-v<M>-<m>/roteiro.md`: fonte (faixa de tags e issues), parâmetros medidos
   (duração, caracteres/s mais alto) e a tabela de cenas EN/PT — o mesmo formato de `video-v41-11/roteiro.md`.
9. **Entrega ao dono** (≤ 15 linhas): caminho dos arquivos, duração, e o texto de cada rede está no `social.md`.

## Formato do `scenes.json`

```json
{
  "version": "v42.0.0",
  "label": "42.0",
  "sources": { "terminal-en": { "gif": "img/terminal.gif" }, "shots": { "files": ["docs/…/a.png"] } },
  "scenes": [
    { "id": "title", "kind": "title", "headline": { "en": "…", "pt": "…" },
      "lines": { "en": { "text": "Usage Monitor 42 is here.", "say": "Usage Monitor forty-two is here." }, "pt": { "text": "…" } } },
    { "id": "status", "kind": "screen", "tag": "#439", "kicker": { "en": "terminal", "pt": "terminal" },
      "headline": { "en": "…", "pt": "…" }, "image": { "en": "terminal-en/022.png", "pt": "terminal-pt/022.png" },
      "crop": [x, y, largura, altura], "chips": ["usage-monitor status"], "lines": { … } },
    { "id": "scripts", "kind": "commands", "commands": [ { "cmd": "…" | {"en","pt"}, "out": { "en", "pt" } } ], "pause": 0.5, … },
    { "id": "team", "kind": "steps", "steps": [ { "label": {…}, "state": "ok|fail|pending", "why": {…} } ], … },
    { "id": "more", "kind": "list", "items": [ { "en": "…", "pt": "…" } ], "stagger": 1.2, … },
    { "id": "closing", "kind": "closing", "headline": {…}, "link": "github.com/edilsonvilarinho/usage-monitor", … }
  ]
}
```

`screen`: a imagem cabe em 876 × 544 px; recorte menor = texto maior. Captura de terminal a 880 px de largura só fica
legível no celular com escala ≥ 1,2 (recorte de até ~720 px). `commands`: digita a 30 caracteres/s a partir da fala.

## Onde mexer

| Arquivo | Papel |
|---|---|
| `scripts/collect-changes.mjs` | commits entre tags estáveis, por issue (testado) |
| `scripts/timeline.mjs` | ritmo, tempos das cenas, blocos de legenda, SRT (testado) |
| `scripts/social.mjs` | limites das redes e `social.md` (testado) |
| `scripts/synthesize.py` | Piper: escala por idioma (EN 1,4 · PT 1,5; a faber é mais rápida) e pausa entre frases |
| `assets/video.html` | renderizador: `renderFrame(t)` puro, sem relógio nem transição CSS |
| `scripts/render.mjs` | Chrome sem tela → JPEG → ffmpeg; `--stills` |
| `scripts/make-video.mjs` | orquestra; áudio: falas no instante da cena + drone 55/82 Hz a −16 dB, `loudnorm` −16 LUFS |
| `scripts/setup-tools.mjs` | instala as ferramentas fora de `build/` (o `gradlew clean` apagou o pipeline da 41.11) |

Testes: `node --test .claude/skills/usage-monitor-release-video/scripts/test/*.test.mjs`.

O Piper sorteia a duração: duas sínteses do mesmo texto variam alguns décimos por fala e até ~1,7 s no total (v42.0.0:
88,9 s numa execução, 90,6 s na seguinte). Por isso o `synthesize.py` **reaproveita** o WAV enquanto texto, escala e
pausa não mudam (assinatura em `<cena>.txt`): o vídeo final tem exatamente a fala que o `--stills` conferiu. Mudou o
texto, a fala é sorteada de novo — deixe folga (≤ 14 caracteres/s, ≤ 88 s) em vez de parar no limite.

Ao rodar com saída filtrada (`| tail`, `| grep`), use `set -o pipefail`: sem ele a recusa da linha do tempo sai com 0.
