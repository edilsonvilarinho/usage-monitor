package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.theme.AppTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A escala da interface é **densidade**, não tipografia: o mesmo
 * `Modifier.size(100.dp)` tem de ocupar 50% mais pixels a 150%. É o teste que
 * prova a fiação sem depender de nenhuma tela — passando ele, texto, ícone,
 * padding e alvo de clique escalam junto, porque todos saem da mesma densidade.
 *
 * A medida é em **pixels** (`boundsInRoot`), não em `Dp`: a conversão para `Dp`
 * usa a densidade do próprio nó, que é justamente a que está sendo alterada, e
 * devolveria 100dp nos dois casos — um teste que passa sem medir nada.
 */
@OptIn(ExperimentalTestApi::class)
class AppThemeScaleTest {

    @Test
    fun `a escala aumenta o tamanho em pixels na mesma razao`() {
        val neutral = measureWidthPx(100) {
            Box(modifier = Modifier.testTag(BOX_TAG).size(100.dp))
        }
        val scaled = measureWidthPx(150) {
            Box(modifier = Modifier.testTag(BOX_TAG).size(100.dp))
        }

        val ratio = scaled / neutral
        assertTrue(
            abs(ratio - 1.5f) < 0.02f,
            "Razão esperada de 1,5 entre 150% e 100%; foi $ratio ($neutral → $scaled px)"
        )
    }

    @Test
    fun `o texto acompanha a escala`() {
        val neutral = measureWidthPx(100) {
            Text(text = "115%", modifier = Modifier.testTag(BOX_TAG))
        }
        val scaled = measureWidthPx(150) {
            Text(text = "115%", modifier = Modifier.testTag(BOX_TAG))
        }

        assertTrue(
            scaled > neutral * 1.3f,
            "Texto a 150% deveria ser bem mais largo: $neutral → $scaled px"
        )
    }

    private fun measureWidthPx(
        uiScalePercent: Int,
        content: @androidx.compose.runtime.Composable () -> Unit
    ): Float {
        var width = 0f
        runDesktopComposeUiTest {
            setContent {
                AppTheme(isDark = true, uiScalePercent = uiScalePercent) {
                    content()
                }
            }
            width = onNodeWithTag(BOX_TAG).fetchSemanticsNode().boundsInRoot.width
        }
        return width
    }

    private companion object {
        const val BOX_TAG = "scaledNode"
    }
}
