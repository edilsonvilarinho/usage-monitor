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
    val capturedAt: Instant
)
