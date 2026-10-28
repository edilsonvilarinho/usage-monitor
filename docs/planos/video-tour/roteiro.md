# Tour em vídeo 16:9 — roteiro (EN + PT)

Fonte: README (Features, Supported integrations) e as 17 demos da janela de ajuda, gravadas pelo próprio app
(`HelpMediaGenerator --frames`, dados sintéticos). Protótipo (animatic, vozes, trilha) aprovado pelo dono em 2026-10-28.
Feito pela skill `usage-monitor-release-video` (`make-tour.mjs`, issue #505). Cenas em `scenes.json`.

## Parâmetros (medidos na geração de 2026-10-28)

| Item | Valor |
| --- | --- |
| Formato | 1920×1080, 30 fps, H.264 + AAC 192 kb/s |
| Duração | **5:52** (352,4 s), a mesma linha do tempo nos dois idiomas; 20 capítulos + abertura e fechamento |
| Voz | Kokoro 82M: `pf_dora` (PT, velocidade 0,78) e `am_michael` (EN, 0,86), 0,5 s entre frases |
| Ritmo | fala mais rápida: 13,8 caracteres/s; teto 14. 1,4 s de respiro depois de cada fala |
| Legenda | queimada, até 2 linhas de 42 caracteres; também em `.srt` |
| Imagens | demos da ajuda a 2× (2000×840 por quadro), em PT no vídeo PT e em EN no vídeo EN |
| Trilha | sintetizada (`soundtrack.py`, semente 505): drone 55/82 Hz, pad Am9 → Fmaj7 → Cmaj7 → G6, sinos a 72 bpm |
| Efeitos | subida (abertura), lente (cada capítulo), deslize (captura nova), sino (foco), teclas (terminal) |
| Mixagem | voz +8 dB, trilha −12 dB com sidechain sob a fala; medido: fala −19 dB, trilha no respiro −30 dB |
| Loudness | `loudnorm` em duas passadas (linear): EN −16,1 LUFS / pico −1,2 dBFS, PT −16,0 LUFS / −1,3 dBFS |

## Cenas

| # | Tempo | Visual | EN | PT |
| --- | --- | --- | --- | --- |
| 0 | 0:00–0:13 | anel Gargantua · Todas as cotas, num só lugar | This is Usage Monitor. Every quota from all your AI tools, in one place, without opening a single dashboard. | Este é o Usage Monitor. As cotas de todas as suas ferramentas de IA, num só lugar, sem abrir nenhum painel. |
| 1 | 0:13–0:35 | Onze fontes — `dashboard` | It reads eleven sources: Anthropic, Codex, MiniMax, DeepSeek, OpenCode, Kilo, OpenRouter, Gemini, Antigravity and Cursor. Turn on only the ones you use. API keys stay on your computer. | São onze fontes: Anthropic, Codex, MiniMax, DeepSeek, OpenCode, Kilo, OpenRouter, Gemini, Antigravity e Cursor. Você liga só as que usa. As chaves de API ficam no seu computador. |
| 2 | 0:35–0:56 | Barra HUD — `window-modes` (foco) | The HUD bar sits on the edge of your screen. One ring per account, one arc per quota: weekly outside, five hours inside. Hover a ring, and the balloon shows what was used, what is left and when it resets. | A barra HUD fica na borda da tela. Um anel por conta, um arco por cota: semanal por fora, cinco horas por dentro. Passe o mouse num anel, e o balão mostra quanto foi usado, quanto resta e quando reinicia. |
| 3 | 0:56–1:11 | Várias contas — `accounts` | Several Claude and Codex accounts on the same computer. Each one gets a name, a colour and an emoji, so the ring tells them apart at a glance. | Várias contas Claude e Codex no mesmo computador. Cada uma ganha um nome, uma cor e um emoji, e o anel diz qual é qual num relance. |
| 4 | 1:11–1:33 | Sessões CLI — `cli-sessions` | Claude Code sessions are read from the local logs: tokens, cache, active time and an estimated cost at list price. Each session gets a context verdict: healthy, attention or saturated. | As sessões do Claude Code são lidas dos registros locais: tokens, cache, tempo ativo e um custo estimado a preço de tabela. Cada sessão recebe um veredito de contexto: saudável, atenção ou saturada. |
| 5 | 1:33–1:46 | Detalhamento — `breakdown` | The breakdown splits the same usage by project, by model and by branch, with a filter, sorting and how much the cache saved. | O resumo separa o mesmo uso por projeto, por modelo e por branch, com filtro, ordenação e quanto o cache economizou. |
| 6 | 1:46–2:03 | Análise da sessão — `session-analysis` (foco) | The analysis compares your usage with how much of the window has already passed. Above the line, the quota runs out before the reset. Below it, it lasts. | A análise compara o uso com o tempo que já passou da janela. Acima da linha, a cota acaba antes do reinício. Abaixo dela, a cota chega até lá. |
| 7 | 2:03–2:20 | Histórico — `history` | History keeps every reading and draws the curve of each window, from five hours to thirty days. The summary comes before the chart, and the report comes out as a PDF. | O histórico guarda cada leitura e desenha a curva de cada janela, de cinco horas a trinta dias. O resumo vem antes do gráfico, e o relatório sai em PDF. |
| 8 | 2:20–2:38 | Comparação — `comparison` | The comparison puts models and sources side by side: tokens, cost, cache, throughput and the quota of each source. Colour marks the column maximum, and the number is always written. | A comparação põe modelos e fontes lado a lado: tokens, custo, cache, vazão e a cota de cada fonte. A cor marca o máximo da coluna, e o número está sempre escrito. |
| 9 | 2:38–2:48 | Orçamento — `budget` | Set a monthly budget in dollars. See where the current pace ends the month. | Defina um orçamento do mês em dólares e veja onde o ritmo atual vai fechar o mês. |
| 10 | 2:48–3:06 | Alertas — `alerts` | Tray alerts when a quota crosses a threshold, before and after a reset, and when a session saturates or stops answering. And quiet hours, so nothing rings at night. | Alertas na bandeja quando a cota cruza um limiar, antes e depois do reinício, e quando uma sessão satura ou para de responder. E um período de silêncio, para nada tocar à noite. |
| 11 | 3:06–3:17 | Exportar — `export` | Sessions and summaries export to CSV or JSON, and the report comes out as a PDF. | Sessões e resumos saem em CSV ou JSON, e o relatório sai em PDF. |
| 12 | 3:17–3:34 | Modo terminal — `usage-monitor status --json`, `usage-monitor check --max 80`, `usage-monitor statusline tmux` | The same app runs in the terminal. Status, check and wait for your scripts, a status line for Claude Code and tmux, and a full-screen interactive mode. | O mesmo app roda no terminal. Status, check e wait para os seus scripts, uma linha de status para o Claude Code e o tmux, e um modo interativo em tela cheia. |
| 13 | 3:34–3:51 | Time — `team` | With a server you host, which is optional, the team sees the usage of each member: sessions, tokens, cost and each share. And a thirty-day trend. | Com um servidor seu, que é opcional, o time vê o uso de cada integrante: sessões, tokens, custo e a participação de cada um. E a tendência de trinta dias. |
| 14 | 3:51–4:03 | Presença — `presence` | Presence shows who is working now and who is only connected. And the last signal of each machine. | A presença mostra quem está trabalhando agora, quem está só conectado, e o último sinal de cada máquina. |
| 15 | 4:03–4:20 | Rede local — `web-access` | Turn on local network access, and open the HUD in your phone browser. Anyone on the network with the full address sees usage data, and nothing more. | Ligue o acesso pela rede local, e abra a barra no navegador do celular. Quem está na rede com o endereço completo vê os dados de uso, e nada além disso. |
| 16 | 4:20–4:32 | Bot do Telegram — `telegram-bot` | Or pair a Telegram bot. It sends the alerts and answers commands, with usage metadata only. | Ou pareie um bot do Telegram. Ele manda os alertas e responde a comandos, só com metadados de uso. |
| 17 | 4:32–4:45 | Aparência — `appearance` | Thirteen dark palettes and thirteen light ones, English and Portuguese, and an interface scale for every window. | Treze paletas escuras e treze claras, português e inglês, e uma escala de interface para todas as janelas. |
| 18 | 4:45–4:59 | Atualizações — `updates` | On Windows and Linux, the app updates itself. It downloads the new version, checks the file, and applies it when you close it. | No Windows e no Linux, o app se atualiza sozinho. Ele baixa a versão nova, confere o arquivo e aplica quando você fecha. |
| 19 | 4:59–5:19 | Confiança — lista: Cada fonte falha sozinha · Limite de requisições espera · Nunca prompt nem resposta | Each source fails on its own and keeps its last reading. Rate limits back off instead of insisting. And no prompt or reply ever leaves the app: only usage metadata. | Cada fonte falha sozinha e mantém a última leitura. O limite de requisições faz a leitura esperar em vez de insistir. E nenhum prompt ou resposta sai do app: só metadados de uso. |
| 20 | 5:19–5:35 | Instalação — lista: Windows · Linux · macOS | There is an installer for Windows, deb and rpm packages and a script for Linux, and a DMG for macOS. It can also start with your system. | Tem instalador para Windows, pacotes deb e rpm e um script para Linux, e DMG para macOS. Ele também pode abrir junto com o sistema. |
| 21 | 5:35–5:52 | anel Gargantua · Grátis e de código aberto · MIT | Usage Monitor is free and open source. For Windows, Linux and macOS. Get it on GitHub. | O Usage Monitor é grátis e de código aberto. Para Windows, Linux e macOS. Baixe no GitHub. |
