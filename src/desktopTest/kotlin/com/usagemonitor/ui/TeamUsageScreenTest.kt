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
import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.CliSessionTurn
import com.usagemonitor.domain.entity.TeamMemberUsage
import com.usagemonitor.domain.usecase.CliSessionDetailResult
import com.usagemonitor.domain.usecase.ComputeCliSessionAnalyticsUseCase
import com.usagemonitor.presentation.ui.TEAM_LIST_SCROLLBAR_TAG
import com.usagemonitor.presentation.ui.TEAM_MEMBER_HEALTH_TAG_PREFIX
import com.usagemonitor.presentation.ui.TEAM_MEMBER_REMOVE_TAG_PREFIX
import com.usagemonitor.presentation.ui.TEAM_MEMBER_ROW_TAG_PREFIX
import com.usagemonitor.presentation.ui.TEAM_REMOVE_CONFIRM_TAG
import com.usagemonitor.presentation.ui.TeamUsageContent
import com.usagemonitor.presentation.ui.theme.AppTheme
import com.usagemonitor.presentation.viewmodel.TeamSessionDetailUiState
import com.usagemonitor.presentation.viewmodel.TeamUsageUiState
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

private val NOW = Instant.parse("2026-08-11T12:00:00Z")

/** Janela de contexto de 1M — a única forma de a saturação ser conhecida. */
private const val OPUS = "claude-opus-5"

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

    /**
     * Issue #35: reset vencido ancora a janela nova no próprio reset, e o fim
     * dela só existe quando a API publicar o `resets_at` seguinte.
     */
    @Test
    fun `header nomeia a janela aberta pelo reset quando o fim e desconhecido`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamUsageUiState.Success(
                members = listOf(
                    member("device-1", "edilson", "DESKTOP-A1", listOf(session("s1", tokens = 2_000L)))
                ),
                rangeEndsAt = null,
                rangeAnchored = true
            )
        )

        onNodeWithText("custo estimado · janela 5h desde o reinício").assertIsDisplayed()
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

    @Test
    fun `clicar numa sessao do time abre o detalhe dela`() = runDesktopComposeUiTest {
        var opened: Pair<String, String>? = null

        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(1_200.dp).height(700.dp)) {
                    TeamUsageContent(
                        state = TeamUsageUiState.Success(
                            members = listOf(
                                member(
                                    "device-1",
                                    "edilson",
                                    "DESKTOP-A1",
                                    listOf(session("abcdef0123", tokens = 500L))
                                )
                            ),
                            expandedDeviceIds = setOf("device-1")
                        ),
                        language = AppLanguage.PT,
                        onSelectRange = {},
                        onToggleMember = {},
                        onOpenSession = { deviceId, sessionId -> opened = deviceId to sessionId }
                    )
                }
            }
        }

        onNodeWithText("abcdef01").performClick()

        // O transcript é de outra máquina, mas os turnos estão no servidor: o
        // clique que antes era morto agora pede o detalhe daquela máquina.
        assertEquals("device-1" to "abcdef0123", opened)
    }

    @Test
    fun `o detalhe do time mostra o mesmo painel do modal local`() = runDesktopComposeUiTest {
        val summary = session("abcdef0123", tokens = 40_000L, cost = 5_000_000L).copy(
            hostName = "NOTE-B2",
            cwd = "/home/dev/api-gateway",
            gitBranch = "main",
            primaryModel = OPUS
        )
        val detail = CliSessionDetail(
            summary = summary,
            turns = listOf(turn(seq = 0, cacheReadTokens = 10_000L), turn(seq = 1, cacheReadTokens = 30_000L))
        )

        renderSuccess(
            TeamUsageUiState.Success(
                members = listOf(member("device-1", "edilson", "NOTE-B2", listOf(summary))),
                detail = TeamSessionDetailUiState.Ready(
                    deviceId = "device-1",
                    sessionId = summary.sessionId,
                    result = CliSessionDetailResult(
                        detail = detail,
                        analytics = ComputeCliSessionAnalyticsUseCase().invoke(detail)
                    )
                )
            )
        )

        onNodeWithText("Sessão saudável").assertIsDisplayed()
        // A máquina do integrante chega ao card de metadados do detalhe.
        onNodeWithText("NOTE-B2").assertIsDisplayed()
        onNodeWithText("/home/dev/api-gateway").assertIsDisplayed()
        // Paridade total: o gráfico por turno é o mesmo do modal da própria máquina.
        onNodeWithText("Contexto por turno").assertExists()
        onNodeWithText("Avançado").assertExists()
    }

    @Test
    fun `servidor sem os turnos avisa e esconde os graficos por turno`() = runDesktopComposeUiTest {
        val summary = session("abcdef0123", tokens = 40_000L, cost = 5_000_000L).copy(primaryModel = OPUS)

        renderSuccess(
            TeamUsageUiState.Success(
                members = listOf(member("device-1", "edilson", "DESKTOP-A1", listOf(summary))),
                detail = TeamSessionDetailUiState.Ready(
                    deviceId = "device-1",
                    sessionId = summary.sessionId,
                    result = CliSessionDetailResult(
                        detail = CliSessionDetail(summary = summary, turns = emptyList()),
                        analytics = ComputeCliSessionAnalyticsUseCase().fromSummary(summary)
                    ),
                    turnsUnavailable = true
                )
            )
        )

        // Um gráfico vazio se leria como sessão sem atividade; o aviso diz o que
        // falta e o que fazer.
        onNodeWithText("Contexto por turno").assertDoesNotExist()
        onNodeWithText("Avançado").assertDoesNotExist()
        onNodeWithText(
            "Este servidor de time não devolve os turnos desta sessão (versão anterior a 0.2.0 " +
                "ou sessão já expirada na retenção). Só os agregados do período estão disponíveis; " +
                "os gráficos por turno voltam depois de atualizar o servidor."
        ).assertIsDisplayed()
        // O que o agregado prova continua na tela.
        onNodeWithText("Sessão saudável").assertIsDisplayed()
    }

    @Test
    fun `o botao voltar do detalhe fecha o painel`() = runDesktopComposeUiTest {
        var closed = false

        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    TeamUsageContent(
                        state = TeamUsageUiState.Success(
                            members = listOf(
                                member("device-1", "edilson", "DESKTOP-A1", listOf(session("abcdef0123")))
                            ),
                            detail = TeamSessionDetailUiState.Loading("device-1", "abcdef0123")
                        ),
                        language = AppLanguage.PT,
                        onSelectRange = {},
                        onToggleMember = {},
                        onCloseDetail = { closed = true }
                    )
                }
            }
        }

        onNodeWithText("Voltar").performClick()

        assertEquals(true, closed)
    }

    @Test
    fun `a linha do integrante recolhido mostra o pior status das sessoes dele`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamUsageUiState.Success(
                members = listOf(
                    member(
                        "device-1",
                        "edilson",
                        "DESKTOP-A1",
                        listOf(
                            // 650K de contexto vivo numa janela de 1M e 10K noutra.
                            ratedSession("s1", liveContextTokens = 650_000L),
                            ratedSession("s2", liveContextTokens = 10_000L)
                        )
                    )
                )
            ),
            width = 1_400.dp
        )

        // Recolhido: sem isso a sessão saturada ficaria atrás de um clique que
        // ninguém dá, porque nada na linha indicaria que vale a pena.
        //
        // Árvore não mesclada: a linha do integrante é clicável e absorve a
        // semântica dos filhos, então o tag interno só existe aqui.
        onNodeWithTag("${TEAM_MEMBER_HEALTH_TAG_PREFIX}device-1", useUnmergedTree = true).assertExists()
        onNodeWithText("Saturada").assertIsDisplayed()
        assertEquals(0, onAllNodesWithTextCount("Saudável"))
    }

    @Test
    fun `integrante so com sessao de janela desconhecida nao ganha status`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamUsageUiState.Success(
                members = listOf(
                    member(
                        "device-1",
                        "edilson",
                        "DESKTOP-A1",
                        listOf(
                            session("s1").copy(
                                primaryModel = "claude-3-5-sonnet",
                                liveContextModel = "claude-3-5-sonnet",
                                liveContextTokens = 10_000L
                            )
                        )
                    )
                ),
                range = CliSessionRange.LAST_5H
            ),
            width = 1_400.dp
        )

        // Sem a janela do modelo não há fração, e chutar um veredito seria pior
        // que não dar nenhum.
        onNodeWithTag("${TEAM_MEMBER_HEALTH_TAG_PREFIX}device-1", useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun `o cabecalho conta as sessoes saturadas e em atencao do time`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamUsageUiState.Success(
                members = listOf(
                    member(
                        "device-1",
                        "edilson",
                        "DESKTOP-A1",
                        listOf(
                            ratedSession("s1", liveContextTokens = 650_000L),
                            ratedSession("s2", liveContextTokens = 450_000L)
                        )
                    ),
                    member(
                        "device-2",
                        "maria",
                        "NOTE-B2",
                        listOf(ratedSession("s3", liveContextTokens = 450_000L))
                    )
                )
            ),
            width = 1_400.dp
        )

        onNodeWithText("1 saturada · 2 em atenção").assertIsDisplayed()
    }

    @Test
    fun `cabecalho sem sessao problematica nao mostra contagem`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamUsageUiState.Success(
                members = listOf(
                    member(
                        "device-1",
                        "edilson",
                        "DESKTOP-A1",
                        listOf(ratedSession("s1", liveContextTokens = 10_000L))
                    )
                )
            ),
            width = 1_400.dp
        )

        // Um alerta que não existe não deve ocupar linha no cabeçalho.
        assertEquals(0, onAllNodesWithTextCount("0 saturadas"))
        assertEquals(0, onAllNodesWithTextCount("0 em atenção"))
    }

    private fun ComposeUiTest.renderSuccess(
        state: TeamUsageUiState.Success,
        localDeviceId: String? = null,
        width: androidx.compose.ui.unit.Dp = 900.dp
    ) {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(width).height(700.dp)) {
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

    /** Sessão com janela de contexto conhecida: só assim há veredito de saúde. */
    private fun ratedSession(id: String, liveContextTokens: Long): CliSessionSummary {
        return session(id, tokens = 10L).copy(
            primaryModel = OPUS,
            liveContextModel = OPUS,
            liveContextTokens = liveContextTokens
        )
    }

    private fun turn(seq: Int, cacheReadTokens: Long): CliSessionTurn {
        return CliSessionTurn(
            sessionId = "abcdef0123",
            seq = seq,
            messageId = "msg-$seq",
            ts = NOW,
            model = OPUS,
            cacheReadTokens = cacheReadTokens
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
