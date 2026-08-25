package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.CliSessionHealth
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.TeamMemberUsage
import com.usagemonitor.domain.entity.TeamAccountEmailSource
import com.usagemonitor.presentation.ui.teamUsageWindowTitle
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.viewmodel.TeamUsageUiState
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val GROUP_NOW = Instant.parse("2026-08-13T12:00:00Z")

/** Janela de contexto conhecida: sem ela a sessão não recebe veredito. */
private const val GROUP_OPUS = "claude-opus-5"

private fun groupSession(
    id: String,
    tokens: Long = 0L,
    cost: Long = 0L,
    unpricedTurnCount: Int = 0
): CliSessionSummary {
    return CliSessionSummary(
        sessionId = id,
        filePath = "",
        firstTs = GROUP_NOW,
        lastTs = GROUP_NOW,
        inputTokens = tokens,
        costMicros = cost,
        unpricedTurnCount = unpricedTurnCount
    )
}

private fun ratedGroupSession(id: String, liveContextTokens: Long): CliSessionSummary {
    return groupSession(id, tokens = 10L).copy(
        primaryModel = GROUP_OPUS,
        liveContextModel = GROUP_OPUS,
        liveContextTokens = liveContextTokens
    )
}

private fun groupMember(
    deviceId: String,
    accountKey: String?,
    accountLabel: String? = null,
    accountEmail: String? = null,
    emailSource: TeamAccountEmailSource? = null,
    sessions: List<CliSessionSummary> = emptyList()
): TeamMemberUsage {
    return TeamMemberUsage(
        deviceId = deviceId,
        alias = deviceId,
        sessions = sessions,
        accountKey = accountKey,
        accountLabel = accountLabel,
        accountEmail = accountEmail,
        accountEmailSource = emailSource
    )
}

/**
 * Totais da faixa de conta na visão global (issue #45).
 *
 * Os derivados ficam no grupo, e não na tela, pela mesma razão dos derivados do
 * integrante: são a única prova de que o número da faixa é a soma exata das
 * linhas debaixo dela.
 */
class TeamAccountGroupTest {

    @Test
    fun `dois UUIDs do mesmo e-mail formam um grupo sem fundir identidades`() {
        val state = TeamUsageUiState.Success(
            members = listOf(
                groupMember(
                    deviceId = "device-1",
                    accountKey = "account-a",
                    accountEmail = "pessoa@empresa.com",
                    emailSource = TeamAccountEmailSource.REPORTED,
                    sessions = listOf(groupSession("s1", tokens = 300L))
                ),
                groupMember(
                    deviceId = "device-1",
                    accountKey = "account-b",
                    accountEmail = "pessoa@empresa.com",
                    emailSource = TeamAccountEmailSource.LABEL,
                    sessions = listOf(groupSession("s2", tokens = 700L))
                )
            ),
            isAdminOverview = true
        )

        val group = state.emailGroups.single()
        assertEquals(2, group.accounts.size)
        assertEquals(1_000L, group.totalTokens)
        assertEquals(setOf("account-a", "account-b"), group.accounts.map { it.accountKey }.toSet())
        assertEquals(2, group.members.map { it.memberKey }.distinct().size)
        assertTrue(group.hasProvisionalAccounts)
    }

    @Test
    fun `grupo soma sessoes tokens e custo dos integrantes`() {
        val state = TeamUsageUiState.Success(
            members = listOf(
                groupMember(
                    "device-1",
                    accountKey = "account-a",
                    accountLabel = "fulano@empresa.com",
                    sessions = listOf(
                        groupSession("s1", tokens = 300L, cost = 1_000_000L),
                        groupSession("s2", tokens = 200L, cost = 500_000L)
                    )
                ),
                groupMember(
                    "device-2",
                    accountKey = "account-a",
                    sessions = listOf(groupSession("s3", tokens = 500L, cost = 2_500_000L))
                )
            ),
            isAdminOverview = true
        )

        val group = state.memberGroups.single()
        assertEquals(3, group.sessionCount)
        assertEquals(1_000L, group.totalTokens)
        assertEquals(4_000_000L, group.totalCostMicros)
        assertEquals(2, group.activeMemberCount)
        assertEquals("fulano@empresa.com", group.accountLabel)
    }

