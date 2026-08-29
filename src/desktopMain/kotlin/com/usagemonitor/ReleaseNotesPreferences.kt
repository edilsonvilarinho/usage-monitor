package com.usagemonitor

import com.russhwolf.settings.PreferencesSettings

private const val RELEASE_NOTES_SEEN_VERSION_KEY = "releaseNotesSeenVersion"

/**
 * Última versão cujas novidades já foram mostradas.
 *
 * **É o gatilho inteiro da janela**, e não um desempate do recibo do
 * instalador: a janela abre quando a versão em execução difere desta marca. O
 * recibo deixou de ser condição porque ele não existe em instalação manual nem
 * no macOS, e no Linux ele é escrito depois de o app novo já ter subido
 * (issue #127) — ver `releaseNotesDecision`.
 *
 * Guarda a versão e não um booleano porque a pergunta é "já vi **esta**
 * versão?" — um booleano teria de ser limpo por alguém a cada troca de versão, e
 * o único candidato seria o instalador, que não conhece as preferências do app.
 *
 * É gravada também nos casos em que a janela **não** abre — instalação nova,
 * retrocesso e release sem nada a mostrar —, senão a abertura seguinte refaria a
 * mesma conta indefinidamente.
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
