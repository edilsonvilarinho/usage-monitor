package com.usagemonitor.data.datasource

import com.usagemonitor.data.dto.ClaudeTranscriptLineDto
import com.usagemonitor.data.dto.ClaudeTranscriptTailLineDto
import com.usagemonitor.domain.entity.CliProjectRoot
import com.usagemonitor.domain.entity.CliSessionTail
import com.usagemonitor.domain.entity.CliSessionTailOutcome
import com.usagemonitor.domain.entity.SESSION_TAIL_WINDOW_BYTES
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream

// Leitura dos transcripts do Claude CLI: descoberta, parse incremental e cauda.
// Saíram de `LocalCliSessionDataSource` pelo limite de 800 linhas (#303).

internal fun listTranscriptFiles(root: CliProjectRoot): List<DiscoveredTranscript> {
    val directory = File(root.directoryPath)
    if (!directory.isDirectory) {
        return emptyList()
    }
    return directory.walkTopDown()
        .filter { candidate -> candidate.isFile && candidate.name.endsWith(TRANSCRIPT_EXTENSION) }
        .map { file -> DiscoveredTranscript(file = file, profileId = root.profileId) }
        .toList()
}

/**
 * Caminho do transcript **principal** de uma sessão.
 *
 * `cli_sessions.file_path` não serve direto: os transcripts de subagente vivem
 * em `projects/<slug>/<sessionId>/subagents/agent-*.jsonl`, carregam o
 * `sessionId` do pai e são varridos pelo `walkTopDown` — e o `UPSERT` grava
 * `file_path = excluded.file_path`, então a coluna pode acabar apontando para
 * o arquivo do subagente. Ler a cauda dele responderia sobre o subagente, não
 * sobre a sessão.
 *
 * A derivação é estrutural, não heurística: dentro de `subagents` o transcript
 * principal é `<sessionId>.jsonl` dois níveis acima.
 */
internal fun mainTranscriptOf(filePath: String, sessionId: String): File? {
    val file = File(filePath)
    if (file.parentFile?.name != SUBAGENTS_DIRECTORY_NAME) {
        return file
    }
    val projectDir = file.parentFile?.parentFile?.parentFile ?: return null
    return File(projectDir, "$sessionId$TRANSCRIPT_EXTENSION")
}

/**
 * Lê a última janela do arquivo e compara o último pedido com o último
 * marcador de fim de turno.
 *
 * A primeira linha da janela é descartada quando a leitura não começou no
 * início do arquivo: ela está cortada ao meio e decodificá-la daria lixo. Pela
 * mesma razão, linha que não decodifica é ignorada em vez de derrubar a
 * leitura — o transcript é escrito por outro processo e pode estar no meio de
 * um `append`.
 *
 * Sem nenhum marcador na janela o veredito é
 * [CliSessionTailOutcome.NOT_EVALUATED]: pode ser sessão de versão que não o
 * escreve, cauda maior que a janela, ou transcript de subagente. Chamar isso
 * de "sem resposta" seria afirmar o que não se sabe.
 */
internal fun readTail(sessionId: String, file: File, sizeBytes: Long, json: Json): CliSessionTail {
    val startOffset = (sizeBytes - SESSION_TAIL_WINDOW_BYTES).coerceAtLeast(0L)

    var lastRequestAt: Instant? = null
    var lastTurnEndAt: Instant? = null
    var isFirstLine = true

    forEachCompleteLine(file, startOffset) { rawLine ->
        val skipPartialLine = isFirstLine && startOffset > 0L
        isFirstLine = false
        if (skipPartialLine) {
            return@forEachCompleteLine
        }

        val line = runCatching { json.decodeFromString<ClaudeTranscriptTailLineDto>(rawLine) }.getOrNull()
            ?: return@forEachCompleteLine
        val instant = line.timestamp
            ?.let { value -> runCatching { Instant.parse(value) }.getOrNull() }

        when {
            line.type == USER_LINE_TYPE -> {
                // Sem carimbo o pedido existe mas não tem idade a medir: guardar
                // o anterior faria a pendência parecer mais velha do que é.
                lastRequestAt = instant
            }

            line.type == SYSTEM_LINE_TYPE && line.subtype == TURN_END_SUBTYPE -> {
                lastTurnEndAt = instant ?: lastTurnEndAt
                lastRequestAt = null
            }
        }
    }

    if (lastTurnEndAt == null) {
        return CliSessionTail(sessionId, CliSessionTailOutcome.NOT_EVALUATED)
    }

    val pendingRequestAt = lastRequestAt
    if (pendingRequestAt == null) {
        return CliSessionTail(
            sessionId = sessionId,
            outcome = CliSessionTailOutcome.TURN_COMPLETED,
            lastTurnEndAt = lastTurnEndAt
        )
    }

    return CliSessionTail(
        sessionId = sessionId,
        outcome = CliSessionTailOutcome.PENDING_REQUEST,
        lastRequestAt = pendingRequestAt,
        lastTurnEndAt = lastTurnEndAt
    )
}

