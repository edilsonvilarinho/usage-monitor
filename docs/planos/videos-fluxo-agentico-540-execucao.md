# Vídeos do fluxo agêntico — issue #540

Procedência: Claude Opus 5.5, Claude Code CLI no Windows 11, 2026-11-06, branch `docs/issue-540-workflow-video`, commits
com a identidade `claude <claude@anthropic.com>`.

## Objetivo

Apresentar o fluxo de desenvolvimento agêntico do Usage Monitor, com destaque para a skill `usage-monitor-workflow`, o CI
e os testes documentados como comentário na issue. Entregas finais: TikTok, Shorts e LinkedIn (PT, 1 min), X (EN, 1 min),
YouTube (PT e EN, 5 min) e a descrição de cada rede. **Antes delas**, 10 protótipos com vozes PT/EN e trilha original,
para o dono escolher.

## Fatos confirmados (2026-11-06, `618316a6`)

- `GET /api/v1/models?output_modalities=speech`: 38 modelos, rota `/api/v1/audio/speech`. Vozes `pt-BR-*` só em
  `microsoft/mai-voice-2.1` e `-flash`; ElevenLabs e Gemini são multilíngues sem voz por idioma.
- `opencode --version`: 1.19.4; `opencode providers list` traz credencial OpenRouter. O opencode não tem ferramenta de
  voz: o harness executa um script que chama a rota acima.
- Chave temporária: `GET /api/v1/key` informa expiração em 2026-11-07T14:02:42Z, sem limite. Fica só no ambiente do
  processo e em arquivo do job, fora do repositório.
- `gh issue view 537` e `gh pr view 539`: 24 testes novos, 3961 na suíte, 0 falhas, cobertura do diff 212/212, Kotlin
  95,2%, run 42230027000, 0 incidências, 6 commits. São os únicos números do roteiro (`data/script.js`).
- Worktree `workflow-video-prototypes` (commit `f84a88e0`, `prototipos/` não versionado): duas rodadas anteriores. A 1ª
  foi recusada por parecer slides (`workflow-cinema/direcao-e-escolha.md`); a 2ª tem 10 modelos quase iguais
  (`workflow-cinema/qa/mosaic.png`) e `previews/` vazia. Preservada, sem alteração.
- `python` do PATH sem numpy; `%LOCALAPPDATA%\usage-monitor-video\venv` tem numpy 2.5.4 e scipy 1.18.1, e `node/` tem
  puppeteer-core.

## Decisões

