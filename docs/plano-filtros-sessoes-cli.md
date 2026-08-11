# Plano — Filtros temporais e limpeza da tela de Sessões CLI

> **Status:** implementado em 2026-08-10 sobre `35a71d3`.
> **Data:** 2026-08-10
> **Base:** commit `35a71d3` (`feat(cli-sessions): monitor Claude Code sessions per Anthropic account`), branch `feat/cli-sessions-per-account`.
> **Escopo:** evolução da tela entregue em [`plano-sessoes-cli.md`](plano-sessoes-cli.md).

## Desvios aplicados na execução

1. **`PRAGMA busy_timeout = 5000`** em `LocalCliSessionDataSource` **e** `LocalUsageHistoryDataSource`.
   Os dois apontam para o mesmo `~/.usage-monitor/usage-history.db` por conexões separadas e nenhum
   definia timeout: com a indexação em background, uma escrita do dashboard que caísse no meio de uma
   transação de indexação falharia na hora com `SQLITE_BUSY`.
2. O teste `legacy schema is migrated preserving the hidden flag` foi **adaptado** (cobertura de
   migração de `profile_id`) em vez de removido.
3. O laço de background **não recarrega a lista com o detalhe aberto** — recarregar poderia arrancar
   da tela a sessão que saiu da janela temporal enquanto o usuário a lia.

## Contexto

A tela de Sessões CLI mostra hoje **todas** as sessões indexadas, sem recorte temporal, com um chip "Mostrar ocultas" cujo significado não é óbvio e um botão "Voltar" no header da listagem que não faz nada útil — a listagem já roda numa janela própria com botão de fechar no title bar (`DesktopWindowFrame.kt:250`).

Três mudanças:

1. **Filtro temporal 5h / 7 dias / 30 dias / Total**, recortando os **turnos**: cada sessão é reagregada somando só os turnos dentro da janela, e o header soma esses valores janelados. Uma sessão de 10 dias atrás com atividade nas últimas 5h aparece com os tokens dessas 5h, não com o total histórico. Padrão ao abrir: **5h** (mesma janela de quota da Anthropic, alinhada aos cards do dashboard).
2. **Remover o recurso de ocultar sessões** por completo — chip do header, botão da linha e badge.
3. **Indexação em background** (boot + polling de 10min), para que o histórico no SQLite não dependa do usuário abrir a tela antes da limpeza de transcripts do Claude Code.

### O que já funciona e não precisa mudar

Verificado no código, não é hipótese:

- As sessões **já são persistidas em SQLite** (`~/.usage-monitor/usage-history.db`, tabelas `cli_sessions` + `cli_turns`), criadas em `LocalCliSessionDataSource.initializeSchema`.
- O histórico **já sobrevive à remoção do `.jsonl`**: `syncIndex` itera apenas sobre os arquivos descobertos no disco e nunca apaga linhas de arquivo que sumiu. `readSummary` marca `stale = !File(filePath).isFile` e o detalhe exibe o aviso `staleNotice`. O único `DELETE` (`purgeIndexedFile`) roda apenas quando `startOffset > sizeBytes`, ou seja, arquivo truncado/recriado no mesmo caminho.
- `cli_turns` já guarda `ts` por turno — o recorte temporal é possível sem mudar o formato de ingestão.

O buraco real de retenção é **quando** o índice roda: só ao abrir a janela ou clicar "Atualizar". Item 3 fecha isso.

---

## Camada domain

### Novo: `src/commonMain/kotlin/com/usagemonitor/domain/entity/CliSessionRange.kt`

```kotlin
enum class CliSessionRange(val durationMillis: Long?) {
    LAST_5H(5L * 60 * 60 * 1_000),
    LAST_7D(7L * 24 * 60 * 60 * 1_000),
    LAST_30D(30L * 24 * 60 * 60 * 1_000),
    ALL(null);

    /** Corte em epoch millis, ou `null` quando a janela é aberta. */
    fun cutoffMillis(now: Instant): Long? = durationMillis?.let { now.toEpochMilliseconds() - it }

    companion object { val DEFAULT = LAST_5H }
}
```

### `CliSessionModels.kt` — `CliSessionSummary`

- Remover o campo `hidden` (o recurso morre; a coluna fica no banco, ignorada).
- Manter todo o resto. Os campos passam a significar "dentro da janela" quando o filtro não é `ALL`.

### `domain/repository/CliSessionRepository.kt` e `data/datasource/CliSessionDataSource.kt`

- `getSessions/readSessions(profileId: String?, sinceEpochMillis: Long?)` — `includeHidden` sai, `sinceEpochMillis` entra (`null` = tudo).
- Remover `setHidden` das duas interfaces e de `CliSessionRepositoryImpl`.

### Use cases

