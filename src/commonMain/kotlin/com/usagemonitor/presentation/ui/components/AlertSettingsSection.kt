package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.theme.AppSpacing
import com.usagemonitor.domain.entity.DEFAULT_QUOTA_ALERT_PERCENTS
import com.usagemonitor.domain.entity.QuietHours
import com.usagemonitor.domain.entity.UsageAlertSettings

const val ALERT_SETTINGS_QUOTA_SWITCH_TEST_TAG = "alertSettingsQuotaSwitch"
const val ALERT_SETTINGS_SESSION_SWITCH_TEST_TAG = "alertSettingsSessionSwitch"
const val ALERT_SETTINGS_QUIET_SWITCH_TEST_TAG = "alertSettingsQuietSwitch"
const val ALERT_SETTINGS_BUDGET_FIELD_TEST_TAG = "alertSettingsBudgetField"

/** Limiares oferecidos na tela. Outros valores continuam válidos se já gravados. */
private val OFFERED_PERCENTS = listOf(50, 75, 90, 100)

private val DEFAULT_QUIET_HOURS = QuietHours(22, 8)

/**
 * Cartão de alertas das Configurações.
 *
 * Stateless como o resto do diálogo: recebe [settings] e devolve o valor inteiro
 * já alterado. Emitir campo a campo obrigaria quem chama a remontar o objeto e a
 * regra de "qual campo mudou" viveria em dois lugares.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlertSettingsSection(
    settings: UsageAlertSettings,
    language: AppLanguage,
    onSettingsChange: (UsageAlertSettings) -> Unit,
    /** Teto mensal já formatado; vazio significa desligado. */
    budgetText: String = "",
    onBudgetCommit: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isPt = language == AppLanguage.PT
    val selected = settings.effectiveQuotaPercents

    // Um limiar gravado fora da lista oferecida (registro editado à mão, versão
    // futura) continua visível e desmarcável. Sem esta união ele sumiria da tela
    // e seria apagado no primeiro clique em qualquer outro chip.
    val offered = (OFFERED_PERCENTS + selected).distinct().sorted()

    // Painel com cabeçalho, como as outras seções das Configurações. O título
    // era um `Text` solto no topo de uma coluna, e as três opções abaixo dele
    // eram linhas sem divisória sobre o mesmo fundo.
    AppDataSurfaceFlush(
        modifier = modifier.fillMaxWidth(),
        header = { AppSectionHeader(title = if (isPt) "Alertas" else "Alerts") }
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(AppSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
      ) {
        AlertToggleRow(
            label = if (isPt) "Avisar quando a quota cruzar um limiar" else "Warn when a quota crosses a threshold",
            checked = settings.quotaAlertsEnabled,
            testTag = ALERT_SETTINGS_QUOTA_SWITCH_TEST_TAG,
            onCheckedChange = { checked -> onSettingsChange(settings.copy(quotaAlertsEnabled = checked)) }
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            offered.forEach { percent ->
                val isSelected = percent in selected
                // Chip de alternância: os limiares são escolha múltipla — 75 e 90
                // podem estar ligados ao mesmo tempo —, então nem segmentado nem
                // aba servem aqui.
                AppToggleChip(
                    label = "$percent%",
                    selected = isSelected,
                    enabled = settings.quotaAlertsEnabled,
                    onClick = {
                        val updated = if (isSelected) selected - percent else selected + percent
                        onSettingsChange(settings.copy(quotaPercents = updated.sorted()))
                    }
                )
            }
        }

        if (settings.quotaAlertsEnabled && selected.isEmpty()) {
            Text(
                text = if (isPt) {
                    "Nenhum limiar escolhido: nenhum aviso de quota será emitido."
                } else {
                    "No threshold selected: no quota warning will be sent."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        AlertToggleRow(
            label = if (isPt) "Avisar quando uma sessão CLI saturar" else "Warn when a CLI session saturates",
            checked = settings.sessionAlertsEnabled,
            testTag = ALERT_SETTINGS_SESSION_SWITCH_TEST_TAG,
            onCheckedChange = { checked -> onSettingsChange(settings.copy(sessionAlertsEnabled = checked)) }
        )

        AlertToggleRow(
            label = if (isPt) "Silenciar num período do dia" else "Mute during a time range",
            checked = settings.quietHours != null,
            testTag = ALERT_SETTINGS_QUIET_SWITCH_TEST_TAG,
            onCheckedChange = { checked ->
                onSettingsChange(settings.copy(quietHours = if (checked) DEFAULT_QUIET_HOURS else null))
            }
        )

        val quietHours = settings.quietHours
        if (quietHours != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HourStepper(
                    label = if (isPt) "Das" else "From",
                    hour = quietHours.startHour,
                    onHourChange = { hour ->
                        onSettingsChange(settings.copy(quietHours = quietHours.copy(startHour = hour)))
                    }
                )
                HourStepper(
                    label = if (isPt) "Até" else "To",
                    hour = quietHours.endHour,
                    onHourChange = { hour ->
                        onSettingsChange(settings.copy(quietHours = quietHours.copy(endHour = hour)))
                    }
                )
            }
            Text(
                text = if (isPt) {
                    "No silêncio o aviso é adiado, não descartado: ele sai quando o período terminar."
                } else {
                    "While muted the warning is postponed, not dropped: it is sent once the range ends."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        DebouncedTextField(
            value = budgetText,
            label = if (isPt) "Teto mensal em USD (vazio desliga)" else "Monthly cap in USD (empty disables)",
            onCommit = onBudgetCommit,
            placeholder = "200",
            validate = { text -> budgetValidationError(text, isPt) },
            modifier = Modifier.fillMaxWidth().testTag(ALERT_SETTINGS_BUDGET_FIELD_TEST_TAG)
        )
        Text(
            text = if (isPt) {
                "O teto mede o custo estimado das sessões CLI, em USD. Os créditos de uso da Anthropic aparecem à parte, na moeda da conta."
            } else {
                "The cap measures the estimated CLI session cost, in USD. Anthropic usage credits are shown separately, in the account currency."
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (settings.quotaPercents != DEFAULT_QUOTA_ALERT_PERCENTS) {
            AppButton(
                label = if (isPt) "Voltar aos limiares padrão" else "Restore default thresholds",
                onClick = { onSettingsChange(settings.copy(quotaPercents = DEFAULT_QUOTA_ALERT_PERCENTS)) },
                tone = AppButtonTone.GHOST
            )
        }
      }
    }
}

@Composable
private fun AlertToggleRow(
    label: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    // Rótulo em mono e linha de dados, como as opções da aba Geral: o texto
    // estava em `bodySmall`, que é sans, e lia como frase e não como rótulo.
    AppDataRow(showDivider = false, horizontalPadding = 0.dp) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        AppSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

/** Hora inteira com passo circular: 23 + 1 volta a 0, como um relógio. */
@Composable
private fun HourStepper(
    label: String,
    hour: Int,
    onHourChange: (Int) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 6.dp)
        )
        AppButton(
            label = "−",
            onClick = { onHourChange(wrapHour(hour - 1)) },
            tone = AppButtonTone.GHOST
        )
        Text(
            text = "${hour.toString().padStart(2, '0')}h",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        AppButton(
            label = "+",
            onClick = { onHourChange(wrapHour(hour + 1)) },
            tone = AppButtonTone.GHOST
        )
    }
}

internal fun wrapHour(hour: Int): Int {
    return ((hour % 24) + 24) % 24
}

/**
 * Recusa texto que não é número, em vez de gravar zero em silêncio.
 *
 * Vírgula é aceita: em pt-BR é o separador decimal que a pessoa digita, e
 * rejeitá-la seria transformar um hábito em erro.
 */
internal fun budgetValidationError(text: String, isPt: Boolean): String? {
    val normalized = text.trim().replace(',', '.')
    if (normalized.isEmpty()) {
        return null
    }
    val value = normalized.toDoubleOrNull()
    if (value == null || value < 0.0) {
        return if (isPt) "Informe um valor em dólares, ex.: 200" else "Enter a dollar amount, e.g. 200"
    }
    return null
}
