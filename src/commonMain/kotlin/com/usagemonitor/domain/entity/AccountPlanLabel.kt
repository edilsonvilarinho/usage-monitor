package com.usagemonitor.domain.entity

/**
 * O plano da conta como o usuário o reconhece: "Max 20x", "ChatGPT Plus",
 * "Ultra". Cada fornecedor informa o plano de um jeito, e estas funções são a
 * única tradução — o mesmo mapeamento do ai-usagebar, que já foi conferido
 * contra contas reais.
 *
 * `null` é "não informado", e a tela não mostra nada: um "Desconhecido" ao lado
 * do nome afirmaria que a conta tem um plano estranho, quando o que existe é a
 * falta do campo.
 */

/**
 * Claude: `subscriptionType` ("max", "pro", "team") do `.credentials.json`, e a
 * multiplicação do `rateLimitTier` ("default_claude_max_20x") quando existe. O
 * plano não vem do endpoint de uso — só do arquivo de credenciais.
 */
fun anthropicPlanLabel(subscriptionType: String?, rateLimitTier: String?): String? {
    val base = subscriptionType?.trim()?.takeIf { value -> value.isNotEmpty() } ?: return null
    val name = base.replaceFirstChar { char -> char.uppercaseChar() }
    val tier = rateLimitTier.orEmpty()
    return when {
        tier.contains("20x") -> "$name 20x"
        tier.contains("5x") -> "$name 5x"
        else -> name
    }
}

/**
 * Codex: `plan_type` da própria resposta de uso ("plus", "pro", "team"). O
 * prefixo "ChatGPT" é o nome do plano, não do produto — a assinatura que paga o
 * Codex é a do ChatGPT.
 */
fun codexPlanLabel(planType: String?): String? {
    val raw = planType?.trim()?.takeIf { value -> value.isNotEmpty() } ?: return null
    return "ChatGPT ${raw.replaceFirstChar { char -> char.uppercaseChar() }}"
}

/** Cursor: `membershipType` do resumo de uso ("pro", "ultra", "free"). */
fun cursorPlanLabel(membershipType: String?): String? {
    val raw = membershipType?.trim()?.takeIf { value -> value.isNotEmpty() } ?: return null
    return raw.replaceFirstChar { char -> char.uppercaseChar() }
}
