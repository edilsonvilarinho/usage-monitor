package com.usagemonitor.domain.entity

import kotlinx.datetime.Instant

/**
 * Um turno do assistente dentro de uma sessão do Claude Code.
 *
 * Os valores são o **delta real daquele turno**, lidos do `message.usage` da linha
 * `assistant` do transcript — não o acumulado da sessão. `messageId` é a chave de
 * deduplicação: a mesma mensagem pode aparecer repetida no `.jsonl`.
 */
data class CliSessionTurn(
    val sessionId: String,
    val seq: Int,
    val messageId: String,
    val ts: Instant,
    val model: String?,
    val isSidechain: Boolean = false,
    val inputTokens: Long = 0L,
    val outputTokens: Long = 0L,
    val cacheReadTokens: Long = 0L,
    val cacheWrite5mTokens: Long = 0L,
    val cacheWrite1hTokens: Long = 0L
) {
    val cacheWriteTokens: Long
        get() = cacheWrite5mTokens + cacheWrite1hTokens

    val totalTokens: Long
        get() = inputTokens + outputTokens + cacheReadTokens + cacheWriteTokens

    val pricing: ModelPricing?
        get() = ModelPricingTable.forModel(model)

    /** Custo do turno em micros de USD, ou `null` quando o modelo não tem preço conhecido. */
    val costMicros: Long?
        get() = pricing?.costMicros(
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            cacheReadTokens = cacheReadTokens,
            cacheWrite5mTokens = cacheWrite5mTokens,
            cacheWrite1hTokens = cacheWrite1hTokens
        )

    /**
     * Economia gerada pelo cache neste turno: o que os tokens lidos do cache
     * teriam custado como input menos o que custaram como cache read.
     */
    val cacheSavingsMicros: Long?
        get() = pricing?.cacheSavingsMicros(cacheReadTokens)
}

/**
 * Raiz de transcripts de uma conta Anthropic.
 *
 * Os transcripts não carregam identidade de conta. A atribuição vem do
 * caminho: cada perfil tem seu próprio config dir do Claude Code
 * (`~/.claude`, `~/.claude-conta2`, ...) e cada um tem seu `projects/`.
 */
data class CliProjectRoot(
    val profileId: String,
    val directoryPath: String
)

/**
 * Agregados de uma sessão, servidos direto do índice SQLite.
 *
 * Quando a leitura usa uma janela temporal, todos os valores são os da janela —
 * não os totais históricos da sessão.
 */
data class CliSessionSummary(
    val sessionId: String,
    val filePath: String,
    /** Perfil Anthropic dono do transcript; `null` em linhas ainda não reatribuídas. */
    val profileId: String? = null,
    val cwd: String? = null,
    val gitBranch: String? = null,
    /**
     * Máquina onde o transcript foi indexado. Os transcripts do Claude Code não
     * carregam identidade de máquina, então o valor é o hostname de quem leu o
     * arquivo — na prática a máquina onde a sessão rodou, já que a leitura é do
     * filesystem local.
     */
    val hostName: String? = null,
    val firstTs: Instant,
    val lastTs: Instant,
    val primaryModel: String? = null,
    val turnCount: Int = 0,
    val inputTokens: Long = 0L,
    val outputTokens: Long = 0L,
    val cacheReadTokens: Long = 0L,
    val cacheWrite5mTokens: Long = 0L,
    val cacheWrite1hTokens: Long = 0L,
    val costMicros: Long = 0L,
    /** Turnos cujo modelo não está na tabela de preços: o custo exibido está incompleto. */
    val unpricedTurnCount: Int = 0,
    /**
     * `cache_read` do último turno da thread principal — o tamanho do contexto que
     * a próxima mensagem vai reenviar.
     *
     * Ao contrário dos demais agregados, este valor é sempre da sessão inteira, mesmo
     * numa leitura com janela temporal: "continuar esta sessão custa X" é uma
     * propriedade do estado atual dela, e recortá-la por janela de quota daria um
     * número sem significado.
     */
    val liveContextTokens: Long = 0L,
    /** Modelo desse último turno principal; define a tarifa da próxima mensagem. */
    val liveContextModel: String? = null,
    /**
     * Tempo de trabalho da sessão dentro da janela lida, pela mesma definição de
     * [activeTimeMillisOf]: só os intervalos entre turnos consecutivos da thread
     * principal menores que [TURN_GAP_CUTOFF_MILLIS].
     *
     * `null` significa **não medido** — leitura que não consultou o tempo ativo,
     * ou servidor de time anterior ao campo. Zero significa medido e sem
     * intervalo, o caso da sessão de um turno só. Colapsar os dois faria a tela
     * afirmar "não trabalhou" onde a resposta certa é "não se sabe".
     */
    val activeMillis: Long? = null,
    /** O `.jsonl` de origem não existe mais (retenção do CLI); só o resumo sobrevive. */
    val stale: Boolean = false
) {
    val cacheWriteTokens: Long
        get() = cacheWrite5mTokens + cacheWrite1hTokens

    val totalTokens: Long
        get() = inputTokens + outputTokens + cacheReadTokens + cacheWriteTokens

    /** `cacheRead / (cacheRead + cacheWrite)`. Zero quando não houve atividade de cache. */
    val cacheHitRate: Double
        get() {
            val denominator = cacheReadTokens + cacheWriteTokens
            if (denominator <= 0L) {
                return 0.0
            }
            return cacheReadTokens.toDouble() / denominator.toDouble()
        }

    /** O custo cobre todos os turnos apenas quando nenhum modelo ficou sem preço. */
    val isCostComplete: Boolean
        get() = unpricedTurnCount == 0

    /**
     * Mesmo veredito que o detalhe apresenta, sem carregar os turnos: a lista só
     * precisa do contexto vivo, que o índice já entrega pronto.
     */
    val contextStatus: CliSessionContextStatus
        get() = computeContextStatus(
            liveContextTokens = liveContextTokens,
            windowModel = primaryModel,
            lastTurnModel = liveContextModel
        )

    /** Último segmento do `cwd`, usado como nome do projeto na lista. */
    val projectName: String?
        get() = projectNameFromCwd(cwd)
}

