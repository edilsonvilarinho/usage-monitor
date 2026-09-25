package com.usagemonitor.data.datasource

import com.usagemonitor.domain.entity.ACTIVE_SESSION_WINDOW_MILLIS
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.sqlite.SQLiteConfig

/**
 * O Codex está trabalhando agora? Acende o arco de sessão ativa do anel do Codex
 * na HUD, que até aqui só olhava o índice de sessões do Claude CLI.
 *
 * **Dois sinais, na ordem do Codenotch** (`activity.rs`):
 * 1. **O app desktop** guarda o estado real dos turnos em
 *    `thread_history_1.sqlite`: `thread_turns.status = 'inProgress'`. Medido nesta
 *    máquina: com um turno rodando havia sete minutos, o rollout não era escrito
 *    desde o início do turno — a data de modificação do arquivo sozinha diria "parado"
 *    no meio da execução. É por isso que a tabela vem primeiro.
 * 2. **O CLI e a extensão** não passam pela tabela, e para eles o rollout
 *    escrito nos últimos 5 minutos ([ACTIVE_SESSION_WINDOW_MILLIS], o mesmo corte
 *    do Claude) é o sinal.
 *
 * Só leitura, nunca cria nem migra o banco. Só metadados: nenhuma coluna de
 * conteúdo é lida.
 */
class LocalCodexActivityDataSource(
    private val codexHome: File,
    private val zone: ZoneId = ZoneId.systemDefault()
) {
    fun isActive(nowMillis: Long): Boolean {
        return hasLiveDesktopTurn(nowMillis) || hasRecentRollout(nowMillis)
    }

    private fun hasLiveDesktopTurn(nowMillis: Long): Boolean {
        val database = File(codexHome, THREAD_HISTORY_DB)
        if (!database.isFile) return false
        return openReadOnly(database).use { connection ->
            connection.prepareStatement(SELECT_IN_PROGRESS_TURNS).use { statement ->
                statement.executeQuery().use { rows ->
                    var live = false
                    while (!live && rows.next()) {
                        val startedAt = rows.getLong(1)
                        val lastItem = rows.getLong(2).takeUnless { rows.wasNull() }
                        live = isCodexTurnLive(startedAt, lastItem, nowMillis)
                    }
                    live
                }
            }
        }
    }

    /**
     * Só as pastas de hoje e de ontem: o Codex grava `sessions/AAAA/MM/DD` pela data
     * local de início, e varrer o histórico inteiro a cada 30s seria ler milhares de
     * arquivos para achar um de cinco minutos atrás.
     */
    private fun hasRecentRollout(nowMillis: Long): Boolean {
        val sessions = File(codexHome, "sessions")
        if (!sessions.isDirectory) return false
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val cutoff = nowMillis - ACTIVE_SESSION_WINDOW_MILLIS
        return listOf(today, today.minusDays(1)).any { date ->
            dayDirectory(sessions, date).listFiles()?.any { file ->
                file.isFile && file.name.startsWith("rollout-") && file.name.endsWith(".jsonl") &&
                    file.lastModified() >= cutoff
            } ?: false
        }
    }

    private fun dayDirectory(sessions: File, date: LocalDate): File {
        return File(sessions, "%04d/%02d/%02d".format(date.year, date.monthValue, date.dayOfMonth))
    }

    private fun openReadOnly(database: File): Connection {
        val config = SQLiteConfig()
        config.setReadOnly(true)
        config.setBusyTimeout(BUSY_TIMEOUT_MILLIS)
        return DriverManager.getConnection("jdbc:sqlite:${database.absolutePath}", config.toProperties())
    }

    private companion object {
        const val THREAD_HISTORY_DB = "thread_history_1.sqlite"
        const val BUSY_TIMEOUT_MILLIS = 200

        /** O último item de cada turno em andamento; o conteúdo do item não é lido. */
        const val SELECT_IN_PROGRESS_TURNS = """
            SELECT t.started_at,
                   (SELECT MAX(i.created_at_ms) FROM thread_items i WHERE i.thread_id = t.thread_id)
            FROM thread_turns t
            WHERE t.status = 'inProgress'
            ORDER BY t.started_at DESC
            LIMIT 8
        """
    }
}

/**
 * Um turno `inProgress` está vivo? A guarda do Codenotch contra o turno que ficou
 * "em andamento" para sempre depois de uma queda do app: vivo se teve item nos
 * últimos 10 minutos, ou se começou há menos de 2 — um turno recém-aberto ainda
 * não escreveu item nenhum.
 *
 * [startedAt] vem em segundos nas versões medidas e em milissegundos em outras; o
 * tamanho do número diz qual.
 */
internal fun isCodexTurnLive(startedAt: Long, lastItemMillis: Long?, nowMillis: Long): Boolean {
    val startedMillis = if (startedAt > SECONDS_VS_MILLIS_PIVOT) startedAt else startedAt * 1_000
    val last = lastItemMillis ?: startedMillis
    return nowMillis - last <= CODEX_TURN_QUIET_MILLIS || nowMillis - startedMillis <= CODEX_TURN_FRESH_MILLIS
}

/** Acima disto o número já é milissegundo: 10¹⁰ segundos é o ano 2286. */
private const val SECONDS_VS_MILLIS_PIVOT = 10_000_000_000L
private const val CODEX_TURN_QUIET_MILLIS = 10 * 60_000L
private const val CODEX_TURN_FRESH_MILLIS = 2 * 60_000L
