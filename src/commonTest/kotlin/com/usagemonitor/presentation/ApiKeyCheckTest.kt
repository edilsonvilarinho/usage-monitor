package com.usagemonitor.presentation

import com.usagemonitor.data.repository.OPEN_CODE_GO_NO_SUBSCRIPTION_MESSAGE
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.components.ApiKeyCheckStatus
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.apiKeyCheckResult
import java.net.ConnectException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApiKeyCheckTest {

    @Test
    fun `sem erro a chave e valida`() {
        val result = apiKeyCheckResult(ApiSource.OPENROUTER, error = null, language = AppLanguage.PT)

        assertEquals(ApiKeyCheckStatus.OK, result.status)
        assertEquals(AppTone.OK, result.tone)
        assertEquals("Chave válida.", result.message)
    }

    @Test
    fun `falha de conectividade orienta sobre rede e nao sobre credencial`() {
        // Classificada por TIPO da exceção: o texto de `ConnectException` varia
        // por JVM e por sistema, e amarrar o teste a ele seria testar o JDK.
        val result = apiKeyCheckResult(
            ApiSource.DEEPSEEK,
            error = ConnectException("Connection refused: connect"),
            language = AppLanguage.PT
        )

        assertEquals(ApiKeyCheckStatus.FAILED, result.status)
        assertEquals(AppTone.CRITICAL, result.tone)
        assertTrue(result.message.orEmpty().contains("Sem conexão"), result.message.orEmpty())
        assertTrue(result.message.orEmpty().contains("Rede"), result.message.orEmpty())
        assertFalse(result.message.orEmpty().contains("chave", ignoreCase = true), result.message.orEmpty())
    }

    @Test
    fun `dns que nao resolve tambem e conectividade`() {
        val result = apiKeyCheckResult(
            ApiSource.MINIMAX,
            error = UnknownHostException("api.minimax.io"),
            language = AppLanguage.EN
        )

        assertTrue(result.message.orEmpty().contains("No connection"), result.message.orEmpty())
    }

    @Test
    fun `407 manda revisar o proxy`() {
        val result = apiKeyCheckResult(
            ApiSource.OPENROUTER,
            error = IllegalStateException("OpenRouter HTTP 407: Proxy Authentication Required"),
            language = AppLanguage.PT
        )

        assertEquals(AppTone.CRITICAL, result.tone)
        assertTrue(result.message.orEmpty().contains("407"), result.message.orEmpty())
    }

    @Test
    fun `429 diz que nao houve veredito sobre a chave`() {
        // Ambar e não vermelho: mandar trocar uma credencial correta por causa de
        // um limite temporário é o erro que esta ordem de testes existe para evitar.
        val result = apiKeyCheckResult(
            ApiSource.DEEPSEEK,
            error = IllegalStateException("DeepSeek HTTP 429: Too Many Requests"),
            language = AppLanguage.PT
        )

        assertEquals(ApiKeyCheckStatus.FAILED, result.status)
        assertEquals(AppTone.WARNING, result.tone)
        assertTrue(result.message.orEmpty().contains("Não dá para concluir"), result.message.orEmpty())
    }

    @Test
    fun `503 tambem e inconclusivo`() {
        val result = apiKeyCheckResult(
            ApiSource.MINIMAX,
            error = IllegalStateException("MiniMax HTTP 503: service unavailable"),
            language = AppLanguage.EN
        )

        assertEquals(AppTone.WARNING, result.tone)
        assertTrue(result.message.orEmpty().contains("No verdict"), result.message.orEmpty())
    }

    @Test
    fun `assinatura ausente do OpenCode Go e avaliada antes do 403 generico`() {
        // A mensagem carrega os DOIS marcadores. Com a ordem invertida, quem só
        // usa o Zen pago seria mandado revisar uma chave que está correta.
        val result = apiKeyCheckResult(
            ApiSource.OPENCODE_GO,
            error = IllegalStateException("OpenCode Go HTTP 403: {\"error\":\"EntitlementError\"}"),
            language = AppLanguage.PT
        )

        assertEquals(AppTone.WARNING, result.tone)
        assertEquals("Chave válida, sem assinatura Go ativa.", result.message)
    }

    @Test
    fun `mensagem traduzida do repositorio para assinatura ausente tambem casa`() {
        val result = apiKeyCheckResult(
            ApiSource.OPENCODE_GO,
            error = IllegalStateException(OPEN_CODE_GO_NO_SUBSCRIPTION_MESSAGE),
            language = AppLanguage.EN
        )

        assertEquals(AppTone.WARNING, result.tone)
        assertTrue(result.message.orEmpty().contains("no active Go subscription"), result.message.orEmpty())
    }

    @Test
    fun `plano inativo da MiniMax nao e chave errada`() {
        val result = apiKeyCheckResult(
            ApiSource.MINIMAX,
            error = IllegalStateException(
                "MiniMax sem plano/token ativo. Ative um plano ou gere um token com assinatura válida e tente novamente."
            ),
            language = AppLanguage.PT
        )

        assertEquals(AppTone.WARNING, result.tone)
        assertEquals("Chave válida, sem plano ativo na MiniMax.", result.message)
    }

    @Test
    fun `401 manda revisar a chave`() {
        val result = apiKeyCheckResult(
            ApiSource.OPENROUTER,
            error = IllegalStateException("OpenRouter HTTP 401: {\"error\":{\"message\":\"No auth credentials found\"}}"),
            language = AppLanguage.PT
        )

        assertEquals(ApiKeyCheckStatus.FAILED, result.status)
        assertEquals(AppTone.CRITICAL, result.tone)
        assertTrue(result.message.orEmpty().contains("Revise a chave"), result.message.orEmpty())
    }

    @Test
    fun `erro sem categoria mostra a mensagem do repositorio verbatim`() {
        // O caso da MiniMax com chave inválida: HTTP 200 com `status_code` de erro
        // no corpo. Só o repositório sabe formular isso, e reescrever aqui seria
        // um segundo dono da tradução.
        val repositoryMessage = "Erro na API MiniMax: invalid api key (código 1004)"
        val result = apiKeyCheckResult(
            ApiSource.MINIMAX,
            error = IllegalStateException(repositoryMessage),
            language = AppLanguage.PT
        )

        assertEquals(ApiKeyCheckStatus.FAILED, result.status)
        assertEquals(AppTone.CRITICAL, result.tone)
        assertEquals(repositoryMessage, result.message)
    }

    @Test
    fun `a chave nunca vaza no texto do veredito`() {
        val result = apiKeyCheckResult(
            ApiSource.DEEPSEEK,
            error = IllegalStateException("DeepSeek HTTP 400: rejected Bearer sk-live-abcdef123456"),
            language = AppLanguage.PT
        )

        assertFalse(result.message.orEmpty().contains("sk-live-abcdef123456"), result.message.orEmpty())
        assertTrue(result.message.orEmpty().contains("[REDACTED]"), result.message.orEmpty())
    }

    @Test
    fun `exececao sem mensagem nao produz veredito vazio`() {
        val result = apiKeyCheckResult(
            ApiSource.OPENROUTER,
            error = IllegalStateException(),
            language = AppLanguage.PT
        )

        assertEquals(ApiKeyCheckStatus.FAILED, result.status)
        assertFalse(result.message.isNullOrBlank())
    }

    @Test
    fun `todo veredito tem texto nos dois idiomas`() {
        val errors = listOf(
            null,
            ConnectException("refused"),
            IllegalStateException("HTTP 407"),
            IllegalStateException("HTTP 429"),
            IllegalStateException("HTTP 503"),
            IllegalStateException(OPEN_CODE_GO_NO_SUBSCRIPTION_MESSAGE),
            IllegalStateException("MiniMax sem plano/token ativo"),
            IllegalStateException("HTTP 401"),
            IllegalStateException("qualquer outra coisa")
        )

        for (error in errors) {
            for (language in AppLanguage.entries) {
                val result = apiKeyCheckResult(ApiSource.OPENCODE_GO, error, language)
                assertFalse(result.message.isNullOrBlank(), "$error / $language")
            }
        }
    }
}
