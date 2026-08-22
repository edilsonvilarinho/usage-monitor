package com.usagemonitor.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import com.russhwolf.settings.PreferencesSettings
import com.usagemonitor.data.repository.UPDATE_FEED_URL_ENV_VAR
import com.usagemonitor.domain.entity.AppUpdateReceipt
import com.usagemonitor.domain.repository.AppUpdateInstaller
import com.usagemonitor.domain.repository.AppUpdateSupport
import com.usagemonitor.persistAutoUpdateEnabled
import com.usagemonitor.readPersistedAutoUpdateEnabled
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Se **esta build** traz o mecanismo de atualização automática.
 *
 * Falso enquanto o instalador NSIS não entender `/UPDATE` (atividade A16 do
 * plano). Com ele falso, `installer` é nulo e nenhum caminho de código lança
 * instalador nenhum — a decisão 8 do plano deixa de poder virar acidente.
 *
 * A atividade A19 vira este valor **junto** com [MIN_UPDATABLE_TARGET_VERSION],
 * e há um teste que reprova a combinação inconsistente.
 */
internal const val AUTO_UPDATE_SHIPPED = false

/**
 * Tudo que a janela principal precisa saber sobre atualização automática.
 *
 * Existe como classe própria, e não como mais um punhado de `remember` dentro do
 * `main()`, porque aquele composable já está no limite do backend JVM: a análise
 * de fluxo de controle sobre o método inteiro estoura em `OutOfMemoryError`
 * dentro do ASM. O `main()` ganha **uma** chamada.
 */
internal class AutoUpdateController(
    val installer: AppUpdateInstaller?,
    val enabled: MutableStateFlow<Boolean>,
    val lastReceipt: AppUpdateReceipt?,
    /** Valor de USAGE_MONITOR_UPDATE_FEED_URL, quando definida. */
    val feedUrlOverride: String?,
    private val persist: (Boolean) -> Unit
) {

    val support: AppUpdateSupport
        get() = installer?.support() ?: AppUpdateSupport.UNAVAILABLE

    fun setEnabled(value: Boolean) {
        enabled.value = value
        persist(value)
    }

    /**
     * Encerramento ordenado, ligado depois da construção.
     *
     * A indireção existe porque o `DashboardViewModel` é criado antes de o
     * `main()` montar a rotina de saída, e a alternativa seria mais um estado
     * mutável dentro do composable que não pode crescer.
     */
    private var restartAction: () -> Unit = {}

    fun bindRestart(action: () -> Unit) {
        restartAction = action
    }

    fun requestRestart() {
        restartAction()
    }
}

/**
 * Valor corrente do interruptor, como estado de composição.
 *
 * Função de extensão em vez de um `collectAsState` no `main()`: ali não pode
 * entrar mais estado, e uma chamada não é estado.
 */
@Composable
internal fun AutoUpdateController.isEnabled(): Boolean = enabled.collectAsState().value

@Composable
internal fun rememberAutoUpdateController(
    settings: PreferencesSettings,
    httpClient: HttpClient
): AutoUpdateController {
    return remember(settings, httpClient) {
        val installer = if (AUTO_UPDATE_SHIPPED) {
            WindowsAppUpdateInstaller(httpClient = httpClient)
        } else {
            null
        }
        AutoUpdateController(
            installer = installer,
            enabled = MutableStateFlow(readPersistedAutoUpdateEnabled(settings)),
            // Lido uma vez, na abertura: o recibo é escrito pelo instalador
            // enquanto o app está fechado, e reler a cada recomposição seria
            // I/O de disco para um valor que não muda com a janela aberta.
            lastReceipt = readUpdateReceipt(),
            feedUrlOverride = System.getenv(UPDATE_FEED_URL_ENV_VAR),
            persist = { value -> persistAutoUpdateEnabled(settings, value) }
        )
    }
}
