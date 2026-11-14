package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.presentation.ui.TeamConnectionPath
import com.usagemonitor.presentation.ui.theme.AppSpacing

/** Prefixo da tag de cada bloco do detalhe do time; o número vai no fim. */
const val TEAM_STEP_TAG_PREFIX = "teamIntegrationStep:"

/** Os três blocos do detalhe do time. Um aberto por vez. */
internal enum class TeamStep { CONNECTION, ACCOUNTS, ADMIN }

/**
 * Bloco que abre ao entrar no time: Conexão sem servidor ou com a última conexão falhando (o
 * caminho do erro fica à vista); senão Contas. Token de admin não move a abertura: o dono
 * admin veria sempre o bloco 3 e as contas ficariam escondidas.
 */
internal fun initialTeamStepOf(
    settings: TeamIntegrationSettings,
    connection: TeamConnectionFormState
): TeamStep = when {
    settings.serverUrl.isBlank() -> TeamStep.CONNECTION
    connection.status == TeamConnectionUiStatus.FAILED -> TeamStep.CONNECTION
    else -> TeamStep.ACCOUNTS
}

/**
 * Detalhe do time em três blocos numerados e recolhíveis: 1 Conexão, 2 Contas, 3 Administração.
 * O bloco aberto guarda o estado localmente, chaveado pelo id do time; trocar de time reabre
 * pelo padrão de [initialTeamStepOf].
 */
@Composable
internal fun TeamStepsPane(
    settings: TeamIntegrationSettings,
    teams: List<TeamIntegrationSettings>,
    language: AppLanguage,
    labels: TeamIntegrationLabels,
    profiles: List<AnthropicProfileUiModel>,
    connection: TeamConnectionFormState,
    teamConnections: Map<String, TeamConnectionFormState>,
    adminConnection: TeamConnectionFormState,
    syncFailureMessage: String?,
    rejectedProfiles: Map<String, String>,
    onRenameTeam: (String) -> Unit,
    onServerUrlChange: (String) -> Unit,
    onAliasChange: (String) -> Unit,
    onProfileParticipationChange: (String, Boolean) -> Unit,
    onAccountApiKeyChange: (String, String) -> Unit,
    onTestConnection: () -> Unit,
    onAdminTokenChange: (String) -> Unit,
    onValidateAdminToken: () -> Unit,
    onOpenKeysManager: () -> Unit,
    onExitAdminMode: () -> Unit
) {
    var openStep by remember(settings.id) {
        mutableStateOf<TeamStep?>(initialTeamStepOf(settings, connection))
    }
    val participating = profiles.count { profile -> profile.id in settings.participatingProfileIds }

    // O envio em background falhava calado; o aviso fica visível sem depender de bloco aberto.
    if (syncFailureMessage != null) {
        Text(
            text = labels.lastUploadFailed(syncFailureMessage),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.fillMaxWidth().testTag(TEAM_SYNC_STATUS_TEST_TAG)
        )
    }

    TeamStepBlock(
        number = 1,
        title = labels.connectionStep,
        summary = teamStatusOf(settings, teamConnections[settings.id], labels).first,
        tag = "${TEAM_STEP_TAG_PREFIX}1",
        expanded = openStep == TeamStep.CONNECTION,
        onToggle = { openStep = if (openStep == TeamStep.CONNECTION) null else TeamStep.CONNECTION }
    ) {
        TeamConnectionStep(
            settings = settings,
            language = language,
            labels = labels,
            connection = connection,
            onRenameTeam = onRenameTeam,
            onServerUrlChange = onServerUrlChange,
            onAliasChange = onAliasChange,
            onTestConnection = onTestConnection
        )
    }

    TeamStepBlock(
        number = 2,
        title = labels.accountsStep,
        summary = labels.accountsSummary(on = participating, total = profiles.size),
        tag = "${TEAM_STEP_TAG_PREFIX}2",
        expanded = openStep == TeamStep.ACCOUNTS,
        onToggle = { openStep = if (openStep == TeamStep.ACCOUNTS) null else TeamStep.ACCOUNTS }
    ) {
        TeamAccountsStep(
            settings = settings,
            teams = teams,
            labels = labels,
            profiles = profiles,
            rejectedProfiles = rejectedProfiles,
            onProfileParticipationChange = onProfileParticipationChange,
            onAccountApiKeyChange = onAccountApiKeyChange
        )
    }

    TeamStepBlock(
        number = 3,
        title = labels.adminStep,
        summary = if (settings.adminToken.isNotBlank()) labels.adminModeOn else labels.adminOff,
        tag = "${TEAM_STEP_TAG_PREFIX}3",
        expanded = openStep == TeamStep.ADMIN,
        onToggle = { openStep = if (openStep == TeamStep.ADMIN) null else TeamStep.ADMIN }
    ) {
        TeamAdminStep(
            settings = settings,
            labels = labels,
            language = language,
            connection = adminConnection,
            onAdminTokenChange = onAdminTokenChange,
            onValidateAdminToken = onValidateAdminToken,
            onOpenKeysManager = onOpenKeysManager,
            onExitAdminMode = onExitAdminMode
        )
    }
}

