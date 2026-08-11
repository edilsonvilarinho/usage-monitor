package com.usagemonitor.domain.entity

/**
 * Configuração da integração com o servidor de time.
 *
 * O time é definido pela **conta Anthropic**, não por um identificador digitado:
 * o agrupamento usa o `accountUuid` que o app já lê de `~/.claude.json`. O que o
 * usuário informa aqui é só como ele quer ser identificado ([alias]) e onde fica
 * o servidor da empresa.
 *
 * [participatingProfileIds] existe porque a mesma máquina costuma estar logada em
 * várias contas ao mesmo tempo, e nem toda conta é da empresa: só os perfis
 * marcados aqui empurram dados e exibem o botão de time no card.
 */
data class TeamIntegrationSettings(
    val enabled: Boolean = false,
    val serverUrl: String = "",
    val apiKey: String = "",
    val alias: String = "",
    /** UUID gerado uma vez por instalação; separa duas máquinas do mesmo alias. */
    val deviceId: String = "",
    val participatingProfileIds: Set<String> = emptySet()
) {
    /** Sem a barra final, para concatenar o caminho sem gerar `//`. */
    val normalizedServerUrl: String
        get() = serverUrl.trim().trimEnd('/')

    val isConfigured: Boolean
        get() = normalizedServerUrl.isNotEmpty() &&
            apiKey.isNotBlank() &&
            alias.isNotBlank() &&
            deviceId.isNotBlank()

    /** Ligada e com tudo o que a chamada de rede precisa. */
    val isActive: Boolean
        get() = enabled && isConfigured

    /** `true` quando o perfil participa do time e a integração está utilizável. */
    fun participates(profileId: String?): Boolean {
        if (!isActive || profileId == null) {
            return false
        }
        return profileId in participatingProfileIds
    }
}
