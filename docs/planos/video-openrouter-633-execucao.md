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

## Atividades

| ID | Atividade |
|---|---|
| A01 | Este plano |
| A02 | Cliente OpenRouter (fala + imagem) com testes de contrato |
| A03 | Síntese pelo OpenRouter no `make-video.mjs`, cache por assinatura |
| A04 | `--preview`: HTML com `video.html` sincronizado à trilha |
| A05 | 5 roteiros da v42.3.0 em prévia; escolha do dono |
| A06 | Vídeo v42.3.0, `roteiro.md`, `social.json`, SKILL.md |

## Ponto de situação

| ID | Comando | Resultado |
|---|---|---|
| A01 | plano escrito | — |
| A02 | `node --test …/scripts/test/openrouter.test.mjs` | vermelho: 9 × `Error: não implementado` (esboço); verde: exit 0 |
| A03 | `node --test …/test/speak.test.mjs …/test/setup-tools.test.mjs` | vermelho: 6 × `Error: não implementado` (esboço do `speak`), depois `pt.status` 3.1 ≠ 3 (erro da fixture: `ó` tem 2 bytes); verde: 4 arquivos, 27/27, exit 0 |

## Problemas em aberto e riscos

- Não há captura versionada do buraco negro, do modo flutuante nem da janela de sessões do Codex (`git ls-files`
  por `*.png`/`*.gif`): cena `screen` dessas novidades depende de captura nova.
- Custo de fala não vem na resposta; só o painel do OpenRouter mede.
