package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.CliSessionTurn
import com.usagemonitor.domain.usecase.CliSessionDetailResult
import com.usagemonitor.domain.usecase.ComputeCliSessionAnalyticsUseCase
import com.usagemonitor.presentation.ui.CliSessionsContent
import com.usagemonitor.presentation.ui.theme.AppTheme
import com.usagemonitor.presentation.viewmodel.CliSessionDetailUiState
import com.usagemonitor.presentation.viewmodel.CliSessionsUiState
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class CliSessionsScreenTest {

    @Test
    fun `list shows session count total tokens and total cost`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    CliSessionsContent(
                        state = CliSessionsUiState.Success(
                            sessions = listOf(summary("session-abcdef01", costMicros = 1_230_000L))
                        ),
                        language = AppLanguage.PT,
                        onRefresh = {},
                        onSelectRange = {},
                        onOpenSession = {},
                        onCloseDetail = {}
                    )
                }
            }
        }

        onNodeWithText("1 sessão").assertIsDisplayed()
        onNodeWithText("session-").assertIsDisplayed()
        onNodeWithText("\$1.23").assertIsDisplayed()
        onNodeWithText("custo estimado · últimas 5h").assertIsDisplayed()
    }

    @Test
    fun `header names the end of the quota window when the cutoff is anchored`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    CliSessionsContent(
                        state = CliSessionsUiState.Success(
                            sessions = listOf(summary("session-abcdef01")),
                            rangeEndsAt = Instant.parse("2026-08-11T03:30:00Z"),
                            rangeAnchored = true
                        ),
                        language = AppLanguage.PT,
                        onRefresh = {},
                        onSelectRange = {},
                        onOpenSession = {},
                        onCloseDetail = {}
                    )
                }
            }
        }

        // 03:30 UTC = 00:30 BRT, o reinício mostrado no card do dashboard.
        onNodeWithText("custo estimado · janela 5h até 11/08 00:30 BRT").assertIsDisplayed()
    }

    @Test
    fun `empty list inside a quota window says so`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    CliSessionsContent(
                        state = CliSessionsUiState.Success(
                            sessions = emptyList(),
                            rangeEndsAt = Instant.parse("2026-08-11T03:30:00Z"),
                            rangeAnchored = true
                        ),
                        language = AppLanguage.PT,
                        onRefresh = {},
                        onSelectRange = {},
                        onOpenSession = {},
                        onCloseDetail = {}
                    )
                }
            }
        }

        onNodeWithText("Nenhuma sessão nesta janela de quota (5h). Escolha uma janela maior.")
            .assertIsDisplayed()
    }

    @Test
    fun `header totals the tokens of the listed sessions`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    CliSessionsContent(
                        state = CliSessionsUiState.Success(
                            sessions = listOf(summary("session-abcdef01"), summary("session-abcdef02"))
                        ),
                        language = AppLanguage.PT,
                        onRefresh = {},
                        onSelectRange = {},
                        onOpenSession = {},
                        onCloseDetail = {}
                    )
                }
            }
        }

        // 41K por sessão nas linhas; só o header mostra a soma das duas.
        onNodeWithText("82K").assertIsDisplayed()
    }

    @Test
    fun `header names the account the sessions belong to`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    CliSessionsContent(
                        state = CliSessionsUiState.Success(
                            sessions = listOf(summary("session-abcdef01")),
                            profileLabel = "INFORMATA2"
                        ),
                        language = AppLanguage.PT,
                        onRefresh = {},
                        onSelectRange = {},
                        onOpenSession = {},
                        onCloseDetail = {}
                    )
                }
            }
        }

        onNodeWithText("INFORMATA2").assertIsDisplayed()
    }

    @Test
    fun `header offers every window with the active one selected`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    CliSessionsContent(
                        state = CliSessionsUiState.Success(
                            sessions = listOf(summary("session-abcdef01")),
                            range = CliSessionRange.LAST_7D
                        ),
                        language = AppLanguage.PT,
                        onRefresh = {},
                        onSelectRange = {},
                        onOpenSession = {},
                        onCloseDetail = {}
                    )
                }
            }
        }

        onNodeWithText("5h").assertIsDisplayed()
        onNodeWithText("30 dias").assertIsDisplayed()
        onNodeWithText("Total").assertIsDisplayed()
        onNodeWithText("7 dias").assertIsSelected()
    }

    @Test
    fun `clicking a window chip reports the selection`() = runDesktopComposeUiTest {
        val selected = mutableListOf<CliSessionRange>()

        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    CliSessionsContent(
                        state = CliSessionsUiState.Success(sessions = listOf(summary("session-abcdef01"))),
                        language = AppLanguage.PT,
                        onRefresh = {},
                        onSelectRange = { range -> selected.add(range) },
                        onOpenSession = {},
                        onCloseDetail = {}
                    )
                }
            }
        }

        onNodeWithText("Total").performClick()

        assertEquals(listOf(CliSessionRange.ALL), selected)
    }

    @Test
    fun `the list header has no back button`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    CliSessionsContent(
                        state = CliSessionsUiState.Success(sessions = listOf(summary("session-abcdef01"))),
                        language = AppLanguage.PT,
                        onRefresh = {},
                        onSelectRange = {},
                        onOpenSession = {},
                        onCloseDetail = {}
                    )
                }
            }
        }

        // O fechamento da janela é do title bar; um "Voltar" aqui não teria destino.
        onNodeWithText("Voltar").assertDoesNotExist()
        onNodeWithText("Atualizar").assertIsDisplayed()
    }

    @Test
    fun `empty list with an active window points at the filter`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    CliSessionsContent(
                        state = CliSessionsUiState.Success(sessions = emptyList()),
                        language = AppLanguage.PT,
                        onRefresh = {},
                        onSelectRange = {},
                        onOpenSession = {},
                        onCloseDetail = {}
                    )
                }
            }
        }

        onNodeWithText("Nenhuma sessão com atividade nas últimas 5h. Escolha uma janela maior.")
            .assertIsDisplayed()
    }

    @Test
    fun `empty list without a window explains where transcripts are read from`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    CliSessionsContent(
                        state = CliSessionsUiState.Success(
                            sessions = emptyList(),
                            range = CliSessionRange.ALL
                        ),
                        language = AppLanguage.PT,
                        onRefresh = {},
                        onSelectRange = {},
                        onOpenSession = {},
                        onCloseDetail = {}
                    )
                }
            }
        }

        onNodeWithText("Nenhuma sessão do Claude Code encontrada em ~/.claude/projects.").assertIsDisplayed()
    }

    @Test
    fun `detail renders the analytics sections`() = runDesktopComposeUiTest {
        val summary = summary("session-abcdef01", costMicros = 5_000_000L)
        val detail = CliSessionDetail(
            summary = summary,
            turns = listOf(
                turn(seq = 1, cacheReadTokens = 10_000L, cacheWrite1hTokens = 4_000L),
                turn(seq = 2, cacheReadTokens = 30_000L, cacheWrite5mTokens = 2_000L)
            )
        )

        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    CliSessionsContent(
                        state = CliSessionsUiState.Success(
                            sessions = listOf(summary),
                            detail = CliSessionDetailUiState.Ready(
                                sessionId = summary.sessionId,
                                result = CliSessionDetailResult(
                                    detail = detail,
                                    analytics = ComputeCliSessionAnalyticsUseCase().invoke(detail)
                                )
                            )
                        ),
                        language = AppLanguage.PT,
                        onRefresh = {},
                        onSelectRange = {},
                        onOpenSession = {},
                        onCloseDetail = {}
                    )
                }
            }
        }

        onNodeWithText("Taxa de acerto de cache").assertIsDisplayed()
        onNodeWithText("Distribuição de custo").assertIsDisplayed()
        // Abaixo da dobra na altura de teste: basta existirem na árvore.
        onNodeWithText("Economia do cache").assertExists()
        onNodeWithText("Contexto por turno").assertExists()
        onNodeWithText("Custo x economia acumulados").assertExists()
    }

    @Test
    fun `detail shows where the session ran`() = runDesktopComposeUiTest {
        val summary = summary("session-abcdef01").copy(
            hostName = "DESKTOP-EDILS",
            gitBranch = "feat/cli-sessions-per-account"
        )
        val detail = CliSessionDetail(summary = summary, turns = listOf(turn(seq = 1)))

        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    CliSessionsContent(
                        state = CliSessionsUiState.Success(
                            sessions = listOf(summary),
                            detail = CliSessionDetailUiState.Ready(
                                sessionId = summary.sessionId,
                                result = CliSessionDetailResult(
                                    detail = detail,
                                    analytics = ComputeCliSessionAnalyticsUseCase().invoke(detail)
                                )
                            )
                        ),
                        language = AppLanguage.PT,
                        onRefresh = {},
                        onSelectRange = {},
                        onOpenSession = {},
                        onCloseDetail = {}
                    )
                }
            }
        }

        onNodeWithText("Máquina").assertIsDisplayed()
        onNodeWithText("DESKTOP-EDILS").assertIsDisplayed()
        // Caminho completo do projeto, não só o último segmento da lista.
        onNodeWithText("/workspace/usage-monitor").assertIsDisplayed()
        onNodeWithText("feat/cli-sessions-per-account").assertIsDisplayed()
        onNodeWithText("01/08 07:00 BRT → 01/08 08:00 BRT").assertIsDisplayed()
    }

    @Test
    fun `detail shows a dash when the machine is unknown`() = runDesktopComposeUiTest {
        val summary = summary("session-abcdef01")
        val detail = CliSessionDetail(summary = summary, turns = listOf(turn(seq = 1)))

        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    CliSessionsContent(
                        state = CliSessionsUiState.Success(
                            sessions = listOf(summary),
                            detail = CliSessionDetailUiState.Ready(
                                sessionId = summary.sessionId,
                                result = CliSessionDetailResult(
                                    detail = detail,
                                    analytics = ComputeCliSessionAnalyticsUseCase().invoke(detail)
                                )
                            )
                        ),
                        language = AppLanguage.PT,
                        onRefresh = {},
                        onSelectRange = {},
                        onOpenSession = {},
                        onCloseDetail = {}
                    )
                }
            }
        }

        onNodeWithText("Máquina").assertIsDisplayed()
        // Máquina e branch desconhecidas neste resumo; projeto e período têm valor.
        onAllNodesWithText("—").assertCountEquals(2)
    }

    @Test
    fun `detail banner recommends compacting a saturated session`() = runDesktopComposeUiTest {
        val summary = summary("session-abcdef01")
        // 650K de contexto vivo numa janela de 1M.
        val detail = CliSessionDetail(
            summary = summary,
            turns = listOf(turn(seq = 1, cacheReadTokens = 650_000L))
        )

        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    CliSessionsContent(
                        state = CliSessionsUiState.Success(
                            sessions = listOf(summary),
                            detail = CliSessionDetailUiState.Ready(
                                sessionId = summary.sessionId,
                                result = CliSessionDetailResult(
                                    detail = detail,
                                    analytics = ComputeCliSessionAnalyticsUseCase().invoke(detail)
                                )
                            )
                        ),
                        language = AppLanguage.PT,
                        onRefresh = {},
                        onSelectRange = {},
                        onOpenSession = {},
                        onCloseDetail = {}
                    )
                }
            }
        }

        onNodeWithText("Sessão saturada").assertIsDisplayed()
        onNodeWithText(
            "Considere /compact ou abrir uma sessão nova: continuar assim sai caro e a janela está perto do limite."
        ).assertIsDisplayed()
        // O motivo do status precisa estar visível para o alerta ser conferível.
        onNodeWithText("65% da janela · \$0.3250 por mensagem").assertIsDisplayed()
    }

    @Test
    fun `detail banner reports a healthy session`() = runDesktopComposeUiTest {
        val summary = summary("session-abcdef01")
        val detail = CliSessionDetail(
            summary = summary,
            turns = listOf(turn(seq = 1, cacheReadTokens = 10_000L))
        )

        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    CliSessionsContent(
                        state = CliSessionsUiState.Success(
                            sessions = listOf(summary),
                            detail = CliSessionDetailUiState.Ready(
                                sessionId = summary.sessionId,
                                result = CliSessionDetailResult(
                                    detail = detail,
                                    analytics = ComputeCliSessionAnalyticsUseCase().invoke(detail)
                                )
                            )
                        ),
                        language = AppLanguage.PT,
                        onRefresh = {},
                        onSelectRange = {},
                        onOpenSession = {},
                        onCloseDetail = {}
                    )
                }
            }
        }

        onNodeWithText("Sessão saudável").assertIsDisplayed()
    }

    @Test
    fun `detail back button returns to the list`() = runDesktopComposeUiTest {
        var closed = false
        val summary = summary("session-abcdef01")

        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    CliSessionsContent(
                        state = CliSessionsUiState.Success(
                            sessions = listOf(summary),
                            detail = CliSessionDetailUiState.Loading(summary.sessionId)
                        ),
                        language = AppLanguage.PT,
                        onRefresh = {},
                        onSelectRange = {},
                        onOpenSession = {},
                        onCloseDetail = { closed = true }
                    )
                }
            }
        }

        onNodeWithText("Voltar").performClick()

        assertEquals(true, closed)
    }

    @Test
    fun `error state shows the message`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    CliSessionsContent(
                        state = CliSessionsUiState.Error("índice indisponível"),
                        language = AppLanguage.PT,
                        onRefresh = {},
                        onSelectRange = {},
                        onOpenSession = {},
                        onCloseDetail = {}
                    )
                }
            }
        }

        onNodeWithText("índice indisponível").assertIsDisplayed()
    }

    private fun summary(
        sessionId: String,
        costMicros: Long = 0L
    ): CliSessionSummary {
        return CliSessionSummary(
            sessionId = sessionId,
            filePath = "/tmp/$sessionId.jsonl",
            cwd = "/workspace/usage-monitor",
            firstTs = Instant.parse("2026-08-01T10:00:00Z"),
            lastTs = Instant.parse("2026-08-01T11:00:00Z"),
            primaryModel = "claude-opus-5",
            turnCount = 2,
            outputTokens = 1_000L,
            cacheReadTokens = 40_000L,
            costMicros = costMicros
        )
    }

    private fun turn(
        seq: Int,
        cacheReadTokens: Long = 0L,
        cacheWrite5mTokens: Long = 0L,
        cacheWrite1hTokens: Long = 0L
    ): CliSessionTurn {
        return CliSessionTurn(
            sessionId = "session-abcdef01",
            seq = seq,
            messageId = "msg-$seq",
            ts = Instant.parse("2026-08-01T10:0$seq:00Z"),
            model = "claude-opus-5",
            outputTokens = 500L,
            cacheReadTokens = cacheReadTokens,
            cacheWrite5mTokens = cacheWrite5mTokens,
            cacheWrite1hTokens = cacheWrite1hTokens
        )
    }
}
