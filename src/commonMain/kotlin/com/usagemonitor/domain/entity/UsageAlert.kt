package com.usagemonitor.domain.entity

import kotlinx.datetime.Instant

/**
 * Preferências de alerta, persistidas fora do domain.
 *
 * [quotaPercents] são limiares em pontos percentuais. Vazio desliga o alerta de
 * quota sem precisar de um segundo interruptor.
 */
data class UsageAlertSettings(
    val quotaAlertsEnabled: Boolean = true,
    val quotaPercents: List<Int> = DEFAULT_QUOTA_ALERT_PERCENTS,
    val sessionAlertsEnabled: Boolean = true,
    /** Aviso de sessão cujo último pedido ficou sem resposta; ver [StalledCliSession]. */
    val stalledSessionAlertsEnabled: Boolean = true,
    val stallThresholdMillis: Long = DEFAULT_STALL_THRESHOLD_MILLIS,
    val quietHours: QuietHours? = null
) {
    /**
     * Limiar saneado, com [MIN_STALL_THRESHOLD_MILLIS] como piso.
     *
     * Mora aqui pelo mesmo motivo de [effectiveQuotaPercents]: o valor vem de
     * armazenamento em claro, e dois leitores saneando por conta própria acabariam
     * divergindo. `detectStalledSessions` não sabe nada disto — ela aplica o que
     * recebe.
     */
    val effectiveStallThresholdMillis: Long
        get() = stallThresholdMillis.coerceAtLeast(MIN_STALL_THRESHOLD_MILLIS)

    /**
     * Limiares normalizados: dentro de 1..100, sem repetidos e em ordem.
     *
     * A lista vem de texto digitado nas Configurações, então a normalização mora
     * aqui e não em quem lê o valor — dois leitores normalizando por conta
     * própria acabariam divergindo.
     */
    val effectiveQuotaPercents: List<Int>
        get() = quotaPercents
            .filter { percent -> percent in 1..100 }
            .distinct()
            .sorted()

    companion object {
        val DEFAULT = UsageAlertSettings()
    }
}

/** Limiares padrão: um aviso antecipado, um urgente e o estouro. */
val DEFAULT_QUOTA_ALERT_PERCENTS: List<Int> = listOf(75, 90, 100)

/**
 * Faixa horária em que nenhum alerta é emitido, no fuso local da apresentação.
 *
 * [startHour] maior que [endHour] atravessa a meia-noite (ex.: 22 → 8), que é o
 * caso normal de "não me acorde".
 */
data class QuietHours(
    val startHour: Int,
    val endHour: Int
) {
    init {
        require(startHour in 0..23) { "A hora inicial do silêncio deve estar entre 0 e 23." }
        require(endHour in 0..23) { "A hora final do silêncio deve estar entre 0 e 23." }
    }

    fun contains(hour: Int): Boolean {
        if (startHour == endHour) {
            // Faixa degenerada: silêncio de uma hora, não o dia inteiro. Silenciar
            // 24h é o que `quotaAlertsEnabled = false` faz, e com um nome honesto.
            return hour == startHour
        }
        if (startHour < endHour) {
            return hour >= startHour && hour < endHour
        }
        return hour >= startHour || hour < endHour
    }
}

/**
 * Um alerta a ser entregue fora da janela do app.
 *
 * Carrega apenas o fato — a frase é montada na borda da UI, como em
 * [com.usagemonitor.presentation.viewmodel.DashboardToast].
 */
sealed interface UsageAlert {

    /** Uma cota cruzou um limiar configurado dentro da janela corrente. */
    data class QuotaThreshold(
        val target: UsageTargetKey,
        val targetLabel: String,
        val quotaLabel: String,
        val thresholdPercent: Int,
        /** Percentual real no momento do disparo; pode passar do limiar cruzado. */
        val actualPercent: Int,
        val periodEndAt: Instant,
        val hasKnownResetAt: Boolean
    ) : UsageAlert

    /** Uma sessão CLI com interação recente saturou a janela de contexto. */
    data class SessionSaturated(
        val sessionId: String,
        val projectName: String?
    ) : UsageAlert

    /**
     * O último pedido de uma sessão CLI não recebeu resposta dentro do limiar.
     *
     * Carrega ausência de resposta, e não "processo travado": a evidência está no
     * transcript, e o app não olha o sistema operacional. A frase montada na borda
     * da UI mantém a mesma reserva.
     */
    data class SessionStalled(
        val sessionId: String,
        val projectName: String?,
        val pendingSince: Instant,
        val pendingMillis: Long
    ) : UsageAlert
}

/**
 * Memória de disparo entre passadas do polling.
 *
 * Sem ela, o laço de 10 minutos reemitiria o mesmo alerta a cada coleta enquanto
 * a cota permanecesse acima do limiar.
 */
