package com.usagemonitor.data.datasource

import java.io.File
import java.sql.Connection
import java.sql.DriverManager

internal class SqliteConnectionManager(
    private val databaseFile: File,
    private val onOpen: (Connection) -> Unit = {}
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

        databaseFile.parentFile?.mkdirs()
        return DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}")
            .also { openedConnection ->
                onOpen(openedConnection)
                connection = openedConnection
            }
    }
}
