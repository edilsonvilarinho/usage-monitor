package com.usagemonitor

import com.usagemonitor.domain.entity.AnthropicProfileRef
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.DEFAULT_ANTHROPIC_PROFILE_ID
import com.usagemonitor.domain.entity.UsageAccountContext
import com.usagemonitor.domain.entity.UsageAccountKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.prefs.Preferences

internal enum class AnthropicProfileOrigin { DEFAULT, ENVIRONMENT, DISCOVERED, MANUAL }

internal data class AnthropicProfileRecord(
    val id: String,
    val label: String,
    val configDirectory: String,
    val enabled: Boolean,
    val origin: AnthropicProfileOrigin,
    val hidden: Boolean = false
) {
    val ref: AnthropicProfileRef
        get() = AnthropicProfileRef(id, label)
}

internal data class AnthropicProfileLocation(
    val profile: AnthropicProfileRef,
    val configDirectory: File,
    val credentialsFile: File,
    val identityFile: File
)

internal enum class AnthropicProfileInspectionStatus { READY, INCOMPLETE, INVALID }

internal data class AnthropicProfileInspection(
    val status: AnthropicProfileInspectionStatus,
    val accountContext: UsageAccountContext? = null,
    val detail: String? = null
)

internal class AnthropicProfileRegistry(
    preferences: Preferences,
    private val defaultEnabled: Boolean,
    private val homeDirProvider: () -> File = {
        File(System.getProperty("user.home") ?: error("Propriedade 'user.home' não disponível"))
    },
    private val environmentProvider: (String) -> String? = System::getenv
) {
    private val profilesNode = preferences.node(PREFERENCES_NODE)
    private val json = Json { ignoreUnknownKeys = true }
    private val _profiles = MutableStateFlow<List<AnthropicProfileRecord>>(emptyList())
    val profiles: StateFlow<List<AnthropicProfileRecord>> = _profiles.asStateFlow()

    init {
        rescan()
    }

    fun rescan(restoreRemoved: Boolean = false) {
        val stored = readStoredProfiles().associateBy { normalizePath(it.configDirectory) }.toMutableMap()
        val candidates = discoverCandidates()

        candidates.forEach { candidate ->
            val normalizedPath = normalizePath(candidate.configDirectory)
            val previous = stored[normalizedPath]
            val merged = if (previous == null) {
                candidate
            } else {
                previous.copy(
                    origin = preferredOrigin(previous.origin, candidate.origin),
                    hidden = if (restoreRemoved) false else previous.hidden
                )
            }
            stored[normalizedPath] = merged
            writeProfile(merged)
        }

        if (restoreRemoved) {
            stored.values.filter { it.hidden }.forEach { record ->
                val restored = record.copy(hidden = false)
                stored[normalizePath(record.configDirectory)] = restored
                writeProfile(restored)
            }
        }

        publish(stored.values)
    }

    fun addManual(directory: File): Result<Unit> = runCatching {
        require(directory.exists() && directory.isDirectory) {
            "Diretório Anthropic não encontrado: ${directory.absolutePath}"
        }
        val canonical = directory.canonicalFile
        val existing = readStoredProfiles().firstOrNull {
            normalizePath(it.configDirectory) == normalizePath(canonical.absolutePath)
        }
        val record = if (existing == null) {
            AnthropicProfileRecord(
                id = stableId(canonical.absolutePath),
                label = canonical.name.ifBlank { "Anthropic" },
                configDirectory = canonical.absolutePath,
                enabled = false,
                origin = AnthropicProfileOrigin.MANUAL
            )
        } else {
            existing.copy(hidden = false, origin = AnthropicProfileOrigin.MANUAL)
        }
        writeProfile(record)
        rescan()
    }

    fun updateLabel(profileId: String, label: String) {
        val current = readStoredProfiles().firstOrNull { it.id == profileId } ?: return
        val updated = current.copy(label = label)
        writeProfile(updated)
        // Atualiza in-place, preservando a ordem atual da lista publicada.
        // Evita chamar rescan()/publish() (que reordena por label) a cada tecla digitada,
        // o que fazia o Compose perder o estado do campo de texto (cursor voltava pro final).
        _profiles.value = _profiles.value.map { record ->
            if (record.id == profileId) updated else record
        }
    }

    fun setEnabled(profileId: String, enabled: Boolean) {
        update(profileId) { record -> record.copy(enabled = enabled) }
    }

    fun removeFromMonitor(profileId: String) {
        if (profileId == DEFAULT_ANTHROPIC_PROFILE_ID) {
            return
        }
        update(profileId) { record -> record.copy(enabled = false, hidden = true) }
    }

    fun locationFor(profileId: String): AnthropicProfileLocation? {
        val record = _profiles.value.firstOrNull { it.id == profileId } ?: return null
        return locationFor(record)
    }

    fun inspect(record: AnthropicProfileRecord): AnthropicProfileInspection {
        val location = locationFor(record)
        if (!location.credentialsFile.isFile || !location.identityFile.isFile) {
            val missing = buildList {
                if (!location.credentialsFile.isFile) add(location.credentialsFile.name)
                if (!location.identityFile.isFile) add(location.identityFile.name)
            }
            return AnthropicProfileInspection(
                status = AnthropicProfileInspectionStatus.INCOMPLETE,
                detail = "Ausente: ${missing.joinToString(", ")}"
            )
        }

        return try {
            val credentialsRoot = json.parseToJsonElement(location.credentialsFile.readText()).jsonObject
            val oauth = credentialsRoot["claudeAiOauth"]?.jsonObject
                ?: error("claudeAiOauth ausente")
            val accessToken = oauth["accessToken"]?.jsonPrimitive?.contentOrNull.orEmpty()
            require(accessToken.isNotBlank()) { "accessToken ausente" }
            val root = json.parseToJsonElement(location.identityFile.readText()).jsonObject
            val account = root["oauthAccount"]?.jsonObject
                ?: error("oauthAccount ausente")
            val accountId = account["accountUuid"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val email = account["emailAddress"]?.jsonPrimitive?.contentOrNull.orEmpty()
            require(accountId.isNotBlank()) { "accountUuid ausente" }
            require(email.isNotBlank()) { "emailAddress ausente" }
            val organizationId = account["organizationUuid"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() }
            val organizationName = account["organizationName"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() }
            AnthropicProfileInspection(
                status = AnthropicProfileInspectionStatus.READY,
                accountContext = UsageAccountContext(
                    key = UsageAccountKey(ApiSource.ANTHROPIC, accountId, organizationId),
                    email = email,
                    workspaceName = organizationName
                )
            )
        } catch (error: Throwable) {
            AnthropicProfileInspection(
                status = AnthropicProfileInspectionStatus.INVALID,
                detail = error.message ?: "Identidade Anthropic inválida"
            )
        }
    }

    private fun update(profileId: String, transform: (AnthropicProfileRecord) -> AnthropicProfileRecord) {
        val current = readStoredProfiles().firstOrNull { it.id == profileId } ?: return
        writeProfile(transform(current))
        rescan()
    }

    private fun discoverCandidates(): List<AnthropicProfileRecord> {
        val home = homeDirProvider().absoluteFile
        val candidates = linkedMapOf<String, AnthropicProfileRecord>()

        fun add(directory: File, origin: AnthropicProfileOrigin, label: String) {
            val absolute = runCatching { directory.canonicalFile }.getOrElse { directory.absoluteFile }
            val normalized = normalizePath(absolute.absolutePath)
            if (origin != AnthropicProfileOrigin.DEFAULT && !containsAnthropicFiles(absolute)) {
                return
            }
            val id = if (origin == AnthropicProfileOrigin.DEFAULT) {
                DEFAULT_ANTHROPIC_PROFILE_ID
            } else {
                stableId(absolute.absolutePath)
            }
            candidates.putIfAbsent(
                normalized,
                AnthropicProfileRecord(
                    id = id,
                    label = label,
                    configDirectory = absolute.absolutePath,
                    enabled = origin == AnthropicProfileOrigin.DEFAULT && defaultEnabled,
                    origin = origin
                )
            )
        }

        add(File(home, ".claude"), AnthropicProfileOrigin.DEFAULT, "Padrão")

        environmentProvider("CLAUDE_CONFIG_DIR")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { path -> add(File(path), AnthropicProfileOrigin.ENVIRONMENT, "Ambiente") }

        home.listFiles()
            ?.asSequence()
            ?.filter { file -> file.isDirectory && file.name.startsWith(".claude-") }
            ?.sortedBy { file -> file.name.lowercase() }
            ?.forEach { directory ->
                add(directory, AnthropicProfileOrigin.DISCOVERED, directory.name.removePrefix(".claude-"))
            }

        return candidates.values.toList()
    }

    private fun containsAnthropicFiles(directory: File): Boolean {
        return File(directory, ".credentials.json").isFile || File(directory, ".claude.json").isFile
    }

    private fun locationFor(record: AnthropicProfileRecord): AnthropicProfileLocation {
        val directory = File(record.configDirectory)
        val identityFile = if (record.id == DEFAULT_ANTHROPIC_PROFILE_ID) {
            File(homeDirProvider(), ".claude.json")
        } else {
            File(directory, ".claude.json")
        }
        return AnthropicProfileLocation(
            profile = record.ref,
            configDirectory = directory,
            credentialsFile = File(directory, ".credentials.json"),
            identityFile = identityFile
        )
    }

    private fun readStoredProfiles(): List<AnthropicProfileRecord> {
        val children = runCatching { profilesNode.childrenNames().toList() }.getOrDefault(emptyList())
        return children.mapNotNull { childName ->
            val node = profilesNode.node(childName)
            val path = node.get(KEY_PATH, "").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val origin = runCatching {
                AnthropicProfileOrigin.valueOf(node.get(KEY_ORIGIN, AnthropicProfileOrigin.DISCOVERED.name))
            }.getOrDefault(AnthropicProfileOrigin.DISCOVERED)
            AnthropicProfileRecord(
                id = childName,
                label = node.get(KEY_LABEL, File(path).name.ifBlank { "Anthropic" }),
                configDirectory = path,
                enabled = node.getBoolean(KEY_ENABLED, false),
                origin = origin,
                hidden = node.getBoolean(KEY_HIDDEN, false)
            )
        }
    }

    private fun writeProfile(record: AnthropicProfileRecord) {
        val node = profilesNode.node(record.id)
        node.put(KEY_LABEL, record.label)
        node.put(KEY_PATH, record.configDirectory)
        node.putBoolean(KEY_ENABLED, record.enabled)
        node.put(KEY_ORIGIN, record.origin.name)
        node.putBoolean(KEY_HIDDEN, record.hidden)
        runCatching { node.flush() }
    }

    private fun publish(records: Collection<AnthropicProfileRecord>) {
        _profiles.value = records
            .filterNot { it.hidden }
            .sortedWith(
                compareBy<AnthropicProfileRecord> { it.id != DEFAULT_ANTHROPIC_PROFILE_ID }
                    .thenBy { it.label.lowercase() }
                    .thenBy { normalizePath(it.configDirectory) }
            )
    }

    private fun preferredOrigin(
        stored: AnthropicProfileOrigin,
        discovered: AnthropicProfileOrigin
    ): AnthropicProfileOrigin {
        if (stored == AnthropicProfileOrigin.MANUAL) return stored
        if (discovered == AnthropicProfileOrigin.DEFAULT) return discovered
        if (discovered == AnthropicProfileOrigin.ENVIRONMENT) return discovered
        return stored
    }

    private fun stableId(path: String): String {
        return UUID.nameUUIDFromBytes(normalizePath(path).toByteArray(StandardCharsets.UTF_8)).toString()
    }

    private fun normalizePath(path: String): String {
        return File(path).absoluteFile.normalize().path.trimEnd(File.separatorChar).lowercase()
    }

    private companion object {
        const val PREFERENCES_NODE = "anthropicProfiles"
        const val KEY_LABEL = "label"
        const val KEY_PATH = "path"
        const val KEY_ENABLED = "enabled"
        const val KEY_ORIGIN = "origin"
        const val KEY_HIDDEN = "hidden"
    }
}
