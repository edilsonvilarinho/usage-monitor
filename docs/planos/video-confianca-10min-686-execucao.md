# Vídeo da jornada de confiança em 10 min — issue #686

Procedência: Claude Opus 5.5 (orquestração), Claude Code CLI no Windows 11, 2026-11-27, branch
`media/issue-686-video-confianca-10min` (worktree `.claude/worktrees/video-v3`, base `origin/main` `5aa401b6`),
commits com a identidade `claude <claude@anthropic.com>`. Skill `usage-monitor-social-video`.

## Objetivo

Refazer o vídeo da #684 em 10 min, calmo e explicativo. O dono assistiu ao de 5 min e apontou três problemas:

- as informações continuam "jogadas";
- falta mostrar o que se fala: o trecho de código e o app ("fala de HUD, porém não mostra o que é");
- o roteiro não explica.

Ele pediu um protótipo HTML antes do vídeo e da voz, e quer manter a voz.

## Fatos confirmados

- No vídeo de 5 min (#684, A05), a pausa entre as falas ficou em 2,43 s (PT) e 2,72 s (EN), com 3456 e 3414 caracteres falados.
- A voz rende cerca de 14 caracteres por segundo (#682, A04).
- Hoje o corte longo mira 300 s, com teto de 3500 caracteres falados por idioma e de 20 beats; cada beat leva no máximo uma imagem
  e 3 itens (`scripts/spec.mjs`; `references/video-spec.md`, regras 2 e 7). A pausa entre falas fica entre 0,8 e 3,0 s.
- Limites das redes (fato externo, não medido aqui): o X aceita vídeo acima de 2 min 20 s só em conta Premium.

## Decisões

- A voz não muda: `eleven-v4`/`george`, mesma velocidade. O vídeo fica mais calmo com mais tempo por ideia, não com voz mais lenta.
- Cada parte do roteiro segue a mesma ordem: o problema, o conceito, como foi feito, e um exemplo real na tela (trecho de código com
  caminho, ou captura real do app). Nenhum conceito é citado sem aparecer.
- O visual sai do repositório: capturas pelo gerador de cenas e demos do app (dados sintéticos), e trechos de código copiados dos
  arquivos, com caminho visível. Nenhum mockup.
- O protótipo HTML usa a própria cena da skill no navegador, com tempos estimados e legenda, e vem antes de gastar API.
- Continuam valendo as regras da #682/#684: sem infográfico de terceiro, sem som de transição ou de abertura, e todo número em `facts`.

## Atividades

- **A01** este plano.
- **A02** o material visual: capturas reais e trechos de código, com origem registrada.
- **A03** a skill: corte de 10 min, tetos proporcionais e várias imagens por beat. TDD em `scripts/test/`.
- **A04** o roteiro explicativo (`video.json` e `roteiro.md`), com `make.mjs check`.
- **A05** o protótipo HTML em `prototipos/video-confianca-10min/`, validado no Chrome. **Parada:** a aprovação do dono.
- **A06** a voz, o render, a validação pelo MP4 e os posts.

## Ponto de situação

| Atividade | Comando | Resultado |
|---|---|---|
| A01 | `gh issue create`, `git worktree add` | issue #686; worktree com base em `5aa401b6` |
| A03a | `node --test ".claude/skills/usage-monitor-social-video/scripts/test/*.test.mjs"`; `make.mjs check` no `video.json` da #684 | `longSeconds` (300 padrão, ou 600) muda o alvo do longo, o teto falado (`3500 × longSeconds / 300`) e o máximo de beats (20 ou 30). Vermelho: `gap ficaria abaixo de 0,8 s; sobram 294,400 s de fala para caber em 300 s` (o alvo de 600 s ainda não era lido). Verde 56/56. O `check` do vídeo da #684, sem o campo, continua em 300 s e passa |
| A03b | `node --test ".claude/skills/usage-monitor-social-video/scripts/test/*.test.mjs"`; `make.mjs check` e `make.mjs stage` no `video.json` da #686 | Beat com `code` (`file`, `from`, `to`): o trecho real vira PNG (Plex Mono, linhas numeradas, `file:from-to` no topo, fonte de 26 a 18 px para caber; teto de 16 linhas e 110 colunas). Vermelho: `esperado: beat "fluxo" code: não pode vir junto com image`. Verde 72/72. O `check` recusou o trecho 41-48 do `HudGeometryPropertyTest.kt` (127 colunas), trocado por 125-133. 9 cartões gerados; conferi `regra-prosa` e `propriedade` na imagem: legíveis, sem corte |

## Problemas em aberto e riscos

- Um render longo de 300 s levou cerca de 11 min por idioma (#684). O de 600 s deve dobrar isso; ainda não foi medido.
- A voz da A06 precisa de uma chave nova da OpenRouter. A da #684 foi colada no chat e precisa ser revogada.
- Uma captura do app depende do gerador de cenas do Gradle. Se a cena da HUD com notch não existir, ela precisa ser criada
  e entra em `src/`, com o fluxo de testes completo.
