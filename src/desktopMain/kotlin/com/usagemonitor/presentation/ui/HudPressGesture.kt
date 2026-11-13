package com.usagemonitor.presentation.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.onClick
import com.usagemonitor.domain.entity.UsageTargetKey
import kotlinx.coroutines.withTimeoutOrNull

/** Tempo de pressão sem sair do limiar para virar arrasto de reordenação (#584). */
internal const val HUD_REORDER_HOLD_MILLIS = 300L

/**
 * Um gesto só para as ações do notch: mover (só pela mão) e clicar num anel. O
 * que separa clique de arrasto é o limiar de deslocamento. O botão direito é
 * decidido no próprio `down` e engolido: não vira arrasto nem recoleta conta —
 * levava ao modo somente cards (#215), que saiu do app. Nenhuma coordenada sai daqui.
 *
 * Sem [draggable], passar do limiar só desiste do clique: o ponteiro que
 * escorregou não recoleta a conta, e o `move` não é consumido.
 *
 * [onHoldStart] roda uma vez se o ponteiro ficar [HUD_REORDER_HOLD_MILLIS] sem
 * passar do limiar. Se devolver `true`, o gesto vira arrasto de reordenação:
 * [onHoldMove] recebe cada posição e [onHoldEnd] o soltar, sem clique. Se
 * devolver `false`, segue como clique.
 */
@Composable
internal fun Modifier.hudPressGesture(
    draggable: Boolean = true,
    onDragStart: () -> Unit,
    onDragMove: () -> Unit,
    onDragEnd: () -> Unit,
    /** Recebe a posição do `down`, no nó do gesto: é por ela que o notch acha o anel. */
    onClick: (Offset) -> Unit,
    onHoldStart: (Offset) -> Boolean = { false },
    onHoldMove: (Offset) -> Unit = {},
    onHoldEnd: () -> Unit = {},
): Modifier {
    val currentDragStart by rememberUpdatedState(onDragStart)
    val currentDragMove by rememberUpdatedState(onDragMove)
    val currentDragEnd by rememberUpdatedState(onDragEnd)
    val currentClick by rememberUpdatedState(onClick)
    val currentHoldStart by rememberUpdatedState(onHoldStart)
    val currentHoldMove by rememberUpdatedState(onHoldMove)
    val currentHoldEnd by rememberUpdatedState(onHoldEnd)

    return pointerInput(Unit) {
        awaitEachGesture {
            // `awaitFirstDown` só reage ao botão primário do mouse; o laço abaixo
            // é o mesmo, sem esse filtro, para o direito chegar aqui.
            var down: PointerInputChange
            while (true) {
                val event = awaitPointerEvent()
                val candidate = event.changes.firstOrNull { it.changedToDownIgnoreConsumed() }
                if (candidate != null) {
                    down = candidate
                    break
                }
            }

            if (currentEvent.buttons.isSecondaryPressed) {
                down.consume()
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { candidate -> candidate.id == down.id }
                        ?: break
                    change.consume()
                    if (!change.pressed) break
                }
                return@awaitEachGesture
            }

            var travelled = 0f
            var dragging = false
            var slipped = false
            var holdArmed = true
            var holding = false
            while (true) {
                // Enquanto a pressão não saiu do limiar, o tempo de espera é o da reordenação.
                val event = if (holdArmed && !holding) {
                    withTimeoutOrNull(HUD_REORDER_HOLD_MILLIS) { awaitPointerEvent() }
                } else {
                    awaitPointerEvent()
                }
                if (event == null) {
                    holdArmed = false
                    holding = currentHoldStart(down.position)
                    continue
                }
                val change = event.changes.firstOrNull { candidate -> candidate.id == down.id }
                if (change == null) {
                    if (holding) currentHoldEnd()
                    break
                }
                if (holding) {
                    if (!change.pressed) {
                        currentHoldEnd()
                        break
                    }
                    change.consume()
                    currentHoldMove(change.position)
                    continue
                }
                if (!change.pressed) {
                    when {
                        dragging -> currentDragEnd()
                        !slipped -> currentClick(down.position)
                    }
                    break
                }
                travelled += change.positionChange().getDistance()
                if (!dragging && !slipped && travelled > viewConfiguration.touchSlop) {
                    holdArmed = false
                    if (draggable) {
                        dragging = true
                        currentDragStart()
                    } else {
                        slipped = true
                    }
                }
                if (dragging) {
                    change.consume()
                    currentDragMove()
                }
            }
        }
    }
}

/**
 * Estado de uma reordenação em curso: a conta pega, o alvo e se o arrasto foi
 * cancelado pelo Esc. Não é estado de composição: é lido só no gesto e no teclado.
 */
internal class HudReorderSession {
    private var origin: Int? = null
    private var spans: List<CardAxisSpan> = emptyList()
    private var target = -1
    private var cancelled = false

    /** Índice da conta pega, ou `null` sem arrasto. */
    val draggedIndex: Int?
        get() = origin

    /** Alvo atual do arrasto. */
    val targetIndex: Int
        get() = target

    /** `true` depois do Esc: o anel volta e o arrasto só termina no soltar. */
    val isCancelled: Boolean
        get() = cancelled

    /** Começa o arrasto da conta [index], com os limites dos anéis ao longo da borda. */
    fun begin(index: Int, spans: List<CardAxisSpan>) {
        origin = index
        this.spans = spans
        target = index
        cancelled = false
    }

    /** Atualiza o alvo pela posição do ponteiro ao longo da borda. */
    fun moveTo(along: Float) {
        val from = origin ?: return
        target = dragTargetIndex(spans, from, along)
    }

