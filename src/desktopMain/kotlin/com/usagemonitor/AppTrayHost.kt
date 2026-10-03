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
import com.usagemonitor.presentation.ui.hudTraySummary
import com.usagemonitor.presentation.ui.trayUsageRingFraction
import com.usagemonitor.presentation.ui.usageAlertMessage
import kotlin.time.Clock
import androidx.compose.ui.input.key.type
import com.russhwolf.settings.Settings
import com.usagemonitor.domain.repository.BreadcrumbRecorder

/**
 * O ícone da bandeja: ponto de risco, resumo das contas no tooltip, menu com os
 * atalhos que a barra HUD não tem espaço para mostrar e as notificações de alerta.
 */
@Composable
internal fun ApplicationScope.AppTrayHost(
    viewModels: AppViewModels,
    shell: AppShellState,
    modal: AppModalState,
    iconImage: Painter?,
    breadcrumbs: com.usagemonitor.domain.repository.BreadcrumbRecorder,
    onFocusHud: () -> Unit,
    onQuit: () -> Unit
) {
    val language = shell.language
    val trayState = rememberTrayState()
    val worstRisk by viewModels.usageAlert.worstRisk.collectAsState()
    // O tooltip resume as contas — "Anthropic — Padrão 87% · Codex 0%" —, como o
    // do Codenotch: mesmas contas e mesma ordem da HUD (`buildHudAccounts`).
    val quotaRisks by viewModels.usageAlert.quotaRisks.collectAsState()
    // Anel de uso (issue #328): o painter só muda quando o percentual muda, e
    // não a cada recomposição — o `Tray` reconstrói a imagem AWT a cada troca.
    val ringFraction = if (shell.trayUsageRing) trayUsageRingFraction(quotaRisks, Clock.System.now()) else null
    val trayIcon = remember(iconImage, worstRisk, ringFraction) { TrayRiskIconPainter(iconImage, worstRisk, ringFraction) }
    val tooltip = hudTraySummary(
        appName = "Usage Monitor",
        accounts = buildHudAccounts(quotaRisks, shell.cardOrder, language, Clock.System.now())
    )
    val pt = language == AppLanguage.PT

    Tray(
        icon = trayIcon,
        state = trayState,
        tooltip = tooltip,
        onAction = onFocusHud,
        menu = {
            Item(text = if (pt) "Abrir" else "Open", onClick = onFocusHud)
            Item(text = if (pt) "Atualizar agora" else "Refresh now", onClick = { viewModels.dashboard.refresh() })
            // Configurações e Ajuda também estão no balão da engrenagem; aqui
            // valem para a barra HUD coberta ou fora da tela.
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
