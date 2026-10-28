# Vídeo de lançamento vertical, com voz, legenda e descrições sociais — issue #503

Procedência: Claude Opus 5.5 (esforço padrão), Claude Code CLI no Windows 11, 2026-10-28, branch
`feat/issue-503-release-video`, commits com a identidade `claude <claude@anthropic.com>`.

## Objetivo

O vídeo da 41.11 (`docs/planos/video-v41-11/roteiro.md`) foi feito à mão numa sessão, e o pipeline ficou em
`build/video-v41-11/`, que não existe mais. O dono pediu um vídeo novo para Instagram e TikTok, no tema do app, com voz
sintética em PT e EN, legenda, sem pressa na fala, cobrindo todas as mudanças da versão, e que o processo vire uma skill:
basta pedir, e ela gera os vídeos e as descrições para X, Instagram e TikTok.

## Fatos confirmados (2026-10-28, `96cf9513`)

- `ls build/video-v41-11/`: `No such file or directory`. Só o roteiro está versionado; renderizador, falas e linha do
  tempo se perderam (a pasta `build/` some com `gradlew clean`).
- Ferramentas: `ffmpeg` 9.0.2 (WinGet), Node em `C:\Program Files\nodejs`, Chrome em
  `C:\Program Files\Google\Chrome\Application\chrome.exe`, Python 3.14.3. Piper, vozes e puppeteer **não** estavam
  instalados (`find`, `pip list`).
- `python -m venv` + `pip install piper-tts` em `%LOCALAPPDATA%\usage-monitor-video\venv`: `onnxruntime 1.31.1`, import
  OK. Vozes da 41.11 baixadas de `huggingface.co/rhasspy/piper-voices`: `en_US-ryan-high` (121 MB),
  `pt_BR-faber-medium` (63 MB). "Usage Monitor, versão quarenta e dois." com `--length-scale 1.2`: 3,40 s.
- `npm i puppeteer-core@24` no mesmo cache: 24.43.1.
- Última versão estável: `v42.0.0` (2026-10-28 14:10), anterior `v41.11.0`; `git log v41.11.0..v42.0.0`: 253 commits.
  O corpo da release lista feat/fix de #439, #440, #441, #445, #453, #460, #463, #478 (cadência), #489, #494, o PATH do
  instalador, a data de reinício da HUD e a escala padrão do sistema.
- Capturas reais e bilíngues: `img/terminal.gif` (EN) e `img/terminal-pt.gif` (PT), geradas pelo código de terminal do
  próprio app (`b0c7e20a`), 60 quadros, 880×578. Quadros parados (duração ≥ 2 s): 22 `status`, 41 visão geral,
  43 sessões, 44 histórico, 45 gráfico da janela, 47 time, 48 análise, 59 configurações.
