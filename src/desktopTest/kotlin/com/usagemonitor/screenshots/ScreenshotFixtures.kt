package com.usagemonitor.screenshots

import com.usagemonitor.domain.entity.AnthropicQuotaLabels
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageHistoryReport
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.CliSessionTurn
import com.usagemonitor.domain.entity.CliUsageBreakdown
import com.usagemonitor.domain.entity.CliUsageGroupRow
import com.usagemonitor.domain.entity.toUsageBreakdown
import com.usagemonitor.domain.entity.DeepSeekQuotaLabels
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.QuotaRiskSummary
import com.usagemonitor.domain.entity.QuotaSeriesKey
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.domain.entity.TeamMemberPresence
import com.usagemonitor.domain.entity.TeamMemberTrend
import com.usagemonitor.domain.entity.TeamMemberUsage
import com.usagemonitor.domain.entity.TeamTrendPoint
import com.usagemonitor.domain.entity.TeamUsageTrend
import com.usagemonitor.domain.entity.UsageAccountContext
import com.usagemonitor.domain.entity.UsageAccountKey
import com.usagemonitor.domain.entity.UsageForecast
import com.usagemonitor.domain.entity.UsageHistoryPoint
import com.usagemonitor.domain.entity.UsageHistorySeries
import com.usagemonitor.domain.entity.UsageRiskLevel
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.presentation.ui.components.AnthropicProfileUiModel
import com.usagemonitor.presentation.ui.components.AnthropicProfileUiStatus
import com.usagemonitor.presentation.ui.components.TeamConnectionUiState
import com.usagemonitor.presentation.ui.components.TeamConnectionUiStatus
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * Dados sintéticos das capturas do README.
 *
 * Tudo o que aparece nas imagens publicadas nasce aqui. É o único arquivo a
 * auditar antes de commitar um print: nenhum e-mail, organização, máquina,
 * caminho de projeto ou chave real pode entrar neste arquivo.
 *
 * Os instantes derivam de [NOW], um valor fixo. Usar o relógio faria cada
 * execução gerar PNGs diferentes e encher o diff de ruído.
 */
internal object ScreenshotFixtures {

    /** Âncora temporal de todas as capturas. */
    val NOW: Instant = Instant.parse("2026-08-12T14:00:00Z")

    /** Sentinela do mapper para cota sem reinício conhecido. */
    private val UNKNOWN_RESET_AT: Instant = Instant.parse("2100-01-01T00:00:00Z")

    /** Janela de contexto de 1M: a única forma de a saturação ser conhecida. */
    const val OPUS = "claude-opus-5"
    const val SONNET = "claude-sonnet-5"

    private const val PRIMARY_PROFILE_ID = "default"
    private const val SECONDARY_PROFILE_ID = "sandbox"

    private const val PRIMARY_EMAIL = "dev@example.com"
    private const val SECONDARY_EMAIL = "qa@example.com"

    // --- Dashboard -----------------------------------------------------------

    private val primaryAccount = UsageAccountContext(
        key = UsageAccountKey(source = ApiSource.ANTHROPIC, providerAccountId = "account-primary"),
        email = PRIMARY_EMAIL,
        workspaceName = "Example Org"
    )

    private val secondaryAccount = UsageAccountContext(
        key = UsageAccountKey(source = ApiSource.ANTHROPIC, providerAccountId = "account-sandbox"),
        email = SECONDARY_EMAIL,
        workspaceName = "Example Org (Sandbox)"
    )

    private val codexAccount = UsageAccountContext(
        key = UsageAccountKey(source = ApiSource.CODEX, providerAccountId = "account-codex"),
        email = PRIMARY_EMAIL
    )

    private fun fiveHourQuota(usedPercent: Long, endsAt: Instant) = QuotaInfo(
        label = AnthropicQuotaLabels.FIVE_HOUR,
        used = usedPercent,
        total = 100L,
        periodEndAt = endsAt,
        periodType = PeriodType.INTERVAL,
        unit = UsageUnit.PERCENTAGE
    )

    private fun sevenDayQuota(usedPercent: Long, endsAt: Instant) = QuotaInfo(
        label = AnthropicQuotaLabels.SEVEN_DAY,
        used = usedPercent,
        total = 100L,
        periodEndAt = endsAt,
        periodType = PeriodType.WEEKLY,
        unit = UsageUnit.PERCENTAGE
    )

