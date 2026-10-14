package com.usagemonitor.domain.entity

/**
 * Como um segredo aparece em `toString`: `null` e vazio continuam distinguíveis
 * (dizem "não configurado"), qualquer outro valor vira `***`.
 *
 * Dono único da forma: tipo com credencial (token, chave de API, senha) sobrescreve
 * `toString` com esta função, e `ArchitectureRulesTest` cobra o `override` em toda
 * `data class` com campo de segredo — o `toString` gerado vazaria o valor em log,
 * relatório de bug ou mensagem de exceção.
 */
fun redactedSecret(value: String?): String {
    return when {
        value == null -> "null"
        value.isEmpty() -> ""
        else -> "***"
    }
}
