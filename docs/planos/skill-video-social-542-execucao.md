# Skill de vídeos para redes a partir de um tema — issue #542

Procedência: Claude Opus 5.5, Claude Code CLI no Windows 11, 2026-11-06, branch `feat/issue-542-social-video`, commits com a
identidade `claude <claude@anthropic.com>`.

## Objetivo

Transformar o pipeline do #540 numa skill: o dono entrega um tema, com informações, imagens ou só texto, e uma chave temporária
da OpenRouter. A skill devolve roteiro para aprovação e, depois, os vídeos de TikTok, Shorts, LinkedIn, X e YouTube e as
descrições, sempre com o padrão aprovado.

## Fatos confirmados (2026-11-06, `1175c7c8`)

- `prototipos/workflow-video-v3/final/`: `scene.js` 642 linhas, `build.mjs` 286, `audio.py` 517, `render.mjs` 259; `wc -l`.
- Acoplamento ao tema: `grep` mostra poses por `beat.id` (`scene.js:126-157`), `SHORT_ORDER` (`build.mjs:29`), clímax em `comment` e
  resolução em `close` (`audio.py:232-252`).
- Testes de skill rodam por `node --test` local; o CI não os executa (`ci.yml`, job `ci-scripts`, só `tools/ci/`). Precedente:
  `.claude/skills/usage-monitor-release-video/scripts/test/`.
- `%LOCALAPPDATA%\usage-monitor-video` tem venv com numpy e scipy e puppeteer-core (`setup-tools.mjs` da skill de release).
- Medido no #540: Matilda 12,2 caracteres/s, George 12,4; teto de 60 s cerca de 630 caracteres; 3400 caracteres deram 255 s de fala.
- `opencode run` em primeiro plano sem entrada fechada ficou 10 minutos com 0 bytes; com `</dev/null` terminou.

## Decisões

- Vídeo novo é dado, não código: `video.json` por tema. Cena, voz e trilha ficam congeladas; o dono aprovou esse padrão.
- Roteiro é parada obrigatória: o dono recusou um roteiro raso depois da escolha do visual, e render longo é caro.
- Roteiro explica cada etapa; 60 s é resumo com teto medido, 5 minutos é o documento completo.
- Número em tela só com origem: `facts` do `video.json`; a validação recusa o que não estiver lá.
- Prévia sem API: silêncio com a duração estimada, para conferir quadros antes de gastar.
- Chave só em variável de ambiente; nunca em arquivo versionado nem em saída de comando.
- Imagem entra como painel na cena; sem geração de imagem por IA.

## Atividades

- A01 plano.
- A02 contrato e validação.
- A03 linha do tempo, legendas e cues.
- A04 voz e prévia.
- A05 cena e página.
- A06 trilha, efeitos e render.
- A07 `SKILL.md`.
- A08 execução ponta a ponta.

## Ponto de situação

| Atividade | Estado | Comando e resultado |
|---|---|---|
| A01 | Feito | Sem harness de teste: documento conferido por leitura. |
| A02 | Feito | Vermelho: `node --test scripts/test/spec.test.mjs`, stub, `0 !== 2` em ERR_ASSERTION, 19 testes, 17 falhas. Verde: 19 de 19. |
| A03 | Feito | Vermelho: `node --test scripts/test/timeline.test.mjs`, stub, `AssertionError: Expected values to be strictly equal` em `gap é limitado a 3,0 s`, 19 falhas. Verde: 19 de 19. |
| A06 | Feito | Sem teste unitário: porte de código de áudio e navegador. Validado pela execução da A08; `grep` sem id de tema em `audio.py`. |

## Problemas em aberto e riscos

- A chamada real à API não é exercida neste trabalho: não há chave válida para isso. O teste cobre o pedido por `fetch` simulado.
- Testes da skill não rodam no CI, como os da skill de release.
- Escuta humana continua sendo do dono.
