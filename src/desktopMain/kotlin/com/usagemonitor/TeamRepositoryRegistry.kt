package com.usagemonitor

import com.usagemonitor.data.datasource.RemoteTeamDataSource
import com.usagemonitor.data.repository.TeamAdminRepositoryImpl
import com.usagemonitor.data.repository.TeamUsageRepositoryImpl
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.domain.entity.TeamIntegrations
import com.usagemonitor.domain.repository.InMemoryTeamServerClockOffset
import com.usagemonitor.domain.repository.TeamServerClockOffset
import java.util.concurrent.ConcurrentHashMap

/**
 * Repositórios de time roteados pelo id do time. Cada id tem o seu repositório de uso, o de
 * administração e o seu próprio offset de relógio: o desvio de um servidor nunca corrige o de outro.
 *
 * As configurações são lidas do agregado a cada chamada, então o repositório segue o servidor
 * corrente do time. Id desconhecido resolve para configuração vazia (não configurado).
 */
class TeamRepositoryRegistry(
    private val remoteDataSource: RemoteTeamDataSource,
    private val integrations: () -> TeamIntegrations
) {
    private val usageRepositories = ConcurrentHashMap<String, TeamUsageRepositoryImpl>()
    private val adminRepositories = ConcurrentHashMap<String, TeamAdminRepositoryImpl>()
    private val clockOffsets = ConcurrentHashMap<String, InMemoryTeamServerClockOffset>()

    fun usageRepository(teamId: String): TeamUsageRepositoryImpl =
        usageRepositories.computeIfAbsent(teamId) { id ->
            TeamUsageRepositoryImpl(remoteDataSource, { settingsOf(id) }, serverClockOffset(id))
        }

    fun adminRepository(teamId: String): TeamAdminRepositoryImpl =
        adminRepositories.computeIfAbsent(teamId) { id ->
            TeamAdminRepositoryImpl(remoteDataSource, { settingsOf(id) })
        }

    fun serverClockOffset(teamId: String): TeamServerClockOffset =
        clockOffsets.computeIfAbsent(teamId) { InMemoryTeamServerClockOffset() }

    private fun settingsOf(teamId: String): TeamIntegrationSettings =
        integrations().team(teamId) ?: TeamIntegrationSettings()
}
