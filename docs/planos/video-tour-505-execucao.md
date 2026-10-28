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
- **Ritmo sem pressa:** Kokoro com velocidade por idioma (PT 0,82, EN 0,9; a dora fala mais depressa), 1,4 s de respiro depois de cada fala; o conserto de fala rápida
  continua sendo cortar texto.

## Atividades

| # | Atividade |
|---|---|
| A01 | Este plano |
| A02 | `HelpMediaGenerator`: opções (`HelpMediaOptions`), idioma e saída em sequência de PNG (`FrameSequenceWriter`) |
| A03 | `timeline.mjs`: perfil `TOUR_PACING` e `soundEvents` |
| A04 | Voz por roteiro (`voice.engine`): Kokoro no tour, Piper no release |
| A05 | Trilha e efeitos (`soundtrack.py`), determinísticos, na duração da linha do tempo |
| A06 | Renderizador 16:9 `assets/tour.html` e alvo `tour` no `make-video.mjs`, com a mixagem (trilha abaixada sob a fala) |
| A07 | Roteiro, quadros de conferência e render dos dois vídeos |

## Ponto de situação

| # | Estado | Execução |
|---|---|---|
| A01 | feito | plano criado; sem teste (documento) |
| A02 | feito | vermelho: `gradlew.bat desktopTest --tests "com.usagemonitor.screenshots.HelpMediaOptionsTest"` → 7 testes, 6 falhas na asserção (`expected:<[history, budget]> but was:<null>`, `expected:<build/video-media> but was:<src/desktopMain/resources/help>`, `expected:<[000.png, 001.png, frames.json]> but was:<[]>`…); o caso do padrão já passava (é o comportamento atual). Verde: mesmo comando, 7/7. `generateVideoMedia -PvideoMediaLanguage=en`: 17 pastas, quadros 2000×840; folha de contato conferida, textos em inglês (HUD e bot do Telegram localizados nas fixtures). Recorte `screenshots.*`, `help.*`, `SettingsDialogContentTest`, `architecture.*`: 53 testes, 0 falhas |
| A03 | feito | vermelho: `node --test .claude/skills/usage-monitor-release-video/scripts/test/*.test.mjs` → 27 testes, 9 falhas (`expected: 300`, "Vídeo de 278.6 s passa de 90 s", "Missing expected exception", `expected: 1 actual: 2` na legenda): sem perfil do tour tudo caía no teto de 90 s. Verde: mesmo comando, 27/27 (18 antigos intactos) |
| A04 | feito | sem harness de teste em Python; validado por execução real: `synthesize.py` com `voice.engine: "kokoro"` na fala da HUD → PT 9,13 s (13,3 car/s) e EN 9,38 s (12,5 car/s). A 0,9 para os dois a dora deu 14,4 car/s (acima do teto): velocidade por idioma, PT 0,82 e EN 0,9. Assinatura do Piper inalterada (`<escala>\|<silêncio>\|<texto>`), o WAV do release continua reaproveitado. `setupTools({ kokoro: true })` instalou `kokoro-onnx`, `scipy` e o modelo |
| A05 | feito | sem harness de teste em Python; validado por execução real: `soundtrack.py 560 <5 eventos> <saída>` → `music.wav` e `effects.wav` de 560 s (107 MB cada, 48 kHz estéreo) em 30 s. Mesma síntese do protótipo aprovado, com acordes de 10 s e sinos a 72 bpm (metade da densidade), semente 505; reverb por `oaconvolve` para caber em memória num vídeo de 10 min |
| A06 | feito | sem harness de teste para o renderizador (HTML no Chrome sem tela); validado por `node .claude/skills/usage-monitor-release-video/scripts/make-tour.mjs --stills` → linha do tempo de 352,4 s, 22 cenas, fala mais rápida 13,8 car/s; 23 quadros por idioma conferidos em folha. Corrigido o que a folha mostrou: chips das fontes encostando na legenda (área da captura encolhe para 548 px com chips) e capturas pequenas (área de 643 px de altura; recorte da HUD e da rede local). A primeira rodada recusou 14,5 car/s em 8 falas com dora 0,82: fala mais lenta (PT 0,78, EN 0,86) e pausa de 0,5 s entre frases (`voice.pause`); a de orçamento em EN ainda deu 15,1 e teve o texto cortado |
| A07 | feito | sem harness de teste (roteiro e render); `make-tour.mjs --only en` e `--only pt` → `usage-monitor-tour-{en,pt}.mp4`, `ffprobe`: h264 1920×1080 + aac, 352,37 s. Quadros do MP4 conferidos (abertura, HUD com foco, terminal digitando). A primeira mixagem deixou a trilha no nível da fala (`volumedetect`: −22,3 dB no respiro contra −21,4 dB com fala): o `loudnorm` de uma passada é dinâmico e levantava a trilha nos respiros. Corrigido com ganhos fixos (voz +8 dB, trilha −12 dB) e `loudnorm` linear em duas passadas, refeito por `--remix` sobre o vídeo mudo → fala −19 dB, respiro −30 dB, EN −16,1 LUFS, PT −16,0 LUFS, pico −1,2/−1,3 dBFS |

## Problemas em aberto e riscos

- Os GIFs versionados da ajuda estão defasados do código: `generateHelpMedia -PhelpMediaTopic=budget,window-modes`
  regerou `window-modes.gif` com o balão atual (botão de análise, linha "usado · restante" em duas colunas), diferente
  do arquivo de 2026-09-29. Não é efeito desta mudança e fica fora do escopo; os dois arquivos foram restaurados.
- Duração medida: 5:52, perto do piso de 5 min. Cortar fala de novo pode derrubar o vídeo abaixo de 300 s; o
  `buildTimeline` recusa, e o conserto é capítulo novo com captura real, não fala mais lenta.
- Render medido: ~19 min por idioma (10 572 quadros 1920×1080).
- O áudio foi conferido por medição (níveis, loudness, espectrograma), não por escuta: a aprovação de ouvido é do dono.
- O texto das demos traz rótulos de dados sintéticos em português ("Padrão") também na versão EN: é nome de conta,
  conteúdo do usuário, não texto da interface.
