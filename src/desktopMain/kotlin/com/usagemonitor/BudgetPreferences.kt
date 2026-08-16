package com.usagemonitor

import com.russhwolf.settings.PreferencesSettings
import com.usagemonitor.domain.entity.MICROS_PER_USD

private const val MONTHLY_BUDGET_KEY = "monthlyBudgetMicros"

/** Teto máximo aceito; acima disso é dedo escorregado, não orçamento. */
private const val MAX_MONTHLY_BUDGET_MICROS = 1_000_000L * MICROS_PER_USD

/**
 * Teto mensal em micros de USD. Zero desliga o cartão.
 *
 * Guardado em micros, e não em dólares fracionários, pelo mesmo motivo do resto
 * do app: toda a aritmética de custo é inteira, e um `Double` aqui reintroduziria
 * erro de arredondamento na única comparação que importa — gasto contra teto.
 */
internal fun readPersistedBudgetMicros(settings: PreferencesSettings): Long {
    return clampBudgetMicros(settings.getLong(MONTHLY_BUDGET_KEY, 0L))
}

internal fun persistBudgetMicros(settings: PreferencesSettings, micros: Long) {
    settings.putLong(MONTHLY_BUDGET_KEY, clampBudgetMicros(micros))
}

internal fun clampBudgetMicros(micros: Long): Long {
    return micros.coerceIn(0L, MAX_MONTHLY_BUDGET_MICROS)
}

/**
 * Converte o que foi digitado nas Configurações.
 *
 * Aceita vírgula como separador decimal — em pt-BR é o que a pessoa digita — e
 * devolve `null` para texto que não é número, para o campo poder recusar sem
 * gravar um zero silencioso.
 */
internal fun parseBudgetUsd(text: String): Long? {
    val normalized = text.trim().replace(',', '.')
    if (normalized.isEmpty()) {
        return 0L
    }
    val value = normalized.toDoubleOrNull() ?: return null
    if (value < 0.0) {
        return null
    }
    return clampBudgetMicros((value * MICROS_PER_USD).toLong())
}

/** Formata o teto para o campo de texto: vazio quando desligado. */
internal fun formatBudgetUsd(micros: Long): String {
    if (micros <= 0L) {
        return ""
    }
    val dollars = micros / MICROS_PER_USD
    val cents = (micros % MICROS_PER_USD) / 10_000L
    if (cents == 0L) {
        return dollars.toString()
    }
    return "$dollars.${cents.toString().padStart(2, '0')}"
}
