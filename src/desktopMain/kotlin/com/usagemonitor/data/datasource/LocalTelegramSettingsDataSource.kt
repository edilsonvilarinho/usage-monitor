package com.usagemonitor.data.datasource

import com.usagemonitor.domain.entity.redactedSecret
import com.usagemonitor.domain.entity.TELEGRAM_SUMMARY_HOURS
import com.usagemonitor.domain.entity.TelegramBotPreferences
import com.usagemonitor.domain.entity.TelegramBotSettings
import com.usagemonitor.domain.entity.TelegramChat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.SecureRandom

@Serializable
private data class TelegramChatDto(val id: Long, val name: String = "", val readOnly: Boolean = false)

@Serializable
private data class TelegramPanelMessageDto(val chatId: Long, val messageId: Long)

/** Opções da #408; cada campo com default, para o arquivo antigo continuar valendo. */
@Serializable
private data class TelegramPreferencesDto(
    val allowPcActions: Boolean = false,
    val showMachineName: Boolean = false,
    val showTeam: Boolean = false,
    val allowRemoteUpdate: Boolean = false,
    val announcedUpdateVersion: String? = null,
    val soundOnlyOnCritical: Boolean = true,
    val groupRepeatedAlerts: Boolean = true,
    val batchAlerts: Boolean = true,
    val compactPanel: Boolean = true,
    val sourceHealthAlerts: Boolean = true
)

@Serializable
private data class TelegramSettingsDto(
    val enabled: Boolean = false,
    val botToken: String = "",
    val authorizedChats: List<TelegramChatDto> = emptyList(),
    val pairingCode: String? = null,
    val pairingExpiresAtMillis: Long? = null,
    val allowSourceControl: Boolean = false,
    val dailySummaryHour: Int? = null,
    val lastSummaryDate: String? = null,
    val livePanelEnabled: Boolean = false,
    val panelMessages: List<TelegramPanelMessageDto> = emptyList(),
    val preferences: TelegramPreferencesDto = TelegramPreferencesDto()
) {
    /** Segredo nunca no `toString` (log, relatório de bug, mensagem de exceção). */
    override fun toString(): String =
        "TelegramSettingsDto(enabled=$enabled, botToken=${redactedSecret(botToken)}, chats=${authorizedChats.size}, " +
            "pairingCode=${redactedSecret(pairingCode)})"
}

/**
 * `~/.usage-monitor/telegram.json` (#387). Arquivo de segredo pelo mesmo motivo
 * do `team.json`: o token do bot dá controle sobre ele, e as preferências do app
 * vão em claro para o registro do Windows.
 */
internal class LocalTelegramSettingsDataSource(
    private val settingsFile: File = defaultSettingsFile()
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun load(): TelegramBotSettings {
        val dto = readSecretFile(settingsFile) { text -> json.decodeFromString(TelegramSettingsDto.serializer(), text) }
            ?: TelegramSettingsDto()
        return TelegramBotSettings(
            enabled = dto.enabled,
            botToken = dto.botToken,
            // Idioma fora do enum é arquivo editado à mão: vale como "o do app".
            authorizedChats = dto.authorizedChats.map { chat ->
                TelegramChat(chat.id, chat.name, chat.readOnly)
            },
            pairingCode = dto.pairingCode,
            pairingExpiresAtMillis = dto.pairingExpiresAtMillis,
            allowSourceControl = dto.allowSourceControl,
            // Hora fora das oferecidas é arquivo editado à mão: vale como desligado,
            // e o seletor das Configurações mostra isso em vez de uma hora falsa.
            dailySummaryHour = dto.dailySummaryHour?.takeIf { hour -> hour in TELEGRAM_SUMMARY_HOURS },
            lastSummaryDate = dto.lastSummaryDate,
            livePanelEnabled = dto.livePanelEnabled,
            panelMessages = dto.panelMessages.associate { panel -> panel.chatId to panel.messageId },
            preferences = TelegramBotPreferences(
                allowPcActions = dto.preferences.allowPcActions,
                showMachineName = dto.preferences.showMachineName,
                showTeam = dto.preferences.showTeam,
                allowRemoteUpdate = dto.preferences.allowRemoteUpdate,
                announcedUpdateVersion = dto.preferences.announcedUpdateVersion,
                soundOnlyOnCritical = dto.preferences.soundOnlyOnCritical,
                groupRepeatedAlerts = dto.preferences.groupRepeatedAlerts,
                batchAlerts = dto.preferences.batchAlerts,
                compactPanel = dto.preferences.compactPanel,
                sourceHealthAlerts = dto.preferences.sourceHealthAlerts
            )
        )
    }

    fun save(settings: TelegramBotSettings) {
        val dto = TelegramSettingsDto(
            enabled = settings.enabled,
            botToken = settings.botToken.trim(),
            authorizedChats = settings.authorizedChats.map { chat -> TelegramChatDto(chat.id, chat.name, chat.readOnly) },
            pairingCode = settings.pairingCode,
            pairingExpiresAtMillis = settings.pairingExpiresAtMillis,
            allowSourceControl = settings.allowSourceControl,
            dailySummaryHour = settings.dailySummaryHour,
            lastSummaryDate = settings.lastSummaryDate,
            livePanelEnabled = settings.livePanelEnabled,
            panelMessages = settings.panelMessages.map { (chatId, messageId) -> TelegramPanelMessageDto(chatId, messageId) },
            preferences = TelegramPreferencesDto(
                allowPcActions = settings.preferences.allowPcActions,
                showMachineName = settings.preferences.showMachineName,
                showTeam = settings.preferences.showTeam,
                allowRemoteUpdate = settings.preferences.allowRemoteUpdate,
                announcedUpdateVersion = settings.preferences.announcedUpdateVersion,
                soundOnlyOnCritical = settings.preferences.soundOnlyOnCritical,
                groupRepeatedAlerts = settings.preferences.groupRepeatedAlerts,
                batchAlerts = settings.preferences.batchAlerts,
                compactPanel = settings.preferences.compactPanel,
                sourceHealthAlerts = settings.preferences.sourceHealthAlerts
            )
        )
        writeSecretFile(settingsFile, json.encodeToString(TelegramSettingsDto.serializer(), dto))
    }

    companion object {
        fun defaultSettingsFile(): File {
            val home = System.getProperty("user.home") ?: throw IllegalStateException("Propriedade 'user.home' não disponível")
            return File(home, ".usage-monitor/telegram.json")
        }

        /** Seis caracteres sem os ambíguos (0/O, 1/I): digitável no celular sem erro. */
        fun newPairingCode(random: SecureRandom = SecureRandom()): String {
            val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
            return (1..6).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
        }
    }
}
