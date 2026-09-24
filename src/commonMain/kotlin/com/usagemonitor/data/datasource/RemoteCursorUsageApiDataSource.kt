package com.usagemonitor.data.datasource

import com.usagemonitor.data.datasource.CursorUsageApiFailureKind.AUTHENTICATION_REJECTED
import com.usagemonitor.data.datasource.CursorUsageApiFailureKind.INVALID_RESPONSE
import com.usagemonitor.data.datasource.CursorUsageApiFailureKind.HTTP_STATUS
import kotlinx.serialization.json.JsonElement

class RemoteCursorUsageApiDataSource(
    private val remoteApiDataSource: RemoteApiDataSource
) : CursorUsageApiDataSource {
    override suspend fun fetchUsage(credentials: CursorSessionCredentials): JsonElement {
        return remoteApiDataSource.fetchCursorUsageSummary(
            accountId = credentials.accountId,
            accessToken = credentials.accessToken
        )
    }
}
