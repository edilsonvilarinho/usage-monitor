package com.usagemonitor.presentation.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import com.usagemonitor.hudOrbitAccountLabelWidth
import com.usagemonitor.hudOrbitFieldDiameter
import com.usagemonitor.hudOrbitLabelWidths
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.usagemonitor.HudOrbitAccountSlot
import com.usagemonitor.HudOrbitLabelWidths
import com.usagemonitor.HudOrbitLabels
import com.usagemonitor.HudOrbitParams
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.hudOrbitLayout
import com.usagemonitor.presentation.ui.components.AppGargantuaRing
import com.usagemonitor.presentation.ui.components.AppProviderMark
import com.usagemonitor.presentation.ui.components.AppRingArc
import com.usagemonitor.presentation.ui.theme.AppGargantuaTokens
import com.usagemonitor.presentation.ui.theme.LocalAppMotionPolicy
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/** Tag do campo orbital, para os testes de tela. */
const val HUD_ORBIT_FIELD_TEST_TAG = "hud-orbit-field"

/** Tag de cada conta do campo, para os testes de tela. */
const val HUD_ORBIT_ACCOUNT_TEST_TAG = "hud-orbit-account"

/** Semente fixa da poeira: a mesma posição em toda composição. */
private const val DUST_SEED = 619L
private const val DUST_COUNT = 60
private const val DUST_BASE_ALPHA = 0.25f
private const val DUST_RADIUS_DP = 0.8f

/** Distância entre a borda do anel e o rótulo, em dp. */
private const val LABEL_GAP_DP = 2f

/** Altura de uma linha de rótulo como múltiplo do corpo do texto. */
private const val LABEL_LINE_FACTOR = 1.4f

/** Cosseno acima do qual o rótulo fica à direita ou à esquerda do anel, e não acima ou abaixo. */
private const val SIDE_COS_THRESHOLD = 0.3f

/**
 * Campo orbital do modo flutuante expandido: as contas giram em volta do buraco central e cada uma mostra
 * o anel de cotas com a marca do provedor. Os rótulos seguem `params.labels`.
 *
 * A fase da órbita é um `State` lido só dentro das lambdas de `offset` (fase de layout). A composição não lê
 * a fase, então o quadro da órbita não recompõe o campo nem os textos.
 */
@Composable
internal fun HudOrbitField(
    accounts: List<HudAccount>,
    params: HudOrbitParams,
    language: AppLanguage,
    onAccountClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val motion = LocalAppMotionPolicy.current
    val phase = rememberHudOrbitPhase(live = motion.continuous && !motion.reduced, periodSeconds = params.orbitPeriodSeconds)
    val widths = remember(accounts) { hudOrbitLabelWidths(accounts) }
    // O diâmetro do campo não depende da fase: a fase 0 dimensiona o Box sem ler o State.
    val fieldDiameter = hudOrbitLayout(params, accounts.size, widths, orbitPhase = 0f).fieldDiameter
    val orbit = HudOrbitSlots(params = params, count = accounts.size, widths = widths, phase = phase)
    val fieldRadius = fieldDiameter / 2f
    Box(
        modifier = modifier.testTag(HUD_ORBIT_FIELD_TEST_TAG).size(fieldDiameter.dp),
        contentAlignment = Alignment.Center
    ) {
        HudOrbitBody(params = params, fieldDiameter = fieldDiameter)
        HudFloatingHole(
            params = params,
            update = null,
            description = localized(language, "Buraco orbital", "Orbital hole")
        )
        accounts.forEachIndexed { index, account ->
            HudOrbitAccountNode(
                index = index,
                account = account,
                params = params,
                fieldRadius = fieldRadius,
                language = language,
                orbit = orbit,
                onAccountClick = onAccountClick
            )
            if (params.labels != HudOrbitLabels.OFF) {
                HudOrbitAccountLabel(
                    index = index,
                    account = account,
                    params = params,
                    fieldRadius = fieldRadius,
                    orbit = orbit
                )
            }
        }
    }
}

