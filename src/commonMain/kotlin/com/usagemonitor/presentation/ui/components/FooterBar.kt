package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.theme.AppShapes
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

const val FOOTER_ADMIN_OVERVIEW_TEST_TAG = "footerAdminOverview"

const val FOOTER_TEAM_PRESENCE_TEST_TAG = "footerTeamPresence"

/**
 * Âncoras do rodapé.
 *
 * A versão e a contagem regressiva são hoje dois emblemas arredondados e viram
 * texto solto de uma barra de estado. O conteúdo é o mesmo — `v1.1.0`, `02:05` —,
 * mas o nó que o carrega muda de forma, e o assert por texto encontraria também
 * qualquer outro lugar da tela onde a versão apareça.
 */
const val FOOTER_VERSION_TEST_TAG = "footerVersion"
const val FOOTER_COUNTDOWN_TEST_TAG = "footerCountdown"

@Composable
fun FooterBar(
    appVersion: String,
    language: AppLanguage,
    nextRefreshAt: Instant,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    nowProvider: () -> Instant = { Clock.System.now() },
    countdownUpdatesEnabled: Boolean = true,
    waitNextTick: suspend () -> Unit = { delay(1_000L) },
    /**
     * Abre a visão de todas as contas do servidor.
     *
     * `null` esconde o botão — é o estado de quem não administra, que é a
     * maioria. O componente segue sem saber o que é modo admin: quem decide é o
     * `Main`, a partir das configurações de time.
     */
    onOpenAdminOverview: (() -> Unit)? = null,
    /**
     * Abre a presença de todas as contas do servidor.
     *
     * `null` esconde, pela mesma razão de [onOpenAdminOverview]: o integrante
     * comum entra pela porta do card, que já é escopada na conta dele.
     */
    onOpenTeamPresence: (() -> Unit)? = null
) {
    val initialRemaining = (nextRefreshAt - nowProvider()).inWholeSeconds.coerceAtLeast(0).toInt()
    var secondsUntilRefresh by remember(nextRefreshAt) { mutableStateOf(initialRemaining) }

    LaunchedEffect(nextRefreshAt, countdownUpdatesEnabled) {
        secondsUntilRefresh = (nextRefreshAt - nowProvider()).inWholeSeconds.coerceAtLeast(0).toInt()
        if (!countdownUpdatesEnabled) {
            return@LaunchedEffect
        }

        while (true) {
            val remaining = (nextRefreshAt - nowProvider()).inWholeSeconds.coerceAtLeast(0).toInt()
            secondsUntilRefresh = remaining
            if (remaining <= 0) {
                break
            }
            waitNextTick()
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FooterCompactStatusGroup(
                appVersion = appVersion,
                language = language,
                secondsUntilRefresh = secondsUntilRefresh,
                dense = true,
                modifier = Modifier.weight(1f)
            )
            FooterActionGroup(
                language = language,
                onRefresh = onRefresh,
                onOpenSettings = onOpenSettings,
                iconOnly = true,
                onOpenAdminOverview = onOpenAdminOverview,
                onOpenTeamPresence = onOpenTeamPresence
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FooterCompactStatusGroup(
    appVersion: String,
    language: AppLanguage,
    secondsUntilRefresh: Int,
    dense: Boolean = false,
    modifier: Modifier = Modifier
) {
    val countdown = formatRefreshCountdown(secondsUntilRefresh)
    val refreshLabel = if (dense) {
        countdown
    } else if (language == AppLanguage.PT) {
        "Próx. $countdown"
    } else {
        "Next $countdown"
    }
    val versionTooltip = if (language == AppLanguage.PT) "Versão do app" else "App version"
    val refreshTooltip = if (language == AppLanguage.PT) {
        "Próxima atualização automática"
    } else {
        "Next automatic refresh"
    }

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FooterCompactBadge(
            text = "v$appVersion",
            tooltipLabel = versionTooltip,
            testTag = FOOTER_VERSION_TEST_TAG
        )
        FooterCompactBadge(
            text = refreshLabel,
            tooltipLabel = refreshTooltip,
            testTag = FOOTER_COUNTDOWN_TEST_TAG
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FooterCompactBadge(
    text: String,
    tooltipLabel: String,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(tooltipLabel)
            }
        },
        state = rememberTooltipState(isPersistent = true)
    ) {
        Surface(
            modifier = modifier,
            shape = AppShapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // A tag mora no texto e não no `Surface`: é o texto que o assert
                // compara, e o nó do `Surface` não carrega nenhum.
                modifier = Modifier
                    .let { base -> if (testTag == null) base else base.testTag(testTag) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun FooterActionGroup(
    language: AppLanguage,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    iconOnly: Boolean = false,
    modifier: Modifier = Modifier,
    onOpenAdminOverview: (() -> Unit)? = null,
    onOpenTeamPresence: (() -> Unit)? = null
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Vem antes da visão global e ao lado dela: as duas são do administrador
        // e abrem o servidor inteiro, só que por perguntas diferentes — quem está
        // aí agora, e quanto cada um gastou.
        if (onOpenTeamPresence != null) {
            FooterIconActionButton(
                label = if (language == AppLanguage.PT) {
                    "Quem está conectado agora"
                } else {
                    "Who is connected now"
                },
                onClick = onOpenTeamPresence,
                testTag = FOOTER_TEAM_PRESENCE_TEST_TAG
            ) {
                Icon(
                    imageVector = Icons.Rounded.Sensors,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        if (onOpenAdminOverview != null) {
            FooterIconActionButton(
                label = if (language == AppLanguage.PT) {
                    "Ver todas as contas do time"
                } else {
                    "View all team accounts"
                },
                onClick = onOpenAdminOverview,
                testTag = FOOTER_ADMIN_OVERVIEW_TEST_TAG
            ) {
                Icon(
                    imageVector = Icons.Rounded.Groups,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        FooterIconActionButton(
            label = if (language == AppLanguage.PT) "Atualizar agora" else "Refresh now",
            onClick = onRefresh
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        if (iconOnly) {
            FooterIconActionButton(
                label = if (language == AppLanguage.PT) "Abrir configurações" else "Open settings",
                onClick = onOpenSettings
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        } else {
            TextButton(
                onClick = onOpenSettings,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (language == AppLanguage.PT) "Configurações" else "Settings",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FooterIconActionButton(
    label: String,
    onClick: () -> Unit,
    testTag: String? = null,
    content: @Composable () -> Unit
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(label)
            }
        },
        state = rememberTooltipState(isPersistent = true)
    ) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier
                .height(34.dp)
                .width(34.dp)
                .semantics {
                    contentDescription = label
                }
                .let { base -> if (testTag == null) base else base.testTag(testTag) }
        ) {
            content()
        }
    }
}

internal fun formatRefreshCountdown(secondsUntilRefresh: Int): String {
    val minutes = secondsUntilRefresh / 60
    val seconds = secondsUntilRefresh % 60
    return String.format("%02d:%02d", minutes, seconds)
}
