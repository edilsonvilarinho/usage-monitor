package com.usagemonitor

import com.usagemonitor.data.CodexCliHomeProvider
import com.usagemonitor.domain.entity.CodexProfileRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.prefs.Preferences

/** Uma conta Codex extra gravada nas preferências (issue #329). */
internal data class CodexProfileRecord(
    val id: String,
    val label: String,
    /** Um diretório no formato do `CODEX_HOME`: `auth.json`, `cap_sid` e `sessions/`. */
    val directory: String,
    val enabled: Boolean
) {
    val ref: CodexProfileRef
        get() = CodexProfileRef(id, label)
}

/**
 * As contas Codex **extras** (issue #329). A padrão (`~/.codex` ou o
 * `CODEX_HOME` do ambiente) não passa por aqui: ela é o alvo sem perfil de sempre.
 *
 * Bem menor que o registro da Anthropic, e de propósito: não há descoberta, nem
 * cor, nem emoji, nem filtro de sessões por conta — o escopo aprovado foi um card
 * por conta. Cada conta é um diretório que o próprio Codex CLI usa (rodado com
 * `CODEX_HOME=<diretório>`), porque é o CLI que mantém o `auth.json` renovado; o
 * app só lê. Diretório que nenhum CLI usa fica com token vencido, e o card diz.
 *
 * O `id` leva o prefixo `codex-` para nunca coincidir com um perfil Anthropic nos
 * mapas por `profileId`.
 */
internal class CodexProfileRegistry(
    preferences: Preferences,
    private val defaultHome: () -> File = { CodexCliHomeProvider.resolve() }
) {
    private val profilesNode = preferences.node(PREFERENCES_NODE)
    private val _profiles = MutableStateFlow(readStoredProfiles())
    val profiles: StateFlow<List<CodexProfileRecord>> = _profiles.asStateFlow()

    val enabledProfiles: List<CodexProfileRef>
        get() = _profiles.value.filter { it.enabled }.map { it.ref }

    /**
     * Adiciona o diretório como conta extra, já habilitada. Recusa diretório sem
     * `auth.json` — não é uma sessão do Codex — e o diretório da conta padrão,
     * que já tem card. O mesmo diretório duas vezes devolve o registro existente.
     */
    fun add(directory: File): Result<CodexProfileRecord> = runCatching {
        require(directory.isDirectory) { "Diretório do Codex não encontrado: ${directory.absolutePath}" }
        require(File(directory, AUTH_FILE).isFile) {
            "Não há $AUTH_FILE em ${directory.absolutePath}. Rode o Codex com CODEX_HOME apontando para ele e faça login."
        }
        val canonical = directory.canonicalFile
        require(normalizePath(canonical.path) != normalizePath(defaultHome().path)) {
            "Esse diretório já é a conta Codex padrão."
        }
        _profiles.value.firstOrNull { normalizePath(it.directory) == normalizePath(canonical.path) }?.let { existing ->
            return@runCatching existing
        }
        val record = CodexProfileRecord(
            id = stableId(canonical.path),
            label = canonical.name.ifBlank { "Codex" },
            directory = canonical.path,
            enabled = true
        )
        writeProfile(record)
        publish()
        record
    }

    fun remove(profileId: String) {
        runCatching {
            profilesNode.node(profileId).removeNode()
            profilesNode.flush()
        }
        publish()
    }

    fun setEnabled(profileId: String, enabled: Boolean) {
        val current = _profiles.value.firstOrNull { it.id == profileId } ?: return
        writeProfile(current.copy(enabled = enabled))
        publish()
    }

    fun directoryOf(profileId: String): File? =
        _profiles.value.firstOrNull { it.id == profileId }?.let { record -> File(record.directory) }

    private fun publish() {
        _profiles.value = readStoredProfiles()
    }

    /** Ordem total e determinística: nome, depois caminho — a lista vai para um `StateFlow`. */
    private fun readStoredProfiles(): List<CodexProfileRecord> {
        val children = runCatching { profilesNode.childrenNames().toList() }.getOrDefault(emptyList())
        return children.mapNotNull { childName ->
            val node = profilesNode.node(childName)
            val path = node.get(KEY_PATH, "").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            CodexProfileRecord(
                id = childName,
                label = node.get(KEY_LABEL, File(path).name.ifBlank { "Codex" }),
                directory = path,
                enabled = node.getBoolean(KEY_ENABLED, true)
            )
        }.sortedWith(compareBy<CodexProfileRecord> { it.label.lowercase() }.thenBy { normalizePath(it.directory) })
    }

    private fun writeProfile(record: CodexProfileRecord) {
        val node = profilesNode.node(record.id)
        node.put(KEY_LABEL, record.label)
        node.put(KEY_PATH, record.directory)
        node.putBoolean(KEY_ENABLED, record.enabled)
        runCatching { node.flush() }
    }

    private fun stableId(path: String): String {
        return ID_PREFIX + UUID.nameUUIDFromBytes(normalizePath(path).toByteArray(StandardCharsets.UTF_8)).toString()
    }

    /**
     * Canônico dos **dois** lados da comparação: o diretório escolhido chega
     * canonicalizado, e o `CODEX_HOME` pode vir em nome curto 8.3 (`RUNNER~1`) ou
     * por link simbólico. Comparar canônico com cru deixava a conta padrão entrar
     * como extra — o CI do Windows pegou isso com o `TEMP` do runner.
     */
    private fun normalizePath(path: String): String {
        val file = File(path)
        val resolved = runCatching { file.canonicalFile }.getOrElse { file.absoluteFile.normalize() }
        return resolved.path.trimEnd(File.separatorChar).lowercase()
    }

    private companion object {
        const val PREFERENCES_NODE = "codexProfiles"
        const val ID_PREFIX = "codex-"
        const val AUTH_FILE = "auth.json"
        const val KEY_LABEL = "label"
        const val KEY_PATH = "path"
        const val KEY_ENABLED = "enabled"
    }
}
