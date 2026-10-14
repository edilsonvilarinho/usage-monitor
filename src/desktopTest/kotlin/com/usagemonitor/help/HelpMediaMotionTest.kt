package com.usagemonitor.help

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.usagemonitor.presentation.ui.help.HelpCatalog
import com.usagemonitor.presentation.ui.help.HelpMediaState
import com.usagemonitor.presentation.ui.help.HelpTopic
import com.usagemonitor.presentation.ui.theme.AppMotionPolicy
import com.usagemonitor.presentation.ui.theme.LocalAppMotionPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * O laço de quadros é animação contínua: com "Reduzir animações" a demo fica no
 * primeiro quadro. Antes ele ignorava a política e seguia publicando quadros.
 */
@OptIn(ExperimentalTestApi::class)
class HelpMediaMotionTest {

    @Test
    fun `with reduced motion the demo stays on its first frame`() = runDesktopComposeUiTest {
        val mediaId = HelpCatalog.mediaId(HelpTopic.entries.first())
        val clip = assertNotNull(decodeHelpMedia(assertNotNull(readHelpMediaBytes(mediaId))))
        val frameCount = clip.frameCount
        clip.close()
        assertTrue(frameCount > 1, "a demo de teste precisa ter mais de um quadro")

        val published = mutableListOf<HelpMediaState>()
        setContent {
            CompositionLocalProvider(LocalAppMotionPolicy provides AppMotionPolicy.Reduced) {
                val state = rememberHelpMedia(mediaId)
                SideEffect { published += state }
            }
        }

        waitUntil(timeoutMillis = 5_000) { published.lastOrNull() is HelpMediaState.Frame }
        mainClock.advanceTimeBy(10_000)
        waitForIdle()

        assertEquals(1, published.filterIsInstance<HelpMediaState.Frame>().distinct().size)
    }
}