data class UsageAlertState(
    val quotaWindows: Map<QuotaAlertScope, FiredQuotaWindow> = emptyMap(),
    val firedSessionIds: Set<String> = emptySet(),
    val firedStalledSessionIds: Set<String> = emptySet()
) {
    companion object {
        val EMPTY = UsageAlertState()
    }
}

/** Identidade de uma cota através dos períodos: não inclui o reset, de propósito. */
data class QuotaAlertScope(
    val target: UsageTargetKey,
    val quotaLabel: String,
    val periodType: PeriodType
)

/** Limiares já disparados dentro de uma janela específica. */
data class FiredQuotaWindow(
    val periodEndAt: Instant,
    val firedPercents: Set<Int> = emptySet()
)

/** Alertas a emitir agora e o estado a guardar para a próxima passada. */
data class UsageAlertEvaluation(
    val alerts: List<UsageAlert> = emptyList(),
    val state: UsageAlertState = UsageAlertState.EMPTY
)

/**
 * Decide o que alertar a partir do estado corrente do dashboard e do semáforo.
 *
 * Função pura: mesma entrada, mesma saída, sem relógio próprio nem I/O.
 *
 * `currentLocalHour` é a hora local já resolvida por quem chama — o domain não
 * conhece fuso de apresentação. `null` significa "não sei a hora", e nesse caso
 * o silêncio não se aplica: deixar de alertar por não saber a hora seria perder
 * o alerta em silêncio.
 */
fun evaluateUsageAlerts(
    stats: List<ApiUsageStats>,
    sessionPulse: SessionPulse,
    previous: UsageAlertState,
    settings: UsageAlertSettings,
    now: Instant,
    currentLocalHour: Int? = null,
    stalledSessions: List<StalledCliSession> = emptyList()
): UsageAlertEvaluation {
    val silenced = currentLocalHour != null && settings.quietHours?.contains(currentLocalHour) == true

    val quotaResult = evaluateQuotaAlerts(
        stats = stats,
        previous = previous.quotaWindows,
        settings = settings,
        now = now,
        silenced = silenced
    )
    val sessionResult = evaluateSessionAlerts(
        pulse = sessionPulse,
        previous = previous.firedSessionIds,
        settings = settings,
        silenced = silenced
    )
    val stalledResult = evaluateStalledSessionAlerts(
        stalledSessions = stalledSessions,
        previous = previous.firedStalledSessionIds,
        settings = settings,
        silenced = silenced
    )

    return UsageAlertEvaluation(
        alerts = quotaResult.alerts + sessionResult.alerts + stalledResult.alerts,
        state = UsageAlertState(
            quotaWindows = quotaResult.windows,
            firedSessionIds = sessionResult.firedIds,
            firedStalledSessionIds = stalledResult.firedIds
        )
    )
}

private data class QuotaAlertResult(
    val alerts: List<UsageAlert>,
    val windows: Map<QuotaAlertScope, FiredQuotaWindow>
)

private fun evaluateQuotaAlerts(
    stats: List<ApiUsageStats>,
    previous: Map<QuotaAlertScope, FiredQuotaWindow>,
    settings: UsageAlertSettings,
    now: Instant,
    silenced: Boolean
): QuotaAlertResult {
    val thresholds = settings.effectiveQuotaPercents
    if (!settings.quotaAlertsEnabled || thresholds.isEmpty()) {
        // Alerta desligado não pode deixar rastro: religá-lo tem de voltar a
        // avisar sobre a janela corrente, não herdar disparos de quando estava
        // ligado.
        return QuotaAlertResult(emptyList(), emptyMap())
    }

    val alerts = mutableListOf<UsageAlert>()
    val windows = mutableMapOf<QuotaAlertScope, FiredQuotaWindow>()

    for (stat in stats) {
        for (quota in stat.quotas) {
            if (quota.total <= 0L || quota.isExpiredAt(now)) {
                // Janela vencida descreve um período que não existe mais: alertar
                // sobre ela seria alertar sobre o passado. O valor novo só vem da
                // próxima coleta.
                continue
            }

            val scope = QuotaAlertScope(
                target = stat.targetKey,
                quotaLabel = quota.label,
                periodType = quota.periodType
            )
            val storedWindow = previous[scope]
            val isSameWindow = storedWindow != null && isSamePeriod(storedWindow.periodEndAt, quota.periodEndAt)
            val alreadyFired = if (isSameWindow) storedWindow.firedPercents else emptySet()

            val actualPercent = quotaPercentUsed(quota)
            val crossed = thresholds.filter { threshold -> actualPercent >= threshold }
            val pending = crossed.filter { threshold -> threshold !in alreadyFired }

            if (silenced || pending.isEmpty()) {
                // No silêncio o limiar cruzado não é marcado como disparado: o
                // consumo só cresce dentro de uma janela, então o alerta volta a
                // ser avaliado — e emitido — quando o silêncio terminar. Marcá-lo
                // aqui perderia o aviso para sempre.
                windows[scope] = FiredQuotaWindow(quota.periodEndAt, alreadyFired)
                continue
            }

            // Um alerta por limiar cruzado: quem pula de 70% para 95% entre duas
            // coletas precisa saber que passou dos dois.
            for (threshold in pending) {
                alerts += UsageAlert.QuotaThreshold(
                    target = stat.targetKey,
                    targetLabel = stat.profileLabel?.takeIf { label -> label.isNotBlank() } ?: stat.apiName,
                    quotaLabel = quota.label,
                    thresholdPercent = threshold,
                    actualPercent = actualPercent,
                    periodEndAt = quota.periodEndAt,
                    hasKnownResetAt = quota.hasKnownResetAt
                )
            }
            windows[scope] = FiredQuotaWindow(quota.periodEndAt, alreadyFired + pending)
        }
    }

    return QuotaAlertResult(alerts, windows)
}

