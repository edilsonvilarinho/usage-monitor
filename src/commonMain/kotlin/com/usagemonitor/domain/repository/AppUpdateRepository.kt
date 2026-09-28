package com.usagemonitor.domain.repository

import com.usagemonitor.domain.entity.AppUpdateInfo
import com.usagemonitor.domain.entity.ReleaseNotes

/**
 * Nome da variável de ambiente que substitui o feed de releases.
 *
 * Existe para o smoke test da atualização automática: sem ela, cada tentativa
 * exigiria publicar uma release de verdade no GitHub. **A UI mostra um aviso
 * quando ela está ativa** — quem esquecer de desligá-la precisa esbarrar nisso,
 * porque o SHA-256 que barra artefato trocado vem do mesmo feed.
 *
 * No domain, e não na implementação, porque a tela de Configurações cita o nome
 * no aviso e a apresentação não pode importar `data`.
 */
const val UPDATE_FEED_URL_ENV_VAR = "USAGE_MONITOR_UPDATE_FEED_URL"

interface AppUpdateRepository {
    /**
     * A maior versão publicada acima de [currentVersion], ou `null` se não há.
     *
     * [includePrereleases] é o canal beta (issue #355): desligado, só a release
     * estável mais recente conta; ligado, betas e estáveis concorrem pela
     * precedência do SemVer. Nunca oferece versão **menor** que a atual — quem
     * desliga o canal estando numa beta fica nela até sair uma estável maior.
     */
    suspend fun getLatestAvailableUpdate(
        currentVersion: String,
        includePrereleases: Boolean = false
    ): Result<AppUpdateInfo?>

    /**
     * Notas da release de [version], já filtradas para o que o usuário percebe.
     *
     * Sucesso com `null` é **nada a mostrar**, e cobre dois casos que dão na
     * mesma para quem chama: release cujos commits são todos internos, e release
     * que **não existe** — a tag ainda não foi publicada. Sucesso com lista
     * vazia não existe.
     *
     * Falha é falha de rede, e a distinção importa: quem falha tenta de novo na
     * abertura seguinte, em vez de marcar a versão como já vista. Tag inexistente
     * não é falha justamente por isso — a resposta é definitiva e retentar não a
     * mudaria, então retentar a cada abertura seria requisição perpétua.
     */
    suspend fun getReleaseNotes(version: String, previousVersion: String?): Result<ReleaseNotes?>
}
