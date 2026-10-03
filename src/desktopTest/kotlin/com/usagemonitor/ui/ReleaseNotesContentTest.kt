package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.ReleaseNotes
import com.usagemonitor.presentation.ui.RELEASE_NOTES_BETA_BADGE_TAG
import com.usagemonitor.presentation.ui.ReleaseNotesContent
import com.usagemonitor.presentation.ui.releaseNotesSubtitle
import com.usagemonitor.presentation.ui.releaseNotesTitle
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class ReleaseNotesContentTest {

    @Test
    fun `lists what changed under a heading that does not repeat the frame title`() = runDesktopComposeUiTest {
        showNotes(notes())

        // A versão fica na barra de título da moldura; repeti-la aqui gastaria
        // a altura que a lista precisa.
        onNodeWithText("Novidades").assertIsDisplayed()
        onNodeWithText("prune the applied artifact").assertIsDisplayed()
        onNodeWithText("stop killing every JVM on the machine").assertIsDisplayed()
    }

    @Test
    fun `the frame title carries the version`() {
        assertEquals("Novidades da versão 39.0.0", releaseNotesTitle("39.0.0", isPt = true))
        assertEquals("What's new in 39.0.0", releaseNotesTitle("39.0.0", isPt = false))
    }

    /** Issue #355: a beta diz que é beta no título da moldura e com um selo no cabeçalho. */
    @Test
    fun `a beta release is named and badged as beta`() = runDesktopComposeUiTest {
        assertEquals("Novidades da versão beta 39.0.0-beta.1", releaseNotesTitle("39.0.0-beta.1", isPt = true))
        assertEquals("What's new in beta 39.0.0-beta.1", releaseNotesTitle("39.0.0-beta.1", isPt = false))

        showNotes(notes(version = "39.0.0-beta.1"))

        onNodeWithTag(RELEASE_NOTES_BETA_BADGE_TAG).assertIsDisplayed()
        onNodeWithText("Beta").assertIsDisplayed()
    }

    @Test
    fun `a stable release has no beta badge`() = runDesktopComposeUiTest {
        showNotes(notes())

        onNodeWithTag(RELEASE_NOTES_BETA_BADGE_TAG).assertDoesNotExist()
    }

    @Test
    fun `says where the update came from and when it was published`() = runDesktopComposeUiTest {
        showNotes(notes())

        onNodeWithText("Atualizado de 37.0.0 · 24/08/2026").assertIsDisplayed()
    }

    @Test
    fun `both buttons report their events`() = runDesktopComposeUiTest {
        var opened = 0
        var closed = 0
        showNotes(notes(), onOpenReleasePage = { opened++ }, onClose = { closed++ })

        onNodeWithText("Ver no GitHub").performClick()
        onNodeWithText("Fechar").performClick()

        assertEquals(1, opened)
        assertEquals(1, closed)
    }

    @Test
    fun `english translates the chrome and leaves the items alone`() = runDesktopComposeUiTest {
        // Os itens são o assunto do commit, e o app não traduz texto de commit.
        showNotes(notes(), language = AppLanguage.EN)

        onNodeWithText("What's new").assertIsDisplayed()
        onNodeWithText("Updated from 37.0.0 · 2026-08-24").assertIsDisplayed()
        onNodeWithText("Close").assertIsDisplayed()
        onNodeWithText("prune the applied artifact").assertIsDisplayed()
    }

    @Test
    fun `without a previous version or a date there is no subtitle`() {
        // Meia frase pendurada é pior que frase nenhuma.
        val bare = notes(previousVersion = null, publishedAt = null)

        assertNull(releaseNotesSubtitle(bare, isPt = true))
    }

    @Test
    fun `a known previous version alone still makes a subtitle`() {
        val withoutDate = notes(publishedAt = null)

        assertEquals("Atualizado de 37.0.0", releaseNotesSubtitle(withoutDate, isPt = true))
    }

    private fun notes(
        previousVersion: String? = "37.0.0",
        publishedAt: Instant? = Instant.parse("2026-08-24T21:15:00Z"),
        version: String = "39.0.0"
    ) = ReleaseNotes(
        version = version,
        previousVersion = previousVersion,
        publishedAt = publishedAt,
        releasePageUrl = "https://github.com/edilsonvilarinho/usage-monitor/releases/tag/v39.0.0",
        items = listOf("prune the applied artifact", "stop killing every JVM on the machine")
    )

    private fun androidx.compose.ui.test.ComposeUiTest.showNotes(
        notes: ReleaseNotes,
        language: AppLanguage = AppLanguage.PT,
        onOpenReleasePage: () -> Unit = {},
        onClose: () -> Unit = {}
    ) {
        setContent {
            ScreenTestTheme(isDark = true) {
                Box(modifier = Modifier.size(width = 560.dp, height = 520.dp)) {
                    ReleaseNotesContent(
                        notes = notes,
                        language = language,
                        onOpenReleasePage = onOpenReleasePage,
                        onClose = onClose
                    )
                }
            }
        }
    }
}
