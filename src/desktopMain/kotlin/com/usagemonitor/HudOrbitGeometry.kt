package com.usagemonitor

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.HudAccount
import com.usagemonitor.presentation.ui.quotaText
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/** Rótulos do campo orbital: nome, cota e estado (`FULL`), só a cota (`COMPACT`) ou nenhum (`OFF`). */
internal enum class HudOrbitLabels { FULL, COMPACT, OFF }

/**
 * Parâmetros do campo orbital em dp, exceto os marcados. Padrões dobrados na A08e1 (teste real do dono: o app
 * desenhava os números do protótipo em 1x). O JSON de origem, `prototipos/issue-619/parametros-dono.json`, ainda traz os valores de 1x.
 */
internal data class HudOrbitParams(
    val fieldD: Float = 360f,
    val orbR: Float = 80f,
    val accD: Float = 56f,
    val markD: Float = 16f,
    val holeD: Float = 64f,
    /** Inclinação do disco de acreção, em porcentagem: ry = rx * tilt / 100. */
    val tiltPercent: Float = 29f,
    /** Espessura do disco, em porcentagem. */
    val diskWidthPercent: Float = 3f,
    /** Segundos para uma volta do disco. */
    val diskSpinSeconds: Float = 3.5f,
    /** Segundos para uma volta da órbita das contas. */
    val orbitPeriodSeconds: Float = 180f,
    val labels: HudOrbitLabels = HudOrbitLabels.FULL,
    /** Tamanho do texto dos rótulos, em sp. */
    val textSp: Float = 16f,
)

/** Largura medida dos rótulos, em dp. A geometria não mede texto: quem tem Canvas mede e entrega o número. */
internal data class HudOrbitLabelWidths(val complete: Float, val compact: Float)

/** Posição de uma conta relativa ao centro do campo, em dp, com y para baixo como na tela. */
internal data class HudOrbitAccountSlot(val index: Int, val angleDegrees: Float, val x: Float, val y: Float)

/** Diâmetro do campo expandido, diâmetro do buraco minimizado e as contas na ordem da lista. */
internal data class HudOrbitLayout(val fieldDiameter: Float, val holeDiameter: Float, val accounts: List<HudOrbitAccountSlot>)

/** Caixa de um rótulo em dp, relativa ao centro do campo, com y para baixo. `index` é a posição da conta na lista. */
internal data class HudOrbitLabelBox(val index: Int, val x: Float, val y: Float, val width: Float, val height: Float)

/** Lado do anel em que o rótulo fica: à direita, à esquerda, ou centrado acima/abaixo. */
internal enum class HudOrbitLabelSide { RIGHT, LEFT, CENTER }

/**
 * Altura de uma linha de rótulo como múltiplo do corpo do texto. Fonte única: raio, caixas e desenho usam este valor,
 * para que a caixa medida na geometria seja a mesma altura que o texto ocupa na tela.
 */
internal const val HUD_ORBIT_LINE_FACTOR = 1.2f

/** Distância entre a borda do anel e o rótulo, em dp. */
private const val LABEL_GAP_DP = 2f

/** Folga entre a borda de uma conta e a borda da vizinha, usada no cálculo do raio efetivo, em dp. */
private const val SPACING_GAP_DP = 8.0

/** Margem entre o rótulo mais longe e a borda do campo, em dp. */
private const val FIT_MARGIN_DP = 6.0

/** Passos de 5 graus (1/72 de volta) em volta do anel para achar o raio mínimo do campo que contém os rótulos. */
private const val FIT_STEPS = 72

/** Cosseno acima do qual o rótulo fica à direita ou à esquerda do anel, e não acima ou abaixo. */
private const val SIDE_COS_THRESHOLD = 0.3

/**
 * Diâmetro do campo orbital em dp para as contas e os parâmetros dados. Função pura, sem composição: pode ser
 * chamada no escopo `application { }`, onde não há `LocalFontFamilyResolver`. Os rótulos já chegam localizados
 * em [HudAccount], por isso o idioma não altera a largura estimada.
 */
@Suppress("UNUSED_PARAMETER")
internal fun hudOrbitFieldDiameter(accounts: List<HudAccount>, params: HudOrbitParams, language: AppLanguage): Dp {
    val widths = hudOrbitLabelWidths(accounts)
    return hudOrbitLayout(params, accounts.size, widths, orbitPhase = 0f).fieldDiameter.dp
}

/** Largura estimada dos rótulos em dp: `FULL` é o maior entre nome, cota e estado; `COMPACT` é só a cota. */
internal fun hudOrbitLabelWidths(accounts: List<HudAccount>): HudOrbitLabelWidths {
    val complete = accounts.maxOfOrNull { account -> hudOrbitAccountLabelWidth(account, HudOrbitLabels.FULL).value } ?: 0f
    val compact = accounts.maxOfOrNull { account -> hudOrbitAccountLabelWidth(account, HudOrbitLabels.COMPACT).value } ?: 0f
    return HudOrbitLabelWidths(complete = complete, compact = compact)
}

