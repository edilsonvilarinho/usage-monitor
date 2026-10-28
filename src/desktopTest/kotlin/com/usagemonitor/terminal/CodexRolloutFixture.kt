package com.usagemonitor.terminal

import com.usagemonitor.data.datasource.LocalCodexCliSessionDataSource
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * O índice do Codex como o coletor o deixa: um rollout por sessão em `codex-home`, indexado
 * num banco próprio. Mesmo formato de linha do `LocalCodexCliSessionDataSourceTest`.
 */
internal object CodexRolloutFixture {

    /** Uma resposta da sessão: o contexto do turno em [startedAt] e o uso em [answeredAt]. */
    data class Response(
        val turnId: String,
        val startedAt: String,
        val answeredAt: String,
        val input: Long,
        val cached: Long,
        val output: Long,
        val model: String = "gpt-5.5-codex"
    )

    fun indexed(dir: File, sessionId: String, cwd: String, responses: List<Response>): File {
        val home = File(dir, "codex-home")
        val rollout = File(home, "sessions/2026/10/21/rollout-$sessionId.jsonl").also { file -> file.parentFile.mkdirs() }
        val lines = listOf(
            """{"type":"session_meta","payload":{"session_id":"$sessionId","cwd":"$cwd","cli_version":"0.153.4","source":"cli","thread_source":"user"}}"""
        ) + responses.flatMap { response ->
            listOf(
                """{"type":"turn_context","timestamp":"${response.startedAt}","payload":{"turn_id":"${response.turnId}","cwd":"$cwd","model":"${response.model}"}}""",
                """{"type":"token_usage_record","timestamp":"${response.answeredAt}","payload":{"session_id":"$sessionId","turn_id":"${response.turnId}",""" +
                    """"response_id":"r-${response.turnId}","usage":{"input_tokens":${response.input},"cached_input_tokens":${response.cached},""" +
                    """"cache_write_input_tokens":0,"output_tokens":${response.output},"reasoning_output_tokens":0,""" +
                    """"total_tokens":${response.input + response.output}}}}"""
            )
        }
        rollout.writeText(lines.joinToString("\n", postfix = "\n"))
        val database = File(dir, "codex-cli-history.db")
        LocalCodexCliSessionDataSource(codexHomeProvider = { home }, databaseFile = database).use { writer ->
            runBlocking { writer.syncIndex() }
        }
        return database
    }
}