/** Cabeçalho do bloco (número, título e resumo escrito) e, abaixo, o corpo quando aberto. */
@Composable
private fun TeamStepBlock(
    number: Int,
    title: String,
    summary: String,
    tag: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    AppDataRow(
        onClick = onToggle,
        showDivider = true,
        highlighted = expanded,
        horizontalPadding = 0.dp,
        modifier = Modifier.testTag(tag)
    ) {
        Text(
            text = "$number",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = if (expanded) "▾" else "▸",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    AppExpandable(expanded = expanded) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            content()
        }
    }
}

/** Bloco 1: servidor, apelido e o teste de conexão com o retorno. */
@Composable
private fun TeamConnectionStep(
    settings: TeamIntegrationSettings,
    language: AppLanguage,
    labels: TeamIntegrationLabels,
    connection: TeamConnectionFormState,
    onRenameTeam: (String) -> Unit,
    onServerUrlChange: (String) -> Unit,
    onAliasChange: (String) -> Unit,
    onTestConnection: () -> Unit
) {
    DebouncedTextField(
        value = settings.name,
        label = labels.teamNameLabel,
        placeholder = labels.teamNamePlaceholder,
        onCommit = onRenameTeam,
        validate = { text ->
            if (text.isBlank()) labels.teamNameEmpty else null
        },
        modifier = Modifier.testTag(TEAM_NAME_FIELD_TEST_TAG)
    )

    DebouncedTextField(
        value = settings.serverUrl,
        label = labels.serverLabel,
        placeholder = "https://usage.empresa.com",
        onCommit = onServerUrlChange
    )

    DebouncedTextField(
        value = settings.alias,
        label = labels.aliasLabel,
        placeholder = labels.aliasPlaceholder,
        onCommit = onAliasChange,
        // Apagar o apelido derrubaria `isConfigured` e pararia o laço de envio sem aviso.
        validate = { text ->
            if (text.isBlank() && settings.alias.isNotBlank()) labels.aliasEmpty else null
        },
        modifier = Modifier.testTag(TEAM_ALIAS_FIELD_TEST_TAG)
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Sem servidor, chave e apelido não há o que testar.
        AppButton(
            label = labels.testConnection,
            onClick = onTestConnection,
            enabled = settings.isConfigured && connection.status != TeamConnectionUiStatus.CHECKING,
            modifier = Modifier.testTag(TEAM_TEST_CONNECTION_TEST_TAG)
        )

        if (connection.status == TeamConnectionUiStatus.CHECKING) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        }

        // Ponto e palavra: a mensagem dizia o resultado só pela cor do texto.
        val statusMessage = connection.message
        if (statusMessage != null && connection.failure == null) {
            AppStatusIndicator(
                label = statusMessage,
                tone = connectionTone(connection.status),
                modifier = Modifier.weight(1f)
            )
        }
    }

    // O caminho mora fora da linha do botão: `AppStatusIndicator` é uma linha só (#494).
    val connectionFailure = connection.failure
    if (connectionFailure != null) {
        TeamConnectionPath(failure = connectionFailure, kind = connection.credentialKind, language = language)
    }
}

