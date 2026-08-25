package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.AppUpdateInfo

/**
 * Os quatro estados da faixa de atualização.
 *
 * `sealed interface` e **não** enum de propósito: acrescentar um estado aqui é
 * erro de compilação em `updateBannerContent`, e portanto visível. Um valor novo
 * num enum passaria pelo `when` sem ninguém notar que a tela não sabe desenhá-lo.
 */
sealed interface AppUpdateUiState {

    val update: AppUpdateInfo

    /**
     * Versão nova anunciada e nada baixado.
     *
     * É o estado de quem está com a atualização automática desligada — e também
     * o primeiro instante de quem está com ela ligada.
     */
    data class Available(override val update: AppUpdateInfo) : AppUpdateUiState

    /**
     * Baixando em segundo plano.
     *
     * [percent] é nulo quando a release não informou o tamanho do asset: sem
     * total não há porcentagem, e inventar uma seria pior que dizer só
     * "baixando".
     */
    data class Downloading(
        override val update: AppUpdateInfo,
        val percent: Int?
    ) : AppUpdateUiState

    /** Baixada e conferida. Será aplicada ao fechar o app. */
    data class Ready(override val update: AppUpdateInfo) : AppUpdateUiState

    /**
     * A tentativa falhou. A versão instalada continua intacta — a faixa oferece
     * o caminho manual, que é o comportamento que o app sempre teve.
     */
    data class Failed(
        override val update: AppUpdateInfo,
        val reason: AppUpdateFailureReason
    ) : AppUpdateUiState
}

/**
 * Por que falhou, em vocabulário de tela.
 *
 * A mensagem da exceção não chega à interface: ela é técnica, está em inglês e
 * vem de camadas que não sabem quem vai ler. O motivo é traduzido em PT/EN junto
 * com o resto dos textos.
 */
enum class AppUpdateFailureReason {
    /** Rede, checksum ou tamanho: o pacote não chegou íntegro. */
    DOWNLOAD,

    /** O pacote está no disco, mas o instalador não pôde ser iniciado. */
    SCHEDULE
}
