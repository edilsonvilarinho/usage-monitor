package com.usagemonitor.data.datasource

import com.usagemonitor.AnthropicProfileLocation
import com.usagemonitor.domain.entity.AnthropicProfileRef
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Origem das credenciais OAuth do Claude Code.
 *
 * Windows e Linux usam sempre `~/.claude/.credentials.json`. No macOS o CLI grava
 * o mesmo JSON no Keychain (serviço `Claude Code-credentials`) e só recorre ao
 * ficheiro quando o Keychain está indisponível — daí a abstração.
 */
internal interface AnthropicCredentialStore {

    /** JSON das credenciais, ou `null` quando a origem não tem entrada. */
    fun read(): String?

    fun write(content: String)

    /** Mensagem de erro usada quando [read] devolve `null`. */
    fun missingCredentialsMessage(profileLabel: String): String
}

internal const val KEYCHAIN_CREDENTIALS_SERVICE = "Claude Code-credentials"

internal class FileCredentialStore(
    private val credentialsFile: File
) : AnthropicCredentialStore {

    override fun read(): String? {
        if (!credentialsFile.exists()) {
            return null
        }
        return credentialsFile.readText()
    }

    override fun write(content: String) {
        val parentDir = credentialsFile.parentFile
            ?: throw IllegalStateException("Diretório pai do ficheiro de credenciais não encontrado.")
        parentDir.mkdirs()
        val tempFile = File(parentDir, "${credentialsFile.name}.tmp")
        try {
            Files.writeString(tempFile.toPath(), content)
            try {
                Files.move(
                    tempFile.toPath(),
                    credentialsFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    tempFile.toPath(),
                    credentialsFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
            restrictToOwnerReadWrite(credentialsFile.toPath())
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    override fun missingCredentialsMessage(profileLabel: String): String {
        return "Credenciais não encontradas para o perfil '$profileLabel': ${credentialsFile.absolutePath}. " +
            "Execute o Claude Code CLI com esse CLAUDE_CONFIG_DIR para autenticar."
    }
}

/**
 * Lê e regrava a entrada do Keychain criada pelo Claude Code no macOS.
 *
 * A gravação passa o JSON no `argv` do `security`, que é o formato documentado do
 * comando. Isso expõe o segredo a quem consiga listar processos na máquina durante
 * a chamada; não há alternativa confirmada sem interatividade de terminal.
 */
internal class KeychainCredentialStore(
    private val accountName: String,
    private val commandRunner: (List<String>) -> ShellCommandResult = ::runShellCommand
) : AnthropicCredentialStore {

    override fun read(): String? {
        val result = commandRunner(
            listOf("security", "find-generic-password", "-a", accountName, "-s", KEYCHAIN_CREDENTIALS_SERVICE, "-w")
        )
        if (result.exitCode != 0) {
            return null
        }
        return result.output.trim().takeIf { it.isNotEmpty() }
    }

    override fun write(content: String) {
        val result = commandRunner(
            listOf(
                "security",
                "add-generic-password",
                "-U",
                "-a",
                accountName,
                "-s",
                KEYCHAIN_CREDENTIALS_SERVICE,
                "-w",
                content
            )
        )
        if (result.exitCode != 0) {
            throw IllegalStateException(
                "Falha ao gravar as credenciais renovadas no Keychain (código ${result.exitCode})."
            )
        }
    }

    override fun missingCredentialsMessage(profileLabel: String): String {
        return "Credenciais do Claude Code não encontradas no Keychain para o perfil '$profileLabel' " +
            "(serviço '$KEYCHAIN_CREDENTIALS_SERVICE', conta '$accountName'). " +
            "Execute /login no Claude Code ou confirme com: " +
            "security find-generic-password -s \"$KEYCHAIN_CREDENTIALS_SERVICE\""
    }
}

internal data class ShellCommandResult(
    val exitCode: Int,
    val output: String
)

internal fun runShellCommand(command: List<String>): ShellCommandResult {
    return runCatching {
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        ShellCommandResult(exitCode = process.waitFor(), output = output)
    }.getOrElse { error ->
        ShellCommandResult(exitCode = -1, output = error.message.orEmpty())
    }
}

/**
 * O ficheiro tem prioridade em todos os sistemas: quando existe, é a fonte que o
 * próprio Claude Code passa a respeitar. O Keychain só entra no macOS e apenas
 * para o perfil padrão — perfis extras (`CLAUDE_CONFIG_DIR`) não têm entrada lá.
 */
internal fun defaultCredentialStore(
    location: AnthropicProfileLocation,
    osName: String = System.getProperty("os.name").orEmpty(),
    userName: String = System.getProperty("user.name").orEmpty()
): AnthropicCredentialStore {
    val isDefaultProfile = location.profile.id == AnthropicProfileRef.DEFAULT.id
    if (!location.credentialsFile.exists() && isMacOs(osName) && isDefaultProfile) {
        return KeychainCredentialStore(accountName = userName)
    }
    return FileCredentialStore(location.credentialsFile)
}

internal fun isMacOs(osName: String): Boolean {
    val normalized = osName.lowercase()
    return normalized.contains("mac") || normalized.contains("darwin")
}
