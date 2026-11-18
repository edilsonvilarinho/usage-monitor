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
import kotlin.math.sign
import kotlin.math.sin

/** Rótulos do campo orbital: nome, cota e estado (`FULL`), só a cota (`COMPACT`) ou nenhum (`OFF`). */
internal enum class HudOrbitLabels { FULL, COMPACT, OFF }

/** Parâmetros do campo orbital em dp, exceto os marcados. Padrões: `prototipos/issue-619/parametros-dono.json`. */
internal data class HudOrbitParams(
    val fieldD: Float = 180f,
    val orbR: Float = 40f,
    val accD: Float = 28f,
    val markD: Float = 8f,
    val holeD: Float = 32f,
    /** Inclinação do disco de acreção, em porcentagem: ry = rx * tilt / 100. */
    val tiltPercent: Float = 29f,
    /** Espessura do disco, em porcentagem. */
    val diskWidthPercent: Float = 3f,
    /** Segundos para uma volta do disco. */
    val diskSpinSeconds: Float = 3.5f,
    /** Segundos para uma volta da órbita das contas. */
    val orbitPeriodSeconds: Float = 180f,
    /** Opacidade do fundo do corpo da HUD, em porcentagem. */
    val backgroundAlphaPercent: Float = 100f,
    /** Densidade da poeira, em porcentagem. */
    val dustDensityPercent: Float = 100f,
    /** Brilho da poeira, em porcentagem. */
    val dustBrightnessPercent: Float = 200f,
    val borderWidth: Float = 0f,
    val labels: HudOrbitLabels = HudOrbitLabels.FULL,
    /** Tamanho do texto dos rótulos, em sp. */
    val textSp: Float = 8f,
)

/** Largura medida dos rótulos, em dp. A geometria não mede texto: quem tem Canvas mede e entrega o número. */
internal data class HudOrbitLabelWidths(val complete: Float, val compact: Float)

/** Posição de uma conta relativa ao centro do campo, em dp, com y para baixo como na tela. */
internal data class HudOrbitAccountSlot(val index: Int, val angleDegrees: Float, val x: Float, val y: Float)

/** Diâmetro do campo expandido, diâmetro do buraco minimizado e as contas na ordem da lista. */
internal data class HudOrbitLayout(val fieldDiameter: Float, val holeDiameter: Float, val accounts: List<HudOrbitAccountSlot>)

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

/** Passos de 5 graus (k·π/36) em volta do anel para achar o raio mínimo que contém os rótulos, como o `fitRadius` do R2. */
private const val FIT_STEPS = 72

/** Lado do anel em que o rótulo é ancorado: à esquerda, à direita ou centrado acima/abaixo. */
private enum class LabelSide { LEFT, RIGHT, CENTER }

/**
 * Geometria do campo orbital. Função pura e determinística: mesma entrada, mesma saída.
 *
 * As contas começam em −90° (topo) e giram no sentido horário da tela, com passo 360°/n e na ordem da lista.
 * `orbitPhase` é a fração de uma volta (0 a 1); só a parte fracionária conta, então uma volta inteira repete a posição.
 * O campo cresce até caber os rótulos completos; compacto e desligado não medem nada além do tamanho pedido.
 */
internal fun hudOrbitLayout(params: HudOrbitParams, accountCount: Int, labels: HudOrbitLabelWidths, orbitPhase: Float): HudOrbitLayout {
    val count = max(accountCount, 0)
    val phase = (orbitPhase - floor(orbitPhase)).toDouble()
    val slots = (0 until count).map { index -> orbitSlot(index, count, params.orbR.toDouble(), phase) }
    return HudOrbitLayout(
        fieldDiameter = fieldDiameterFor(params, count, labels),
        holeDiameter = params.holeD,
        accounts = slots,
    )
}

/** Centro da conta `index` no anel: ângulo inicial −90°, passo 360°/`count`, y para baixo. */
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

/** Sem contas ou com rótulos desligados não há o que medir: o campo fica no tamanho pedido. */
private fun fieldDiameterFor(params: HudOrbitParams, count: Int, labels: HudOrbitLabelWidths): Float {
    if (count == 0 || params.labels == HudOrbitLabels.OFF) return params.fieldD
    val full = params.labels == HudOrbitLabels.FULL
    val width = (if (full) labels.complete else labels.compact).toDouble()
    val fit = labelFitRadius(full, width, params.orbR.toDouble(), params.accD.toDouble())
    return (2.0 * max(params.fieldD.toDouble() / 2.0, fit)).toFloat()
}

/** Raio mínimo do campo para que nenhum rótulo saia dele, medido em todas as posições do anel. */
private fun labelFitRadius(full: Boolean, width: Double, orbR: Double, accD: Double): Double {
    val labelRadius = orbR + accD / 2.0 + 6.0
    var reach = 0.0
    for (step in 0 until FIT_STEPS) {
        reach = max(reach, labelReach(full, width, labelRadius, step * PI / 36.0))
    }
    return ceil(reach + 6.0)
}

/** Distância do centro até o canto mais longe da caixa do rótulo posicionado no ângulo `angle`. */
private fun labelReach(full: Boolean, width: Double, labelRadius: Double, angle: Double): Double {
    val ca = cos(angle)
    val sa = sin(angle)
    val side = when {
        !full -> LabelSide.CENTER
        ca > 0.3 -> LabelSide.LEFT
        ca < -0.3 -> LabelSide.RIGHT
        else -> LabelSide.CENTER
    }
    val radial = labelRadius + if (full) 2.0 else 4.0
    val ax = radial * ca
    val ay = radial * sa + if (full && side == LabelSide.CENTER) sign(sa) * 12.0 else 0.0
    val halfHeight = if (full) 18.0 else 6.0
    val x0 = when (side) {
        LabelSide.LEFT -> ax
        LabelSide.RIGHT -> ax - width
        LabelSide.CENTER -> ax - width / 2.0
    }
    val x1 = x0 + width
    return hypot(max(abs(x0), abs(x1)), max(abs(ay - halfHeight), abs(ay + halfHeight)))
}
