package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.presentation.ui.components.ObservedUsageSummary
import com.usagemonitor.presentation.ui.components.observedActivityTrackTag
import kotlin.test.Test
import kotlin.time.Instant

/**
 * O bloco de atividade observada das fontes sem cota oficial (OpenCode, Kilo,
 * Gemini): vazio com orientação por fonte, e uma linha por modelo com as duas
 * janelas.
 */
@OptIn(ExperimentalTestApi::class)
class ObservedUsageSummaryTest {

    private val endsAt = Instant.parse("2026-10-27T20:00:00Z")

    private fun quota(label: String, used: Long, unit: UsageUnit = UsageUnit.REQUESTS) = QuotaInfo(
        label = label,
        used = used,
        total = 0L,
        periodEndAt = endsAt,
        periodType = if (label.endsWith(" 7d")) PeriodType.WEEKLY else PeriodType.INTERVAL,
        unit = unit
    )

    private fun androidx.compose.ui.test.ComposeUiTest.show(
        source: ApiSource,
        quotas: List<QuotaInfo>,
        language: AppLanguage,
        compact: Boolean = false
    ) {
        setContent {
            ScreenTestTheme {
                Box(Modifier.width(520.dp)) {
                    ObservedUsageSummary(source = source, quotas = quotas, language = language, compact = compact)
                }
            }
        }
    }

    @Test
    fun `empty free source explains how to start`() = runDesktopComposeUiTest {
        show(ApiSource.OPENCODE, emptyList(), AppLanguage.EN)

        onNodeWithText("No free usage detected").assertExists()
        onNodeWithText("Use a free OpenCode Zen Free model to start populating this card.", substring = true).assertExists()
    }

    @Test
    fun `empty gemini talks about tokens, not free models`() = runDesktopComposeUiTest {
        show(ApiSource.GEMINI, emptyList(), AppLanguage.PT)

        onNodeWithText("Nenhum token registrado").assertExists()
        onNodeWithText("O uso aparece quando o Gemini CLI registra sessões locais com contagem de tokens.").assertExists()
    }

    @Test
    fun `each model gets one row with both windows`() = runDesktopComposeUiTest {
        show(
            ApiSource.KILO,
            listOf(quota("glm-5 5h", 3L), quota("glm-5 7d", 12L), quota("kimi-k3 5h", 1L)),
            AppLanguage.PT
        )

        onNodeWithText("glm-5").assertExists()
        onNodeWithText("kimi-k3").assertExists()
        onAllNodesWithText("Limite oficial indisponível").assertCountEquals(2)
    }

    @Test
    fun `gemini rows say the account quota is separate`() = runDesktopComposeUiTest {
        show(
            ApiSource.GEMINI,
            listOf(quota("gemini-3-pro 5h", 4_000L, UsageUnit.TOKENS), quota("gemini-3-pro 7d", 9_000L, UsageUnit.TOKENS)),
            AppLanguage.EN
        )

        onNodeWithText("gemini-3-pro").assertExists()
        onNodeWithText("Local metric; account quota is separate").assertExists()
    }

    @Test
    fun `compact rows keep the model name`() = runDesktopComposeUiTest {
        show(ApiSource.OPENCODE, listOf(quota("big-pickle 5h", 2L), quota("big-pickle 7d", 5L)), AppLanguage.EN, compact = true)

        onNodeWithText("big-pickle").assertExists()
        onAllNodesWithText("Official limit unavailable").assertCountEquals(1)
    }

    @Test
    fun `full rows draw a track per window`() = runDesktopComposeUiTest {
        show(ApiSource.OPENCODE, listOf(quota("big-pickle 5h", 2L), quota("big-pickle 7d", 5L)), AppLanguage.PT)

        onNodeWithTag(observedActivityTrackTag("big-pickle", "5h"), useUnmergedTree = true).assertExists()
    }
}
