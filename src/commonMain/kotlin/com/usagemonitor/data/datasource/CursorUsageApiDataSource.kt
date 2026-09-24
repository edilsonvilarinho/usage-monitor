package com.usagemonitor.data.datasource

import kotlinx.serialization.json.JsonElement

interface CursorUsageApiDataSource {
    suspend fun fetchUsage(credentials: CursorSessionCredentials): JsonElement
}

enum class CursorUsageApiFailureKind {
    HTTP_STATUS,
    AUTHENTICATION_REJECTED,
    INVALID_RESPONSE
}

/** Sem corpo HTTP: a rota pode ecoar dados de sessão e nunca deve entrar no erro/log. */
class CursorUsageApiException(
    val statusCode: Int,
    val kind: CursorUsageApiFailureKind
) : IllegalStateException("Cursor usage request failed")
