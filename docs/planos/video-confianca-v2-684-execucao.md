# Vídeo da jornada de confiança v2 — issue #684

Procedência: Claude Opus 5.5 (orquestração), Claude Code CLI no Windows 11, 2026-11-26, branch
`media/issue-684-video-confianca-v2` (worktree `.claude/worktrees/video-v2`, base `origin/main` `9983593e`),
commits com a identidade `claude <claude@anthropic.com>`. Skill `usage-monitor-social-video`.

## Objetivo

Refazer o vídeo da #682 para LinkedIn (PT) e X (EN) em 5 minutos, com um roteiro encadeado (cada parte responde à anterior) e
transições sem som. O dono recusou o de 60 s por quatro motivos: conteúdo raso, partes sem ligação, fala corrida e som de transição.

## Fatos confirmados

- O corte de 60 s tinha 622 caracteres falados em PT. A fala ocupou cerca de 44,5 s (60 − 1,0 − 0,6 − 6 × 2,31 de `gap`), ou seja,
  cerca de 14 caracteres por segundo (`video-confianca-682-execucao.md`, A04).
- O som de transição é a cue `whoosh`, emitida antes de cada beat menos o primeiro (`scripts/timeline.mjs` `buildCues`). A mesma cue
  desenha um anel na cena (`assets/scene.js` `drawWaves`).
- Hoje LinkedIn e X saem do corte curto (`scripts/spec.mjs` `VARIANTS`). O corte longo mira 300 s, com `gap` entre falas de no máximo
  3,0 s e teto de 3500 caracteres falados por idioma (`references/video-spec.md`).
- Limites das redes (fato externo, não medido aqui): o X aceita vídeo acima de 2 min 20 s só em conta Premium; o LinkedIn aceita 5 min.

## Decisões

- A velocidade da voz não muda (regra da skill desde a #503). A fala fica mais calma de três formas: menos caracteres por minuto,
  pausas entre as partes e frases mais curtas.
- Sem `whoosh`, nem o som nem o anel. As transições de câmera, título e imagem ficam mais longas.
- O roteiro segue a ordem pedida pelo dono. A diferença: os conceitos da palestra vêm antes de "como aplicar", porque foram eles que
  definiram o plano. Também entraram "o que deu errado", "o que melhorou no app" e "o que falta".
- Continuam valendo as decisões da #682: o infográfico de terceiro não entra, e os números da palestra não aparecem.

## Atividades

- **A01** este plano.
- **A02** a skill: LinkedIn e X passam ao corte longo; sai o `whoosh`; as transições ficam mais longas. TDD em `scripts/test/`.
- **A03** o `video.json` e o `roteiro.md` novos, com `make.mjs check` e a prévia.
- **A04** a aprovação do dono do roteiro, da folha de quadros e de um teste curto sem voz.
- **A05** a voz e o render, validados pelo MP4. Depois, o `social.md` revisado.

## Ponto de situação

| Atividade | Comando | Resultado |
|---|---|---|
| A01 | `gh issue create`, `git worktree add` | issue #684; worktree com base em `9983593e` |
| A02 | `node --test ".claude/skills/usage-monitor-social-video/scripts/test/*.test.mjs"` | vermelho: `✖ cues: rise em 0, sem whoosh, itens por status, impact no último beat, ordenados` (`AssertionError`), pass 44, fail 5 (cues e `VARIANTS`); verde 49/49. A02b (teto falado somado por idioma, antes só por beat: o `check` aceitou 5398 caracteres no longo): vermelho `regra 5: o teto vale para o total` `0 !== 1` (`spec.test.mjs:109`), verde 50/50; conferido de novo pelo `test-runner`: 50/50. `grep whoosh` na skill: nenhum. Transições: câmera 1,2 → 2,0 s, título 0,8/0,6 → 1,2/1,0 s, imagem 0,6 → 1,0 s |
| A03 | `make.mjs check` e `make.mjs preview` em `video-confianca-644/video.json` | 15 partes (máximo da skill). Longo PT 3456 e EN 3414 caracteres falados, teto 3500. A 1ª versão tinha 5398 e foi cortada: os números ficam na tela e a fala conta a história. Prévia estimada a 12,2 caracteres/s: 300 s, `gap` 0,88 s (PT) e 1,15 s (EN). Com os cerca de 14 caracteres/s medidos na #682, sobram cerca de 3 s entre as partes. Folha `build/social-video/confianca-644/qa/sheet.jpg`: sem texto cortado. A numeração no kicker foi retirada porque a cena já numera (`01/13`). Fatos novos conferidos nos planos: #645:56,60,67; #646:30-39; #652:33,37; #657:111; #669:57; #679:59. Correção de conteúdo: a janela da HUD não é a categoria com mais escape (`other` 25,0%), então a fala diz só que houve escape ali, 3 de 16 |
| A05a | `ffmpeg -af astats` no vídeo-teste; `node --test ".claude/skills/usage-monitor-social-video/scripts/test/*.test.mjs"` | Depois da aprovação, o dono reclamou do som de abertura: "estourado demais, parece com a transição". Medido: o `rise` atinge -1,6 dBFS entre 2,3 e 2,6 s e é cortado em 20 ms; de 3 a 10 s o pico é -6,1 dBFS. Saíram o `rise` (cue, `fx_rise`) e o anel da cena (`drawWaves`). A música mantém o fade-in de 2 s. Vermelho: `expected: [ [ 1.3, 'tick' ], …]`, `actual: [ [ 0, 'rise' ], [ 1.3, 'tick' ], …]`. Verde 50/50, conferido pelo `test-runner`. `grep -rnw rise` na skill: nenhum |
| A05 | `opencode run … make.mjs voice`, `make.mjs render`, `ffprobe`, `ebur128`, quadros do MP4 | Voz: 37 segmentos novos; `gap` 2,20 s (curto), 2,43 s (longo PT) e 2,72 s (longo EN); saída sem `sk-or-`; arquivo da chave apagado. MP4: 1920x1080 30 fps 300,0 s (`linkedin-pt` = `youtube-pt`, `x-en` = `youtube-en`, conferido com `cmp`), 1080x1920 30 fps 60,0 s (`tiktok-pt`). Volume integrado: -14,1 LUFS (PT longo), -14,2 (EN longo), -14,2 (curto); pico -1,5 dBFS. Faixa de efeitos em silêncio de 0 a 1,2 s; o pico em 1,0 s é o início da voz. Quadros em 3, 40, 150 e 290 s nos dois idiomas: título, itens e legenda legíveis. `social.md`: vídeo de 5 min, ressalva do X Premium, post do X com 245 de 280 caracteres. Ninguém ouviu o resultado |
| A04 | Mensagem ao dono com `v2-roteiro.md`, `v2-folha-quadros.jpg` e `v2-teste-transicao-sem-voz.mp4` (50 s, sem voz) | Sem teste automatizado: validação é a revisão do dono. Resposta em 2026-11-26: "aprovado". Vereditos no portal: A02-684 e A03-684 aprovadas |

## Problemas em aberto e riscos

- Ninguém ouviu a voz do corte anterior em ritmo de 5 min. A calma da fala só se confirma na escuta do dono.
- Postar no X com mais de 2 min 20 s exige conta Premium. Se o dono não tiver, o X precisa de um corte próprio.
- A chave da OpenRouter da #682 foi apagada do disco. A A05 precisa de uma chave nova.
