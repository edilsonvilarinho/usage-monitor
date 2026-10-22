# Prints do modo interativo — #441

Telas reais de `usage-monitor tui`, abertas **pelo SSH** num servidor Linux sem ambiente gráfico (contêiner Fedora 44 com systemd e sshd, sem `libX11`/`libGL`/`fontconfig`), contra o serviço do usuário (`service install`) coletando com a chave real do OpenCode Go. As sessões do Claude Code são de exemplo (só contagens de tokens e nomes de modelo), e o time é o servidor de `server/` rodando no host, com pessoas de exemplo. Capturadas com `tmux capture-pane -e` e renderizadas em PNG (Noto Sans Mono, Pillow). Nenhuma chave, token ou `accountUuid` aparece nos prints (conferido por `grep` nas capturas). Plano: [`../modo-terminal-tui-441-execucao.md`](../modo-terminal-tui-441-execucao.md), A09.

- [`01-visao-geral`](01-visao-geral.png) — aba 1, truecolor escuro, 96 colunas
- [`02-visao-geral-clara`](02-visao-geral-clara.png) — terminal claro (`COLORFGBG=0;15`)
- [`03-visao-geral-16-cores`](03-visao-geral-16-cores.png) — 16 cores (sem `COLORTERM`)
- [`04-visao-geral-sem-cor`](04-visao-geral-sem-cor.png) — `NO_COLOR=1`
- [`05-visao-geral-ascii`](05-visao-geral-ascii.png) — `LC_ALL=C NO_COLOR=1`: glifos ASCII, sem acento
- [`06-visao-geral-80-colunas`](06-visao-geral-80-colunas.png) — 80 colunas
- [`07-visao-geral-empilhada`](07-visao-geral-empilhada.png) — 70 colunas: lista e detalhe empilhados
- [`08-visao-geral-empilhada-detalhe`](08-visao-geral-empilhada-detalhe.png) — 70 colunas, `enter` abre a conta
- [`09-ingles`](09-ingles.png) — `--locale en`
- [`10-terminal-pequeno`](10-terminal-pequeno.png) — abaixo de 40×12 (6.3)
- [`11-sessoes`](11-sessoes.png) — aba 2, hoje
- [`12-sessoes-7-dias`](12-sessoes-7-dias.png) — `→` 7 dias
- [`13-sessoes-turnos`](13-sessoes-turnos.png) — `enter`: os turnos da sessão
- [`14-sessoes-csv`](14-sessoes-csv.png) — `e`: o CSV do app, com o aviso
- [`15-sessoes-80-colunas`](15-sessoes-80-colunas.png) — aba 2 em 80 colunas
- [`16-historico`](16-historico.png) — aba 3, 24 h
- [`17-historico-7-dias`](17-historico-7-dias.png) — aba 3, 7 dias
- [`18-time`](18-time.png) — aba 4, presença e consumo do dia
- [`19-time-ascii-80`](19-time-ascii-80.png) — aba 4 em ASCII, 80 colunas
- [`20-ajuda`](20-ajuda.png) — `?` (3.5)
- [`21-config-secoes`](21-config-secoes.png) — aba 5, as seções
- [`22-config-alertas`](22-config-alertas.png) — aba 5, Alertas
- [`23-config-editando`](23-config-editando.png) — campo de texto aberto
- [`24-config-aplicado`](24-config-aplicado.png) — `config set` pelo campo, com o serviço vivo: "o serviço já está usando"
- [`25-config-valor-invalido`](25-config-valor-invalido.png) — valor recusado, com a frase do `config set`
- [`26-config-chave-sem-eco`](26-config-chave-sem-eco.png) — chave em edição: nada ecoa, nem o tamanho
- [`27-config-confirmar`](27-config-confirmar.png) — "Sair do time" pedindo o segundo `enter`
- [`28-config-proxy`](28-config-proxy.png) — Rede e proxy: vale depois de reiniciar o serviço
- [`29-config-telegram`](29-config-telegram.png) — Telegram sem token
- [`30-config-ascii-80`](30-config-ascii-80.png) — aba 5 em ASCII, 80 colunas
- [`31-config-empilhada`](31-config-empilhada.png) — aba 5 empilhada, 70 colunas
- [`32-leitura-falhou`](32-leitura-falhou.png) — a leitura que falhou (6.2), com os números anteriores
- [`33-time-servidor-fora`](33-time-servidor-fora.png) — aba 4 com o servidor do time fora do ar
- [`34-sem-terminal`](34-sem-terminal.png) — `tui` sem terminal: a frase e saída 1
