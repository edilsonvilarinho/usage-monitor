package com.usagemonitor.data.mapper

import com.usagemonitor.domain.entity.ReportedModelQuota
import com.usagemonitor.domain.entity.UsageUnit

/** Parser estrito dos valores textuais conhecidos no painel /usage. */
object AntigravityUsageParser {
    private val ansiEscape = Regex("\\u001B(?:\\[[0-?]*[ -/]*[@-~]|\\][^\\u0007]*(?:\\u0007|\\u001B\\\\))")
    private val ansiStyling = Regex("\\u001B\\[[0-?]*[ -/]*m")
    private val modelLabel = Regex("^model\\s*:\\s*(.+)$", RegexOption.IGNORE_CASE)
    private val usedPercent = Regex("^(.+?)\\s+(\\d{1,3}(?:[.,]\\d{1,2})?)\\s*%\\s*used\\b", RegexOption.IGNORE_CASE)
    private val remainingPercent = Regex("^(.+?)\\s+(\\d{1,3}(?:[.,]\\d{1,2})?)\\s*%\\s*remaining\\b", RegexOption.IGNORE_CASE)
    private val inlinePercent = Regex("^(used|remaining)\\s*:\\s*(\\d{1,3}(?:[.,]\\d{1,2})?)\\s*%\\s*$", RegexOption.IGNORE_CASE)
    private val anyPercent = Regex("(?:^|[^\\d.])(\\d{1,3}(?:[.,]\\d{1,2})?)\\s*%")
    private val groupHeading = Regex("^(.+?)\\s+models$", RegexOption.IGNORE_CASE)
    private val weeklyLimitRemaining = Regex("weekly\\s+limit\\s+remaining", RegexOption.IGNORE_CASE)
    private val inlineCount = Regex("^(used|remaining|limit)\\s*:\\s*([\\d,]+)\\s*(tokens?|requests?)\\b.*$", RegexOption.IGNORE_CASE)
    private val countSuffix = Regex("^([\\d,]+)\\s*(tokens?|requests?)\\s+(used|remaining|limit)\\b.*$", RegexOption.IGNORE_CASE)
    private val countRatio = Regex("^(.+?)\\s+([\\d,]+)\\s*/\\s*([\\d,]+)\\s*(tokens?|requests?)\\s+(used|remaining)\\b.*$", RegexOption.IGNORE_CASE)
    private val reset = Regex("^(?:resets?|reset|refresh(?:es)?)\\s*(?::|at|in|on)?\\s*(.+)$", RegexOption.IGNORE_CASE)
    private val authenticationRequired = Regex(
        "(?:sign in to continue|login required|authentication required|session expired|not authenticated)",
        RegexOption.IGNORE_CASE
    )

    fun hasRecognizedPanelHeader(terminalOutput: String): Boolean {
        val normalized = cleanOutput(terminalOutput).lowercase()
        return "model quotas" in normalized ||
            "models & quota" in normalized ||
            "quota & credits" in normalized ||
            "quota and credits" in normalized
    }

