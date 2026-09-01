package com.usagemonitor.domain.usecase

import com.usagemonitor.domain.entity.TeamAccountDeletion
import com.usagemonitor.domain.entity.TeamBlockedAccount
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

/** Remove um integrante com o token de administração do servidor. */
class RemoveAdminTeamMemberUseCase(
    private val repository: TeamAdminRepository
) {
    suspend operator fun invoke(accountKey: String, deviceId: String): Result<Unit> =
        repository.removeMember(accountKey = accountKey, deviceId = deviceId)
}

/** Remove uma sessão usando exclusivamente o token administrativo. */
class RemoveAdminTeamSessionUseCase(
    private val repository: TeamAdminRepository
) {
    suspend operator fun invoke(
        accountKey: String,
        deviceId: String,
        sessionId: String
    ): Result<Unit> = repository.removeSession(
        accountKey = accountKey,
        deviceId = deviceId,
        sessionId = sessionId
    )
}

/**
 * Apaga uma conta inteira do servidor. **Irreversível.**
 *
 * É o conserto da conta que a empresa deixou de usar — alguém trocou de conta
 * Anthropic e a antiga ficou na visão global com os integrantes de antes.
 * [UnclaimTeamKeyAccountUseCase] não resolve esse caso: solta o vínculo e deixa
 * os dados, então a conta continua na tela, agora sem rótulo.
 *
 * **Também declara a conta fora do time** (servidor 0.11.0+). Sem isso apagar era
 * gesto sem efeito: envio e presença reivindicam sozinhos, e uma máquina que
 * ainda participasse da conta a recriava na batida seguinte. Para devolvê-la
 * existe [UnblockTeamAccountUseCase].
 */
class DeleteTeamAccountUseCase(
    private val repository: TeamAdminRepository
) {
    suspend operator fun invoke(accountKey: String): Result<TeamAccountDeletion> =
        repository.deleteAccount(accountKey)
}

/** Contas que o administrador declarou fora do time. */
class ListBlockedTeamAccountsUseCase(
    private val repository: TeamAdminRepository
) {
    suspend operator fun invoke(): Result<List<TeamBlockedAccount>> =
        repository.fetchBlockedAccounts()
}

/**
 * Devolve a conta ao time, e responde com a lista restante.
 *
 * Não restaura dado nenhum: o histórico saiu junto do bloqueio e a máquina
 * daquela conta já marcou os turnos como enviados. O que volta é a possibilidade
 * de a conta se vincular de novo.
 */
class UnblockTeamAccountUseCase(
    private val repository: TeamAdminRepository
) {
    suspend operator fun invoke(accountKey: String): Result<List<TeamBlockedAccount>> =
        repository.unblockAccount(accountKey)
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
    suspend operator fun invoke(
        accountKey: String,
        accountEmail: String? = null
    ): Result<TeamKeyVerification> = repository.verifyKeyForAccount(accountKey, accountEmail)
}

/**
 * Amarra a conta à chave configurada, e é o que o "Testar conexão" dispara.
 *
 * Verificar sem vincular deixava o usuário num beco: o botão aprovava a chave e
 * a tela do time recusava a leitura, porque o vínculo só nascia num envio de
 * turnos que numa máquina já sincronizada nunca acontecia.
 */
class ClaimTeamKeyForAccountUseCase(
    private val repository: TeamAdminRepository
) {
    suspend operator fun invoke(
        accountKey: String,
        accountEmail: String? = null
    ): Result<TeamKeyVerification> = repository.claimKeyForAccount(accountKey, accountEmail)
}
