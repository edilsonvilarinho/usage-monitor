package com.usagemonitor.presentation.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.AppUpdatePlatform
import com.usagemonitor.domain.entity.AppUpdateReceipt
import com.usagemonitor.domain.repository.AppUpdateSupport

/**
 * Aba Sistema (issue #399, direção X1): o que o app faz fora da janela. Três
 * painéis nomeados — achar "atualização" é ler o título, não a lista inteira.
 */
@Composable
internal fun SystemSettingsTab(
    currentLanguage: AppLanguage,
    autoStartEnabled: Boolean,
    autoUpdateEnabled: Boolean,
    autoUpdateSupport: AppUpdateSupport,
    autoUpdatePlatform: AppUpdatePlatform?,
    lastUpdateReceipt: AppUpdateReceipt?,
    autoUpdateFeedOverride: String?,
    onAutoStartChange: (Boolean) -> Unit,
    onAutoUpdateChange: (Boolean) -> Unit,
    onReportBug: () -> Unit,
    trayUsageRing: Boolean,
    onTrayUsageRingChange: (Boolean) -> Unit,
    receiveBetaUpdates: Boolean,
    onReceiveBetaUpdatesChange: (Boolean) -> Unit
) {
    val isPt = currentLanguage == AppLanguage.PT

    // O que o app faz sem ninguém pedir, fora da barra HUD: abrir com o sistema
    // e desenhar na bandeja.
    AppDataSurfaceFlush(
        header = { AppSectionHeader(title = if (isPt) "Inicialização e bandeja" else "Startup and tray") }
    ) {
        AutoStartToggle(
            enabled = autoStartEnabled,
            language = currentLanguage,
            onToggle = onAutoStartChange
        )
        TrayUsageRingToggle(
            enabled = trayUsageRing,
            language = currentLanguage,
            onToggle = onTrayUsageRingChange,
            showDivider = false
        )
    }

    AppDataSurfaceFlush(
        header = { AppSectionHeader(title = if (isPt) "Atualizações" else "Updates") }
    ) {
        AutoUpdateToggle(
            enabled = autoUpdateEnabled,
            support = autoUpdateSupport,
            platform = autoUpdatePlatform,
            language = currentLanguage,
            lastReceipt = lastUpdateReceipt,
            feedUrlOverride = autoUpdateFeedOverride,
            onToggle = onAutoUpdateChange
        )
        // Logo abaixo: escolhe *quais* versões a linha de cima baixa.
        BetaUpdatesToggle(
            enabled = receiveBetaUpdates,
            language = currentLanguage,
            onToggle = onReceiveBetaUpdatesChange,
            showDivider = false
        )
    }

    // Seção própria: aquelas são interruptores de comportamento contínuo, e esta
    // é uma ação que o usuário dispara uma vez. A ação vai no `trailing` do
    // cabeçalho porque age sobre a seção inteira -- mesmo lugar do "Adicionar"
    // da aba Contas. `PRIMARY` porque é a única ação que a aba propõe.
    AppDataSurfaceFlush(
        header = {
            AppSectionHeader(
                title = if (isPt) "Diagnóstico" else "Diagnostics",
                trailing = {
                    AppButton(
                        label = if (isPt) "Reportar um bug" else "Report a bug",
                        tone = AppButtonTone.PRIMARY,
                        onClick = onReportBug,
                        modifier = Modifier.testTag(REPORT_BUG_BUTTON_TEST_TAG)
                    )
                }
            )
        }
    ) {
        AppDataRow(showDivider = false) {
            Text(
                text = if (isPt) {
                    "O app guarda uma trilha dos últimos passos e dos erros em " +
                        "~/.usage-monitor/diagnostics. Ao reportar, você revisa o pacote antes de " +
                        "publicá-lo: nada é enviado automaticamente."
                } else {
                    "The app keeps a trail of the last steps and errors in " +
                        "~/.usage-monitor/diagnostics. When reporting, you review the package " +
                        "before publishing it: nothing is sent automatically."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
