package com.usagemonitor.data.datasource

interface AntigravityUsageDataSource {
    /**
     * Devolve o envelope JSON cru de `agy --output-format json --print /usage`.
     *
     * O texto fica só em memória: ele pode trazer a conta do usuário e nunca é
     * anexado a exceção, log ou cache.
     */
    suspend fun readUsageJson(): String
}
