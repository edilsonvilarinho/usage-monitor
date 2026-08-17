package com.usagemonitor.data.mapper

import com.usagemonitor.data.dto.TeamIngestRequestDto
import com.usagemonitor.data.dto.TeamIngestResponseDto
import com.usagemonitor.data.dto.TeamMemberDto
import com.usagemonitor.data.dto.TeamPresenceResponseDto
import com.usagemonitor.data.dto.TeamSessionDetailResponseDto
import com.usagemonitor.data.dto.TeamSessionUploadDto
import com.usagemonitor.data.dto.TeamTrendRowDto
import com.usagemonitor.domain.entity.TeamTrendRow
import com.usagemonitor.data.dto.TeamSnapshotDto
import com.usagemonitor.data.dto.TeamTurnUploadDto
import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.CliSessionTurn
import com.usagemonitor.domain.entity.CliUsageGroupRow
import com.usagemonitor.domain.entity.TeamIngestPayload
import com.usagemonitor.domain.entity.TeamIngestReceipt
import com.usagemonitor.domain.entity.TeamMemberIdentity
import com.usagemonitor.domain.entity.TeamMemberUsage
import com.usagemonitor.domain.entity.TeamPresenceReceipt
import com.usagemonitor.domain.entity.TeamUsageSnapshot
import com.usagemonitor.domain.entity.WindowedSessionAccumulator
import kotlinx.datetime.Instant

/**
 * Identidade da máquina no formato do servidor.
 *
 * Extraída para função própria porque dois caminhos a enviam — o ingest e a
 * batida de presença — e duas montagens do mesmo DTO divergiriam no primeiro
 * campo novo de identidade.
 */
fun TeamMemberIdentity.toDto(): TeamMemberDto {
    return TeamMemberDto(
        deviceId = deviceId,
        alias = alias,
        hostName = hostName,
        organizationUuid = organizationUuid,
        organizationName = organizationName
    )
}

fun TeamPresenceResponseDto.toDomain(): TeamPresenceReceipt {
    // `0L` é a ausência do campo, não a época zero: um servidor que não o mande
    // deixa o cliente sem medida, e não com um desvio de 56 anos.
    return TeamPresenceReceipt(
        serverTimeAt = lastSeenAt.takeIf { millis -> millis > 0L }
            ?.let { millis -> Instant.fromEpochMilliseconds(millis) }
    )
}

