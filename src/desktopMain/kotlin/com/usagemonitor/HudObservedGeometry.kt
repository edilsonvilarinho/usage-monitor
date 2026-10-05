package com.usagemonitor

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.HudAccount
import com.usagemonitor.presentation.ui.theme.AppSpacing

/** Quatro modelos visíveis; os demais continuam acessíveis pela rolagem. */
internal const val HUD_OBSERVED_VISIBLE_MODELS = 4
internal val HUD_OBSERVED_MODEL_HEIGHT = HUD_BALLOON_QUOTA_TITLE + HUD_WORD_LINE * 2 + AppSpacing.sm * 2 + 1.dp
internal val HUD_OBSERVED_NOTE_HEIGHT = HUD_WORD_LINE * 2

private fun hudObservedFixedHeight(account: HudAccount): Dp {
    var height = HUD_BALLOON_PADDING * 2 + HUD_BALLOON_HEADER +
        HUD_BALLOON_SECTION_GAP * 3 + HUD_OBSERVED_NOTE_HEIGHT + HUD_BALLOON_ACTIONS
    if (account.detailLine != null) height += HUD_BALLOON_SECTION_GAP + HUD_BALLOON_FOOTER
    if (account.sessionSignals.isNotEmpty()) height += HUD_BALLOON_SECTION_GAP + hudSessionBannerHeight(account.sessionSignals.size)
    return height
}

internal fun hudObservedViewportHeight(account: HudAccount, maxHeight: Dp = Dp.Infinity): Dp {
    val natural = HUD_OBSERVED_MODEL_HEIGHT * account.observedModels.size.coerceAtMost(HUD_OBSERVED_VISIBLE_MODELS)
    return minOf(natural, (maxHeight - hudObservedFixedHeight(account)).coerceAtLeast(0.dp))
}

internal fun hudObservedBalloonHeight(account: HudAccount, maxHeight: Dp): Dp =
    hudObservedFixedHeight(account) + hudObservedViewportHeight(account, maxHeight)