    /**
     * Cota dos créditos de uso, na mesma forma que `AnthropicMapper` produz:
     * percentual em `used`/`total` e o dinheiro em unidades menores da moeda
     * em `rawUsed`/`rawTotal`.
     */
    private fun extraCreditsQuota(usedMinor: Long, limitMinor: Long) = QuotaInfo(
        label = AnthropicQuotaLabels.EXTRA_CREDITS,
        used = usedMinor * 100L / limitMinor,
        total = 100L,
        periodEndAt = UNKNOWN_RESET_AT,
        hasKnownResetAt = false,
        periodType = PeriodType.REPORTED,
        unit = UsageUnit.PERCENTAGE,
        rawUsed = usedMinor,
        rawTotal = limitMinor,
        currencyCode = "USD"
    )

    val primaryAnthropicTarget = UsageTargetKey(ApiSource.ANTHROPIC, PRIMARY_PROFILE_ID)
    private val secondaryAnthropicTarget = UsageTargetKey(ApiSource.ANTHROPIC, SECONDARY_PROFILE_ID)

    val dashboardStats: List<ApiUsageStats> = listOf(
        ApiUsageStats(
            source = ApiSource.ANTHROPIC,
            targetKey = primaryAnthropicTarget,
            apiName = "Anthropic",
            profileLabel = "Padrão",
            accountContext = primaryAccount,
            quotas = listOf(
                fiveHourQuota(usedPercent = 68L, endsAt = NOW.plusHours(2)),
                sevenDayQuota(usedPercent = 41L, endsAt = NOW.plusHours(79)),
                extraCreditsQuota(usedMinor = 19_000L, limitMinor = 50_000L)
            )
        ),
        ApiUsageStats(
            source = ApiSource.ANTHROPIC,
            targetKey = secondaryAnthropicTarget,
            apiName = "Anthropic",
            profileLabel = "Sandbox",
            accountContext = secondaryAccount,
            quotas = listOf(
                fiveHourQuota(usedPercent = 12L, endsAt = NOW.plusHours(4)),
                sevenDayQuota(usedPercent = 7L, endsAt = NOW.plusHours(103))
            )
        ),
        ApiUsageStats(
            source = ApiSource.CODEX,
            apiName = "Codex",
            accountContext = codexAccount,
            quotas = listOf(
                QuotaInfo(
                    label = "Codex 5h",
                    used = 23L,
                    total = 100L,
                    periodEndAt = NOW.plusHours(3),
                    periodType = PeriodType.INTERVAL,
                    unit = UsageUnit.PERCENTAGE
                ),
                QuotaInfo(
                    label = "Codex 7d",
                    used = 11L,
                    total = 100L,
                    periodEndAt = NOW.plusHours(103),
                    periodType = PeriodType.WEEKLY,
                    unit = UsageUnit.PERCENTAGE
                )
            )
        ),
        ApiUsageStats(
            source = ApiSource.DEEPSEEK,
            apiName = "DeepSeek",
            quotas = listOf(
                QuotaInfo(
                    label = DeepSeekQuotaLabels.BALANCE,
                    used = 0L,
                    total = 1_284L,
                    periodEndAt = UNKNOWN_RESET_AT,
                    hasKnownResetAt = false,
                    periodType = PeriodType.REPORTED,
                    unit = UsageUnit.CURRENCY_USD
                )
            )
        )
    )

    /** Semáforo de risco na janela de 5h da conta principal. */
    val dashboardRiskSummaries: Map<UsageTargetKey, Map<QuotaSeriesKey, QuotaRiskSummary>> = mapOf(
        primaryAnthropicTarget to mapOf(
            QuotaSeriesKey(AnthropicQuotaLabels.FIVE_HOUR, PeriodType.INTERVAL) to QuotaRiskSummary(
                level = UsageRiskLevel.AT_RISK,
                estimatedExhaustionAt = NOW.plusHours(1)
            )
        )
    )

    // --- Histórico -----------------------------------------------------------

