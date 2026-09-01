# Detecção de sessão CLI travada — plano de execução

Issue [#177](https://github.com/edilsonvilarinho/usage-monitor/issues/177). Acompanhamento público
no comentário `#issuecomment-5498840775`, editado a cada atividade concluída.

## Problema

O índice CLI sabe quando um turno começou, mas não distingue "sessão terminou normal" de "processo
travou e ficou preso". O segundo caso pode queimar cota sem ninguém perceber, sobretudo em
automação sem supervisão.

## Por que a regra proposta na issue não se sustenta sozinha

A issue propõe reusar `ACTIVE_SESSION_WINDOW_MILLIS` "na direção oposta": sessão sem turno novo
acima de um limiar vira aviso. Medido sobre os 323 transcripts reais de três contas locais
(`~/.claude`, `~/.claude-conta2`, `~/.claude-conta3`, 426 MB):

| Regra | Sessões marcadas |
|---|---|
| "sem turno novo há X" | **323 de 323** |
| "último pedido sem marcador de fim de turno depois dele" | **4 de 181** avaliáveis |

Pelo `last_ts` do índice, sessão encerrada normalmente e sessão travada são idênticas: as duas
param de produzir turno. O discriminador está no transcript, não no índice — o CLI escreve
`{"type":"system","subtype":"turn_duration","durationMs":…}` ao fim de um turno, e pedido do
usuário posterior ao último marcador significa turno aberto que nunca fechou.

## Medições que sustentam o desenho

- **Cobertura do marcador.** Nos 219 transcripts principais (`projects/<slug>/<id>.jsonl`), 181 têm
  o marcador e 38 não; 31 desses 38 não têm turno nenhum e nem entram no índice. Entre as sessões
  que o app indexa, **181/188 ≈ 96%**.
- **Não depende de hook do usuário.** 66 dos 181 arquivos com marcador não têm nenhum
  `stop_hook_summary`. É o CLI que escreve.
- **Exceções conhecidas**: transcripts de subagente (`<id>/subagents/agent-*.jsonl`, 0 de 102, e
  carregam o `sessionId` do pai com `isSidechain: true`) e um punhado de sessões conduzidas por
  harness de agente.
- **Janela de leitura.** Ler os últimos 256 KB reproduz o veredito do arquivo inteiro nos 323
  casos. Com 64 KB um caso degrada para "não avaliada" — subdetecção, nunca alarme falso.
- **Nenhum arquivo termina com `tool_use` sem resposta**: o modo "travado em ferramenta" não deixa
  esse rastro neste corpus.
- **Todas as 4 pendências reais têm +500 h**: são terminais fechados no meio de um turno, não
  processos vivos. Daí o teto de 24 h.

## Decisões

1. **Superfície**: notificação na bandeja pelo caminho de `UsageAlert` que já existe, mais marca na
   linha da tela de Sessões CLI. Sem banner no dashboard.
2. **Limiar**: ligado por padrão, default de 2 h, controle segmentado (30 min / 1 h / 2 h / 4 h) na
   aba Alertas. Segmentado e não chip: é escolha única entre alternativas.
3. **Teto de 24 h**: acima disso é sessão abandonada. Sem ele, as pendências antigas virariam
   alerta a cada arranque, porque a deduplicação de `UsageAlertState` vive em memória.
4. **`NOT_EVALUATED` é um veredito**, não uma falha: sem marcador na cauda a sessão nunca é
   marcada, pela mesma recusa de `withKnownWindow()`.
5. **O caminho do transcript é derivado, não lido de `cli_sessions.file_path`**: com subagentes a
   coluna pode apontar para `subagents/agent-*.jsonl`, e a cauda dele responderia sobre o
   subagente.
6. **Nada muda no laço de indexação e não há bump de `INDEX_SCHEMA_VERSION`**: decodificar toda
   linha na indexação custaria uma releitura completa de 426 MB.
7. **Nenhum texto de prompt ou resposta é lido** — DTO mínimo, mesma regra de `cli_turn_tools`.

## Pontos de situação

| # | Atividade | Comando | Resultado |
|---|---|---|---|
| A0 | Medir o comportamento do ESC na cauda | inspeção dos 323 transcripts por `type`/`subtype` | **inconclusivo**: das 8 interrupções encontradas, 2 são seguidas de `turn_duration` (turno fechado) e as demais são seguidas de novo pedido do usuário, que resolve a pendência. Nenhum caso de interrupção deixando pendência permanente em arquivo com marcador. Verificação manual pendente. |
| A1 | Domain: `CliSessionTail` + `detectStalledSessions` | `gradlew.bat desktopTest --tests "com.usagemonitor.domain.CliStalledSessionTest"` | `BUILD SUCCESSFUL`, 9 testes |
| A2 | Data: leitura da cauda do transcript | `gradlew.bat desktopTest --tests "com.usagemonitor.data.LocalCliSessionDataSourceTest"` | `BUILD SUCCESSFUL`, 57 testes (7 novos) |
| A3 | Caso de uso + publicação no laço de 30 s | `gradlew.bat desktopTest --tests "com.usagemonitor.presentation.SessionPulseViewModelTest"` | `BUILD SUCCESSFUL`, 16 testes (5 novos) |
| A4 | Alerta na bandeja | — | pendente |
| A5 | Configurações → Alertas | — | pendente |
| A6 | Marca na lista de Sessões CLI | — | pendente |
| A7 | CLAUDE.md | — | pendente |
