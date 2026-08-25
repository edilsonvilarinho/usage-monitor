package com.usagemonitor.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubReleaseDto(
    @SerialName("tag_name")
    val tagName: String,
    @SerialName("html_url")
    val htmlUrl: String,
    val assets: List<GitHubReleaseAssetDto> = emptyList()
)

@Serializable
data class GitHubReleaseAssetDto(
    val name: String,
    @SerialName("browser_download_url")
    val browserDownloadUrl: String,
    /**
     * Tamanho em bytes. **Nulo é "não informado", nunca zero**: zero afirmaria
     * um artefato vazio e reprovaria todo download. Campo que a API sempre manda
     * hoje, mas derrubar a verificação de atualização inteira porque ele sumiu
     * seria trocar uma conferência acessória pela funcionalidade toda — quem
     * barra artefato trocado é o [digest].
     */
    val size: Long? = null,
    /**
     * Checksum no formato `sha256:<hex>`, conferido nos sete assets do v37.0.0.
     * É o portão de integridade do download: a allowlist de host só valida a URL
     * inicial, e o GitHub redireciona os downloads para `objects.githubusercontent.com`.
     * Asset sem digest não é elegível para atualização automática.
     */
    val digest: String? = null
)
