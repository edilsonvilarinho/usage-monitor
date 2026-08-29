package com.usagemonitor.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.russhwolf.settings.PreferencesSettings
import com.usagemonitor.CURRENT_APP_VERSION
import com.usagemonitor.domain.entity.AppUpdateReceipt
import com.usagemonitor.domain.entity.ReleaseNotes
import com.usagemonitor.domain.entity.ReleaseNotesDecision
import com.usagemonitor.domain.entity.releaseNotesDecision
import com.usagemonitor.domain.entity.releaseNotesPreviousVersion
import com.usagemonitor.domain.usecase.GetReleaseNotesUseCase
import com.usagemonitor.persistReleaseNotesSeenVersion
import com.usagemonitor.readPersistedReleaseNotesSeenVersion

/**
 * Decide, busca e lembra: tudo que a janela de novidades precisa.
 *
 * Classe própria pelo mesmo motivo do [AutoUpdateController]: o `main()` está no
 * limite do backend JVM e recebe **uma** chamada, não estado novo.
 */
internal class ReleaseNotesController(
    /** Notas a mostrar. `null` enquanto não há o que mostrar — que é o caso normal. */
    val notes: ReleaseNotes?,
    private val dismiss: () -> Unit
) {
    fun onDismiss() = dismiss()
}

@Composable
internal fun rememberReleaseNotesController(
    settings: PreferencesSettings,
    getReleaseNotes: GetReleaseNotesUseCase,
    /**
     * Recibo do instalador. Serve a **duas** coisas e a nenhuma terceira: dizer
     * de onde se veio, no subtítulo, e provar que esta máquina já atualizou
     * alguma vez, o que separa instalação nova de marca ausente por defeito.
     *
     * **Não decide o conteúdo da janela.** Ele foi a condição principal e era
     * por isso que a janela não aparecia no Linux, em instalação manual nem no
     * macOS (issue #127) — quem remover este parâmetro como resto do gatilho
     * antigo tira o subtítulo exato do caminho automático do Windows.
     */
    receipt: AppUpdateReceipt?,
    currentVersion: String = CURRENT_APP_VERSION
): ReleaseNotesController {
    var notes by remember(currentVersion) { mutableStateOf<ReleaseNotes?>(null) }

    // O recibo continua na chave do efeito porque ele ainda entra na decisão
    // pela sua existência e no subtítulo pelo seu conteúdo — não porque ele
    // decida se há novidade a mostrar.
    LaunchedEffect(receipt, currentVersion) {
        val seenVersion = readPersistedReleaseNotesSeenVersion(settings)
        // `when` como expressão: valor novo no enum tem de virar erro de
        // compilação aqui, que é o único ponto que trata os três desfechos.
        val shouldFetch = when (releaseNotesDecision(currentVersion, seenVersion, receipt != null)) {
            ReleaseNotesDecision.SKIP -> false

            // Silêncio COM marca, e sem requisição: pedir ao GitHub a release de
            // uma versão que não vamos anunciar é rede gasta por nada, e sem
            // gravar a marca a abertura seguinte refaria a mesma conta para
            // sempre.
            ReleaseNotesDecision.MARK_SEEN_ONLY -> {
                persistReleaseNotesSeenVersion(settings, currentVersion)
                false
            }

            ReleaseNotesDecision.SHOW -> true
        }

        if (!shouldFetch) {
            return@LaunchedEffect
        }

        val result = getReleaseNotes(
            currentVersion,
            releaseNotesPreviousVersion(receipt, currentVersion, seenVersion)
        )

        // Os três desfechos da BUSCA não são o mesmo, e é por isso que a marca
        // não é gravada num lugar só:
        //
        // - notas com itens: a janela abre, e a versão é marcada AO ABRIR. Marcar
        //   ao fechar perderia a marca num encerramento anormal, e a janela
        //   voltaria numa abertura em que o usuário já a viu.
        // - release sem nada a mostrar (só chore/docs): não há janela, mas a
        //   versão é marcada — retentar a cada abertura seria uma requisição
        //   perpétua por uma resposta que não vai mudar.
        // - falha de rede: NÃO marca. A espera acabou sem resposta, e a abertura
        //   seguinte tenta de novo.
        val fetched = result.getOrElse { return@LaunchedEffect }

        persistReleaseNotesSeenVersion(settings, currentVersion)
        notes = fetched
    }

    return remember(notes) {
        ReleaseNotesController(
            notes = notes,
            dismiss = { notes = null }
        )
    }
}