    /** Esc: cancela o arrasto em curso. Devolve `false` se não havia arrasto. */
    fun cancel(): Boolean {
        if (origin == null) return false
        cancelled = true
        return true
    }

    /**
     * Fecha o arrasto. Devolve a ordem nova das contas, ou `null` se foi cancelado
     * ou se a conta ficou onde estava.
     */
    fun finish(keys: List<UsageTargetKey>): List<UsageTargetKey>? {
        val from = origin
        val result = if (from == null || cancelled || target == from) null else reorderedKeys(keys, from, target)
        origin = null
        spans = emptyList()
        cancelled = false
        return result
    }
}

/** Move o item de [from] para [to] na lista, com os vizinhos andando um passo. */
internal fun <T> reorderedKeys(items: List<T>, from: Int, to: Int): List<T> {
    val list = items.toMutableList()
    list.add(to, list.removeAt(from))
    return list
}

/**
 * Arrasto de reordenação de um notch (#584): sessão, foco do Esc e ponte do
 * gesto às caixas dos anéis. Fica fora de `HudNotch` para o composable caber
 * no limite de função.
 */
/** Arrasto visível (#584): a conta pega, o alvo e quanto o ponteiro andou ao longo da borda. */
internal class HudRingDrag(
    val index: Int,
    val target: Int,
    /** Ponteiro menos pressão, ao longo da borda: é quanto o anel pego anda. */
    val offset: Float,
    val spans: List<CardAxisSpan>,
)

/** Soltar com reordenação: quanto cada conta anda de vaga, ao longo da borda. Objeto novo a cada soltar. */
internal class HudRingDrop(val deltas: Map<UsageTargetKey, Float>)

/** Quanto anda cada conta de vaga com a ordem nova (início da vaga nova menos o da antiga). */
internal fun dropDeltas(
    spans: List<CardAxisSpan>,
    before: List<UsageTargetKey>,
    after: List<UsageTargetKey>,
): Map<UsageTargetKey, Float> =
    after.withIndex().associate { (index, key) -> key to spans[index].start - spans[before.indexOf(key)].start }

internal class HudRingReorder(
    private val session: HudReorderSession,
    private val bounds: HudRingHitBoxes,
    private val horizontal: () -> Boolean,
    private val count: () -> Int,
    private val keys: () -> List<UsageTargetKey>,
    private val onReorder: (List<UsageTargetKey>) -> Unit,
) {
    val focus = FocusRequester()

    /** Arrasto em curso, lido pela faixa para mover o capturado e as vizinhas. */
    var drag: HudRingDrag? by mutableStateOf(null)
        private set

    /** Último soltar com reordenação; a faixa assenta a partir dele. */
    var drop: HudRingDrop? by mutableStateOf(null)
        private set

    private var pressAlong = 0f
    private var pressSpans: List<CardAxisSpan> = emptyList()

    /** Pressão longa sobre um anel: `true` se o arrasto começou. */
    fun start(position: Offset): Boolean {
        val index = bounds.indexAt(position) ?: return false
        val spans = bounds.spansAlong(count(), horizontal()) ?: return false
        session.begin(index, spans)
        pressAlong = alongOf(position)
        pressSpans = spans
        drop = null
        drag = HudRingDrag(index, index, 0f, spans)
        focus.requestFocus()
        return true
    }

    fun move(position: Offset) {
        val along = alongOf(position)
        session.moveTo(along)
        val from = session.draggedIndex ?: return
        if (session.isCancelled) return
        drag = HudRingDrag(from, session.targetIndex, along - pressAlong, pressSpans)
    }

    fun end() {
        val before = keys()
        val order = session.finish(before)
        drag = null
        if (order == null) return
        drop = HudRingDrop(dropDeltas(pressSpans, before, order))
        onReorder(order)
    }

    /** Esc: `true` se havia arrasto para cancelar; o anel volta à vaga. */
    fun cancel(): Boolean {
        val cancelled = session.cancel()
        if (cancelled) drag = null
        return cancelled
    }

    private fun alongOf(position: Offset): Float = if (horizontal()) position.x else position.y
}

/** Guarda o arrasto de um notch; as leituras ao vivo passam por `rememberUpdatedState`. */
@Composable
internal fun rememberHudRingReorder(
    bounds: HudRingHitBoxes,
    accounts: List<HudAccount>,
    horizontal: Boolean,
    onReorderAccounts: (List<UsageTargetKey>) -> Unit,
): HudRingReorder {
    val currentAccounts by rememberUpdatedState(accounts)
    val currentHorizontal by rememberUpdatedState(horizontal)
    val currentOnReorder by rememberUpdatedState(onReorderAccounts)
    return remember {
        HudRingReorder(
            session = HudReorderSession(),
            bounds = bounds,
            horizontal = { currentHorizontal },
            count = { currentAccounts.size },
            keys = { currentAccounts.map { account -> account.targetKey } },
            onReorder = { order -> currentOnReorder(order) },
        )
    }
}

/** Foco no corpo do notch para o Esc chegar ao arrasto; só o arrasto em curso o consome. */
internal fun Modifier.hudReorderKeys(reorder: HudRingReorder): Modifier = this
    .focusRequester(reorder.focus)
    .focusable()
    .onKeyEvent { event ->
        event.type == KeyEventType.KeyDown && event.key == Key.Escape && reorder.cancel()
    }

/** Conta do anel sob o ponto, para o clique curto que recoleta. */
internal fun hudRingKeyAt(bounds: HudRingHitBoxes, accounts: List<HudAccount>, position: Offset): UsageTargetKey? =
    bounds.indexAt(position)?.let { index -> accounts.getOrNull(index) }?.targetKey
