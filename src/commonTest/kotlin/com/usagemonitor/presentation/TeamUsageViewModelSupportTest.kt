package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.TeamAccountUsage
import com.usagemonitor.domain.entity.TeamMemberUsage
import com.usagemonitor.domain.entity.TeamUsageSnapshot
import com.usagemonitor.presentation.viewmodel.flattenTeamAccounts
import kotlin.test.Test
import kotlin.test.assertEquals

class TeamUsageViewModelSupportTest {

    private fun account(vararg members: TeamMemberUsage) = TeamAccountUsage(
        accountKey = "acc-1",
        label = "ana@empresa.com",
        snapshot = TeamUsageSnapshot(members = members.toList())
    )

    /**
     * Duas máquinas da mesma conta com o mesmo apelido e sem consumo empatavam em
     * todos os degraus do comparador; a ordem seguia a da resposta e o `StateFlow`
     * reemitia sem dado mudar.
     */
    @Test
    fun `mesmo apelido e mesmo total desempatam pelo dispositivo`() {
        val desktop = TeamMemberUsage(deviceId = "d-desktop", alias = "ana")
        val laptop = TeamMemberUsage(deviceId = "d-laptop", alias = "ana")

        val forward = flattenTeamAccounts(listOf(account(desktop, laptop))).map { member -> member.deviceId }
        val reversed = flattenTeamAccounts(listOf(account(laptop, desktop))).map { member -> member.deviceId }

        assertEquals(listOf("d-desktop", "d-laptop"), forward)
        assertEquals(forward, reversed)
    }
}
