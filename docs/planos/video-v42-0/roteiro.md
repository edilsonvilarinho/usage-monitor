# Vídeo da versão 42.0.0 — roteiro (EN + PT)

Fonte: `collect-changes.mjs v42.0.0` (`v41.11.0..v42.0.0`, 215 commits sem merge) — issues #439, #440, #441, #445, #453,
#460, #463, #494 e os feat/fix sem issue (PATH do instalador, data de reinício na HUD, escala do sistema, cadência de
1 min). #460 (canal de configuração do terminal) e #489 (imagem do servidor no Node 22) são correção interna, sem
cena. Feito pela skill `usage-monitor-release-video` (issue #503). Cenas em `scenes.json`, descrições em `social.json`.

## Parâmetros (medidos na geração de 2026-10-28)

| Item | Valor |
| --- | --- |
| Formato | 1080×1920, 30 fps, H.264 + AAC, Instagram Reels e TikTok |
| Duração | **1:28** (87,8 s), a mesma linha do tempo nos dois idiomas |
| Voz | Piper `en_US-ryan-high` (escala 1,4) e `pt_BR-faber-medium` (1,5), 0,35 s entre frases |
| Ritmo | fala mais rápida: 15,0 caracteres/s (`statusline` PT); teto 15. 1,2 s de respiro depois de cada fala |
| Legenda | queimada, até 2 linhas de 26 caracteres, acima dos 420 px de baixo; também em `.srt` |
| Imagens | quadros reais de `img/terminal.gif` (EN) e `img/terminal-pt.gif` (PT): 22, 41, 43, 45 |
| Trilha | drone 55/82 Hz a −16 dB sob as falas; medido: EN −16,2 LUFS / pico −1,4 dBFS, PT −15,8 LUFS / −1,5 dBFS |

## Cenas

| # | Tempo | Visual | EN | PT |
| --- | --- | --- | --- | --- |
| 1 | 0:00–0:07 | Anel Gargantua, "Now in your terminal", 42.0 | Usage Monitor 42 is here. Now in your terminal, too. | Usage Monitor 42: agora também no seu terminal. |
| 2 | 0:07–0:13 | #439 · `usage-monitor status` real, com a linha de tempo de cada cota | One command shows every quota and its pace. | Um comando mostra todas as cotas e o ritmo de cada uma. |
| 3 | 0:13–0:21 | #439 · `status --json`, `check --max 80`, `wait <cota> --below 50` digitados, com o texto da ajuda | JSON output, a check that exits by state, and a wait until a quota drops. | Saída em JSON, um check que sai pelo estado, e uma espera até a cota baixar. |
| 4 | 0:21–0:28 | #439 · #453 · `statusline claude-code`, `statusline tmux`, `status --watch` | A status line for Claude Code and tmux, or a live pane. | Uma linha de status para o Claude Code e o tmux, ou um painel ao vivo. |
| 5 | 0:28–0:34 | #440 · `service install` (systemd, só Linux), `service run` | No screen needed. The service collects on its own. | Sem tela, o serviço faz a coleta sozinho. |
| 6 | 0:34–0:42 | #440 · `setup`, `keys set <fonte>`, `telegram pair`, `team join` | Setup, API keys, accounts, Telegram and team: all from the terminal. | Configuração inicial, chaves, contas, Telegram e time: tudo pelo terminal. |
| 7 | 0:42–0:50 | #441 · `usage-monitor tui`, aba Visão geral real | Interactive mode puts six tabs on one screen, even over SSH. | O modo interativo reúne seis abas numa tela, até por SSH. |
| 8 | 0:50–0:55 | #445 · aba Sessões real, coluna de modelo e composição | Each session now splits by model. | Cada sessão agora se divide por modelo. |
| 9 | 0:55–1:02 | #463 · gráfico da janela no Histórico, "à frente do tempo" | Analysis and History put each quota window on the pace diagonal. | Análise e Histórico mostram cada janela da cota na diagonal de ritmo. |
| 10 | 1:02–1:09 | #494 · trilha Endereço ✓ › Conexão ✕ "nada responde" › Certificado · Servidor · Chave (rótulos de `TeamConnectionLabels.kt`) | Team server down? Now you see, step by step, where it stopped. | Servidor do time fora? Você vê a etapa em que parou. |
| 11 | 1:09–1:20 | "E mais": PATH do Windows, data de reinício na HUD, escala do sistema, cota em movimento a cada minuto | Plus: the command on your PATH, reset dates on the HUD, the system scale, and faster reads while a quota moves. | E mais: o comando no PATH, a data de reinício na HUD, a escala do sistema e leitura a cada minuto quando a cota se mexe. |
| 12 | 1:20–1:28 | Anel, 42.0, "Get it on GitHub", link do repositório | Usage Monitor 42. Get it on GitHub. | Usage Monitor 42. Baixe no GitHub. |

## Produção

`node .claude/skills/usage-monitor-release-video/scripts/make-video.mjs v42.0.0` — entregáveis em
`~/Videos/usage-monitor/v42.0.0/` (`usage-monitor-42.0-{en,pt}.mp4`, `.srt`, `social.md`).