fun TeamIngestPayload.toDto(): TeamIngestRequestDto {
    return TeamIngestRequestDto(
        accountKey = accountKey,
        member = member.toDto(),
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

    // As mesmas linhas da resposta, guardadas cruas para o resumo por eixo. Sair
    // das sessões já dobradas não serviria: `toSummary()` colapsa os modelos num
    // `primaryModel` só, e o eixo "por modelo" some no caminho.
    val groupRowsByDevice = LinkedHashMap<String, MutableList<CliUsageGroupRow>>()

    // A máquina é do integrante, não da linha de uso: `team_sessions` não guarda
    // hostname. Sem este índice a sessão chegaria com `hostName` nulo e o card de
    // metadados do detalhe mostraria "Máquina —" para uma máquina conhecida.
    val hostNamesByDevice = members.associate { member -> member.deviceId to member.hostName }

    for (row in rows) {
        val sessionsOfDevice = accumulatorsByDevice.getOrPut(row.deviceId) { LinkedHashMap() }
        val accumulator = sessionsOfDevice.getOrPut(row.sessionId) {
            WindowedSessionAccumulator(
                sessionId = row.sessionId,
                cwd = row.cwd,
                gitBranch = row.gitBranch,
                hostName = hostNamesByDevice[row.deviceId],
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

        groupRowsByDevice.getOrPut(row.deviceId) { mutableListOf() }.add(
            CliUsageGroupRow(
                sessionId = row.sessionId,
                cwd = row.cwd,
                gitBranch = row.gitBranch,
                model = row.model,
                turnCount = row.turnCount,
                inputTokens = row.inputTokens,
                outputTokens = row.outputTokens,
                cacheReadTokens = row.cacheReadTokens,
                cacheWrite5mTokens = row.cacheWrite5mTokens,
                cacheWrite1hTokens = row.cacheWrite1hTokens
            )
        )
    }

    // Servidor anterior à 0.7.0 não manda o campo: o mapa fica vazio e as sessões
    // ficam com `activeMillis` nulo — "não medido", e não zero.
    val activeMillisByKey = activity.associate { entry ->
        (entry.deviceId to entry.sessionId) to entry.activeMillis
    }
    val hasActivity = activity.isNotEmpty()

    val summariesByDevice = accumulatorsByDevice.mapValues { (deviceId, sessions) ->
        sessions.values
            .map { accumulator ->
                val summary = accumulator.toSummary()
                if (!hasActivity) {
                    return@map summary
                }
                // Sessão fora do mapa recebe zero: a medida aconteceu e a ausência
                // ali significa "nenhum intervalo dentro do corte".
                summary.copy(activeMillis = activeMillisByKey[deviceId to summary.sessionId] ?: 0L)
            }
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
            sessions = summariesByDevice[member.deviceId].orEmpty(),
            groupRows = groupRowsByDevice[member.deviceId].orEmpty()
        )
    }

    // Um device com sessões mas sem linha em `members` não pode sumir da conta
    // do time: os totais ficariam menores que a soma real.
    val orphanMembers = summariesByDevice
        .filterKeys { deviceId -> deviceId !in knownDeviceIds }
        .map { (deviceId, sessions) ->
            TeamMemberUsage(
                deviceId = deviceId,
                alias = deviceId,
                sessions = sessions,
                groupRows = groupRowsByDevice[deviceId].orEmpty()
            )
        }

    val allMembers = (declaredMembers + orphanMembers).sortedWith(
        // Quem mais consumiu primeiro; sem atividade vai para o fim, em ordem
        // alfabética, para a lista não embaralhar entre tiques do tempo real.
        compareByDescending<TeamMemberUsage> { member -> member.totalTokens }
            .thenBy { member -> member.alias.lowercase() }
    )

    return TeamUsageSnapshot(members = allMembers)
}

/**
 * Monta o detalhe de uma sessão do time a partir dos turnos crus do servidor.
 *
 * O resumo não vem pronto na resposta: ele é reagregado aqui pelo
 * [WindowedSessionAccumulator], a mesma classe que o índice local usa, para o
 * custo do detalhe do time sair do mesmo cálculo do detalhe da própria máquina.
 * Um turno por grupo de modelo — cada turno tem o seu, e é a tarifa dele que
 * vale.
 *
 * O `seq` é sintetizado da ordem da resposta (`ts`, depois `messageId`): o
 * servidor não guarda sequência, e o que o cálculo de analytics precisa é
 * apenas de uma ordem estável entre leituras.
 *
 * `filePath` fica vazio de propósito — o transcript está na máquina do colega e
 * o caminho dele não tem valor aqui.
 */
fun TeamSessionDetailResponseDto.toDomain(): CliSessionDetail {
    val accumulator = WindowedSessionAccumulator(
        sessionId = session.sessionId,
        cwd = session.cwd,
        gitBranch = session.gitBranch,
        hostName = session.hostName,
        liveContextTokens = session.liveContextTokens,
        liveContextModel = session.liveContextModel
    )

    for (turn in turns) {
        accumulator.addModelGroup(
            model = turn.model,
            turnCount = 1,
            firstTsMillis = turn.ts,
            lastTsMillis = turn.ts,
            inputTokens = turn.inputTokens,
            outputTokens = turn.outputTokens,
            cacheReadTokens = turn.cacheReadTokens,
            cacheWrite5mTokens = turn.cacheWrite5mTokens,
            cacheWrite1hTokens = turn.cacheWrite1hTokens
        )
    }

    val domainTurns = turns.mapIndexed { index, turn ->
        CliSessionTurn(
            sessionId = session.sessionId,
            seq = index,
            messageId = turn.messageId,
            ts = Instant.fromEpochMilliseconds(turn.ts),
            model = turn.model,
            isSidechain = turn.isSidechain,
            inputTokens = turn.inputTokens,
            outputTokens = turn.outputTokens,
            cacheReadTokens = turn.cacheReadTokens,
            cacheWrite5mTokens = turn.cacheWrite5mTokens,
            cacheWrite1hTokens = turn.cacheWrite1hTokens
        )
    }

    // Sessão sem turno mantém a janela declarada pelo servidor: o acumulador
    // vazio devolveria época zero e o card de período mostraria 01/01/1970.
    val summary = if (turns.isEmpty()) {
        accumulator.toSummary().copy(
            firstTs = Instant.fromEpochMilliseconds(session.firstTs),
            lastTs = Instant.fromEpochMilliseconds(session.lastTs)
        )
    } else {
        accumulator.toSummary()
    }

    return CliSessionDetail(summary = summary, turns = domainTurns)
}

/**
 * Membro do time como identidade.
 *
 * A tendência precisa do apelido para nomear as linhas do gráfico, e o snapshot
 * já traz o mesmo campo por outro caminho — este mapeia só a identidade, sem o
 * consumo que a tendência não usa.
 */
fun TeamMemberDto.toIdentity(): TeamMemberIdentity {
    return TeamMemberIdentity(
        deviceId = deviceId,
        alias = alias,
        hostName = hostName,
        organizationUuid = organizationUuid,
        organizationName = organizationName
    )
}

fun TeamTrendRowDto.toDomain(): TeamTrendRow {
    return TeamTrendRow(
        deviceId = deviceId,
        dayStartMillis = dayStartMillis,
        model = model,
        turnCount = turnCount,
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        cacheReadTokens = cacheReadTokens,
        cacheWrite5mTokens = cacheWrite5mTokens,
        cacheWrite1hTokens = cacheWrite1hTokens
    )
}
