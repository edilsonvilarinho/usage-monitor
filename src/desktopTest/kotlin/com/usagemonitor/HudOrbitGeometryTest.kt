package com.usagemonitor

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.ui.HudAccount
import com.usagemonitor.presentation.ui.HudQuota
import com.usagemonitor.presentation.ui.components.AppTone
import kotlin.math.hypot
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Geometria pura do campo orbital do modo flutuante (issue #619, A04 e A08e1).
 *
 * Padrões do dono em `prototipos/issue-619/parametros-dono.json`, dobrados na A08e1 (o app real ficou pequeno em 1x).
 * O campo cresce até caber os rótulos; a órbita abre o raio até os rótulos não baterem uns nos outros nem nos anéis.
 * A geometria não mede texto: quem tem Canvas entrega a largura medida.
 */
class HudOrbitGeometryTest {

    private val labelWidths = HudOrbitLabelWidths(complete = 60f, compact = 30f)

    /** Padrões do dono dobrados (A08e1): se um valor mudar sem o JSON, o modo deixa de ser o aprovado. */
    @Test
    fun `defaults are the owner values`() {
        val p = HudOrbitParams()
        assertEquals(360.0, p.fieldD.toDouble(), "fieldD")
        assertEquals(80.0, p.orbR.toDouble(), "orbR")
        assertEquals(56.0, p.accD.toDouble(), "accD")
        assertEquals(16.0, p.markD.toDouble(), "markD")
        assertEquals(64.0, p.holeD.toDouble(), "holeD")
        assertEquals(29.0, p.tiltPercent.toDouble(), "tilt")
        assertEquals(3.0, p.diskWidthPercent.toDouble(), "diskW")
        assertEquals(3.5, p.diskSpinSeconds.toDouble(), "spin")
        assertEquals(180.0, p.orbitPeriodSeconds.toDouble(), "orbS")
        assertEquals(HudOrbitLabels.FULL, p.labels, "rotulos")
        assertEquals(16.0, p.textSp.toDouble(), "txt")
    }

    /** Sentido horário na tela (y para baixo): a primeira conta no topo, depois direita, base, esquerda. */
    @Test
    fun `first account starts at minus 90 degrees and the ring runs clockwise`() {
        val layout = hudOrbitLayout(HudOrbitParams(), 4, labelWidths, 0f)
        assertEquals(listOf(-90.0, 0.0, 90.0, 180.0), layout.accounts.map { it.angleDegrees.toDouble() }, "angulos")
        assertNear(0f, layout.accounts[0].x, "x0")
        assertNear(-80f, layout.accounts[0].y, "y0")
        assertNear(80f, layout.accounts[1].x, "x1")
        assertNear(0f, layout.accounts[1].y, "y1")
        assertNear(0f, layout.accounts[2].x, "x2")
        assertNear(80f, layout.accounts[2].y, "y2")
        assertNear(-80f, layout.accounts[3].x, "x3")
        assertNear(0f, layout.accounts[3].y, "y3")
    }

    /** Passo de 360/n entre contas consecutivas, para n ímpar também. */
    @Test
    fun `angular step is 360 divided by the account count`() {
        for (n in listOf(3, 5)) {
            val angles = hudOrbitLayout(HudOrbitParams(), n, labelWidths, 0f).accounts.map { it.angleDegrees }
            assertEquals(n, angles.size, "quantidade para n=$n")
            for (i in 1 until n) {
                assertNear(360f / n, angles[i] - angles[i - 1], "passo entre $i e ${i - 1} para n=$n")
            }
        }
    }

    /** A ordem das contas é a ordem da lista, sem reordenar por nome, cor ou valor. */
    @Test
    fun `slots keep the list order`() {
        val layout = hudOrbitLayout(HudOrbitParams(), 5, labelWidths, 0f)
        assertEquals(listOf(0, 1, 2, 3, 4), layout.accounts.map { it.index }, "ordem")
    }

    /** A fase da órbita gira o anel: 0,25 de volta adiciona 90 graus; uma volta inteira é a mesma posição. */
    @Test
    fun `orbit phase rotates the ring and a full turn is the same layout`() {
        val shifted = hudOrbitLayout(HudOrbitParams(), 4, labelWidths, 0.25f)
        assertEquals(4, shifted.accounts.size, "quantidade com fase")
        assertNear(0f, shifted.accounts[0].angleDegrees, "fase 0,25 conta 0")
        assertNear(90f, shifted.accounts[1].angleDegrees, "fase 0,25 conta 1")
        assertEquals(
            hudOrbitLayout(HudOrbitParams(), 4, labelWidths, 0f),
            hudOrbitLayout(HudOrbitParams(), 4, labelWidths, 1f),
            "volta inteira",
        )
    }

    /** Sem contas não há rótulo para medir: o campo fica no tamanho pedido. */
    @Test
    fun `no accounts means no slots and no growth`() {
        val layout = hudOrbitLayout(HudOrbitParams(), 0, labelWidths, 0f)
        assertEquals(emptyList(), layout.accounts, "contas")
        assertNear(360f, layout.fieldDiameter, "campo sem contas")
    }

    /** Rótulo compacto (só a cota) cabe no campo padrão: não cresce. */
    @Test
    fun `compact labels keep the owner field size`() {
        val layout = hudOrbitLayout(HudOrbitParams(labels = HudOrbitLabels.COMPACT), 4, labelWidths, 0f)
        assertNear(360f, layout.fieldDiameter, "campo compacto")
    }

    /** Rótulo completo com largura grande cresce o campo além do tamanho do dono. */
    @Test
    fun `complete labels grow the field to fit`() {
        val wide = hudOrbitLayout(HudOrbitParams(), 4, HudOrbitLabelWidths(complete = 120f, compact = 30f), 0f)
        assertTrue(wide.fieldDiameter > 360f, "campo completo largo ${wide.fieldDiameter} dp")
    }

    /** Rótulo completo estreito cabe no campo do dono: não cresce. */
    @Test
    fun `complete labels with narrow width keep the owner field size`() {
        val layout = hudOrbitLayout(HudOrbitParams(), 4, HudOrbitLabelWidths(complete = 10f, compact = 10f), 0f)
        assertNear(360f, layout.fieldDiameter, "campo completo estreito")
    }

    /** Crescimento depende da largura e não da quantidade de contas. */
    @Test
    fun `field growth does not depend on the account count`() {
        val one = hudOrbitLayout(HudOrbitParams(), 1, labelWidths, 0f).fieldDiameter
        val six = hudOrbitLayout(HudOrbitParams(), 6, labelWidths, 0f).fieldDiameter
        assertNear(one, six, "um e seis contas")
    }

    /** Rótulos desligados não medem nada: campo no tamanho pedido mesmo com largura completa. */
    @Test
    fun `labels off keep the owner field size`() {
        val layout = hudOrbitLayout(HudOrbitParams(labels = HudOrbitLabels.OFF), 4, labelWidths, 0f)
        assertNear(360f, layout.fieldDiameter, "campo desligado")
    }

    /** O buraco minimizado é o `holeD`, independente das contas. */
    @Test
    fun `hole diameter is the owner hole size`() {
        assertNear(64f, hudOrbitLayout(HudOrbitParams(), 3, labelWidths, 0f).holeDiameter, "buraco")
    }

    /** Mesma entrada, mesma saída, mesmo com outra chamada no meio. */
    @Test
    fun `same input gives the same layout`() {
        val first = hudOrbitLayout(HudOrbitParams(), 5, labelWidths, 0.37f)
        hudOrbitLayout(HudOrbitParams(labels = HudOrbitLabels.COMPACT), 2, labelWidths, 0.9f)
        val second = hudOrbitLayout(HudOrbitParams(), 5, labelWidths, 0.37f)
        assertEquals(first, second, "determinismo")
    }

    /**
     * Oito contas com os rótulos reais do teste do dono (capturas `r4-app-real`): em FULL nenhuma caixa cruza outra,
     * nenhuma cruza um anel e todas ficam dentro do campo. Antes da A08e1 "OpenRouter" cobria "Normal" e
     * "Local activity" cobria "DeepSeek".
     */
    @Test
    fun `eight real account labels never overlap each other, any ring or leave the field`() {
        val params = HudOrbitParams()
        val accounts = realAccounts()
        val larguras = accounts.map { account -> hudOrbitAccountLabelWidth(account, params.labels).value }
        val layout = hudOrbitLayout(params, accounts.size, hudOrbitLabelWidths(accounts), orbitPhase = 0f)
        val boxes = hudOrbitLabelBoxes(layout, larguras, params)
        val fieldRadius = (layout.fieldDiameter / 2f).toDouble()
        val accRadius = (params.accD / 2f).toDouble()
        assertEquals(accounts.size, boxes.size, "uma caixa por conta")
        boxes.forEach { box ->
            boxes.filter { other -> other.index > box.index }.forEach { other ->
                assertFalse(overlaps(box, other), "rotulo ${box.index} sobrepoe o ${other.index}")
            }
            layout.accounts.forEach { slot ->
                assertTrue(distanceToBox(box, slot.x, slot.y) >= accRadius - EPS, "rotulo ${box.index} cruza o anel ${slot.index}")
            }
            corners(box).forEach { (x, y) ->
                assertTrue(hypot(x, y) <= fieldRadius + EPS, "rotulo ${box.index} sai do campo")
            }
        }
    }

    /**
     * Slot por fase sem montar o layout inteiro: `hudOrbitSlotAt` tem de dar a mesma conta que `hudOrbitLayout`
     * em toda fase. O campo chama isso a cada quadro, por conta, então não pode divergir do desenho do layout.
     */
    @Test
    fun `slot by phase matches the full layout for every account and phase`() {
        val params = HudOrbitParams()
        val accounts = realAccounts()
        val widths = hudOrbitLabelWidths(accounts)
        for (phase in listOf(0f, 0.25f, 0.6f, 0.999f)) {
            val layout = hudOrbitLayout(params, accounts.size, widths, phase)
            layout.accounts.forEach { slot ->
                assertEquals(slot, hudOrbitSlotAt(params, accounts.size, slot.index, phase), "slot ${slot.index} fase $phase")
            }
        }
    }

    /** A caixa do rótulo por fase bate com a caixa do layout completo, conta a conta. */
    @Test
    fun `label box by phase matches the full layout for every account and phase`() {
        val params = HudOrbitParams()
        val accounts = realAccounts()
        val widths = hudOrbitLabelWidths(accounts)
        val larguras = accounts.map { account -> hudOrbitAccountLabelWidth(account, params.labels).value }
        for (phase in listOf(0f, 0.25f, 0.6f, 0.999f)) {
            val layout = hudOrbitLayout(params, accounts.size, widths, phase)
            val boxes = hudOrbitLabelBoxes(layout, larguras, params)
            layout.accounts.forEach { slot ->
                assertEquals(
                    boxes[slot.index],
                    hudOrbitLabelBoxAt(params, accounts.size, slot.index, larguras[slot.index], phase),
                    "caixa ${slot.index} fase $phase",
                )
            }
        }
    }

    /** Fora de composição (escopo `application { }`) o diâmetro sai da mesma geometria, sem medir texto. */
    @Test
    fun `field diameter outside composition keeps owner size for compact and off labels`() {
        val accounts = sevenAccounts()
        assertNear(360f, hudOrbitFieldDiameter(accounts, HudOrbitParams(labels = HudOrbitLabels.COMPACT), AppLanguage.EN).value, "diametro compacto")
        assertNear(360f, hudOrbitFieldDiameter(accounts, HudOrbitParams(labels = HudOrbitLabels.OFF), AppLanguage.EN).value, "diametro desligado")
    }

    /** Sete contas com rótulo completo: o campo cresce além do tamanho do dono. */
    @Test
    fun `field diameter outside composition grows with full labels`() {
        // Nomes reais e longos: com "Conta N" (~47 dp) as caixas cabem nos 360 dp e o campo não cresce.
        val diameter = hudOrbitFieldDiameter(realAccounts(), HudOrbitParams(), AppLanguage.EN).value
        assertTrue(diameter > 360f, "diametro completo $diameter dp")
    }

    private fun realAccounts(): List<HudAccount> = listOf(
        account(0, "Anthropic — TRABALHO", statusLabel = "Normal"),
        account(1, "OpenCode Zen Free"),
        account(2, "OpenRouter"),
        account(3, "Local activity"),
        account(4, "DeepSeek"),
        account(5, "MiniMax — PESSOAL"),
        account(6, "Codex Extra"),
        account(7, "Anthropic — CLIENTE"),
    )

    private fun sevenAccounts(): List<HudAccount> = (0 until 7).map { index -> account(index, "Conta $index") }

    private fun account(index: Int, label: String, statusLabel: String = "Ok"): HudAccount {
        val percent = 10 + index
        val quota = HudQuota("5h", "$percent%", percent / 100f, AppTone.OK, resetText = null, hasForecast = false)
        return HudAccount(
            targetKey = UsageTargetKey(ApiSource.ANTHROPIC, "perfil-$index"),
            label = label,
            statusLabel = statusLabel,
            tone = AppTone.OK,
            quotas = listOf(quota),
            focusIndex = 0,
            sessionActive = false,
            minimized = false
        )
    }

    /** Duas caixas se cruzam só se as duas faixas, horizontal e vertical, se sobrepõem. Encostar não é cruzar. */
    private fun overlaps(a: HudOrbitLabelBox, b: HudOrbitLabelBox): Boolean =
        a.x < b.x + b.width && b.x < a.x + a.width && a.y < b.y + b.height && b.y < a.y + a.height

    /** Distância do centro do anel (`px`, `py`) até a caixa; zero se o centro está dentro dela. */
    private fun distanceToBox(box: HudOrbitLabelBox, px: Float, py: Float): Double {
        val dx = max(max(box.x - px, 0f), px - (box.x + box.width)).toDouble()
        val dy = max(max(box.y - py, 0f), py - (box.y + box.height)).toDouble()
        return hypot(dx, dy)
    }

    /** Os quatro cantos da caixa, em dp relativos ao centro do campo. */
    private fun corners(box: HudOrbitLabelBox): List<Pair<Double, Double>> {
        val xs = listOf(box.x.toDouble(), (box.x + box.width).toDouble())
        val ys = listOf(box.y.toDouble(), (box.y + box.height).toDouble())
        return xs.flatMap { x -> ys.map { y -> x to y } }
    }

    private fun assertNear(expected: Float, actual: Float, message: String) {
        assertEquals(expected.toDouble(), actual.toDouble(), 0.01, message)
    }

    private companion object {
        const val EPS = 0.01
    }
}
