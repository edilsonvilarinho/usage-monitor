# Janela "Análise": painel único ao vivo (rodada AD, direção AD1)

## Ponto de situação

**Estado atual:** `Em execução — A00 concluída.`

| Campo | Valor |
| --- | --- |
| Modelo | Claude Opus 5.5 (`claude-opus-5-5`) |
| Ferramenta | Claude Code (desktop, aba Code) |
| Data | 2026-10-14 |
| Branch | `feat/analysis-live-panel` |
| Autor dos commits | identidade temporária `claude`, trailer `Co-Authored-By: Claude Opus 5.5` |

- Galeria da rodada em [`analise-modal-visual/options.html`](analise-modal-visual/options.html) (rodada **AD**, 16 opções + hoje).
- As alterações pré-existentes em `server/package-lock.json` e `docs/llm/` ficam fora desta entrega.

## Decisões do usuário (2026-10-14)

| # | Pergunta | Resposta |
| --- | --- | --- |
| U1 | Direção visual | **AD1 · Painel único**: sem abas; as duas janelas lado a lado, as 24 h com a vazão por cima, intervalos e antes × agora em tabela |
| U2 | Atualização | **Tempo real**: relê a cada coleta nova da conta e a cada 30 s com a janela visível |
| U3 | Botão "Atualizar" | **Sai**; no lugar, o selo "Ao vivo · HH:mm:ss" |
| U4 | tok/s do Codex (índice sincronizado a cada 10 min) | **Não sincronizar a mais**; a linha da vazão do Codex diz a hora do índice |
| U5 | Gráfico | Desenhado em Compose no tema (consequência da AD1); o bot continua com os PNGs. Desfaz, só na janela, a decisão B21 "um desenho para as duas portas" |

## Diagnóstico (evidência)

| # | Ponto | Evidência | Estado |
| --- | --- | --- | --- |
| D1 | A janela não atualiza sozinha | `AnalysisViewModel`: "Lê sob demanda, ao abrir e no Atualizar"; só `refresh()` relê | confirmado |
| D2 | Leitura que falha apaga a tela | `refresh()`: exceção ou `null` → `AnalysisUiState.Empty`, mesmo com dados na tela | confirmado |
| D3 | Cota muda a cada coleta | `ApiUsageStats.fetchedAt` muda a cada coleta da conta (60 s com sessão CLI, 5 min sem) | confirmado |
| D4 | Vazão do Claude | índice sincronizado a cada 30 s pelo `SessionPulseViewModel` (`DEFAULT_INTERVAL_MILLIS`, `autoStart = true`) | confirmado |
| D5 | Vazão do Codex | índice sincronizado a cada 10 min (`CODEX_CLI_SESSION_INDEX_INTERVAL_MILLIS`); nenhuma hora da última sincronização é guardada | confirmado |
| D6 | Custo de uma releitura | SQLite (Python) numa cópia de `usage-history.db` (30 459 turnos): as 41 consultas de vazão de `analysisThroughputCuts` = 8 ms; 3 586 linhas de 7 dias = 9 ms. O caminho JVM não foi medido | medido |
| D7 | Gráficos atuais | PNG do bot (`TelegramAnalysisRenderer`) esticado com `ContentScale.FillWidth` (`ModalWindowsHost.kt`): escuro nos dois temas, sem tooltip | confirmado |

## Execução — uma atividade, um commit

| # | Atividade |
| --- | --- |
| A00 | `docs`: plano e galeria AD |
| A01 | `feat(analysis)`: painel único AD1 ao vivo — relê no `fetchedAt` da conta e a cada 30 s com a janela visível, para ao fechar, falha mantém os números; cards das janelas, 24 h com tok/s e bolha, tabelas de intervalos e antes × agora, selo "Ao vivo" no lugar de "Atualizar"; abas e PNG saem da janela; hora do índice do Codex; Ajuda e GIF, protótipo, `presentation.md` e histórico da skill no mesmo commit |

**Desvio do rascunho:** o rascunho separava "ao vivo" (A01) e "painel" (A02). Os dois mexem nas mesmas linhas do
`AnalysisUiState` e do `AnalysisViewModel` (o estado perde `view`/`isRefreshing` e ganha `liveFailedAt`); separar
deixaria um commit intermediário com a tela antiga ligada a um view model que nenhuma tela usa. Viraram uma atividade.

## Pontos de situação

| # | Data | Resultado | Comando e evidência |
| --- | --- | --- | --- |
| A00 | 2026-10-14 | concluída | plano e galeria; sem código |

## Problemas em aberto e riscos

- **Linux (X11)**: a mudança no host da janela (`onCloseRequest` para o laço) não foi aberta no Linux; o PR diz o risco.
- **Custo no JVM**: D6 mediu o SQL fora do app. Se a releitura no app passar de ~300 ms, a cadência sobe para 60 s.
