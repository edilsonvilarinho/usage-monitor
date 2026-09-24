package com.usagemonitor.presentation.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.round
import com.usagemonitor.presentation.ui.previewCardOrder
import com.usagemonitor.presentation.ui.theme.appSpring
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.datetime.Instant
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.QuotaRiskSummary
import com.usagemonitor.domain.entity.QuotaSeriesKey
import com.usagemonitor.domain.entity.SessionPulse
import com.usagemonitor.domain.entity.UsageAccountKey
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.ui.CardGridSlot
import com.usagemonitor.presentation.ui.resolveDropTargetIndex
import com.usagemonitor.presentation.ui.theme.AppMotion
import com.usagemonitor.presentation.ui.theme.AppSpacing

// Threshold de largura (em dp) acima do qual o grid alterna de 1 para 2 colunas.
private val CompactColumnsThreshold = 720.dp

// Espaçamento horizontal e vertical entre os cards.
//
// 12 e não 16: o vão largo vinha do card com sombra, que precisava de ar em volta
// para a sombra não encostar na do vizinho. Sem sombra quem separa é a borda de
// 1dp, e 16dp entre dois cards numa janela estreita é largura que falta dentro
// deles.
private val CardSpacing = AppSpacing.md

/**
 * Grid responsivo de cards de uso de API.
 *
 * Stateless quanto à ordem dos cards (recebida em `items`); mantém somente
 * estado local de drag/drop em memória de composição. Reordenamentos são
 * comunicados via `onMoveCardToIndex`.
 */
