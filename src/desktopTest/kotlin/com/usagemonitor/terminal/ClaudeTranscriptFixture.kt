package com.usagemonitor.terminal

import com.usagemonitor.data.datasource.LocalCliSessionDataSource
import com.usagemonitor.domain.entity.CliProjectRoot
import kotlinx.coroutines.runBlocking
import java.io.File

/** O índice do Claude Code como o coletor o deixa: transcripts em `projects/` indexados no `usage-history.db`. */
internal object ClaudeTranscriptFixture {

    fun assistantLine(sessionId: String, messageId: String, timestamp: String, input: Long, output: Long, model: String = "claude-opus-5") =
        """{"type":"assistant","uuid":"uuid-$messageId","sessionId":"$sessionId","timestamp":"$timestamp","cwd":"/workspace/usage-monitor","gitBranch":"main",""" +
            """"version":"2.1.226","isSidechain":false,"message":{"id":"$messageId","model":"$model","stop_reason":"end_turn","usage":{""" +
            """"input_tokens":$input,"output_tokens":$output,"cache_read_input_tokens":0,"cache_creation_input_tokens":0,""" +
            """"cache_creation":{"ephemeral_5m_input_tokens":0,"ephemeral_1h_input_tokens":0},"service_tier":"standard"}}}"""

    /** Cada transcript é um arquivo (nome → linhas) do projeto `usage-monitor`, do perfil `default`. */
    fun indexed(dir: File, transcripts: Map<String, List<String>>): File {
        val root = File(dir, "projects").also { it.mkdirs() }
        val project = File(root, "-workspace-usage-monitor").also { it.mkdirs() }
        transcripts.forEach { (name, lines) -> File(project, "$name.jsonl").writeText(lines.joinToString("\n", postfix = "\n")) }
        val database = File(dir, "usage-history.db")
        LocalCliSessionDataSource(projectRootsProvider = { listOf(CliProjectRoot("default", root.absolutePath)) }, databaseFile = database).use { writer ->
            runBlocking { writer.syncIndex() }
        }
        return database
    }
}
