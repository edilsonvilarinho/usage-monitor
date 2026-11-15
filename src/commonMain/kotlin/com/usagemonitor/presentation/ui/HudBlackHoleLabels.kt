package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.AppLanguage

/** Contagem das contas que estão no buraco negro da HUD (issue #600). */
fun hudBlackHoleCountLabel(count: Int, language: AppLanguage): String =
    if (count == 1) {
        localized(language, "1 minimizada", "1 minimized")
    } else {
        localized(language, "$count minimizadas", "$count minimized")
    }

/** Volta do balão de uma conta do buraco: "← N no buraco" (#600, A05). */
fun hudBlackHoleBackLabel(count: Int, language: AppLanguage): String =
    localized(language, "← $count no buraco", "← $count in the black hole")

/** Selo "auto" da conta auto-minimizada na lista do buraco (#600, A05). */
fun hudBlackHoleAutoBadge(language: AppLanguage): String = localized(language, "auto", "auto")

/** Texto da conta auto-minimizada no balão: volta sozinha quando couber (#600, A05). */
fun hudBlackHoleAutoNote(language: AppLanguage): String =
    localized(language, "volta sozinha quando couber", "returns on its own when there is room")