internal fun parseTranscript(file: File, startOffset: Long, json: Json): ParsedTranscript {
    val turns = mutableListOf<ParsedTurn>()
    val metadata = mutableMapOf<String, SessionMetadata>()
    var skippedLines = 0

    val endOffset = forEachCompleteLine(file, startOffset) { rawLine ->
        // Rejeição barata: linhas sem `usage` nunca são turnos com consumo.
        if (!rawLine.contains(USAGE_MARKER)) {
            return@forEachCompleteLine
        }

        val line = runCatching { json.decodeFromString<ClaudeTranscriptLineDto>(rawLine) }.getOrNull()
        if (line == null) {
            skippedLines++
            return@forEachCompleteLine
        }

        val parsedTurn = line.toParsedTurn()
        if (parsedTurn == null) {
            return@forEachCompleteLine
        }

        turns.add(parsedTurn)
        metadata[parsedTurn.sessionId] = SessionMetadata(cwd = line.cwd, gitBranch = line.gitBranch)
    }

    return ParsedTranscript(
        turns = turns,
        metadataBySession = metadata,
        endOffset = endOffset,
        skippedLines = skippedLines
    )
}

internal fun ClaudeTranscriptLineDto.toParsedTurn(): ParsedTurn? {
    if (type != ASSISTANT_LINE_TYPE) {
        return null
    }
    val usage = message?.usage ?: return null
    val messageId = message.id?.takeIf { it.isNotBlank() } ?: uuid?.takeIf { it.isNotBlank() } ?: return null
    val session = sessionId?.takeIf { it.isNotBlank() } ?: return null
    val instant = timestamp?.let { value -> runCatching { Instant.parse(value) }.getOrNull() } ?: return null

    return ParsedTurn(
        sessionId = session,
        messageId = messageId,
        timestampMillis = instant.toEpochMilliseconds(),
        model = message.model,
        isSidechain = isSidechain,
        inputTokens = usage.inputTokens,
        outputTokens = usage.outputTokens,
        cacheReadTokens = usage.cacheReadInputTokens,
        cacheWrite5mTokens = usage.cacheWrite5mTokens,
        cacheWrite1hTokens = usage.cacheWrite1hTokens,
        toolCalls = message.toolCalls
    )
}

/**
 * Percorre o arquivo a partir de [startOffset] entregando apenas linhas terminadas
 * em `\n`. Devolve o offset em bytes logo após a última linha completa.
 */
internal fun forEachCompleteLine(file: File, startOffset: Long, onLine: (String) -> Unit): Long {
    var offset = startOffset

    FileInputStream(file).use { stream ->
        stream.channel.position(startOffset)

        val buffer = ByteArray(READ_BUFFER_BYTES)
        val pendingBytes = ByteArrayOutputStream()

        while (true) {
            val readCount = stream.read(buffer)
            if (readCount <= 0) {
                break
            }

            var lineStart = 0
            for (index in 0 until readCount) {
                if (buffer[index] != NEW_LINE_BYTE) {
                    continue
                }

                pendingBytes.write(buffer, lineStart, index - lineStart + 1)
                offset += pendingBytes.size().toLong()
                val rawLine = pendingBytes.toByteArray().toString(Charsets.UTF_8).trim()
                pendingBytes.reset()
                lineStart = index + 1

                if (rawLine.isNotEmpty()) {
                    onLine(rawLine)
                }
            }

            if (lineStart < readCount) {
                pendingBytes.write(buffer, lineStart, readCount - lineStart)
            }
        }
    }

    return offset
}

internal data class DiscoveredTranscript(
    val file: File,
    val profileId: String
)

internal data class SessionMetadata(
    val cwd: String?,
    val gitBranch: String?
)

internal data class ParsedTurn(
    val sessionId: String,
    val messageId: String,
    val timestampMillis: Long,
    val model: String?,
    val isSidechain: Boolean,
    val inputTokens: Long,
    val outputTokens: Long,
    val cacheReadTokens: Long,
    val cacheWrite5mTokens: Long,
    val cacheWrite1hTokens: Long,
    /** Nome da ferramenta → número de chamadas neste turno. */
    val toolCalls: Map<String, Int> = emptyMap()
)

internal data class ParsedTranscript(
    val turns: List<ParsedTurn>,
    val metadataBySession: Map<String, SessionMetadata>,
    val endOffset: Long,
    val skippedLines: Int
)
