package com.usagemonitor.data.repository

import com.usagemonitor.data.datasource.CursorSessionDataSource
import com.usagemonitor.data.datasource.CursorUsageApiDataSource
import com.usagemonitor.data.datasource.CursorUsageApiException
import com.usagemonitor.data.datasource.CursorUsageApiFailureKind
import com.usagemonitor.data.mapper.CURSOR_UNRECOGNIZED_RESPONSE
import com.usagemonitor.data.mapper.CursorUsageMapper
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.repository.CursorRepository
import com.usagemonitor.domain.repository.CursorUsageException
import com.usagemonitor.domain.repository.CursorUsageFailureKind
import kotlinx.coroutines.CancellationException
import java.io.IOException

/**
 * Lê o resumo pessoal do Cursor pela sessão que o editor já mantém.
 *
 * **Falha de rede passa intacta.** `DashboardViewModel.handleTargetFailure`
 * classifica conectividade pelo **tipo** da exceção (`isConnectivityFailure`), e a
 * primeira versão embrulhava tudo numa `IllegalStateException` genérica: atrás de
 * proxy o Cursor perdia o banner de rede e o "Tentar novamente" universal.
 */
class CursorRepositoryImpl(
    private val sessionDataSource: CursorSessionDataSource,
    private val usageApiDataSource: CursorUsageApiDataSource
) : CursorRepository {

    override suspend fun getUsage(): Result<ApiUsageStats> {
        return try {
            val credentials = sessionDataSource.readCredentials()
            val response = usageApiDataSource.fetchUsage(credentials)
            Result.success(CursorUsageMapper.toDomain(response))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (known: CursorUsageException) {
            Result.failure(known)
        } catch (error: CursorUsageApiException) {
            Result.failure(
                when (error.kind) {
                    CursorUsageApiFailureKind.AUTHENTICATION_REJECTED ->
                        CursorUsageException(CursorUsageFailureKind.SESSION_REJECTED)
                    CursorUsageApiFailureKind.INVALID_RESPONSE ->
                        IllegalStateException(CURSOR_UNRECOGNIZED_RESPONSE)
                    // "HTTP 429"/"HTTP 503" no texto é o que os marcadores de limite e
                    // indisponibilidade da tela procuram.
                    CursorUsageApiFailureKind.HTTP_STATUS ->
                        IllegalStateException("Cursor usage request failed (HTTP ${error.statusCode})")
                }
            )
        } catch (network: IOException) {
            Result.failure(network)
        } catch (formatChanged: IllegalStateException) {
            // Só a mensagem do próprio mapper passa; qualquer outra pode trazer
            // detalhe local e vira a genérica.
            val message = formatChanged.message.takeIf { it == CURSOR_UNRECOGNIZED_RESPONSE }
                ?: "Cursor local session usage is unavailable"
            Result.failure(IllegalStateException(message))
        } catch (_: Throwable) {
            // Não inclui exceções JDBC/HTTP brutas: podem conter caminho local ou segredo.
            Result.failure(IllegalStateException("Cursor local session usage is unavailable"))
        }
    }
}
