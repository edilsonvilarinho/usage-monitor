package com.usagemonitor.presentation.terminal

import kotlin.test.Test
import kotlin.test.assertEquals

class TuiReducerTest {

    private val withTeam = TuiContext(teamAvailable = true, stacked = false)
    private val withoutTeam = TuiContext(teamAvailable = false, stacked = false)

    private fun press(state: TuiState, key: TuiKey, context: TuiContext = withTeam) = TuiReducer.reduce(state, key, context)

    @Test
    fun `numero troca de aba e a aba 4 so existe com time`() {
        assertEquals(TuiTab.HISTORY, press(TuiState(), TuiKey.Text('3')).state.tab)
        assertEquals(TuiTab.TEAM, press(TuiState(), TuiKey.Text('4')).state.tab)
        assertEquals(TuiTab.OVERVIEW, press(TuiState(), TuiKey.Text('4'), withoutTeam).state.tab)
        assertEquals(TuiTab.SETTINGS, press(TuiState(), TuiKey.Text('5'), withoutTeam).state.tab)
        assertEquals(TuiTab.OVERVIEW, press(TuiState(), TuiKey.Text('9')).state.tab)
    }

    @Test
    fun `q e ctrl c saem e r rele`() {
        assertEquals(listOf<TuiEffect>(TuiEffect.Quit), press(TuiState(), TuiKey.Text('q')).effects)
        assertEquals(listOf<TuiEffect>(TuiEffect.Quit), press(TuiState(helpOpen = true), TuiKey.Interrupt).effects)
        assertEquals(listOf<TuiEffect>(TuiEffect.Refresh), press(TuiState(), TuiKey.Text('r')).effects)
    }

    @Test
    fun `interrogacao abre a ajuda e esc ou interrogacao fecham`() {
        val open = press(TuiState(), TuiKey.Text('?')).state
        assertEquals(true, open.helpOpen)
        assertEquals(false, press(open, TuiKey.Escape).state.helpOpen)
        assertEquals(false, press(open, TuiKey.Text('?')).state.helpOpen)
        // Com a ajuda aberta, as outras teclas não mexem em nada por baixo dela.
        assertEquals(open, press(open, TuiKey.Text('3')).state)
        assertEquals(listOf<TuiEffect>(TuiEffect.Quit), press(open, TuiKey.Text('q')).effects)
    }

    @Test
    fun `a aba do time que sumiu volta para a visao geral`() {
        assertEquals(TuiTab.OVERVIEW, TuiState(tab = TuiTab.TEAM).normalized(withoutTeam).tab)
        assertEquals(TuiTab.TEAM, TuiState(tab = TuiTab.TEAM).normalized(withTeam).tab)
    }
}
