package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CollectorKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerminalConfigLabelsTest {

    @Test
    fun `the unanswered warning says the limit the client waited, rounded up`() {
        val pt = TerminalConfigLabels(AppLanguage.PT)
        val en = TerminalConfigLabels(AppLanguage.EN)

        assertEquals(
            "O serviço não respondeu em 10 s, e nada mudou. Veja se ele está de pé com usage-monitor status.",
            pt.notAnswered(CollectorKind.SERVICE, 10_000)
        )
        assertTrue(pt.notAnswered(CollectorKind.APP, 3_000).startsWith("O app aberto não respondeu em 3 s,"))
        assertTrue(en.notAnswered(CollectorKind.SERVICE, 200).startsWith("The service did not answer within 1 s,"))
    }
}
