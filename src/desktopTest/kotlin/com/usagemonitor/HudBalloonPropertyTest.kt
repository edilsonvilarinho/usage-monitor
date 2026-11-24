package com.usagemonitor

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.ui.HudAccount
import com.usagemonitor.presentation.ui.HudFloatingBalloon
import com.usagemonitor.presentation.ui.HudNotchBalloonState
import com.usagemonitor.presentation.ui.components.AppTone
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Sequências sorteadas de até 30 ações sobre o balão, com a regra escrita conferida depois de cada ação.
 * Só entra regra escrita no KDoc de [HudNotchBalloonState] e de [hudFloatingBalloonAfterMinimize].
 * `FollowAccounts` (composable com efeitos) fica fora: nenhum teste o roda em `runDesktopComposeUiTest`.
 */
class HudBalloonPropertyTest {

    @Test
    fun notchBalloonSequencesKeepTheDocumentedRules() {
        forAllNotchSequences()
    }

    @Test
    fun floatingBalloonSequencesFollowTheMinimizeRule() {
        forAllFloatingSequences()
    }

    private fun forAllNotchSequences() {
        val random = Random(SEED)
        repeat(ITERATIONS) { iteration ->
            val case = generateNotchCase(random)
            val failure = failureOf { replayNotch(case) } ?: return@repeat
            val smallest = case.copy(
                actions = shrinkActions(case.actions) { candidate ->
                    failureOf { replayNotch(case.copy(actions = candidate)) } != null
                }
            )
            throw AssertionError(
                "propriedade falhou: semente=$SEED, iteração=$iteration, menor caso=$smallest: " +
                    "${failureOf { replayNotch(smallest) }?.message}",
                failure
            )
        }
    }

    private fun forAllFloatingSequences() {
        val random = Random(SEED)
        repeat(ITERATIONS) { iteration ->
            val case = generateFloatingCase(random)
            val failure = failureOf { replayFloating(case) } ?: return@repeat
            val smallest = case.copy(
                actions = shrinkActions(case.actions) { candidate ->
                    failureOf { replayFloating(case.copy(actions = candidate)) } != null
                }
            )
            throw AssertionError(
                "propriedade falhou: semente=$SEED, iteração=$iteration, menor caso=$smallest: " +
                    "${failureOf { replayFloating(smallest) }?.message}",
                failure
            )
        }
    }

    // ---- Balão do notch: HudNotchBalloonState ----

    private sealed interface NotchAction {
        data class Ring(val index: Int) : NotchAction
        data class HoleClick(val hole: Int) : NotchAction
        data class HoleHover(val hole: Int) : NotchAction
        data class RowSelect(val position: Int?) : NotchAction
        data object Close : NotchAction
    }

    private data class NotchCase(val count: Int, val actions: List<NotchAction>)

    /** Linha da lista fora da lista aberta: a UI não a oferece; o encolhimento descarta o candidato. */
    private class PreconditionBroken : Exception()

    private fun generateNotchCase(random: Random): NotchCase {
        val count = random.nextInt(0, 7)
        val accounts = balloonAccountsOf(count)
        val state = HudNotchBalloonState(initialBalloonIndex = null)
        val actions = List(random.nextInt(1, 31)) {
            val action = nextNotchAction(random, state, count)
            applyNotch(action, state, accounts) {}
            action
        }
        return NotchCase(count, actions)
    }

    /** Buraco = índice após o último anel, como em `HudNotch.kt`. Linha só existe com a lista do buraco aberta. */
    private fun nextNotchAction(random: Random, state: HudNotchBalloonState, count: Int): NotchAction {
        val hole = count
        return when (random.nextInt(6)) {
            0 -> if (count > 0) NotchAction.Ring(random.nextInt(count)) else NotchAction.HoleHover(hole)
            1 -> NotchAction.HoleClick(hole)
            2 -> NotchAction.HoleHover(hole)
            3 -> NotchAction.Close
            4 -> if (state.balloonIndex == hole) {
                NotchAction.RowSelect(if (count > 0 && random.nextBoolean()) random.nextInt(count) else null)
            } else {
                NotchAction.Close
            }
            else -> NotchAction.HoleClick(hole)
        }
    }

