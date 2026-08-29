package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
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
const val API_SELECTOR_SWITCH_TEST_TAG_PREFIX = "apiSelectorSwitch_"
const val API_SELECTOR_EDIT_KEY_TEST_TAG_PREFIX = "apiSelectorEditKey_"

fun apiSelectorRowTestTag(api: ApiSource): String = "$API_SELECTOR_ROW_TEST_TAG_PREFIX${api.name}"

/**
 * Marca do interruptor da linha, separada da marca da linha.
 *
 * A linha deixou de ser o alvo do clique de ligar/desligar (ver
 * [ApiCheckboxRow]), então quem quer alternar a fonte mira aqui. A marca da
 * linha continua existindo: é ela que localiza a linha inteira para rolar até
 * ela e para afirmar o que está escrito.
 */
fun apiSelectorSwitchTestTag(api: ApiSource): String =
    "$API_SELECTOR_SWITCH_TEST_TAG_PREFIX${api.name}"

/**
 * Marca do lápis que gerencia a chave da fonte.
 *
 * O `contentDescription` continua sendo o caminho do leitor de tela e é ele que
 * a suíte de acessibilidade usa; a marca existe para o teste não depender do
 * idioma em vigor, como toda ação traduzida deste app.
 */
fun apiSelectorEditKeyTestTag(api: ApiSource): String =
    "$API_SELECTOR_EDIT_KEY_TEST_TAG_PREFIX${api.name}"

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
 * @param enabledApis        Conjunto de APIs atualmente ativas
 * @param editableApiKeys    Fontes cuja chave pode ser gerenciada pelo lápis.
 *                           Conjunto, e não um booleano no callback, pelo mesmo
 *                           desenho de `configuredApiKeys`: quem sabe quais
 *                           fontes dependem de chave é a tela, não esta lista.
 * @param onToggle           Callback ao alternar: recebe a API e o novo estado
 * @param onEditApiKey       Callback do lápis. Só é fiado nas fontes de
 *                           [editableApiKeys]; nas outras a linha não desenha
 *                           ícone nenhum.
 */
@Composable
fun ApiSelector(
    enabledApis: Set<ApiSource>,
    configuredApiKeys: Set<ApiSource> = emptySet(),
    editableApiKeys: Set<ApiSource> = emptySet(),
    language: AppLanguage,
    onToggle: (ApiSource, Boolean) -> Unit,
    onEditApiKey: (ApiSource) -> Unit = {},
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
                onEditApiKey = if (api in editableApiKeys) {
                    { onEditApiKey(api) }
                } else {
                    null
                },
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
 * O `toggleable` **não fica na linha**, e sim no interruptor. `toggleable` traz
 * `mergeDescendants = true`: com ele na linha inteira, o `contentDescription`
 * de qualquer botão de ícone colocado ali seria mesclado no nó do pai, e
 * `performClick()` sobre ele alternaria o interruptor em vez de disparar a ação
 * do botão — a armadilha 3 do `CLAUDE.md` na versão de botão de ícone. É por
 * isso que `AnthropicProfileRow` já faz assim no mesmo arquivo das
 * Configurações, com switch e lápis convivendo na mesma linha.
 *
 * `assertIsOn`/`assertIsOff` continuam funcionando: `AppSwitch` publica
 * `ToggleableState` com `Role.Switch`, agora no nó do próprio interruptor
 * ([apiSelectorSwitchTestTag]). O realce de hover da linha não depende do
 * `toggleable` — ele vem do `hoverable` interno do [AppDataRow].
 */
@Composable
fun ApiCheckboxRow(
    api: ApiSource,
    language: AppLanguage = AppLanguage.PT,
    isChecked: Boolean,
    hasConfiguredApiKey: Boolean = false,
    onCheckedChange: (Boolean) -> Unit,
    /**
     * Ação do lápis. `null` — o default — **não desenha ícone nenhum**: fonte
     * sem chave local não tem o que gerenciar, e um lápis que abrisse um
     * diálogo vazio seria pior que ícone nenhum.
     */
    onEditApiKey: (() -> Unit)? = null,
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
        modifier = modifier.testTag(apiSelectorRowTestTag(api)),
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
        // O lápis vem **antes** do interruptor, e não depois: assim o
        // interruptor continua sendo o último elemento de todas as sete linhas
        // e fica no mesmo x, com ou sem ícone. É também a ordem que o protótipo
        // desenha na aba Contas, a outra linha do app com switch e lápis.
        if (onEditApiKey != null) {
            AppIconButton(
                contentDescription = if (language == AppLanguage.PT) {
                    "Gerenciar chave"
                } else {
                    "Manage key"
                },
                onClick = onEditApiKey,
                modifier = Modifier.testTag(apiSelectorEditKeyTestTag(api))
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        AppSwitch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(apiSelectorSwitchTestTag(api))
        )
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