/** Largura estimada de um rótulo: `wordWidth` para nome e estado, `percentWidth` para a cota. Sem medir texto. */
internal fun hudOrbitAccountLabelWidth(account: HudAccount, labels: HudOrbitLabels): Dp {
    val quota = percentWidth(quotaText(account))
    if (labels != HudOrbitLabels.FULL) return quota
    return maxOf(wordWidth(account.label), quota, wordWidth(account.statusLabel))
}

/** Linhas de cada rótulo por modo: nome, cota e estado; só a cota; nenhuma. */
private fun labelLineCount(labels: HudOrbitLabels): Int = when (labels) {
    HudOrbitLabels.FULL -> 3
    HudOrbitLabels.COMPACT -> 1
    HudOrbitLabels.OFF -> 0
}

/** Altura do bloco de rótulos de uma conta, em dp: linhas × corpo do texto × [HUD_ORBIT_LINE_FACTOR]. */
internal fun hudOrbitLabelBlockHeight(params: HudOrbitParams): Float =
    labelLineCount(params.labels) * params.textSp * HUD_ORBIT_LINE_FACTOR

/**
 * Raio efetivo do anel, em dp: `max(orbR, (max(accD, alturaDoBloco) + 8) / (2·sin(π/n)))`. O segundo termo é o raio em
 * que a corda entre contas vizinhas cabe o bloco de rótulos sem bater. Com menos de duas contas não há vizinho: vale `orbR`.
 * Com rótulos desligados o bloco tem altura zero e só o anel conta.
 */
internal fun hudOrbitRingRadius(params: HudOrbitParams, accountCount: Int): Float {
    if (accountCount < 2) return params.orbR
    val spacing = max(params.accD.toDouble(), hudOrbitLabelBlockHeight(params).toDouble()) + SPACING_GAP_DP
    return max(params.orbR.toDouble(), spacing / (2.0 * sin(PI / accountCount))).toFloat()
}

/**
 * Geometria do campo orbital. Função pura e determinística: mesma entrada, mesma saída.
 *
 * As contas começam em −90° (topo) e giram no sentido horário da tela, com passo 360°/n e na ordem da lista.
 * `orbitPhase` é a fração de uma volta (0 a 1); só a parte fracionária conta, então uma volta inteira repete a posição.
 * O anel usa o raio efetivo de [hudOrbitRingRadius]. O campo cresce até caber as caixas dos rótulos em todas as fases;
 * compacto e desligado não crescem além do tamanho pedido quando os rótulos cabem.
 */
internal fun hudOrbitLayout(params: HudOrbitParams, accountCount: Int, labels: HudOrbitLabelWidths, orbitPhase: Float): HudOrbitLayout {
    val count = max(accountCount, 0)
    val phase = (orbitPhase - floor(orbitPhase)).toDouble()
    val radius = hudOrbitRingRadius(params, count)
    return HudOrbitLayout(
        fieldDiameter = fieldDiameterFor(params, count, labels, radius),
        holeDiameter = params.holeD,
        accounts = orbitSlots(count, radius.toDouble(), phase),
    )
}

/**
 * Caixas dos rótulos do layout, na ordem das contas. `larguras` traz a largura de cada rótulo, em dp, na mesma ordem.
 * Ancoragem: à direita ou à esquerda do anel quando a conta está nos lados, acima ou abaixo quando está no topo/base.
 * Com rótulos desligados não há caixa.
 */
internal fun hudOrbitLabelBoxes(layout: HudOrbitLayout, larguras: List<Float>, params: HudOrbitParams): List<HudOrbitLabelBox> =
    labelBoxesOf(layout.accounts, larguras, params)

/** Lado do anel em que o rótulo da conta fica, pelo cosseno do ângulo do slot. */
internal fun hudOrbitLabelSide(slot: HudOrbitAccountSlot): HudOrbitLabelSide {
    val ca = cos(Math.toRadians(slot.angleDegrees.toDouble()))
    return when {
        ca > SIDE_COS_THRESHOLD -> HudOrbitLabelSide.RIGHT
        ca < -SIDE_COS_THRESHOLD -> HudOrbitLabelSide.LEFT
        else -> HudOrbitLabelSide.CENTER
    }
}

/** Centro da conta `index` no anel: ângulo inicial −90°, passo 360°/`count`, y para baixo. */
private fun orbitSlots(count: Int, orbR: Double, phase: Double): List<HudOrbitAccountSlot> =
    (0 until count).map { index -> orbitSlot(index, count, orbR, phase) }

private fun orbitSlot(index: Int, count: Int, orbR: Double, phase: Double): HudOrbitAccountSlot {
    val angle = -90.0 + 360.0 * (index.toDouble() / count + phase)
    val radians = Math.toRadians(angle)
    return HudOrbitAccountSlot(
        index = index,
        angleDegrees = angle.toFloat(),
        x = (orbR * cos(radians)).toFloat(),
        y = (orbR * sin(radians)).toFloat(),
    )
}

