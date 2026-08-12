package com.usagemonitor.domain.usecase

import com.usagemonitor.domain.entity.TeamKeyEntry
import com.usagemonitor.domain.entity.TeamKeyVerification
import com.usagemonitor.domain.repository.TeamAdminRepository

/**
 * Casos de uso da administração de chaves de time.
 *
 * Ficam juntos num arquivo por serem operações da mesma tela e do mesmo
 * contrato: cada um continua sendo uma classe com `invoke`, como o resto do
 * domain, mas seis arquivos de cinco linhas só afastariam o que se lê junto.
 */

/** Confere o token de administração. É o que o botão "Validar" chama. */
class ValidateAdminTokenUseCase(
    private val repository: TeamAdminRepository
) {
    suspend operator fun invoke(): Result<Unit> = repository.validateToken()
}

class ListTeamKeysUseCase(
    private val repository: TeamAdminRepository
) {
    suspend operator fun invoke(): Result<List<TeamKeyEntry>> = repository.listKeys()
}

/**
 * Emite uma chave nova, ainda sem conta.
 *
 * O vínculo com a conta nasce no primeiro envio daquela chave — é o que permite
 * emitir sem antes descobrir o `accountUuid` de ninguém.
 */
class CreateTeamKeyUseCase(
    private val repository: TeamAdminRepository
) {
    suspend operator fun invoke(label: String, maxAccounts: Int = 1): Result<TeamKeyEntry> =
        repository.createKey(label = label.trim(), maxAccounts = maxAccounts)
}

class UpdateTeamKeyUseCase(
    private val repository: TeamAdminRepository
) {
    suspend operator fun invoke(
        id: String,
        label: String? = null,
        maxAccounts: Int? = null
    ): Result<TeamKeyEntry> = repository.updateKey(
        id = id,
        label = label?.trim(),
        maxAccounts = maxAccounts
    )
}

/**
 * Troca a chave crua mantendo os vínculos.
 *
 * Serve a chave perdida e a chave vazada; nos dois casos o time já formado não
 * pode se desfazer. A chave antiga para de valer na requisição seguinte, então
 * quem a estiver usando precisa receber a nova.
 */
class RegenerateTeamKeyUseCase(
    private val repository: TeamAdminRepository
) {
    suspend operator fun invoke(id: String): Result<TeamKeyEntry> = repository.regenerateKey(id)
}

/**
 * Revoga o acesso de uma chave. **Não** apaga o histórico já enviado.
 *
 * Apagar dados é outra decisão, e continua sendo a remoção de integrante.
 */
class RevokeTeamKeyUseCase(
    private val repository: TeamAdminRepository
) {
    suspend operator fun invoke(id: String): Result<TeamKeyEntry> = repository.revokeKey(id)
}

/**
 * Desfaz um vínculo criado no envio errado.
 *
 * Enquanto ele existir, nenhuma outra chave consegue adotar aquela conta: uma
 * conta pertence a no máximo uma chave.
 */
class UnclaimTeamKeyAccountUseCase(
    private val repository: TeamAdminRepository
) {
    suspend operator fun invoke(id: String, accountKey: String): Result<TeamKeyEntry> =
        repository.unclaimAccount(id = id, accountKey = accountKey)
}

/**
 * Pergunta ao servidor se a chave de time configurada serve para uma conta.
 *
 * É o que substitui a consulta a uma conta inventada no "Testar conexão": com
 * autorização por conta, aquela consulta passaria a ser recusada e o botão
 * reprovaria uma configuração correta.
 */
class VerifyTeamKeyForAccountUseCase(
    private val repository: TeamAdminRepository
) {
    suspend operator fun invoke(accountKey: String): Result<TeamKeyVerification> =
        repository.verifyKeyForAccount(accountKey)
}