    private fun replayNotch(case: NotchCase) {
        val accounts = balloonAccountsOf(case.count)
        val hole = case.count
        val state = HudNotchBalloonState(initialBalloonIndex = null)
        for (action in case.actions) {
            if (action is NotchAction.RowSelect && state.balloonIndex != hole) throw PreconditionBroken()
            val beforeBalloon = state.balloonIndex
            val beforeSelection = state.holeSelection
            val refreshed = mutableListOf<UsageTargetKey>()
            applyNotch(action, state, accounts) { refreshed.add(it) }
            checkNotchStep(action, hole, accounts, beforeBalloon, beforeSelection, state, refreshed)
        }
    }

    private fun applyNotch(
        action: NotchAction,
        state: HudNotchBalloonState,
        accounts: List<HudAccount>,
        onRefresh: (UsageTargetKey) -> Unit
    ) {
        when (action) {
            is NotchAction.Ring -> state.ringHovered(action.index)
            is NotchAction.HoleClick -> state.holeClicked(action.hole)
            is NotchAction.HoleHover -> state.holeHovered(action.hole)
            is NotchAction.RowSelect -> state.holeRowSelected(action.position, accounts, onRefresh)
            NotchAction.Close -> state.closeHole()
        }
    }

    private fun checkNotchStep(
        action: NotchAction,
        hole: Int,
        accounts: List<HudAccount>,
        beforeBalloon: Int?,
        beforeSelection: Int?,
        state: HudNotchBalloonState,
        refreshed: List<UsageTargetKey>
    ) {
        val balloon = state.balloonIndex
        val selection = state.holeSelection
        if (balloon != hole) assertNull(selection, "$INV6 (balão=$balloon, lista=$selection)")
        when (action) {
            is NotchAction.Ring -> {
                assertEquals(action.index, balloon, "$INV1 (anel ${action.index})")
                assertNull(selection, INV1)
            }
            is NotchAction.HoleClick -> {
                if (beforeBalloon == hole && beforeSelection == null) {
                    assertNull(balloon, INV3)
                } else {
                    assertEquals(hole, balloon, INV3)
                    assertNull(selection, INV3)
                }
            }
            is NotchAction.HoleHover -> {
                if (beforeBalloon != hole) {
                    assertEquals(hole, balloon, INV2)
                    assertNull(selection, INV2)
                } else {
                    assertEquals(beforeSelection, selection, INV2)
                }
            }
            is NotchAction.RowSelect -> {
                assertEquals(action.position, selection, INV4)
                val expected = action.position?.let { listOf(accounts[it].targetKey) } ?: emptyList()
                assertEquals(expected, refreshed, INV4)
            }
            NotchAction.Close -> {
                assertNull(balloon, INV5)
                assertNull(selection, INV5)
            }
        }
    }

    // ---- Balão flutuante: hudFloatingBalloonAfterMinimize ----

    private sealed interface FloatingAction {
        data class Open(val balloon: HudFloatingBalloon?) : FloatingAction
        data class Toggle(val index: Int) : FloatingAction
    }

    private data class FloatingCase(val count: Int, val actions: List<FloatingAction>)

    private fun generateFloatingCase(random: Random): FloatingCase {
        val count = random.nextInt(0, 7)
        val actions = List(random.nextInt(1, 31)) {
            if (count > 0 && random.nextBoolean()) {
                FloatingAction.Toggle(random.nextInt(count))
            } else {
                FloatingAction.Open(randomFloatingBalloon(random, count))
            }
        }
        return FloatingCase(count, actions)
    }

    private fun randomFloatingBalloon(random: Random, count: Int): HudFloatingBalloon? =
        when (random.nextInt(5)) {
            0 -> null
            1 -> HudFloatingBalloon.App
            2 -> if (count > 0) HudFloatingBalloon.Account(random.nextInt(count)) else HudFloatingBalloon.App
            3 -> HudFloatingBalloon.Hole(null)
            else -> HudFloatingBalloon.Hole(if (count > 0) random.nextInt(count) else null)
        }

