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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.usagemonitor.data.export.UsageExportFormat
import com.usagemonitor.domain.entity.AccountCreditUsage
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.CliSessionTurn
import com.usagemonitor.domain.entity.CliUsageGroupRow
import com.usagemonitor.domain.entity.MICROS_PER_USD
import com.usagemonitor.domain.entity.MonthlyBudgetStatus
import com.usagemonitor.domain.entity.toUsageBreakdown
import com.usagemonitor.domain.usecase.CliSessionDetailResult
import com.usagemonitor.domain.usecase.ComputeCliSessionAnalyticsUseCase
import com.usagemonitor.presentation.ui.CliSessionsContent
import com.usagemonitor.presentation.ui.DETAIL_SCROLLBAR_TAG
import com.usagemonitor.presentation.ui.EXPORT_CSV_TAG
import com.usagemonitor.presentation.ui.EXPORT_JSON_TAG
import com.usagemonitor.presentation.ui.LIST_SCROLLBAR_TAG
import com.usagemonitor.presentation.ui.TAB_BREAKDOWN_TAG
import com.usagemonitor.presentation.ui.TAB_SESSIONS_TAG
import com.usagemonitor.presentation.ui.theme.AppTheme
import com.usagemonitor.presentation.viewmodel.CliSessionDetailUiState
import com.usagemonitor.presentation.viewmodel.CliSessionsUiState
import com.usagemonitor.presentation.viewmodel.CliSessionsView
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

    /**
     * Issue #35: reset vencido ancora a janela nova no próprio reset, mas o fim
     * dela só existe quando a API publicar o `resets_at` seguinte. Dizer "até
     * HH:MM" ali seria inventar um horário.
     */
    @Test
    fun `header names the window opened by the reset when its end is unknown`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    CliSessionsContent(
                        state = CliSessionsUiState.Success(
                            sessions = listOf(summary("session-abcdef01")),
                            rangeEndsAt = null,
                            rangeAnchored = true
                        ),
                        language = AppLanguage.PT,
                        onSelectRange = {},
                        onOpenSession = {},
                        onCloseDetail = {}
                    )
                }
            }
        }

        onNodeWithText("custo estimado · janela 5h desde o reinício").assertIsDisplayed()
    }

    /** 7 dias é janela corrida desde a issue #28: sem "até", e no masculino. */
    @Test
    fun `header names the 7d window as a sliding one`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    CliSessionsContent(
                        state = CliSessionsUiState.Success(
                            sessions = listOf(summary("session-abcdef01")),
                            range = CliSessionRange.LAST_7D
                        ),
                        language = AppLanguage.PT,
                        onSelectRange = {},
                        onOpenSession = {},
                        onCloseDetail = {}
                    )
                }
            }
        }

        onNodeWithText("custo estimado · últimos 7 dias").assertIsDisplayed()
    }

    @Test
    fun `empty list in the 7d window points at the filter`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    CliSessionsContent(
                        state = CliSessionsUiState.Success(
                            sessions = emptyList(),
                            range = CliSessionRange.LAST_7D
                        ),
                        language = AppLanguage.PT,
                        onSelectRange = {},
                        onOpenSession = {},
                        onCloseDetail = {}
                    )
                }
            }
        }

        onNodeWithText("Nenhuma sessão com atividade nos últimos 7 dias. Escolha uma janela maior.")
            .assertIsDisplayed()
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
    fun `the list header has no back button and no refresh button`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    CliSessionsContent(
                        state = CliSessionsUiState.Success(
                            sessions = listOf(summary("session-abcdef01")),
                            lastChangedAt = Instant.parse("2026-08-10T21:04:37Z")
                        ),
                        language = AppLanguage.PT,
                        onSelectRange = {},
                        onOpenSession = {},
                        onCloseDetail = {}
                    )
                }
            }
        }

        // O fechamento da janela é do title bar; um "Voltar" aqui não teria destino.
        onNodeWithText("Voltar").assertDoesNotExist()
        // A tela se atualiza sozinha: o botão saiu e a pílula ocupou o lugar dele.
        onNodeWithText("Atualizar").assertDoesNotExist()
        onNodeWithText("AO VIVO").assertIsDisplayed()
        onNodeWithText("última alteração 10/08 18:04 BRT").assertIsDisplayed()
    }

    @Test
    fun `the list header says so when nothing has changed yet`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    CliSessionsContent(
                        state = CliSessionsUiState.Success(sessions = listOf(summary("session-abcdef01"))),
                        language = AppLanguage.PT,
                        onSelectRange = {},
                        onOpenSession = {},
                        onCloseDetail = {}
                    )
                }
            }
        }

        onNodeWithText("sem alterações ainda").assertIsDisplayed()
    }

    @Test
    fun `the list row carries the session status with the number behind it`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(1_200.dp).height(700.dp)) {
                    CliSessionsContent(
                        state = CliSessionsUiState.Success(
                            sessions = listOf(
                                // 650K de contexto vivo numa janela de 1M.
                                summary("session-abcdef01").copy(
                                    liveContextTokens = 650_000L,
                                    liveContextModel = "claude-opus-5"
                                )
                            )
                        ),
                        language = AppLanguage.PT,
                        onSelectRange = {},
                        onOpenSession = {},
                        onCloseDetail = {}
                    )
                }
            }
        }

        // O status que antes exigia abrir o detalhe, com a evidência que o gerou.
        onNodeWithText("Saturada").assertIsDisplayed()
        onNodeWithText("65% da janela · \$0.3250 por mensagem").assertIsDisplayed()
    }

    @Test
    fun `the list row reports an unknown window instead of guessing one`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(1_200.dp).height(700.dp)) {
                    CliSessionsContent(
                        state = CliSessionsUiState.Success(
                            sessions = listOf(
                                summary("session-abcdef01").copy(
                                    primaryModel = "claude-3-5-sonnet",
                                    liveContextTokens = 10_000L,
                                    liveContextModel = "claude-3-5-sonnet"
                                )
                            )
                        ),
                        language = AppLanguage.PT,
                        onSelectRange = {},
                        onOpenSession = {},
                        onCloseDetail = {}
                    )
                }
            }
        }

        onNodeWithText("janela do modelo desconhecida · \$0.0000 por mensagem").assertIsDisplayed()
    }

    @Test
    fun `the header counts the saturated and attention sessions`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(1_200.dp).height(700.dp)) {
                    CliSessionsContent(
                        state = CliSessionsUiState.Success(
                            sessions = listOf(
                                // 650K, 450K e 10K de contexto vivo numa janela de 1M.
                                summary("session-a").copy(
                                    liveContextTokens = 650_000L,
                                    liveContextModel = "claude-opus-5"
                                ),
                                summary("session-b").copy(
                                    liveContextTokens = 450_000L,
                                    liveContextModel = "claude-opus-5"
                                ),
                                summary("session-c").copy(
                                    liveContextTokens = 10_000L,
                                    liveContextModel = "claude-opus-5"
                                )
                            )
                        ),
                        language = AppLanguage.PT,
                        onSelectRange = {},
                        onOpenSession = {},
                        onCloseDetail = {}
                    )
                }
            }
        }

        // O veredito por sessão já está na linha, mas some da vista assim que a
        // lista rola.
        onNodeWithText("1 saturada · 1 em atenção").assertIsDisplayed()
    }

    @Test
    fun `the header omits the tally when every session is healthy`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(1_200.dp).height(700.dp)) {
                    CliSessionsContent(
                        state = CliSessionsUiState.Success(
                            sessions = listOf(
                                summary("session-a").copy(
                                    liveContextTokens = 10_000L,
                                    liveContextModel = "claude-opus-5"
                                )
                            )
                        ),
                        language = AppLanguage.PT,
                        onSelectRange = {},
                        onOpenSession = {},
                        onCloseDetail = {}
                    )
                }
            }
        }

        onAllNodesWithText("0 saturadas").assertCountEquals(0)
        onAllNodesWithText("0 em atenção").assertCountEquals(0)
    }

    @Test
    fun `the header tally skips sessions whose model window is unknown`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(1_200.dp).height(700.dp)) {
                    CliSessionsContent(
                        state = CliSessionsUiState.Success(
                            sessions = listOf(
                                summary("session-a").copy(
                                    primaryModel = "claude-3-5-sonnet",
                                    liveContextTokens = 900_000L,
                                    liveContextModel = "claude-3-5-sonnet"
                                )
                            )
                        ),
                        language = AppLanguage.PT,
                        onSelectRange = {},
                        onOpenSession = {},
                        onCloseDetail = {}
                    )
                }
            }
        }

        // Sem a janela do modelo não há fração; contá-la seria afirmar o que não
        // se sabe. O mesmo cuidado que a linha da lista já tem.
        onAllNodesWithText("1 saturada").assertCountEquals(0)
        onAllNodesWithText("1 em atenção").assertCountEquals(0)
    }

    @Test
    fun `the header breaks the token total into its components`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(1_200.dp).height(700.dp)) {
                    CliSessionsContent(
                        state = CliSessionsUiState.Success(
                            sessions = listOf(summary("session-abcdef01"))
                        ),
                        language = AppLanguage.PT,
                        onSelectRange = {},
                        onOpenSession = {},
                        onCloseDetail = {}
                    )
                }
            }
        }

        // Sem a composição o total parece volume de conteúdo, quando é cache lido.
        // O rótulo aparece duas vezes: no total do header e na linha da sessão.
        onAllNodesWithText("Tokens (com cache)").assertCountEquals(2)
        onNodeWithText("in 0 · out 1K · cache lido 40K · cache gravado 0").assertIsDisplayed()
    }

    @Test
    fun `empty list with an active window points at the filter`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    CliSessionsContent(
                        state = CliSessionsUiState.Success(sessions = emptyList()),
                        language = AppLanguage.PT,
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
    fun `detail renders the analytics sections once the advanced block is open`() = runDesktopComposeUiTest {
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
                            ),
                            advancedExpanded = true
                        ),
                        language = AppLanguage.PT,
                        onSelectRange = {},
                        onOpenSession = {},
                        onCloseDetail = {}
                    )
                }
            }
        }

        onNodeWithText("Contexto por turno").assertExists()
        onNodeWithText("Distribuição de custo").assertExists()
        onNodeWithText("Economia do cache").assertExists()
        onNodeWithText("Cache gravado por turno").assertExists()
        onNodeWithText("Custo x economia acumulados").assertExists()
    }

    @Test
    fun `detail opens with the essentials and hides the rest`() = runDesktopComposeUiTest {
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
                        onSelectRange = {},
                        onOpenSession = {},
                        onCloseDetail = {}
                    )
                }
            }
        }

        // O essencial: veredito, resumo e o único gráfico do primeiro nível.
        onNodeWithText("Sessão saudável").assertIsDisplayed()
        onNodeWithText("Custo").assertExists()
        onNodeWithText("Tokens (com cache)").assertExists()
        onNodeWithText("Contexto por turno").assertExists()
        onNodeWithText("Avançado").assertExists()

        // Fechado, o bloco avançado nem entra na árvore.
        onNodeWithText("Distribuição de custo").assertDoesNotExist()
        onNodeWithText("Cache gravado por turno").assertDoesNotExist()
        onNodeWithText("Custo x economia acumulados").assertDoesNotExist()
    }

    @Test
    fun `clicking the advanced header reports the toggle`() = runDesktopComposeUiTest {
        val summary = summary("session-abcdef01")
        val detail = CliSessionDetail(summary = summary, turns = listOf(turn(seq = 1)))
        var toggled = false

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
                        onSelectRange = {},
                        onOpenSession = {},
                        onCloseDetail = {},
                        onToggleAdvanced = { toggled = true }
                    )
                }
            }
        }

        onNodeWithText("Avançado").performClick()

        assertEquals(true, toggled)
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
    fun `the glossary panel is collapsed until it is opened`() = runDesktopComposeUiTest {
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
                        onSelectRange = {},
                        onOpenSession = {},
                        onCloseDetail = {}
                    )
                }
            }
        }

        onNodeWithText("Como ler esta tela").assertExists()
        onNodeWithText("Turno de subagente").assertDoesNotExist()
    }

    @Test
    fun `the open glossary explains the terms of the screen`() = runDesktopComposeUiTest {
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
                            ),
                            glossaryExpanded = true
                        ),
                        language = AppLanguage.PT,
                        onSelectRange = {},
                        onOpenSession = {},
                        onCloseDetail = {}
                    )
                }
            }
        }

        onNodeWithText("Turno de subagente").assertExists()
        onNodeWithText("Compactação").assertExists()
    }

    /**
     * O idioma tem de chegar ao glossário. O teste de domínio garante que existe
     * texto em inglês; este garante que a tela pede o inglês.
     */
    @Test
    fun `the detail speaks english when the app does`() = runDesktopComposeUiTest {
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
                            ),
                            glossaryExpanded = true
                        ),
                        language = AppLanguage.EN,
                        onSelectRange = {},
                        onOpenSession = {},
                        onCloseDetail = {}
                    )
                }
            }
        }

        onNodeWithText("How to read this screen").assertExists()
        onNodeWithText("Subagent turn").assertExists()
        onNodeWithText("Advanced").assertExists()
        // O rótulo em português não pode sobreviver em lugar nenhum da tela.
        onNodeWithText("Turno de subagente").assertDoesNotExist()
        onNodeWithText("Como ler esta tela").assertDoesNotExist()
    }

    @Test
    fun `clicking the glossary header reports the toggle`() = runDesktopComposeUiTest {
        val summary = summary("session-abcdef01")
        val detail = CliSessionDetail(summary = summary, turns = listOf(turn(seq = 1)))
        var toggled = false

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
                        onSelectRange = {},
                        onOpenSession = {},
                        onCloseDetail = {},
                        onToggleGlossary = { toggled = true }
                    )
                }
            }
        }

        onNodeWithText("Como ler esta tela").performClick()

        assertEquals(true, toggled)
    }

    @Test
    fun `the list carries a scroll indicator`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    CliSessionsContent(
                        state = CliSessionsUiState.Success(
                            sessions = listOf(summary("session-abcdef01"), summary("session-abcdef02"))
                        ),
                        language = AppLanguage.PT,
                        onSelectRange = {},
                        onOpenSession = {},
                        onCloseDetail = {}
                    )
                }
            }
        }

        // `assertExists` e não `assertIsDisplayed`: com pouco conteúdo o polegar
        // da barra tem altura zero e não conta como exibido.
        onNodeWithTag(LIST_SCROLLBAR_TAG, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `the detail carries a scroll indicator`() = runDesktopComposeUiTest {
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
                        onSelectRange = {},
                        onOpenSession = {},
                        onCloseDetail = {}
                    )
                }
            }
        }

        onNodeWithTag(DETAIL_SCROLLBAR_TAG, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `error state shows the message`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    CliSessionsContent(
                        state = CliSessionsUiState.Error("índice indisponível"),
                        language = AppLanguage.PT,
                        onSelectRange = {},
                        onOpenSession = {},
                        onCloseDetail = {}
                    )
                }
            }
        }

        onNodeWithText("índice indisponível").assertIsDisplayed()
    }

    @Test
    fun `the breakdown tab ranks projects by cost and declares the axes`() = runDesktopComposeUiTest {
        val breakdown = listOf(
            groupRow("s1", "/workspace/alpha", inputTokens = 3_000_000L),
            groupRow("s2", "/workspace/beta", inputTokens = 1_000_000L)
        ).toUsageBreakdown()

        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    CliSessionsContent(
                        state = CliSessionsUiState.Success(
                            sessions = listOf(summary("session-abcdef01")),
                            view = CliSessionsView.BREAKDOWN,
                            breakdown = breakdown
                        ),
                        language = AppLanguage.PT,
                        onSelectRange = {},
                        onOpenSession = {},
                        onCloseDetail = {}
                    )
                }
            }
        }

        onNodeWithText("Por projeto").assertIsDisplayed()
        onNodeWithText("alpha").assertIsDisplayed()
        onNodeWithText("beta").assertIsDisplayed()
        onNodeWithText("75%").assertIsDisplayed()
        // Somar seções contaria o mesmo gasto três vezes; a tela diz isso.
        onNodeWithText(
            "As três seções descrevem os mesmos turnos por eixos diferentes — não se somam."
        ).assertIsDisplayed()
    }

    @Test
    fun `the breakdown tab reports a stale reading without hiding the numbers`() = runDesktopComposeUiTest {
        val breakdown = listOf(groupRow("s1", "/workspace/alpha", inputTokens = 1_000_000L)).toUsageBreakdown()

        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    CliSessionsContent(
                        state = CliSessionsUiState.Success(
                            sessions = listOf(summary("session-abcdef01")),
                            view = CliSessionsView.BREAKDOWN,
                            breakdown = breakdown,
                            breakdownError = "banco travado"
                        ),
                        language = AppLanguage.PT,
                        onSelectRange = {},
                        onOpenSession = {},
                        onCloseDetail = {}
                    )
                }
            }
        }

        onNodeWithText("alpha").assertIsDisplayed()
        onNodeWithText(
            "Última leitura falhou; os números são da anterior. banco travado"
        ).assertIsDisplayed()
    }

    @Test
    fun `clicking the breakdown tab emits the view change`() = runDesktopComposeUiTest {
        var selected: CliSessionsView? = null

        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    CliSessionsContent(
                        state = CliSessionsUiState.Success(sessions = listOf(summary("session-abcdef01"))),
                        language = AppLanguage.PT,
                        onSelectRange = {},
                        onOpenSession = {},
                        onCloseDetail = {},
                        onSelectView = { view -> selected = view }
                    )
                }
            }
        }

        onNodeWithTag(TAB_SESSIONS_TAG).assertIsSelected()
        onNodeWithTag(TAB_BREAKDOWN_TAG).performClick()

        assertEquals(CliSessionsView.BREAKDOWN, selected)
    }

    @Test
    fun `the export buttons report the chosen format`() = runDesktopComposeUiTest {
        var chosen: UsageExportFormat? = null

        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    CliSessionsContent(
                        state = CliSessionsUiState.Success(sessions = listOf(summary("session-abcdef01"))),
                        language = AppLanguage.PT,
                        onSelectRange = {},
                        onOpenSession = {},
                        onCloseDetail = {},
                        onExport = { format -> chosen = format }
                    )
                }
            }
        }

        onNodeWithTag(EXPORT_CSV_TAG).performClick()
        assertEquals(UsageExportFormat.CSV, chosen)

        onNodeWithTag(EXPORT_JSON_TAG).performClick()
        assertEquals(UsageExportFormat.JSON, chosen)
    }

    /**
     * As duas moedas ficam em linhas separadas: somar o custo em USD aos
     * créditos numa conta em BRL daria um número inventado.
     */
    @Test
    fun `the budget card keeps the account currency apart`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    CliSessionsContent(
                        state = CliSessionsUiState.Success(
                            sessions = listOf(summary("session-abcdef01")),
                            view = CliSessionsView.BREAKDOWN,
                            breakdown = listOf(
                                groupRow("s1", "/workspace/alpha", inputTokens = 1_000_000L)
                            ).toUsageBreakdown(),
                            budget = MonthlyBudgetStatus(
                                limitMicros = 200L * MICROS_PER_USD,
                                spentMicros = 150L * MICROS_PER_USD,
                                daysElapsed = 15,
                                daysInMonth = 31
                            ),
                            accountCredits = AccountCreditUsage(
                                usedMinorUnits = 27_500L,
                                limitMinorUnits = 55_000L,
                                currencyCode = "BRL"
                            )
                        ),
                        language = AppLanguage.PT,
                        onSelectRange = {},
                        onOpenSession = {},
                        onCloseDetail = {}
                    )
                }
            }
        }

        onNodeWithText("Orçamento do mês").assertIsDisplayed()
        onNodeWithText("\$150.00 de \$200.00").assertIsDisplayed()
        onNodeWithText(
            "Créditos de uso da conta: BRL 275.00 de BRL 550.00 (moeda da conta, não convertida)"
        ).assertIsDisplayed()
    }

    private fun groupRow(
        sessionId: String,
        cwd: String,
        inputTokens: Long = 0L
    ): CliUsageGroupRow {
        return CliUsageGroupRow(
            sessionId = sessionId,
            cwd = cwd,
            gitBranch = "main",
            model = "claude-opus-5",
            turnCount = 1,
            inputTokens = inputTokens
        )
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
