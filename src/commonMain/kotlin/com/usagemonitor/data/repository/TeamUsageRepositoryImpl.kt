package com.usagemonitor.data.repository

import com.usagemonitor.data.datasource.RemoteTeamDataSource
import com.usagemonitor.data.mapper.toDomain
import com.usagemonitor.data.mapper.toDto
import com.usagemonitor.domain.entity.TeamIngestPayload
import com.usagemonitor.domain.entity.TeamIngestReceipt
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.domain.entity.TeamUsageSnapshot
import com.usagemonitor.domain.repository.TeamUsageRepository

/**
 * `accountKey` usado só para validar credenciais.
 *
 * O servidor responde `200` com listas vazias para qualquer conta desconhecida,
 * então isto exercita URL **e** chave sem depender de já haver dados gravados.
 */
private const val CONNECTION_CHECK_ACCOUNT_KEY = "__connection_check__"

private const val NOT_CONFIGURED_MESSAGE =
    "Integração com time incompleta: informe servidor, chave e apelido nas Configurações."

/**
 * As credenciais chegam por [settingsProvider] em vez de pelo construtor: o
 * usuário pode trocar servidor, chave ou apelido com o app aberto, e o
 * repositório tem de passar a usar os valores novos sem ser reconstruído.
 */
class TeamUsageRepositoryImpl(
    private val remoteDataSource: RemoteTeamDataSource,
    private val settingsProvider: () -> TeamIntegrationSettings
) : TeamUsageRepository {

    override suspend fun push(payload: TeamIngestPayload): Result<TeamIngestReceipt> {
        val settings = settingsProvider()
        if (!settings.isActive) {
            return Result.failure(IllegalStateException(NOT_CONFIGURED_MESSAGE))
        }

        return runCatching {
            remoteDataSource.pushIngest(
                baseUrl = settings.normalizedServerUrl,
                apiKey = settings.apiKey,
                request = payload.toDto()
            ).toDomain()
        }
    }

    override suspend fun fetch(accountKey: String, cutoffMillis: Long?): Result<TeamUsageSnapshot> {
        val settings = settingsProvider()
        if (!settings.isActive) {
            return Result.failure(IllegalStateException(NOT_CONFIGURED_MESSAGE))
        }

        return runCatching {
            remoteDataSource.fetchTeam(
                baseUrl = settings.normalizedServerUrl,
                apiKey = settings.apiKey,
                accountKey = accountKey,
                sinceEpochMillis = cutoffMillis
            ).toDomain()
        }
    }

    override suspend fun removeMember(accountKey: String, deviceId: String): Result<Unit> {
        val settings = settingsProvider()
        if (!settings.isActive) {
            return Result.failure(IllegalStateException(NOT_CONFIGURED_MESSAGE))
        }

        return runCatching {
            remoteDataSource.deleteMember(
                baseUrl = settings.normalizedServerUrl,
                apiKey = settings.apiKey,
                accountKey = accountKey,
                deviceId = deviceId
            )
        }
    }

    override suspend fun checkConnection(): Result<Unit> {
        val settings = settingsProvider()
        if (!settings.isConfigured) {
            return Result.failure(IllegalStateException(NOT_CONFIGURED_MESSAGE))
        }

        return runCatching {
            // Health primeiro: separa "URL errada" de "chave errada" na mensagem.
            remoteDataSource.checkHealth(settings.normalizedServerUrl)
            remoteDataSource.fetchTeam(
                baseUrl = settings.normalizedServerUrl,
                apiKey = settings.apiKey,
                accountKey = CONNECTION_CHECK_ACCOUNT_KEY,
                sinceEpochMillis = null
            )
        }
    }
}
