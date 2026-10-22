package com.usagemonitor.data.datasource

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import org.sqlite.SQLiteConfig

/**
 * Uma conexão SQLite por arquivo, aberta na primeira vez que alguém usa.
 *
 * [readOnly] (#441) é a conexão de quem só lê um banco que outro processo escreve (o modo
 * interativo do terminal com o app ou o serviço coletando): abre em somente leitura, não
 * cria o arquivo nem a pasta e **não roda [onOpen]** (a criação e a migração do schema
 * disputariam o arquivo com o coletor, talvez de outra versão). Banco que ainda não existe é
 * falha de leitura, nunca um arquivo novo. Num banco em WAL o próprio SQLite cria o `-wal`
 * (vazio) e o `-shm` para ler, mesmo em somente leitura; eles saem restritos ao dono, como
 * os do coletor.
 */
internal class SqliteConnectionManager(
    private val databaseFile: File,
    private val onOpen: (Connection) -> Unit = {},
    private val readOnly: Boolean = false
) : AutoCloseable {

    private val lock = Any()
    private var connection: Connection? = null

    fun <T> useConnection(block: (Connection) -> T): T {
        synchronized(lock) {
            val activeConnection = openConnectionIfNeeded()
            return block(activeConnection)
        }
    }

    override fun close() {
        synchronized(lock) {
            runCatching { connection?.close() }
            connection = null
        }
    }

    private fun openConnectionIfNeeded(): Connection {
        val cachedConnection = connection
        if (cachedConnection != null && !cachedConnection.isClosed) {
            return cachedConnection
        }

        if (readOnly) {
            check(databaseFile.isFile) { "O banco ${databaseFile.name} ainda não existe." }
            val config = SQLiteConfig()
            config.setReadOnly(true)
            // O checkpoint do coletor segura o arquivo por um instante: espera em vez de falhar.
            config.setBusyTimeout(READ_ONLY_BUSY_TIMEOUT_MILLIS)
            return DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}", config.toProperties())
                .also { openedConnection ->
                    connection = openedConnection
                    restrictDatabaseFiles()
                }
        }
        databaseFile.parentFile?.mkdirs()
        return DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}")
            .also { openedConnection ->
                onOpen(openedConnection)
                connection = openedConnection
                restrictDatabaseFiles()
            }
    }

    /**
     * Restringe o `.db` principal e os sidecars `-wal`/`-shm` do modo WAL
     * (ligado em `LocalCliSessionDataSource`). O `onOpen` já escreveu dados reais
     * neles antes deste ponto — sem isso, os sidecars ficavam com a permissão
     * padrão do SO indefinidamente, mesmo com o `.db` principal restrito.
     */
    private fun restrictDatabaseFiles() {
        restrictToOwnerReadWrite(databaseFile.toPath())
        restrictToOwnerReadWrite(File("${databaseFile.absolutePath}-wal").toPath())
        restrictToOwnerReadWrite(File("${databaseFile.absolutePath}-shm").toPath())
    }

    private companion object {
        const val READ_ONLY_BUSY_TIMEOUT_MILLIS = 5_000
    }
}
