package com.usagemonitor.domain.entity

import kotlin.time.Duration.Companion.hours

/**
 * Período da janela "Análise" (#538): quanto do histórico a análise olha, contado para trás
 * a partir de agora. `NOW` é só a janela atual, sem resumo de períodos. Não reaproveita
 * [HistoryRange] nem [AnalysisSpan]: valor novo nesses enums quebra os `when` exaustivos deles.
 */
enum class AnalysisPeriod(val millis: Long?) {
    NOW(null),
    HOURS_24(24.hours.inWholeMilliseconds),
    DAYS_7(7 * 24.hours.inWholeMilliseconds),
    DAYS_30(30 * 24.hours.inWholeMilliseconds),
    DAYS_90(90 * 24.hours.inWholeMilliseconds);

    /** Só 30 d e 90 d têm linha de ritmo e vazão do período. */
    val usesPeriodRate: Boolean get() = this == DAYS_30 || this == DAYS_90
}
