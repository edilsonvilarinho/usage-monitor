# Voz e imagem pelo OpenRouter no vídeo de lançamento — issue #633

Procedência: Claude Opus 5.5, Claude Code CLI no Windows 11, 2026-11-22, branch `feat/issue-633-video-openrouter`,
commits com a identidade `claude <claude@anthropic.com>`.

## Objetivo

A skill `usage-monitor-release-video` sintetiza a voz com Piper local. O dono reprovou a voz local e a pronúncia
em PT das vozes de catálogo inglês, e pediu voz e imagem por API (OpenRouter), com a chave passada na chamada da
skill. Também pediu protótipos de roteiro **no visual do app** para escolher o padrão dos próximos vídeos.

## Fatos confirmados (2026-11-22, `79f30050`)

- `GET https://openrouter.ai/api/v1/models?output_modalities=speech`: 37 modelos de fala, rota
  `/api/v1/audio/speech`, corpo `{model, input, voice, response_format}`; resposta binária, sem campo de custo.
- `microsoft/mai-voice-2.1` com `response_format: "mp3"`: `200`, `audio/mpeg`, 24 kHz mono.
- `google/*-tts`: `400 Gemini TTS only supports response_format="pcm"`; com `pcm`, `audio/pcm;rate=24000;channels=1`.
- `openai/gpt-audio` em `/audio/speech`: `400 Model openai/gpt-audio does not exist`.
- Imagem: `POST /api/v1/chat/completions` com `modalities: ["image","text"]` devolve
  `choices[0].message.images[0].image_url.url` = `data:image/png;base64,…` e `usage.cost` (5 capas: US$ 0,336).
- Audição de 27 amostras (`build/video-prototypes/voices/`, mesma frase com HUD/Codex/tokens/42.3): o dono escolheu
  **`elevenlabs/eleven-v4`, voz `george`**, para PT e EN.
- Protótipos com página própria foram reprovados pelo dono: "fora do nosso design system". A prévia tem de usar o
  renderizador da skill (`assets/video.html`, `setup(data)` + `renderFrame(t)`).
- Última estável: `v42.3.0` (`gh release list`), faixa `v42.2.0..v42.3.0`.

## Decisões

- Chave só por `OPENROUTER_API_KEY` no ambiente da chamada; ausente, a skill **falha** com mensagem dizendo a
  variável — não cai para o Piper em silêncio. Nunca gravada em arquivo, log ou `scenes.json`.
- Voz padrão `elevenlabs/eleven-v4` + `george`, sobrescrevível por `voice` no `scenes.json`; formato `mp3`, exceto
  `google/*-tts` (`pcm` → ffmpeg `s16le`).
- Cache por assinatura (modelo, voz, texto): a mesma fala não é cobrada duas vezes e o vídeo final usa o áudio que a
  prévia conferiu.
- Imagem (`google/gemini-3.1-flash-image`): só capa/ilustração, opcional por cena; **nunca** imita tela do app.
- Regras de ritmo (15 caracteres/s, 90 s) inalteradas.

- **Vídeo mostra o app real (2026-11-22, dono):** prévias só com texto (`steps`/`list`) foram reprovadas — "não
  mostram como a aplicação funciona". As cenas passam a usar quadros das **telas reais** gravadas offscreen pelo
  gerador de demos (`generateVideoMedia`, `ImageComposeScene`, dados sintéticos), nunca imitação em HTML. Os
  composables da HUD não importam `java.awt` (grep), então gravam offscreen. Demo de vídeo tem registro próprio:
  `HelpTopic` é enum e não ganha valor.

## Atividades

| ID | Atividade |
|---|---|
| A01 | Este plano |
| A02 | Cliente OpenRouter (fala + imagem) com testes de contrato |
| A03 | Síntese pelo OpenRouter no `make-video.mjs`, cache por assinatura |
| A04 | `--preview`: HTML com `video.html` sincronizado à trilha |
| A05 | Registro de demos de vídeo + `video-hud-minimize` e `video-hud-floating` |
| A06 | Helper de arrasto no `SceneRecorder` + `video-hud-reorder` |
| A07 | `video-codex-sessions` e `video-analysis-all` |
| A08 | Cena `screen` toca sequência de quadros (`frames.json`) no `video.html` e na prévia |
| A09 | 5 roteiros da v42.3.0 com as demos, em prévia; escolha do dono |
| A10 | Vídeo v42.3.0, `roteiro.md`, `social.json`, SKILL.md |

