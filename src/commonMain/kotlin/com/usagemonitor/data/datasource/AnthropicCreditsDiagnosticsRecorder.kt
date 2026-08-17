package com.usagemonitor.data.datasource

import kotlinx.serialization.Serializable

/**
 * Registro do que a Anthropic devolveu no bloco de créditos de uso.
 *
 * Existe pelo mesmo motivo do [CodexDiagnosticsRecorder]: quando o contrato da
 * fonte muda, o app precisa de rastro. Em agosto de 2026 a resposta deixou de
 * trazer os créditos por vários dias e a linha sumiu da tela sem deixar nada
 * para investigar — nem no cache, nem no histórico.
 *
 * [isEnabled] é público porque o caminho quente depende dele: só com o registro
 * ligado o corpo da resposta é lido como texto para guardar o payload cru.
 */
interface AnthropicCreditsDiagnosticsRecorder {
    val isEnabled: Boolean

    fun record(event: AnthropicCreditsDiagnosticsEvent)
}

object NoOpAnthropicCreditsDiagnosticsRecorder : AnthropicCreditsDiagnosticsRecorder {
    override val isEnabled: Boolean = false

    override fun record(event: AnthropicCreditsDiagnosticsEvent) = Unit
}

/**
 * Uma coleta.
 *
 * [extraUsageRaw] e [spendRaw] são os nós **crus** da resposta, em texto. Campos
 * derivados não revelariam um campo renomeado, que é justamente a hipótese que
 * este registro existe para confirmar ou descartar. O payload de uso não carrega
 * prompt nem resposta — só metadados de cota.
 */
@Serializable
data class AnthropicCreditsDiagnosticsEvent(
    val timestamp: String,
    val event: String = "anthropic_credits",
    val outcome: String,
    val extraUsageRaw: String? = null,
    val spendRaw: String? = null
)
