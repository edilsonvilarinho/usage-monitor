package com.usagemonitor.presentation.ui

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.UsageTargetKey
import kotlin.math.roundToInt
import com.usagemonitor.HUD_EMOJI_BADGE_OVERSHOOT
import com.usagemonitor.HUD_EMOJI_BADGE_SIZE
import com.usagemonitor.HUD_ITEM_GAP
import com.usagemonitor.HUD_NOTCH_PADDING_ACROSS
import com.usagemonitor.HUD_NOTCH_PADDING_ALONG
import com.usagemonitor.HUD_NOTCH_SHOULDER
import com.usagemonitor.HUD_RING_GAP
import com.usagemonitor.HUD_RING_SIZE
import com.usagemonitor.HUD_RING_STROKE
import com.usagemonitor.HUD_RING_TEXT_GAP
import com.usagemonitor.HudEdge
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.components.gargantuaRoll
import com.usagemonitor.presentation.ui.components.rememberGargantuaRoll
import com.usagemonitor.presentation.ui.components.AccountEmojiGlyph
import com.usagemonitor.presentation.ui.components.AppProviderMark
import com.usagemonitor.presentation.ui.components.AppGargantuaRing
import com.usagemonitor.presentation.ui.components.appGargantuaMarkSize
import com.usagemonitor.presentation.ui.components.AppRingArc
import com.usagemonitor.presentation.ui.components.AppStatusIndicator
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.AppStatusPill
import com.usagemonitor.presentation.ui.components.color
import com.usagemonitor.presentation.ui.theme.AppGargantuaTokens
import com.usagemonitor.presentation.ui.theme.LocalAppMotionPolicy

/** A faixa de anéis, do tamanho do notch. */
@Composable
internal fun HudRingStrip(
    accounts: List<HudAccount>,
    edge: HudEdge,
    fallbackLabel: String,
    fallbackTone: AppTone,
    size: DpSize,
    compact: Boolean,
    /**
     * O modo da faixa depois que as contas que saem se forem (K1). Diferente de
     * [compact], as que ficam trocam de modo por fade cruzado enquanto a vaga fecha.
     */
    settledCompact: Boolean = compact,
    /** O assentamento de cada conta que sai, por chave ([rememberHudDepartures]). */
    departures: Map<UsageTargetKey, Float> = emptyMap(),
    /** O passo mais lento da saída; `null` sem ninguém saindo. */
    settle: Float? = null,
    language: AppLanguage,
    onRingHovered: (Int) -> Unit,
    onRingRefresh: (Int) -> Unit,
    onItemPlaced: (Int, LayoutCoordinates) -> Unit,
    onRingPlaced: (Int, LayoutCoordinates) -> Unit
) {
    val items: @Composable () -> Unit = {
        if (accounts.isEmpty()) {
            AppStatusIndicator(label = fallbackLabel, tone = fallbackTone)
        } else {
            val birthOrder = hudBirthOrder(accounts)
            // O vão é de cada item, e não do arranjo: quem sai leva o seu junto
            // enquanto a vaga fecha (K1). Parado, dá o mesmo que `spacedBy`.
            val gaps = hudLeadingGapScales(accounts.map { account -> departures[account.targetKey] })
            val toFull = when {
                // Durante o colapso (vaga ainda aberta) cada conta fica no modo de sempre.
                settle == null || settle <= 0f || settledCompact == compact -> null
                compact -> settle
                else -> 1f - settle
            }
            accounts.forEachIndexed { index, account ->
                val leaving = departures[account.targetKey]
                // Por chave: a conta que colapsa no meio da faixa não pode herdar
                // o estado (pulso, transição) da vizinha.
                key(account.targetKey) {
                    HudRingItem(
                        account = account,
                        birthOrder = birthOrder[index],
                        vertical = !edge.isHorizontal,
                        compact = compact,
                        toFull = if (leaving == null) toFull else null,
                        slot = Modifier.hudStripSlot(
                            horizontal = edge.isHorizontal,
                            leadingGap = HUD_ITEM_GAP * gaps[index],
                            sizeScale = 1f - (leaving ?: 0f)
                        ),
                        language = language,
                        onHovered = { onRingHovered(index) },
                        onRefresh = { onRingRefresh(index) },
                        onItemPlaced = { coordinates -> onItemPlaced(index, coordinates) },
                        onPlaced = { coordinates -> onRingPlaced(index, coordinates) }
                    )
                }
            }
        }
        // A faixa é só das contas. A contagem até a próxima coleta mora no balão
        // da engrenagem (issue #269): com a cadência de 60 s ela reiniciava a
        // cada minuto na borda da tela. A atualização pendente também não entra
        // (issue #291) — é o ponto da engrenagem.
    }
    if (edge.isHorizontal) {
        Row(
            modifier = Modifier
                .size(size)
                .padding(horizontal = HUD_NOTCH_SHOULDER + HUD_NOTCH_PADDING_ALONG, vertical = HUD_NOTCH_PADDING_ACROSS),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) { items() }
    } else {
        Column(
            modifier = Modifier
                .size(size)
                .padding(horizontal = HUD_NOTCH_PADDING_ACROSS, vertical = HUD_NOTCH_SHOULDER + HUD_NOTCH_PADDING_ALONG),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) { items() }
    }
}

