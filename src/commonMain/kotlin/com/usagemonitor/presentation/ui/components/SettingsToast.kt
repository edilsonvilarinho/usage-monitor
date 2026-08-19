package com.usagemonitor.presentation.ui.components

import com.usagemonitor.domain.entity.AppLanguage

/** Campo das Configurações a que um aviso de gravação se refere. */
enum class SettingsField {
    THEME,
    LANGUAGE,
    AUTO_START,
    ALWAYS_ON_TOP,
    WINDOW_OPACITY,
    UI_SCALE,
    ALERTS,
    MONITORED_APIS,
    ANTHROPIC_PROFILES,
    ANTHROPIC_PROFILE_LABEL,
    TEAM_INTEGRATION,
    TEAM_SERVER,
    TEAM_KEY,
    TEAM_ALIAS,
    TEAM_ACCOUNTS,
    TEAM_ADMIN_TOKEN
}

/**
 * Aviso mostrado no rodapé do diálogo de Configurações.
 *
 * Mesma anatomia de [com.usagemonitor.presentation.viewmodel.DashboardToast]: o
 * tipo carrega só o fato, e a tradução acontece na borda da UI. O diálogo é uma
 * janela separada do dashboard, então ele precisa do próprio host — o
 * `SnackbarHost` da janela principal não desenha por cima dela.
 */
sealed interface SettingsToast {
    /** A alteração chegou ao disco. */
    data class Saved(val field: SettingsField) : SettingsToast

    /** A gravação falhou; o aviso não pode dizer que salvou. */
    data class SaveFailed(val field: SettingsField) : SettingsToast

    /** Apelido do time em branco: não grava e o campo volta ao valor anterior. */
    data object TeamAliasRequired : SettingsToast
}

/**
 * Par [SettingsToast] + identificador de emissão.
 *
 * O id é o que faz dois avisos iguais seguidos aparecerem duas vezes: sem ele o
 * `LaunchedEffect` da tela não reagiria ao segundo, por ser um valor igual.
 */
data class SettingsToastEvent(
    val id: Int,
    val toast: SettingsToast
)

/**
 * Frase completa por campo, em vez de "$campo salvo".
 *
 * Em português o particípio concorda com o gênero do campo ("Tema salvo" mas
 * "Opacidade salva"), e montar a frase por concatenação erraria metade dos
 * casos. A tabela é maior e não tem como sair errada.
 */
fun settingsToastMessage(toast: SettingsToast, language: AppLanguage): String {
    val isPt = language == AppLanguage.PT
    return when (toast) {
        is SettingsToast.Saved -> savedMessage(toast.field, isPt)
        is SettingsToast.SaveFailed -> if (isPt) {
            "Falha ao salvar: ${fieldLabel(toast.field, isPt = true)}"
        } else {
            "Could not save: ${fieldLabel(toast.field, isPt = false)}"
        }
        SettingsToast.TeamAliasRequired -> if (isPt) {
            "O apelido não pode ficar vazio."
        } else {
            "The alias cannot be empty."
        }
    }
}

private fun savedMessage(field: SettingsField, isPt: Boolean): String {
    if (!isPt) {
        return "${fieldLabel(field, isPt = false)} saved"
    }
    return when (field) {
        SettingsField.THEME -> "Tema salvo"
        SettingsField.LANGUAGE -> "Idioma salvo"
        SettingsField.AUTO_START -> "Inicialização com sistema salva"
        SettingsField.ALWAYS_ON_TOP -> "Preferência de janela salva"
        SettingsField.WINDOW_OPACITY -> "Opacidade salva"
        SettingsField.UI_SCALE -> "Tamanho da interface salvo"
        SettingsField.ALERTS -> "Preferências de alerta salvas"
        SettingsField.MONITORED_APIS -> "APIs monitoradas salvas"
        SettingsField.ANTHROPIC_PROFILES -> "Contas Anthropic salvas"
        SettingsField.ANTHROPIC_PROFILE_LABEL -> "Apelido da conta salvo"
        SettingsField.TEAM_INTEGRATION -> "Integração com time salva"
        SettingsField.TEAM_SERVER -> "Servidor do time salvo"
        SettingsField.TEAM_KEY -> "Chave do time salva"
        SettingsField.TEAM_ALIAS -> "Apelido salvo"
        SettingsField.TEAM_ACCOUNTS -> "Contas do time salvas"
        SettingsField.TEAM_ADMIN_TOKEN -> "Token de administração salvo"
    }
}

private fun fieldLabel(field: SettingsField, isPt: Boolean): String {
    return when (field) {
        SettingsField.THEME -> if (isPt) "tema" else "Theme"
        SettingsField.LANGUAGE -> if (isPt) "idioma" else "Language"
        SettingsField.AUTO_START -> if (isPt) "inicialização com sistema" else "System startup"
        SettingsField.ALWAYS_ON_TOP -> if (isPt) "manter sempre visível" else "Always on top"
        SettingsField.WINDOW_OPACITY -> if (isPt) "opacidade da janela" else "Window opacity"
        SettingsField.UI_SCALE -> if (isPt) "tamanho da interface" else "Interface size"
        SettingsField.ALERTS -> if (isPt) "preferências de alerta" else "Alert preferences"
        SettingsField.MONITORED_APIS -> if (isPt) "APIs monitoradas" else "Monitored APIs"
        SettingsField.ANTHROPIC_PROFILES -> if (isPt) "contas Anthropic" else "Anthropic accounts"
        SettingsField.ANTHROPIC_PROFILE_LABEL -> if (isPt) "apelido da conta" else "Account label"
        SettingsField.TEAM_INTEGRATION -> if (isPt) "integração com time" else "Team integration"
        SettingsField.TEAM_SERVER -> if (isPt) "servidor do time" else "Team server"
        SettingsField.TEAM_KEY -> if (isPt) "chave do time" else "Team key"
        SettingsField.TEAM_ALIAS -> if (isPt) "apelido" else "Alias"
        SettingsField.TEAM_ACCOUNTS -> if (isPt) "contas do time" else "Team accounts"
        SettingsField.TEAM_ADMIN_TOKEN ->
            if (isPt) "token de administração" else "Admin token"
    }
}
