package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.domain.entity.TeamBlockedAccount
import com.usagemonitor.domain.entity.TeamKeyAccount
import com.usagemonitor.domain.entity.TeamKeyEntry
import com.usagemonitor.presentation.ui.TEAM_KEYS_CREATE_BUTTON_TAG
import com.usagemonitor.presentation.ui.TEAM_KEYS_CREATE_FIELD_TAG
import com.usagemonitor.presentation.ui.TEAM_KEYS_ERROR_TAG
import com.usagemonitor.presentation.ui.TEAM_KEYS_REMOVE_ACCOUNT_TAG_PREFIX
import com.usagemonitor.presentation.ui.TEAM_KEYS_REMOVE_CONFIRM_TAG
import com.usagemonitor.presentation.ui.TEAM_KEYS_ROW_TAG_PREFIX
import com.usagemonitor.presentation.ui.TEAM_KEYS_UNAUTHORIZED_TAG_PREFIX
import com.usagemonitor.presentation.ui.TEAM_KEYS_UNBLOCK_TAG_PREFIX
import com.usagemonitor.presentation.ui.TeamKeysAdminContent
import com.usagemonitor.presentation.ui.components.TEAM_ADMIN_EXIT_TEST_TAG
import com.usagemonitor.presentation.ui.components.TEAM_ADMIN_KEYS_TEST_TAG
import com.usagemonitor.presentation.ui.components.TEAM_ADMIN_SWITCH_TEST_TAG
import com.usagemonitor.presentation.ui.components.AnthropicProfileUiModel
import com.usagemonitor.presentation.ui.components.AnthropicProfileUiStatus
import com.usagemonitor.presentation.ui.components.TEAM_ADMIN_VALIDATE_TEST_TAG
import com.usagemonitor.presentation.ui.components.TEAM_PROFILE_REJECTION_TAG_PREFIX
import com.usagemonitor.presentation.ui.components.TEAM_SYNC_STATUS_TEST_TAG
import com.usagemonitor.presentation.ui.components.TeamConnectionUiState
import com.usagemonitor.presentation.ui.components.TeamIntegrationSection
import com.usagemonitor.presentation.viewmodel.TeamKeysUiState
import com.usagemonitor.presentation.ui.TeamCredentialKind
import com.usagemonitor.presentation.ui.components.TeamConnectionUiStatus
import com.usagemonitor.domain.entity.TeamCertificateProblem
import com.usagemonitor.domain.entity.TeamConnectionFailure
import com.usagemonitor.domain.entity.TeamConnectionStage
import kotlin.test.Test
import kotlin.test.assertEquals

private const val ACCOUNT_UUID = "account-uuid-aaa"

@OptIn(ExperimentalTestApi::class)
class TeamAdminSectionTest {