    /**
     * Série de 24h com um reinício de janela no meio: é o que o gráfico de
     * consumo precisa mostrar para o recurso ficar legível.
     */
    private fun historyPoints(): List<UsageHistoryPoint> {
        val samples = listOf(
            4L, 11L, 19L, 26L, 34L, 41L, 47L, 52L, 58L, 63L, 69L, 74L,
            6L, 12L, 17L, 23L, 29L, 36L, 44L, 51L, 57L, 62L, 66L, 68L
        )
        val firstPeriodEnd = NOW.minusHours(12)
        val secondPeriodEnd = NOW.plusHours(2)

        return samples.mapIndexed { index, used ->
            UsageHistoryPoint(
                capturedAt = NOW.minusHours(23 - index),
                used = used,
                total = 100L,
                rawUsed = 0L,
                rawTotal = 0L,
                periodEndAt = if (index < 12) firstPeriodEnd else secondPeriodEnd
            )
        }
    }

    val historyReport = ApiUsageHistoryReport(
        source = ApiSource.ANTHROPIC,
        range = HistoryRange.LAST_24_HOURS,
        lastUpdatedAt = NOW,
        accountContext = primaryAccount,
        series = listOf(
            UsageHistorySeries(
                quotaLabel = AnthropicQuotaLabels.FIVE_HOUR,
                periodType = PeriodType.INTERVAL,
                unit = UsageUnit.PERCENTAGE,
                points = historyPoints(),
                currentDisplayUsed = 68L,
                currentDisplayTotal = 100L,
                deltaDisplayUsed = 62L,
                averageDisplayConsumptionPerHour = 5.6,
                currentPeriodEndAt = NOW.plusHours(2),
                forecast = UsageForecast.EstimatedExhaustionAt(NOW.plusHours(6)),
                riskSummary = QuotaRiskSummary(
                    level = UsageRiskLevel.AT_RISK,
                    estimatedExhaustionAt = NOW.plusHours(6)
                )
            ),
            UsageHistorySeries(
                quotaLabel = AnthropicQuotaLabels.SEVEN_DAY,
                periodType = PeriodType.WEEKLY,
                unit = UsageUnit.PERCENTAGE,
                points = historyPoints().mapIndexed { index, point ->
                    point.copy(
                        used = 18L + index,
                        periodEndAt = NOW.plusHours(79)
                    )
                },
                currentDisplayUsed = 41L,
                currentDisplayTotal = 100L,
                deltaDisplayUsed = 23L,
                averageDisplayConsumptionPerHour = 1.0,
                currentPeriodEndAt = NOW.plusHours(79),
                forecast = UsageForecast.ResetsBeforeExhaustion,
                riskSummary = QuotaRiskSummary(
                    level = UsageRiskLevel.ON_TRACK,
                    estimatedExhaustionAt = null
                )
            )
        )
    )

    val historyAccounts: List<UsageAccountContext> = listOf(primaryAccount, secondaryAccount)

    // --- Sessões CLI ---------------------------------------------------------

    private fun session(
        id: String,
        cwd: String,
        branch: String,
        host: String,
        model: String,
        turnCount: Int,
        inputTokens: Long,
        outputTokens: Long,
        cacheReadTokens: Long,
        cacheWriteTokens: Long,
        costMicros: Long,
        liveContextTokens: Long,
        startedHoursAgo: Int,
        lastMinutesAgo: Int,
        activeMillis: Long
    ) = CliSessionSummary(
        sessionId = id,
        filePath = "$cwd/.claude/$id.jsonl",
        profileId = PRIMARY_PROFILE_ID,
        cwd = cwd,
        gitBranch = branch,
        hostName = host,
        firstTs = NOW.minusHours(startedHoursAgo),
        lastTs = NOW.minusMinutes(lastMinutesAgo),
        primaryModel = model,
        turnCount = turnCount,
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        cacheReadTokens = cacheReadTokens,
        cacheWrite5mTokens = cacheWriteTokens,
        costMicros = costMicros,
        liveContextTokens = liveContextTokens,
        liveContextModel = model,
        activeMillis = activeMillis
    )

    /**
     * Saturada: 720K de contexto vivo numa janela de 1M.
     *
     * O custo bate com a soma que `ComputeCliSessionAnalyticsUseCase` deriva dos
     * turnos: o detalhe mostra os dois lado a lado, e valores diferentes na mesma
     * imagem passariam por erro da app.
     */
    val saturatedSession = session(
        id = "7c4a1f92d8b3",
        cwd = "/home/dev/api-gateway",
        branch = "feat/checkout",
        host = "DESKTOP-A1",
        model = OPUS,
        turnCount = 48,
        inputTokens = 31_400L,
        outputTokens = 62_800L,
        cacheReadTokens = 4_120_000L,
        cacheWriteTokens = 186_000L,
        costMicros = 5_479_200L,
        liveContextTokens = 720_000L,
        startedHoursAgo = 4,
        lastMinutesAgo = 2,
        activeMillis = 8_040_000L
    )

