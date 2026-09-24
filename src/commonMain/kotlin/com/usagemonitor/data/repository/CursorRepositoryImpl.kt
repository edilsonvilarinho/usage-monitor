package com.usagemonitor.data.repository

import com.usagemonitor.data.datasource.CursorSessionDataSource
import com.usagemonitor.data.datasource.CursorUsageApiDataSource
import com.usagemonitor.data.datasource.CursorUsageApiException
import com.usagemonitor.data.datasource.CursorUsageApiFailureKind
import com.usagemonitor.data.mapper.CursorUsageMapper
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.repository.CursorRepository
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class CursorRepositoryImpl(
    private val sessionDataSource: CursorSessionDataSource,
    private val usageApiDataSource: CursorUsageApiDataSource,
    private val nowProvider: () -> Instant = { Clock.System.now() }
) : CursorRepository {

    override suspend fun getUsage(): Result<ApiUsageStats> {
        return try {
            val credentials = sessionDataSource.readCredentials()
                ?: throw IllegalStateException("Cursor local session is unavailable")
            val response = usageApiDataSource.fetchUsage(credentials)
            Result.success(CursorUsageMapper.toDomain(response, nowProvider()))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: CursorUsageApiException) {
            val message = when (error.kind) {
                CursorUsageApiFailureKind.AUTHENTICATION_REJECTED ->
                    "Cursor rejected the local session (HTTP ${error.statusCode})"
                CursorUsageApiFailureKind.INVALID_RESPONSE ->
                    "Cursor usage response format is unrecognized"
                CursorUsageApiFailureKind.HTTP_STATUS ->
                    "Cursor usage request failed (HTTP ${error.statusCode})"
            }
            Result.failure(IllegalStateException(message))
        } catch (_: Throwable) {
            // Não inclui exceções JDBC/HTTP brutas: podem conter caminho local ou segredo.
            Result.failure(IllegalStateException("Cursor local session usage is unavailable"))
        }
    }
}