- **Excluir** `domain/usecase/SetCliSessionHiddenUseCase.kt`.
- `GetCliSessionsUseCase`: passa a receber `clock: Clock = Clock.System` no construtor e `range: CliSessionRange` no `invoke`; calcula `range.cutoffMillis(clock.now())` e repassa. O `clock` injetável é o que torna o filtro testável sem congelar o relógio do sistema.
- **Novo** `domain/usecase/SyncCliSessionIndexUseCase.kt`: envelopa `repository.syncIndex()`, para a indexação de background não depender de carregar a lista.
- `GetCliSessionDetailUseCase` / `ComputeCliSessionAnalyticsUseCase`: **sem alteração**. O detalhe (contexto vivo, saturação, séries por turno) só faz sentido sobre a sessão inteira; janelar as séries produziria "contexto vivo" falso.

---

## Camada data — `LocalCliSessionDataSource.kt`

Dois caminhos em `readSessions`:

**`sinceEpochMillis == null`** → caminho atual sobre `cli_sessions` (agregados já gravados, uma query). Só remover o predicado de `hidden` e a leitura da coluna.

**`sinceEpochMillis != null`** → query nova agrupando os turnos da janela por `(session_id, model)`:

```sql
SELECT s.session_id, s.file_path, s.profile_id, s.cwd, s.git_branch, t.model,
       COUNT(*) AS turn_count, MIN(t.ts) AS first_ts, MAX(t.ts) AS last_ts,
       SUM(t.input_tokens), SUM(t.output_tokens), SUM(t.cache_read_tokens),
       SUM(t.cache_write_5m_tokens), SUM(t.cache_write_1h_tokens)
FROM cli_turns t
JOIN cli_sessions s ON s.session_id = t.session_id
WHERE t.ts >= ? AND (? = 1 OR s.profile_id = ?)
GROUP BY t.session_id, t.model;
```

O agrupamento **por modelo** é obrigatório: uma sessão que trocou de modelo no meio precisa ser precificada com a tarifa de cada trecho. As linhas são dobradas em Kotlin, por sessão:

- `costMicros += ModelPricingTable.forModel(model)?.costMicros(...)`; quando `null`, `unpricedTurnCount += turnCount` (mesma regra do `recomputeSession` existente).
- `primaryModel` = modelo com maior `turn_count` na janela.
- `firstTs`/`lastTs` = MIN/MAX dentro da janela; ordenar a lista final por `lastTs` desc.
- `stale = !File(filePath).isFile`, igual ao caminho atual.

Precisão: `ModelPricing.costMicros` soma os produtos e divide **uma vez** no fim (`ModelPricing.kt:48-53`), então somar tokens antes de precificar é exato — não pior que somar turno a turno. Pode haver diferença de poucos micros entre `ALL` (custo gravado, somado por turno) e uma janela que cobre a sessão inteira; é ruído na quarta casa decimal.

Schema:
- Adicionar `CREATE INDEX IF NOT EXISTS idx_cli_turns_ts ON cli_turns(ts DESC);` em `initializeSchema` — sem ele o filtro varre a tabela inteira de turnos.
- **Não** derrubar a coluna `hidden` nem os índices que a usam. Remover coluna em SQLite exige recriar a tabela; o custo não se justifica para uma coluna ignorada.
- Remover `UPDATE_HIDDEN_SQL` e `setHidden`.

---

## Camada presentation

### `CliSessionsUiState.kt`

- `Success(sessions, range, profileLabel, indexWarning, detail)` — `showHidden` sai, `range: CliSessionRange` entra. Idem em `Error`.
- Novo derivado `totalTokens: Long get() = sessions.sumOf { it.totalTokens }` ao lado dos já existentes `totalCostMicros` / `isTotalCostComplete`.

### `CliSessionsViewModel.kt`

- Campo `range` (inicial `CliSessionRange.DEFAULT`), novo `setRange(range)` que atribui e chama `refresh()`.
- Remover `showHidden`, `toggleShowHidden`, `setSessionHidden` e o parâmetro `setCliSessionHidden` do construtor.
- Novo parâmetro `syncCliSessionIndex: SyncCliSessionIndexUseCase` e `backgroundIndexIntervalMillis: Long? = null`. Quando não nulo, `init` lança no `viewModelScope` um `while (true) { syncCliSessionIndex(); delay(interval) }`; se a janela estiver aberta (estado `Success`), recarrega a lista depois de cada passada. `null` nos testes desliga o loop. O `onDestroy()` já cancela o escopo — nada novo a desmontar.

### `CliSessionsScreen.kt`

- `CliSessionsScreen` / `CliSessionsContent`: remover o parâmetro `onBack` e `onToggleShowHidden`/`onSetHidden`; adicionar `onSelectRange: (CliSessionRange) -> Unit`. Com o "Voltar" fora, `onBack` não tem mais nenhum uso na listagem — o fechamento é do frame da janela.
- `CliSessionsHeader`: no lugar do chip "Mostrar ocultas" + botão "Voltar", uma linha de quatro `FilterChip` (`5h`, `7d`, `30d`, `Total`) com `selected = state.range == entry`. Manter "Atualizar".
- Header ganha uma terceira coluna de métrica: `formatQuantity(state.totalTokens)` com o rótulo `columnTokens`, entre a contagem de sessões e o custo. Hoje o header só mostra custo — o total de tokens da janela é justamente o número pedido.
- Legenda do header muda com o filtro: em `ALL` segue "custo estimado"; nas janelas, deixar explícito que os números são do período (`estimatedTotalInRange`).
- `CliSessionRow`: remover o `TextButton` de ocultar, o badge `hiddenBadge` e o `accent` condicional (volta a ser sempre `CACHE_READ_COLOR`).
- `CliSessionDetailPane`: **mantém** o "Voltar" — ali ele volta para a lista e faz sentido.
- Estado vazio: `empty` hoje diz "Nenhuma sessão encontrada em ~/.claude/projects", o que é enganoso com filtro de 5h ativo. Adicionar `emptyInRange(range, language)` distinguindo "sem sessões no período" de "nada indexado".

