package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.teamPresenceWindowTitle
import kotlin.test.Test
import kotlin.test.assertEquals

class TeamPresenceFormattingTest {

    @Test
    fun `titulo nomeia a conta quando ha rotulo`() {
        assertEquals("Conectados agora — ana@empresa.com", teamPresenceWindowTitle(AppLanguage.PT, "ana@empresa.com"))
        assertEquals("Connected now", teamPresenceWindowTitle(AppLanguage.EN, null))
    }

    /** Visão de admin vem de quem abriu a janela; rótulo em branco numa conta não a liga. */
    @Test
    fun `visao de todas as contas so pelo parametro`() {
        assertEquals("Conectados agora", teamPresenceWindowTitle(AppLanguage.PT, "  "))
        assertEquals("Connected now — all accounts", teamPresenceWindowTitle(AppLanguage.EN, null, isAdminOverview = true))
    }
}
