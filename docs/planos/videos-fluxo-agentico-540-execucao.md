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

## Problemas em aberto e riscos

- Escuta humana é do dono: agente mede duração e loudness, não pronúncia nem beleza da voz.
- Termos de uso de saída do Lyria não verificados; faixa de Lyria só vai ao vídeo final depois disso.
- Ranking público de TTS não cobre PT-BR nem os modelos atuais do catálogo: a escolha de vozes é por disponibilidade.
