package com.usagemonitor.presentation

import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.components.resolveApiUsageCardDensity
import com.usagemonitor.presentation.ui.components.shouldShowQuotaTooltip
import com.usagemonitor.presentation.ui.components.shouldStackCompactQuotas
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApiUsageCardDensityTest {

    @Test
    fun `wide card keeps regular density`() {
        val density = resolveApiUsageCardDensity(360.dp)

        assertEquals(12.dp, density.contentHorizontalPadding)
        assertEquals(26.dp, density.actionButtonSize)
        assertEquals(14.dp, density.actionIconSize)
    }

    @Test
    fun `narrow card shrinks paddings and buttons`() {
        val density = resolveApiUsageCardDensity(226.dp)

        assertEquals(8.dp, density.contentHorizontalPadding)
        assertEquals(24.dp, density.actionButtonSize)
        assertEquals(12.dp, density.actionIconSize)
    }

    @Test
    fun `density threshold is inclusive for the regular variant`() {
        assertEquals(26.dp, resolveApiUsageCardDensity(320.dp).actionButtonSize)
        assertEquals(24.dp, resolveApiUsageCardDensity(319.dp).actionButtonSize)
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

    @Test
    fun `three compact quotas need more width than two`() {
        // Card da Anthropic com a cota de créditos: o limite acompanha a
        // quantidade de badges em vez de ficar preso no valor de duas cotas.
        assertTrue(shouldStackCompactQuotas(cardWidth = 300.dp, quotaCount = 3))
        assertFalse(shouldStackCompactQuotas(cardWidth = 315.dp, quotaCount = 3))
        assertFalse(shouldStackCompactQuotas(cardWidth = 300.dp, quotaCount = 2))
    }

    /**
     * A decisão de empilhamento das cotas expandidas deixou de existir junto com
     * os arcos: a cota virou linha de largura cheia, e linha empilha por
     * construção. O que sobrou aqui vale só para os badges do card minimizado.
     */

    @Test
    fun `quota tooltip is off below the card width floor`() {
        // A janela do modo somente cards tem ~230dp de largura útil: ali o popup
        // de cinco linhas cobre o card inteiro.
        assertFalse(shouldShowQuotaTooltip(226.dp))
        assertFalse(shouldShowQuotaTooltip(319.dp))
    }

    @Test
    fun `quota tooltip floor is inclusive`() {
        assertTrue(shouldShowQuotaTooltip(320.dp))
        assertTrue(shouldShowQuotaTooltip(430.dp))
    }
}
