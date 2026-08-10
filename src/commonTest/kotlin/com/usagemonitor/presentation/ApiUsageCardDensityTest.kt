package com.usagemonitor.presentation

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.usagemonitor.presentation.ui.components.resolveApiUsageCardDensity
import com.usagemonitor.presentation.ui.components.shouldStackCompactQuotas
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApiUsageCardDensityTest {

    @Test
    fun `wide card keeps regular density`() {
        val density = resolveApiUsageCardDensity(360.dp)

        assertEquals(20.dp, density.contentHorizontalPadding)
        assertEquals(34.dp, density.actionButtonSize)
        assertEquals(16.dp, density.actionIconSize)
        assertEquals(92.dp, density.arcSize)
        assertEquals(18.sp, density.arcPercentageFontSize)
    }

    @Test
    fun `narrow card shrinks paddings buttons and arcs`() {
        val density = resolveApiUsageCardDensity(226.dp)

        assertEquals(12.dp, density.contentHorizontalPadding)
        assertEquals(28.dp, density.actionButtonSize)
        assertEquals(14.dp, density.actionIconSize)
        assertEquals(72.dp, density.arcSize)
        assertEquals(15.sp, density.arcPercentageFontSize)
    }

    @Test
    fun `density threshold is inclusive for the regular variant`() {
        assertEquals(34.dp, resolveApiUsageCardDensity(320.dp).actionButtonSize)
        assertEquals(28.dp, resolveApiUsageCardDensity(319.dp).actionButtonSize)
    }

    @Test
    fun `narrow density leaves room for the card title`() {
        val density = resolveApiUsageCardDensity(226.dp)
        // Bloco de ações: 3 botões + 2 gaps. Precisa caber com folga no conteúdo
        // disponível (largura do card menos os paddings horizontais).
        val actionsWidth = density.actionButtonSize * 3 + density.actionSpacing * 2
        val contentWidth = 226.dp - density.contentHorizontalPadding * 2

        assertTrue(
            contentWidth - actionsWidth >= 100.dp,
            "Sobra para o título foi ${contentWidth - actionsWidth}"
        )
    }

    @Test
    fun `compact quotas stack only when multiple quotas do not fit side by side`() {
        assertTrue(shouldStackCompactQuotas(cardWidth = 200.dp, quotaCount = 2))
        assertFalse(shouldStackCompactQuotas(cardWidth = 200.dp, quotaCount = 1))
        assertFalse(shouldStackCompactQuotas(cardWidth = 260.dp, quotaCount = 2))
        assertFalse(shouldStackCompactQuotas(cardWidth = 210.dp, quotaCount = 2))
    }
}
