# Vídeo da versão 41.11.0 — roteiro (EN + PT)

Fonte: `git log v41.10.0..v41.11.0` (68 commits) e as issues #411, #414, #416–#419, #421, #426,
#430, #432, #434, AE4 e AE7. Nada no roteiro vai além do que está nesses commits e nos planos
`hud-cosmic-compositor-430-execucao.md` e `hud-cosmic-arrasto-434-execucao.md`.

## Decisões do usuário (2026-10-21)

| # | Pergunta | Resposta |
| --- | --- | --- |
| U1 | Direção visual (rodada AF, galeria `build/gargantua-preview/video-v41-11-options.html`) | **AF5 · Mergulho no tesseract** |
| U2 | Duração | **~1 min** (a primeira versão, de 3 min, foi trocada por esta) |
| U3 | Destaque | Suporte a Windows, Linux e macOS, e **o processo do COSMIC** (três cenas) |
| U4 | Voz | Piper, ouvida na prévia e aprovada pelo uso |

## Parâmetros

| Item | Valor |
| --- | --- |
| Duração | **1:04** (64,0 s), a mesma linha do tempo nos dois idiomas |
| Voz | Piper offline: `en_US-ryan-high` e `pt_BR-faber-medium`, `length_scale 1.12`, 0,35 s entre frases |
| Tempo de cada cena | entrada de 0,55 s + a fala mais longa entre EN e PT + 1,0 s de respiro. O título tem 1,6 s de entrada (o anel acende antes) e 1,5 s de saída (o mergulho). O fechamento tem 1,8 s de saída |
| Legenda | queimada no quadro, no máximo 2 linhas, entra 150 ms antes da fala e sai 300 ms depois. Também sai em `.srt` |
| Trilha | drone grave a ≈ −30 dBFS, que sobe no mergulho e no renascimento do anel |
| Saída | `usage-monitor-41.11-en.mp4` e `usage-monitor-41.11-pt.mp4`, 1920×1080, 30 fps, H.264 + AAC |
| Tema | Gargantua do design system: anel real do `AppGargantuaRing`, arco começando às 12 h e entrando uma vez até o valor, ouro e brasa só no cenário, palavra ao lado da cor |
| Gesto (AF5) | Cada assunto chega numa célula do tesseract, vindo do ponto de fuga em 900 ms (easeOut), e sai passando pela câmera em 700 ms (easeIn). Fios de luz verticais marcam a célula acesa. A voz lê o número por extenso e a legenda mostra `41.11` |

## Cenas

| # | Tempo | Visual | EN | PT |
| --- | --- | --- | --- | --- |
| 1 | 0:00–0:06 | O anel de fótons acende, o título aparece dentro da sombra e a câmera mergulha no horizonte | Usage Monitor, version 41.11. | Usage Monitor, versão 41.11. |
| 2 | 0:06–0:11 | Tesseract. Três células acendem uma por vez: Windows (NSIS · launcher de console), Linux (.deb · .rpm · X11 + Wayland), macOS (.dmg) | One HUD on Windows, Linux and macOS. | Uma HUD no Windows, no Linux e no macOS. |
| 3 | 0:11–0:15 | Desktop com o carrossel de boas-vindas (5 passos) e o cartão "Dica do dia" (#411) | A welcome guide shows you around. | Um guia de boas-vindas apresenta o app. |
| 4 | 0:15–0:19 | Janela Análise em painel único, selo "Ao vivo · HH:mm:ss" (#414) | Analysis is now one live panel. | A Análise virou um painel único, ao vivo. |
| 5 | 0:19–0:24 | Celular com o bot: `/status`, barra de quadrados por zona, teclado fixo (AE4, AE7). Terminal com `--version`, `--locale`, `--user-data-dir` (#418, #419) | The Telegram bot and the command line learned new tricks. | O bot do Telegram e a linha de comando ganharam novos recursos. |
| 6 | 0:24–0:29 | `app.log` com segredos tarjados e `--diagnostics` gerando o pacote sem abrir janela (#416) | A diagnostics package helps find problems fast. | Um pacote de diagnóstico ajuda a achar problemas rápido. |
| 7 | 0:29–0:35 | Dois monitores. A HUD atravessa de um para o outro e continua transparente; o retângulo preto da 41.10 aparece riscado (#421, #426) | On Linux: no black screen on Wayland, and transparency across monitors. | No Linux: sem tela preta no Wayland, e transparência entre monitores. |
| 8 | 0:35–0:44 | **COSMIC 1/3.** Selo "Pop!_OS · COSMIC · Wayland + XWayland". O pacote `--diagnostics` do usuário se abre: "pedido 404,0" tracejado, a HUD indo parar no outro monitor e a linha real do log `pedido 404,0 458x368 · obtido 2699,211 459x368 (diferente)` (#430) | COSMIC took the most work. A Pop!_OS user's diagnostics showed the compositor ignored our window positions. | O COSMIC deu mais trabalho. O diagnóstico de um usuário do Pop!_OS mostrou que o compositor ignorava a posição das janelas. |
| 9 | 0:44–0:50 | **COSMIC 2/3.** Terminal: `distrobox enter fedora-44`, `cosmic-comp` 1.10.0 aninhado, `./gradlew run`. Ao lado, o COSMIC aninhado com a HUD de borda ciano e a janela que não arrasta. Checklist: reproduzido, tipos de janela do AWT medidos, arrasto depois da folga medido (#434) | So we ran COSMIC here, reproduced the bug, and measured every fix. | Então rodamos o COSMIC aqui, reproduzimos o defeito e medimos cada correção. |
| 10 | 0:50–0:58 | **COSMIC 3/3.** A borda ciano some da HUD (fora do gerenciamento de janelas), a janela Análise é arrastada pela barra de título e aparece o selo "confirmado no COSMIC real · beta.11 → 41.11.0" | Now windows drag, the HUD stays in place, and the user confirmed it on real COSMIC. | Agora as janelas arrastam, a HUD fica no lugar, e o usuário confirmou no COSMIC de verdade. |
| 11 | 0:58–1:04 | O tesseract colapsa num ponto, que renasce como o anel com os arcos de cota. "Windows · Linux · macOS" e o link de releases | Usage Monitor 41.11. Get it on GitHub. | Usage Monitor 41.11. Baixe no GitHub. |

## Produção (reproduzível)

Tudo em `build/video-v41-11/`, pasta ignorada pelo git:

- `lines.json`: as falas (legenda e o texto lido, quando são diferentes).
- `timeline.json`: gerado da duração medida de cada fala.
- `video.html` + `scenes.js`: o renderizador. `renderFrame(t, idioma)` é puro, e o mesmo `t` sempre desenha o mesmo quadro.
- Pipeline: Piper → `timeline.json` → Chrome headless (puppeteer-core, JPEG 0,95 por quadro) → ffmpeg `libx264 -crf 17` → mixagem (numpy) → mux AAC.
