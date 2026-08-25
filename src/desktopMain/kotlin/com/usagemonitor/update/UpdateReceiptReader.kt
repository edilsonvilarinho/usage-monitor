package com.usagemonitor.update

import com.usagemonitor.domain.entity.AppUpdateReceipt
import com.usagemonitor.domain.entity.AppUpdateReceiptStatus
import java.io.File
import java.util.Properties

/** Caminho padrão do recibo, escrito pelo instalador silencioso. */
internal fun defaultUpdateReceiptFile(): File {
    val home = System.getProperty("user.home") ?: return File(".usage-monitor/update-receipt.properties")
    return File(home, ".usage-monitor/update-receipt.properties")
}

/**
 * Lê o recibo da última tentativa de atualização.
 *
 * Devolve `null` para arquivo ausente, ilegível ou incompleto — nenhum dos três
 * é erro: o arquivo só existe depois da primeira atualização silenciosa, e a tela
 * simplesmente não mostra a linha. **Recibo sem `version` não é recibo**: exibir
 * "última atualização: —" afirmaria que houve uma.
 */
internal fun readUpdateReceipt(file: File = defaultUpdateReceiptFile()): AppUpdateReceipt? {
    if (!file.isFile) {
        return null
    }

    val properties = runCatching {
        Properties().apply {
            file.inputStream().use { load(it) }
        }
    }.getOrNull() ?: return null

    val version = properties.getProperty("version")?.trim()?.takeIf { it.isNotEmpty() } ?: return null

    // Só "success" é sucesso. Um valor desconhecido — arquivo truncado, escrita
    // interrompida — é tratado como falha, que é o lado seguro: anunciar sucesso
    // de uma atualização que não se sabe se aconteceu é pior que anunciar falha
    // de uma que deu certo.
    val status = if (properties.getProperty("status")?.trim().equals("success", ignoreCase = true)) {
        AppUpdateReceiptStatus.SUCCESS
    } else {
        AppUpdateReceiptStatus.FAILED
    }

    return AppUpdateReceipt(
        version = version,
        previousVersion = properties.getProperty("previousVersion")?.trim()?.takeIf { it.isNotEmpty() },
        status = status,
        reason = properties.getProperty("reason")?.trim()?.takeIf { it.isNotEmpty() }
    )
}

/**
 * Escreve um recibo de falha quando o pacote nem chegou a ser entregue.
 *
 * O recibo é normalmente escrito pelo instalador NSIS. Quando `schedule` falha,
 * o instalador **não roda**, então ninguém escreve nada: o usuário fecha o app
 * esperando a atualização, o app não volta, e o disco fica sem rastro. Medido na
 * atividade A20 do plano de atualização automática.
 *
 * Mesmo formato e mesmo caminho do recibo do instalador, para o leitor e a tela
 * não ganharem um segundo caso. `previousVersion` não vai: quem a conhece é o
 * instalador, e inventá-la aqui seria afirmar o que não se sabe.
 */
internal fun writeUpdateScheduleFailureReceipt(
    version: String,
    reason: String,
    file: File = defaultUpdateReceiptFile()
) {
    runCatching {
        file.parentFile?.mkdirs()
        val properties = Properties()
        properties.setProperty("version", version)
        properties.setProperty("status", "failed")
        properties.setProperty("reason", reason)
        file.outputStream().use { properties.store(it, "usage-monitor update schedule failure") }
    }
}
