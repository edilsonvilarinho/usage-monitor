package com.usagemonitor.domain.entity

/**
 * Soma de contagens não negativas (tokens, requisições) que trava em `Long.MAX_VALUE`
 * em vez de dar a volta para negativo. Dono único: o Gemini somava assim num helper
 * privado e o HUD repetia a mesma expressão inline.
 */
fun saturatingAdd(left: Long, right: Long): Long {
    return if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right
}
