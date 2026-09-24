package com.usagemonitor.data.datasource

data class CursorSessionCredentials(
    val accountId: String,
    val accessToken: String
)

/** Lê, sem gravar, a sessão que o próprio editor Cursor já mantém. */
interface CursorSessionDataSource {
    suspend fun readCredentials(): CursorSessionCredentials?
}
