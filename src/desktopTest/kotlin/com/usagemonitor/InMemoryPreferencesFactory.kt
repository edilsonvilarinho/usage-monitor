package com.usagemonitor

import java.util.concurrent.ConcurrentHashMap
import java.util.prefs.AbstractPreferences
import java.util.prefs.Preferences
import java.util.prefs.PreferencesFactory

/**
 * `java.util.prefs` só para os testes, em memória.
 *
 * No Linux o `FileSystemPreferences` grava um arquivo por nó e disputa uma trava por usuário:
 * os forks paralelos (`-PtestForks=3`) batem nela e `removeNode` falha com
 * `BackingStoreException: Couldn't get file lock`. Aqui não há arquivo nem trava. O `build.gradle.kts`
 * registra esta fábrica no `desktopTest` só fora do Windows; no Windows o registro segue como antes.
 */
class InMemoryPreferencesFactory : PreferencesFactory {
    private val userRootNode by lazy { InMemoryPreferences(null, "") }
    private val systemRootNode by lazy { InMemoryPreferences(null, "") }

    override fun userRoot(): Preferences = userRootNode

    override fun systemRoot(): Preferences = systemRootNode
}

private class InMemoryPreferences(
    private val parentNode: InMemoryPreferences?,
    private val nodeName: String
) : AbstractPreferences(parentNode, nodeName) {
    private val values = ConcurrentHashMap<String, String>()
    private val children = ConcurrentHashMap<String, InMemoryPreferences>()

    override fun putSpi(key: String, value: String) {
        values[key] = value
    }

    override fun getSpi(key: String): String? = values[key]

    override fun removeSpi(key: String) {
        values.remove(key)
    }

    override fun removeNodeSpi() {
        parentNode?.children?.remove(nodeName)
        values.clear()
        children.clear()
    }

    override fun keysSpi(): Array<String> = values.keys.toTypedArray()

    override fun childrenNamesSpi(): Array<String> = children.keys.toTypedArray()

    override fun childSpi(name: String): AbstractPreferences {
        return children.computeIfAbsent(name) { childName -> InMemoryPreferences(this, childName) }
    }

    // Nada sai da memória, então não há o que descarregar nem sincronizar.
    override fun flushSpi() = Unit

    override fun syncSpi() = Unit
}
