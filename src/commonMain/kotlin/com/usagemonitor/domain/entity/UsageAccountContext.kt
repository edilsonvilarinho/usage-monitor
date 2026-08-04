package com.usagemonitor.domain.entity

data class UsageAccountKey(
    val source: ApiSource,
    val providerAccountId: String,
    val workspaceId: String? = null
)

data class UsageAccountContext(
    val key: UsageAccountKey,
    val email: String,
    val workspaceName: String? = null
) {
    init {
        require(email.isNotBlank()) { "O e-mail da conta de uso não pode ser vazio." }
        require(key.providerAccountId.isNotBlank()) { "O identificador da conta de uso não pode ser vazio." }
    }

    val displayLabel: String
        get() {
            val normalizedWorkspaceName = workspaceName?.trim().orEmpty()
            return if (normalizedWorkspaceName.isEmpty()) {
                email
            } else {
                "$email — $normalizedWorkspaceName"
            }
        }
}

val ApiSource.requiresUsageAccount: Boolean
    get() = this == ApiSource.ANTHROPIC || this == ApiSource.CODEX
