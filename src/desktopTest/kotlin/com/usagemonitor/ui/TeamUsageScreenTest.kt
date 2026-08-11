package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.TeamMemberUsage
import com.usagemonitor.presentation.ui.TEAM_LIST_SCROLLBAR_TAG
import com.usagemonitor.presentation.ui.TEAM_MEMBER_REMOVE_TAG_PREFIX
import com.usagemonitor.presentation.ui.TEAM_MEMBER_ROW_TAG_PREFIX
import com.usagemonitor.presentation.ui.TEAM_REMOVE_CONFIRM_TAG
import com.usagemonitor.presentation.ui.TeamUsageContent
import com.usagemonitor.presentation.ui.theme.AppTheme
import com.usagemonitor.presentation.viewmodel.TeamUsageUiState
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

private val NOW = Instant.parse("2026-08-11T12:00:00Z")

@OptIn(ExperimentalTestApi::class)
class TeamUsageScreenTest {

    @Test
    fun `header mostra integrantes ativos tokens e custo do periodo`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamUsageUiState.Success(
                members = listOf(
                    member("device-1", "edilson", "DESKTOP-A1", listOf(session("s1", tokens = 2_000L, cost = 1_230_000L)))
                )
            )
        )

        onNodeWithText("1 integrante").assertIsDisplayed()
        onNodeWithText("$1.23").assertIsDisplayed()
        onNodeWithText("custo estimado · últimas 5h").assertIsDisplayed()
    }

    @Test
    fun `lista mostra alias maquina e tokens de cada integrante`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamUsageUiState.Success(
                members = listOf(
                    member("device-1", "edilson", "DESKTOP-A1", listOf(session("s1", tokens = 2_100_000L))),
                    member("device-2", "maria", "NOTE-B2", listOf(session("s2", tokens = 1_400_000L)))
                )
            )
        )

        onNodeWithText("edilson").assertIsDisplayed()
        onNodeWithText("DESKTOP-A1").assertIsDisplayed()
        onNodeWithText("maria").assertIsDisplayed()
        onNodeWithText("NOTE-B2").assertIsDisplayed()
    }

    @Test
    fun `os quatro chips de janela aparecem com o selecionado correto`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamUsageUiState.Success(
                members = listOf(member("device-1", "edilson", "DESKTOP-A1", listOf(session("s1")))),
                range = CliSessionRange.LAST_7D
            )
        )

        onNodeWithText("5h").assertIsDisplayed()
        onNodeWithText("7 dias").assertIsSelected()
        onNodeWithText("30 dias").assertIsDisplayed()
        onNodeWithText("Total").assertIsDisplayed()
    }

    @Test
    fun `clicar num chip emite a nova janela`() = runDesktopComposeUiTest {
        var selected: CliSessionRange? = null

        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    TeamUsageContent(
                        state = TeamUsageUiState.Success(
                            members = listOf(member("device-1", "edilson", "DESKTOP-A1", listOf(session("s1"))))
                        ),
                        language = AppLanguage.PT,
                        onSelectRange = { range -> selected = range },
                        onToggleMember = {}
                    )
                }
            }
        }

        onNodeWithText("30 dias").performClick()

        assertEquals(CliSessionRange.LAST_30D, selected)
    }

    @Test
    fun `clicar num integrante com atividade emite o toggle`() = runDesktopComposeUiTest {
        var toggled: String? = null

        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    TeamUsageContent(
                        state = TeamUsageUiState.Success(
                            members = listOf(member("device-1", "edilson", "DESKTOP-A1", listOf(session("s1"))))
                        ),
                        language = AppLanguage.PT,
                        onSelectRange = {},
                        onToggleMember = { deviceId -> toggled = deviceId }
                    )
                }
            }
        }

        onNodeWithTag("${TEAM_MEMBER_ROW_TAG_PREFIX}device-1").performClick()

        assertEquals("device-1", toggled)
    }

    @Test
    fun `integrante expandido mostra as sessoes dele`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamUsageUiState.Success(
                members = listOf(
                    member("device-1", "edilson", "DESKTOP-A1", listOf(session("abcdef0123", tokens = 500L)))
                ),
                expandedDeviceIds = setOf("device-1")
            )
        )

        // O id curto de oito caracteres é a mesma célula da lista de sessões local.
        onNodeWithText("abcdef01").assertIsDisplayed()
    }

    @Test
    fun `integrante recolhido nao mostra as sessoes`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamUsageUiState.Success(
                members = listOf(
                    member("device-1", "edilson", "DESKTOP-A1", listOf(session("abcdef0123")))
                )
            )
        )

        onNodeWithText("edilson").assertIsDisplayed()
        assertEquals(0, onAllNodesWithTextCount("abcdef01"))
    }

    @Test
    fun `integrante sem uso no periodo aparece marcado`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamUsageUiState.Success(
                members = listOf(
                    member("device-1", "edilson", "DESKTOP-A1", listOf(session("s1", tokens = 10L))),
                    member("device-2", "joao", "NOTE-C3", emptyList())
                )
            )
        )

        onNodeWithText("joao").assertIsDisplayed()
        onNodeWithText("sem uso no período").assertIsDisplayed()
        // Quem não usou não conta como integrante ativo do período.
        onNodeWithText("1 integrante").assertIsDisplayed()
    }

    @Test
    fun `time sem uso na janela explica que a janela e o motivo`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamUsageUiState.Success(
                members = listOf(member("device-1", "edilson", "DESKTOP-A1", emptyList())),
                range = CliSessionRange.LAST_5H
            )
        )

        onNodeWithText("Nenhum uso do time no período (5h). Escolha uma janela maior.").assertIsDisplayed()
    }

    @Test
    fun `estado de erro nomeia o servidor de time`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    TeamUsageContent(
                        state = TeamUsageUiState.Error(message = "HTTP 401"),
                        language = AppLanguage.PT,
                        onSelectRange = {},
                        onToggleMember = {}
                    )
                }
            }
        }

        onNodeWithText("Não foi possível falar com o servidor de time: HTTP 401").assertIsDisplayed()
    }

    @Test
    fun `a lista tem barra de rolagem`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamUsageUiState.Success(
                members = listOf(member("device-1", "edilson", "DESKTOP-A1", listOf(session("s1"))))
            )
        )

        onNodeWithTag(TEAM_LIST_SCROLLBAR_TAG).assertExists()
    }

    @Test
    fun `so o integrante de outra maquina ganha o botao de remover`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamUsageUiState.Success(
                members = listOf(
                    member("device-1", "edilson", "DESKTOP-A1", listOf(session("s1", tokens = 10L))),
                    member("device-2", "fantasma", "NOTE-C3", emptyList())
                )
            ),
            localDeviceId = "device-1"
        )

        // Esta máquina voltaria no próximo envio: remover a si mesma só apagaria
        // o próprio histórico sem tirar a linha da lista.
        onNodeWithTag("${TEAM_MEMBER_REMOVE_TAG_PREFIX}device-1").assertDoesNotExist()
        onNodeWithTag("${TEAM_MEMBER_REMOVE_TAG_PREFIX}device-2").assertExists()
    }

    @Test
    fun `remover so emite depois da confirmacao`() = runDesktopComposeUiTest {
        var removed: String? = null

        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    TeamUsageContent(
                        state = TeamUsageUiState.Success(
                            members = listOf(
                                member("device-1", "edilson", "DESKTOP-A1", listOf(session("s1"))),
                                member("device-2", "fantasma", "NOTE-C3", emptyList())
                            )
                        ),
                        language = AppLanguage.PT,
                        onSelectRange = {},
                        onToggleMember = {},
                        localDeviceId = "device-1",
                        onRemoveMember = { deviceId -> removed = deviceId }
                    )
                }
            }
        }

        onNodeWithTag("${TEAM_MEMBER_REMOVE_TAG_PREFIX}device-2").performClick()
        // A ação apaga dados no servidor e não tem desfazer.
        assertEquals(null, removed)

        onNodeWithTag(TEAM_REMOVE_CONFIRM_TAG).performClick()

        assertEquals("device-2", removed)
    }

    @Test
    fun `falha ao remover aparece na tela`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    TeamUsageContent(
                        state = TeamUsageUiState.Success(
                            members = listOf(member("device-1", "edilson", "DESKTOP-A1", listOf(session("s1"))))
                        ),
                        language = AppLanguage.PT,
                        onSelectRange = {},
                        onToggleMember = {},
                        removalError = "HTTP 401"
                    )
                }
            }
        }

        onNodeWithText("Não foi possível remover o integrante: HTTP 401").assertIsDisplayed()
    }

    private fun ComposeUiTest.renderSuccess(
        state: TeamUsageUiState.Success,
        localDeviceId: String? = null
    ) {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    TeamUsageContent(
                        state = state,
                        language = AppLanguage.PT,
                        onSelectRange = {},
                        onToggleMember = {},
                        localDeviceId = localDeviceId
                    )
                }
            }
        }
    }

    private fun ComposeUiTest.onAllNodesWithTextCount(text: String): Int {
        return onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired = false).size
    }

    private fun session(id: String, tokens: Long = 0L, cost: Long = 0L): CliSessionSummary {
        return CliSessionSummary(
            sessionId = id,
            filePath = "",
            firstTs = NOW,
            lastTs = NOW,
            inputTokens = tokens,
            costMicros = cost
        )
    }

    private fun member(
        deviceId: String,
        alias: String,
        hostName: String,
        sessions: List<CliSessionSummary>
    ): TeamMemberUsage {
        return TeamMemberUsage(
            deviceId = deviceId,
            alias = alias,
            hostName = hostName,
            lastSeenAt = NOW,
            sessions = sessions
        )
    }
}
