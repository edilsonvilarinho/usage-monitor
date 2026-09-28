package com.usagemonitor

import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SystemTrustStoreTest {

    private class FakeTrustManager(private val accepts: Boolean, private val message: String = "") : X509TrustManager {
        var calls = 0

        override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
            calls++
            if (!accepts) throw CertificateException(message)
        }

        override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) {
            calls++
            if (!accepts) throw CertificateException(message)
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    @Test
    fun `chain refused by the JVM but accepted by the system store is trusted`() {
        val jvm = FakeTrustManager(accepts = false, message = "cacerts")
        val system = FakeTrustManager(accepts = true)

        CompositeX509TrustManager(listOf(jvm, system)).checkServerTrusted(emptyArray(), "RSA")

        assertEquals(1, jvm.calls)
        assertEquals(1, system.calls)
    }

    @Test
    fun `chain accepted by the JVM does not consult the system store`() {
        val jvm = FakeTrustManager(accepts = true)
        val system = FakeTrustManager(accepts = true)

        CompositeX509TrustManager(listOf(jvm, system)).checkServerTrusted(emptyArray(), "RSA")

        assertEquals(0, system.calls)
    }

    @Test
    fun `chain refused by every store surfaces the JVM failure`() {
        val composite = CompositeX509TrustManager(
            listOf(FakeTrustManager(accepts = false, message = "cacerts"), FakeTrustManager(accepts = false, message = "sistema"))
        )

        val failure = assertFailsWith<CertificateException> { composite.checkServerTrusted(emptyArray(), "RSA") }

        assertEquals("cacerts", failure.message)
    }

    @Test
    fun `system key store type follows the operating system`() {
        assertEquals("Windows-ROOT", systemKeyStoreType("Windows 11"))
        assertEquals("KeychainStore", systemKeyStoreType("Mac OS X"))
        assertNull(systemKeyStoreType("Linux"))
    }

    @Test
    fun `Linux keeps the JVM default trust`() {
        assertNull(buildSystemTlsTrust("Linux"))
    }

    @Test
    fun `the Windows store loads on a Windows JDK`() {
        val osName = System.getProperty("os.name").orEmpty()
        if (!osName.startsWith("Windows")) return

        val trust = assertNotNull(buildSystemTlsTrust(osName), "Windows-ROOT não carregou neste JDK")
        assertTrue(trust.trustManager.acceptedIssuers.isNotEmpty())
    }
}