    /** Minimizar ou restaurar uma conta: a lista troca o `minimized` dela; a função recebe a conta alvo. */
    private fun replayFloating(case: FloatingCase) {
        var accounts = balloonAccountsOf(case.count)
        var balloon: HudFloatingBalloon? = null
        for (action in case.actions) {
            when (action) {
                is FloatingAction.Open -> balloon = action.balloon
                is FloatingAction.Toggle -> {
                    val target = accounts[action.index].targetKey
                    val after = hudFloatingBalloonAfterMinimize(balloon, accounts, target)
                    checkFloatingStep(balloon, after, accounts, target)
                    balloon = after
                    accounts = accounts.mapIndexed { index, account ->
                        if (index == action.index) account.copy(minimized = !account.minimized) else account
                    }
                }
            }
        }
    }

    private fun checkFloatingStep(
        before: HudFloatingBalloon?,
        after: HudFloatingBalloon?,
        accounts: List<HudAccount>,
        target: UsageTargetKey
    ) {
        when (before) {
            is HudFloatingBalloon.Account -> {
                if (accounts[before.index].targetKey == target) {
                    assertNull(after, F1)
                } else {
                    assertEquals(before, after, F3)
                }
            }
            is HudFloatingBalloon.Hole -> {
                val selected = before.selection?.let { accounts[it].targetKey }
                if (selected == target) assertNull(after, F2) else assertEquals(before, after, F3)
            }
            else -> assertEquals(before, after, F4)
        }
    }

    // ---- Suporte: contas, falha, encolhimento ----

    /** Contas com `minimized` alternado; só o alvo importa para as duas funções, mas a lista muda como no produto. */
    private fun balloonAccountsOf(count: Int): List<HudAccount> = List(count) { index ->
        HudAccount(
            targetKey = UsageTargetKey(ApiSource.ANTHROPIC, "perfil-$index"),
            label = "Conta $index",
            statusLabel = "OK",
            tone = AppTone.entries[0],
            quotas = emptyList(),
            focusIndex = 0,
            minimized = index % 2 == 0
        )
    }

    private fun failureOf(block: () -> Unit): Throwable? =
        try {
            block()
            null
        } catch (broken: PreconditionBroken) {
            null
        } catch (failure: Throwable) {
            failure
        }

    /** Remove ações uma a uma enquanto a sequência ainda reprova; termina quando nenhuma remoção reprova. */
    private fun <T> shrinkActions(actions: List<T>, fails: (List<T>) -> Boolean): List<T> {
        var current = actions
        var shrunk = true
        while (shrunk) {
            shrunk = false
            for (index in current.indices) {
                val candidate = current.filterIndexed { other, _ -> other != index }
                if (fails(candidate)) {
                    current = candidate
                    shrunk = true
                    break
                }
            }
        }
        return current
    }

    private companion object {
        const val SEED = 650L
        const val ITERATIONS = 500
        const val INV1 = "KDoc ringHovered: abre o balão do anel e fecha a lista do buraco"
        const val INV2 = "KDoc holeHovered: entrar no buraco abre a lista, salvo se o balão já é o buraco (não zera a conta aberta)"
        const val INV3 = "KDoc holeClicked: clique alterna a lista (aberta fecha; fechada ou com conta aberta mostra a lista)"
        const val INV4 = "KDoc holeRowSelected: linha abre o detalhe e recoleta; volta (null) só fecha o detalhe, sem coleta"
        const val INV5 = "KDoc closeHole: fecha o balão e a lista do buraco de uma vez"
        const val INV6 = "KDoc classe HudNotchBalloonState: a lista do buraco não fica com conta aberta que o balão deixou"
        const val F1 = "KDoc hudFloatingBalloonAfterMinimize (A05): minimizar/restaurar a conta do balão de conta fecha o balão"
        const val F2 = "KDoc hudFloatingBalloonAfterMinimize (A05): minimizar/restaurar a conta do detalhe do buraco fecha o balão"
        const val F3 = "KDoc hudFloatingBalloonAfterMinimize (A05): outra conta, o balão do app e a lista sem conta ficam"
        const val F4 = "KDoc hudFloatingBalloonAfterMinimize (A05): balão do app e lista sem conta escolhida ficam"
    }
}