/**
 * Posições das contas na órbita. `slot` lê a fase e só pode ser chamado de lambda de layout ou de desenho;
 * chamá-lo na composição faria o campo inteiro recompor a cada quadro.
 */
private class HudOrbitSlots(
    private val params: HudOrbitParams,
    private val count: Int,
    private val widths: HudOrbitLabelWidths,
    private val phase: State<Float>
) {
    fun slot(index: Int): HudOrbitAccountSlot = hudOrbitLayout(params, count, widths, phase.value).accounts[index]
}

/** Relógio da órbita: só anda com a política viva; fora disso fica na fase zero. Devolve o State, não o valor. */
@Composable
private fun rememberHudOrbitPhase(live: Boolean, periodSeconds: Float): State<Float> {
    val phase = remember { mutableStateOf(0f) }
    LaunchedEffect(live, periodSeconds) {
        if (!live) {
            phase.value = 0f
            return@LaunchedEffect
        }
        val period = max(periodSeconds, 1f)
        var start = -1L
        while (true) {
            withFrameMillis { now ->
                if (start < 0L) start = now
                phase.value = ((now - start) / 1000f) / period
            }
        }
    }
    return phase
}

/**
 * Diâmetro do campo orbital em dp, o mesmo que [HudOrbitField] desenha. Wrapper composable fino sobre
 * [hudOrbitFieldDiameter]: não mede texto, então pode ser chamado em qualquer escopo de composição.
 */
@Composable
internal fun rememberHudOrbitFieldDiameter(accounts: List<HudAccount>, params: HudOrbitParams, language: AppLanguage): Dp {
    return remember(accounts, params, language) { hudOrbitFieldDiameter(accounts, params, language) }
}

/** Corpo da HUD: fundo opaco circular com a borda e a poeira. */
@Composable
private fun HudOrbitBody(params: HudOrbitParams, fieldDiameter: Float) {
    val body = MaterialTheme.colorScheme.surface.copy(alpha = params.backgroundAlphaPercent / 100f)
    val border = MaterialTheme.colorScheme.outline
    val dust = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .size(fieldDiameter.dp)
            .clip(CircleShape)
            .background(body)
            .border(params.borderWidth.dp, border, CircleShape)
    )
    Canvas(modifier = Modifier.size(fieldDiameter.dp)) {
        val random = Random(DUST_SEED)
        val count = (DUST_COUNT * params.dustDensityPercent / 100f).toInt()
        val radius = size.width / 2f
        val alpha = (DUST_BASE_ALPHA * params.dustBrightnessPercent / 100f).coerceIn(0f, 1f)
        repeat(count) {
            val x = random.nextFloat() * size.width
            val y = random.nextFloat() * size.height
            if (hypot(x - radius, y - radius) <= radius) {
                drawCircle(color = dust.copy(alpha = alpha), radius = DUST_RADIUS_DP.dp.toPx(), center = Offset(x, y))
            }
        }
    }
}

/**
 * Conta no seu slot: anel de cotas com a marca do provedor e semântica de clique com a descrição.
 * O deslocamento é lido no layout, pela lambda de `offset`, então a órbita não recompõe a conta.
 */
@Composable
private fun BoxScope.HudOrbitAccountNode(
    index: Int,
    account: HudAccount,
    params: HudOrbitParams,
    fieldRadius: Float,
    language: AppLanguage,
    orbit: HudOrbitSlots,
    onAccountClick: (Int) -> Unit
) {
    val description = hudRingDescription(account, language)
    val accountRadius = params.accD / 2f
    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset {
                val slot = orbit.slot(index)
                IntOffset(
                    x = (fieldRadius + slot.x - accountRadius).dp.roundToPx(),
                    y = (fieldRadius + slot.y - accountRadius).dp.roundToPx()
                )
            }
            .size(params.accD.dp)
            .testTag(HUD_ORBIT_ACCOUNT_TEST_TAG)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                onClick(label = description) {
                    onAccountClick(index)
                    true
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AppGargantuaRing(
            arcs = account.rings.map { quota -> AppRingArc(quota.fraction, quota.tone, quota.hasForecast) },
            description = description,
            size = params.accD.dp,
            active = account.sessionActive,
            attention = account.needsAttention,
            attentionIndex = account.attentionRingIndex,
            refreshing = account.refreshing,
            centerContent = {
                AppProviderMark(
                    source = account.source,
                    tint = account.accountAccent?.dark ?: AppGargantuaTokens.mark,
                    size = params.markD.dp
                )
            }
        )
    }
}

