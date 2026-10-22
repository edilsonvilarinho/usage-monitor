package com.usagemonitor.terminal

import com.usagemonitor.AnthropicProfileRecord
import com.usagemonitor.AnthropicProfileRegistry
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.UsageTargetKey
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * O que a linha de status usa do JSON que o Claude Code passa pela entrada a cada
 * atualização (`statusLine` do `settings.json`). Só três campos; o resto é ignorado, e
 * entrada ausente ou malformada vira campos nulos, nunca erro: a linha de status não pode
 * quebrar o prompt de ninguém.
 */
internal data class ClaudeCodeStatusInput(
    val sessionId: String? = null,
    val transcriptPath: String? = null,
    val model: String? = null
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun parse(text: String): ClaudeCodeStatusInput {
            val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return ClaudeCodeStatusInput()
            return ClaudeCodeStatusInput(
                sessionId = root.string("session_id"),
                transcriptPath = root.string("transcript_path"),
                model = runCatching { root["model"]?.jsonObject?.string("display_name") }.getOrNull()
            )
        }

        private fun JsonObject.string(key: String): String? =
            runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()?.takeIf { value -> value.isNotBlank() }
    }
}

/**
 * O diretório de configuração do Claude Code que chamou a linha de status: o
 * `CLAUDE_CONFIG_DIR` herdado, senão o pai da pasta `projects` do transcript (o Claude
 * Code grava as sessões em `<config>/projects/<projeto>/<sessão>.jsonl`), senão `~/.claude`.
 */
internal fun claudeConfigDirectory(environment: Map<String, String>, input: ClaudeCodeStatusInput, home: File): File {
    environment["CLAUDE_CONFIG_DIR"]?.takeIf { value -> value.isNotBlank() }?.let { value -> return File(value) }
    val fromTranscript = input.transcriptPath?.let { path ->
        generateSequence(File(path).parentFile) { directory -> directory.parentFile }
            .firstOrNull { directory -> directory.name == "projects" }
            ?.parentFile
    }
    return fromTranscript ?: File(home, ".claude")
}

/**
 * A conta do app que usa este diretório de configuração. `null` quando nenhum perfil
 * gravado aponta para ele: melhor não mostrar cota do que mostrar a de outra conta.
 *
 * Compara pelo caminho **real**, com os links resolvidos: no Fedora Silverblue `/home` é
 * link para `/var/home`, e o perfil gravado com um lado não casava com o transcript que
 * chegou pelo outro (medido na A08 da #439). `toRealPath`, e não `canonicalPath`, porque
 * no Windows o `canonicalPath` não resolve link simbólico (o CI da #439 pegou). Diretório
 * que não existe cai na normalização do registro. Sem caixa, como o registro de perfis.
 */
internal fun anthropicTargetFor(configDirectory: File, profiles: List<AnthropicProfileRecord>): UsageTargetKey? {
    val wanted = canonicalKey(configDirectory.path)
    val profile = profiles.firstOrNull { record -> canonicalKey(record.configDirectory) == wanted } ?: return null
    return UsageTargetKey(ApiSource.ANTHROPIC, profile.id)
}

private fun canonicalKey(path: String): String =
    runCatching { File(path).toPath().toRealPath().toString().trimEnd(File.separatorChar).lowercase() }
        .getOrElse { AnthropicProfileRegistry.normalizedPath(path) }
