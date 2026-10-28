package com.usagemonitor

import com.usagemonitor.domain.entity.AnthropicProfileRef
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.CodexProfileRef
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.domain.entity.UsageAccountContext
import com.usagemonitor.domain.entity.UsageAccountKey
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.ui.components.AnthropicProfileUiStatus
import com.usagemonitor.presentation.ui.components.CodexProfileUiModel
import com.usagemonitor.presentation.ui.theme.AccountAccent
import com.usagemonitor.presentation.ui.theme.AccountEmoji
import com.usagemonitor.presentation.viewmodel.TeamPulseTarget
import java.io.File
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppProfilesTest {
    private val tempDir = createTempDirectory("app-profiles").toFile()
    private val preferences = Preferences.userRoot().node("com.usagemonitor.test/${UUID.randomUUID()}")
    private val registries = mutableListOf<AnthropicProfileRegistry>()

    @AfterTest
    fun cleanup() {
        registries.forEach { registry -> registry.close() }
        registries.clear()
        tempDir.deleteRecursively()
        runCatching { preferences.removeNode() }
    }

    @Test
    fun `profile models carry the inspection status and a duplicate wins over a ready inspection`() {
        val records = listOf(
            record("default", color = "VIOLET", emoji = "FOX"),
            record("work"),
            record("broken"),
            record("copy"),
            record("missing")
        )
        val ready = AnthropicProfileInspection(AnthropicProfileInspectionStatus.READY, accountContext("a@example.com", "Acme"))
        val inspections = mapOf(
            "default" to ready,
            "work" to AnthropicProfileInspection(AnthropicProfileInspectionStatus.INCOMPLETE, detail = "Sem credenciais"),
            "broken" to AnthropicProfileInspection(AnthropicProfileInspectionStatus.INVALID, detail = "JSON inválido"),
            "copy" to ready
        )

        val models = buildAnthropicProfileUiModels(records, inspections, duplicateProfileIds = setOf("copy"))

        assertEquals(
            listOf(
                AnthropicProfileUiStatus.READY,
                AnthropicProfileUiStatus.INCOMPLETE,
                AnthropicProfileUiStatus.INVALID,
                AnthropicProfileUiStatus.DUPLICATE,
                AnthropicProfileUiStatus.INCOMPLETE
            ),
            models.map { model -> model.status }
        )
        val default = models.first()
        assertEquals("a@example.com — Acme", default.identityLabel)
        assertFalse(default.removable, "o perfil padrão não sai da lista")
        assertEquals(AccountAccent.VIOLET, default.color)
        assertEquals(AccountEmoji.FOX, default.emoji)
        assertEquals("/home/u/.claude-default", default.path)
        assertTrue(models.drop(1).all { model -> model.removable })
        assertEquals("Sem credenciais", models[1].detail)
        assertEquals("JSON inválido", models[2].detail)
        // A duplicada diz por que não é coletada, mesmo com a identidade lida.
        assertEquals("Já monitorada por outro perfil habilitado", models[3].detail)
        assertEquals("a@example.com — Acme", models[3].identityLabel)
        // Sem inspeção não há identidade nem detalhe para mostrar.
        assertNull(models[4].identityLabel)
        assertNull(models[4].detail)
    }

    @Test
    fun `colors and emojis map only the profiles that chose one and ignore unknown stored names`() {
        val records = listOf(
            record("default", color = "GREEN", emoji = "CAT"),
            record("work", color = "NOT_A_COLOR", emoji = "NOT_AN_EMOJI"),
            record("home")
        )

        assertEquals(mapOf("default" to AccountAccent.GREEN), accountColorsOf(records))
        assertEquals(mapOf("default" to AccountEmoji.CAT), accountEmojisOf(records))
    }

    @Test
    fun `codex profile models keep the registry order and fields`() {
        val records = listOf(
            CodexProfileRecord("codex-a", "Pessoal", "/home/u/.codex-a", enabled = true),
            CodexProfileRecord("codex-b", "Trabalho", "/home/u/.codex-b", enabled = false)
        )

        assertEquals(
            listOf(
                CodexProfileUiModel("codex-a", "Pessoal", "/home/u/.codex-a", true),
                CodexProfileUiModel("codex-b", "Trabalho", "/home/u/.codex-b", false)
            ),
            buildCodexProfileUiModels(records)
        )
    }

    @Test
    fun `available targets follow the source order with one card per anthropic profile and the default codex first`() {
        val targets = availableUsageTargets(
            records = listOf(record("default"), record("work")),
            codexRecords = listOf(CodexProfileRecord("codex-extra", "Extra", "/x", enabled = true))
        )

        val expected = ApiSource.entries.flatMap { source ->
            when (source) {
                ApiSource.ANTHROPIC -> listOf(UsageTargetKey(source, "default"), UsageTargetKey(source, "work"))
                ApiSource.CODEX -> listOf(UsageTargetKey.forSource(source), UsageTargetKey(source, "codex-extra"))
                else -> listOf(UsageTargetKey.forSource(source))
            }
        }
        assertEquals(expected, targets)
        // A conta Codex padrão segue sem profileId (CLAUDE.md, contas Codex extras).
        assertNull(targets.first { target -> target.source == ApiSource.CODEX }.profileId)
    }

    @Test
    fun `available targets without anthropic profiles have no anthropic card`() {
        val targets = availableUsageTargets(records = emptyList())

        assertTrue(targets.none { target -> target.source == ApiSource.ANTHROPIC })
        assertEquals(ApiSource.entries.size - 1, targets.size)
    }

    @Test
    fun `enabled targets keep only enabled sources and the enabled profiles`() {
        val targets = enabledUsageTargets(
            enabledSources = setOf(ApiSource.ANTHROPIC, ApiSource.CODEX),
            enabledProfiles = listOf(AnthropicProfileRef("work", "Trabalho")),
            enabledCodexProfiles = listOf(CodexProfileRef("codex-extra", "Extra"))
        )

        assertTrue(UsageTargetKey(ApiSource.ANTHROPIC, "work") in targets)
        assertTrue(UsageTargetKey(ApiSource.CODEX, "codex-extra") in targets)
        assertTrue(targets.none { target -> target.source == ApiSource.MINIMAX })
    }

    @Test
    fun `session pulse asks only for participating profiles with a resolved identity while the integration is active`() {
        val defaultDir = File(tempDir, ".claude").also { directory -> directory.mkdirs() }
        val workDir = File(tempDir, ".claude-work").also { directory -> directory.mkdirs() }
        // Só credenciais, sem `.claude.json`: o perfil é descoberto, mas sem identidade.
        val noIdentityDir = File(tempDir, ".claude-empty").also { directory -> directory.mkdirs() }
        File(noIdentityDir, ".credentials.json").writeText("{\"claudeAiOauth\":{\"accessToken\":\"test-token\"}}")
        writeProfileFiles(defaultDir, File(tempDir, ".claude.json"), "default@example.com", "account-a")
        writeProfileFiles(workDir, File(workDir, ".claude.json"), "work@example.com", "account-b")
        val registry = newRegistry()
        val workId = registry.profiles.value.first { record -> record.configDirectory.endsWith(".claude-work") }.id
        val emptyId = registry.profiles.value.first { record -> record.configDirectory.endsWith(".claude-empty") }.id
        val active = TeamIntegrationSettings(
            enabled = true,
            serverUrl = "https://team.example.com",
            apiKey = "team-key",
            alias = "dev",
            deviceId = "device-1",
            participatingProfileIds = setOf(workId, emptyId)
        )

        // O perfil sem identidade fica de fora: sem accountUuid não há chave de agrupamento.
        assertEquals(listOf(TeamPulseTarget(profileId = workId, accountKey = "account-b")), buildSessionPulseTargets(registry, active))
        assertEquals(emptyList(), buildSessionPulseTargets(registry, active.copy(enabled = false)))
        assertEquals(emptyList(), buildSessionPulseTargets(registry, active.copy(apiKey = "")))
    }

    private fun newRegistry(): AnthropicProfileRegistry {
        val registry = AnthropicProfileRegistry(
            preferences = preferences,
            defaultEnabled = true,
            homeDirProvider = { tempDir },
            environmentProvider = { null }
        )
        registries += registry
        return registry
    }

    private fun record(id: String, color: String? = null, emoji: String? = null) = AnthropicProfileRecord(
        id = id,
        label = "Conta $id",
        configDirectory = "/home/u/.claude-$id",
        enabled = true,
        origin = AnthropicProfileOrigin.DISCOVERED,
        color = color,
        emoji = emoji
    )

    private fun accountContext(email: String, workspace: String) = UsageAccountContext(
        key = UsageAccountKey(ApiSource.ANTHROPIC, providerAccountId = "account-$email", workspaceId = "org"),
        email = email,
        workspaceName = workspace
    )

    private fun writeProfileFiles(configDirectory: File, identityFile: File, email: String, accountId: String) {
        File(configDirectory, ".credentials.json").writeText("{\"claudeAiOauth\":{\"accessToken\":\"test-token\"}}")
        identityFile.writeText(
            """
            {
              "oauthAccount": {
                "accountUuid": "$accountId",
                "emailAddress": "$email",
                "organizationUuid": "org-$accountId",
                "organizationName": "Org $accountId"
              }
            }
            """.trimIndent()
        )
    }
}
