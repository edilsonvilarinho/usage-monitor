package com.usagemonitor.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageNotice
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.components.CardNoticeHint
import com.usagemonitor.presentation.ui.components.staleReadingText
import kotlin.test.Test
import kotlin.test.assertEquals

/** O aviso do card em inglês e a idade da leitura mantida depois de uma falha (#269). */
@OptIn(ExperimentalTestApi::class)
class CardNoticeHintTest {

    @Test
    fun `every codex notice is described in english, in the enum order`() = runDesktopComposeUiTest {
        setContent {
            ScreenTestTheme(isDark = true) {
                CardNoticeHint(
                    notices = ApiUsageNotice.entries.toSet(),
                    source = ApiSource.CODEX,
                    language = AppLanguage.EN,
                    iconSize = 16.dp
                )
            }
        }

        onNodeWithContentDescription(
            "Notices: 7d quota unavailable in Codex weekly source " +
                "Codex usage source is unstable: the contract changed and limits may fluctuate until it stabilizes. " +
                "Usage credits missing from this snapshot. The balance on claude.ai still holds."
        ).assertExists()
    }

    @Test
    fun `outside codex an unstable source is the reading kept after a failure`() = runDesktopComposeUiTest {
        setContent {
            ScreenTestTheme(isDark = true) {
                CardNoticeHint(
                    notices = setOf(ApiUsageNotice.SOURCE_UNSTABLE),
                    source = ApiSource.MINIMAX,
                    language = AppLanguage.EN,
                    lastReadingAgeMinutes = 90,
                    iconSize = 16.dp
                )
            }
        }

        onNodeWithContentDescription(
            "Notice: The latest refresh failed. These numbers are from the last valid reading, taken 1h30 ago."
        ).assertExists()
    }

    @Test
    fun `the kept reading says its age in minutes, hours or days`() {
        assertEquals(
            "The latest refresh failed. These numbers are from the last valid reading and may be out of date.",
            staleReadingText(AppLanguage.EN, ageMinutes = null)
        )
        assertEquals(
            "A coleta mais recente falhou. Os números são da última leitura válida, coletada há menos de 1 min.",
            staleReadingText(AppLanguage.PT, ageMinutes = 0)
        )
        assertEquals(
            "The latest refresh failed. These numbers are from the last valid reading, taken less than 1 min ago.",
            staleReadingText(AppLanguage.EN, ageMinutes = 0)
        )
        assertEquals(
            "A coleta mais recente falhou. Os números são da última leitura válida, coletada há 42 min.",
            staleReadingText(AppLanguage.PT, ageMinutes = 42)
        )
        assertEquals(
            "A coleta mais recente falhou. Os números são da última leitura válida, coletada há 47h05.",
            staleReadingText(AppLanguage.PT, ageMinutes = 47 * 60 + 5)
        )
        assertEquals(
            "A coleta mais recente falhou. Os números são da última leitura válida, coletada há 3 dias.",
            staleReadingText(AppLanguage.PT, ageMinutes = 3 * 24 * 60)
        )
        assertEquals(
            "The latest refresh failed. These numbers are from the last valid reading, taken 3 days ago.",
            staleReadingText(AppLanguage.EN, ageMinutes = 3 * 24 * 60 + 59)
        )
    }
}
