package com.usagemonitor.data.repository

import com.usagemonitor.data.datasource.RemoteTeamDataSource
import com.usagemonitor.data.datasource.TeamCredential
import com.usagemonitor.data.datasource.TeamServerException
import com.usagemonitor.data.mapper.toDomain
import com.usagemonitor.data.mapper.toDto
import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.TeamIngestPayload
import com.usagemonitor.domain.entity.TeamIngestReceipt
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.domain.entity.TeamUsageSnapshot
import com.usagemonitor.domain.repository.TeamUsageRepository

private const val NOT_CONFIGURED_MESSAGE =
    "Integração com time incompleta: informe servidor, chave e apelido nas Configurações."

private const val NOT_FOUND_STATUS = 404

/**
 * As credenciais chegam por [settingsProvider] em vez de pelo construtor: o
 * usuário pode trocar servidor, chave ou apelido com o app aberto, e o
 * repositório tem de passar a usar os valores novos sem ser reconstruído.
 *
 * Toda leitura aceita duas credenciais. A chave de time é a do usuário comum e
 * vale para as contas dela; o token de administração vale para qualquer conta e
 * é o que permite ao admin abrir a tela do time sem participar de nenhum. O
 * envio, ao contrário, só aceita chave de time — o servidor recusa o token de
 * admin ali, porque quem administra não escreve consumo em nome de ninguém.
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
        val credential = settings.readCredential()
            ?: return Result.failure(IllegalStateException(NOT_CONFIGURED_MESSAGE))

        return runCatching {
            remoteDataSource.fetchTeam(
                baseUrl = settings.normalizedServerUrl,
                credential = credential,
                accountKey = accountKey,
                sinceEpochMillis = cutoffMillis
            ).toDomain()
        }
    }

    /**
     * O `404` vira `success(null)`, e não falha.
     *
     * São dois casos indistinguíveis do lado do cliente: a sessão não existe mais
     * no servidor, ou o servidor é anterior à rota `/v1/session` e nem a conhece.
     * Nos dois o desfecho é o mesmo — não há turno a mostrar — e nenhum deles
     * pode virar erro na tela: nem todo time atualiza servidor e app juntos, e
     * essa janela é real.
     */
    override suspend fun fetchSessionDetail(
        accountKey: String,
        deviceId: String,
        sessionId: String
    ): Result<CliSessionDetail?> {
        val settings = settingsProvider()
        val credential = settings.readCredential()
            ?: return Result.failure(IllegalStateException(NOT_CONFIGURED_MESSAGE))

        val result = runCatching {
            remoteDataSource.fetchSessionDetail(
                baseUrl = settings.normalizedServerUrl,
                credential = credential,
                accountKey = accountKey,
                deviceId = deviceId,
                sessionId = sessionId
            ).toDomain()
        }

        val error = result.exceptionOrNull()
        if (error is TeamServerException && error.statusCode == NOT_FOUND_STATUS) {
            return Result.success(null)
        }
        return result
    }

    override suspend fun removeMember(accountKey: String, deviceId: String): Result<Unit> {
        val settings = settingsProvider()
        val credential = settings.readCredential()
            ?: return Result.failure(IllegalStateException(NOT_CONFIGURED_MESSAGE))

        return runCatching {
            remoteDataSource.deleteMember(
                baseUrl = settings.normalizedServerUrl,
                credential = credential,
                accountKey = accountKey,
                deviceId = deviceId
            )
        }
    }

    /**
     * Confere URL e chave sem gravar nada.
     *
     * Só o healthcheck: a conferência do vínculo com cada conta é feita por
     * `VerifyTeamKeyForAccountUseCase`, que tem o `accountUuid` real em mãos.
     * Este método consultava uma conta inventada para exercitar a chave, o que
     * funcionava enquanto qualquer chave lia qualquer conta — com autorização por
     * conta aquilo passaria a ser recusado e o botão reprovaria uma configuração
     * correta.
     */
    override suspend fun checkConnection(): Result<Unit> {
        val settings = settingsProvider()
        if (!settings.isConfigured) {
            return Result.failure(IllegalStateException(NOT_CONFIGURED_MESSAGE))
        }

        return runCatching { remoteDataSource.checkHealth(settings.normalizedServerUrl) }
    }
}

/**
 * Credencial de leitura desta instalação, ou `null` quando não há nenhuma.
 *
 * A chave de time vem primeiro: quem tem as duas é o admin que também participa
 * de um time, e nesse caso a leitura precisa ser a do time dele — usar o token
 * de admin mascararia um vínculo errado, que é justamente o que a tela quer
 * denunciar.
 */
private fun TeamIntegrationSettings.readCredential(): TeamCredential? {
    return when {
        isActive -> TeamCredential.TeamKey(apiKey)
        isAdminMode -> TeamCredential.AdminToken(adminToken)
        else -> null
    }
}
