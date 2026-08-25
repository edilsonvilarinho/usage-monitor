package com.usagemonitor

import com.russhwolf.settings.PreferencesSettings

private const val RELEASE_NOTES_SEEN_VERSION_KEY = "releaseNotesSeenVersion"

/**
 * Última versão cujas novidades já foram mostradas.
 *
 * É a marca que impede a janela de reabrir a cada inicialização: o recibo da
 * atualização só é sobrescrito na atualização **seguinte**, então ele sobrevive
 * a todas as aberturas até lá e sozinho não distingue a primeira das demais.
 *
 * Guarda a versão e não um booleano porque a pergunta é "já vi **esta**
 * versão?" — um booleano precisaria ser limpo por quem escreve o recibo, que é
 * o instalador NSIS, e ele não conhece as preferências do app.
 *
 * Vai em `PreferencesSettings`, no mesmo armazenamento das demais preferências,
 * e não em `~/.usage-monitor/`: ali moram os segredos do time, e um número de
 * versão não é segredo.
 */
internal fun readPersistedReleaseNotesSeenVersion(settings: PreferencesSettings): String? {
    return settings.getStringOrNull(RELEASE_NOTES_SEEN_VERSION_KEY)?.takeIf { it.isNotBlank() }
}

internal fun persistReleaseNotesSeenVersion(settings: PreferencesSettings, version: String) {
    settings.putString(RELEASE_NOTES_SEEN_VERSION_KEY, version)
}