/** Bloco 2: uma linha por conta do time, com e-mail, status escrito, chave e interruptor. */
@Composable
private fun TeamAccountsStep(
    settings: TeamIntegrationSettings,
    teams: List<TeamIntegrationSettings>,
    labels: TeamIntegrationLabels,
    profiles: List<AnthropicProfileUiModel>,
    rejectedProfiles: Map<String, String>,
    onProfileParticipationChange: (String, Boolean) -> Unit,
    onAccountApiKeyChange: (String, String) -> Unit
) {
    if (profiles.isEmpty()) {
        Text(
            text = labels.noAccounts,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val missingKeys = settings.missingKeyProfileIds()
    profiles.forEach { profile ->
        key(profile.id) {
            TeamProfileCheckboxRow(
                profile = profile,
                checked = profile.id in settings.participatingProfileIds,
                onCheckedChange = { checked -> onProfileParticipationChange(profile.id, checked) },
                rejection = rejectedProfiles[profile.id],
                labels = labels,
                showDivider = profile !== profiles.last(),
                movedFromTeam = otherTeamNameFor(teams, settings.id, profile.id),
                accountKey = settings.apiKeyFor(profile.id).orEmpty(),
                keyMissing = profile.id in missingKeys,
                onAccountKeyChange = { value -> onAccountApiKeyChange(profile.id, value) }
            )
        }
    }
}

/**
 * Bloco 3: "Eu sou admin". Fica abaixo das contas e não depende delas: basta servidor e token.
 *
 * A marca de "validado" não é persistida: uma credencial que deixou de valer se denuncia
 * na primeira chamada, com a mensagem do servidor.
 */
@Composable
private fun TeamAdminStep(
    settings: TeamIntegrationSettings,
    labels: TeamIntegrationLabels,
    language: AppLanguage,
    connection: TeamConnectionFormState,
    onAdminTokenChange: (String) -> Unit,
    onValidateAdminToken: () -> Unit,
    onOpenKeysManager: () -> Unit,
    onExitAdminMode: () -> Unit
) {
    var adminExpanded by remember(settings.adminToken.isNotBlank()) {
        mutableStateOf(settings.adminToken.isNotBlank())
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = labels.adminSwitch,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        AppSwitch(
            checked = adminExpanded,
            onCheckedChange = { checked ->
                adminExpanded = checked
                if (!checked) {
                    onExitAdminMode()
                }
            },
            modifier = Modifier.testTag(TEAM_ADMIN_SWITCH_TEST_TAG)
        )
    }

    if (!adminExpanded) {
        return
    }

    Text(
        text = labels.adminDescription,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    DebouncedSecretField(
        value = settings.adminToken,
        label = labels.adminTokenLabel,
        revealLabel = labels.showToken,
        hideLabel = labels.hideToken,
        onCommit = onAdminTokenChange
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppButton(
            label = labels.validate,
            onClick = onValidateAdminToken,
            enabled = settings.isAdminMode && connection.status != TeamConnectionUiStatus.CHECKING,
            modifier = Modifier.testTag(TEAM_ADMIN_VALIDATE_TEST_TAG)
        )

        AppButton(
            label = labels.manageKeys,
            onClick = onOpenKeysManager,
            enabled = settings.isAdminMode,
            modifier = Modifier.testTag(TEAM_ADMIN_KEYS_TEST_TAG)
        )

        if (connection.status == TeamConnectionUiStatus.CHECKING) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        }
    }

    val adminFailure = connection.failure
    val adminMessage = connection.message
    if (adminFailure != null) {
        TeamConnectionPath(failure = adminFailure, kind = connection.credentialKind, language = language)
    } else if (adminMessage != null) {
        AppStatusIndicator(label = adminMessage, tone = connectionTone(connection.status))
    }

    if (settings.adminToken.isNotBlank()) {
        AppButton(
            label = labels.leaveAdmin,
            onClick = onExitAdminMode,
            modifier = Modifier.testTag(TEAM_ADMIN_EXIT_TEST_TAG),
            tone = AppButtonTone.GHOST
        )
    }
}

/**
 * Linha de uma conta. Desligada, não mostra campo de chave: a chave própria não tem para onde ir.
 * Ligada sem chave, o status escreve "falta a chave" e o campo fica vazio com o placeholder.
 */
@Composable
private fun TeamProfileCheckboxRow(
    profile: AnthropicProfileUiModel,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    labels: TeamIntegrationLabels,
    rejection: String?,
    showDivider: Boolean,
    /** Outro time que já tem esta conta; ligar aqui move a conta para o time aberto. */
    movedFromTeam: String?,
    /** Chave própria da conta no time, já com o fallback do legado aplicado. */
    accountKey: String,
    /** Conta ligada sem chave: o servidor recusa o envio dela. */
    keyMissing: Boolean,
    onAccountKeyChange: (String) -> Unit
) {
    val status = when {
        !checked -> labels.statusOff to AppTone.NEUTRAL
        keyMissing -> labels.statusKeyMissing to AppTone.WARNING
        else -> labels.statusOn to AppTone.OK
    }

    // Linha de dados como as opções da aba Sistema: identidade à esquerda, interruptor à direita.
    AppDataRow(showDivider = showDivider, horizontalPadding = 0.dp) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = profile.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            val identity = profile.identityLabel
            if (identity != null) {
                Text(
                    text = identity,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AppStatusIndicator(
                label = status.first,
                tone = status.second,
                modifier = Modifier.padding(top = 4.dp).testTag("teamIntegrationAccountStatus:${profile.id}")
            )
            // Conta de outro time: o texto diz o que o interruptor faz antes de ser ligado.
            if (movedFromTeam != null && !checked) {
                Text(
                    text = labels.moveHint(movedFromTeam),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp).testTag("$TEAM_MOVE_HINT_TAG_PREFIX${profile.id}")
                )
            }
            // O motivo é o que o próprio servidor mandou: nomeia a chave e a conta.
            if (rejection != null) {
                AppStatusIndicator(
                    label = labels.rejected(rejection),
                    tone = AppTone.CRITICAL,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .testTag("$TEAM_PROFILE_REJECTION_TAG_PREFIX${profile.id}")
                )
            }
            if (checked) {
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    DebouncedSecretField(
                        value = accountKey,
                        label = labels.accountKeyLabel,
                        revealLabel = labels.showKey,
                        hideLabel = labels.hideKey,
                        onCommit = onAccountKeyChange,
                        placeholder = labels.accountKeyPlaceholder,
                        modifier = Modifier.testTag("$TEAM_ACCOUNT_KEY_TAG_PREFIX${profile.id}")
                    )
                }
            }
        }
        // Interruptor e não caixa de seleção: a linha diz se a conta participa do time.
        AppSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Nome do outro time que já tem a conta marcada; `null` quando nenhum tem. */
private fun otherTeamNameFor(
    teams: List<TeamIntegrationSettings>,
    selectedTeamId: String,
    profileId: String
): String? {
    return teams.firstOrNull { it.id != selectedTeamId && profileId in it.participatingProfileIds }?.name
}

/**
 * Tom do resultado de uma verificação de conexão.
 *
 * Enquanto checa não há veredito: neutro. Sem tentativa, também não �?? mas ali
 * não há mensagem, e o indicador nem chega a ser composto.
 */
private fun connectionTone(status: TeamConnectionUiStatus): AppTone {
    return when (status) {
        TeamConnectionUiStatus.OK -> AppTone.OK
        TeamConnectionUiStatus.FAILED -> AppTone.CRITICAL
        TeamConnectionUiStatus.CHECKING, TeamConnectionUiStatus.IDLE -> AppTone.NEUTRAL
    }
}
