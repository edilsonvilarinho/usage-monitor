package com.usagemonitor.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.components.AppButton
import com.usagemonitor.presentation.ui.components.AppMenu
import com.usagemonitor.presentation.ui.components.AppMenuOption

/** Ancora do gatilho de time no painel. Só existe com dois ou mais times ativos. */
const val TEAM_SELECTOR_TAG = "teamUsageSelector"

/** Um time ativo oferecido no seletor: o id vai para o domínio, o nome vai para o rótulo. */
data class TeamSelectorOption(
    val id: String,
    val name: String
)

/**
 * Seletor de time do painel.
 *
 * [options] chega na ordem que o chamador decidiu e é ela que o menu mostra.
 * [selectedId] é o time que o painel está mostrando; quando ele não está entre
 * as opções, o gatilho nomeia a primeira delas. [onSelect] recebe o id escolhido.
 */
data class TeamSelectorUi(
    val options: List<TeamSelectorOption>,
    val selectedId: String?,
    val onSelect: (String) -> Unit
)

/** Textos do seletor de time, PT/EN pelo labels object. */
internal object TeamSelectorLabels {
    fun trigger(language: AppLanguage, teamName: String): String {
        return localized(language, "Time: $teamName ▾", "Team: $teamName ▾")
    }
}

/**
 * Gatilho "Time: <nome> ▾" com o menu dos times ativos. Estado aberto e fechado
 * vem de fora, para o painel dono do estado poder fechar o menu ao escolher.
 */
@Composable
internal fun TeamSelectorTrigger(
    selector: TeamSelectorUi,
    language: AppLanguage,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedOption = selector.options.firstOrNull { option -> option.id == selector.selectedId }
        ?: selector.options.first()
    val selectedIndex = selector.options.indexOf(selectedOption)

    AppMenu(
        expanded = expanded,
        options = selector.options.map { option -> AppMenuOption(label = option.name) },
        selectedIndex = selectedIndex,
        onSelect = { index ->
            onExpandedChange(false)
            selector.onSelect(selector.options[index].id)
        },
        onDismissRequest = { onExpandedChange(false) },
        modifier = modifier
    ) {
        AppButton(
            label = TeamSelectorLabels.trigger(language, selectedOption.name),
            onClick = { onExpandedChange(!expanded) },
            modifier = Modifier.testTag(TEAM_SELECTOR_TAG)
        )
    }
}
