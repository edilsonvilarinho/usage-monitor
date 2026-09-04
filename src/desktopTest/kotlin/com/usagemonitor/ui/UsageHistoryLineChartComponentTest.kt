package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.UsageHistoryPoint
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.presentation.ui.components.UsageHistoryLineChart
import com.usagemonitor.presentation.ui.theme.AppTheme
import kotlinx.datetime.Instant
import kotlin.test.Test

/**
 * A linha do período anterior (issue #215) é desenhada num `Canvas`, que os
 * testes de componente não conseguem inspecionar pixel a pixel — o que dá
 * para afirmar aqui é a legenda em texto, que é o único jeito de a cor
 * sozinha não estar informando o que a linha tracejada significa.
 */
@OptIn(ExperimentalTestApi::class)
class UsageHistoryLineChartComponentTest {

    private fun point(capturedAt: String, used: Long) = UsageHistoryPoint(
        capturedAt = Instant.parse(capturedAt),
        used = used,
        total = 1000L,
        rawUsed = used,
        rawTotal = 1000L,
        periodEndAt = Instant.parse("2026-04-28T20:00:00Z")
    )

    private val currentPoints = listOf(
        point("2026-04-27T18:00:00Z", 100L),
        point("2026-04-28T17:00:00Z", 400L)
    )

    private val previousPoints = listOf(
        point("2026-04-26T18:00:00Z", 50L),
        point("2026-04-27T17:00:00Z", 300L)
    )

    @Test
    fun `chart shows the previous period legend when there are previous points`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(400.dp)) {
                    UsageHistoryLineChart(
                        points = currentPoints,
                        unit = UsageUnit.REQUESTS,
                        language = AppLanguage.PT,
                        chartSelectionKey = "codex-5h",
                        previousPoints = previousPoints
                    )
                }
            }
        }

        onNodeWithText("Tracejado: mesmo ponto do período anterior").assertIsDisplayed()
    }

    /** Sem dado anterior não há o que legendar — nada é desenhado, nada é dito. */
    @Test
    fun `chart hides the previous period legend without previous points`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(400.dp)) {
                    UsageHistoryLineChart(
                        points = currentPoints,
                        unit = UsageUnit.REQUESTS,
                        language = AppLanguage.PT,
                        chartSelectionKey = "codex-5h"
                    )
                }
            }
        }

        onAllNodesWithText("Tracejado: mesmo ponto do período anterior").assertCountEquals(0)
    }
}
