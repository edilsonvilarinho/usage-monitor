package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.theme.AppSpacing

const val WEB_ACCESS_SECTION_TEST_TAG = "webAccessSection"
const val WEB_ACCESS_SWITCH_TEST_TAG = "webAccessSwitch"

/** Estado do servidor, já traduzido para a seção (#388). */
enum class WebAccessUiStatus { STOPPED, RUNNING, FAILED }

/**
 * O que a seção "Acesso pela rede local" mostra (#388, direção R3).
 *
 * [urls] já trazem o token: são o que o usuário copia para o celular. A seção
 * nunca mostra o token sozinho, só dentro do endereço que o usa.
 */
class WebAccessSectionModel(
    val enabled: Boolean,
    val portText: String,
    val status: WebAccessUiStatus,
    val urls: List<String>,
    val failureMessage: String?,
    val onEnabledChange: (Boolean) -> Unit,
    val onPortChange: (String) -> Unit,
    val onRegenerateToken: () -> Unit
)

/**
 * Seção dentro da aba Rede — e não aba própria: `SettingsTab` não ganha valor
 * novo (regra do projeto), e o acesso web é uma porta aberta na rede, vizinha do
 * proxy. Desligada por padrão; ligar mostra o aviso do firewall do Windows.
 */
@Composable
fun WebAccessSection(model: WebAccessSectionModel, language: AppLanguage, modifier: Modifier = Modifier) {
    val pt = language == AppLanguage.PT
    val clipboard = LocalClipboardManager.current
    AppDataSurfaceFlush(
        modifier = modifier.fillMaxWidth().testTag(WEB_ACCESS_SECTION_TEST_TAG),
        header = {
            AppSectionHeader(
                title = if (pt) "Acesso pela rede local" else "Local network access",
                trailing = {
                    AppSwitch(
                        checked = model.enabled,
                        onCheckedChange = model.onEnabledChange,
                        modifier = Modifier.testTag(WEB_ACCESS_SWITCH_TEST_TAG)
                    )
                }
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            Text(
                text = if (pt) {
                    "Mostra a HUD no navegador de outro aparelho desta rede (celular, outro computador). " +
                        "Qualquer pessoa da rede com o endereço completo vê os dados de uso — nunca prompt ou resposta. " +
                        "Ao ativar, o Windows pode pedir permissão no firewall."
                } else {
                    "Shows the HUD in the browser of another device on this network (phone, another computer). " +
                        "Anyone on the network with the full address sees usage data — never prompts or responses. " +
                        "When enabled, Windows may ask for firewall permission."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!model.enabled) {
                return@Column
            }
            when (model.status) {
                WebAccessUiStatus.RUNNING -> AppStatusIndicator(label = if (pt) "Ativo" else "Running", tone = AppTone.OK)
                WebAccessUiStatus.STOPPED -> AppStatusIndicator(label = if (pt) "Parado" else "Stopped", tone = AppTone.NEUTRAL)
                WebAccessUiStatus.FAILED -> AppStatusIndicator(
                    label = (if (pt) "Não iniciou: " else "Did not start: ") + model.failureMessage.orEmpty(),
                    tone = AppTone.CRITICAL
                )
            }
            if (model.status == WebAccessUiStatus.RUNNING && model.urls.isEmpty()) {
                Text(
                    text = if (pt) "Nenhum endereço de rede local encontrado: o computador não está numa rede privada." else "No local network address found: this computer is not on a private network.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            model.urls.forEach { url ->
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = url,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    AppButton(
                        label = if (pt) "Copiar" else "Copy",
                        onClick = { clipboard.setText(AnnotatedString(url)) },
                        tone = AppButtonTone.GHOST
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Text(if (pt) "Porta" else "Port", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                AppTextField(value = model.portText, onValueChange = model.onPortChange, modifier = Modifier.width(96.dp))
                AppButton(
                    label = if (pt) "Gerar novo endereço" else "Generate new address",
                    onClick = model.onRegenerateToken,
                    tone = AppButtonTone.GHOST
                )
            }
            Text(
                text = if (pt) "Gerar novo endereço invalida os links já copiados." else "Generating a new address invalidates links already copied.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
