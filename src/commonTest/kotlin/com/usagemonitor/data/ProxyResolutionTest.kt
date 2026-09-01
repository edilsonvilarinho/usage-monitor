package com.usagemonitor.data

import com.usagemonitor.data.repository.HTTPS_PROXY_ENV_VAR
import com.usagemonitor.data.repository.HTTP_PROXY_ENV_VAR
import com.usagemonitor.data.repository.resolveEffectiveProxy
import com.usagemonitor.domain.entity.ProxySettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProxyResolutionTest {

    @Test
    fun `manual active wins over environment variables`() {
        val settings = ProxySettings(useEnvironmentProxy = false, host = "manual.empresa.com", port = 8080)
        val env = mapOf(HTTPS_PROXY_ENV_VAR to "http://env.empresa.com:3128")

        val config = resolveEffectiveProxy(settings, envVarReader = env::get)

        assertEquals("manual.empresa.com", config?.host)
    }

    @Test
    fun `https proxy wins over http proxy`() {
        val settings = ProxySettings(useEnvironmentProxy = true)
        val env = mapOf(
            HTTPS_PROXY_ENV_VAR to "http://https-proxy.empresa.com:3128",
            HTTP_PROXY_ENV_VAR to "http://http-proxy.empresa.com:3128"
        )

        val config = resolveEffectiveProxy(settings, envVarReader = env::get)

        assertEquals("https-proxy.empresa.com", config?.host)
    }

    @Test
    fun `falls back to http proxy when https proxy is absent`() {
        val settings = ProxySettings(useEnvironmentProxy = true)
        val env = mapOf(HTTP_PROXY_ENV_VAR to "http://http-proxy.empresa.com:3128")

        val config = resolveEffectiveProxy(settings, envVarReader = env::get)

        assertEquals("http-proxy.empresa.com", config?.host)
    }

    @Test
    fun `no proxy when nothing is configured`() {
        val settings = ProxySettings(useEnvironmentProxy = true)

        val config = resolveEffectiveProxy(settings, envVarReader = { null })

        assertNull(config)
    }

    @Test
    fun `environment mode ignores manual fields even when filled`() {
        val settings = ProxySettings(useEnvironmentProxy = true, host = "manual.empresa.com", port = 8080)

        val config = resolveEffectiveProxy(settings, envVarReader = { null })

        assertNull(config)
    }
}
