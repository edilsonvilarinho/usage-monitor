package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.usageTargetPublicationOrder

// Separador da preferência: vírgula, igual às outras listas gravadas em texto.
private const val MINIMIZED_SEPARATOR = ","

/** Alterna a conta: minimizada volta a ficar visível, visível vira minimizada. Não muta o conjunto recebido. */
internal fun toggleHudMinimizedAccount(
    minimized: Set<UsageTargetKey>,
    target: UsageTargetKey
): Set<UsageTargetKey> =
    if (target in minimized) minimized - target else minimized + target

/** Grava na ordem de publicação: duas leituras iguais geram o mesmo texto. */
internal fun encodeHudMinimizedAccounts(minimized: Set<UsageTargetKey>): String =
    minimized
        .sortedWith(usageTargetPublicationOrder)
        .joinToString(MINIMIZED_SEPARATOR) { target -> target.storageKey }

/**
 * Lê o texto gravado. Entradas em branco ou desconhecidas são descartadas. Contas que
 * deixaram de existir não são filtradas aqui: ficam no conjunto e voltam ao texto na
 * escrita seguinte.
 */
internal fun decodeHudMinimizedAccounts(raw: String?): Set<UsageTargetKey> {
    if (raw.isNullOrBlank()) return emptySet()
    return raw.split(MINIMIZED_SEPARATOR)
        .map { entry -> entry.trim() }
        .filter { entry -> entry.isNotEmpty() }
        .mapNotNull { entry -> UsageTargetKey.fromStorageKey(entry) }
        .toSet()
}