    /** Em atenção: 460K numa janela de 1M. */
    private val attentionSession = session(
        id = "b81e35c07af6",
        cwd = "/home/dev/checkout-web",
        branch = "main",
        host = "DESKTOP-A1",
        model = OPUS,
        turnCount = 26,
        inputTokens = 18_200L,
        outputTokens = 34_500L,
        cacheReadTokens = 1_780_000L,
        cacheWriteTokens = 94_000L,
        costMicros = 3_610_000L,
        liveContextTokens = 460_000L,
        startedHoursAgo = 3,
        lastMinutesAgo = 27,
        activeMillis = 3_900_000L
    )

    private val healthySession = session(
        id = "2f9d64ba1c50",
        cwd = "/home/dev/api-gateway",
        branch = "main",
        host = "DESKTOP-A1",
        model = SONNET,
        turnCount = 9,
        inputTokens = 6_400L,
        outputTokens = 11_900L,
        cacheReadTokens = 214_000L,
        cacheWriteTokens = 31_000L,
        costMicros = 412_000L,
        liveContextTokens = 48_000L,
        startedHoursAgo = 2,
        lastMinutesAgo = 96,
        activeMillis = 2_280_000L
    )

    val cliSessions: List<CliSessionSummary> = listOf(
        saturatedSession,
        attentionSession,
        healthySession
    )

    /**
     * Tendência de sete dias para três integrantes.
     *
     * Sete e não trinta: é o recorte que cabe na captura sem rolagem horizontal,
     * e é o mesmo que o protótipo desenha. O gráfico com trinta dias continua
     * coberto pelo piso de largura de barra, que é código e não captura.
     */
    val teamTrend: TeamUsageTrend = run {
        val days = (0..6).map { offset ->
            LocalDate(2026, 8, 6).plus(offset, DateTimeUnit.DAY)
        }
        // Micros por dia, por integrante: o primeiro domina, o segundo acompanha
        // e o terceiro quase não aparece — é o caso que a escala única existe
        // para mostrar.
        val series = listOf(
            Triple("device-a1", "dev-01", listOf(4_800L, 7_400L, 3_200L, 9_000L, 6_200L, 10_200L, 6_800L)),
            Triple("device-b2", "dev-02", listOf(1_600L, 2_800L, 5_400L, 4_000L, 2_200L, 4_600L, 3_400L)),
            Triple("device-c3", "dev-03", listOf(200L, 200L, 800L, 200L, 1_400L, 200L, 600L))
        )
        TeamUsageTrend(
            days = days,
            members = series.map { (deviceId, alias, costs) ->
                TeamMemberTrend(
                    deviceId = deviceId,
                    alias = alias,
                    points = days.mapIndexed { index, date ->
                        TeamTrendPoint(
                            date = date,
                            costMicros = costs[index] * 1_000L,
                            totalTokens = costs[index] * 400L,
                            turnCount = (costs[index] / 200L).toInt()
                        )
                    }
                )
            }
        )
    }

    /**
     * Resumo por eixo com um balde por projeto e hora medida.
     *
     * Sai do mesmo dobrador que a tela usa (`toUsageBreakdown`), e não de baldes
     * escritos à mão: uma captura montada com números inventados não prova que a
     * fatia, o total e a ordem batem.
     */
    val cliBreakdown: CliUsageBreakdown = listOf(
        CliUsageGroupRow(
            sessionId = "7c4a1f92",
            cwd = "/workspace/api-gateway",
            gitBranch = "feat/checkout",
            model = "claude-opus-5",
            turnCount = 57,
            inputTokens = 31_400L,
            outputTokens = 62_800L,
            cacheReadTokens = 4_120_000L,
            cacheWrite5mTokens = 186_000L
        ),
        CliUsageGroupRow(
            sessionId = "b81e35c0",
            cwd = "/workspace/checkout-web",
            gitBranch = "main",
            model = "claude-opus-5",
            turnCount = 26,
            inputTokens = 12_100L,
            outputTokens = 24_500L,
            cacheReadTokens = 1_780_000L,
            cacheWrite5mTokens = 94_000L
        ),
        CliUsageGroupRow(
            sessionId = "3ac09d41",
            cwd = "/workspace/usage-monitor",
            gitBranch = "fix/visual",
            model = "claude-sonnet-5",
            turnCount = 18,
            inputTokens = 8_400L,
            outputTokens = 15_200L,
            cacheReadTokens = 960_000L,
            cacheWrite5mTokens = 52_000L
        )
    ).toUsageBreakdown(
        activeTimes = mapOf(
            "7c4a1f92" to 2 * 3_600_000L + 52 * 60_000L,
            "b81e35c0" to 3_600_000L + 5 * 60_000L,
            "3ac09d41" to 41 * 60_000L
        )
    )

