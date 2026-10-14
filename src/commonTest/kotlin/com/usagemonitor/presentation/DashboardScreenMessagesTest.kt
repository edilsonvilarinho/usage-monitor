package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.decodeToastMessage
import com.usagemonitor.presentation.ui.formatRetryClock
import com.usagemonitor.presentation.viewmodel.DashboardToast
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class DashboardScreenMessagesTest {

    @Test
    fun `prazo do backoff sai em BRT com dois digitos`() {
        // 03:07 UTC é 00:07 em São Paulo (UTC-3).
        assertEquals("00:07", formatRetryClock(Instant.parse("2026-10-14T03:07:00Z")))
        assertEquals("14:05", formatRetryClock(Instant.parse("2026-10-14T17:05:00Z")))
    }

    @Test
    fun `limite com prazo diz ate quando em cada idioma`() {
        val toast = DashboardToast.RateLimit(ApiSource.ANTHROPIC, Instant.parse("2026-10-14T17:05:00Z"))

        assertEquals("Anthropic limitado temporariamente - aguarde até 14:05 BRT.", decodeToastMessage(toast, AppLanguage.PT))
        assertEquals("Anthropic temporarily limited - wait until 14:05 BRT.", decodeToastMessage(toast, AppLanguage.EN))
    }

    @Test
    fun `limite sem prazo espera a proxima coleta`() {
        val toast = DashboardToast.RateLimit(ApiSource.CODEX)

        assertEquals("Codex limitado temporariamente - aguardando próxima atualização...", decodeToastMessage(toast, AppLanguage.PT))
    }

    @Test
    fun `fonte indisponivel, erro e pagina da release`() {
        assertEquals(
            "OpenRouter is temporarily unavailable - retry in a few moments.",
            decodeToastMessage(DashboardToast.ServiceUnavailable(ApiSource.OPENROUTER), AppLanguage.EN)
        )
        assertEquals("Kilo Free: timeout", decodeToastMessage(DashboardToast.ApiError(ApiSource.KILO, "timeout"), AppLanguage.PT))
        assertEquals(
            "Não foi possível abrir a página da release. sem navegador",
            decodeToastMessage(DashboardToast.ReleasePageError("sem navegador"), AppLanguage.PT)
        )
    }
}
