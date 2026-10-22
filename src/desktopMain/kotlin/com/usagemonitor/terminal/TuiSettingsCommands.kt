package com.usagemonitor.terminal

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.terminal.TerminalProfile
import com.usagemonitor.presentation.terminal.TuiNotice
import com.usagemonitor.presentation.terminal.TuiSettingAction

/**
 * Os ids das ações da aba 5 e o comando da 4.3 que cada um roda (#441, A08). A aba não grava
 * nada por conta própria: monta o mesmo comando que o terminal rodaria e mostra a saída dele.
 * O texto digitado num campo secreto entra como a entrada do comando, sem eco.
 */
internal object TuiSettingsCommands {
    private const val SET = "set:"
    private const val SOURCE = "source:"
    private const val KEY = "key:"
    private const val KEY_REMOVE = "key-remove:"
    private const val ACCOUNT = "account:"
    private const val ACCOUNT_LABEL = "account-label:"
    private const val CHAT_READ_ONLY = "telegram-readonly:"
    private const val CHAT_REMOVE = "telegram-remove:"
    const val ACCOUNTS_RESCAN = "accounts-rescan"
    const val TELEGRAM = "telegram"
    const val TELEGRAM_TOKEN = "telegram-token"
    const val TELEGRAM_PAIR = "telegram-pair"
    const val TELEGRAM_TEST = "telegram-test"
    const val TEAM_KEY = "team-key"
    const val TEAM_LEAVE = "team-leave"
    const val WEB = "web"
    const val WEB_TOKEN = "web-token"

    fun setId(key: String): String = SET + key
    fun set(key: String, value: String): TuiSettingAction = TuiSettingAction(setId(key), value)
    fun source(source: ApiSource, enabled: Boolean): TuiSettingAction = TuiSettingAction(SOURCE + source.name, enabled.toString())
    fun key(source: ApiSource): String = KEY + source.name
    fun keyRemove(source: ApiSource): String = KEY_REMOVE + source.name
    fun account(id: String): String = ACCOUNT + id
    fun accountLabel(id: String): String = ACCOUNT_LABEL + id
    fun chatReadOnly(chat: Long): String = CHAT_READ_ONLY + chat
    fun chatRemove(chat: Long): String = CHAT_REMOVE + chat

    /** O comando e, num campo secreto, o segredo digitado; `null` para id desconhecido. */
    fun commandOf(action: TuiSettingAction): Pair<ConfigCommand, String?>? {
        val id = action.id
        val value = action.value
        val on = value == "true"
        fun sourceOf(prefix: String): ApiSource? = ApiSource.entries.firstOrNull { source -> source.name == id.removePrefix(prefix) }
        return when {
            id.startsWith(SET) -> SettingCommand.Set(id.removePrefix(SET), value.orEmpty()) to null
            id.startsWith(SOURCE) -> sourceOf(SOURCE)?.let { source -> SourcesCommand.Switch(source, on) to null }
            id.startsWith(KEY_REMOVE) -> sourceOf(KEY_REMOVE)?.let { source -> KeysCommand.Remove(source) to null }
            id.startsWith(KEY) -> sourceOf(KEY)?.let { source -> KeysCommand.Set(source) to value }
            id.startsWith(ACCOUNT_LABEL) -> AccountsCommand.Rename(id.removePrefix(ACCOUNT_LABEL), value.orEmpty()) to null
            id.startsWith(ACCOUNT) -> AccountsCommand.Switch(id.removePrefix(ACCOUNT), on) to null
            id.startsWith(CHAT_READ_ONLY) -> TelegramCommand.ReadOnly(id.removePrefix(CHAT_READ_ONLY), on) to null
            id.startsWith(CHAT_REMOVE) -> TelegramCommand.Remove(id.removePrefix(CHAT_REMOVE)) to null
            id == ACCOUNTS_RESCAN -> AccountsCommand.Rescan to null
            id == TELEGRAM -> TelegramCommand.Switch(on) to null
            id == TELEGRAM_TOKEN -> TelegramCommand.Setup to value
            // Aqui o `pair` só abre o código: a conversa aparece na próxima leitura.
            id == TELEGRAM_PAIR -> TelegramCommand.Pair() to null
            id == TELEGRAM_TEST -> TelegramCommand.Test to null
            id == TEAM_KEY -> TeamCommand.Join(server = null, alias = null, accounts = null) to value
            id == TEAM_LEAVE -> TeamCommand.Leave to null
            id == WEB -> WebCommand.Switch(on) to null
            id == WEB_TOKEN -> WebCommand.RegenerateToken to null
            else -> null
        }
    }

    /**
     * Roda o comando de [action] pelo [runner] (feito com o segredo digitado como entrada) e
     * devolve a saída dele como aviso.
     */
    fun apply(
        action: TuiSettingAction,
        runner: (secret: String?) -> TerminalConfigRunner,
        languageOverride: AppLanguage?,
        profile: TerminalProfile
    ): TuiNotice? {
        val (command, secret) = commandOf(action) ?: return null
        val output = runner(secret?.takeIf { text -> text.isNotBlank() }).output(command, languageOverride, profile)
        return TuiNotice.of(output, profile.glyphs)
    }
}
