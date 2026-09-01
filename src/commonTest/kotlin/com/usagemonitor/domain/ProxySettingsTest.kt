package com.usagemonitor.domain

import com.usagemonitor.domain.entity.ProxyEnvironmentConfig
import com.usagemonitor.domain.entity.ProxySettings
import com.usagemonitor.domain.entity.parseProxyEnvironmentValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProxySettingsTest {

    @Test
    fun `manual active requires being off environment mode and having host and port`() {
        val settings = ProxySettings(useEnvironmentProxy = false, host = "proxy.empresa.com", port = 8080)

        assertTrue(settings.isManualConfigured)
        assertTrue(settings.isManualActive)
    }

    @Test
    fun `manual active is false when environment mode is on, even with host and port filled`() {
        val settings = ProxySettings(useEnvironmentProxy = true, host = "proxy.empresa.com", port = 8080)

        assertTrue(settings.isManualConfigured)
        assertFalse(settings.isManualActive)
    }

    @Test
    fun `manual configured is false without host`() {
        val settings = ProxySettings(useEnvironmentProxy = false, host = "", port = 8080)

        assertFalse(settings.isManualConfigured)
    }

    @Test
    fun `manual configured is false with out-of-range port`() {
        val settings = ProxySettings(useEnvironmentProxy = false, host = "proxy.empresa.com", port = 0)

        assertFalse(settings.isManualConfigured)
    }

    @Test
    fun `parses scheme host port and credentials`() {
        val config = parseProxyEnvironmentValue("http://user:pass@proxy.empresa.com:8080")

        assertEquals(
            ProxyEnvironmentConfig(scheme = "http", host = "proxy.empresa.com", port = 8080, username = "user", password = "pass"),
            config
        )
    }

    @Test
    fun `parses https scheme without credentials`() {
        val config = parseProxyEnvironmentValue("https://proxy.empresa.com:3128")

        assertEquals(
            ProxyEnvironmentConfig(scheme = "https", host = "proxy.empresa.com", port = 3128, username = null, password = null),
            config
        )
    }

    @Test
    fun `defaults to port 80 when absent`() {
        val config = parseProxyEnvironmentValue("http://proxy.empresa.com")

        assertEquals(80, config?.port)
    }

    @Test
    fun `defaults to http scheme when absent`() {
        val config = parseProxyEnvironmentValue("proxy.empresa.com:8080")

        assertEquals("http", config?.scheme)
    }

    @Test
    fun `username without password yields null password`() {
        val config = parseProxyEnvironmentValue("http://user@proxy.empresa.com:8080")

        assertEquals("user", config?.username)
        assertNull(config?.password)
    }

    @Test
    fun `trailing path is discarded`() {
        val config = parseProxyEnvironmentValue("http://proxy.empresa.com:8080/")

        assertEquals("proxy.empresa.com", config?.host)
    }

    @Test
    fun `blank value yields null`() {
        assertNull(parseProxyEnvironmentValue(""))
        assertNull(parseProxyEnvironmentValue("   "))
    }

    @Test
    fun `malformed port yields null instead of throwing`() {
        assertNull(parseProxyEnvironmentValue("http://proxy.empresa.com:notaport"))
    }
}
