package com.usagemonitor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.rememberTrayState
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.buildHudAccounts
import com.usagemonitor.presentation.ui.hudDefaultShouldSwitch
import com.usagemonitor.presentation.ui.hudTraySummary
import com.usagemonitor.presentation.ui.usageAlertMessage
import kotlinx.datetime.Clock
import androidx.compose.ui.input.key.type
import com.russhwolf.settings.Settings
import com.usagemonitor.domain.repository.BreadcrumbRecorder

/**
 * O ícone da bandeja: ponto de risco, resumo das contas no tooltip, menu com os
 * caminhos de volta das molduras reduzidas e as notificações de alerta.
 *
 * Também mora aqui a troca para a HUD na instalação nova (issue #277): a bandeja
 * é um dos caminhos de volta, e sem ela o app não troca sozinho.
 */
@Composable
internal fun ApplicationScope.AppTrayHost(
    viewModels: AppViewModels,
    shell: AppShellState,
    modal: AppModalState,
    iconImage: Painter?,
    breadcrumbs: com.usagemonitor.domain.repository.BreadcrumbRecorder,
    onRestoreMainWindow: () -> Unit,
    onQuit: () -> Unit
) {
    val language = shell.language
    val trayState = rememberTrayState()
    val worstRisk by viewModels.usageAlert.worstRisk.collectAsState()
    val trayIcon = remember(iconImage, worstRisk) { TrayRiskIconPainter(iconImage, worstRisk) }
    // O tooltip resume as contas — "Anthropic — Padrão 87% · Codex 0%" —, como o
    // do Codenotch: mesmas contas e mesma ordem da HUD (`buildHudAccounts`).
    val quotaRisks by viewModels.usageAlert.quotaRisks.collectAsState()
    val switchToHudByDefault = hudDefaultShouldSwitch(shell.hudDefaultPending, quotaRisks.isNotEmpty(), modal.anyOpen)
    LaunchedEffect(switchToHudByDefault) {
        if (switchToHudByDefault) {
            shell.changeHudMode(true)
            val notice = hudDefaultNotice(language)
            trayState.sendNotification(Notification(notice.first, notice.second, Notification.Type.Info))
        }
    }
    val tooltip = hudTraySummary(
        appName = "Usage Monitor",
        accounts = buildHudAccounts(quotaRisks, shell.cardOrder, language, Clock.System.now())
    )
    val pt = language == AppLanguage.PT

    Tray(
        icon = trayIcon,
        state = trayState,
        tooltip = tooltip,
        onAction = onRestoreMainWindow,
        menu = {
            Item(text = if (pt) "Abrir" else "Open", onClick = onRestoreMainWindow)
            Item(text = if (pt) "Atualizar agora" else "Refresh now", onClick = { viewModels.dashboard.refresh() })
            // Configurações e Ajuda entram por causa das molduras reduzidas: com
            // o rodapé escondido, a bandeja e o teclado são os únicos caminhos.
            Item(
                text = if (pt) "Configurações" else "Settings",
                onClick = {
                    breadcrumbs.recordScreenOpened("Configurações (bandeja)")
                    modal.openSettings()
                }
            )
            Item(
                text = if (pt) "Ajuda" else "Help",
                onClick = {
                    breadcrumbs.recordScreenOpened("Ajuda (bandeja)")
                    modal.isHelpOpen = true
                }
            )
            Item(
                text = when {
                    shell.cardsOnlyMode -> if (pt) "Sair do modo somente cards" else "Exit cards only mode"
                    else -> if (pt) "Somente os cards" else "Cards only"
                },
                onClick = { shell.changeCardsOnlyMode(!shell.cardsOnlyMode) }
            )
            // Mesmo padrão (issue #164): com a janela reduzida à barra, a bandeja
            // continua sendo um caminho de volta.
            Item(
                text = when {
                    shell.hudMode -> if (pt) "Sair da barra HUD" else "Exit HUD strip"
                    else -> if (pt) "Barra HUD" else "HUD strip"
                },
                onClick = { shell.changeHudMode(!shell.hudMode) }
            )
            Separator()
            Item(text = if (pt) "Sair" else "Quit", onClick = onQuit)
        }
    )

    // A língua entra na chave: trocar o idioma tem de recomeçar a coleta com o
    // valor novo, senão a notificação seguinte sairia no idioma anterior.
    LaunchedEffect(viewModels.usageAlert, trayState, language) {
        viewModels.usageAlert.alerts.collect { alert ->
            val message = usageAlertMessage(alert, language)
            trayState.sendNotification(
                Notification(title = message.title, message = message.body, type = Notification.Type.Warning)
            )
        }
    }
}

/**
 * O aviso da troca para a HUD (issue #277): o que aconteceu e os três caminhos
 * de volta que existem mesmo com a janela escondida. Uma vez só — a troca não se
 * repete, porque a pendência é apagada nela.
 */
internal fun hudDefaultNotice(language: AppLanguage): Pair<String, String> {
    return if (language == AppLanguage.PT) {
        "Usage Monitor agora na barra HUD" to
            "Para voltar à janela padrão: Ctrl+Shift+H, o menu da bandeja ou a engrenagem na ponta do notch."
    } else {
        "Usage Monitor is now on the HUD strip" to
            "To go back to the standard window: Ctrl+Shift+H, the tray menu or the gear at the end of the notch."
    }
}
