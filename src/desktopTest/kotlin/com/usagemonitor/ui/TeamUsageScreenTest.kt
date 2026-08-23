package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.entity.CliRangeWindow
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.CliSessionTurn
import com.usagemonitor.domain.entity.CliUsageBreakdown
import com.usagemonitor.domain.entity.CliUsageGroupRow
import com.usagemonitor.domain.entity.TeamMemberTrend
import com.usagemonitor.domain.entity.TeamMemberUsage
import com.usagemonitor.domain.entity.TeamTrendPoint
import com.usagemonitor.domain.entity.TeamUsageSnapshot
import com.usagemonitor.domain.entity.TeamUsageTrend
import com.usagemonitor.domain.entity.toTeamBreakdown
import com.usagemonitor.domain.usecase.CliSessionDetailResult
import com.usagemonitor.domain.usecase.ComputeCliSessionAnalyticsUseCase
import com.usagemonitor.presentation.ui.TEAM_ACCOUNT_GROUP_TAG_PREFIX
import com.usagemonitor.presentation.ui.TEAM_LIST_SCROLLBAR_TAG
import com.usagemonitor.presentation.ui.TEAM_SLIDING_WINDOW_NOTICE_TAG
import com.usagemonitor.presentation.ui.TEAM_TOTAL_MEMBERS_BLOCK_TAG
import com.usagemonitor.presentation.ui.TEAM_MEMBER_HEALTH_TAG_PREFIX
import com.usagemonitor.presentation.ui.TEAM_MEMBER_REMOVE_TAG_PREFIX
import com.usagemonitor.presentation.ui.TEAM_MEMBER_ROW_TAG_PREFIX
import com.usagemonitor.presentation.ui.TEAM_REMOVE_CONFIRM_TAG
import com.usagemonitor.presentation.ui.TEAM_SESSION_REMOVE_CONFIRM_TAG
import com.usagemonitor.presentation.ui.TEAM_SESSION_REMOVE_TAG_PREFIX
import com.usagemonitor.presentation.ui.TEAM_EXPORT_PDF_TAG
import com.usagemonitor.presentation.ui.TEAM_TAB_BREAKDOWN_TAG
import com.usagemonitor.presentation.ui.TEAM_TAB_MEMBERS_TAG
import com.usagemonitor.presentation.ui.TEAM_TAB_TREND_TAG
import com.usagemonitor.presentation.ui.BREAKDOWN_AXIS_TABS_TAG
import com.usagemonitor.presentation.ui.BREAKDOWN_AXIS_TAB_TAG_PREFIX
import com.usagemonitor.presentation.ui.BREAKDOWN_PAGER_TAG
import com.usagemonitor.presentation.ui.BREAKDOWN_PANE_TAG
import com.usagemonitor.presentation.ui.REFRESHING_NOTICE_TAG
import com.usagemonitor.presentation.ui.TeamUsageContent
import com.usagemonitor.presentation.ui.TeamUsageLabels
import com.usagemonitor.presentation.ui.components.TEAM_TREND_CHART_TAG
import com.usagemonitor.presentation.ui.theme.AppTheme
import com.usagemonitor.presentation.viewmodel.TeamSessionDetailUiState
import com.usagemonitor.presentation.viewmodel.TeamUsageUiState
import com.usagemonitor.presentation.viewmodel.TeamUsageView
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
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

        onNode(hasTestTag(TEAM_TOTAL_MEMBERS_BLOCK_TAG) and hasAnyDescendant(hasText("1")))
            .assertIsDisplayed()
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
                expandedMemberKeys = setOf("device-1")
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
        onNode(hasTestTag(TEAM_TOTAL_MEMBERS_BLOCK_TAG) and hasAnyDescendant(hasText("1")))
            .assertIsDisplayed()
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
    fun `modal de uma conta nao mostra nenhuma acao de remocao`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamUsageUiState.Success(
                members = listOf(
                    member("device-1", "edilson", "DESKTOP-A1", listOf(session("s1", tokens = 10L))),
                    member("device-2", "fantasma", "NOTE-C3", emptyList())
                ),
                expandedMemberKeys = setOf("device-1")
            )
        )

        onNodeWithTag("${TEAM_MEMBER_REMOVE_TAG_PREFIX}device-1").assertDoesNotExist()
        onNodeWithTag("${TEAM_MEMBER_REMOVE_TAG_PREFIX}device-2").assertDoesNotExist()
        onNodeWithTag("${TEAM_SESSION_REMOVE_TAG_PREFIX}device-1:s1").assertDoesNotExist()
    }

    /** Issue #66: o administrador também pode apagar o histórico da máquina local. */
    @Test
    fun `visao global permite remover a maquina local`() = runDesktopComposeUiTest {
        var removed: String? = null

        renderSuccess(
            state = TeamUsageUiState.Success(
                members = listOf(
                    member(
                        "device-1",
                        "edilson",
                        "DESKTOP-A1",
                        listOf(session("s1")),
                        accountKey = "account-a",
                        accountLabel = "fulano@empresa.com"
                    )
                ),
                expandedAccountKeys = setOf("account-a"),
                isAdminOverview = true
            ),
            onRemoveMember = { memberKey -> removed = memberKey }
        )

        onNodeWithTag("${TEAM_MEMBER_REMOVE_TAG_PREFIX}device-1").performClick()
        assertEquals(null, removed)

        onNodeWithTag(TEAM_REMOVE_CONFIRM_TAG).performClick()

        assertEquals("account-a/device-1", removed)
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
                                member(
                                    "device-2",
                                    "fantasma",
                                    "NOTE-C3",
                                    listOf(session("s2", tokens = 10L)),
                                    accountKey = "account-a",
                                    accountLabel = "time-a"
                                )
                            ),
                            expandedAccountKeys = setOf("account-a"),
                            isAdminOverview = true
                        ),
                        language = AppLanguage.PT,
                        onSelectRange = {},
                        onToggleMember = {},
                        onRemoveMember = { deviceId -> removed = deviceId }
                    )
                }
            }
        }

        onNodeWithTag("${TEAM_MEMBER_REMOVE_TAG_PREFIX}device-2").performClick()
        // A ação apaga dados no servidor e não tem desfazer.
        assertEquals(null, removed)

        onNodeWithTag(TEAM_REMOVE_CONFIRM_TAG).performClick()

        assertEquals("account-a/device-2", removed)
    }

    @Test
    fun `excluir sessao administrativa exige confirmacao e nao abre detalhe`() =
        runDesktopComposeUiTest {
            var removed: Pair<String, String>? = null
            var opened: Pair<String, String>? = null
            val member = member(
                "device-1",
                "edilson",
                "DESKTOP-A1",
                listOf(session("session-12345678")),
                accountKey = "account-a",
                accountLabel = "time-a"
            )

            setContent {
                AppTheme(isDark = true) {
                    Box(modifier = Modifier.width(1100.dp).height(700.dp)) {
                        TeamUsageContent(
                            state = TeamUsageUiState.Success(
                                members = listOf(member),
                                expandedAccountKeys = setOf("account-a"),
                                expandedMemberKeys = setOf(member.memberKey),
                                isAdminOverview = true
                            ),
                            language = AppLanguage.PT,
                            onSelectRange = {},
                            onToggleMember = {},
                            onOpenSession = { memberKey, sessionId ->
                                opened = memberKey to sessionId
                            },
                            onRemoveSession = { memberKey, sessionId ->
                                removed = memberKey to sessionId
                            }
                        )
                    }
                }
            }

            val tag = "$TEAM_SESSION_REMOVE_TAG_PREFIX${member.memberKey}:session-12345678"
            onNodeWithTag(tag).performClick()
            assertEquals(null, opened)
            assertEquals(null, removed)
            onNodeWithText("Excluir sessão?").assertIsDisplayed()
            onNodeWithText("novos turnos poderão recriá-la", substring = true).assertIsDisplayed()

            onNodeWithTag(TEAM_SESSION_REMOVE_CONFIRM_TAG).performClick()

            assertEquals(member.memberKey to "session-12345678", removed)
            assertEquals(null, opened)
        }

    @Test
    fun `falha ao excluir sessao aparece com mensagem especifica`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    TeamUsageContent(
                        state = TeamUsageUiState.Success(members = emptyList()),
                        language = AppLanguage.PT,
                        onSelectRange = {},
                        onToggleMember = {},
                        sessionRemovalError = "HTTP 401"
                    )
                }
            }
        }

        onNodeWithText("Não foi possível excluir a sessão: HTTP 401").assertIsDisplayed()
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
                            expandedMemberKeys = setOf("device-1")
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

    // ------------------------------------------------------------------------
    // Abas: Integrantes, Resumo e Tendência
    // ------------------------------------------------------------------------

    @Test
    fun `o cabecalho oferece as tres abas`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamUsageUiState.Success(
                members = listOf(member("device-1", "edilson", "DESKTOP-A1", listOf(session("s1"))))
            ),
            width = 1_400.dp
        )

        onNodeWithTag(TEAM_TAB_MEMBERS_TAG).assertIsDisplayed()
        onNodeWithTag(TEAM_TAB_BREAKDOWN_TAG).assertIsDisplayed()
        onNodeWithTag(TEAM_TAB_TREND_TAG).assertIsDisplayed()
        onNodeWithTag(TEAM_TAB_MEMBERS_TAG).assertIsSelected()
    }

    /**
     * O relatorio nao e uma aba: ele e o recorte inteiro da janela, e clicar nele
     * nao pode trocar o que a tela mostra.
     */
    @Test
    fun `o botao de relatorio pede a exportacao sem trocar de aba`() = runDesktopComposeUiTest {
        var selected: TeamUsageView? = null
        var reportRequests = 0

        renderSuccess(
            TeamUsageUiState.Success(
                members = listOf(member("device-1", "edilson", "DESKTOP-A1", listOf(session("s1"))))
            ),
            width = 1_400.dp,
            onSelectView = { view -> selected = view },
            onExportReport = { reportRequests += 1 }
        )

        onNodeWithTag(TEAM_EXPORT_PDF_TAG).performClick()

        assertEquals(1, reportRequests)
        assertEquals(null, selected)
    }

    @Test
    fun `clicar na aba de resumo emite a troca de aba`() = runDesktopComposeUiTest {
        var selected: TeamUsageView? = null

        renderSuccess(
            TeamUsageUiState.Success(
                members = listOf(member("device-1", "edilson", "DESKTOP-A1", listOf(session("s1"))))
            ),
            width = 1_400.dp,
            onSelectView = { view -> selected = view }
        )

        onNodeWithTag(TEAM_TAB_BREAKDOWN_TAG).performClick()

        assertEquals(TeamUsageView.BREAKDOWN, selected)
    }

    @Test
    fun `a aba de resumo mostra os eixos do consumo do time`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamUsageUiState.Success(
                members = listOf(member("device-1", "edilson", "DESKTOP-A1", listOf(session("s1")))),
                view = TeamUsageView.BREAKDOWN,
                breakdown = teamBreakdown()
            ),
            width = 1_400.dp
        )

        onNodeWithTag(BREAKDOWN_PANE_TAG).assertIsDisplayed()
        // Um eixo por aba: a coluna única de antes obrigava a rolar por todos.
        onNodeWithTag("${BREAKDOWN_AXIS_TAB_TAG_PREFIX}MEMBER").assertIsSelected()
        onNodeWithTag("${BREAKDOWN_AXIS_TAB_TAG_PREFIX}PROJECT").assertIsDisplayed()
        // O eixo por integrante é o que só existe aqui: no resumo da máquina a
        // máquina é uma só e a pergunta não faria sentido. Ele abre primeiro.
        onNodeWithText("edilson").assertIsDisplayed()
        assertEquals(0, onAllNodesWithTextCount("alpha"))

        onNodeWithTag("${BREAKDOWN_AXIS_TAB_TAG_PREFIX}PROJECT").performClick()

        onNodeWithText("alpha").assertIsDisplayed()
    }

    /** O mesmo Resumo, na janela que mistura todas as contas. */
    @Test
    fun `a visao global tambem pagina o resumo por eixo`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamUsageUiState.Success(
                members = twoAccountMembers(),
                isAdminOverview = true,
                view = TeamUsageView.BREAKDOWN,
                breakdown = teamBreakdown()
            ),
            width = 1_400.dp
        )

        onNodeWithTag(BREAKDOWN_PANE_TAG).assertIsDisplayed()
        onNodeWithTag(BREAKDOWN_AXIS_TABS_TAG).assertIsDisplayed()
        onNodeWithTag(BREAKDOWN_PAGER_TAG).assertIsDisplayed()
    }

    @Test
    fun `trocar a janela avisa que os numeros ainda sao os antigos`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamUsageUiState.Success(
                members = listOf(member("device-1", "edilson", "DESKTOP-A1", listOf(session("s1")))),
                isRefreshing = true
            ),
            width = 1_400.dp
        )

        onNodeWithTag(REFRESHING_NOTICE_TAG).assertIsDisplayed()
    }

    @Test
    fun `sem recarga em voo nao ha aviso de atualizacao`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamUsageUiState.Success(
                members = listOf(member("device-1", "edilson", "DESKTOP-A1", listOf(session("s1"))))
            ),
            width = 1_400.dp
        )

        assertEquals(0, onAllNodesWithTag(REFRESHING_NOTICE_TAG).fetchSemanticsNodes(false).size)
    }

    /**
     * Duas máquinas com o mesmo apelido derrubavam a janela: dois baldes com o
     * rótulo "SUETONIO" repetiam a chave do `LazyColumn`.
     */
    @Test
    fun `a aba de resumo aguenta dois integrantes com o mesmo apelido`() =
        runDesktopComposeUiTest {
            renderSuccess(
                TeamUsageUiState.Success(
                    members = listOf(member("device-1", "SUETONIO", "devmachine", listOf(session("s1")))),
                    view = TeamUsageView.BREAKDOWN,
                    breakdown = sameAliasBreakdown()
                ),
                width = 1_400.dp,
                height = 1_100.dp
            )

            onNodeWithTag(BREAKDOWN_PANE_TAG).assertIsDisplayed()
            onNodeWithText("SUETONIO (device-a)").assertIsDisplayed()
            onNodeWithText("SUETONIO (device-b)").assertIsDisplayed()
        }

    /** Issue #57: o gráfico ocupava metade do modal sem ninguém o ter pedido. */
    @Test
    fun `a tendencia nao aparece na aba de integrantes`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamUsageUiState.Success(
                members = listOf(member("device-1", "edilson", "DESKTOP-A1", listOf(session("s1")))),
                trend = trend()
            ),
            width = 1_400.dp
        )

        assertEquals(0, onAllNodesWithTag(TEAM_TREND_CHART_TAG).fetchSemanticsNodes(false).size)
    }

    @Test
    fun `a aba de tendencia mostra o grafico com a explicacao do que ele mede`() =
        runDesktopComposeUiTest {
            renderSuccess(
                TeamUsageUiState.Success(
                    members = listOf(member("device-1", "edilson", "DESKTOP-A1", listOf(session("s1")))),
                    view = TeamUsageView.TREND,
                    trend = trend()
                ),
                width = 1_400.dp
            )

            onNodeWithTag(TEAM_TREND_CHART_TAG).assertIsDisplayed()
            // O que faltava na issue #57: o painel dizia como ler, nunca o que é.
            onNodeWithText(
                TeamUsageLabels.trendHint(2, AppLanguage.PT)
            ).assertIsDisplayed()
        }

    @Test
    fun `a aba de tendencia sem serie diz que o servidor nao a serve`() =
        runDesktopComposeUiTest {
            renderSuccess(
                TeamUsageUiState.Success(
                    members = listOf(member("device-1", "edilson", "DESKTOP-A1", listOf(session("s1")))),
                    view = TeamUsageView.TREND
                ),
                width = 1_400.dp
            )

            onNodeWithText(TeamUsageLabels.trendUnavailable(AppLanguage.PT)).assertIsDisplayed()
        }

    @Test
    fun `a visao global nao mostra a aba de tendencia`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamUsageUiState.Success(
                members = twoAccountMembers(),
                isAdminOverview = true,
                expandedAccountKeys = setOf("account-a")
            ),
            width = 1_400.dp
        )

        // A série é por conta; ali a janela mistura várias e ela nunca é lida.
        assertEquals(0, onAllNodesWithTag(TEAM_TAB_TREND_TAG).fetchSemanticsNodes(false).size)
        onNodeWithTag(TEAM_TAB_BREAKDOWN_TAG).assertIsDisplayed()
    }

    private fun teamBreakdown(): CliUsageBreakdown {
        val snapshot = TeamUsageSnapshot(
            members = listOf(
                TeamMemberUsage(
                    deviceId = "device-1",
                    alias = "edilson",
                    sessions = listOf(session("s1")),
                    groupRows = listOf(
                        CliUsageGroupRow(
                            sessionId = "s1",
                            cwd = "/home/dev/alpha",
                            gitBranch = "main",
                            model = OPUS,
                            turnCount = 2,
                            inputTokens = 1_000_000L
                        )
                    )
                )
            )
        )
        return snapshot.toTeamBreakdown(window = CliRangeWindow(), now = NOW)
    }

    /** Duas máquinas distintas com o mesmo apelido, como no time real. */
    private fun sameAliasBreakdown(): CliUsageBreakdown {
        val snapshot = TeamUsageSnapshot(
            members = listOf("device-aaaaaaaa1111", "device-bbbbbbbb2222").mapIndexed { index, deviceId ->
                TeamMemberUsage(
                    deviceId = deviceId,
                    alias = "SUETONIO",
                    hostName = "devmachine",
                    sessions = listOf(session("s$index")),
                    groupRows = listOf(
                        CliUsageGroupRow(
                            sessionId = "s$index",
                            cwd = "/home/dev/alpha",
                            model = OPUS,
                            turnCount = 1,
                            inputTokens = 1_000_000L * (index + 1)
                        )
                    )
                )
            }
        )
        return snapshot.toTeamBreakdown(window = CliRangeWindow(), now = NOW)
    }

    private fun trend(): TeamUsageTrend {
        val days = listOf(LocalDate(2026, 8, 15), LocalDate(2026, 8, 16))
        return TeamUsageTrend(
            days = days,
            members = listOf(
                TeamMemberTrend(
                    deviceId = "device-1",
                    alias = "edilson",
                    points = days.mapIndexed { index, date ->
                        TeamTrendPoint(date = date, costMicros = 1_000_000L * (index + 1), turnCount = 1)
                    }
                )
            )
        )
    }

    private fun ComposeUiTest.renderSuccess(
        state: TeamUsageUiState.Success,
        width: androidx.compose.ui.unit.Dp = 900.dp,
        height: androidx.compose.ui.unit.Dp = 700.dp,
        onToggleAccount: (String) -> Unit = {},
        onSelectView: (TeamUsageView) -> Unit = {},
        onExportReport: () -> Unit = {},
        onRemoveMember: (String) -> Unit = {},
        onRemoveSession: (String, String) -> Unit = { _, _ -> }
    ) {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(width).height(height)) {
                    TeamUsageContent(
                        state = state,
                        language = AppLanguage.PT,
                        onSelectRange = {},
                        onToggleMember = {},
                        onToggleAccount = onToggleAccount,
                        onSelectView = onSelectView,
                        onExportReport = onExportReport,
                        onRemoveMember = onRemoveMember,
                        onRemoveSession = onRemoveSession
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

    /** Duas contas na visão global: A com duas máquinas, B com uma. */
    private fun twoAccountMembers(): List<TeamMemberUsage> {
        return listOf(
            member(
                "device-1",
                "edilson",
                "DESKTOP-A1",
                listOf(
                    session("aaaaaa0123", tokens = 300L, cost = 1_000_000L),
                    session("aaaaaa0456", tokens = 200L, cost = 500_000L)
                ),
                accountKey = "account-a",
                accountLabel = "fulano@empresa.com"
            ),
            member(
                "device-2",
                "romero",
                "NOTE-LAT-015",
                listOf(session("aaaaaa0789", tokens = 500L, cost = 2_500_000L)),
                accountKey = "account-a",
                accountLabel = "fulano@empresa.com"
            ),
            member(
                "device-3",
                "helio",
                "DESKTOP-B1",
                listOf(session("bbbbbb0123", tokens = 100L, cost = 100_000L)),
                accountKey = "account-b"
            )
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
        sessions: List<CliSessionSummary>,
        accountKey: String? = null,
        accountLabel: String? = null
    ): TeamMemberUsage {
        return TeamMemberUsage(
            deviceId = deviceId,
            alias = alias,
            hostName = hostName,
            lastSeenAt = NOW,
            sessions = sessions,
            accountKey = accountKey,
            accountLabel = accountLabel
        )
    }

    /**
     * A linha do integrante encolheu de 109dp para 88dp na refatoração visual:
     * ela deixou de ser um card com padding próprio e virou linha de tabela. O
     * assert continua existindo pelo mesmo motivo de antes — a linha não pode
     * voltar a crescer —, com o número novo.
     */
    @Test
    fun `faixa da conta cresce e linha do integrante fica compacta em 960dp`() =
        runDesktopComposeUiTest {
            renderSuccess(
                TeamUsageUiState.Success(
                    members = listOf(
                        member(
                            "device-1",
                            "edilson",
                            "DESKTOP-A1",
                            listOf(session("abcdef0123", tokens = 500L)),
                            accountKey = "account-a",
                            accountLabel = "fulano@empresa.com"
                        )
                    ),
                    expandedAccountKeys = setOf("account-a"),
                    isAdminOverview = true
                ),
                width = 960.dp
            )

            // 62 e não 57: a faixa ganhou a palavra "Conta" acima do e-mail
            // (issue #69). Cinco dp é o preço de dizer o que a linha é; o assert
            // continua existindo para ela não voltar a crescer sem motivo.
            onNodeWithTag("${TEAM_ACCOUNT_GROUP_TAG_PREFIX}account-a")
                .assertHeightIsEqualTo(62.dp)
            onNodeWithTag("${TEAM_MEMBER_ROW_TAG_PREFIX}device-1")
                .assertHeightIsEqualTo(88.dp)
        }

    @Test
    fun `visao global agrupa por conta e mostra o uuid ao lado do rotulo`() =
        runDesktopComposeUiTest {
            renderSuccess(
                TeamUsageUiState.Success(
                    members = listOf(
                        member(
                            "device-1",
                            "edilson",
                            "DESKTOP-A1",
                            listOf(session("abcdef0123", tokens = 500L)),
                            accountKey = "account-a",
                            accountLabel = "fulano@empresa.com"
                        ),
                        member(
                            "device-2",
                            "helio",
                            "DESKTOP-B1",
                            listOf(session("bbbbbb0123", tokens = 100L)),
                            accountKey = "account-b"
                        )
                    ),
                    isAdminOverview = true
                )
            )

            onNodeWithTag("${TEAM_ACCOUNT_GROUP_TAG_PREFIX}account-a").assertIsDisplayed()
            onNodeWithTag("${TEAM_ACCOUNT_GROUP_TAG_PREFIX}account-b").assertIsDisplayed()
            // O rótulo é texto que o admin digitou; o uuid ao lado é o que prova.
            onNodeWithText("fulano@empresa.com").assertIsDisplayed()
            onNodeWithText("account-a").assertIsDisplayed()
            // Conta sem chave emitida não fica sem identificação na tela.
            onNodeWithText("Conta sem chave").assertIsDisplayed()
        }

    /**
     * Issue #69: a faixa entregava um e-mail e um uuid sem dizer o que eles
     * eram, e ao lado de uma linha de integrante — que também tem nome e
     * identificador — as duas liam igual.
     */
    @Test
    fun `a faixa da conta se anuncia como conta`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamUsageUiState.Success(
                members = twoAccountMembers(),
                isAdminOverview = true
            ),
            width = 1_200.dp
        )

        onNodeWithTag("${TEAM_ACCOUNT_GROUP_TAG_PREFIX}account-a")
            .assertIsDisplayed()
            .assertTextContains("Conta")
    }

    /**
     * Issue #45: sem o totalizador, comparar duas contas exige somar as linhas
     * de cada uma na mão — o único total da tela é o do cabeçalho, que já
     * mistura todas as contas.
     */
    @Test
    fun `faixa da conta totaliza os integrantes dela na visao global`() =
        runDesktopComposeUiTest {
            renderSuccess(
                TeamUsageUiState.Success(
                    members = twoAccountMembers(),
                    isAdminOverview = true
                ),
                width = 1_200.dp
            )

            // Números da conta A: 2 das 3 máquinas, 3 das 4 sessões e o custo
            // somado delas. A asserção é sobre a faixa, não sobre a tela: é ela
            // que prova que o número está na conta certa.
            onNodeWithTag("${TEAM_ACCOUNT_GROUP_TAG_PREFIX}account-a")
                .assertIsDisplayed()
                .assertTextContains("2")
                .assertTextContains("3 sessões")
                .assertTextContains("$4.0000")

            // Conta B tem uma máquina só; o total dela não some no da conta A.
            onNodeWithTag("${TEAM_ACCOUNT_GROUP_TAG_PREFIX}account-b")
                .assertIsDisplayed()
                .assertTextContains("1")
                .assertTextContains("$0.1000")

            // O cabeçalho continua somando tudo: nenhuma faixa o substitui.
            onNode(hasTestTag(TEAM_TOTAL_MEMBERS_BLOCK_TAG) and hasAnyDescendant(hasText("3")))
                .assertIsDisplayed()
            onNodeWithText("4 sessões").assertIsDisplayed()
        }

    /**
     * Issue #45: a visão global nasce recolhida — a faixa de cada conta é a
     * lista inteira até alguém pedir o detalhe de uma delas.
     */
    @Test
    fun `visao global nasce recolhida e a faixa abre a conta clicada`() =
        runDesktopComposeUiTest {
            val toggled = mutableListOf<String>()
            renderSuccess(
                TeamUsageUiState.Success(
                    members = twoAccountMembers(),
                    isAdminOverview = true
                ),
                width = 1_200.dp,
                onToggleAccount = { groupKey -> toggled += groupKey }
            )

            // Nenhum integrante na tela; só as duas faixas.
            onAllNodesWithText("edilson").assertCountEquals(0)
            onAllNodesWithText("helio").assertCountEquals(0)
            onNodeWithTag("${TEAM_ACCOUNT_GROUP_TAG_PREFIX}account-a").assertIsDisplayed()

            onNodeWithTag("${TEAM_ACCOUNT_GROUP_TAG_PREFIX}account-a").performClick()
            assertEquals(listOf("account-a"), toggled)
        }

    @Test
    fun `conta expandida mostra so os integrantes dela`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamUsageUiState.Success(
                members = twoAccountMembers(),
                expandedAccountKeys = setOf("account-a"),
                isAdminOverview = true
            ),
            width = 1_200.dp
        )

        onNodeWithText("edilson").assertIsDisplayed()
        // A conta B continua recolhida: a faixa dela aparece, a máquina não.
        onAllNodesWithText("helio").assertCountEquals(0)
        onNodeWithTag("${TEAM_ACCOUNT_GROUP_TAG_PREFIX}account-b").assertIsDisplayed()
    }

    @Test
    fun `modal de uma conta nao desenha cabecalho de grupo`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamUsageUiState.Success(
                members = listOf(
                    member("device-1", "edilson", "DESKTOP-A1", listOf(session("abcdef0123")))
                )
            )
        )

        onAllNodesWithTag("${TEAM_ACCOUNT_GROUP_TAG_PREFIX}").assertCountEquals(0)
    }

    @Test
    fun `visao global avisa que o recorte de 5h e deslizante`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamUsageUiState.Success(
                members = listOf(
                    member(
                        "device-1",
                        "edilson",
                        "DESKTOP-A1",
                        listOf(session("abcdef0123", tokens = 500L)),
                        accountKey = "account-a",
                        accountLabel = "fulano@empresa.com"
                    )
                ),
                range = CliSessionRange.LAST_5H,
                isAdminOverview = true
            )
        )

        onNodeWithTag(TEAM_SLIDING_WINDOW_NOTICE_TAG).assertIsDisplayed()
    }

    @Test
    fun `mesma maquina em duas contas expande so a linha clicada`() = runDesktopComposeUiTest {
        renderSuccess(
            TeamUsageUiState.Success(
                members = listOf(
                    member(
                        "device-1",
                        "edilson",
                        "DESKTOP-A1",
                        listOf(session("aaaaaa0123", tokens = 500L)),
                        accountKey = "account-a",
                        accountLabel = "fulano@empresa.com"
                    ),
                    member(
                        "device-1",
                        "edilson",
                        "DESKTOP-A1",
                        listOf(session("bbbbbb0123", tokens = 100L)),
                        accountKey = "account-b",
                        accountLabel = "sicrano@empresa.com"
                    )
                ),
                // A chave é `accountKey/deviceId`: só a linha da conta A abre.
                expandedMemberKeys = setOf("account-a/device-1"),
                expandedAccountKeys = setOf("account-a", "account-b"),
                isAdminOverview = true
            )
        )

        onNodeWithText("aaaaaa01").assertIsDisplayed()
        onAllNodesWithText("bbbbbb01").assertCountEquals(0)
    }
}