    /**
     * Turnos do detalhe: o contexto cresce turno a turno, que é exatamente o
     * que o gráfico "Contexto por turno" existe para mostrar.
     */
    val saturatedSessionDetail: CliSessionDetail = CliSessionDetail(
        summary = saturatedSession,
        turns = buildList {
            val contextGrowth = listOf(
                12_000L, 38_000L, 71_000L, 96_000L, 134_000L, 178_000L,
                221_000L, 268_000L, 310_000L, 366_000L, 402_000L, 458_000L,
                505_000L, 548_000L, 601_000L, 654_000L, 688_000L, 720_000L
            )
            contextGrowth.forEachIndexed { index, cacheRead ->
                add(
                    CliSessionTurn(
                        sessionId = saturatedSession.sessionId,
                        seq = index,
                        messageId = "msg-$index",
                        ts = NOW.minusMinutes((contextGrowth.size - index) * 13),
                        model = OPUS,
                        inputTokens = 620L + index * 40L,
                        outputTokens = 1_180L + index * 95L,
                        cacheReadTokens = cacheRead,
                        cacheWrite5mTokens = 9_400L + index * 320L
                    )
                )
            }
        }
    )

    // --- Sessões do time -----------------------------------------------------

    private fun teamSession(
        id: String,
        cwd: String,
        branch: String,
        host: String,
        model: String,
        turnCount: Int,
        tokens: Long,
        costMicros: Long,
        liveContextTokens: Long,
        lastMinutesAgo: Int,
        activeMillis: Long
    ) = CliSessionSummary(
        sessionId = id,
        filePath = "",
        cwd = cwd,
        gitBranch = branch,
        hostName = host,
        firstTs = NOW.minusHours(4),
        lastTs = NOW.minusMinutes(lastMinutesAgo),
        primaryModel = model,
        turnCount = turnCount,
        inputTokens = tokens / 40L,
        outputTokens = tokens / 25L,
        cacheReadTokens = tokens - tokens / 40L - tokens / 25L - tokens / 20L,
        cacheWrite5mTokens = tokens / 20L,
        costMicros = costMicros,
        liveContextTokens = liveContextTokens,
        liveContextModel = model,
        activeMillis = activeMillis
    )

    val teamMembers: List<TeamMemberUsage> = listOf(
        TeamMemberUsage(
            deviceId = "device-a1",
            alias = "dev-01",
            hostName = "DESKTOP-A1",
            lastSeenAt = NOW.minusMinutes(2),
            sessions = listOf(
                teamSession(
                    id = "7c4a1f92d8b3",
                    cwd = "/home/dev/api-gateway",
                    branch = "feat/checkout",
                    host = "DESKTOP-A1",
                    model = OPUS,
                    turnCount = 48,
                    tokens = 4_400_000L,
                    // Mesma sessão de `saturatedSession`: o custo tem de ser o mesmo
                    // nas duas capturas.
                    costMicros = 5_479_200L,
                    liveContextTokens = 720_000L,
                    lastMinutesAgo = 2,
                    activeMillis = 8_040_000L
                ),
                teamSession(
                    id = "b81e35c07af6",
                    cwd = "/home/dev/checkout-web",
                    branch = "main",
                    host = "DESKTOP-A1",
                    model = OPUS,
                    turnCount = 26,
                    tokens = 1_920_000L,
                    costMicros = 3_610_000L,
                    liveContextTokens = 460_000L,
                    lastMinutesAgo = 27,
                    activeMillis = 3_900_000L
                )
            )
        ),
        TeamMemberUsage(
            deviceId = "device-b2",
            alias = "dev-02",
            hostName = "NOTE-B2",
            lastSeenAt = NOW.minusMinutes(11),
            sessions = listOf(
                teamSession(
                    id = "e05fa7431dc9",
                    cwd = "/home/dev/checkout-web",
                    branch = "main",
                    host = "NOTE-B2",
                    model = OPUS,
                    turnCount = 31,
                    tokens = 2_650_000L,
                    costMicros = 5_120_000L,
                    liveContextTokens = 480_000L,
                    lastMinutesAgo = 11,
                    activeMillis = 5_220_000L
                )
            )
        ),
        TeamMemberUsage(
            deviceId = "device-c3",
            alias = "dev-03",
            hostName = "NOTE-C3",
            lastSeenAt = NOW.minusHours(9),
            sessions = emptyList()
        )
    )

