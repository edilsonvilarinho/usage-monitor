package com.usagemonitor.domain

import com.usagemonitor.domain.entity.TeamCertificateProblem
import com.usagemonitor.domain.entity.TeamConnectionFailure
import com.usagemonitor.domain.entity.TeamConnectionStage
import com.usagemonitor.domain.entity.TeamStageStatus
import com.usagemonitor.domain.entity.stageStatuses
import kotlin.test.Test
import kotlin.test.assertEquals

class TeamConnectionFailureTest {

    @Test
    fun `certificate failure proves address and connection and leaves the rest not reached`() {
        val failure = TeamConnectionFailure(
            stage = TeamConnectionStage.CERTIFICATE,
            certificateProblem = TeamCertificateProblem.EXPIRED
        )

        assertEquals(
            listOf(
                TeamConnectionStage.ADDRESS to TeamStageStatus.PASSED,
                TeamConnectionStage.CONNECTION to TeamStageStatus.PASSED,
                TeamConnectionStage.CERTIFICATE to TeamStageStatus.FAILED,
                TeamConnectionStage.SERVER to TeamStageStatus.NOT_REACHED,
                TeamConnectionStage.CREDENTIAL to TeamStageStatus.NOT_REACHED
            ),
            failure.stageStatuses()
        )
    }

    @Test
    fun `address failure is the first stage and nothing before it is claimed`() {
        val statuses = TeamConnectionFailure(stage = TeamConnectionStage.ADDRESS).stageStatuses()

        assertEquals(TeamStageStatus.FAILED, statuses.first().second)
        assertEquals(List(4) { TeamStageStatus.NOT_REACHED }, statuses.drop(1).map { it.second })
    }

    @Test
    fun `unproven earlier stages stay unknown instead of passed`() {
        // Timeout de socket não diz se o nome resolveu: nada antes dele ganha ✓.
        val failure = TeamConnectionFailure(
            stage = TeamConnectionStage.CONNECTION,
            timedOut = true,
            earlierStagesProven = false
        )

        assertEquals(
            listOf(TeamStageStatus.UNKNOWN, TeamStageStatus.FAILED, TeamStageStatus.NOT_REACHED),
            failure.stageStatuses().take(3).map { it.second }
        )
    }

    @Test
    fun `credential failure passes every stage before it`() {
        val failure = TeamConnectionFailure(stage = TeamConnectionStage.CREDENTIAL, httpStatus = 401)

        assertEquals(
            List(4) { TeamStageStatus.PASSED } + TeamStageStatus.FAILED,
            failure.stageStatuses().map { it.second }
        )
    }
}
