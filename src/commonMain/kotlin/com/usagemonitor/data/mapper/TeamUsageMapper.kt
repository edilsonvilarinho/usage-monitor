package com.usagemonitor.data.mapper

import com.usagemonitor.data.dto.TeamIngestRequestDto
import com.usagemonitor.data.dto.TeamIngestResponseDto
import com.usagemonitor.data.dto.TeamMemberDto
import com.usagemonitor.data.dto.TeamSessionUploadDto
import com.usagemonitor.data.dto.TeamSnapshotDto
import com.usagemonitor.data.dto.TeamTurnUploadDto
import com.usagemonitor.domain.entity.TeamIngestPayload
import com.usagemonitor.domain.entity.TeamIngestReceipt
import com.usagemonitor.domain.entity.TeamMemberUsage
import com.usagemonitor.domain.entity.TeamUsageSnapshot
import com.usagemonitor.domain.entity.WindowedSessionAccumulator
import kotlinx.datetime.Instant

fun TeamIngestPayload.toDto(): TeamIngestRequestDto {
    return TeamIngestRequestDto(
        accountKey = accountKey,
        member = TeamMemberDto(
            deviceId = member.deviceId,
            alias = member.alias,
            hostName = member.hostName,
            organizationUuid = member.organizationUuid,
            organizationName = member.organizationName
        ),
        sessions = sessions.map { session ->
            TeamSessionUploadDto(
                sessionId = session.sessionId,
                cwd = session.cwd,
                gitBranch = session.gitBranch,
                firstTs = session.firstTs.toEpochMilliseconds(),
                lastTs = session.lastTs.toEpochMilliseconds(),
                liveContextTokens = session.liveContextTokens,
                liveContextModel = session.liveContextModel
            )
        },
        turns = turns.map { turn ->
            TeamTurnUploadDto(
                sessionId = turn.sessionId,
                messageId = turn.messageId,
                ts = turn.ts.toEpochMilliseconds(),
                model = turn.model,
                isSidechain = turn.isSidechain,
                inputTokens = turn.inputTokens,
                outputTokens = turn.outputTokens,
                cacheReadTokens = turn.cacheReadTokens,
                cacheWrite5mTokens = turn.cacheWrite5mTokens,
                cacheWrite1hTokens = turn.cacheWrite1hTokens
            )
        }
    )
}

fun TeamIngestResponseDto.toDomain(): TeamIngestReceipt {
    return TeamIngestReceipt(
        acceptedTurns = acceptedTurns,
        ignoredTurns = ignoredTurns,
        acceptedSessions = acceptedSessions
    )
}

/**
 * Dobra as linhas `(deviceId, sessionId, model)` do servidor em sessões e
 * agrupa por integrante.
 *
 * A precificação acontece aqui, com [WindowedSessionAccumulator] — a mesma
 * classe que o índice local usa. É o que garante que o custo do modal de time e
 * o do modal de sessões da máquina saiam do mesmo cálculo, tarifa por tarifa,
 * em vez de duas implementações que divergem com o tempo.
 *
 * Membros sem nenhuma linha na janela continuam na lista com `sessions` vazia:
 * quem não consumiu no período é informação, não ruído.
 */
fun TeamSnapshotDto.toDomain(): TeamUsageSnapshot {
    val accumulatorsByDevice = LinkedHashMap<String, LinkedHashMap<String, WindowedSessionAccumulator>>()

    for (row in rows) {
        val sessionsOfDevice = accumulatorsByDevice.getOrPut(row.deviceId) { LinkedHashMap() }
        val accumulator = sessionsOfDevice.getOrPut(row.sessionId) {
            WindowedSessionAccumulator(
                sessionId = row.sessionId,
                cwd = row.cwd,
                gitBranch = row.gitBranch,
                liveContextTokens = row.liveContextTokens,
                liveContextModel = row.liveContextModel
            )
        }
        accumulator.addModelGroup(
            model = row.model,
            turnCount = row.turnCount,
            firstTsMillis = row.firstTs,
            lastTsMillis = row.lastTs,
            inputTokens = row.inputTokens,
            outputTokens = row.outputTokens,
            cacheReadTokens = row.cacheReadTokens,
            cacheWrite5mTokens = row.cacheWrite5mTokens,
            cacheWrite1hTokens = row.cacheWrite1hTokens
        )
    }

    val summariesByDevice = accumulatorsByDevice.mapValues { (_, sessions) ->
        sessions.values
            .map { accumulator -> accumulator.toSummary() }
            .sortedByDescending { summary -> summary.lastTs }
    }

    val knownDeviceIds = members.map { member -> member.deviceId }.toSet()

    val declaredMembers = members.map { member ->
        TeamMemberUsage(
            deviceId = member.deviceId,
            alias = member.alias,
            hostName = member.hostName,
            organizationName = member.organizationName,
            lastSeenAt = member.lastSeenAt
                .takeIf { millis -> millis > 0L }
                ?.let { millis -> Instant.fromEpochMilliseconds(millis) },
            sessions = summariesByDevice[member.deviceId].orEmpty()
        )
    }

    // Um device com sessões mas sem linha em `members` não pode sumir da conta
    // do time: os totais ficariam menores que a soma real.
    val orphanMembers = summariesByDevice
        .filterKeys { deviceId -> deviceId !in knownDeviceIds }
        .map { (deviceId, sessions) ->
            TeamMemberUsage(deviceId = deviceId, alias = deviceId, sessions = sessions)
        }

    val allMembers = (declaredMembers + orphanMembers).sortedWith(
        // Quem mais consumiu primeiro; sem atividade vai para o fim, em ordem
        // alfabética, para a lista não embaralhar entre tiques do tempo real.
        compareByDescending<TeamMemberUsage> { member -> member.totalTokens }
            .thenBy { member -> member.alias.lowercase() }
    )

    return TeamUsageSnapshot(members = allMembers)
}
