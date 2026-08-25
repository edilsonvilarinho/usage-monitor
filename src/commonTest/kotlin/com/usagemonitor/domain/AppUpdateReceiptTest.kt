package com.usagemonitor.domain

import com.usagemonitor.domain.entity.AppUpdateReceipt
import com.usagemonitor.domain.entity.AppUpdateReceiptStatus
import com.usagemonitor.domain.entity.shouldDiscardUpdateArtifacts
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppUpdateReceiptTest {

    @Test
    fun `a successful receipt for the running version releases the artifact`() {
        assertTrue(shouldDiscardUpdateArtifacts(receipt(version = "39.0.0"), currentVersion = "39.0.0"))
    }

    @Test
    fun `a failed receipt keeps the artifact`() {
        // O download é retomável, e um arquivo íntegro no disco não toca a rede:
        // apagá-lo obrigaria a rebaixar 120 MB na tentativa seguinte.
        val failed = receipt(version = "39.0.0", status = AppUpdateReceiptStatus.FAILED, reason = "locked")

        assertFalse(shouldDiscardUpdateArtifacts(failed, currentVersion = "37.0.0"))
    }

    @Test
    fun `a successful receipt for another version keeps the artifact`() {
        // Recibo de sucesso da 39 com o app em 37 é prova de que a troca não
        // aconteceu — o artefato ainda pode ser o caminho até ela.
        assertFalse(shouldDiscardUpdateArtifacts(receipt(version = "39.0.0"), currentVersion = "37.0.0"))
    }

    @Test
    fun `no receipt releases nothing`() {
        // Quem nunca atualizou pode ter um artefato baixado e ainda não aplicado.
        assertFalse(shouldDiscardUpdateArtifacts(receipt = null, currentVersion = "37.0.0"))
    }

    private fun receipt(
        version: String,
        status: AppUpdateReceiptStatus = AppUpdateReceiptStatus.SUCCESS,
        reason: String? = null
    ) = AppUpdateReceipt(
        version = version,
        previousVersion = "37.0.0",
        status = status,
        reason = reason
    )
}
