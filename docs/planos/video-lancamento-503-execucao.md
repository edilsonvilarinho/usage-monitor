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
- **Ritmo medido, não lembrado** (pedido "não ficar muito rápido"): `length_scale 1.2` (a 41.11 usou 1,12), no máximo
  2,6 palavras por segundo de fala por linha, 1,2 s de respiro depois de cada fala, e duração total até 90 s. A linha do
  tempo **falha** em vez de acelerar quando o roteiro não cabe: corta-se texto, nunca velocidade.
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
| A05 | `synthesize.py`, `render.mjs` + `assets/video.html`, mixagem e mux com ffmpeg, `make-video.mjs` |
| A06 | `SKILL.md`: o processo de ponta a ponta e as regras do roteiro |
| A07 | Roteiro, `scenes.json` e `social.md` da v42.0.0; os dois vídeos gerados e conferidos |

## Ponto de situação

| Atividade | Data | Commit | Situação | Execução |
|---|---|---|---|---|
| A01 | 2026-10-28 | `docs(video): plan the release video skill (#503)` | concluída | Plano, sem código |

## Problemas em aberto e riscos

- R1: Piper e as vozes vêm da rede na primeira execução numa máquina nova (≈ 185 MB). A skill instala; sem rede, para.
- R2: Testado só no Windows 11. Caminhos do cache usam `LOCALAPPDATA`; em outro SO a skill cai para `~/.cache`.