    /** Máquina que abriu o modal: a própria linha não ganha botão de remover. */
    const val LOCAL_DEVICE_ID = "device-a1"

    /**
     * Presença montada à mão, e não por `toTeamPresence(NOW)`.
     *
     * A janela de online são 90 segundos, e os carimbos de [teamMembers] são muito
     * mais velhos que isso — passados pelo classificador, todo mundo sairia
     * desconectado e a captura não mostraria nada do que interessa. Aqui cada um
     * dos três estados aparece uma vez: trabalhando, conectado e parado,
     * desconectado.
     */
    val teamPresence: List<TeamMemberPresence> = listOf(
        TeamMemberPresence(
            member = teamMembers[0],
            isOnline = true,
            isWorkingNow = true,
            activeSessionCount = 2
        ),
        TeamMemberPresence(
            member = teamMembers[1],
            isOnline = true,
            isWorkingNow = false,
            activeSessionCount = 0
        ),
        TeamMemberPresence(
            member = teamMembers[2],
            isOnline = false,
            isWorkingNow = false,
            activeSessionCount = 0
        )
    )

    /**
     * A mesma presença, agora na visão global do administrador.
     *
     * É ela que exercita a faixa de conta e a coluna de ação: o `canManage` da
     * captura de uma conta só é inerte, porque `TeamPresenceContent` só libera os
     * botões destrutivos quando o estado é global.
     */
    val teamPresenceAccounts: List<TeamMemberPresence> = listOf(
        teamPresence[0].withAccount("account-primary", "ana@example.com"),
        teamPresence[1].withAccount("account-primary", "ana@example.com"),
        teamPresence[2].withAccount("account-sandbox", "bruno@example.com")
    )

    // --- Configurações -------------------------------------------------------

    val anthropicProfiles: List<AnthropicProfileUiModel> = listOf(
        AnthropicProfileUiModel(
            id = PRIMARY_PROFILE_ID,
            label = "Padrão",
            path = "~/.claude",
            enabled = true,
            removable = false,
            identityLabel = "$PRIMARY_EMAIL — Example Org",
            status = AnthropicProfileUiStatus.READY
        ),
        AnthropicProfileUiModel(
            id = SECONDARY_PROFILE_ID,
            label = "Sandbox",
            path = "~/.claude-sandbox",
            enabled = true,
            removable = true,
            identityLabel = "$SECONDARY_EMAIL — Example Org (Sandbox)",
            status = AnthropicProfileUiStatus.READY
        )
    )

    val teamSettings = TeamIntegrationSettings(
        enabled = true,
        serverUrl = "https://usage.example.com",
        apiKey = "um_sk_exemplo_nao_use",
        alias = "dev-01",
        deviceId = LOCAL_DEVICE_ID,
        participatingProfileIds = setOf(PRIMARY_PROFILE_ID)
    )

    val teamConnection = TeamConnectionUiState(
        status = TeamConnectionUiStatus.OK,
        message = "Conexão confirmada."
    )

    val enabledApis: Set<ApiSource> = setOf(
        ApiSource.ANTHROPIC,
        ApiSource.CODEX,
        ApiSource.DEEPSEEK
    )
}

private fun Instant.plusHours(hours: Int): Instant =
    Instant.fromEpochMilliseconds(toEpochMilliseconds() + hours * 3_600_000L)

private fun Instant.minusHours(hours: Int): Instant =
    Instant.fromEpochMilliseconds(toEpochMilliseconds() - hours * 3_600_000L)

private fun Instant.minusMinutes(minutes: Int): Instant =
    Instant.fromEpochMilliseconds(toEpochMilliseconds() - minutes * 60_000L)

private fun TeamMemberPresence.withAccount(
    accountKey: String,
    accountLabel: String
): TeamMemberPresence {
    return copy(member = member.copy(accountKey = accountKey, accountLabel = accountLabel))
}