/**
 * Último segmento de um `cwd`, sem separador final.
 *
 * Extraído de [CliSessionSummary.projectName] porque a agregação por projeto
 * precisa do mesmo nome: duas derivações do mesmo caminho acabariam divergindo
 * em algum caso de borda e a tela mostraria dois rótulos para um projeto só.
 */
fun projectNameFromCwd(cwd: String?): String? {
    return cwd
        ?.trimEnd('/', '\\')
        ?.split('/', '\\')
        ?.lastOrNull()
        ?.takeIf { segment -> segment.isNotBlank() }
}

/** Sessão com os turnos carregados, entrada do cálculo de analytics. */
data class CliSessionDetail(
    val summary: CliSessionSummary,
    val turns: List<CliSessionTurn>
)

/** Resultado de uma passada de indexação incremental sobre `~/.claude/projects`. */
data class CliSessionIndexReport(
    val scannedFiles: Int = 0,
    val updatedFiles: Int = 0,
    /** Linhas que falharam o parse e foram ignoradas — nunca derrubam a sessão. */
    val skippedLines: Int = 0
)

/**
 * Intervalo acima do qual dois turnos deixam de ser a mesma sessão de trabalho.
 *
 * Cinco minutos é o mesmo corte de [ACTIVE_SESSION_WINDOW_MILLIS]: o valor que
 * o app já usa para dizer que alguém está trabalhando numa sessão agora. Um
 * segundo corte diferente para a mesma pergunta daria duas respostas.
 */
const val TURN_GAP_CUTOFF_MILLIS = ACTIVE_SESSION_WINDOW_MILLIS

private const val MILLIS_PER_HOUR = 3_600_000.0

/**
 * Soma os intervalos entre turnos consecutivos, descartando as pausas.
 *
 * Os turnos precisam vir em ordem cronológica; a função não reordena para não
 * esconder um índice fora de ordem, que seria um defeito a corrigir e não a
 * contornar.
 */
/**
 * Tempo de trabalho de uma sessão dentro da janela lida.
 *
 * Par cru, e não uma coluna a mais em [CliUsageGroupRow]: aquela linha é
 * `(sessão, modelo)` e uma sessão que trocou de modelo aparece em várias delas —
 * somar o tempo por linha multiplicaria a hora pelo número de modelos.
 */
data class CliSessionActiveTime(
    val sessionId: String,
    val activeMillis: Long = 0L
)

fun activeTimeMillisOf(turns: List<CliSessionTurn>): Long {
    if (turns.size < 2) {
        return 0L
    }
    var total = 0L
    for (index in 1 until turns.size) {
        val gap = turns[index].ts.toEpochMilliseconds() - turns[index - 1].ts.toEpochMilliseconds()
        if (gap in 1 until TURN_GAP_CUTOFF_MILLIS) {
            total += gap
        }
    }
    return total
}

/** Distribuição do custo da sessão por componente, em micros de USD. */
data class CliSessionCostBreakdown(
    val inputMicros: Long = 0L,
    val outputMicros: Long = 0L,
    val cacheReadMicros: Long = 0L,
    val cacheWriteMicros: Long = 0L
) {
    val totalMicros: Long
        get() = inputMicros + outputMicros + cacheReadMicros + cacheWriteMicros

    fun fractionOf(component: Long): Double {
        val total = totalMicros
        if (total <= 0L) {
            return 0.0
        }
        return component.toDouble() / total.toDouble()
    }
}

