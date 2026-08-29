package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.displayName
import com.usagemonitor.domain.entity.statusBadgeLabel
import com.usagemonitor.domain.entity.statusSupportingText
import com.usagemonitor.presentation.ui.theme.AppAccents
import com.usagemonitor.presentation.ui.theme.AppShapes
import com.usagemonitor.presentation.ui.theme.AppSpacing

const val API_SELECTOR_ROW_TEST_TAG_PREFIX = "apiSelectorRow_"

fun apiSelectorRowTestTag(api: ApiSource): String = "$API_SELECTOR_ROW_TEST_TAG_PREFIX${api.name}"

/**
 * As integrações, uma por linha, com o interruptor à direita.
 *
 * STATELESS: não armazena estado. Recebe `enabledApis` do ViewModel e emite
 * eventos via `onToggle`.
 *
 * Era uma `Row` de caixas de seleção lado a lado, e com seis fontes ela quebrava
 * em blocos de larguras diferentes conforme o nome de cada uma. Linha empilhada
 * é o que o protótipo desenha e é o que mantém marcador, nome e interruptor no
 * mesmo x em todas.
 *
 * @param enabledApis  Conjunto de APIs atualmente ativas
 * @param onToggle     Callback ao clicar: recebe a API e o novo estado
 */
@Composable
fun ApiSelector(
    enabledApis: Set<ApiSource>,
    configuredApiKeys: Set<ApiSource> = emptySet(),
    language: AppLanguage,
    onToggle: (ApiSource, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        ApiSource.entries.forEachIndexed { index, api ->
            ApiCheckboxRow(
                api = api,
                language = language,
                isChecked = api in enabledApis,
                hasConfiguredApiKey = api in configuredApiKeys,
                onCheckedChange = { checked -> onToggle(api, checked) },
                showDivider = index != ApiSource.entries.lastIndex
            )
        }
    }
}

/**
 * Linha de uma integração: marcador de fonte, nome, estado e interruptor.
 *
 * O nome continua `ApiCheckboxRow` porque é por ele que três suítes a encontram,
 * e renomear componente não é o que esta passada faz. O controle, esse, deixou
 * de ser caixa de seleção: a linha diz se a fonte está sendo monitorada — ligado
 * ou desligado —, que é o que o interruptor diz e o mesmo controle que as outras
 * opções das Configurações já usam.
 *
 * O `role` da semântica continua `Checkbox`, e não `Switch`: é ele que
 * `assertIsOn`/`assertIsOff` observam, e o que a linha faz não mudou.
 */
@Composable
fun ApiCheckboxRow(
    api: ApiSource,
    language: AppLanguage = AppLanguage.PT,
    isChecked: Boolean,
    hasConfiguredApiKey: Boolean = false,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = false
) {
    val badgeLabel = if (
        api == ApiSource.MINIMAX ||
        api == ApiSource.DEEPSEEK ||
        api == ApiSource.OPENCODE_GO
    ) {
        if (hasConfiguredApiKey) {
            if (language == AppLanguage.PT) "Chave configurada" else "Key configured"
        } else {
            if (language == AppLanguage.PT) "Chave necessária" else "Key required"
        }
    } else {
        api.statusBadgeLabel(language)
    }
    val supportingText = api.statusSupportingText(language)

    AppDataRow(
        modifier = modifier.toggleable(
            value = isChecked,
            role = Role.Checkbox,
            onValueChange = onCheckedChange
        ).testTag(apiSelectorRowTestTag(api)),
        showDivider = showDivider
    ) {
        // A identidade da fonte cabe no traço de 2dp, como no card do dashboard.
        AppSourceMarker(
            color = accentColorFor(source = api, accents = AppAccents.current),
            height = if (supportingText == null) 18.dp else 28.dp
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                Text(
                    text = apiLabel(api, language),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (badgeLabel != null) {
                    SourceStatusBadge(label = badgeLabel)
                }
            }
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        AppSwitch(checked = isChecked, onCheckedChange = onCheckedChange)
    }
}

/**
 * Selo de estado da fonte: retângulo de raio 6 com borda, como o do card.
 *
 * Era uma cápsula preenchida com `tertiaryContainer`, a única do app: o mesmo
 * selo aparece no cabeçalho do card do dashboard com superfície neutra e borda,
 * e dois desenhos para o mesmo dado obrigavam a reaprender a ler ao trocar de
 * janela.
 */
@Composable
private fun SourceStatusBadge(
    label: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .clip(AppShapes.small)
            .border(AppBorderWidth, MaterialTheme.colorScheme.outlineVariant, AppShapes.small)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

private fun apiLabel(api: ApiSource, language: AppLanguage): String {
    return api.displayName(language)
}
