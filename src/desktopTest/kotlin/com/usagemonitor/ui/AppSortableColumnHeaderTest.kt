package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.components.AppColumnHeaderLabel
import com.usagemonitor.presentation.ui.components.AppSortableColumnHeaderLabel
import com.usagemonitor.presentation.ui.components.SORTABLE_HEADER_ARROW_ASC_TAG
import com.usagemonitor.presentation.ui.components.SORTABLE_HEADER_ARROW_DESC_TAG
import com.usagemonitor.presentation.ui.theme.AppMotion
import com.usagemonitor.presentation.ui.theme.AppMotionPolicy
import com.usagemonitor.presentation.ui.theme.AppSpacing
import com.usagemonitor.presentation.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private const val CELL_TAG = "sortable-header-cell"

@OptIn(ExperimentalTestApi::class)
class AppSortableColumnHeaderTest {

    @Test
    fun `click calls onClick`() = runDesktopComposeUiTest {
        var clicks = 0
        setContent {
            AppTheme(isDark = true) {
                Box(Modifier.width(120.dp)) {
                    AppSortableColumnHeaderLabel(
                        label = "Tokens",
                        sortDescending = null,
                        onClick = { clicks += 1 },
                        descendingStateDescription = "maior primeiro",
                        ascendingStateDescription = "menor primeiro"
                    )
                }
            }
        }

        onNodeWithText("Tokens").assertHasClickAction().performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun `active descending column writes the descending state and only the down arrow`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(Modifier.width(120.dp)) {
                    AppSortableColumnHeaderLabel(
                        label = "Tokens",
                        sortDescending = true,
                        onClick = {},
                        descendingStateDescription = "maior primeiro",
                        ascendingStateDescription = "menor primeiro"
                    )
                }
            }
        }

        onNodeWithText("Tokens").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "maior primeiro")
        )
        assertEquals(1, onAllNodesWithTag(SORTABLE_HEADER_ARROW_DESC_TAG, useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(0, onAllNodesWithTag(SORTABLE_HEADER_ARROW_ASC_TAG, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun `active ascending column writes the ascending state and only the up arrow`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(Modifier.width(120.dp)) {
                    AppSortableColumnHeaderLabel(
                        label = "Tokens",
                        sortDescending = false,
                        onClick = {},
                        descendingStateDescription = "maior primeiro",
                        ascendingStateDescription = "menor primeiro"
                    )
                }
            }
        }

        onNodeWithText("Tokens").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "menor primeiro")
        )
        assertEquals(1, onAllNodesWithTag(SORTABLE_HEADER_ARROW_ASC_TAG, useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(0, onAllNodesWithTag(SORTABLE_HEADER_ARROW_DESC_TAG, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun `inactive column has no arrow and no state description`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(Modifier.width(120.dp)) {
                    AppSortableColumnHeaderLabel(
                        label = "Tokens",
                        sortDescending = null,
                        onClick = {},
                        descendingStateDescription = "maior primeiro",
                        ascendingStateDescription = "menor primeiro"
                    )
                }
            }
        }

        onNodeWithText("Tokens").assert(
            SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription)
        )
        assertEquals(0, onAllNodesWithTag(SORTABLE_HEADER_ARROW_DESC_TAG, useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(0, onAllNodesWithTag(SORTABLE_HEADER_ARROW_ASC_TAG, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun `header is exposed as a button in every state`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(Modifier.width(120.dp)) {
                    AppSortableColumnHeaderLabel(
                        label = "Tokens",
                        sortDescending = true,
                        onClick = {},
                        descendingStateDescription = "maior primeiro",
                        ascendingStateDescription = "menor primeiro"
                    )
                }
            }
        }

        onNodeWithText("Tokens").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        )
    }

    /**
     * Movimento: sob Reduced a cor chega ao alvo em 2 quadros (~32 ms, abaixo dos 120 ms de AppMotion.fast).
     * Controle: sob Static, com os mesmos 2 quadros, a cor ainda não chegou ao alvo.
     */
    @Test
    fun `reduced motion lands the label colour within two frames`() = runDesktopComposeUiTest {
        var sortDescending by mutableStateOf<Boolean?>(null)
        var finalColor: Color = Color.Unspecified
        mainClock.autoAdvance = false
        setContent {
            AppTheme(isDark = true, motion = AppMotionPolicy.Reduced) {
                finalColor = MaterialTheme.colorScheme.onSurface
                Box(Modifier.width(120.dp)) {
                    AppSortableColumnHeaderLabel(
                        label = "Tokens",
                        sortDescending = sortDescending,
                        onClick = {},
                        descendingStateDescription = "maior primeiro",
                        ascendingStateDescription = "menor primeiro"
                    )
                }
            }
        }

        sortDescending = true
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        waitForIdle()

        val layouts = mutableListOf<TextLayoutResult>()
        onNodeWithText("Tokens", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(finalColor, layouts.single().layoutInput.style.color)
    }

    @Test
    fun `static motion control is still mid-transition after the same two frames`() = runDesktopComposeUiTest {
        var sortDescending by mutableStateOf<Boolean?>(null)
        var finalColor: Color = Color.Unspecified
        mainClock.autoAdvance = false
        setContent {
            AppTheme(isDark = true, motion = AppMotionPolicy.Static) {
                finalColor = MaterialTheme.colorScheme.onSurface
                Box(Modifier.width(120.dp)) {
                    AppSortableColumnHeaderLabel(
                        label = "Tokens",
                        sortDescending = sortDescending,
                        onClick = {},
                        descendingStateDescription = "maior primeiro",
                        ascendingStateDescription = "menor primeiro"
                    )
                }
            }
        }

        sortDescending = true
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        waitForIdle()

        val layouts = mutableListOf<TextLayoutResult>()
        onNodeWithText("Tokens", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertNotEquals(finalColor, layouts.single().layoutInput.style.color)
    }

    /**
     * Truncamento nas larguras reais, medido pela largura intrínseca contra a disponível: `isLineEllipsized` não acusou a seta no fluxo.
     * Controle: `AppColumnHeaderLabel` na mesma largura, sem seta; tem de caber também.
     */
    @Test
    fun `label is not ellipsized at the real column widths in both states`() {
        val cases = listOf(
            Triple("Tokens (with cache)", 136, null),
            Triple("Tokens (with cache)", 136, true),
            Triple("Active time", 84, null),
            Triple("Active time", 84, true),
            Triple("Throughput", 76, null),
            Triple("Throughput", 76, false),
            Triple("Tempo ativo", 84, null),
            Triple("Tempo ativo", 84, true)
        )
        cases.forEach { (label, width, sortDescending) ->
            runDesktopComposeUiTest {
                setContent {
                    AppTheme(isDark = true) {
                        Box(Modifier.width(width.dp)) {
                            AppSortableColumnHeaderLabel(
                                label = label,
                                sortDescending = sortDescending,
                                onClick = {},
                                descendingStateDescription = "maior primeiro",
                                ascendingStateDescription = "menor primeiro"
                            )
                        }
                    }
                }

                val layouts = mutableListOf<TextLayoutResult>()
                onNodeWithText(label, useUnmergedTree = true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                val layout = layouts.single()
                val intrinsic = layout.multiParagraph.intrinsics.maxIntrinsicWidth
                assertTrue(
                    intrinsic <= layout.size.width,
                    "rótulo '$label' trunca em ${width}dp (sortDescending=$sortDescending): maxIntrinsicWidth=$intrinsic, largura=${layout.size.width}"
                )
            }
        }
    }

    @Test
    fun `control label without arrow fits the same widths`() {
        val cases = listOf(
            "Tokens (with cache)" to 136,
            "Active time" to 84,
            "Throughput" to 76,
            "Tempo ativo" to 84
        )
        cases.forEach { (label, width) ->
            runDesktopComposeUiTest {
                setContent {
                    AppTheme(isDark = true) {
                        Box(Modifier.width(width.dp)) {
                            AppColumnHeaderLabel(label = label)
                        }
                    }
                }

                val layouts = mutableListOf<TextLayoutResult>()
                onNodeWithText(label, useUnmergedTree = true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                val layout = layouts.single()
                val intrinsic = layout.multiParagraph.intrinsics.maxIntrinsicWidth
                assertTrue(
                    intrinsic <= layout.size.width,
                    "controle '$label' trunca em ${width}dp: maxIntrinsicWidth=$intrinsic, largura=${layout.size.width}"
                )
            }
        }
    }

    /**
     * Posição da seta: começa depois do fim do rótulo e sai no máximo `AppSpacing.md` além da célula.
     * Com "Tokens (with cache)" a 136dp, ativa.
     */
    @Test
    fun `arrow starts after the label end and stays within one spacing gap of the cell`() = runDesktopComposeUiTest {
        var scale = 1f
        setContent {
            scale = LocalDensity.current.density
            AppTheme(isDark = true) {
                Box(Modifier.width(136.dp).testTag(CELL_TAG)) {
                    AppSortableColumnHeaderLabel(
                        label = "Tokens (with cache)",
                        sortDescending = true,
                        onClick = {},
                        descendingStateDescription = "maior primeiro",
                        ascendingStateDescription = "menor primeiro"
                    )
                }
            }
        }

        val text = onNodeWithText("Tokens (with cache)", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val arrow = onNodeWithTag(SORTABLE_HEADER_ARROW_DESC_TAG, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val cell = onNodeWithTag(CELL_TAG).fetchSemanticsNode().boundsInRoot
        val gapPx = AppSpacing.md.value * scale

        assertTrue(
            arrow.left >= text.right,
            "seta começa antes do fim do rótulo: arrow.left=${arrow.left} text.right=${text.right}"
        )
        assertTrue(
            arrow.right <= cell.right + gapPx,
            "seta sai além do vão de AppSpacing.md: arrow.right=${arrow.right} cell.right=${cell.right} gap=$gapPx"
        )
    }
}