private data class SessionAlertResult(
    val alerts: List<UsageAlert>,
    val firedIds: Set<String>
)

private fun evaluateSessionAlerts(
    pulse: SessionPulse,
    previous: Set<String>,
    settings: UsageAlertSettings,
    silenced: Boolean
): SessionAlertResult {
    if (!settings.sessionAlertsEnabled) {
        return SessionAlertResult(emptyList(), emptySet())
    }

    // Só saturada vira notificação. "Atenção" apareceu em 7 das 70 sessões
    // medidas (ver CliSessionHealthThresholds): notificar nesse patamar tornaria
    // o alerta rotina e, portanto, ignorável.
    val saturated = pulse.alerts.filter { alert -> alert.health == CliSessionHealth.SATURATED }
    val saturatedIds = saturated.map { alert -> alert.sessionId }.toSet()

    // O estado só guarda sessão ainda presente no pulso. Uma sessão que saiu da
    // janela de atividade e volta saturada depois é um problema novo, e merece
    // um aviso novo.
    val stillKnown = previous.intersect(saturatedIds)

    if (silenced) {
        return SessionAlertResult(emptyList(), stillKnown)
    }

    val pending = saturated.filter { alert -> alert.sessionId !in stillKnown }
    val alerts = pending.map { alert ->
        UsageAlert.SessionSaturated(
            sessionId = alert.sessionId,
            projectName = alert.projectName
        )
    }

    return SessionAlertResult(alerts, stillKnown + pending.map { alert -> alert.sessionId })
}

/**
 * Um aviso por sessão que ficou sem resposta.
 *
 * Mesma anatomia de [evaluateSessionAlerts], e pelas mesmas razões: desligar o
 * alerta zera o estado, para religá-lo voltar a avisar sobre o que está
 * acontecendo agora; o silêncio adia sem marcar, porque a pendência só cresce
 * dentro da janela de idade e o aviso continua verdadeiro quando o silêncio
 * terminar; e o estado só guarda sessão ainda presente na lista — uma sessão que
 * saiu (respondeu, ou passou do teto de idade) e volta a ficar sem resposta é um
 * problema novo e merece aviso novo.
 */
private fun evaluateStalledSessionAlerts(
    stalledSessions: List<StalledCliSession>,
    previous: Set<String>,
    settings: UsageAlertSettings,
    silenced: Boolean
): SessionAlertResult {
    if (!settings.stalledSessionAlertsEnabled) {
        return SessionAlertResult(emptyList(), emptySet())
    }

    val currentIds = stalledSessions.map { stalled -> stalled.sessionId }.toSet()
    val stillKnown = previous.intersect(currentIds)

    if (silenced) {
        return SessionAlertResult(emptyList(), stillKnown)
    }

    val pending = stalledSessions.filter { stalled -> stalled.sessionId !in stillKnown }
    val alerts = pending.map { stalled ->
        UsageAlert.SessionStalled(
            sessionId = stalled.sessionId,
            projectName = stalled.projectName,
            pendingSince = stalled.pendingSince,
            pendingMillis = stalled.pendingMillis
        )
    }

    return SessionAlertResult(alerts, stillKnown + pending.map { stalled -> stalled.sessionId })
}

/**
 * Percentual inteiro da cota, arredondado para baixo.
 *
 * Para baixo porque o limiar é um piso: 89,9% não cruzou 90%. Arredondar para o
 * mais próximo dispararia o alerta de 90% antes de a cota chegar lá.
 */
private fun quotaPercentUsed(quota: QuotaInfo): Int {
    return (quota.percentageUsed * 100f).toInt().coerceIn(0, 100)
}