    @Test
    fun `campos de admin so aparecem depois de ligar a chave`() = runDesktopComposeUiTest {
        renderSection(TeamIntegrationSettings(enabled = true, serverUrl = "https://time.local"))

        onAllNodesWithTag(TEAM_ADMIN_VALIDATE_TEST_TAG).assertCountEquals(0)

        onNodeWithTag(TEAM_ADMIN_SWITCH_TEST_TAG).performClick()

        onNodeWithTag(TEAM_ADMIN_VALIDATE_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun `validar e gerenciar exigem servidor e token, nao chave de time`() =
        runDesktopComposeUiTest {
            renderSection(
                TeamIntegrationSettings(
                    enabled = true,
                    serverUrl = "https://time.local",
                    adminToken = "token-de-admin",
                    // Sem chave, sem apelido e sem conta marcada de propósito:
                    // administrar não exige participar de time nenhum.
                    apiKey = "",
                    alias = ""
                )
            )

            onNodeWithTag(TEAM_ADMIN_VALIDATE_TEST_TAG).assertIsEnabled()
            onNodeWithTag(TEAM_ADMIN_KEYS_TEST_TAG).assertIsEnabled()
        }

    @Test
    fun `sem servidor os botoes de admin ficam desabilitados`() = runDesktopComposeUiTest {
        renderSection(
            TeamIntegrationSettings(enabled = true, serverUrl = "", adminToken = "token-de-admin")
        )

        onNodeWithTag(TEAM_ADMIN_VALIDATE_TEST_TAG).assertIsNotEnabled()
        onNodeWithTag(TEAM_ADMIN_KEYS_TEST_TAG).assertIsNotEnabled()
    }

    @Test
    fun `sair do modo admin emite o evento`() = runDesktopComposeUiTest {
        var exits = 0
        renderSection(
            settings = TeamIntegrationSettings(
                enabled = true,
                serverUrl = "https://time.local",
                adminToken = "token-de-admin"
            ),
            onExitAdminMode = { exits += 1 }
        )

        onNodeWithTag(TEAM_ADMIN_EXIT_TEST_TAG).performClick()

        assertEquals(1, exits)
    }

    @Test
    fun `falha do envio em background aparece na secao`() = runDesktopComposeUiTest {
        renderSection(
            settings = TeamIntegrationSettings(
                enabled = true,
                serverUrl = "https://time.local",
                apiKey = "chave",
                alias = "edilson",
                deviceId = "device-1"
            ),
            syncFailureMessage = "Chave de time nao autorizada para esta conta."
        )

        onNodeWithTag(TEAM_SYNC_STATUS_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun `teste de conexao classificado mostra o caminho completo`() = runDesktopComposeUiTest {
        renderSection(
            settings = TeamIntegrationSettings(enabled = true, serverUrl = "https://time.local", apiKey = "chave"),
            connection = TeamConnectionUiState(
                status = TeamConnectionUiStatus.FAILED,
                message = "(certificate_expired) PKIX path validation failed:",
                failure = TeamConnectionFailure(stage = TeamConnectionStage.CERTIFICATE, certificateProblem = TeamCertificateProblem.EXPIRED)
            )
        )

        // Uma etapa por linha, sem cortar: o print do #494 parava em "failed:".
        onNodeWithText("Certificado").assertIsDisplayed()
        onNodeWithText("venceu").assertIsDisplayed()
        onNodeWithText("Chave").assertIsDisplayed()
        onNodeWithText("O certificado do servidor venceu: quem administra o servidor precisa renová-lo. A chave não chegou a ser testada.")
            .assertIsDisplayed()
    }

    @Test
    fun `validacao do token classificada nomeia o token admin`() = runDesktopComposeUiTest {
        renderSection(
            settings = TeamIntegrationSettings(enabled = true, serverUrl = "https://time.local", adminToken = "tok"),
            adminConnection = TeamConnectionUiState(
                status = TeamConnectionUiStatus.FAILED,
                message = "lab.example",
                failure = TeamConnectionFailure(stage = TeamConnectionStage.ADDRESS),
                credentialKind = TeamCredentialKind.ADMIN_TOKEN
            )
        )

        onNodeWithText("Token admin").assertIsDisplayed()
        onNodeWithText("o nome não resolve").assertIsDisplayed()
    }

    @Test
    fun `sem falha nao ha aviso de envio`() = runDesktopComposeUiTest {
        renderSection(
            TeamIntegrationSettings(
                enabled = true,
                serverUrl = "https://time.local",
                apiKey = "chave",
                alias = "edilson",
                deviceId = "device-1"
            )
        )

        onAllNodesWithTag(TEAM_SYNC_STATUS_TEST_TAG).assertCountEquals(0)
    }

    // O aviso de recusa mora na linha da conta, e não junto do erro geral: o
    // conserto é desligar aquele interruptor, que está ali do lado.
    @Test
    fun `conta recusada pelo servidor e marcada na propria linha`() = runDesktopComposeUiTest {
        renderSection(
            settings = TeamIntegrationSettings(
                enabled = true,
                serverUrl = "https://time.local",
                apiKey = "chave-de-time-com-tamanho-suficiente",
                alias = "romero",
                deviceId = "device-1",
                participatingProfileIds = setOf("pessoal", "empresa")
            ),
            profiles = listOf(
                profileModel("pessoal", "Pessoal", "ronac2007@gmail.com"),
                profileModel("empresa", "Empresa", "helio.sales@empresa.com")
            ),
            rejectedProfiles = mapOf("pessoal" to "a conta ronac2007@gmail.com nao esta na relacao")
        )

        onNodeWithTag("${TEAM_PROFILE_REJECTION_TAG_PREFIX}pessoal").assertIsDisplayed()
        // A conta boa da mesma máquina não pode ser marcada junto.
        onAllNodesWithTag("${TEAM_PROFILE_REJECTION_TAG_PREFIX}empresa").assertCountEquals(0)
    }

    @Test
    fun `sem recusa nenhuma conta e marcada`() = runDesktopComposeUiTest {
        renderSection(
            settings = TeamIntegrationSettings(
                enabled = true,
                serverUrl = "https://time.local",
                apiKey = "chave-de-time-com-tamanho-suficiente",
                alias = "romero",
                deviceId = "device-1",
                participatingProfileIds = setOf("pessoal")
            ),
            profiles = listOf(profileModel("pessoal", "Pessoal", "ronac2007@gmail.com"))
        )

        onAllNodesWithTag("${TEAM_PROFILE_REJECTION_TAG_PREFIX}pessoal").assertCountEquals(0)
    }

    private fun profileModel(id: String, label: String, identity: String) =
        AnthropicProfileUiModel(
            id = id,
            label = label,
            path = "~/.claude-$id",
            enabled = true,
            removable = true,
            identityLabel = identity,
            status = AnthropicProfileUiStatus.READY
        )

    private fun ComposeUiTest.renderSection(
        settings: TeamIntegrationSettings,
        onExitAdminMode: () -> Unit = {},
        syncFailureMessage: String? = null,
        profiles: List<AnthropicProfileUiModel> = emptyList(),
        rejectedProfiles: Map<String, String> = emptyMap(),
        connection: TeamConnectionUiState = TeamConnectionUiState(),
        adminConnection: TeamConnectionUiState = TeamConnectionUiState()
    ) {
        setContent {
            ScreenTestTheme(isDark = true) {
                Box(modifier = Modifier.width(620.dp).height(900.dp)) {
                    TeamIntegrationSection(
                        settings = settings,
                        language = AppLanguage.PT,
                        profiles = profiles,
                        connection = connection,
                        onEnabledChange = {},
                        onServerUrlChange = {},
                        onApiKeyChange = {},
                        onAliasChange = {},
                        onProfileParticipationChange = { _, _ -> },
                        onTestConnection = {},
                        syncFailureMessage = syncFailureMessage,
                        rejectedProfiles = rejectedProfiles,
                        adminConnection = adminConnection,
                        onExitAdminMode = onExitAdminMode
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTestApi::class)
class TeamKeysAdminScreenTest {

    @Test
    fun `lista mostra o rotulo, o prefixo e a conta vinculada`() = runDesktopComposeUiTest {
        renderKeys(
            TeamKeysUiState.Success(
                keys = listOf(
                    entry(
                        id = "key-1",
                        label = "fulano@empresa.com",
                        accounts = listOf("account-uuid-aaa")
                    )
                )
            )
        )

        onNodeWithTag("${TEAM_KEYS_ROW_TAG_PREFIX}key-1").assertIsDisplayed()
        onNodeWithText("fulano@empresa.com").assertIsDisplayed()
        onNodeWithText("account-uuid-aaa").assertIsDisplayed()
    }

    @Test
    fun `chave nasce oculta e o botao revela`() = runDesktopComposeUiTest {
        renderKeys(TeamKeysUiState.Success(keys = listOf(entry("key-1", "fulano@empresa.com"))))

        onAllNodesWithText("raw-key-secreta").assertCountEquals(0)

        onNodeWithText("Mostrar").performClick()

        onNodeWithText("raw-key-secreta").assertIsDisplayed()
    }

    @Test
    fun `chave sem conta diz que o vinculo nasce no primeiro envio`() = runDesktopComposeUiTest {
        renderKeys(TeamKeysUiState.Success(keys = listOf(entry("key-1", "fulano@empresa.com"))))

        onNodeWithText(
            "Sem conta vinculada — o vínculo nasce no primeiro envio."
        ).assertIsDisplayed()
    }

    @Test
    fun `emitir exige rotulo`() = runDesktopComposeUiTest {
        val created = mutableListOf<Pair<String, Int>>()
        renderKeys(TeamKeysUiState.Success(), onCreate = { label, max -> created += label to max })

        onNodeWithTag(TEAM_KEYS_CREATE_BUTTON_TAG).assertIsNotEnabled()

        onNodeWithTag(TEAM_KEYS_CREATE_FIELD_TAG).performTextInput("sicrano@empresa.com")
        onNodeWithTag(TEAM_KEYS_CREATE_BUTTON_TAG).performClick()

        assertEquals(listOf("sicrano@empresa.com" to 1), created)
    }

    @Test
    fun `erro de acao aparece sem apagar a lista`() = runDesktopComposeUiTest {
        renderKeys(
            TeamKeysUiState.Success(
                keys = listOf(entry("key-1", "fulano@empresa.com")),
                actionError = "limite excedido"
            )
        )

        onNodeWithTag(TEAM_KEYS_ERROR_TAG).assertIsDisplayed()
        onNodeWithTag("${TEAM_KEYS_ROW_TAG_PREFIX}key-1").assertIsDisplayed()
    }

    @Test
    fun `erro classificado da carga mostra onde parou e tenta de novo`() = runDesktopComposeUiTest {
        var retries = 0
        renderKeys(
            TeamKeysUiState.Error(
                message = "(certificate_expired) PKIX",
                failure = TeamConnectionFailure(stage = TeamConnectionStage.CERTIFICATE, certificateProblem = TeamCertificateProblem.EXPIRED)
            ),
            onRetry = { retries += 1 }
        )

        onNodeWithText("Parou em Certificado — venceu").assertIsDisplayed()
        onNodeWithText("– Token admin").assertIsDisplayed()
        onNodeWithText("Tentar de novo").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun `erro classificado de acao diz onde parou sem apagar a lista`() = runDesktopComposeUiTest {
        renderKeys(
            TeamKeysUiState.Success(
                keys = listOf(entry("key-1", "fulano@empresa.com")),
                actionError = "Connection refused",
                actionFailure = TeamConnectionFailure(stage = TeamConnectionStage.CONNECTION)
            )
        )

        onNodeWithText("Parou em Conexão — nada responde").assertIsDisplayed()
        onNodeWithTag("${TEAM_KEYS_ROW_TAG_PREFIX}key-1").assertIsDisplayed()
    }

    @Test
    fun `conta vinculada mostra o e-mail junto do uuid`() = runDesktopComposeUiTest {
        renderKeys(
            TeamKeysUiState.Success(
                keys = listOf(
                    entry(
                        id = "key-1",
                        label = "fulano@empresa.com",
                        accounts = listOf(ACCOUNT_UUID),
                        details = listOf(
                            TeamKeyAccount(
                                accountKey = ACCOUNT_UUID,
                                accountEmail = "fulano@empresa.com"
                            )
                        )
                    )
                )
            )
        )

        // Duas ocorrências: o rótulo da chave e o e-mail da conta, que numa
        // instalação coerente são o mesmo texto — e é justamente a segunda que
        // faltava. Era o uuid sozinho, e a conta pessoal que entrou no time
        // ficava indistinguível das legítimas.
        onAllNodesWithText("fulano@empresa.com").assertCountEquals(2)
        onNodeWithText(ACCOUNT_UUID).assertIsDisplayed()
    }

    @Test
    fun `conta fora do rotulo e marcada com ponto e palavra`() = runDesktopComposeUiTest {
        renderKeys(
            TeamKeysUiState.Success(
                keys = listOf(
                    entry(
                        id = "key-1",
                        label = "fulano@empresa.com",
                        accounts = listOf(ACCOUNT_UUID),
                        details = listOf(
                            TeamKeyAccount(
                                accountKey = ACCOUNT_UUID,
                                accountEmail = "pessoal@gmail.com",
                                authorized = false
                            )
                        )
                    )
                )
            )
        )

        onNodeWithTag("$TEAM_KEYS_UNAUTHORIZED_TAG_PREFIX$ACCOUNT_UUID").assertIsDisplayed()
    }

    @Test
    fun `remover a conta do time exige confirmacao`() = runDesktopComposeUiTest {
        val removed = mutableListOf<String>()
        renderKeys(
            TeamKeysUiState.Success(
                keys = listOf(
                    entry(
                        id = "key-1",
                        label = "fulano@empresa.com",
                        accounts = listOf(ACCOUNT_UUID),
                        details = listOf(TeamKeyAccount(accountKey = ACCOUNT_UUID))
                    )
                )
            ),
            onRemoveAccount = { accountKey -> removed += accountKey }
        )

        onNodeWithTag("$TEAM_KEYS_REMOVE_ACCOUNT_TAG_PREFIX$ACCOUNT_UUID").performClick()

        // Só o clique não apaga nada: a ação é irreversível.
        assertEquals(emptyList(), removed)

        onNodeWithTag(TEAM_KEYS_REMOVE_CONFIRM_TAG).performClick()

        assertEquals(listOf(ACCOUNT_UUID), removed)
    }

    @Test
    fun `contas fora do time aparecem em secao propria e voltam pelo botao`() =
        runDesktopComposeUiTest {
            val unblocked = mutableListOf<String>()
            renderKeys(
                TeamKeysUiState.Success(
                    keys = listOf(entry("key-1", "fulano@empresa.com")),
                    blockedAccounts = listOf(
                        TeamBlockedAccount(
                            accountKey = ACCOUNT_UUID,
                            accountEmail = "pessoal@gmail.com"
                        )
                    )
                ),
                onUnblockAccount = { accountKey -> unblocked += accountKey }
            )

            onNodeWithText("Contas fora do time").assertIsDisplayed()

            onNodeWithTag("$TEAM_KEYS_UNBLOCK_TAG_PREFIX$ACCOUNT_UUID").performClick()

            assertEquals(listOf(ACCOUNT_UUID), unblocked)
        }

    @Test
    fun `renomear abre o campo, salva o rotulo novo e cancelar descarta`() = runDesktopComposeUiTest {
        val renamed = mutableListOf<Pair<String, String>>()
        renderKeys(
            TeamKeysUiState.Success(keys = listOf(entry("key-1", "fulano@empresa.com"))),
            onRename = { id, label -> renamed += id to label }
        )

        onNodeWithText("Renomear").performClick()
        onNodeWithText("Cancelar").performClick()
        onNodeWithText("Renomear").assertIsDisplayed()

        onNodeWithText("Renomear").performClick()
        // O campo de renomear nasce com o rótulo atual; o de emitir nasce vazio.
        onNode(hasSetTextAction() and hasText("fulano@empresa.com")).performTextReplacement("ciclano@empresa.com")
        onNodeWithText("Salvar").performClick()

        assertEquals(listOf("key-1" to "ciclano@empresa.com"), renamed)
        onNodeWithText("Renomear").assertIsDisplayed()
    }

    @Test
    fun `chave revogada e marcada e nao revoga de novo`() = runDesktopComposeUiTest {
        val revoked = mutableListOf<String>()
        renderKeys(
            TeamKeysUiState.Success(
                keys = listOf(entry("key-1", "fulano@empresa.com").copy(revokedAt = kotlin.time.Instant.parse("2026-10-01T00:00:00Z")))
            ),
            onRevoke = { id -> revoked += id }
        )

        onNodeWithText("revogada").assertIsDisplayed()
        onNodeWithText("Revogar").assertIsNotEnabled()
        assertEquals(emptyList(), revoked)
    }

    @Test
    fun `falha de leitura mostra a causa e tenta de novo`() = runDesktopComposeUiTest {
        var retries = 0
        renderKeys(TeamKeysUiState.Error("servidor fora"), onRetry = { retries += 1 })

        onNodeWithTag(TEAM_KEYS_ERROR_TAG).assertTextEquals("servidor fora")
        onNodeWithText("Tentar de novo").performClick()

        assertEquals(1, retries)
    }

    @Test
    fun `rotulos em ingles`() = runDesktopComposeUiTest {
        renderKeys(
            TeamKeysUiState.Success(keys = listOf(entry("key-1", "fulano@empresa.com"))),
            language = AppLanguage.EN
        )

        onNodeWithText("Rename").assertIsDisplayed()
        onNodeWithText("Show").assertExists()
        onAllNodesWithText("Renomear").assertCountEquals(0)
    }

    private fun ComposeUiTest.renderKeys(
        state: TeamKeysUiState,
        onCreate: (String, Int) -> Unit = { _, _ -> },
        onRemoveAccount: (String) -> Unit = {},
        onUnblockAccount: (String) -> Unit = {},
        onRename: (String, String) -> Unit = { _, _ -> },
        onRevoke: (String) -> Unit = {},
        onRetry: () -> Unit = {},
        language: AppLanguage = AppLanguage.PT
    ) {
        setContent {
            ScreenTestTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    TeamKeysAdminContent(
                        state = state,
                        language = language,
                        onCreate = onCreate,
                        onRename = onRename,
                        onRevoke = onRevoke,
                        onRemoveAccount = onRemoveAccount,
                        onUnblockAccount = onUnblockAccount,
                        onRetry = onRetry
                    )
                }
            }
        }
    }

    private fun entry(
        id: String,
        label: String,
        accounts: List<String> = emptyList(),
        details: List<TeamKeyAccount> = emptyList()
    ): TeamKeyEntry {
        return TeamKeyEntry(
            id = id,
            label = label,
            key = "raw-key-secreta",
            keyPrefix = "raw-key-",
            maxAccounts = 1,
            accounts = accounts,
            accountDetails = details
        )
    }
}