- Rodada nova em worktree a partir de `origin/main`, como pedido; as rodadas antigas só servem de referência.
- Narração completa de 60 s por voz, sintetizada por beat e posicionada em `speechStart`: o dono avalia a cadência real,
  não uma amostra. Velocidade nunca é alterada; o conserto é cortar texto (regra do #503).
- Cadência: 9 beats de 6,4 s, 8,2 caracteres/s em PT e 8,8 em EN, abaixo do teto de 15.
- Trilha sintetizada aqui, com harmonia própria. Faixas de Lyria entram como opção rotulada, com prompt descritivo, sem
  citar obra ou compositor.
- Anel do design system em escala de cena é exceção de vídeo promocional: a regra de `readme.md:143-149` protege a HUD.
- Mídia fica fora do versionamento até a escolha; o plano e o roteiro entram no repositório.

## Atividades

- A01 plano.
- A02 roteiro e contrato (`data/script.js`, `data/wf.js`, `BRIEF.md`).
- A03 vozes pela OpenRouter via opencode.
- A04 trilhas e efeitos originais.
- A05 galeria e ferramenta de render.
- A06–A15 modelos 01 a 10.
- A16 MP4 de comparação, validação em navegador real, parada para escolha.
- A17 em diante: definidos depois da escolha.

## Ponto de situação

| Atividade | Estado | Comando e resultado |
|---|---|---|
| A01 | Feito | Sem harness de teste: documento conferido por leitura. |
| A02 | Feito | `node -e "require('./data/script.js')"`: 9 beats, 25 cues. Beats `pr`, `ci` e `stop` cortados depois da medição das vozes. |
| A03 | Feito | `opencode run` com `audio/tts.mjs`: 16 vozes, 0 falhas. `pt-pedro` e `en-charon` retiradas (fala acima do beat), `pt-rafael` e `pt-kore` retiradas (sobreposição de 0,32 s e 0,04 s). Restam 5 PT e 7 EN, 60,0 s por `ffprobe`. |
| A04 | Feito | `audio/music.py` e `audio/sfx.py`: 4 trilhas e efeitos, 60,0 s, cerca de -20 LUFS. Lyria: 1 faixa; a 2ª recebeu `403 PROHIBITED_CONTENT` e não foi refeita. |
| A05 | Feito | Chrome real, `file://`: 10 modelos, 9:16 e EN sem erro; voz, trilha e efeitos com desvio de cerca de 20 ms; ducking 0,6 para 0,21; sem rolagem em 390 px. |
| A06–A15 | Aprovados como direção | `render/stills.mjs`, 9 tempos, 2 formatos, saída 0. Conferidos por folha de contato em `qa/sheet-NN.jpg`. Ressalvas da versão final abaixo. |
| A16 | Feito | `render/render.mjs`: 10 MP4 1280x720, 60,0 s, H.264 e AAC. Escolha do dono: modelo 01, Matilda, George, Pulso de sonda. |
| A17 | Feito | Roteiro refeito a pedido do dono (`video-fluxo-540/roteiro.md`). Vozes via `opencode run`, 0 falhas: 60 s com 51,8 s de fala em PT e 51,9 s em EN; longo com 255,2 s e 247,8 s. |
| A18 | Feito | `node final/build.mjs`: short 60,000 s (pausas de 0,83 e 0,81 s); long-pt 299,996 s (2,914 s); long-en 293,760 s (3,000 s). SRT sem `C I OK`. |
| A19 | Feito | `final/audio.py`: 4 variantes de trilha e efeitos com a duração exata, -20,0 LUFS medido em short-pt e long-pt. |
| A20 | Feito | `final/stills.mjs` nos instantes densos, 9:16 e 16:9, saída 0. Itens em 9:16 viraram coluna depois de reprovação; horizonte ajustado para z 0,86. |
| A21 | Feito | `final/render.mjs`: 5 renders, saída 0, cópia para Shorts. `ffprobe` e `ebur128`: 1080x1920 e 1920x1080, 30 fps, 60,0 s, 300,0 s e 293,8 s, -14,1 a -14,3 LUFS, pico -1,2 a -1,3 dBTP. Quadros extraídos dos MP4 conferidos. |
| A22 | Feito | `video-fluxo-540/social.md`: X com 253 de 280 caracteres, capítulos pelos tempos reais de `final/data/long-*.js`. Sem harness de teste: conferido por leitura. |

## Problemas em aberto e riscos

- O anel do modelo 01 ainda lembra uma esfera com anéis; a escolha do dono foi feita vendo essa aparência.
- Fonte e mídia ficam em `prototipos/workflow-video-v3/` e `~/Videos/usage-monitor/workflow/`, fora do versionamento.
- Só `pt-caio` e `en-harper` mantêm o respiro de 1,2 s em todos os beats.
- Agentes passaram de 4 simultâneos uma vez (6), ao devolver 01 e 03 com 05 e 06 já abertos.
- Escuta humana é do dono: agente mede duração e loudness, não pronúncia nem beleza da voz. Conferir "ci-ok", "Claude ponto M D" e "T D D" antes de publicar.
- Termos de uso de saída do Lyria não verificados; faixa de Lyria só vai ao vídeo final depois disso.
- Ranking público de TTS não cobre PT-BR nem os modelos atuais do catálogo: a escolha de vozes é por disponibilidade.