/** A pílula de estado de cada conta; os testes medem ela contra a geometria. */
internal const val HUD_STATUS_PILL_TEST_TAG = "hud-status-pill"
internal const val HUD_STRIP_LINE_TEST_TAG = "hud-strip-line"

@Composable
private fun HudRingItem(
    account: HudAccount,
    birthOrder: Int?,
    vertical: Boolean,
    /** A célula do Codenotch: anel e percentual embaixo, sem a palavra. */
    compact: Boolean,
    /** Trocando de modo enquanto uma vizinha sai (K1): 0 é compacto, 1 é completo. */
    toFull: Float?,
    /** A vaga do item na faixa: o vão antes dele e o quanto dela ainda resta. */
    slot: Modifier,
    language: AppLanguage,
    onHovered: () -> Unit,
    onRefresh: () -> Unit,
    onItemPlaced: (LayoutCoordinates) -> Unit,
    onPlaced: (LayoutCoordinates) -> Unit
) {
    val policy = LocalAppMotionPolicy.current
    // Nascimento (API ativada, início do app) e colapso (API desativada).
    val frame = rememberHudRingFrame(account.presence, birthOrder, policy)
    val refreshLabel = hudRefreshAccountLabel(account, language)
    // A ação é **declarada** na semântica, não instalada: um `clickable` aqui
    // consumiria o `down` e o arrasto pelo corpo nunca começaria.
    val itemModifier = slot
        .onGloballyPositioned(onItemPlaced)
        .semantics {
            onClick(label = refreshLabel) {
                onRefresh()
                true
            }
        }
    // O anel sob o ponteiro escolhe a conta do balão.
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    val currentOnHovered by rememberUpdatedState(onHovered)
    LaunchedEffect(isHovered) {
        if (isHovered) currentOnHovered()
    }
    val description = hudRingDescription(account, language)
    val ring: @Composable () -> Unit = {
        // A marca do fornecedor no centro do anel, como no Codenotch: a conta se
        // reconhece antes de ler o nome, que o notch recolhido nem mostra. Na
        // cor do texto e não no acento — em volta dela já estão os arcos, e o
        // acento ali competiria com a cor de risco deles. **Exceção: a cor que o
        // usuário deu à conta** (issue #275). Com duas contas Claude, o miolo é
        // o único ponto do notch recolhido que diz qual é qual, e ali a escolha
        // é dele — um marcador à parte mudaria a geometria e dividiria espaço
        // com a órbita de sessão ativa.
        Box(
            modifier = Modifier
                .onGloballyPositioned(onPlaced),
            contentAlignment = Alignment.Center
        ) {
            AppGargantuaRing(
                arcs = account.rings.map { quota -> AppRingArc(quota.fraction, quota.tone, quota.hasForecast) },
                description = description,
                size = HUD_RING_SIZE,
                stroke = HUD_RING_STROKE,
                gap = HUD_RING_GAP,
                active = account.sessionActive,
                attention = account.needsAttention,
                attentionIndex = account.attentionRingIndex,
                refreshing = account.refreshing,
                frame = frame
            )
            AppProviderMark(
                source = account.source,
                // O núcleo é sempre escuro, inclusive dentro de um preset claro.
                tint = account.accountAccent?.dark ?: AppGargantuaTokens.mark,
                size = hudRingMarkSize(account.rings.size),
                modifier = Modifier.graphicsLayer {
                    scaleX = frame.markScale * frame.scale
                    scaleY = frame.markScale * frame.scale
                    alpha = frame.mark
                }
            )
            // O emoji da conta (issue #287), selo no canto de cima à direita do
            // anel. Passa só `HUD_EMOJI_BADGE_OVERSHOOT` para fora dele, dentro do
            // respiro do notch: é selo, não item, e não mexe em `hudNotchSizes`.
            // Fica de pé em toda borda, como o texto do balão.
            val emoji = account.accountEmoji
            if (emoji != null) {
                AccountEmojiGlyph(
                    emoji = emoji,
                    size = HUD_EMOJI_BADGE_SIZE,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = HUD_EMOJI_BADGE_OVERSHOOT, y = -HUD_EMOJI_BADGE_OVERSHOOT)
                        .testTag(HUD_ACCOUNT_EMOJI_TEST_TAG)
                        .graphicsLayer { alpha = frame.mark }
                )
            }
        }
    }
    // Uma linha por anel, com a janela (#286); compacta, só a cota em foco. As
    // linhas são desenhadas no tamanho que `stripLineWidth`/`stripLineHeight`
    // medem — a costura com a geometria.
    val lines: @Composable (Boolean, Float) -> Unit = { compactMode, fade ->
        val stripLines = if (compactMode) listOf(account.focusLine) else account.stripLines
        // Compacta, a linha única já é a cota em foco.
        val emphasized = if (compactMode) account.emphasizedStripLineIndex?.let { 0 } else account.emphasizedStripLineIndex
        val emphasisColor = account.tone.color()
        Column(
            modifier = Modifier.graphicsLayer { alpha = frame.text * fade },
            horizontalAlignment = if (vertical || compactMode) Alignment.CenterHorizontally else Alignment.Start
        ) {
            stripLines.forEachIndexed { index, line ->
                HudStripLineText(line, percentColor = if (index == emphasized) emphasisColor else null)
            }
        }
    }
    // A palavra do estado em pílula tonal (issue #322): solta, ela tinha o
    // mesmo peso dos percentuais ao lado e o notch lia "flat". A largura e a
    // altura saem de `statusPillWidth`/`statusPillHeight`, a costura com a
    // geometria.
    val word: @Composable (Float) -> Unit = { fade ->
        AppStatusPill(
            label = account.statusLabel,
            tone = account.tone,
            // Na coluna vertical "Sem projeção" quebra em duas linhas; alinhadas
            // à esquerda elas destoavam do anel e dos percentuais, centrados.
            textAlign = if (vertical) TextAlign.Center else TextAlign.Start,
            maxLines = if (vertical) 2 else 1,
            rollLabelChanges = true,
            modifier = Modifier.testTag(HUD_STATUS_PILL_TEST_TAG).graphicsLayer { alpha = frame.text * fade }
        )
    }
    // Parado, só o texto do modo em vigor existe. Trocando de modo (K1) os dois
    // convivem por fade cruzado, e o anel continua o mesmo nó: um anel novo
    // animaria os arcos do zero, e dado nunca anima errado.
    val fullWeight = toFull ?: if (compact) 0f else 1f
    Layout(
        modifier = itemModifier.hoverable(hover),
        content = {
            Box(Modifier.layoutId(HudItemPart.RING)) { ring() }
            if (toFull != null || compact) {
                Box(Modifier.layoutId(HudItemPart.COMPACT)) { lines(true, 1f - fullWeight) }
            }
            // Compacto, a palavra fica no balão e na descrição do anel: com
            // contas demais ela é o que fazia a faixa atravessar a tela.
            if (toFull != null || !compact) {
                Column(
                    modifier = Modifier.layoutId(HudItemPart.FULL),
                    horizontalAlignment = if (vertical) Alignment.CenterHorizontally else Alignment.Start
                ) {
                    lines(false, fullWeight)
                    word(fullWeight)
                }
            }
        },
        measurePolicy = hudRingItemMeasurePolicy(vertical, fullWeight)
    )
}

