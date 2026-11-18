package com.usagemonitor.presentation.ui

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
import com.usagemonitor.HudOrbitAccountSlot
import com.usagemonitor.HudOrbitLabelBox
import com.usagemonitor.HudOrbitLabelSide
import com.usagemonitor.HudOrbitLabelWidths
import com.usagemonitor.HudOrbitLabels
import com.usagemonitor.HudOrbitParams
import com.usagemonitor.hudOrbitAccountLabelWidth
import com.usagemonitor.hudOrbitFieldDiameter
import com.usagemonitor.hudOrbitLabelBoxAt
import com.usagemonitor.hudOrbitLabelSide
import com.usagemonitor.hudOrbitLabelWidths
import com.usagemonitor.hudOrbitLayout
import com.usagemonitor.hudOrbitSlotAt
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
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
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.components.AppGargantuaRing
import com.usagemonitor.presentation.ui.components.AppProviderMark
import com.usagemonitor.presentation.ui.components.AppRingArc
import com.usagemonitor.presentation.ui.components.HudStarfieldBox
import com.usagemonitor.presentation.ui.components.gargantuaHorizonBody
import com.usagemonitor.presentation.ui.theme.AppGargantuaTokens
import com.usagemonitor.presentation.ui.theme.LocalAppMotionPolicy
import kotlin.math.max

/** Tag do campo orbital, para os testes de tela. */
const val HUD_ORBIT_FIELD_TEST_TAG = "hud-orbit-field"

/** Tag de cada conta do campo, para os testes de tela. */
const val HUD_ORBIT_ACCOUNT_TEST_TAG = "hud-orbit-account"

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
    val larguras = remember(accounts, params.labels) { accounts.map { account -> hudOrbitAccountLabelWidth(account, params.labels).value } }
    // Diâmetro calculado uma vez por contas e parâmetros: não depende da fase, então não varre o layout por quadro.
    val fieldDiameter = remember(accounts, params) { hudOrbitLayout(params, accounts.size, widths, orbitPhase = 0f).fieldDiameter }
    val orbit = HudOrbitSlots(params = params, count = accounts.size, larguras = larguras, phase = phase)
    val fieldRadius = fieldDiameter / 2f
    val scale = LocalDensity.current.density
    Box(
        modifier = modifier.testTag(HUD_ORBIT_FIELD_TEST_TAG).size(fieldDiameter.dp),
        contentAlignment = Alignment.Center
    ) {
        // O céu lê anéis, caixas e corrida só no desenho: a fase entra por `orbit`, sem recompor o campo.
        HudOrbitBody(
            fieldDiameter = fieldDiameter,
            rings = { orbit.ringRects(fieldRadius, scale) },
            running = { hudStarRunningRings(accounts) },
            forbidden = { hudStarfieldForbidden(orbit.labelRects(fieldRadius, scale), orbit.ringRects(fieldRadius, scale)) }
        )
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
 * Posições das contas e caixas dos rótulos na órbita. `slot` e `labelBox` leem a fase e só podem ser chamados de
 * lambda de layout ou de desenho; chamá-los na composição faria o campo inteiro recompor a cada quadro.
 */
private class HudOrbitSlots(
    private val params: HudOrbitParams,
    private val count: Int,
    private val larguras: List<Float>,
    private val phase: State<Float>
) {
    fun slot(index: Int): HudOrbitAccountSlot = hudOrbitSlotAt(params, count, index, phase.value)

    fun labelBox(index: Int): HudOrbitLabelBox = hudOrbitLabelBoxAt(params, count, index, larguras[index], phase.value)

    /** Anel de cada conta em px do campo (origem no canto do Box), na fase atual. Lido só no desenho. */
    fun ringRects(fieldRadius: Float, scale: Float): Map<Int, Rect> {
        val half = params.accD / 2f * scale
        return (0 until count).associateWith { index ->
            val slot = slot(index)
            val cx = (fieldRadius + slot.x) * scale
            val cy = (fieldRadius + slot.y) * scale
            Rect(cx - half, cy - half, cx + half, cy + half)
        }
    }

    /** Caixa de cada rótulo em px, a mesma geometria de [HudOrbitAccountLabel]; vazio com os rótulos desligados. */
    fun labelRects(fieldRadius: Float, scale: Float): Map<Int, Rect> {
        if (params.labels == HudOrbitLabels.OFF) return emptyMap()
        return (0 until count).associateWith { index ->
            val box = labelBox(index)
            val left = (fieldRadius + box.x) * scale
            val top = (fieldRadius + box.y) * scale
            Rect(left, top, left + box.width * scale, top + box.height * scale)
        }
    }
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

/**
 * Corpo da HUD: o horizonte do notch recortado em círculo, com o céu estrelado do notch (poços dos anéis, anéis em
 * execução e caixas dos rótulos proibidas). O buraco central fica com o `HudFloatingHole`, por isso `holeIndex` é -1.
 */
@Composable
private fun HudOrbitBody(
    fieldDiameter: Float,
    rings: () -> Map<Int, Rect>,
    running: () -> Set<Int>,
    forbidden: () -> List<HudStarfieldBox>
) {
    Box(
        modifier = Modifier
            .size(fieldDiameter.dp)
            .gargantuaHorizonBody(CircleShape)
    ) {
        HudStarfieldCanvas(
            rings = rings,
            running = running,
            forbidden = forbidden,
            holeIndex = -1,
            modifier = Modifier.matchParentSize().clip(CircleShape)
        )
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

/**
 * Rótulo da conta, na caixa que [hudOrbitLabelBoxes] calcula: mesma geometria que o teste de sobreposição verifica.
 * A posição é lida no layout, como a conta. O alinhamento do texto segue o lado do anel em que a caixa fica.
 */
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
    val width = hudOrbitAccountLabelWidth(account, params.labels).value
    val style = MaterialTheme.typography.labelSmall.copy(fontSize = params.textSp.sp)
    val color = MaterialTheme.colorScheme.onSurface
    Layout(
        content = {
            lines.forEach { line -> Text(text = line, style = style, color = color, maxLines = 1) }
        },
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset {
                val box = orbit.labelBox(index)
                IntOffset(
                    x = (fieldRadius + box.x).dp.roundToPx(),
                    y = (fieldRadius + box.y).dp.roundToPx()
                )
            }
            .width(width.dp)
    ) { measurables, constraints ->
        // O alinhamento do texto lê a fase na medição, não na composição: o rótulo acompanha o lado do anel sem recompor.
        val align = labelAlign(hudOrbitLabelSide(orbit.slot(index)))
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

/** Alinhamento do texto: encostado no anel do lado em que o rótulo fica. */
private fun labelAlign(side: HudOrbitLabelSide): TextAlign = when (side) {
    HudOrbitLabelSide.RIGHT -> TextAlign.Start
    HudOrbitLabelSide.LEFT -> TextAlign.End
    HudOrbitLabelSide.CENTER -> TextAlign.Center
}
