package com.usagemonitor

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.ui.HudAccount
import com.usagemonitor.presentation.ui.hudBlackHoleCountLabel
import com.usagemonitor.presentation.ui.HudQuota
import com.usagemonitor.presentation.ui.components.AppTone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Geometria da coluna vertical com o buraco negro das minimizadas (issue #600, A03). */
class HudBlackHoleGeometryTest {

    private val fallback = "Carregando"

    @Test
    fun `quatro minimizadas e tres completas medem tres celulas mais uma do buraco`() {
        val completas = listOf(conta("A"), conta("B"), conta("C"))
        val minimizadas = listOf(
            conta("D", minimizada = true),
            conta("E", minimizada = true),
            conta("F", minimizada = true),
            conta("G", minimizada = true)
        )

        val base = hudNotchSizes(completas, HudEdge.LEFT, fallback, hasUpdateIndicator = false).collapsed.height
        val comBuraco = hudNotchSizes(completas + minimizadas, HudEdge.LEFT, fallback, hasUpdateIndicator = false).collapsed.height

        // Buraco = uma célula de anel + linha da contagem, mais o vão até ela.
        assertEquals(base + HUD_RING_SIZE + HUD_WORD_LINE + HUD_ITEM_GAP, comBuraco)
    }

    @Test
    fun `sem minimizadas a altura da coluna nao muda`() {
        val contas = listOf(conta("A"), conta("B"), conta("C"))

        val altura = hudNotchSizes(contas, HudEdge.LEFT, fallback, hasUpdateIndicator = false).collapsed.height

        assertEquals(verticalAlturaEsperadaSemBuraco(contas), altura)
    }

    @Test
    fun `o rotulo do buraco cabe inteiro numa linha so na largura da coluna`() {
        val completas = listOf(conta("A"), conta("B"), conta("C"))
        val minimizadas = listOf(
            conta("D", minimizada = true),
            conta("E", minimizada = true),
            conta("F", minimizada = true),
            conta("G", minimizada = true)
        )

        val largura = hudNotchSizes(completas + minimizadas, HudEdge.LEFT, fallback, hasUpdateIndicator = false).collapsed.width
        // "4 minimizadas" são 13 caracteres: uma linha mono inteira, não só a palavra mais longa (#600, protótipo).
        val rotulo = hudBlackHoleCountLabel(4, AppLanguage.PT)
        val umaLinha = wordWidth(rotulo)

        assertTrue(largura >= umaLinha + HUD_NOTCH_PADDING_ACROSS * 2, "largura=$largura rotulo=$rotulo umaLinha=$umaLinha")
    }

    @Test
    fun `so minimizadas mantem o piso de 64 dp da largura`() {
        val contas = listOf(conta("A", minimizada = true), conta("B", minimizada = true))

        val largura = hudNotchSizes(contas, HudEdge.LEFT, fallback, hasUpdateIndicator = false).collapsed.width

        assertTrue(largura >= HUD_RING_SIZE + HUD_NOTCH_PADDING_ACROSS * 2, "largura=$largura")
    }

    @Test
    fun `com buraco a regra de auto-minimizar ainda converge no teto`() {
        val contas = listOf(
            conta("A", 0.9f),
            conta("B", 0.8f),
            conta("C", 0.3f),
            conta("D", 0.1f),
            conta("E", 0.05f)
        )
        val teto: Dp = hudNotchSizes(
            contas.map { c -> if (c.label in setOf("C", "D", "E")) c.copy(minimized = true) else c },
            HudEdge.LEFT, fallback, hasUpdateIndicator = false
        ).collapsed.height

        val aplicadas = applyHudAutoMinimize(contas, HudEdge.LEFT, fallback, teto)
        val altura = hudNotchSizes(aplicadas, HudEdge.LEFT, fallback, hasUpdateIndicator = false).collapsed.height

        assertTrue(altura <= teto, "altura=$altura teto=$teto")
    }

    /** Altura de referência: a coluna sem nenhuma minimizada, medida pela mesma função. */
    private fun verticalAlturaEsperadaSemBuraco(contas: List<HudAccount>): Dp =
        verticalCollapsed(contas, fallback, compact = false).height

    private fun conta(nome: String, vararg usos: Float, minimizada: Boolean = false): HudAccount {
        return HudAccount(
            targetKey = UsageTargetKey(ApiSource.ANTHROPIC, nome),
            label = nome,
            statusLabel = "Em dia",
            tone = AppTone.OK,
            quotas = usos.map { uso ->
                HudQuota("5h", "${(uso * 100).toInt()}%", uso, AppTone.OK, resetText = null, hasForecast = true)
            },
            focusIndex = 0,
            minimized = minimizada
        )
    }
}
