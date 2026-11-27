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

- Cena nova "explica" só no corte longo, escolhida por `scene` no `video.json`; o curto e os vídeos anteriores continuam na cena atual. O estado de cada quadro é função de `t`, sem animação do CSS, porque o render tira uma captura por quadro.

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
- **A07** a cena "explica" (protótipo aprovado) na skill: dados (`markers`, `diagram`, `code.highlight`, `at` por trecho da fala) e a cena determinística em DOM com `<canvas>`.
- **A04b** o conteúdo das 29 partes nos campos novos.

## Ponto de situação

| Atividade | Comando | Resultado |
|---|---|---|
| A01 | `gh issue create`, `git worktree add` | issue #686; worktree com base em `5aa401b6` |
| A03a | `node --test ".claude/skills/usage-monitor-social-video/scripts/test/*.test.mjs"`; `make.mjs check` no `video.json` da #684 | `longSeconds` (300 padrão, ou 600) muda o alvo do longo, o teto falado (`3500 × longSeconds / 300`) e o máximo de beats (20 ou 30). Vermelho: `gap ficaria abaixo de 0,8 s; sobram 294,400 s de fala para caber em 300 s` (o alvo de 600 s ainda não era lido). Verde 56/56. O `check` do vídeo da #684, sem o campo, continua em 300 s e passa |
| A03b | `node --test ".claude/skills/usage-monitor-social-video/scripts/test/*.test.mjs"`; `make.mjs check` e `make.mjs stage` no `video.json` da #686 | Beat com `code` (`file`, `from`, `to`): o trecho real vira PNG (Plex Mono, linhas numeradas, `file:from-to` no topo, fonte de 26 a 18 px para caber; teto de 16 linhas e 110 colunas). Vermelho: `esperado: beat "fluxo" code: não pode vir junto com image`. Verde 72/72. O `check` recusou o trecho 41-48 do `HudGeometryPropertyTest.kt` (127 colunas), trocado por 125-133. 9 cartões gerados; conferi `regra-prosa` e `propriedade` na imagem: legíveis, sem corte |
| A03c+d | `node --test ".claude/skills/usage-monitor-social-video/scripts/test/*.test.mjs"`; `make.mjs preview … --only long-pt`; Chrome em `make.mjs serve` | Prévia jogável `qa/player-<variante>.html` (cena real no `iframe`, tocar/pausar, barra, lista de beats com o texto escrito) e `make.mjs serve` (só `GET`, só dentro do repo: `POST` 405, caminho fora 404). No Chrome: toca, pula de beat, mostra o código. A conferência dos quadros achou três defeitos, corrigidos na A03d: rótulo de item sobre o título (separação de obstáculos passou a ser o último passo); imagem pequena (painel de 58% para 70% de W, câmera z 0,72 → 0,60, imagem inteira em vez de cortada); lista com a forma falada (`writtenTexts`). Vermelho: `does not provide an export named 'writtenTexts'`. Verde 79/79, conferido pelo `test-runner`. Junta duas atividades num commit porque os dois agentes editaram os mesmos arquivos na sequência |
| A04 | `gen-10min.mjs` → `video.json`; `make.mjs check`; `roteiro-686.mjs` | 29 beats, `longSeconds` 600. PT 6965 e EN 6833 caracteres falados (teto 7000). Cada parte: problema, conceito, como foi feito e exemplo na tela. 9 cartões de código real; capturas da HUD recortadas de `img/hud-rest.png` e `img/hud.png` (geradas fora da tela pelo `ScreenshotGenerator`, `ImageComposeScene`); 2 quadros reais do CI (run `45675713489`, `docked-top-idle`; e o defeito `hud-misplaced` do run `45672620350`). A imagem do detalhe volta no beat do teste de tela para ligar as partes. Prévia estimada a 12,2 caracteres/s: `gap` 1,28 s (EN); com os ~14 caracteres/s medidos, cerca de 3 s |

| A07a | `node --test "…/scripts/test/*.test.mjs"`; vermelho reproduzido numa cópia do HEAD com o teste novo | `scene`, `markers`, `diagram`, `code.highlight` e `at` validados; `phraseTime` mapeia o trecho da fala para segundos, com o mesmo critério das legendas; `buildScript` leva os campos para o `WF_SCRIPT`; `make.mjs` lê `code.lines` dos arquivos. Vermelho (1/19): `esperado: scene: use "explica" ou ausente` (`explica.test.mjs:45`) e `timeline.phraseTime is not a function`. Verde 98/98, conferido pelo `test-runner`. O `check` do vídeo de 10 min continua passando |
| A07b | Chrome headless (`browser.mjs`) na fixture de 4 beats; MD5 de `renderAt(t)` → `renderAt(t+0,5)` → `renderAt(t)` | `assets/scene-explica.js` (477 linhas), carregado pelo `index.html` quando `WF.scene === 'explica'` em 16:9. O estado é função de `t`: sem `transition`, animação de CSS, relógio ou `Math.random`. `__ready` sem `__error`; MD5 iguais em t = 10, 27, 45 e 62. Conferi os 4 quadros na imagem: igual ao protótipo aprovado (marcadores, cartões, esquema, código com destaque e balão, buraco negro no canto). Sem teste automatizado: é página de navegador; a validação é a captura |
| A07c+d | `make.mjs preview … --only long-pt` com o `video.json` real (29 beats); folha `qa/sheet.jpg`; folga entre o código e o balão medida no DOM (`gap-measure.mjs`) | Defeitos achados na folha e corrigidos: o código saía como PNG (o `make.mjs` não gera mais o cartão PNG com `scene: explica`; a cena prefere `code` a `image`); imagem do app cortada (agora `contain`); 4.º cartão sobre a legenda; chip cortado (quebra a linha); trechos de 12 a 14 linhas cortados (fonte cabe em largura e altura, piso de 22 px, chip na linha do cabeçalho); balão sobre a linha. Devolução 1/3 (A07d): a medida rodava antes da Plex Mono 400 carregar. Corrigido no `index.html` (`document.fonts.load('400 26px …')`). Folga a 263,9 / 307,6 / 328,5 s: 109,2 / 28,2 / 600 px (mínimo de 24). Verde 98/98, conferido pelo `test-runner` |
| A05 | Protótipos em `prototipos/video-confianca-10min/` (fora do git), abertos no Chrome em `python -m http.server` | Rodada 1 (prévia da cena antiga), recusada pelo dono com prints: rótulo sobre o título, rótulos que se mexem presos a pontos do anel, ponto dentro do buraco negro, código "seco" e mal formatado, buraco negro "desenho". Rodada 2: três direções (`editor`, `foco`, `diagrama`); o dono aprovou a mistura (`final/`). Ajustes da rodada 3, pedidos pelo dono: buraco negro animado sempre e grande na abertura (geometria do modo flutuante, `HudFloatingHole.kt`); grande só na abertura e depois fixo no canto (ele recusou crescer e encolher a cada tópico); conteúdo entrando em 0,3 s fora da abertura (recusou o espaço vazio no início do tópico). Aprovado: "sim aprovo" |

## Problemas em aberto e riscos

- Um render longo de 300 s levou cerca de 11 min por idioma (#684). O de 600 s deve dobrar isso; ainda não foi medido.
- A voz da A06 precisa de uma chave nova da OpenRouter. A da #684 foi colada no chat e precisa ser revogada.
- Uma captura do app depende do gerador de cenas do Gradle. Se a cena da HUD com notch não existir, ela precisa ser criada
  e entra em `src/`, com o fluxo de testes completo.
