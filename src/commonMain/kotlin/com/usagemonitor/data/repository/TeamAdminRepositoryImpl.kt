package com.usagemonitor.data.repository

import com.usagemonitor.data.datasource.RemoteTeamDataSource
import com.usagemonitor.data.datasource.TeamCredential
import com.usagemonitor.data.datasource.TeamServerException
import com.usagemonitor.data.dto.CreateTeamKeyRequestDto
import com.usagemonitor.data.dto.UpdateTeamKeyRequestDto
import com.usagemonitor.data.mapper.toDomain
import com.usagemonitor.domain.entity.TeamAccountDeletion
import com.usagemonitor.domain.entity.TeamAccountUsage
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.domain.entity.TeamKeyEntry
import com.usagemonitor.domain.entity.TeamKeyVerification
import com.usagemonitor.domain.repository.TeamAdminRepository

private const val NOT_ADMIN_MESSAGE =
    "Modo administrador incompleto: informe servidor e token de administração nas Configurações."

private const val NOT_CONFIGURED_MESSAGE =
    "Integração com time incompleta: informe servidor e chave nas Configurações."

private const val OUTDATED_SERVER_MESSAGE =
    "Este servidor de time não sabe apagar contas. Atualize-o para a versão 0.5.0 ou mais nova."

private const val OUTDATED_SESSION_DELETE_SERVER_MESSAGE =
    "Este servidor de time não sabe apagar sessões. Atualize-o para a versão 0.8.0 ou mais nova."

/**
 * Administração do servidor de time.
 *
 * As credenciais chegam por [settingsProvider], e não pelo construtor, pelo
 * mesmo motivo de [TeamUsageRepositoryImpl]: o usuário troca servidor e token
 * com o app aberto e o repositório tem de passar a usar os valores novos sem ser
 * reconstruído.
 */