private enum class HudItemPart { RING, COMPACT, FULL }

/** Onde o anel e o texto ficam num modo: empilhados (em pé e compacto) ou lado a lado. */
private class HudItemFrame(val width: Int, val height: Int, val ringX: Int, val ringY: Int, val textX: Int, val textY: Int)

private fun centered(space: Int, size: Int): Int = ((space - size) / 2f).roundToInt()

private fun lerpPx(start: Int, stop: Int, fraction: Float): Int = (start + (stop - start) * fraction).roundToInt()

private fun stackedFrame(ring: Placeable, text: Placeable): HudItemFrame {
    val width = maxOf(ring.width, text.width)
    return HudItemFrame(width, ring.height + text.height, centered(width, ring.width), 0, centered(width, text.width), ring.height)
}

private fun besideFrame(ring: Placeable, text: Placeable, gap: Int): HudItemFrame {
    val height = maxOf(ring.height, text.height)
    return HudItemFrame(ring.width + gap + text.width, height, 0, centered(height, ring.height), ring.width + gap, centered(height, text.height))
}

/**
 * O item da faixa: em pé ou compacto, anel sobre o texto, centrados; deitado e
 * completo, texto ao lado do anel. Parado é a coluna ou a linha de sempre.
 * Trocando de modo, a caixa vai de um tamanho ao outro, cada modo centrado nela,
 * e o anel desliza entre as duas posições.
 */
