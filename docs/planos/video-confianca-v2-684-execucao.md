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

## Problemas em aberto e riscos

- Ninguém ouviu a voz do corte anterior em ritmo de 5 min. A calma da fala só se confirma na escuta do dono.
- Postar no X com mais de 2 min 20 s exige conta Premium. Se o dono não tiver, o X precisa de um corte próprio.
- A chave da OpenRouter da #682 foi apagada do disco. A A05 precisa de uma chave nova.
