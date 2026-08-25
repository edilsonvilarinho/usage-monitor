package com.usagemonitor.data.mapper

import com.usagemonitor.data.dto.TeamAccountDeletionDto
import com.usagemonitor.data.dto.TeamKeyDto
import com.usagemonitor.data.dto.TeamOverviewDto
import com.usagemonitor.data.dto.TeamSnapshotDto
import com.usagemonitor.data.dto.TeamVerificationDto
import com.usagemonitor.domain.entity.TeamAccountDeletion
import com.usagemonitor.domain.entity.TeamAccountUsage
import com.usagemonitor.domain.entity.TeamAccountEmailSource
import com.usagemonitor.domain.entity.TeamKeyEntry
import com.usagemonitor.domain.entity.TeamKeyVerification
import kotlinx.datetime.Instant

fun TeamVerificationDto.toDomain(): TeamKeyVerification {
    return TeamKeyVerification(
        authorized = authorized,
        claimed = claimed,
        label = label,
        maxAccounts = maxAccounts,
        claimedAccounts = claimedAccounts
    )
}

fun TeamKeyDto.toDomain(): TeamKeyEntry {
    return TeamKeyEntry(
        id = id,
        label = label,
        key = key,
        keyPrefix = keyPrefix,
        maxAccounts = maxAccounts,
        accounts = accounts,
        createdAt = createdAt.toInstantOrNull(),
        revokedAt = revokedAt.toInstantOrNull(),
        lastUsedAt = lastUsedAt.toInstantOrNull()
    )
}

fun TeamAccountDeletionDto.toDomain(): TeamAccountDeletion {
    return TeamAccountDeletion(
        deletedTurns = deletedTurns,
        deletedSessions = deletedSessions,
        deletedMembers = deletedMembers,
        unlinkedKeys = unlinkedKeys
    )
}

/**
 * Achata a resposta global em uma entrada por conta.
 *
 * Cada conta passa pelo mesmo [toDomain] do snapshot por conta — a precificação
 * e o agrupamento por integrante não podem divergir entre o painel do admin e o
 * modal de uma conta só.
 */
fun TeamOverviewDto.toDomain(): List<TeamAccountUsage> {
    return accounts.map { account ->
        TeamAccountUsage(
            accountKey = account.accountKey,
            label = account.label,
            accountEmail = account.accountEmail,
            emailSource = when (account.emailSource) {
                "reported" -> TeamAccountEmailSource.REPORTED
                "label" -> TeamAccountEmailSource.LABEL
                else -> null
            },
            snapshot = TeamSnapshotDto(
                members = account.members,
                rows = account.rows,
                activity = account.activity
            ).toDomain()
        )
    }
}

/** Epoch millis ausente ou zerado vira `null` — não existe data zero aqui. */
private fun Long?.toInstantOrNull(): Instant? {
    if (this == null || this <= 0L) {
        return null
    }
    return Instant.fromEpochMilliseconds(this)
}