private fun hudRingItemMeasurePolicy(vertical: Boolean, fullWeight: Float) = MeasurePolicy { measurables, constraints ->
    val loose = constraints.copy(minWidth = 0, minHeight = 0)
    val ring = measurables.first { measurable -> measurable.layoutId == HudItemPart.RING }.measure(loose)
    val compactText = measurables.firstOrNull { measurable -> measurable.layoutId == HudItemPart.COMPACT }?.measure(loose)
    val fullText = measurables.firstOrNull { measurable -> measurable.layoutId == HudItemPart.FULL }?.measure(loose)
    val compactFrame = compactText?.let { text -> stackedFrame(ring, text) }
    val fullFrame = fullText?.let { text ->
        if (vertical) stackedFrame(ring, text) else besideFrame(ring, text, HUD_RING_TEXT_GAP.roundToPx())
    }
    val from = compactFrame ?: checkNotNull(fullFrame)
    val to = fullFrame ?: from
    val width = lerpPx(from.width, to.width, fullWeight)
    val height = lerpPx(from.height, to.height, fullWeight)
    layout(width, height) {
        val fromX = centered(width, from.width)
        val fromY = centered(height, from.height)
        val toX = centered(width, to.width)
        val toY = centered(height, to.height)
        ring.place(lerpPx(fromX + from.ringX, toX + to.ringX, fullWeight), lerpPx(fromY + from.ringY, toY + to.ringY, fullWeight))
        if (compactText != null && compactFrame != null) {
            compactText.place(centered(width, compactFrame.width) + compactFrame.textX, centered(height, compactFrame.height) + compactFrame.textY)
        }
        if (fullText != null && fullFrame != null) {
            fullText.place(toX + fullFrame.textX, toY + fullFrame.textY)
        }
    }
}

/**
 * A vaga de um item na faixa: o vão antes dele e o comprimento que ainda resta
 * dela. Quem sai encolhe até zero depois do colapso (K1) — ele já sumiu, então
 * o conteúdo passar da vaga não aparece. Parado é o item com o vão na frente.
 */
private fun Modifier.hudStripSlot(horizontal: Boolean, leadingGap: Dp, sizeScale: Float): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val gap = leadingGap.roundToPx()
        val along = ((if (horizontal) placeable.width else placeable.height) * sizeScale).roundToInt()
        val width = if (horizontal) gap + along else placeable.width
        val height = if (horizontal) placeable.height else gap + along
        layout(width, height) {
            if (horizontal) placeable.place(gap, 0) else placeable.place(0, gap)
        }
    }

/**
 * "7d 72%": a janela no tom secundário e o número no do texto. Sem rótulo é o
 * percentual de sempre, em `labelMedium`.
 *
 * [percentColor] pinta só o número da pior janela em atenção (issue #322). Isso
 * não faz a cor informar sozinha: a pílula ao lado escreve o estado, e o tom do
 * número só aponta **qual** janela o causou.
 */
@Composable
private fun HudStripLineText(line: HudStripLine, percentColor: Color? = null) {
    val numberColor = percentColor ?: MaterialTheme.colorScheme.onSurface
    val label = line.label
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    // As cores vão como span, para o número antigo sair rolando no tom dele (D5).
    val text = buildAnnotatedString {
        if (label != null) {
            withStyle(SpanStyle(color = labelColor)) { append(label) }
            append(" ")
        }
        withStyle(SpanStyle(color = numberColor)) { append(line.percentText) }
    }
    val style = (if (label == null) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall)
        .copy(color = MaterialTheme.colorScheme.onSurface)
    val roll = rememberGargantuaRoll(text)
    Text(
        text = text,
        style = style,
        maxLines = 1,
        onTextLayout = roll.onTextLayout,
        modifier = Modifier.testTag(HUD_STRIP_LINE_TEST_TAG).gargantuaRoll(roll, style)
    )
}

/**
 * A marca cabe dentro do horizonte escuro, acima do disco de acreção.
 *
 * A sessão ativa não entra na conta: a órbita dela gira por fora do anel. Quando
 * ela morava por dentro, a marca da conta trabalhando caía de 14dp para 8dp.
 */
internal fun hudRingMarkSize(arcs: Int): Dp {
    return appGargantuaMarkSize(HUD_RING_SIZE, HUD_RING_STROKE, HUD_RING_GAP, arcs)
}