### `CliSessionsFormatting.kt`

Remover `showHidden`, `hide`, `unhide`, `hiddenBadge`. Adicionar `rangeLabel(range, language)` (`5h` / `7 dias` / `30 dias` / `Total`), `estimatedTotalInRange`, `emptyInRange`.

### `Main.kt` (desktopMain)

- Remover o import e o uso de `SetCliSessionHiddenUseCase`; construir `SyncCliSessionIndexUseCase(cliSessionRepository)` e passar ao ViewModel junto com `backgroundIndexIntervalMillis = 10 * 60 * 1_000L`.
- `CliSessionsScreen(...)` perde o argumento `onBack`. O `onCloseRequest` do `Window` e do `DesktopDialogFrame` já fecham a janela.

**Risco conhecido:** `SqliteConnectionManager.useConnection` é `synchronized` e o `syncIndex` inteiro roda dentro de um único bloco. Uma passada de background segura o lock e bloqueia leituras concorrentes até terminar. Passadas incrementais são baratas (só arquivos com `mtime`/`size` alterados), mas a **primeira** indexação, com o índice vazio, percorre todos os transcripts — vai atrasar o primeiro `readSessions` do app. Aceitável e serializado; nenhuma corrupção, já que `useConnection` impede transações entrelaçadas.

---

## Testes

Arquivos existentes a ajustar:

- `src/commonTest/.../presentation/CliSessionsViewModelTest.kt` — remover os testes de `hidden`; adicionar: filtro padrão é 5h ao abrir; `setRange` repassa o corte correto ao repositório (com `Clock` fake); a janela selecionada sobrevive a `refresh()` e à troca de conta; `totalTokens` soma as sessões da janela.
- `src/desktopTest/.../data/LocalCliSessionDataSourceTest.kt` — remover os testes de esconder/reexibir e o de migração que valida `hidden`. Adicionar sobre fixtures `.jsonl` com timestamps controlados: turnos fora da janela não entram nos agregados; sessão sem turno na janela não aparece; sessão com troca de modelo é precificada por trecho; `ALL` continua batendo com o agregado gravado; sessão `stale` (arquivo removido) continua listada dentro da janela.
- `src/desktopTest/.../ui/CliSessionsScreenTest.kt` — remover o teste que clica em "Ocultar". Adicionar: os quatro chips renderizam com o correto selecionado; clicar num chip emite `onSelectRange`; header mostra total de tokens; header **não** tem mais "Voltar"; o "Voltar" do detalhe continua funcionando.

`CliSessionAnalyticsTest.kt`, `ModelPricingTableTest.kt` e `TurnSeriesBinningTest.kt` não são afetados.

---

## Verificação

```bat
gradlew.bat desktopTest --tests "com.usagemonitor.presentation.CliSessionsViewModelTest"
gradlew.bat desktopTest --tests "com.usagemonitor.data.LocalCliSessionDataSourceTest"
gradlew.bat desktopTest --tests "com.usagemonitor.ui.CliSessionsScreenTest"
gradlew.bat test
gradlew.bat run
```

Manual, com o app rodando:

1. Abrir Sessões CLI pelo ícone de terminal num card Anthropic → filtro **5h** já selecionado, lista só com sessões tocadas nas últimas 5h.
2. Header mostra `N sessões · <tokens> · <custo>`, todos referentes à janela.
3. Alternar para 7d / 30d / Total → contagem, tokens e custo crescem monotonicamente entre as janelas.
4. Conferir uma sessão longa: em **Total**, tokens ≫ o valor mostrado em **5h**; o detalhe continua mostrando a sessão inteira nos dois casos.
5. Nenhum "Mostrar ocultas", nenhum "Ocultar" na linha, nenhum "Voltar" no header; o X do title bar fecha a janela; o "Voltar" do detalhe volta para a lista.
6. Fechar a janela, deixar o app aberto ~10min e reabrir: sessões novas do Claude Code aparecem sem clicar "Atualizar" (indexação de background).
7. Retenção: renomear um `.jsonl` já indexado em `~/.claude/projects`, clicar "Atualizar" → a sessão continua na lista e o detalhe exibe o aviso de transcript removido.

---

## Fora de escopo

- Janelar o **detalhe** da sessão (analytics, séries por turno) — continua sobre a sessão inteira.
- Remover fisicamente a coluna `hidden` do schema.
- Filtro por projeto/branch, busca textual, exportação.
- Sessões de outros CLIs (Codex, OpenCode, Kilo).
