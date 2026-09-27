package com.usagemonitor

import androidx.compose.ui.input.key.key
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AnthropicProfileRef
import com.usagemonitor.domain.entity.DEFAULT_ANTHROPIC_PROFILE_ID
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.domain.entity.UsageAccountKey
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.ui.components.AnthropicProfileUiModel
import com.usagemonitor.presentation.ui.components.AnthropicProfileUiStatus
import com.usagemonitor.presentation.ui.theme.AccountAccent
import com.usagemonitor.presentation.ui.theme.AccountEmoji
import com.usagemonitor.presentation.viewmodel.TeamPulseTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import java.io.File
import javax.swing.JFileChooser

/*
 * As contas Anthropic monitoradas: resolução dos perfis, o modelo da tela de
 * Configurações, a identidade que vai para o time e os alvos dos cards.
 */

internal data class AnthropicProfileResolution(
    val enabledProfiles: List<AnthropicProfileRef>,
    val inspections: Map<String, AnthropicProfileInspection>,
    val duplicateProfileIds: Set<String>
)

internal fun resolveAnthropicProfiles(
    registry: AnthropicProfileRegistry,
    records: List<AnthropicProfileRecord>
): AnthropicProfileResolution {
    val inspections = records.associate { record -> record.id to registry.inspect(record) }
    val seenAccounts = linkedSetOf<UsageAccountKey>()
    val duplicateIds = linkedSetOf<String>()
    val enabledProfiles = mutableListOf<AnthropicProfileRef>()

    records.filter { it.enabled }.forEach { record ->
        val accountKey = inspections[record.id]?.accountContext?.key
        if (accountKey != null && !seenAccounts.add(accountKey)) {
            duplicateIds += record.id
        } else {
            enabledProfiles += record.ref
        }
    }

    return AnthropicProfileResolution(enabledProfiles, inspections, duplicateIds)
}

internal fun buildAnthropicProfileUiModels(
    records: List<AnthropicProfileRecord>,
    inspections: Map<String, AnthropicProfileInspection>,
    duplicateProfileIds: Set<String>
): List<AnthropicProfileUiModel> {
    return records.map { record ->
        val inspection = inspections[record.id]
        val duplicate = record.id in duplicateProfileIds
        val status = when {
            duplicate -> AnthropicProfileUiStatus.DUPLICATE
            inspection?.status == AnthropicProfileInspectionStatus.READY -> AnthropicProfileUiStatus.READY
            inspection?.status == AnthropicProfileInspectionStatus.INVALID -> AnthropicProfileUiStatus.INVALID
            else -> AnthropicProfileUiStatus.INCOMPLETE
        }
        AnthropicProfileUiModel(
            id = record.id,
            label = record.label,
            path = record.configDirectory,
            enabled = record.enabled,
            removable = record.id != DEFAULT_ANTHROPIC_PROFILE_ID,
            identityLabel = inspection?.accountContext?.displayLabel,
            status = status,
            detail = if (duplicate) "Já monitorada por outro perfil habilitado" else inspection?.detail,
            color = AccountAccent.fromStorage(record.color),
            emoji = AccountEmoji.fromStorage(record.emoji)
        )
    }
}

/** `profileId → cor` das contas que têm cor escolhida; as demais ficam no acento da fonte. */
internal fun accountColorsOf(records: List<AnthropicProfileRecord>): Map<String, AccountAccent> =
    records.mapNotNull { record -> AccountAccent.fromStorage(record.color)?.let { color -> record.id to color } }.toMap()

/** `profileId → emoji` das contas que têm emoji escolhido (issue #287). */
internal fun accountEmojisOf(records: List<AnthropicProfileRecord>): Map<String, AccountEmoji> =
    records.mapNotNull { record -> AccountEmoji.fromStorage(record.emoji)?.let { emoji -> record.id to emoji } }.toMap()

/**
 * Contas Anthropic candidatas ao envio, com o `accountUuid` de cada perfil.
 *
 * A identidade é lida do disco a cada chamada (`inspect` lê `.credentials.json` e
 * `.claude.json`) em vez de ser cacheada: o usuário pode trocar de conta no
 * Claude Code com o app aberto, e um `accountUuid` velho mandaria o consumo dele
 * para o time errado. A chamada roda no laço de envio, em `Dispatchers.IO`.
 *
 * Perfis sem identidade resolvida ficam de fora — sem `accountUuid` não há chave
 * de agrupamento.
 */
internal fun buildTeamSyncTargets(registry: AnthropicProfileRegistry): List<TeamSyncTarget> {
    return registry.profiles.value.mapNotNull { record ->
        val accountContext = registry.inspect(record).accountContext ?: return@mapNotNull null
        TeamSyncTarget(
            profileId = record.id,
            accountKey = accountContext.key.providerAccountId,
            accountEmail = accountContext.email,
            organizationUuid = accountContext.key.workspaceId,
            organizationName = accountContext.workspaceName
        )
    }
}

/**
 * Contas que o semáforo do botão de time deve consultar.
 *
 * Mesma resolução do envio ([buildTeamSyncTargets]) filtrada pela condição que já
 * decide se o botão aparece no card: integração ligada e perfil marcado. Sem o
 * filtro, o app consultaria o servidor por contas cujo botão nem existe.
 */
internal fun buildSessionPulseTargets(
    registry: AnthropicProfileRegistry,
    settings: TeamIntegrationSettings
): List<TeamPulseTarget> {
    if (!settings.isActive) {
        return emptyList()
    }

    return buildTeamSyncTargets(registry)
        .filter { target -> target.profileId in settings.participatingProfileIds }
        .map { target -> TeamPulseTarget(profileId = target.profileId, accountKey = target.accountKey) }
}

internal fun availableUsageTargets(records: List<AnthropicProfileRecord>): List<UsageTargetKey> {
    val targets = mutableListOf<UsageTargetKey>()
    ApiSource.entries.forEach { source ->
        if (source == ApiSource.ANTHROPIC) {
            records.forEach { record -> targets += UsageTargetKey(source, record.id) }
        } else {
            targets += UsageTargetKey.forSource(source)
        }
    }
    return targets
}

internal fun enabledUsageTargets(
    enabledSources: Set<ApiSource>,
    enabledProfiles: List<AnthropicProfileRef>
): Set<UsageTargetKey> {
    val targets = linkedSetOf<UsageTargetKey>()
    enabledSources.sortedBy { it.ordinal }.forEach { source ->
        if (source == ApiSource.ANTHROPIC) {
            enabledProfiles.forEach { profile -> targets += UsageTargetKey(source, profile.id) }
        } else {
            targets += UsageTargetKey.forSource(source)
        }
    }
    return targets
}

internal fun chooseAnthropicConfigDirectory(): File? {
    val chooser = JFileChooser()
    chooser.dialogTitle = "Selecionar diretório de configuração Anthropic"
    chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    chooser.isAcceptAllFileFilterUsed = false
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile
    } else {
        null
    }
}
