package com.usagemonitor.data.mapper

import com.usagemonitor.data.dto.ApiUsageStatsCacheDto
import com.usagemonitor.data.dto.DashboardCacheDto
import com.usagemonitor.data.dto.QuotaInfoCacheDto
import com.usagemonitor.data.dto.ReportedModelQuotaCacheDto
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageNotice
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.ReportedModelQuota
import com.usagemonitor.domain.entity.UsageAccountContext
import com.usagemonitor.domain.entity.UsageAccountKey
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.entity.isObservedActivitySource
import kotlinx.datetime.Instant

fun ApiUsageStats.toCacheDto(): ApiUsageStatsCacheDto {
    return ApiUsageStatsCacheDto(
        targetKey = targetKey.storageKey,
        source = source.name,
        apiName = apiName,
        quotas = quotas.map { quota -> quota.toCacheDto() },
        accountSource = accountContext?.key?.source?.name,
        accountProviderAccountId = accountContext?.key?.providerAccountId,
        accountWorkspaceId = accountContext?.key?.workspaceId,
        accountEmail = accountContext?.email,
        accountWorkspaceName = accountContext?.workspaceName,
        profileLabel = profileLabel,
        notices = notices.map { notice -> notice.name },
        reportedModelQuotas = reportedModelQuotas.map { metric -> metric.toCacheDto() }
    )
}

private fun QuotaInfo.toCacheDto(): QuotaInfoCacheDto {
    return QuotaInfoCacheDto(
        label = label,
        used = used,
        total = total,
        periodEndAtEpochMillis = periodEndAt.toEpochMilliseconds(),
        hasKnownResetAt = hasKnownResetAt,
        periodType = periodType.name,
        unit = unit.name,
        rawUsed = rawUsed,
        rawTotal = rawTotal,
        currencyCode = currencyCode
    )
}

fun DashboardCacheDto.toDomain(): List<ApiUsageStats> {
    return entries.mapNotNull { entry -> entry.toDomainOrNull() }
}

private fun ApiUsageStatsCacheDto.toDomainOrNull(): ApiUsageStats? {
    val parsedTargetKey = UsageTargetKey.fromStorageKey(targetKey) ?: return null
    val parsedSource = runCatching { ApiSource.valueOf(source) }.getOrNull() ?: return null
    val parsedQuotas = quotas.mapNotNull { quota -> quota.toDomainOrNull() }
    val parsedReportedModelQuotas = reportedModelQuotas.mapNotNull { metric -> metric.toDomainOrNull() }
    if (parsedQuotas.isEmpty() && parsedReportedModelQuotas.isEmpty() && !parsedSource.isObservedActivitySource()) {
        return null
    }

    val parsedAccountContext = accountEmail?.let { email ->
        val providerAccountId = accountProviderAccountId ?: return@let null
        val accountKeySource = accountSource
            ?.let { value -> runCatching { ApiSource.valueOf(value) }.getOrNull() }
            ?: parsedSource
        runCatching {
            UsageAccountContext(
                key = UsageAccountKey(
                    source = accountKeySource,
                    providerAccountId = providerAccountId,
                    workspaceId = accountWorkspaceId
                ),
                email = email,
                workspaceName = accountWorkspaceName
            )
        }.getOrNull()
    }

    return runCatching {
        ApiUsageStats(
            source = parsedSource,
            targetKey = parsedTargetKey,
            apiName = apiName,
            quotas = parsedQuotas,
            accountContext = parsedAccountContext,
            profileLabel = profileLabel,
            notices = notices.mapNotNull { name -> runCatching { ApiUsageNotice.valueOf(name) }.getOrNull() }.toSet(),
            reportedModelQuotas = parsedReportedModelQuotas
        )
    }.getOrNull()
}

private fun ReportedModelQuota.toCacheDto(): ReportedModelQuotaCacheDto = ReportedModelQuotaCacheDto(
    modelName = modelName,
    used = used,
    remaining = remaining,
    limit = limit,
    usedPercent = usedPercent,
    remainingPercent = remainingPercent,
    unit = unit.name,
    resetDescription = resetDescription
)

private fun ReportedModelQuotaCacheDto.toDomainOrNull(): ReportedModelQuota? {
    val parsedUnit = runCatching { UsageUnit.valueOf(unit) }.getOrNull() ?: return null
    if (parsedUnit != UsageUnit.TOKENS && parsedUnit != UsageUnit.REQUESTS && parsedUnit != UsageUnit.PERCENTAGE) return null
    if (modelName.isBlank() || listOfNotNull(used, remaining, limit).any { value -> value < 0L }) return null
    if (listOfNotNull(usedPercent, remainingPercent).any { value -> !value.isFinite() || value !in 0.0..100.0 }) return null
    if (used == null && remaining == null && limit == null && usedPercent == null && remainingPercent == null) return null
    return ReportedModelQuota(modelName, used, remaining, limit, usedPercent, remainingPercent, parsedUnit, resetDescription)
}

private fun QuotaInfoCacheDto.toDomainOrNull(): QuotaInfo? {
    val parsedPeriodType = runCatching { PeriodType.valueOf(periodType) }.getOrNull() ?: return null
    val parsedUnit = runCatching { UsageUnit.valueOf(unit) }.getOrNull() ?: return null
    return QuotaInfo(
        label = label,
        used = used,
        total = total,
        periodEndAt = Instant.fromEpochMilliseconds(periodEndAtEpochMillis),
        hasKnownResetAt = hasKnownResetAt,
        periodType = parsedPeriodType,
        unit = parsedUnit,
        rawUsed = rawUsed,
        rawTotal = rawTotal,
        currencyCode = currencyCode
    )
}