/**
 * Centro de uma conta na fase dada, sem montar o layout inteiro. Dá o mesmo valor que `hudOrbitLayout(...).accounts[index]`,
 * mas sem a varredura de crescimento do campo: é o que o desenho chama por quadro.
 */
internal fun hudOrbitSlotAt(params: HudOrbitParams, accountCount: Int, index: Int, orbitPhase: Float): HudOrbitAccountSlot {
    val count = max(accountCount, 0)
    val phase = (orbitPhase - floor(orbitPhase)).toDouble()
    return orbitSlot(index, count, hudOrbitRingRadius(params, count).toDouble(), phase)
}

/**
 * Caixa do rótulo da conta `index` na fase dada, com largura `width` em dp. Mesma caixa que [hudOrbitLabelBoxes] dá para
 * o mesmo slot. Com rótulos desligados a altura é zero: quem chama não desenha rótulo nesse modo.
 */
internal fun hudOrbitLabelBoxAt(params: HudOrbitParams, accountCount: Int, index: Int, width: Float, orbitPhase: Float): HudOrbitLabelBox {
    val slot = hudOrbitSlotAt(params, accountCount, index, orbitPhase)
    val height = if (params.labels == HudOrbitLabels.OFF) 0f else hudOrbitLabelBlockHeight(params)
    val origin = labelOriginOf(slot, width, height, params.accD / 2f)
    return HudOrbitLabelBox(index = index, x = origin.first, y = origin.second, width = width, height = height)
}

/** Caixas de rótulo dos slots dados, na ordem deles. Sem rótulo, não há caixa. */
private fun labelBoxesOf(slots: List<HudOrbitAccountSlot>, larguras: List<Float>, params: HudOrbitParams): List<HudOrbitLabelBox> {
    if (params.labels == HudOrbitLabels.OFF) return emptyList()
    val height = hudOrbitLabelBlockHeight(params)
    val accountRadius = params.accD / 2f
    return slots.mapIndexed { position, slot ->
        val width = larguras.getOrElse(position) { 0f }
        val origin = labelOriginOf(slot, width, height, accountRadius)
        HudOrbitLabelBox(index = slot.index, x = origin.first, y = origin.second, width = width, height = height)
    }
}

/** Canto superior esquerdo da caixa do rótulo, em dp relativos ao centro do campo. */
private fun labelOriginOf(slot: HudOrbitAccountSlot, width: Float, height: Float, accountRadius: Float): Pair<Float, Float> {
    val gap = LABEL_GAP_DP
    return when (hudOrbitLabelSide(slot)) {
        HudOrbitLabelSide.RIGHT -> (slot.x + accountRadius + gap) to (slot.y - height / 2f)
        HudOrbitLabelSide.LEFT -> (slot.x - accountRadius - gap - width) to (slot.y - height / 2f)
        HudOrbitLabelSide.CENTER -> {
            val below = slot.y >= 0f
            val top = if (below) slot.y + accountRadius + gap else slot.y - accountRadius - gap - height
            (slot.x - width / 2f) to top
        }
    }
}

/** Sem contas ou com rótulos desligados não há o que medir: o campo fica no tamanho pedido. */
private fun fieldDiameterFor(params: HudOrbitParams, count: Int, labels: HudOrbitLabelWidths, orbR: Float): Float {
    if (count == 0 || params.labels == HudOrbitLabels.OFF) return params.fieldD
    val width = (if (params.labels == HudOrbitLabels.FULL) labels.complete else labels.compact)
    val fit = ceil(farthestReach(params, count, width, orbR.toDouble()) + FIT_MARGIN_DP)
    return (2.0 * max(params.fieldD.toDouble() / 2.0, fit)).toFloat()
}

/**
 * Maior distância do centro até um canto de rótulo ou até a borda de uma conta, em todas as fases do anel.
 * Largura igual para todos os rótulos: é o máximo medido, então o campo nunca fica menor que o pior caso.
 */
private fun farthestReach(params: HudOrbitParams, count: Int, width: Float, orbR: Double): Double {
    var reach = orbR + params.accD / 2.0
    val widths = List(count) { width }
    for (step in 0 until FIT_STEPS) {
        val slots = orbitSlots(count, orbR, step / FIT_STEPS.toDouble())
        labelBoxesOf(slots, widths, params).forEach { box -> reach = max(reach, cornerReach(box)) }
    }
    return reach
}

/** Distância do centro até o canto da caixa mais longe dele. */
private fun cornerReach(box: HudOrbitLabelBox): Double {
    val farX = max(abs(box.x), abs(box.x + box.width)).toDouble()
    val farY = max(abs(box.y), abs(box.y + box.height)).toDouble()
    return hypot(farX, farY)
}