/** Rótulo da conta, ao lado do anel no sentido do ângulo do slot. Posição lida no layout, como a conta. */
@Composable
private fun BoxScope.HudOrbitAccountLabel(
    index: Int,
    account: HudAccount,
    params: HudOrbitParams,
    fieldRadius: Float,
    orbit: HudOrbitSlots
) {
    val lines = if (params.labels == HudOrbitLabels.FULL) {
        listOf(account.label, quotaText(account), account.statusLabel)
    } else {
        listOf(quotaText(account))
    }
    val lineDp = params.textSp * LABEL_LINE_FACTOR
    val width = hudOrbitAccountLabelWidth(account, params.labels).value
    val height = lines.size * lineDp
    val accountRadius = params.accD / 2f
    val style = MaterialTheme.typography.labelSmall.copy(fontSize = params.textSp.sp)
    val color = MaterialTheme.colorScheme.onSurface
    Layout(
        content = {
            lines.forEach { line -> Text(text = line, style = style, color = color, maxLines = 1) }
        },
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset {
                val slot = orbit.slot(index)
                val origin = labelOrigin(slot, width, height, accountRadius)
                IntOffset(
                    x = (fieldRadius + origin.first).dp.roundToPx(),
                    y = (fieldRadius + origin.second).dp.roundToPx()
                )
            }
            .width(width.dp)
    ) { measurables, constraints ->
        // O alinhamento do texto lê a fase na medição, não na composição: o rótulo acompanha o lado do anel sem recompor.
        val align = labelAlign(orbit.slot(index))
        val blockWidth = constraints.maxWidth
        val placeables = measurables.map { measurable -> measurable.measure(Constraints(maxWidth = blockWidth)) }
        val blockHeight = placeables.sumOf { placeable -> placeable.height }
        layout(blockWidth, blockHeight) {
            var y = 0
            placeables.forEach { placeable ->
                val x = when (align) {
                    TextAlign.End -> blockWidth - placeable.width
                    TextAlign.Center -> (blockWidth - placeable.width) / 2
                    else -> 0
                }
                placeable.place(x, y)
                y += placeable.height
            }
        }
    }
}

/** Cota principal da conta, a mesma que a faixa de hoje mostra no anel. */
internal fun quotaText(account: HudAccount): String = account.rings.firstOrNull()?.percentText.orEmpty()

/** Canto superior esquerdo do rótulo, em dp relativos ao centro do campo. */
private fun labelOrigin(slot: HudOrbitAccountSlot, width: Float, height: Float, accountRadius: Float): Pair<Float, Float> {
    val radians = Math.toRadians(slot.angleDegrees.toDouble())
    val ca = cos(radians).toFloat()
    val sa = sin(radians).toFloat()
    val gap = LABEL_GAP_DP
    return when {
        ca > SIDE_COS_THRESHOLD -> (slot.x + accountRadius + gap) to (slot.y - height / 2f)
        ca < -SIDE_COS_THRESHOLD -> (slot.x - accountRadius - gap - width) to (slot.y - height / 2f)
        sa >= 0f -> (slot.x - width / 2f) to (slot.y + accountRadius + gap)
        else -> (slot.x - width / 2f) to (slot.y - accountRadius - gap - height)
    }
}

/** Alinhamento do texto: encostado no anel do lado em que o rótulo fica. */
private fun labelAlign(slot: HudOrbitAccountSlot): TextAlign {
    val ca = cos(Math.toRadians(slot.angleDegrees.toDouble())).toFloat()
    return when {
        ca > SIDE_COS_THRESHOLD -> TextAlign.Start
        ca < -SIDE_COS_THRESHOLD -> TextAlign.End
        else -> TextAlign.Center
    }
}
