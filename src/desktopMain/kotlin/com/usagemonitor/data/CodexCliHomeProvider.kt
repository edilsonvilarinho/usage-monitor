package com.usagemonitor.data

import java.io.File

/** Resolve somente a raiz de dados do CLI; não lê nem depende do cache de autenticação. */
object CodexCliHomeProvider {
    fun resolve(
        environment: Map<String, String> = System.getenv(),
        userHome: String = System.getProperty("user.home")
    ): File {
        val configuredHome = environment["CODEX_HOME"]?.trim().orEmpty()
        return if (configuredHome.isNotEmpty()) File(configuredHome) else File(userHome, ".codex")
    }
}