@Composable
internal fun ResponsiveDashboardCardGrid(
    items: List<ApiUsageStats>,
    refreshingTargets: Set<UsageTargetKey>,
    minimizedCards: Set<UsageTargetKey>,
    riskSummaries: Map<UsageTargetKey, Map<QuotaSeriesKey, QuotaRiskSummary>> = emptyMap(),
    language: AppLanguage,
    onRefreshCard: (UsageTargetKey) -> Unit,
    onMoveCardToIndex: (UsageTargetKey, Int) -> Unit,
    onToggleCardMinimized: (UsageTargetKey) -> Unit,
    onOpenHistoryCard: (ApiSource, UsageAccountKey?) -> Unit,
    onOpenCliSessionsCard: (UsageTargetKey) -> Unit = {},
    onOpenCodexCliSessionsCard: (UsageTargetKey) -> Unit = {},
    onOpenTeamUsageCard: (UsageTargetKey) -> Unit = {},
    onOpenTeamPresenceCard: (UsageTargetKey) -> Unit = {},
    /**
     * Perfis Anthropic marcados como parte do time nas Configurações.
     *
     * Vazio quando a integração está desligada, o que faz o botão de time sumir
     * de todos os cards sem nenhuma outra condição espalhada pela tela.
     */
    teamEnabledProfileIds: Set<String> = emptySet(),
    /** Semáforo das sessões desta máquina, por card. Ausente significa repouso. */
    cliSessionPulses: Map<UsageTargetKey, SessionPulse> = emptyMap(),
    /** Semáforo das sessões de todo o time, por card. */
    teamSessionPulses: Map<UsageTargetKey, SessionPulse> = emptyMap(),
    /** Instante contra o qual o vencimento das janelas de cota é medido. */
    now: Instant,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val spacingPx = with(density) { CardSpacing.roundToPx() }
    val compactThresholdPx = with(density) { CompactColumnsThreshold.roundToPx() }
    // Onde cada card foi **posto** (o alvo, não a posição animada). Mapa comum e
    // não estado: é escrito na fase de posicionamento, e como estado cada
    // escrita pediria uma recomposição.
    val slotBounds = remember { HashMap<UsageTargetKey, CardGridBounds>() }
    // Posição animada de cada card. Ver [placeAnimated].
    val placements = remember { HashMap<UsageTargetKey, Animatable<IntOffset, AnimationVector2D>>() }
    val placementScope = rememberCoroutineScope()
    val placementSpec = appSpring<IntOffset>(AppMotion.Springs.GENTLE, visibilityThreshold = IntOffset.VisibilityThreshold)
    var dragState by remember { mutableStateOf<CardDragState?>(null) }
    var dropTargetIndex by remember { mutableStateOf<Int?>(null) }

    // Durante o arrasto a grade é disposta na ordem **de prévia**: o card
    // arrastado já no lugar em que cairia. Os vizinhos deslizam para abrir o vão
    // em vez de esperar o soltar, e o soltar não move mais nada — a ordem nova
    // já era a da tela.
    val orderedKeys = items.map { item -> item.targetKey }
    val layoutKeys = previewCardOrder(orderedKeys, dragState?.target, dropTargetIndex)

    Layout(
        modifier = modifier,
        content = {
            items.forEachIndexed { index, stats ->
                val isBeingDragged = dragState?.target == stats.targetKey

                key(stats.targetKey) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .layoutId(stats.targetKey)
                            .zIndex(if (isBeingDragged) 3f else 0f)
                    ) {
                        ApiUsageCard(
                            source = stats.source,
                            apiName = stats.profileLabel?.let { label -> "${stats.apiName} — $label" } ?: stats.apiName,
                            quotas = stats.quotas,
                            accountContext = stats.accountContext,
                            notices = stats.notices,
                            riskByQuotaKey = riskSummaries[stats.targetKey].orEmpty(),
                            showUsageDetails = stats.source != ApiSource.ANTHROPIC,
                            isRefreshing = stats.targetKey in refreshingTargets,
                            isMinimized = stats.targetKey in minimizedCards,
                            isBeingDragged = isBeingDragged,
                            // O vão aberto pela ordem de prévia já diz onde o
                            // card vai cair; realçar um vizinho diria outra coisa.
                            isDragTarget = false,
                            language = language,
                            // O atraso de entrada sai do token, não de um
                            // literal: o `ScreenshotGenerator` é calibrado
                            // contra ele e precisa de um dono só.
                            animationDelayMillis = index * AppMotion.stagger.toInt(),
                            onRefresh = { onRefreshCard(stats.targetKey) },
                            onOpenHistory = { onOpenHistoryCard(stats.source, stats.accountContext?.key) },
                            onOpenCliSessions = if (stats.source == ApiSource.ANTHROPIC) {
                                { onOpenCliSessionsCard(stats.targetKey) }
                            } else {
                                null
                            },
                            onOpenCodexCliSessions = if (stats.source == ApiSource.CODEX) {
                                { onOpenCodexCliSessionsCard(stats.targetKey) }
                            } else {
                                null
                            },
                            onOpenTeamUsage = if (
                                stats.source == ApiSource.ANTHROPIC &&
                                stats.targetKey.profileId in teamEnabledProfileIds
                            ) {
                                { onOpenTeamUsageCard(stats.targetKey) }
                            } else {
                                null
                            },
                            // Mesma condição: as duas janelas leem o mesmo
                            // servidor de time, para a mesma conta.
                            onOpenTeamPresence = if (
                                stats.source == ApiSource.ANTHROPIC &&
                                stats.targetKey.profileId in teamEnabledProfileIds
                            ) {
                                { onOpenTeamPresenceCard(stats.targetKey) }
                            } else {
                                null
                            },
                            cliSessionPulse = cliSessionPulses[stats.targetKey] ?: SessionPulse.EMPTY,
                            teamSessionPulse = teamSessionPulses[stats.targetKey] ?: SessionPulse.EMPTY,
                            now = now,
                            onToggleMinimized = { onToggleCardMinimized(stats.targetKey) },
                            onDragStart = {
                                // As caixas do alvo são **congeladas** no início:
                                // a ordem de prévia move os vizinhos, e medir
                                // contra caixas que andam faria o alvo trocar a
                                // cada quadro, com o vão pulando de um lado para
                                // o outro.
                                dragState = CardDragState(
                                    target = stats.targetKey,
                                    frozenBounds = slotBounds.toMap()
                                )
                                dropTargetIndex = items.indexOfFirst { item -> item.targetKey == stats.targetKey }
                            },
                            onDrag = { dragAmount ->
                                val activeDrag = dragState
                                if (activeDrag != null && activeDrag.target == stats.targetKey) {
                                    val updatedDrag = activeDrag.copy(
                                        dragOffset = activeDrag.dragOffset + dragAmount
                                    )
                                    dragState = updatedDrag

                                    val draggedBounds = updatedDrag.frozenBounds[stats.targetKey]
                                    if (draggedBounds == null) {
                                        dropTargetIndex = null
                                    } else {
                                        val draggedCenter = draggedBounds.center + updatedDrag.dragOffset
                                        dropTargetIndex = resolveDropTargetIndex(
                                            orderedTargets = items.map { item -> item.targetKey },
                                            boundsByTarget = updatedDrag.frozenBounds.mapValues { (target, bounds) ->
                                                CardGridSlot(
                                                    target = target,
                                                    left = bounds.topLeft.x,
                                                    top = bounds.topLeft.y,
                                                    width = bounds.width,
                                                    height = bounds.height
                                                )
                                            },
                                            draggedTarget = stats.targetKey,
                                            draggedCenter = draggedCenter
                                        )
                                    }
                                }
                            },
                            onDragEnd = {
                                val activeDrag = dragState
                                val targetIndex = dropTargetIndex
                                // O card sai de onde o ponteiro o deixou e assenta
                                // no vão pela mola, em vez de saltar para a vaga.
                                // `UNDISPATCHED` faz o `snapTo` acontecer agora,
                                // antes de o arrasto ser limpo: no quadro seguinte
                                // a posição de partida já é a do ponteiro.
                                val released = activeDrag?.let { drag ->
                                    drag.frozenBounds[drag.target]?.let { bounds ->
                                        (bounds.topLeft + drag.dragOffset).round()
                                    }
                                }
                                val releasedAnimatable = activeDrag?.let { drag -> placements[drag.target] }
                                if (released != null && releasedAnimatable != null) {
                                    placementScope.launch(start = CoroutineStart.UNDISPATCHED) {
                                        releasedAnimatable.snapTo(released)
                                    }
                                }
                                dragState = null
                                dropTargetIndex = null

                                if (activeDrag != null && activeDrag.target == stats.targetKey && targetIndex != null) {
                                    onMoveCardToIndex(stats.targetKey, targetIndex)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    ) { unorderedMeasurables, constraints ->
        if (unorderedMeasurables.isEmpty()) {
            return@Layout layout(width = constraints.maxWidth, height = 0) {}
        }
        val byKey = unorderedMeasurables.associateBy { measurable -> measurable.layoutId as UsageTargetKey }
        val keysInOrder = layoutKeys.filter { key -> key in byKey }
        val measurables = keysInOrder.map { key -> byKey.getValue(key) }

        val columns = if (constraints.maxWidth < compactThresholdPx) 1 else 2
        val totalSpacing = spacingPx * (columns - 1)
        val itemWidth = ((constraints.maxWidth - totalSpacing).coerceAtLeast(0)) / columns
        val defaultChildConstraints = constraints.copy(
            minWidth = itemWidth,
            maxWidth = itemWidth,
            minHeight = 0
        )
        val fullRowConstraints = constraints.copy(
            minWidth = constraints.maxWidth,
            maxWidth = constraints.maxWidth,
            minHeight = 0
        )
        val rows = buildList {
            var index = 0

            while (index < measurables.size) {
                val remainingItems = measurables.size - index
                val isTrailingSingleCardRow = columns > 1 && remainingItems == 1
                val rowItemCount = if (isTrailingSingleCardRow) 1 else minOf(columns, remainingItems)
                val rowConstraints = if (isTrailingSingleCardRow) {
                    fullRowConstraints
                } else {
                    defaultChildConstraints
                }
                val rowPlaceables = List(rowItemCount) { rowIndex ->
                    measurables[index + rowIndex].measure(rowConstraints)
                }

                add(CardGridRow(placeables = rowPlaceables))
                index += rowItemCount
            }
        }
        val rowHeights = rows.map { row -> row.height }
        val layoutHeight = rowHeights.sum() + spacingPx * (rowHeights.size - 1).coerceAtLeast(0)

        layout(width = constraints.maxWidth, height = layoutHeight) {
            var yPosition = 0
            var flatIndex = 0
            val activeDrag = dragState

            rows.forEach { row ->
                row.placeables.forEachIndexed { columnIndex, placeable ->
                    val xPosition = if (row.placeables.size == 1 && columns > 1) {
                        0
                    } else {
                        columnIndex * (itemWidth + spacingPx)
                    }
                    val key = keysInOrder[flatIndex]
                    flatIndex += 1
                    val target = IntOffset(xPosition, yPosition)
                    slotBounds[key] = CardGridBounds(
                        topLeft = Offset(xPosition.toFloat(), yPosition.toFloat()),
                        width = placeable.width,
                        height = placeable.height
                    )

                    val draggedFrom = activeDrag?.takeIf { drag -> drag.target == key }
                        ?.let { drag -> drag.frozenBounds[key] }
                    if (activeDrag != null && draggedFrom != null) {
                        // O arrastado segue o ponteiro a partir de onde estava
                        // quando o arrasto começou — não da vaga de prévia.
                        placeable.placeRelative((draggedFrom.topLeft + activeDrag.dragOffset).round())
                    } else {
                        placeable.placeRelative(
                            placeAnimated(key, target, placements, placementScope, placementSpec)
                        )
                    }
                }

                yPosition += row.height + spacingPx
            }
        }
    }
}

private data class CardDragState(
    val target: UsageTargetKey,
    val dragOffset: Offset = Offset.Zero,
    val frozenBounds: Map<UsageTargetKey, CardGridBounds> = emptyMap()
)

/**
 * Posição animada de um card: a primeira colocação é salto, as seguintes
 * deslizam pela mola até a vaga nova. Cobre reordenar, minimizar um vizinho
 * (quem está embaixo sobe) e a troca de uma para duas colunas em 720dp — que
 * antes eram todas saltos no mesmo quadro.
 *
 * A leitura de `value` acontece no posicionamento, e é ela que pede o próximo
 * posicionamento enquanto a mola anda; as larguras continuam saltando, porque
 * medir a cada quadro custaria a grade inteira.
 */
private fun placeAnimated(
    key: UsageTargetKey,
    target: IntOffset,
    placements: HashMap<UsageTargetKey, Animatable<IntOffset, AnimationVector2D>>,
    scope: CoroutineScope,
    spec: FiniteAnimationSpec<IntOffset>
): IntOffset {
    val animatable = placements.getOrPut(key) { Animatable(target, IntOffset.VectorConverter) }
    if (animatable.targetValue != target) {
        scope.launch { animatable.animateTo(target, spec) }
    }
    return animatable.value
}

private data class CardGridBounds(
    val topLeft: Offset,
    val width: Int,
    val height: Int
) {
    val center: Offset
        get() = Offset(
            x = topLeft.x + width / 2f,
            y = topLeft.y + height / 2f
        )
}

private data class CardGridRow(
    val placeables: List<Placeable>
) {
    val height: Int
        get() = placeables.maxOf { placeable -> placeable.height }
}