## Ponto de situação

| ID | Comando | Resultado |
|---|---|---|
| A01 | plano escrito | — |
| A02 | `node --test …/scripts/test/openrouter.test.mjs` | vermelho: 9 × `Error: não implementado` (esboço); verde: exit 0 |
| A03 | `node --test …/test/speak.test.mjs …/test/setup-tools.test.mjs` | vermelho: 6 × `Error: não implementado` (esboço do `speak`), depois `pt.status` 3.1 ≠ 3 (erro da fixture: `ó` tem 2 bytes); verde: 4 arquivos, 27/27, exit 0 |
| A04 | `node --test …/test/preview.test.mjs`; `make-video.mjs v42.3.0 --preview --scenes prototipos/video-v42-3/p5/scenes.json --out …/p5` + captura Edge headless `?t=2`/`?t=9` | vermelho: módulo ausente; depois `AssertionError` (quadro não pintava antes do `ready`: título vazio em t=2); verde: 5/5, glob inteiro 50/50, exit 0; capturas com anel, nome, versão e legenda |
| A08 | `node --test …/scripts/test/*.test.mjs`; `renderStills` de 3 PNG sintéticos | vermelho: `frameIndexAt` 0 ≠ 1 e `{lang}` não resolvido; verde: 57/57, exit 0; pixel central em t=0,8/1,8/2,8/3,9 = vermelho/verde/azul/azul (último quadro segurado) |
| A05 | `gradlew.bat desktopTest --tests "*VideoDemosTest*" --tests "*HelpMediaOptionsTest*" --tests "*ArchitectureRulesTest*"`; `generateVideoMedia -PvideoMediaLanguage=pt\|en -PhelpMediaTopic=video-hud-floating,video-hud-minimize` | vermelho: `VideoDemosTest` "o registro de video lista as duas demos de hud" e "topico fora do catalogo…" `AssertionError` (3 testes, 2 falhas); verde: 38/38; quadros a 4× (flutuante 31, minimizar 92 em PT e EN), conferidos início/meio/fim: clique em Minimizar, voo ao buraco, lista, balão pela lista, restauração |
| A06 | `gradlew.bat desktopTest --tests "*VideoDemosTest*" --tests "*SceneRecorderDragTest*" --tests "*HelpMediaOptionsTest*" --tests "*ArchitectureRulesTest*"`; `generateVideoMedia … video-hud-reorder` | vermelho: `SceneRecorderDragTest.kt:48` "esperava um Press, veio []" e `VideoDemosTest.kt:11`; verde: 41/41; 33 quadros PT/EN (6,3 s): ordem Anthropic·Z.ai·DeepSeek → DeepSeek·Anthropic·Z.ai; o balão abre no press, comportamento real do `HudNotch`, mantido |
| A09a | `node --test …/scripts/test/*.test.mjs`; prévia `prototipos/video-v42-3/cine` capturada no Chrome (puppeteer, 4 s de espera) | vermelho: `screen-box.test.mjs` 5 falhas (bloco `layout` ausente) e `pacing.test.mjs:11` 12 ≠ 15; verde: 74/74. Painel da captura usa até 952 px de largura e a altura livre entre título e legenda. `RELEASE_PACING.maxCharsPerSecond` volta a 15: `speed` recusado pelo OpenRouter (HTTP 400) e george fala ~13,5/s. A prévia "em branco" era artefato do `--virtual-time-budget` do Edge, não do renderizador |
| A07 | `gradlew.bat desktopTest --tests "*VideoDemosTest*" --tests "*VideoDemoPaceTest*" --tests "*SceneRecorderDragTest*" --tests "*HelpMediaOptionsTest*" --tests "*ArchitectureRulesTest*"`; `generateVideoMedia … video-codex-sessions,video-analysis-all` | vermelho: `VideoDemosTest` esperava 5 ids e veio 3; `VideoDemoPaceTest` `expected:<2500> but was:<0>`; verde: 42/42. Codex 25 quadros/11 s ("Copiado" no quadro 21 por 2,5 s); análise 60 quadros/14,5 s ("Tudo" → "Pico por semana · tudo" → comparador). Ritmo único `VideoDemoPace` (2,5 s antes da 1ª ação, cursor 1,4 s, 0,9 s antes do clique, 2,5 s por passo) |
| A13 | `gradlew.bat desktopTest --tests "*VideoDemosTest*" --tests "*VideoDemoPaceTest*" --tests "*ArchitectureRulesTest*"`; `generateVideoMedia` PT/EN das 3 demos da HUD | verde (sem teste novo: só aplica o `VideoDemoPace` já testado no A07); minimizar 29,1 s, flutuante 13,1 s (ponteiro clica no buraco antes de abrir a coluna), reordenar 12,3 s; quadros conferidos em 7 pontos por demo |
| A14 | `node --test …/scripts/test/*.test.mjs`; prévia real com cópia do roteiro (3 beats em `minimize`) | vermelho: 11 falhas (código de beats ausente); verde: 89/89. Beats medidos em 1,4 / 7,4 / 16,4 s (= `voiceAt` + `at`); `lines[lang]` aceita objeto (formato antigo) ou lista de beats; ritmo checado por beat |
| A15 | `node --test …/scripts/test/*.test.mjs`; prévia completa capturada (Chrome/puppeteer) | vermelho → verde do `maxZoom` por cena (agente); verde: 94/94. Painel maior (legenda baixada), `maxZoom` por cena (HUD segue 1,35; análise 1,8), recorte nas telas densas: Codex e análise legíveis nas capturas de 74 s, 86 s e 93 s |
| A16b | `node --test …/scripts/test/*.test.mjs`; prévia de `build/a16b` capturada (Chrome/puppeteer) | vermelho: `surface.test.mjs` 6 falhas (bloco `surface` ausente; validador); verde: 100/100. Sem barra de janela em nenhuma cena (dono reprovou a moldura); `surface: "hud"` sem caixa, clip nem câmera; janelas em cartão raio 10. HUD ainda opaca até o A16a gravar PNG com alfa |
| A16a | `gradlew.bat desktopTest --tests "*VideoDemos*" --tests "*VideoDemoPaceTest*" --tests "*SceneRecorderDragTest*" --tests "*HelpMediaOptionsTest*" --tests "*ArchitectureRulesTest*"`; `generateVideoMedia` PT/EN das 3 demos da HUD; `ffprobe` + composição sobre cinza | vermelho: `VideoDemosTest.kt:57` "o canto (0, 0) tem de ser transparente expected:<0> but was:<255>"; verde: 44/44; quadros `rgba`, HUD opaca sobre fundo transparente. Gravar minimize/reorder inteiros no teste estoura o heap de 1 GB: transparência dessas duas testada com o `HudNotch` real numa cena equivalente |
| A12 | `node --test …/scripts/test/*.test.mjs`; 5 capturas da transição (0–100 %) + câmera, Chrome/puppeteer | vermelho: 7 testes de câmera `MAX_ZOOM is not defined`, `scene-check` `ERR_MODULE_NOT_FOUND`; verde: 78/78. Transição de 1,8 s com o Gargantua da abertura (o dono reprovou o disco chapado); zoom ≤ 1,35×; foco some após 2,5 s; roteiro com chaves de câmera a < 1,5 s é recusado |
| A11 | `node --test …/scripts/test/*.test.mjs`; stills de câmera e transição; render de 10 s | vermelho: `RELEASE_PACING` inexistente (SyntaxError de import — não é asserção) e `flashCurve(1)` 1,2e-16 ≠ 0; **`cameraRectAt` e a curva da transição passaram na primeira execução, sem vermelho registrado** (desvio do TDD); verde: 69/69; render 600 quadros 60 fps em 52,2 s; codificação vira parâmetro (`RELEASE_ENCODE` 60 fps/slow/CRF 16; tour segue `DEFAULT_ENCODE` 30/medium/18) |

## Problemas em aberto e riscos

- Não há captura versionada do buraco negro, do modo flutuante nem da janela de sessões do Codex (`git ls-files`
  por `*.png`/`*.gif`): cena `screen` dessas novidades depende de captura nova.
- Custo de fala não vem na resposta; só o painel do OpenRouter mede.