class TeamAdminRepositoryImpl(
    private val remoteDataSource: RemoteTeamDataSource,
    private val settingsProvider: () -> TeamIntegrationSettings
) : TeamAdminRepository {

    override suspend fun validateToken(): Result<Unit> {
        val settings = settingsProvider()
        if (!settings.isAdminMode) {
            return Result.failure(IllegalStateException(NOT_ADMIN_MESSAGE))
        }

        return runCatching {
            // Health antes do ping: separa "URL errada" de "token errado" na
            // mensagem, como o teste de conexão da chave de time já faz.
            remoteDataSource.checkHealth(settings.normalizedServerUrl)
            remoteDataSource.checkAdminToken(settings.normalizedServerUrl, settings.adminToken)
        }
    }

    override suspend fun listKeys(): Result<List<TeamKeyEntry>> {
        return withAdmin { settings ->
            remoteDataSource.listKeys(settings.normalizedServerUrl, settings.adminToken)
                .keys
                .map { key -> key.toDomain() }
        }
    }

    override suspend fun createKey(label: String, maxAccounts: Int): Result<TeamKeyEntry> {
        return withAdmin { settings ->
            remoteDataSource.createKey(
                baseUrl = settings.normalizedServerUrl,
                adminToken = settings.adminToken,
                request = CreateTeamKeyRequestDto(label = label, maxAccounts = maxAccounts)
            ).toDomain()
        }
    }

    override suspend fun updateKey(
        id: String,
        label: String?,
        maxAccounts: Int?
    ): Result<TeamKeyEntry> {
        return withAdmin { settings ->
            remoteDataSource.updateKey(
                baseUrl = settings.normalizedServerUrl,
                adminToken = settings.adminToken,
                id = id,
                request = UpdateTeamKeyRequestDto(label = label, maxAccounts = maxAccounts)
            ).toDomain()
        }
    }

    override suspend fun regenerateKey(id: String): Result<TeamKeyEntry> {
        return withAdmin { settings ->
            remoteDataSource.regenerateKey(
                baseUrl = settings.normalizedServerUrl,
                adminToken = settings.adminToken,
                id = id
            ).toDomain()
        }
    }

    override suspend fun revokeKey(id: String): Result<TeamKeyEntry> {
        return withAdmin { settings ->
            remoteDataSource.revokeKey(
                baseUrl = settings.normalizedServerUrl,
                adminToken = settings.adminToken,
                id = id
            ).toDomain()
        }
    }

    override suspend fun unclaimAccount(id: String, accountKey: String): Result<TeamKeyEntry> {
        return withAdmin { settings ->
            remoteDataSource.unclaimAccount(
                baseUrl = settings.normalizedServerUrl,
                adminToken = settings.adminToken,
                id = id,
                accountKey = accountKey
            ).toDomain()
        }
    }

    override suspend fun removeMember(accountKey: String, deviceId: String): Result<Unit> {
        return withAdmin { settings ->
            remoteDataSource.deleteMember(
                baseUrl = settings.normalizedServerUrl,
                credential = TeamCredential.AdminToken(settings.adminToken),
                accountKey = accountKey,
                deviceId = deviceId
            )
        }
    }

    override suspend fun removeSession(
        accountKey: String,
        deviceId: String,
        sessionId: String
    ): Result<Unit> {
        val result = withAdmin { settings ->
            remoteDataSource.deleteSession(
                baseUrl = settings.normalizedServerUrl,
                adminToken = settings.adminToken,
                accountKey = accountKey,
                deviceId = deviceId,
                sessionId = sessionId
            )
        }

        if (result.isMissingRoute()) {
            return Result.failure(IllegalStateException(OUTDATED_SESSION_DELETE_SERVER_MESSAGE))
        }
        return result
    }

    /**
     * Apaga a conta inteira. Não tem desfazer e não tem fallback.
     *
     * O `404` aqui só pode ser servidor anterior à rota: ela é idempotente e
     * responde `200` com zeros para conta desconhecida. Ao contrário de
     * [claimKeyForAccount], não há rota mais antiga que faça o mesmo — a única
     * saída é atualizar o servidor, e é isso que a mensagem diz.
     */
    override suspend fun deleteAccount(accountKey: String): Result<TeamAccountDeletion> {
        val result = withAdmin { settings ->
            remoteDataSource.deleteAccount(
                baseUrl = settings.normalizedServerUrl,
                adminToken = settings.adminToken,
                accountKey = accountKey
            ).toDomain()
        }

        if (result.isMissingRoute()) {
            return Result.failure(IllegalStateException(OUTDATED_SERVER_MESSAGE))
        }
        return result
    }

    override suspend fun fetchOverview(cutoffMillis: Long?): Result<List<TeamAccountUsage>> {
        return withAdmin { settings ->
            remoteDataSource.fetchOverview(
                baseUrl = settings.normalizedServerUrl,
                adminToken = settings.adminToken,
                sinceEpochMillis = cutoffMillis
            ).toDomain()
        }
    }

    /**
     * Verifica com a chave de **time**, não com o token de admin.
     *
     * O admin também pode chamar isto, e nesse caso o servidor responde
     * autorizado para qualquer conta — o que é a verdade: ele lê todas. Mas o
     * caso que importa é o do usuário comum conferindo a própria chave.
     *
     * `404` significa servidor anterior à rota. Não vira falha: o app não pode
     * reprovar uma configuração correta porque o servidor da empresa ficou para
     * trás — mesmo tratamento que `fetchSessionDetail` já dá ao detalhe ausente.
     */
    override suspend fun verifyKeyForAccount(accountKey: String): Result<TeamKeyVerification> {
        val settings = settingsProvider()
        val credential = settings.readCredential()
            ?: return Result.failure(IllegalStateException(NOT_CONFIGURED_MESSAGE))

        return runCatching {
            remoteDataSource.verifyKey(
                baseUrl = settings.normalizedServerUrl,
                credential = credential,
                accountKey = accountKey
            ).toDomain()
        }.recoverIfMissingRoute()
    }

    /**
     * Amarra a conta à chave, e só cai na verificação se a rota não existir.
     *
     * É o que faz "Testar conexão" resolver o problema em vez de apenas
     * descrevê-lo: até aqui o vínculo só nascia dentro de um ingest, então quem
     * trocava a chave numa máquina sem turno pendente não emitia requisição
     * nenhuma e a conta ficava sem dona — com a leitura recusada o tempo todo.
     */
    override suspend fun claimKeyForAccount(accountKey: String): Result<TeamKeyVerification> {
        val settings = settingsProvider()
        val credential = settings.readCredential()
            ?: return Result.failure(IllegalStateException(NOT_CONFIGURED_MESSAGE))

        val result = runCatching {
            remoteDataSource.claimKey(
                baseUrl = settings.normalizedServerUrl,
                credential = credential,
                accountKey = accountKey
            ).toDomain()
        }

        if (result.isMissingRoute()) {
            // Servidor 0.3.0: sem rota de vínculo, resta informar.
            return verifyKeyForAccount(accountKey)
        }
        return result
    }

    /** Credencial para conferir a conta: a chave de time primeiro, senão o admin. */
    private fun TeamIntegrationSettings.readCredential(): TeamCredential? {
        if (normalizedServerUrl.isEmpty()) {
            return null
        }
        return when {
            apiKey.isNotBlank() -> TeamCredential.TeamKey(apiKey)
            isAdminMode -> TeamCredential.AdminToken(adminToken)
            else -> null
        }
    }

    private suspend fun <T> withAdmin(
        block: suspend (TeamIntegrationSettings) -> T
    ): Result<T> {
        val settings = settingsProvider()
        if (!settings.isAdminMode) {
            return Result.failure(IllegalStateException(NOT_ADMIN_MESSAGE))
        }
        return runCatching { block(settings) }
    }
}

private const val NOT_FOUND_STATUS = 404

private fun Result<*>.isMissingRoute(): Boolean {
    val error = exceptionOrNull()
    return error is TeamServerException && error.statusCode == NOT_FOUND_STATUS
}

/**
 * Rota ausente vira "autorizado, sem verificação disponível".
 *
 * Um servidor anterior à rota não sabe responder sobre vínculo, e isso não é
 * motivo para o app declarar a configuração do usuário inválida.
 */
private fun Result<TeamKeyVerification>.recoverIfMissingRoute(): Result<TeamKeyVerification> {
    if (!isMissingRoute()) {
        return this
    }
    return Result.success(TeamKeyVerification(authorized = true, claimed = true))
}
