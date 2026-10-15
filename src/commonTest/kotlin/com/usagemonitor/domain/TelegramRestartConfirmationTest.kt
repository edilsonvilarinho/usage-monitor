package com.usagemonitor.domain

import com.usagemonitor.domain.entity.TelegramPendingRestart
import com.usagemonitor.domain.entity.TelegramRestartOutcome
import com.usagemonitor.domain.entity.telegramRestartOutcome
import kotlin.test.Test
import kotlin.test.assertEquals

/** #417: o app que abre depois do reinício pedido pelo bot diz se a versão foi instalada. */
class TelegramRestartConfirmationTest {

    private val pending = TelegramPendingRestart(targetVersion = "41.11.0-beta.3", fromVersion = "41.11.0-beta.2")

    @Test
    fun `abrir na versao pedida confirma a instalacao`() {
        assertEquals(TelegramRestartOutcome.UPDATED, telegramRestartOutcome(pending, "41.11.0-beta.3"))
    }

    @Test
    fun `abrir numa versao mais nova que a pedida tambem confirma`() {
        // A estável da série vem depois de todas as betas dela (SemVer, #355).
        assertEquals(TelegramRestartOutcome.UPDATED, telegramRestartOutcome(pending, "41.11.0"))
    }

    @Test
    fun `abrir na versao de origem diz que a atualizacao nao foi aplicada`() {
        assertEquals(TelegramRestartOutcome.NOT_APPLIED, telegramRestartOutcome(pending, "41.11.0-beta.2"))
    }
}