- Rótulos das etapas do time (#494) em `TeamConnectionLabels.kt`: Endereço/Address, Conexão/Connection,
  Certificado/Certificate, Servidor/Server, Chave/Key.

## Decisões

- **Vertical 1080×1920, 30 fps, H.264 + AAC.** É o formato nativo de Reels e TikTok; o da 41.11 era 1920×1080.
- **Ritmo medido, não lembrado** (pedido "não ficar muito rápido"): no máximo **15 caracteres por segundo** de fala
  (normas de legenda para adulto aceitam 17), 1,2 s de respiro depois de cada fala, 0,35 s entre frases, e duração total
  até 90 s. A linha do tempo **falha** em vez de acelerar quando o roteiro não cabe: corta-se texto, nunca velocidade.
  A primeira regra, 2,6 palavras/s, foi trocada na A05: o português tem muitas palavras curtas e a contagem de palavras o
  punia (medido na v42.0.0: `team` PT, 15 palavras em 3,71 s = 4,0 palavras/s, mas 19,9 caracteres/s — rápido de fato
  pelas duas medidas; `tui` PT, 11 palavras em 4,23 s = 2,6/s e 13,5 caracteres/s — calmo, e a regra de palavras o
  recusava no limite).
- **Escala da voz por idioma:** EN 1,4 e PT 1,5. A 1,2, a faber chegou a 19,9 caracteres/s e a ryan a 15,8 na mesma
  cena; a 1,3/1,45, frases curtas ainda passavam de 16/s (o Piper lê corrido o que é curto).
- **Legenda:** queimada, no máximo 2 linhas de 26 caracteres, dentro da área segura das duas redes (fora dos 250 px de
  cima, dos 420 px de baixo e dos 140 px da direita); entra 150 ms antes da fala e sai 300 ms depois, como na 41.11.
  Também sai em `.srt`.
- **Imagem real primeiro:** cena de terminal usa o quadro do GIF do idioma do vídeo. Funcionalidade sem captura
  (etapas do time, diagonal de ritmo) é desenhada com os tokens e os rótulos reais do app, sem número inventado.
- **Tema:** tokens de `docs/design-system/tokens/` (fundo `--bg`, superfície, borda 1px, raio ≤ 10, Plex Mono nos
  títulos e números, Plex Sans no corpo), anel Gargantua só na abertura e no fechamento, como na 41.11.
- **Ferramentas fora do repositório e fora de `build/`:** venv, vozes e `node_modules` em
  `%LOCALAPPDATA%\usage-monitor-video\`, que o `gradlew clean` não apaga. Intermediários em
  `build/release-video/<versão>/`; vídeos, `.srt` e descrições copiados para `%USERPROFILE%\Videos\usage-monitor\<versão>\`.
- **Roteiro, cenas e descrições versionados** em `docs/planos/video-v<maj>-<min>/`, para o vídeo ser refeito igual.
- **Sem rodada de opções visuais:** o dono pediu "usando nosso tema" e um processo automático.
- **A skill não publica nas redes.** Gera o texto; postar é do dono.

## Atividades

| Atividade | O que faz |
|---|---|
| A01 | Este plano |
| A02 | `collect-changes.mjs`: commits entre tags agrupados por issue e por tipo (TDD) |
| A03 | `timeline.mjs`: tempos das cenas a partir das durações medidas, regras de ritmo, blocos de legenda e SRT (TDD) |
| A04 | `social.mjs`: checagem das descrições (limite de cada rede, hashtags, link) (TDD) |
| A05 | Ritmo por caracteres/s em vez de palavras/s (achado da primeira síntese da v42.0.0) (TDD) |
| A06 | Legenda em linhas equilibradas, sem palavra sozinha (achado do primeiro vídeo da v42.0.0) (TDD) |
| A07 | `synthesize.py`, `setup-tools.mjs`, `render.mjs` + `assets/video.html`, mixagem e mux em `make-video.mjs` |
| A08 | `SKILL.md`: o processo de ponta a ponta e as regras do roteiro |
| A09 | Roteiro, `scenes.json` e `social.json` da v42.0.0; os dois vídeos gerados e conferidos |

## Ponto de situação

| Atividade | Data | Commit | Situação | Execução |
|---|---|---|---|---|
| A01 | 2026-10-28 | `docs(video): plan the release video skill (#503)` | concluída | Plano, sem código |
| A02 | 2026-10-28 | `feat(skills): collect the release changes by issue for the video (#503)` | concluída | Vermelho: `node --test .claude/skills/usage-monitor-release-video/scripts/test/*.test.mjs` com stubs: 4 testes, 4 falhas na asserção (`expected` × `actual`). Verde: 4/4. Execução real: `v41.11.0..v42.0.0`, 215 commits sem merge; issues #439, #440, #441, #445, #453, #460, #463, #489, #494 e 15 feat/fix sem issue (instalador, cadência, escala, HUD) |
| A03 | 2026-10-28 | `feat(skills): time the video scenes and captions from the measured speech (#503)` | concluída | Vermelho: `node --test .claude/skills/usage-monitor-release-video/scripts/test/*.test.mjs` com stubs: 7 testes novos, 7 falhas na asserção. Verde: 10/11 — `expected: 13.5, actual: 11.7`: a última cena recebia a saída no `end`; removido, `total` carrega a saída. Depois 11/11 |
| A04 | 2026-10-28 | `feat(skills): check the social captions for X, Instagram and TikTok (#503)` | concluída | Vermelho: `node --test .claude/skills/usage-monitor-release-video/scripts/test/*.test.mjs` com stubs: 5 testes novos, 5 falhas na asserção. Verde: 16/16. Regras: X ≤ 280 com link = 23 e emoji = 2, link da release obrigatório no X; Instagram ≤ 2200 e TikTok ≤ 4000 sem URL (não é clicável); no máximo 5 hashtags |
| A05 | 2026-10-28 | `fix(skills): pace the speech by characters per second (#503)` | concluída | Achado: `make-video.mjs v42.0.0 --stills` recusou 17 falas pela regra de palavras. Vermelho: `node --test .claude/skills/usage-monitor-release-video/scripts/test/*.test.mjs` com `spokenLength` vazio: 2 falhas na asserção (`expected: 26, actual: 0`; mensagem sem `27 caracteres em 1.00 s (27.0/s)`). Verde: 16/16 |
| A06 | 2026-10-28 | `fix(skills): balance the caption lines (#503)` | concluída | Achado no SRT do primeiro vídeo PT: bloco 2 = `terminal.` sozinho por 1,2 s (enchimento guloso). Vermelho: `node --test .claude/skills/usage-monitor-release-video/scripts/test/*.test.mjs`: `blocos 46 × 9`; depois, com blocos equilibrados, `"tabs" em "Interactive mode puts six tabs…"` e `"uma." em "Um comando mostra…"`. Verde: 18/18 com partição de menor irregularidade, desconto após pontuação, multa para linha de uma palavra e total de linhas par. As 24 falas da v42.0.0 conferidas: nenhuma linha de uma palavra |

## Problemas em aberto e riscos

- R1: Piper e as vozes vêm da rede na primeira execução numa máquina nova (≈ 185 MB). A skill instala; sem rede, para.
- R2: Testado só no Windows 11. Caminhos do cache usam `LOCALAPPDATA`; em outro SO a skill cai para `~/.cache`.
