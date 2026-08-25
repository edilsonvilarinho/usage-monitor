package com.usagemonitor.domain.repository

import com.usagemonitor.domain.entity.AppUpdateInfo

/**
 * Aplica uma atualização já anunciada por [AppUpdateRepository].
 *
 * O contrato é de duas fases porque as duas acontecem em momentos diferentes da
 * vida do app: [prepare] baixa e verifica enquanto o app roda, e [schedule]
 * entrega o pacote ao sistema operacional no encerramento. Uma fase só obrigaria
 * a baixar 120 MB no caminho de saída, que é justamente onde não há tela para
 * mostrar progresso nem tempo para esperar.
 *
 * O domain não conhece plataforma: quem responde [support] é a implementação.
 */
interface AppUpdateInstaller {

    /**
     * Se esta instalação pode se atualizar sozinha, e por que não quando não
     * pode. A resposta vira texto na tela — um interruptor desabilitado sem
     * motivo é pior que interruptor nenhum.
     */
    fun support(): AppUpdateSupport

    /**
     * Baixa e verifica o artefato da [update]. Idempotente por versão: chamada
     * repetida com um arquivo já baixado e íntegro não repete a rede.
     *
     * [onProgress] recebe bytes baixados e o total conhecido — `null` quando a
     * release não informou o tamanho.
     */
    suspend fun prepare(
        update: AppUpdateInfo,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> }
    ): Result<AppUpdatePreparation>

    /**
     * Entrega o pacote preparado ao sistema operacional e devolve o controle
     * imediatamente. Chamada no encerramento do app; **não** espera a
     * atualização terminar, porque o processo que espera é o que precisa morrer
     * para que os arquivos possam ser trocados.
     */
    fun schedule(preparation: AppUpdatePreparation): Result<Unit>
}

/**
 * Artefato baixado e conferido, pronto para ser entregue ao sistema.
 *
 * Carrega o nome do asset e o tamanho para a tela poder dizer o que vai
 * aplicar; o caminho no disco fica com a implementação, porque é ela quem sabe
 * o que fazer com ele.
 */
data class AppUpdatePreparation(
    val version: String,
    val assetName: String,
    val sizeBytes: Long?
)

enum class AppUpdateSupport {
    /** A instalação pode se atualizar sozinha. */
    SUPPORTED,

    /**
     * Linux e macOS. Só o Windows tem instalação per-user sem UAC neste projeto;
     * `.deb`/`.rpm` pedem gerenciador de pacotes e o `.dmg` não é assinado.
     */
    UNSUPPORTED_PLATFORM,

    /**
     * Windows instalado pelo MSI ou fora do instalador. Atualizar um MSI com o
     * instalador NSIS criaria cópia paralela e deixaria um registro Windows
     * Installer capaz de remover arquivos da versão nova.
     */
    UNSUPPORTED_INSTALL_ORIGIN,

    /**
     * Esta build não traz o mecanismo. Estado do PR 1, em que o interruptor
     * existe e nenhum caminho de código lança instalador.
     */
    UNAVAILABLE
}
