package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.SessionPulse

/** As janelas que o card de uma conta abre, na ordem da barra dele. */
enum class CardAction { HISTORY, CODEX_CLI_SESSIONS, CLI_SESSIONS, TEAM_USAGE, TEAM_PRESENCE }

/**
 * Quais janelas a conta [target] oferece. **Dono único** da regra, que tem duas
 * telas: a barra do card e o balão da conta na barra HUD. Morava inline na grade
 * de cards, e a HUD precisaria de uma segunda cópia das condições de time.
 *
 * Histórico sempre; sessões CLI na Anthropic e sessões Codex CLI no Codex; uso e
 * presença do time só na conta Anthropic marcada como parte do time — as duas
 * janelas leem o mesmo servidor, para a mesma conta.
 */
internal fun cardActionsFor(target: UsageTargetKey, teamEnabledProfileIds: Set<String>): List<CardAction> {
    return buildList {
        add(CardAction.HISTORY)
        if (target.source == ApiSource.CODEX) add(CardAction.CODEX_CLI_SESSIONS)
        if (target.source == ApiSource.ANTHROPIC) add(CardAction.CLI_SESSIONS)
        if (target.source == ApiSource.ANTHROPIC && target.profileId in teamEnabledProfileIds) {
            add(CardAction.TEAM_USAGE)
            add(CardAction.TEAM_PRESENCE)
        }
    }
}

/** O rótulo da ação, que é também a descrição do botão. */
internal fun cardActionLabel(action: CardAction, language: AppLanguage): String = when (action) {
    CardAction.HISTORY -> historyActionLabel(language)
    CardAction.CODEX_CLI_SESSIONS -> codexCliSessionsActionLabel(language)
    CardAction.CLI_SESSIONS -> cliSessionsActionLabel(language)
    CardAction.TEAM_USAGE -> teamUsageActionLabel(language)
    CardAction.TEAM_PRESENCE -> teamPresenceActionLabel(language)
}

/**
 * O botão de uma ação do card, o mesmo no card e no balão da HUD.
 *
 * O pisca de sessão só vai nos dois que o card já fazia piscar — sessões CLI e
 * uso do time: neste app ele significa uma coisa só, sessão em atenção.
 */
@Composable
internal fun CardActionButton(
    action: CardAction,
    language: AppLanguage,
    buttonSize: Dp,
    iconSize: Dp,
    onClick: () -> Unit,
    cliSessionPulse: SessionPulse = SessionPulse.EMPTY,
    teamSessionPulse: SessionPulse = SessionPulse.EMPTY
) {
    val pulse = when (action) {
        CardAction.CLI_SESSIONS -> cliSessionPulse
        CardAction.TEAM_USAGE -> teamSessionPulse
        else -> SessionPulse.EMPTY
    }
    val icon = when (action) {
        CardAction.HISTORY -> Icons.Rounded.History
        CardAction.CODEX_CLI_SESSIONS, CardAction.CLI_SESSIONS -> Icons.Rounded.Terminal
        CardAction.TEAM_USAGE -> Icons.Rounded.Groups
        CardAction.TEAM_PRESENCE -> Icons.Rounded.Sensors
    }
    CardIconActionButton(
        label = cardActionLabel(action, language),
        onClick = onClick,
        buttonSize = buttonSize,
        pulse = pulse,
        language = language
    ) { tint ->
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(iconSize), tint = tint)
    }
}
