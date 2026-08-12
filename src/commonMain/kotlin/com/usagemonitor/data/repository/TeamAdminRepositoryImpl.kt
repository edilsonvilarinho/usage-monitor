package com.usagemonitor.data.repository

import com.usagemonitor.data.datasource.RemoteTeamDataSource
import com.usagemonitor.data.datasource.TeamCredential
import com.usagemonitor.data.dto.CreateTeamKeyRequestDto
import com.usagemonitor.data.dto.UpdateTeamKeyRequestDto
import com.usagemonitor.data.mapper.toDomain
import com.usagemonitor.domain.entity.TeamAccountUsage
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.domain.entity.TeamKeyEntry
import com.usagemonitor.domain.entity.TeamKeyVerification
import com.usagemonitor.domain.repository.TeamAdminRepository

private const val NOT_ADMIN_MESSAGE =
    "Modo administrador incompleto: informe servidor e token de administração nas Configurações."

private const val NOT_CONFIGURED_MESSAGE =
    "Integração com time incompleta: informe servidor e chave nas Configurações."

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
     */
    override suspend fun verifyKeyForAccount(accountKey: String): Result<TeamKeyVerification> {
        val settings = settingsProvider()
        val credential = when {
            settings.apiKey.isNotBlank() -> TeamCredential.TeamKey(settings.apiKey)
            settings.isAdminMode -> TeamCredential.AdminToken(settings.adminToken)
            else -> return Result.failure(IllegalStateException(NOT_CONFIGURED_MESSAGE))
        }

        if (settings.normalizedServerUrl.isEmpty()) {
            return Result.failure(IllegalStateException(NOT_CONFIGURED_MESSAGE))
        }

        return runCatching {
            remoteDataSource.verifyKey(
                baseUrl = settings.normalizedServerUrl,
                credential = credential,
                accountKey = accountKey
            ).toDomain()
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
