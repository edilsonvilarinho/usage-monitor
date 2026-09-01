package com.usagemonitor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response

/**
 * O autenticador de proxy responde ao desafio 407 uma única vez. Sem o guard
 * contra "já tentei nesta requisição", uma senha errada faz o OkHttp reenviar
 * a mesma credencial recusada para sempre.
 */
class HttpClientFactoryTest {

    @Test
    fun `responde ao primeiro desafio 407 com credencial basic`() {
        val authenticator = buildProxyAuthenticator("usuario", "senha")
        val request = Request.Builder().url("http://proxy.empresa.com:8080/").build()
        val response = fakeProxyAuthResponse(request)

        val authenticated = authenticator.authenticate(route = null, response = response)

        assertEquals(
            "Basic dXN1YXJpbzpzZW5oYQ==",
            authenticated?.header("Proxy-Authorization")
        )
    }

    @Test
    fun `desiste quando a requisicao ja carrega Proxy-Authorization`() {
        val authenticator = buildProxyAuthenticator("usuario", "senha")
        val requestJaTentada = Request.Builder()
            .url("http://proxy.empresa.com:8080/")
            .header("Proxy-Authorization", "Basic dXN1YXJpbzpzZW5oYS1lcnJhZGE=")
            .build()
        val response = fakeProxyAuthResponse(requestJaTentada)

        val authenticated = authenticator.authenticate(route = null, response = response)

        assertNull(authenticated)
    }

    private fun fakeProxyAuthResponse(request: Request): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(407)
            .message("Proxy Authentication Required")
            .build()
    }
}
