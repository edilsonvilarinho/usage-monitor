package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.TeamCertificateProblem
import com.usagemonitor.domain.entity.TeamConnectionFailure
import com.usagemonitor.domain.entity.TeamConnectionStage
import com.usagemonitor.presentation.ui.TeamConnectionErrorState
import com.usagemonitor.presentation.ui.TeamConnectionNotice
import com.usagemonitor.presentation.ui.TeamConnectionPath
import com.usagemonitor.presentation.ui.TeamCredentialKind
import com.usagemonitor.presentation.ui.components.AppStep
import com.usagemonitor.presentation.ui.components.AppStepList
import com.usagemonitor.presentation.ui.components.AppStepStatus
import com.usagemonitor.presentation.ui.components.AppStepTrail
import com.usagemonitor.presentation.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class AppStepTrailTest {

    private val expired = TeamConnectionFailure(
        stage = TeamConnectionStage.CERTIFICATE,
        certificateProblem = TeamCertificateProblem.EXPIRED,
        certificateNotAfter = Instant.parse("2026-10-27T15:21:43Z")
    )

    private val steps = listOf(
        AppStep("Address", AppStepStatus.DONE),
        AppStep("Certificate", AppStepStatus.FAILED, detail = "expired"),
        AppStep("Server", AppStepStatus.NOT_REACHED),
        AppStep("Key", AppStepStatus.UNKNOWN)
    )

    @Test
    fun `compact trail writes a glyph and the name of every step`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) { Box(Modifier.width(600.dp)) { AppStepTrail(steps) } }
        }

        // Cor nunca informa sozinha: cada estado tem o seu glifo escrito.
        onNodeWithText("✓ Address").assertIsDisplayed()
        onNodeWithText("× Certificate").assertIsDisplayed()
        onNodeWithText("– Server").assertIsDisplayed()
        onNodeWithText("? Key").assertIsDisplayed()
    }

    @Test
    fun `full list writes the detail of each step`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) { Box(Modifier.width(600.dp)) { AppStepList(steps) } }
        }

        onNodeWithText("Certificate").assertIsDisplayed()
        onNodeWithText("expired").assertIsDisplayed()
    }

    @Test
    fun `team error state shows the stage, the path, the advice and retries`() = runDesktopComposeUiTest {
        var retries = 0
        setContent {
            AppTheme(isDark = true) {
                Box(Modifier.width(900.dp)) {
                    TeamConnectionErrorState(
                        failure = expired,
                        fallbackMessage = "Could not reach the team server: (certificate_expired) PKIX",
                        technicalDetail = "(certificate_expired) PKIX",
                        kind = TeamCredentialKind.ACCOUNT_KEY,
                        language = AppLanguage.EN,
                        onRetry = { retries += 1 }
                    )
                }
            }
        }

        onNodeWithText("Stopped at Certificate — expired Oct 27, 12:21 BRT").assertIsDisplayed()
        onNodeWithText("✓ Connection").assertIsDisplayed()
        onNodeWithText("– Key").assertIsDisplayed()
        onNodeWithText("The server's certificate expired: whoever runs the server has to renew it. Your key was not tested.")
            .assertIsDisplayed()
        onNodeWithText("Try again").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun `unclassified failure keeps today's message and still retries`() = runDesktopComposeUiTest {
        var retries = 0
        setContent {
            AppTheme(isDark = true) {
                Box(Modifier.width(900.dp)) {
                    TeamConnectionErrorState(
                        failure = null,
                        fallbackMessage = "Could not reach the team server: parse error",
                        technicalDetail = "parse error",
                        kind = TeamCredentialKind.ACCOUNT_KEY,
                        language = AppLanguage.EN,
                        onRetry = { retries += 1 }
                    )
                }
            }
        }

        onNodeWithText("Could not reach the team server: parse error").assertIsDisplayed()
        onNodeWithText("Try again").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun `inline notice names the stage instead of the raw exception`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(Modifier.width(900.dp)) {
                    TeamConnectionNotice(
                        failure = expired,
                        fallbackMessage = "(certificate_expired) PKIX",
                        kind = TeamCredentialKind.ADMIN_TOKEN,
                        language = AppLanguage.PT
                    )
                }
            }
        }

        onNodeWithText("Parou em Certificado — venceu em 27/10 12:21 BRT").assertIsDisplayed()
    }

    @Test
    fun `full path lists every stage with its detail`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(Modifier.width(900.dp)) {
                    TeamConnectionPath(failure = expired, kind = TeamCredentialKind.ADMIN_TOKEN, language = AppLanguage.EN)
                }
            }
        }

        onNodeWithText("expired Oct 27, 12:21 BRT").assertIsDisplayed()
        onNodeWithText("Admin token").assertIsDisplayed()
    }
}
