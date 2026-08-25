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
import com.usagemonitor.domain.entity.shouldShowReleaseNotes
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
    receipt: AppUpdateReceipt?,
    currentVersion: String = CURRENT_APP_VERSION
): ReleaseNotesController {
    var notes by remember(currentVersion) { mutableStateOf<ReleaseNotes?>(null) }

    LaunchedEffect(receipt, currentVersion) {
        if (!shouldShowReleaseNotes(receipt, currentVersion, readPersistedReleaseNotesSeenVersion(settings))) {
            return@LaunchedEffect
        }

        val result = getReleaseNotes(currentVersion, receipt?.previousVersion)

        // Os três desfechos não são o mesmo, e é por isso que a marca não é
        // gravada num lugar só:
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
