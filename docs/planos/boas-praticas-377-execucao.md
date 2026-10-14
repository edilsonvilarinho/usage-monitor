# Boas práticas — replicar o padrão do fix #377 e corrigir desvios

## Objetivo

O commit `8fd848b4` (fix #377, contagens observadas no HUD) é a referência: classificação de fonte num
predicado puro do domain, campo novo com default em vez de valor novo de enum, geometria e composable em
arquivos próprios com teste de concordância, e documentação no mesmo commit. Este plano replica esse padrão
onde o código desvia dele e corrige os desvios das regras do `CLAUDE.md` encontrados numa auditoria do código
de produção (`src/commonMain`, `src/desktopMain`).

Defeitos do próprio #377 que não se replicam: predicado com `||` (fonte nova não obriga decisão), texto PT/EN
inline que refaz helpers existentes, "Últimos 7 dias" no HUD contra "Últimos 7d" no card, soma saturante
inline duplicando a de `GeminiRepositoryImpl`.

## Achados da auditoria (evidência)

| # | Achado | Evidência |
|---|---|---|
| 1 | Selo "Chave configurada/necessária" omite OpenRouter | `ApiSelector.kt:145` checa {MINIMAX, DEEPSEEK, OPENCODE_GO}; `API_KEY_DEPENDENT_SOURCES` inclui OPENROUTER — o mesmo conjunto tem seis donos |
| 2 | 15 tipos com credencial declarados `data class` sem `toString` redigido | `AnthropicSession`, `CodexSession`, `TeamIntegrationSettings`, `ProxySettings`, `ConfirmedIdentity`, DTOs de Telegram, acesso web, time, proxy e OAuth |
| 3 | Texto de UI no domain | `ApiSourcePresentation.kt` (`statusBadgeLabel`, `statusSupportingText`, ramo morto do KILO); `TelegramBot.kt:309` |
| 4 | 737 comparações `language == AppLanguage.PT` em 106 arquivos, sem catálogo comum | Pares duplicados em até quatro arquivos; dois `pick` privados iguais; nove traduções inconsistentes |
| 5 | Ordem não total em lista publicada | `TeamUsageMapper.kt:175` e `LocalCodexCliSessionDataSource.kt:116` só por `lastTs`; comparador de `TeamUsageViewModelSupport.kt:61` sem `deviceId` |
| 6 | Conjuntos de fonte repetidos | {ANTHROPIC, CODEX} literal em três checks; "DEEPSEEK ∪ observadas" em dois sites do histórico; `sourceLabelFromKey` copia `displayName()` |
| 7 | Higiene | 5 `!!`; 18 scope functions aninhadas |
| 8 | Primitivas reimplementadas fora de `components/` | `Surface` raiz em seis telas; faixas `.background(surfaceVariant)`; caixas com borda à mão |
| 9 | Tamanho | `HudWindowHost` com 300 linhas (no teto); `ApiUsageCardFormatting` 782; lista 750–800 do `CLAUDE.md` desatualizada |
| 10 | Cobertura e docs | Sem teste: `ComparisonViewModel`, `ComparisonFormatting`, `DashboardScreenMessages`, `TeamPresenceFormatting`, `TeamUsageViewModelSupport`; 16 primitivas sem `.prompt.md` |

## Hipóteses a verificar antes de alterar

- **H1** O bot do Telegram calcula "visto há X" com o relógio local contra `lastSeenAt` do servidor, sem o
  offset medido (`TelegramBotHandlers.kt:198` → `TelegramBotChatMessages.kt:68`).
- **H2** `HelpMediaPlayer.kt` roda o GIF em laço sem consultar `AppMotionPolicy.continuous`.
- **H3** {ANTHROPIC, CODEX} nos três checks literais quer dizer "tem índice de sessões CLI", não
  `requiresUsageAccount` — dois fatos que hoje coincidem não se fundem.

## Fora do escopo

- Migração total das comparações de idioma: entra uma guarda de catraca e a migração acontece quando o arquivo
  for tocado.
- Divisão de `DashboardViewModel`, `LocalCliSessionDataSource`, `TeamUsageViewModel` e `TeamUsageScreen`.
- Linhas "pendente" de planos antigos, que dependem de validação manual do usuário.

## Ponto de situação

Cada linha é escrita no commit da atividade que descreve, com o comando executado e o resultado.

| Atividade | Alteração | Comando e resultado |
|---|---|---|
| A0 | Este plano | Só documentação; `git diff --check` limpo |
| A1 | `ApiSource.requiresLocalApiKey()` (domain, `when` exaustivo) vira dono único: `API_KEY_DEPENDENT_SOURCES`, `SettingsApisTab`, selo de `ApiCheckboxRow` e `LocalApiKeyDataSource` derivam dele. Corrige o selo ausente na OpenRouter (o protótipo, linha 2317, já o desenhava) | `gradlew.bat desktopTest --tests ApiSourceClassificationTest --tests ComponentTest --tests LocalApiKeyDataSourceTest --tests ApiKeyDialogTest --tests "com.usagemonitor.architecture.*"`: 100 testes, 0 falhas |
| A2 | `isObservedActivitySource()` muda para `ApiSourceClassification.kt` como `when` exaustivo, com teste de equivalência contra o ramo `OBSERVED_ACTIVITY` de `quotaThresholdGap()`. H3 confirmada: os três checks literais eram dois fatos — `ModelComparison` e o heatmap do Telegram perguntam "tem índice de sessões CLI" (`hasCliSessionIndex()`, novo); `SessionAnalysisReader` pergunta "a análise cobre" (`ANALYSIS_SOURCES`, existente). "DeepSeek ∪ observadas" do histórico vira `buildsOwnHistoryCards()` | `gradlew.bat desktopTest --tests "com.usagemonitor.domain.*" --tests "com.usagemonitor.presentation.*" --tests "com.usagemonitor.architecture.*"`: 1.180 testes, 0 falhas |
| A3 | `statusBadgeLabel`/`statusSupportingText` saem do domain para `presentation/ui/components/ApiSourceLabels.kt` como `when` exaustivo. `displayName()` fica no domain sem idioma (nome de marca, igual nos dois idiomas; o ramo `KILO` era morto); `apiLabel` e `sourceLabelFromKey` deixam de copiar a tabela. **Desvio do plano:** a guarda "domain sem `AppLanguage.PT`" não entra — `TelegramBot.kt` mantém a tabela do teclado nos dois idiomas porque o mesmo dado faz o parse do toque (`parseKeyboardLabel`); separar seria dois donos do mesmo fato. O crescimento fica coberto pela catraca de B3 | `gradlew.bat desktopTest --tests "com.usagemonitor.ui.*" --tests "com.usagemonitor.presentation.*" --tests "com.usagemonitor.domain.*" --tests "com.usagemonitor.architecture.*"`: 1.721 testes, 0 falhas |
