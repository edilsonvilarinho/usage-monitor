package com.usagemonitor.data.dto

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.UsageUnit
import kotlinx.datetime.Instant

data class UsageSnapshotRecord(
    val source: ApiSource,
    val quotaLabel: String,
    val periodType: PeriodType,
    val unit: UsageUnit,
    val used: Long,
    val total: Long,
    val rawUsed: Long,
    val rawTotal: Long,
    val periodEndAt: Instant,
    val capturedAt: Instant,
    /**
     * Se a cota tem reset conhecido.
     *
     * Sem isto o histórico só via `periodEndAt`, e ele não distingue "reset daqui
     * a muito tempo" de "não existe reset": o saldo do DeepSeek grava
     * `Instant.DISTANT_FUTURE`, o Kilo e o OpenCode gravam o próprio `capturedAt`
     * e os créditos da Anthropic gravam outra sentinela. Três valores para a
     * mesma ausência, e nenhum deles legível como tal (issue #109).
     *
     * `true` no default porque é o que as linhas gravadas antes desta coluna
     * afirmavam implicitamente — e a decisão só olha o **último** ponto da série,
     * que é sempre recente.
     */
    val hasKnownResetAt: Boolean = true
)
