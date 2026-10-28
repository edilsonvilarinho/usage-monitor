# Tour em vídeo 16:9 da aplicação — issue #505

Procedência: Claude Opus 5.5 (esforço padrão), Claude Code CLI no Windows 11, 2026-10-28, branch
`feat/issue-505-video-tour`, commits com a identidade `claude <claude@anthropic.com>`.

## Objetivo

Um tour de 5 a 10 minutos de todas as funcionalidades, em português e em inglês, com legenda queimada e `.srt`, voz
menos robótica que a do vídeo de release, trilha espacial, efeitos sonoros e transições do tema. O protótipo
(animatic, roteiro, amostras de voz e de trilha) foi aprovado pelo dono antes de qualquer código.

## Fatos confirmados (2026-10-28, `0461d28a`)

- A skill `usage-monitor-release-video` é vertical (1080×1920) e `timeline.mjs` recusa vídeo acima de 90 s
  (`PACING.maxTotal`): o tour não cabe nela sem um segundo perfil.
- Os GIFs da ajuda (`src/desktopMain/resources/help/*.gif`) medem 1000×420 (`ffprobe`), saem só em português
  (`grep -c "AppLanguage.PT"`: 12 em `HelpMediaGenerator.kt`, 6 em `HelpMediaRecentTopics.kt`) e passam pela paleta
  de 255 cores do `GifEncoder`. O `SceneRecorder` renderiza a 2× e reduz cada quadro à metade (`downsampleByTwo`).
- O tópico `window-modes` grava a HUD aberta com o balão de uma conta: as 17 demos da ajuda cobrem todas as telas do
  tour, sem depender das imagens de `img/`.
- Vozes candidatas geradas nesta máquina com a mesma frase: Piper faber/cadu/jeff, Kokoro 82M (`pf_dora`, `pm_alex`,
  `am_michael`) e Microsoft Neural. O dono escolheu **`pf_dora` (PT)** e **`am_michael` (EN)**, ambas locais
  (`kokoro-onnx` sobre `onnxruntime`, que já roda no Python 3.14 do venv da skill).
- Trilha e efeitos do protótipo foram sintetizados em `numpy`/`scipy`, sem amostra de terceiros; aprovados com a
  ressalva "não pode ficar muito rápido".

## Decisões

- **16:9, 1920×1080, 30 fps** (escolha do dono): as telas do app são largas; o vertical recortaria.
- **Mesma skill, segundo perfil.** O alvo `tour` lê `docs/planos/video-tour/` e escreve em
  `~/Videos/usage-monitor/tour/`. O perfil do tour mantém o teto de caracteres por segundo, abaixa para 14 e troca o
  teto de 90 s por piso de 300 s e teto de 600 s. O vídeo de release não muda (nem a voz Piper).
- **Capturas em PNG a 2×**, nos dois idiomas, pelo mesmo gerador da ajuda (`--frames --language=en`): sem paleta,
  sem redução, com os dados sintéticos de sempre. Ficam em `build/`, não são versionadas.
- **Som determinístico:** trilha e efeitos com semente fixa, na duração exata da linha do tempo; efeitos ancorados em
  eventos calculados por função pura (`soundEvents`), testada.
- **Ritmo sem pressa:** Kokoro com velocidade 0,9, 1,4 s de respiro depois de cada fala; o conserto de fala rápida
  continua sendo cortar texto.

## Atividades

| # | Atividade |
|---|---|
| A01 | Este plano |
| A02 | `HelpMediaGenerator`: opções (`HelpMediaOptions`), idioma e saída em sequência de PNG (`FrameSequenceWriter`) |
| A03 | `timeline.mjs`: perfil `TOUR_PACING` e `soundEvents` |
| A04 | Voz por roteiro (`voice.engine`): Kokoro no tour, Piper no release |
| A05 | Trilha e efeitos (`soundtrack.py`) e mixagem com ducking |
| A06 | Renderizador 16:9 `assets/tour.html` e alvo `tour` no `make-video.mjs` |
| A07 | Roteiro, quadros de conferência e render dos dois vídeos |

## Ponto de situação

| # | Estado | Execução |
|---|---|---|
| A01 | feito | plano criado; sem teste (documento) |

## Problemas em aberto e riscos

- Duração real só sai da fala medida; com Kokoro a 0,9 a estimativa do protótipo (≈ 9 min) pode passar de 10 min. O
  conserto é cortar texto, nunca acelerar.
- Render estimado em ~1 h para os dois idiomas (≈ 9 quadros/s medidos no vídeo de release, 1080×1920).
- O texto das demos traz rótulos de dados sintéticos em português ("Padrão") também na versão EN: é nome de conta,
  conteúdo do usuário, não texto da interface.