    @Test
    fun `custo do grupo fica incompleto quando um integrante tem turno sem preco`() {
        val state = TeamUsageUiState.Success(
            members = listOf(
                groupMember(
                    "device-1",
                    accountKey = "account-a",
                    sessions = listOf(groupSession("s1", tokens = 100L))
                ),
                groupMember(
                    "device-2",
                    accountKey = "account-a",
                    sessions = listOf(groupSession("s2", tokens = 100L, unpricedTurnCount = 2))
                )
            ),
            isAdminOverview = true
        )

        assertFalse(state.memberGroups.single().isCostComplete)
    }

    @Test
    fun `grupo reporta o pior veredito entre as sessoes das maquinas dele`() {
        val state = TeamUsageUiState.Success(
            members = listOf(
                groupMember(
                    "device-1",
                    accountKey = "account-a",
                    sessions = listOf(ratedGroupSession("s1", liveContextTokens = 10_000L))
                ),
                groupMember(
                    "device-2",
                    accountKey = "account-a",
                    sessions = listOf(ratedGroupSession("s2", liveContextTokens = 900_000L))
                )
            ),
            isAdminOverview = true
        )

        assertEquals(CliSessionHealth.SATURATED, state.memberGroups.single().worstHealth)
    }

    @Test
    fun `conta sem sessao avaliavel nao inventa veredito`() {
        val state = TeamUsageUiState.Success(
            members = listOf(
                groupMember(
                    "device-1",
                    accountKey = "account-a",
                    sessions = listOf(groupSession("s1", tokens = 100L))
                )
            ),
            isAdminOverview = true
        )

        assertNull(state.memberGroups.single().worstHealth)
    }

    @Test
    fun `fatias das contas usam o total da janela e somam tudo`() {
        val state = TeamUsageUiState.Success(
            members = listOf(
                groupMember(
                    "device-1",
                    accountKey = "account-a",
                    sessions = listOf(groupSession("s1", tokens = 750L))
                ),
                groupMember(
                    "device-2",
                    accountKey = "account-b",
                    sessions = listOf(groupSession("s2", tokens = 250L))
                )
            ),
            isAdminOverview = true
        )

        val groups = state.memberGroups
        assertEquals(0.75, state.tokenShareOf(groups[0]))
        assertEquals(0.25, state.tokenShareOf(groups[1]))
    }

    @Test
    fun `janela sem tokens nao divide por zero`() {
        val state = TeamUsageUiState.Success(
            members = listOf(groupMember("device-1", accountKey = "account-a")),
            isAdminOverview = true
        )

        assertEquals(0.0, state.tokenShareOf(state.memberGroups.single()))
    }

    /** Issue #45: a visão global mostra vários times, então o título vai no plural. */
    @Test
    fun `titulo vai ao plural apenas na visao global`() {
        assertEquals(
            "Sessões dos times",
            teamUsageWindowTitle(AppLanguage.PT, accountLabel = null, isAdminOverview = true)
        )
        assertEquals(
            "All team sessions",
            teamUsageWindowTitle(AppLanguage.EN, accountLabel = null, isAdminOverview = true)
        )
        assertEquals(
            "Sessões do time — fulano@empresa.com",
            teamUsageWindowTitle(AppLanguage.PT, accountLabel = "fulano@empresa.com")
        )
        assertEquals(
            "Team sessions — fulano@empresa.com",
            teamUsageWindowTitle(AppLanguage.EN, accountLabel = "fulano@empresa.com")
        )
    }

    /**
     * Rótulo em branco não é prova de visão global: sem a flag de quem abriu a
     * janela, uma conta sem rótulo receberia o plural.
     */
    @Test
    fun `conta sem rotulo mantem o titulo no singular`() {
        assertEquals(
            "Sessões do time",
            teamUsageWindowTitle(AppLanguage.PT, accountLabel = "")
        )
        assertTrue(teamUsageWindowTitle(AppLanguage.PT, accountLabel = null).endsWith("do time"))
    }
}