/**
 * Métricas derivadas de uma sessão.
 *
 * As séries e as métricas de contexto usam apenas turnos da thread principal
 * (`isSidechain == false`): um subagente tem contexto próprio e misturá-lo
 * distorceria o gráfico. Os valores monetários somam **todos** os turnos,
 * porque o gasto do subagente é real.
 */
data class CliSessionAnalytics(
    val cacheHitRate: Double = 0.0,
    val cacheSavingsMicros: Long = 0L,
    val averageContextPerTurn: Long = 0L,
    val liveContextTokens: Long = 0L,
    val nextInteractionCostMicros: Long = 0L,
    val contextSaturation: Double? = null,
    val costBreakdown: CliSessionCostBreakdown = CliSessionCostBreakdown(),
    val mainTurnCount: Int = 0,
    val sidechainTurnCount: Int = 0,
    val unpricedTurnCount: Int = 0,
    val contextPerTurn: List<Long> = emptyList(),
    val cacheWrite5mPerTurn: List<Long> = emptyList(),
    val cacheWrite1hPerTurn: List<Long> = emptyList(),
    val cumulativeCostMicros: List<Long> = emptyList(),
    val cumulativeSavingsMicros: List<Long> = emptyList(),
    /**
     * Tempo de trabalho da sessão, somando só os intervalos entre turnos
     * consecutivos menores que [TURN_GAP_CUTOFF_MILLIS].
     *
     * Os intervalos maiores são o usuário pensando, almoçando ou dormindo — não
     * tempo de sessão. Sem o corte, "duração" seria só a distância entre o
     * primeiro e o último turno, e uma sessão retomada no dia seguinte
     * "duraria" vinte horas.
     */
    val activeTimeMillis: Long = 0L
) {
    /**
     * Turnos por hora de trabalho. Zero sem tempo ativo medido — uma sessão de
     * um turno só não tem intervalo para medir.
     */
    val turnsPerActiveHour: Double
        get() {
            if (activeTimeMillis <= 0L) {
                return 0.0
            }
            return mainTurnCount.toDouble() / (activeTimeMillis.toDouble() / MILLIS_PER_HOUR)
        }

    /** As três métricas de contexto agrupadas, na mesma forma que a lista usa. */
    val contextStatus: CliSessionContextStatus
        get() = CliSessionContextStatus(
            liveContextTokens = liveContextTokens,
            contextSaturation = contextSaturation,
            nextInteractionCostMicros = nextInteractionCostMicros
        )

    /**
     * Status da sessão. Delega em [CliSessionContextStatus] para que o detalhe e a
     * lista deem sempre o mesmo veredito a partir dos mesmos insumos.
     */
    val health: CliSessionHealth
        get() = contextStatus.health

    val isSaturated: Boolean
        get() = health == CliSessionHealth.SATURATED

    val isCostComplete: Boolean
        get() = unpricedTurnCount == 0
}

/**
 * Recomendação sobre continuar a sessão ou recomeçar com o contexto enxuto.
 *
 * A ordem de declaração é a da severidade — é dela que sai a precedência
 * `SATURATED > ATTENTION > HEALTHY` em [worstHealth] e em [tallyHealth].
 */
enum class CliSessionHealth {
    /** Contexto pequeno para a janela do modelo e mensagem barata. */
    HEALTHY,

    /** Contexto crescendo: compactar já reduz o custo por mensagem. */
    ATTENTION,

    /** Continuar sai caro e a janela está perto do limite. */
    SATURATED
}

/** Quantas sessões de um conjunto pedem atenção, para um cabeçalho resumir. */
data class CliSessionHealthTally(
    val saturated: Int = 0,
    val attention: Int = 0
) {
    val hasWarnings: Boolean
        get() = saturated > 0 || attention > 0
}

/**
 * Sessões com janela de contexto conhecida, na ordem em que estão.
 *
 * Sessão cujo modelo não está em [ModelContextWindowTable] fica de fora de
 * qualquer contagem de saúde: sem a janela não há fração, e
 * [CliSessionContextStatus] se recusa a chutar uma. Contá-la como saudável
 * afirmaria o que não se sabe.
 */
internal fun Iterable<CliSessionSummary>.withKnownWindow(): List<CliSessionSummary> {
    return filter { session -> session.contextStatus.contextSaturation != null }
}

/** Conta saturadas e em atenção, ignorando as de janela desconhecida. */
fun Iterable<CliSessionSummary>.tallyHealth(): CliSessionHealthTally {
    val rated = withKnownWindow()
    return CliSessionHealthTally(
        saturated = rated.count { session -> session.contextStatus.health == CliSessionHealth.SATURATED },
        attention = rated.count { session -> session.contextStatus.health == CliSessionHealth.ATTENTION }
    )
}

/** Pior veredito do conjunto, ou `null` quando nenhuma sessão pôde ser avaliada. */
fun Iterable<CliSessionSummary>.worstHealth(): CliSessionHealth? {
    return withKnownWindow().maxOfOrNull { session -> session.contextStatus.health }
}