    /** O texto fica apenas em memória e nunca é incluído em exceções ou logs. */
    fun parse(terminalOutput: String): List<ReportedModelQuota> {
        val cleanText = cleanOutput(terminalOutput)

        if (authenticationRequired.containsMatchIn(cleanText)) {
            throw IllegalStateException("Antigravity CLI authentication is unavailable")
        }

        if (!hasRecognizedPanelHeader(cleanText)) {
            throw IllegalStateException("Antigravity usage panel format is unrecognized")
        }

        val metrics = linkedMapOf<String, MutableQuota>()
        var currentModel: String? = null
        var expectsWeeklyRemainingPercent = false
        var remainingPercentLinesLeft = 0

        cleanText.lineSequence().forEach { rawLine ->
            val line = rawLine.trim().replace(Regex("\\s+"), " ")
            if (line.isEmpty()) return@forEach

            val group = groupHeading.matchEntire(line)
            if (group != null) {
                currentModel = normalizeGroupName(group.groupValues[1])
                metrics.getOrPut(currentModel!!) { MutableQuota(currentModel!!, UsageUnit.PERCENTAGE) }
                expectsWeeklyRemainingPercent = false
                remainingPercentLinesLeft = 0
                return@forEach
            }

            if (currentModel != null && weeklyLimitRemaining.containsMatchIn(line)) {
                val quota = metrics.getOrPut(currentModel!!) { MutableQuota(currentModel!!, UsageUnit.PERCENTAGE) }
                quota.unit = UsageUnit.PERCENTAGE
                val sameLineValue = anyPercent.find(line)?.groupValues?.get(1)?.let(::parsePercent)
                if (sameLineValue != null) {
                    quota.remainingPercent = sameLineValue
                    expectsWeeklyRemainingPercent = false
                    remainingPercentLinesLeft = 0
                } else {
                    expectsWeeklyRemainingPercent = true
                    remainingPercentLinesLeft = 8
                }
                return@forEach
            }

            if (expectsWeeklyRemainingPercent) {
                val percent = anyPercent.find(line)?.groupValues?.get(1)?.let(::parsePercent)
                if (percent != null) {
                    val quota = metrics.getOrPut(currentModel!!) { MutableQuota(currentModel!!, UsageUnit.PERCENTAGE) }
                    quota.unit = UsageUnit.PERCENTAGE
                    quota.remainingPercent = percent
                    expectsWeeklyRemainingPercent = false
                    remainingPercentLinesLeft = 0
                    return@forEach
                }
                remainingPercentLinesLeft -= 1
                if (remainingPercentLinesLeft <= 0 || line.startsWith("refreshes", ignoreCase = true)) {
                    expectsWeeklyRemainingPercent = false
                }
            }

            val ratio = countRatio.matchEntire(line)
            if (ratio != null) {
                val prefix = ratio.groupValues[1].trim().trimEnd(':', '-', '•')
                val model = modelLabel.matchEntire(prefix)?.groupValues?.get(1)?.trim()
                    ?: prefix.takeIf(String::isNotBlank)
                    ?: currentModel
                if (model != null) {
                    val unit = parseUnit(ratio.groupValues[4])
                    val first = parseCount(ratio.groupValues[2])
                    val second = parseCount(ratio.groupValues[3])
                    val quota = metrics.getOrPut(model) { MutableQuota(model, unit) }
                    quota.unit = unit
                    if (ratio.groupValues[5].equals("used", ignoreCase = true)) {
                        quota.used = first
                        quota.limit = second
                    } else {
                        quota.remaining = first
                        quota.limit = second
                    }
                    currentModel = model
                }
                return@forEach
            }

            val usedPct = usedPercent.matchEntire(line)
            if (usedPct != null) {
                val model = usedPct.groupValues[1].trim().trimEnd(':', '-', '•')
                val value = parsePercent(usedPct.groupValues[2])
                if (model.isNotBlank() && value != null) {
                    val quota = metrics.getOrPut(model) { MutableQuota(model, UsageUnit.REQUESTS) }
                    quota.usedPercent = value
                    currentModel = model
                }
                return@forEach
            }

            val remainingPct = remainingPercent.matchEntire(line)
            if (remainingPct != null) {
                val model = remainingPct.groupValues[1].trim().trimEnd(':', '-', '•')
                val value = parsePercent(remainingPct.groupValues[2])
                if (model.isNotBlank() && value != null) {
                    val quota = metrics.getOrPut(model) { MutableQuota(model, UsageUnit.REQUESTS) }
                    quota.remainingPercent = value
                    currentModel = model
                }
                return@forEach
            }

            modelLabel.matchEntire(line)?.let { match ->
                currentModel = match.groupValues[1].trim().takeIf(String::isNotBlank)
                return@forEach
            }

            val model = currentModel ?: return@forEach
            reset.matchEntire(line)?.let { match ->
                val description = match.groupValues[1].trim().takeIf(String::isNotBlank)
                if (description != null) metrics.getOrPut(model) { MutableQuota(model, UsageUnit.REQUESTS) }.resetDescription = description
                return@forEach
            }

            val pct = inlinePercent.matchEntire(line)
            if (pct != null) {
                val value = parsePercent(pct.groupValues[2])
                if (value != null) {
                    val quota = metrics.getOrPut(model) { MutableQuota(model, UsageUnit.REQUESTS) }
                    if (pct.groupValues[1].equals("used", ignoreCase = true)) quota.usedPercent = value
                    else quota.remainingPercent = value
                }
                return@forEach
            }

            val count = inlineCount.matchEntire(line) ?: countSuffix.matchEntire(line)
            if (count != null) {
                val isPrefix = inlineCount.matches(line)
                val field = if (isPrefix) count.groupValues[1] else count.groupValues[3]
                val value = parseCount(if (isPrefix) count.groupValues[2] else count.groupValues[1])
                val unit = parseUnit(if (isPrefix) count.groupValues[3] else count.groupValues[2])
                val quota = metrics.getOrPut(model) { MutableQuota(model, unit) }
                quota.unit = unit
                when (field.lowercase()) {
                    "used" -> quota.used = value
                    "remaining" -> quota.remaining = value
                    "limit" -> quota.limit = value
                }
            }
        }

        val parsed = metrics.values.mapNotNull(MutableQuota::toDomain)
        if (parsed.isEmpty()) {
            throw IllegalStateException("Antigravity usage panel has no recognized quota values")
        }
        return parsed
    }

    private fun cleanOutput(terminalOutput: String): String = terminalOutput
        .replace(ansiStyling, "")
        .replace(ansiEscape, "\n")
        .replace('\r', '\n')
        .map { character -> if (character == '\n' || character == '\t' || character >= ' ') character else ' ' }
        .joinToString("")

    private fun parsePercent(value: String): Double? {
        val parsed = value.replace(',', '.').toDoubleOrNull() ?: return null
        return parsed.takeIf { number -> number.isFinite() && number in 0.0..100.0 }
    }

    private fun normalizeGroupName(value: String): String = when (value.trim().lowercase()) {
        "gemini" -> "Gemini models"
        "claude and gpt" -> "Claude and GPT models"
        else -> value.trim().replaceFirstChar(Char::uppercase)
    }

    private fun parseCount(value: String): Long = value.replace(",", "").toLong()

    private fun parseUnit(value: String): UsageUnit = when {
        value.startsWith("token", ignoreCase = true) -> UsageUnit.TOKENS
        else -> UsageUnit.REQUESTS
    }

    private data class MutableQuota(
        val modelName: String,
        var unit: UsageUnit,
        var used: Long? = null,
        var remaining: Long? = null,
        var limit: Long? = null,
        var usedPercent: Double? = null,
        var remainingPercent: Double? = null,
        var resetDescription: String? = null
    ) {
        fun toDomain(): ReportedModelQuota? {
            if (listOfNotNull(used, remaining, limit).any { value -> value < 0L }) return null
            if (listOfNotNull(usedPercent, remainingPercent).any { value -> !value.isFinite() || value !in 0.0..100.0 }) return null
            if (used == null && remaining == null && limit == null && usedPercent == null && remainingPercent == null) return null
            return ReportedModelQuota(
                modelName = modelName,
                used = used,
                remaining = remaining,
                limit = limit,
                usedPercent = usedPercent,
                remainingPercent = remainingPercent,
                unit = unit,
                resetDescription = resetDescription
            )
        }
    }
}
