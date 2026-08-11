package com.usagemonitor.data.mapper

import com.usagemonitor.data.dto.ApiUsageStatsCacheDto
import com.usagemonitor.data.dto.DashboardCacheDto
import com.usagemonitor.data.dto.QuotaInfoCacheDto
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageNotice
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageAccountContext
import com.usagemonitor.domain.entity.UsageAccountKey
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.UsageUnit
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
        notices = notices.map { notice -> notice.name }
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
    if (parsedQuotas.isEmpty()) {
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
            notices = notices.mapNotNull { name -> runCatching { ApiUsageNotice.valueOf(name) }.getOrNull() }.toSet()
        )
    }.getOrNull()
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
