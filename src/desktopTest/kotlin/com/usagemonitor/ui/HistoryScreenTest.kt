package com.usagemonitor.ui

import androidx.compose.foundation.layout.height
import com.usagemonitor.presentation.ui.HistoryMetricTable
import com.usagemonitor.presentation.ui.HistoryMetricEntry
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.UsageForecast
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.UsageHistoryPoint
import com.usagemonitor.domain.entity.UsageHistorySeries
import com.usagemonitor.domain.entity.UsageAccountContext
import com.usagemonitor.domain.entity.UsageAccountKey
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.presentation.ui.HistoryScreen
import com.usagemonitor.presentation.ui.historyAccountChipTag
import com.usagemonitor.presentation.viewmodel.HistoryViewModel
import com.usagemonitor.presentation.viewmodel.HistoryQuotaView
import com.usagemonitor.presentation.ui.historyQuotaViewChipTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Testes de componente de `HistoryScreen`, fora de `ComponentTest` pelo mesmo motivo de
 * `SettingsDialogContentTest`: forks são distribuídos por classe (issue #295).
 */
@OptIn(ExperimentalTestApi::class)
class HistoryScreenTest {

    /**
     * A tela de Histórico virou tabela: cada métrica ocupa uma linha de rótulo e
     * valor em vez de um bloco de duas linhas espremido num `FlowRow`, e a coluna
     * ficou mais alta que os 768px da cena padrão. Os asserts falhavam por
     * viewport — o nó existe, só está abaixo do corte.
     */
    private companion object {
        const val HISTORY_SCENE_HEIGHT = 1_600
    }

    @Test
    fun `HistoryScreen renders one OpenCode chart per model instead of separate 5h and 7d cards`() = runDesktopComposeUiTest(height = HISTORY_SCENE_HEIGHT) {
        val report = com.usagemonitor.domain.entity.ApiUsageHistoryReport(
            source = ApiSource.OPENCODE,
            range = HistoryRange.LAST_24_HOURS,
            lastUpdatedAt = Instant.parse("2026-05-07T14:33:00Z"),
            series = listOf(
                UsageHistorySeries(
                    quotaLabel = "MiniMax M2.5 Free 5h",
                    periodType = PeriodType.INTERVAL,
                    unit = UsageUnit.REQUESTS,
                    points = listOf(
                        UsageHistoryPoint(
                            capturedAt = Instant.parse("2026-05-07T11:32:00Z"),
                            used = 4,
                            total = 0,
                            rawUsed = 4,
                            rawTotal = 0,
                            periodEndAt = Instant.parse("2026-05-07T14:33:00Z")
                        ),
                        UsageHistoryPoint(
                            capturedAt = Instant.parse("2026-05-07T11:33:00Z"),
                            used = 11,
                            total = 0,
                            rawUsed = 11,
                            rawTotal = 0,
                            periodEndAt = Instant.parse("2026-05-07T14:33:00Z")
                        )
                    ),
                    currentDisplayUsed = 11,
                    currentDisplayTotal = 0,
                    deltaDisplayUsed = 3,
                    averageDisplayConsumptionPerHour = 17.0,
                    currentPeriodEndAt = Instant.parse("2026-05-07T14:33:00Z"),
                    forecast = UsageForecast.InsufficientData,
                    riskSummary = null
                ),
                UsageHistorySeries(
                    quotaLabel = "MiniMax M2.5 Free 7d",
                    periodType = PeriodType.WEEKLY,
                    unit = UsageUnit.REQUESTS,
                    points = listOf(
                        UsageHistoryPoint(
                            capturedAt = Instant.parse("2026-05-07T11:32:00Z"),
                            used = 16,
                            total = 0,
                            rawUsed = 16,
                            rawTotal = 0,
                            periodEndAt = Instant.parse("2026-05-07T14:33:00Z")
                        ),
                        UsageHistoryPoint(
                            capturedAt = Instant.parse("2026-05-07T11:33:00Z"),
                            used = 29,
                            total = 0,
                            rawUsed = 29,
                            rawTotal = 0,
                            periodEndAt = Instant.parse("2026-05-07T14:33:00Z")
                        )
                    ),
                    currentDisplayUsed = 29,
                    currentDisplayTotal = 0,
                    deltaDisplayUsed = 13,
                    averageDisplayConsumptionPerHour = 2.0,
                    currentPeriodEndAt = Instant.parse("2026-05-07T14:33:00Z"),
                    forecast = UsageForecast.InsufficientData,
                    riskSummary = null
                )
            )
        )

        val viewModel = HistoryViewModel(
            getUsageHistory = com.usagemonitor.domain.usecase.GetUsageHistoryUseCase(
                repository = object : com.usagemonitor.domain.repository.UsageHistoryRepository {
                    override suspend fun recordSnapshot(
                        stats: com.usagemonitor.domain.entity.ApiUsageStats,
                        capturedAt: Instant
                    ) = Unit

                    override suspend fun getHistoryReport(
                        source: ApiSource,
                        range: HistoryRange,
                        now: Instant
                    ): com.usagemonitor.domain.entity.ApiUsageHistoryReport {
                        return report
                    }
                }
            ),
            enabledApis = MutableStateFlow(setOf(ApiSource.OPENCODE))
        )

        setContent {
            ScreenTestTheme(isDark = true) {
                HistoryScreen(
                    viewModel = viewModel,
                    language = AppLanguage.PT,
                    onBack = {},
                    focusedSource = ApiSource.OPENCODE,
                    showSourceSelector = false
                )
            }
        }

        waitUntil(timeoutMillis = 5_000) {
            runCatching {
                onNodeWithText("MiniMax M2.5 Free").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }

        onNodeWithText("MiniMax M2.5 Free").assertIsDisplayed()
        onAllNodesWithText("MiniMax M2.5 Free 5h").assertCountEquals(0)
        onAllNodesWithText("MiniMax M2.5 Free 7d").assertCountEquals(0)
        onNodeWithText("Requisições nas últimas 5h").assertIsDisplayed()
        onNodeWithText("Requisições nos últimos 7 dias").assertIsDisplayed()
        onNodeWithText("Atividade observada do modelo free na janela curta de 5h.").assertIsDisplayed()
        onNodeWithText("3 requisições").assertIsDisplayed()
        onNodeWithText("17 requisições/h").assertIsDisplayed()

        onNodeWithText("7 dias").performClick()

        waitUntil(timeoutMillis = 5_000) {
            runCatching {
                onNodeWithText("Atividade observada do modelo free na janela semanal de 7 dias.").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }

        onNodeWithText("Atividade observada do modelo free na janela semanal de 7 dias.").assertIsDisplayed()
        onNodeWithText("13 requisições").assertIsDisplayed()
        onNodeWithText("2 requisições/h").assertIsDisplayed()
        viewModel.onDestroy()
    }

    @Test
    fun `HistoryScreen renders one Kilo chart per model instead of separate 5h and 7d cards`() = runDesktopComposeUiTest(height = HISTORY_SCENE_HEIGHT) {
        val report = com.usagemonitor.domain.entity.ApiUsageHistoryReport(
            source = ApiSource.KILO,
            range = HistoryRange.LAST_24_HOURS,
            lastUpdatedAt = Instant.parse("2026-05-07T14:33:00Z"),
            series = listOf(
                UsageHistorySeries(
                    quotaLabel = "Auto Free Kilo Gateway 5h",
                    periodType = PeriodType.INTERVAL,
                    unit = UsageUnit.REQUESTS,
                    points = listOf(
                        UsageHistoryPoint(
                            capturedAt = Instant.parse("2026-05-07T11:32:00Z"),
                            used = 6,
                            total = 0,
                            rawUsed = 6,
                            rawTotal = 0,
                            periodEndAt = Instant.parse("2026-05-07T14:33:00Z")
                        ),
                        UsageHistoryPoint(
                            capturedAt = Instant.parse("2026-05-07T11:33:00Z"),
                            used = 15,
                            total = 0,
                            rawUsed = 15,
                            rawTotal = 0,
                            periodEndAt = Instant.parse("2026-05-07T14:33:00Z")
                        )
                    ),
                    currentDisplayUsed = 15,
                    currentDisplayTotal = 0,
                    deltaDisplayUsed = 5,
                    averageDisplayConsumptionPerHour = 19.0,
                    currentPeriodEndAt = Instant.parse("2026-05-07T14:33:00Z"),
                    forecast = UsageForecast.InsufficientData,
                    riskSummary = null
                ),
                UsageHistorySeries(
                    quotaLabel = "Auto Free Kilo Gateway 7d",
                    periodType = PeriodType.WEEKLY,
                    unit = UsageUnit.REQUESTS,
                    points = listOf(
                        UsageHistoryPoint(
                            capturedAt = Instant.parse("2026-05-07T11:32:00Z"),
                            used = 20,
                            total = 0,
                            rawUsed = 20,
                            rawTotal = 0,
                            periodEndAt = Instant.parse("2026-05-07T14:33:00Z")
                        ),
                        UsageHistoryPoint(
                            capturedAt = Instant.parse("2026-05-07T11:33:00Z"),
                            used = 38,
                            total = 0,
                            rawUsed = 38,
                            rawTotal = 0,
                            periodEndAt = Instant.parse("2026-05-07T14:33:00Z")
                        )
                    ),
                    currentDisplayUsed = 38,
                    currentDisplayTotal = 0,
                    deltaDisplayUsed = 18,
                    averageDisplayConsumptionPerHour = 3.0,
                    currentPeriodEndAt = Instant.parse("2026-05-07T14:33:00Z"),
                    forecast = UsageForecast.InsufficientData,
                    riskSummary = null
                )
            )
        )
        val requestedRanges = mutableListOf<HistoryRange>()

        val viewModel = HistoryViewModel(
            getUsageHistory = com.usagemonitor.domain.usecase.GetUsageHistoryUseCase(
                repository = object : com.usagemonitor.domain.repository.UsageHistoryRepository {
                    override suspend fun recordSnapshot(
                        stats: com.usagemonitor.domain.entity.ApiUsageStats,
                        capturedAt: Instant
                    ) = Unit

                    override suspend fun getHistoryReport(
                        source: ApiSource,
                        range: HistoryRange,
                        now: Instant
                    ): com.usagemonitor.domain.entity.ApiUsageHistoryReport {
                        requestedRanges += range
                        return report
                    }
                }
            ),
            enabledApis = MutableStateFlow(setOf(ApiSource.KILO))
        )

        setContent {
            ScreenTestTheme(isDark = true) {
                HistoryScreen(
                    viewModel = viewModel,
                    language = AppLanguage.PT,
                    onBack = {},
                    focusedSource = ApiSource.KILO,
                    showSourceSelector = false
                )
            }
        }

        waitUntil(timeoutMillis = 5_000) {
            runCatching {
                onNodeWithText("Auto Free Kilo Gateway").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }

        onNodeWithText("Auto Free Kilo Gateway").assertIsDisplayed()
        onAllNodesWithText("Auto Free Kilo Gateway 5h").assertCountEquals(0)
        onAllNodesWithText("Auto Free Kilo Gateway 7d").assertCountEquals(0)
        onNodeWithText("Requisições nas últimas 5h").assertIsDisplayed()
        onNodeWithText("Requisições nos últimos 7 dias").assertIsDisplayed()
        onNodeWithText("Atividade observada do modelo free na janela curta de 5h.").assertIsDisplayed()
        onNodeWithText("5 requisições").assertIsDisplayed()
        onNodeWithText("19 requisições/h").assertIsDisplayed()

        onNodeWithText("7 dias").performClick()

        waitUntil(timeoutMillis = 5_000) {
            runCatching {
                onNodeWithText("Atividade observada do modelo free na janela semanal de 7 dias.").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }

        onNodeWithText("Atividade observada do modelo free na janela semanal de 7 dias.").assertIsDisplayed()
        onNodeWithText("18 requisições").assertIsDisplayed()
        onNodeWithText("3 requisições/h").assertIsDisplayed()

        onNodeWithText("Total").performClick()

        waitUntil(timeoutMillis = 5_000) {
            requestedRanges.contains(HistoryRange.TOTAL)
        }

        onNodeWithText("Atividade observada do modelo free na janela semanal de 7 dias.").assertIsDisplayed()
        assertTrue(HistoryRange.LAST_24_HOURS in requestedRanges)
        assertTrue(HistoryRange.LAST_7_DAYS in requestedRanges)
        assertTrue(HistoryRange.TOTAL in requestedRanges)
        viewModel.onDestroy()
    }

    @Test
    fun `HistoryScreen renders one Claude chart instead of separate 5h and 7d cards`() = runDesktopComposeUiTest(height = HISTORY_SCENE_HEIGHT) {
        val report = com.usagemonitor.domain.entity.ApiUsageHistoryReport(
            source = ApiSource.ANTHROPIC,
            range = HistoryRange.LAST_24_HOURS,
            lastUpdatedAt = Instant.parse("2026-05-07T14:33:00Z"),
            series = listOf(
                UsageHistorySeries(
                    quotaLabel = "Claude 5h",
                    periodType = PeriodType.INTERVAL,
                    unit = UsageUnit.PERCENTAGE,
                    points = listOf(
                        UsageHistoryPoint(
                            capturedAt = Instant.parse("2026-05-07T11:32:00Z"),
                            used = 4,
                            total = 100,
                            rawUsed = 180,
                            rawTotal = 4500,
                            periodEndAt = Instant.parse("2026-05-07T14:33:00Z")
                        )
                    ),
                    currentDisplayUsed = 180,
                    currentDisplayTotal = 4500,
                    deltaDisplayUsed = 20,
                    averageDisplayConsumptionPerHour = 5.0,
                    currentPeriodEndAt = Instant.parse("2026-05-07T14:33:00Z"),
                    forecast = UsageForecast.InsufficientData,
                    riskSummary = null
                ),
                UsageHistorySeries(
                    quotaLabel = "Claude 7d",
                    periodType = PeriodType.WEEKLY,
                    unit = UsageUnit.PERCENTAGE,
                    points = listOf(
                        UsageHistoryPoint(
                            capturedAt = Instant.parse("2026-05-07T11:32:00Z"),
                            used = 46,
                            total = 100,
                            rawUsed = 20700,
                            rawTotal = 45000,
                            periodEndAt = Instant.parse("2026-05-10T14:33:00Z")
                        )
                    ),
                    currentDisplayUsed = 20700,
                    currentDisplayTotal = 45000,
                    deltaDisplayUsed = 900,
                    averageDisplayConsumptionPerHour = 30.0,
                    currentPeriodEndAt = Instant.parse("2026-05-10T14:33:00Z"),
                    forecast = UsageForecast.ResetsBeforeExhaustion,
                    riskSummary = null
                )
            )
        )

        val viewModel = HistoryViewModel(
            getUsageHistory = com.usagemonitor.domain.usecase.GetUsageHistoryUseCase(
                repository = object : com.usagemonitor.domain.repository.UsageHistoryRepository {
                    override suspend fun recordSnapshot(
                        stats: com.usagemonitor.domain.entity.ApiUsageStats,
                        capturedAt: Instant
                    ) = Unit

                    override suspend fun getHistoryReport(
                        source: ApiSource,
                        range: HistoryRange,
                        now: Instant
                    ): com.usagemonitor.domain.entity.ApiUsageHistoryReport {
                        return report
                    }
                }
            ),
            enabledApis = MutableStateFlow(setOf(ApiSource.ANTHROPIC))
        )

        setContent {
            ScreenTestTheme(isDark = true) {
                HistoryScreen(
                    viewModel = viewModel,
                    language = AppLanguage.PT,
                    onBack = {},
                    focusedSource = ApiSource.ANTHROPIC,
                    showSourceSelector = false
                )
            }
        }

        waitUntil(timeoutMillis = 5_000) {
            runCatching {
                onNodeWithText("Claude").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }

        onNodeWithText("Claude").assertIsDisplayed()
        onAllNodesWithText("Claude 5h").assertCountEquals(0)
        onAllNodesWithText("Claude 7d").assertCountEquals(0)
        onNodeWithText("Cota intervalar atual").assertIsDisplayed()
        onNodeWithText("Cota semanal atual").assertIsDisplayed()
        // A semanal entra no mesmo gráfico (issue #320), e a legenda nomeia as
        // duas — cada nome aparece no seletor de cota e na legenda.
        onAllNodesWithText("5h").assertCountEquals(2)
        onAllNodesWithText("7d").assertCountEquals(2)
        onAllNodesWithText("Início do recorte").assertCountEquals(0)
        onAllNodesWithText("Arraste no gráfico para comparar dois pontos.").assertCountEquals(0)
        viewModel.onDestroy()
    }

    @Test
    fun `HistoryScreen lists accounts and allows selecting another workspace`() = runDesktopComposeUiTest {
        val accountA = UsageAccountContext(
            key = UsageAccountKey(ApiSource.CODEX, "same-user", "workspace-a"),
            email = "same@example.com",
            workspaceName = "Workspace A"
        )
        val accountB = UsageAccountContext(
            key = UsageAccountKey(ApiSource.CODEX, "same-user", "workspace-b"),
            email = "same@example.com",
            workspaceName = "Workspace B"
        )
        val report = com.usagemonitor.domain.entity.ApiUsageHistoryReport(
            source = ApiSource.CODEX,
            range = HistoryRange.LAST_24_HOURS,
            lastUpdatedAt = null,
            series = emptyList()
        )
        val repository = object : com.usagemonitor.domain.repository.UsageHistoryRepository {
            override suspend fun recordSnapshot(stats: ApiUsageStats, capturedAt: Instant) = Unit

            override suspend fun listAccounts(source: ApiSource): List<UsageAccountContext> {
                return listOf(accountA, accountB)
            }

            override suspend fun getHistoryReport(
                source: ApiSource,
                range: HistoryRange,
                now: Instant
            ) = report
        }
        val viewModel = HistoryViewModel(
            getUsageHistory = com.usagemonitor.domain.usecase.GetUsageHistoryUseCase(repository),
            enabledApis = MutableStateFlow(setOf(ApiSource.CODEX))
        )

        setContent {
            ScreenTestTheme(isDark = true) {
                HistoryScreen(
                    viewModel = viewModel,
                    language = AppLanguage.PT,
                    onBack = {},
                    focusedSource = ApiSource.CODEX,
                    showSourceSelector = false
                )
            }
        }

        waitUntil(timeoutMillis = 5_000) {
            runCatching {
                onNodeWithText(accountA.displayLabel).fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }
        onNodeWithText("Conta").assertIsDisplayed()
        // Pela tag: o rótulo da conta é `email — workspace`, texto longo e livre
        // que também aparece no card do dashboard.
        onNodeWithTag(historyAccountChipTag(accountA)).assertIsSelected()
        onNodeWithTag(historyAccountChipTag(accountB)).performClick()
        waitUntil(timeoutMillis = 5_000) {
            runCatching {
                onNodeWithTag(historyAccountChipTag(accountB)).assertIsSelected()
                true
            }.getOrDefault(false)
        }
        viewModel.onDestroy()
    }

    @Test
    fun `HistoryScreen renders reported Codex series without inferred metrics`() = runDesktopComposeUiTest(height = HISTORY_SCENE_HEIGHT) {
        val report = com.usagemonitor.domain.entity.ApiUsageHistoryReport(
            source = ApiSource.CODEX,
            range = HistoryRange.LAST_24_HOURS,
            lastUpdatedAt = Instant.parse("2026-04-28T18:00:00Z"),
            series = listOf(
                UsageHistorySeries(
                    quotaLabel = "Codex atual",
                    periodType = PeriodType.REPORTED,
                    unit = UsageUnit.PERCENTAGE,
                    points = listOf(
                        UsageHistoryPoint(
                            capturedAt = Instant.parse("2026-04-28T16:00:00Z"),
                            used = 10,
                            total = 100,
                            rawUsed = 10,
                            rawTotal = 100,
                            periodEndAt = Instant.parse("2026-04-28T20:00:00Z")
                        ),
                        UsageHistoryPoint(
                            capturedAt = Instant.parse("2026-04-28T17:00:00Z"),
                            used = 30,
                            total = 100,
                            rawUsed = 30,
                            rawTotal = 100,
                            periodEndAt = Instant.parse("2026-04-28T20:00:00Z")
                        ),
                        UsageHistoryPoint(
                            capturedAt = Instant.parse("2026-04-28T18:00:00Z"),
                            used = 50,
                            total = 100,
                            rawUsed = 50,
                            rawTotal = 100,
                            periodEndAt = Instant.parse("2026-04-28T20:00:00Z")
                        )
                    ),
                    currentDisplayUsed = 50,
                    currentDisplayTotal = 100,
                    deltaDisplayUsed = 40,
                    averageDisplayConsumptionPerHour = 0.0,
                    currentPeriodEndAt = Instant.parse("2026-04-28T20:00:00Z"),
                    forecast = UsageForecast.InsufficientData,
                    riskSummary = null
                )
            )
        )

        val viewModel = HistoryViewModel(
            getUsageHistory = com.usagemonitor.domain.usecase.GetUsageHistoryUseCase(
                repository = object : com.usagemonitor.domain.repository.UsageHistoryRepository {
                    override suspend fun recordSnapshot(
                        stats: com.usagemonitor.domain.entity.ApiUsageStats,
                        capturedAt: Instant
                    ) = Unit

                    override suspend fun getHistoryReport(
                        source: ApiSource,
                        range: HistoryRange,
                        now: Instant
                    ): com.usagemonitor.domain.entity.ApiUsageHistoryReport {
                        return report
                    }
                }
            ),
            enabledApis = MutableStateFlow(setOf(ApiSource.CODEX))
        )

        setContent {
            ScreenTestTheme(isDark = true) {
                HistoryScreen(
                    viewModel = viewModel,
                    language = AppLanguage.PT,
                    onBack = {},
                    focusedSource = ApiSource.CODEX,
                    showSourceSelector = false
                )
            }
        }

        waitUntil(timeoutMillis = 5_000) {
            runCatching {
                onNodeWithText("Codex atual").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }

        onNodeWithText("Histórico do Codex").assertIsDisplayed()
        onNodeWithText("Codex atual").assertIsDisplayed()
        onNodeWithText("Janela reportada").assertIsDisplayed()
        onAllNodesWithText("API").assertCountEquals(0)
        onNodeWithText("Intervalo").assertIsDisplayed()
        onNodeWithText("Total").assertIsDisplayed()
        onAllNodesWithText("Início do recorte").assertCountEquals(0)
        onAllNodesWithText("Atual").assertCountEquals(0)
        onAllNodesWithText("Variação no recorte").assertCountEquals(0)
        onAllNodesWithText("Arraste no gráfico para comparar dois pontos.").assertCountEquals(0)
        onNodeWithText("Uso atual").assertIsDisplayed()
        onNodeWithText("50 / 100 %").assertIsDisplayed()
        onNodeWithText("Variação observada").assertIsDisplayed()
        onNodeWithText("40 %").assertIsDisplayed()
        onNodeWithText("Último reinício reportado").assertIsDisplayed()
        onNodeWithText("28/04 17:00 BRT").assertIsDisplayed()
        onAllNodesWithText("Média por hora").assertCountEquals(0)
        onAllNodesWithText("Previsão").assertCountEquals(0)
        onAllNodesWithText("Fechar").assertCountEquals(0)
        viewModel.onDestroy()
    }

    @Test
    fun `HistoryScreen keeps reported Codex series separate from legacy series`() = runDesktopComposeUiTest(height = HISTORY_SCENE_HEIGHT) {
        val report = com.usagemonitor.domain.entity.ApiUsageHistoryReport(
            source = ApiSource.CODEX,
            range = HistoryRange.LAST_24_HOURS,
            lastUpdatedAt = Instant.parse("2026-04-28T18:00:00Z"),
            series = listOf(
                UsageHistorySeries(
                    quotaLabel = "Codex atual",
                    periodType = PeriodType.REPORTED,
                    unit = UsageUnit.PERCENTAGE,
                    points = listOf(
                        UsageHistoryPoint(
                            capturedAt = Instant.parse("2026-04-28T17:00:00Z"),
                            used = 16,
                            total = 100,
                            rawUsed = 16,
                            rawTotal = 100,
                            periodEndAt = Instant.parse("2026-04-28T20:00:00Z")
                        )
                    ),
                    currentDisplayUsed = 16,
                    currentDisplayTotal = 100,
                    deltaDisplayUsed = 0,
                    averageDisplayConsumptionPerHour = 0.0,
                    currentPeriodEndAt = Instant.parse("2026-04-28T20:00:00Z"),
                    forecast = UsageForecast.InsufficientData,
                    riskSummary = null
                ),
                UsageHistorySeries(
                    quotaLabel = "Codex 5h",
                    periodType = PeriodType.INTERVAL,
                    unit = UsageUnit.PERCENTAGE,
                    points = listOf(
                        UsageHistoryPoint(
                            capturedAt = Instant.parse("2026-04-28T16:00:00Z"),
                            used = 5,
                            total = 100,
                            rawUsed = 5,
                            rawTotal = 100,
                            periodEndAt = Instant.parse("2026-04-28T20:00:00Z")
                        )
                    ),
                    currentDisplayUsed = 5,
                    currentDisplayTotal = 100,
                    deltaDisplayUsed = 0,
                    averageDisplayConsumptionPerHour = 0.0,
                    currentPeriodEndAt = Instant.parse("2026-04-28T20:00:00Z"),
                    forecast = UsageForecast.InsufficientData,
                    riskSummary = null
                )
            )
        )

        val viewModel = HistoryViewModel(
            getUsageHistory = com.usagemonitor.domain.usecase.GetUsageHistoryUseCase(
                repository = object : com.usagemonitor.domain.repository.UsageHistoryRepository {
                    override suspend fun recordSnapshot(
                        stats: com.usagemonitor.domain.entity.ApiUsageStats,
                        capturedAt: Instant
                    ) = Unit

                    override suspend fun getHistoryReport(
                        source: ApiSource,
                        range: HistoryRange,
                        now: Instant
                    ): com.usagemonitor.domain.entity.ApiUsageHistoryReport {
                        return report
                    }
                }
            ),
            enabledApis = MutableStateFlow(setOf(ApiSource.CODEX))
        )

        setContent {
            ScreenTestTheme(isDark = true) {
                HistoryScreen(
                    viewModel = viewModel,
                    language = AppLanguage.PT,
                    onBack = {},
                    focusedSource = ApiSource.CODEX,
                    showSourceSelector = false
                )
            }
        }

        waitUntil(timeoutMillis = 5_000) {
            runCatching {
                onNodeWithText("Codex atual").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }

        onNodeWithText("Codex atual").assertIsDisplayed()
        onNodeWithText("Codex 5h").assertIsDisplayed()
        onNodeWithText("Quota intervalar").assertIsDisplayed()
        viewModel.onDestroy()
    }

    @Test
    fun `HistoryScreen renders DeepSeek-specific balance summary`() = runDesktopComposeUiTest(height = HISTORY_SCENE_HEIGHT) {
        val report = com.usagemonitor.domain.entity.ApiUsageHistoryReport(
            source = ApiSource.DEEPSEEK,
            range = HistoryRange.LAST_24_HOURS,
            lastUpdatedAt = Instant.parse("2026-05-06T21:47:00Z"),
            series = listOf(
                UsageHistorySeries(
                    quotaLabel = com.usagemonitor.domain.entity.DeepSeekQuotaLabels.BALANCE,
                    periodType = PeriodType.INTERVAL,
                    unit = UsageUnit.CURRENCY_USD,
                    points = listOf(
                        UsageHistoryPoint(
                            capturedAt = Instant.parse("2026-05-06T18:00:00Z"),
                            used = 0,
                            total = 469,
                            rawUsed = 469,
                            rawTotal = 469,
                            periodEndAt = Instant.parse("9999-12-31T23:59:59Z")
                        ),
                        UsageHistoryPoint(
                            capturedAt = Instant.parse("2026-05-06T19:30:00Z"),
                            used = 0,
                            total = 468,
                            rawUsed = 468,
                            rawTotal = 468,
                            periodEndAt = Instant.parse("9999-12-31T23:59:59Z")
                        ),
                        UsageHistoryPoint(
                            capturedAt = Instant.parse("2026-05-06T21:47:00Z"),
                            used = 0,
                            total = 466,
                            rawUsed = 466,
                            rawTotal = 466,
                            periodEndAt = Instant.parse("9999-12-31T23:59:59Z")
                        )
                    ),
                    currentDisplayUsed = 466,
                    currentDisplayTotal = 466,
                    deltaDisplayUsed = 3,
                    averageDisplayConsumptionPerHour = 0.8,
                    currentPeriodEndAt = Instant.parse("9999-12-31T23:59:59Z"),
                    forecast = UsageForecast.InsufficientData,
                    riskSummary = null
                )
            )
        )

        val viewModel = HistoryViewModel(
            getUsageHistory = com.usagemonitor.domain.usecase.GetUsageHistoryUseCase(
                repository = object : com.usagemonitor.domain.repository.UsageHistoryRepository {
                    override suspend fun recordSnapshot(
                        stats: com.usagemonitor.domain.entity.ApiUsageStats,
                        capturedAt: Instant
                    ) = Unit

                    override suspend fun getHistoryReport(
                        source: ApiSource,
                        range: HistoryRange,
                        now: Instant
                    ): com.usagemonitor.domain.entity.ApiUsageHistoryReport {
                        return report
                    }
                }
            ),
            enabledApis = MutableStateFlow(setOf(ApiSource.DEEPSEEK))
        )

        setContent {
            ScreenTestTheme(isDark = true) {
                HistoryScreen(
                    viewModel = viewModel,
                    language = AppLanguage.PT,
                    onBack = {},
                    focusedSource = ApiSource.DEEPSEEK,
                    showSourceSelector = false
                )
            }
        }

        waitUntil(timeoutMillis = 5_000) {
            runCatching {
                onNodeWithText("Saldo restante").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }

        onNodeWithText("Histórico do DeepSeek").assertIsDisplayed()
        onNodeWithText("Saldo restante").assertIsDisplayed()
        onNodeWithText("Saldo atual").assertIsDisplayed()
        onNodeWithText("Gasto no período").assertIsDisplayed()
        onNodeWithText("Ritmo médio").assertIsDisplayed()
        onNodeWithText("Última coleta").assertIsDisplayed()
        onNodeWithText("\$4.66").assertIsDisplayed()
        onAllNodesWithText("Uso atual").assertCountEquals(0)
        onAllNodesWithText("Quota intervalar").assertCountEquals(0)
        viewModel.onDestroy()
    }

    @Test
    fun `HistoryScreen renders MiniMax request metrics as counts instead of rounded percentage`() = runDesktopComposeUiTest(height = HISTORY_SCENE_HEIGHT) {
        val report = com.usagemonitor.domain.entity.ApiUsageHistoryReport(
            source = ApiSource.MINIMAX,
            range = HistoryRange.LAST_30_DAYS,
            lastUpdatedAt = Instant.parse("2026-05-06T22:02:00Z"),
            series = listOf(
                UsageHistorySeries(
                    quotaLabel = "MiniMax-M*",
                    periodType = PeriodType.INTERVAL,
                    unit = UsageUnit.REQUESTS,
                    points = listOf(
                        UsageHistoryPoint(
                            capturedAt = Instant.parse("2026-04-28T19:00:00Z"),
                            used = 16,
                            total = 4500,
                            rawUsed = 0,
                            rawTotal = 0,
                            periodEndAt = Instant.parse("2026-05-07T00:00:00Z")
                        ),
                        UsageHistoryPoint(
                            capturedAt = Instant.parse("2026-05-06T22:02:00Z"),
                            used = 16,
                            total = 4500,
                            rawUsed = 0,
                            rawTotal = 0,
                            periodEndAt = Instant.parse("2026-05-07T00:00:00Z")
                        )
                    ),
                    currentDisplayUsed = 16,
                    currentDisplayTotal = 4500,
                    deltaDisplayUsed = 0,
                    averageDisplayConsumptionPerHour = 0.0,
                    currentPeriodEndAt = Instant.parse("2026-05-07T00:00:00Z"),
                    forecast = UsageForecast.ResetsBeforeExhaustion,
                    riskSummary = null
                )
            )
        )

        val viewModel = HistoryViewModel(
            getUsageHistory = com.usagemonitor.domain.usecase.GetUsageHistoryUseCase(
                repository = object : com.usagemonitor.domain.repository.UsageHistoryRepository {
                    override suspend fun recordSnapshot(
                        stats: com.usagemonitor.domain.entity.ApiUsageStats,
                        capturedAt: Instant
                    ) = Unit

                    override suspend fun getHistoryReport(
                        source: ApiSource,
                        range: HistoryRange,
                        now: Instant
                    ): com.usagemonitor.domain.entity.ApiUsageHistoryReport {
                        return report
                    }
                }
            ),
            enabledApis = MutableStateFlow(setOf(ApiSource.MINIMAX))
        )

        setContent {
            ScreenTestTheme(isDark = true) {
                HistoryScreen(
                    viewModel = viewModel,
                    language = AppLanguage.PT,
                    onBack = {},
                    focusedSource = ApiSource.MINIMAX,
                    showSourceSelector = false
                )
            }
        }

        waitUntil(timeoutMillis = 5_000) {
            runCatching {
                onNodeWithText("MiniMax-M*").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }

        onAllNodesWithText("16/4", substring = true).assertCountEquals(1)
        onNodeWithText("0 req").assertIsDisplayed()
        onNodeWithText("0 req/h").assertIsDisplayed()
        onAllNodesWithText("0 / 100 %").assertCountEquals(0)
        viewModel.onDestroy()
    }

    @Test
    fun `forecast metric wraps instead of being clipped in a narrow column`() = runDesktopComposeUiTest {
        val forecast = "A janela deve reiniciar antes do limite"
        setContent {
            ScreenTestTheme(isDark = true) {
                Box(modifier = Modifier.width(520.dp)) {
                    HistoryMetricTable(
                        entries = listOf(
                            HistoryMetricEntry("Uso atual", "44 / 100 %"),
                            HistoryMetricEntry("Consumido no período", "23 %"),
                            HistoryMetricEntry("Média por hora", "1 %/h"),
                            HistoryMetricEntry("Previsão", forecast)
                        )
                    )
                }
            }
        }

        // Com `maxLines = 1` o nó tinha a altura de uma linha e o texto saía
        // cortado (issue #320); a altura é o que prova a segunda linha.
        val oneLine = onNodeWithText("1 %/h").fetchSemanticsNode().size.height
        val wrapped = onNodeWithText(forecast).fetchSemanticsNode().size.height
        assertTrue(wrapped > oneLine * 3 / 2, "previsão com $wrapped px contra $oneLine px de uma linha")
    }

    private fun twoWindowSeries(base: String, periodType: PeriodType, used: Long) = UsageHistorySeries(
        quotaLabel = if (periodType == PeriodType.INTERVAL) "$base 5h" else "$base 7d",
        periodType = periodType,
        unit = UsageUnit.PERCENTAGE,
        points = listOf(
            UsageHistoryPoint(
                capturedAt = Instant.parse("2026-05-07T11:32:00Z"),
                used = used / 2,
                total = 100,
                rawUsed = 0,
                rawTotal = 0,
                periodEndAt = Instant.parse("2026-05-07T14:33:00Z")
            ),
            UsageHistoryPoint(
                capturedAt = Instant.parse("2026-05-07T13:32:00Z"),
                used = used,
                total = 100,
                rawUsed = 0,
                rawTotal = 0,
                periodEndAt = Instant.parse("2026-05-07T14:33:00Z")
            )
        ),
        currentDisplayUsed = used,
        currentDisplayTotal = 100,
        deltaDisplayUsed = used / 2,
        averageDisplayConsumptionPerHour = 1.0,
        currentPeriodEndAt = Instant.parse("2026-05-07T14:33:00Z"),
        forecast = UsageForecast.InsufficientData,
        riskSummary = null
    )

    private fun historyViewModelFor(report: com.usagemonitor.domain.entity.ApiUsageHistoryReport) = HistoryViewModel(
        getUsageHistory = com.usagemonitor.domain.usecase.GetUsageHistoryUseCase(
            repository = object : com.usagemonitor.domain.repository.UsageHistoryRepository {
                override suspend fun recordSnapshot(stats: ApiUsageStats, capturedAt: Instant) = Unit

                override suspend fun getHistoryReport(
                    source: ApiSource,
                    range: HistoryRange,
                    now: Instant
                ): com.usagemonitor.domain.entity.ApiUsageHistoryReport = report
            }
        ),
        enabledApis = MutableStateFlow(setOf(report.source))
    )

    @Test
    fun `quota selector switches the Claude card between 5h, 7d and both`() = runDesktopComposeUiTest(height = HISTORY_SCENE_HEIGHT) {
        val report = com.usagemonitor.domain.entity.ApiUsageHistoryReport(
            source = ApiSource.ANTHROPIC,
            range = HistoryRange.LAST_24_HOURS,
            lastUpdatedAt = Instant.parse("2026-05-07T13:32:00Z"),
            series = listOf(
                twoWindowSeries("Claude", PeriodType.INTERVAL, 40),
                twoWindowSeries("Claude", PeriodType.WEEKLY, 20)
            )
        )
        val viewModel = historyViewModelFor(report)
        setContent {
            ScreenTestTheme(isDark = true) {
                HistoryScreen(viewModel = viewModel, language = AppLanguage.PT, onBack = {}, showSourceSelector = false)
            }
        }
        waitUntil(timeoutMillis = 5_000) {
            runCatching { onNodeWithTag(historyQuotaViewChipTag(HistoryQuotaView.BOTH)).fetchSemanticsNode(); true }
                .getOrDefault(false)
        }

        onNodeWithTag(historyQuotaViewChipTag(HistoryQuotaView.BOTH)).assertIsSelected()
        onNodeWithText("Cota intervalar atual").assertIsDisplayed()
        onNodeWithText("Cota semanal atual").assertIsDisplayed()

        onNodeWithTag(historyQuotaViewChipTag(HistoryQuotaView.WEEKLY)).performClick()
        waitForIdle()
        onAllNodesWithText("Cota intervalar atual").assertCountEquals(0)
        onNodeWithText("Cota semanal atual").assertIsDisplayed()

        onNodeWithTag(historyQuotaViewChipTag(HistoryQuotaView.INTERVAL)).performClick()
        waitForIdle()
        onNodeWithText("Cota intervalar atual").assertIsDisplayed()
        onAllNodesWithText("Cota semanal atual").assertCountEquals(0)
        viewModel.onDestroy()
    }

    @Test
    fun `Codex 5h and 7d share one card with the quota selector`() = runDesktopComposeUiTest(height = HISTORY_SCENE_HEIGHT) {
        val report = com.usagemonitor.domain.entity.ApiUsageHistoryReport(
            source = ApiSource.CODEX,
            range = HistoryRange.LAST_24_HOURS,
            lastUpdatedAt = Instant.parse("2026-05-07T13:32:00Z"),
            series = listOf(
                twoWindowSeries("Codex", PeriodType.INTERVAL, 40),
                twoWindowSeries("Codex", PeriodType.WEEKLY, 20)
            )
        )
        val viewModel = historyViewModelFor(report)
        setContent {
            ScreenTestTheme(isDark = true) {
                HistoryScreen(viewModel = viewModel, language = AppLanguage.PT, onBack = {}, showSourceSelector = false)
            }
        }
        waitUntil(timeoutMillis = 5_000) {
            runCatching { onNodeWithText("Codex").fetchSemanticsNode(); true }.getOrDefault(false)
        }

        onNodeWithText("Codex").assertIsDisplayed()
        onAllNodesWithText("Codex 5h").assertCountEquals(0)
        onAllNodesWithText("Codex 7d").assertCountEquals(0)
        onNodeWithTag(historyQuotaViewChipTag(HistoryQuotaView.BOTH)).assertIsSelected()
        viewModel.onDestroy()
    }

    @Test
    fun `quota selector is hidden when the source has a single window`() = runDesktopComposeUiTest(height = HISTORY_SCENE_HEIGHT) {
        val report = com.usagemonitor.domain.entity.ApiUsageHistoryReport(
            source = ApiSource.ANTHROPIC,
            range = HistoryRange.LAST_24_HOURS,
            lastUpdatedAt = Instant.parse("2026-05-07T13:32:00Z"),
            series = listOf(twoWindowSeries("Claude", PeriodType.INTERVAL, 40))
        )
        val viewModel = historyViewModelFor(report)
        setContent {
            ScreenTestTheme(isDark = true) {
                HistoryScreen(viewModel = viewModel, language = AppLanguage.PT, onBack = {}, showSourceSelector = false)
            }
        }
        waitUntil(timeoutMillis = 5_000) {
            runCatching { onNodeWithText("Claude").fetchSemanticsNode(); true }.getOrDefault(false)
        }

        onAllNodesWithText("Ambas").assertCountEquals(0)
        viewModel.onDestroy()
    }
}
