package com.usagemonitor

import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Confiança TLS do app: o `cacerts` da JVM **mais** o repositório de certificados
 * do sistema operacional (issue #325).
 *
 * Antivírus que inspecionam HTTPS (Kaspersky, ESET) instalam uma CA própria no
 * repositório do Windows e reassinam o tráfego com ela. O runtime empacotado só
 * conhecia o `cacerts` embarcado e recusava todas as fontes remotas nessas
 * máquinas — o codenotch relatou o mesmo com `UnknownIssuer`. Ler o repositório
 * do sistema é o que o navegador já faz na mesma máquina.
 */
internal class TlsTrust(
    val socketFactory: SSLSocketFactory,
    val trustManager: X509TrustManager
)

/**
 * A confiança composta, ou `null` quando o repositório do sistema não existe ou
 * não carrega — aí o cliente fica com o padrão da JVM, exatamente como antes.
 */
internal fun buildSystemTlsTrust(osName: String = System.getProperty("os.name").orEmpty()): TlsTrust? {
    val jvmDefault = trustManagerFor(null) ?: return null
    val system = loadSystemTrustManager(osName) ?: return null
    val composite = CompositeX509TrustManager(listOf(jvmDefault, system))
    val context = SSLContext.getInstance("TLS")
    context.init(null, arrayOf(composite), null)
    return TlsTrust(context.socketFactory, composite)
}

/**
 * O tipo de `KeyStore` do repositório do sistema. Linux não tem um só: cada
 * distribuição guarda as CAs num caminho próprio, e ali o `cacerts` continua
 * sendo a única fonte.
 */
internal fun systemKeyStoreType(osName: String): String? {
    val normalized = osName.lowercase()
    return when {
        normalized.startsWith("windows") -> "Windows-ROOT"
        normalized.startsWith("mac") -> "KeychainStore"
        else -> null
    }
}

/**
 * `Windows-ROOT` só existe com o módulo `jdk.crypto.mscapi` no runtime — o
 * `build.gradle.kts` o inclui só no build Windows. Sem ele, `getInstance` lança e
 * a leitura degrada para `null`.
 */
internal fun loadSystemTrustManager(osName: String): X509TrustManager? {
    val type = systemKeyStoreType(osName) ?: return null
    return runCatching {
        val keyStore = KeyStore.getInstance(type)
        keyStore.load(null, null)
        trustManagerFor(keyStore)
    }.getOrNull()
}

internal fun trustManagerFor(keyStore: KeyStore?): X509TrustManager? {
    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    factory.init(keyStore)
    return factory.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
}

/**
 * Aceita a cadeia se **qualquer** um dos gerentes a aceitar, na ordem dada.
 * Recusada por todos, sobe a exceção do primeiro — a da JVM, que é a mensagem que
 * o app sempre mostrou.
 */
internal class CompositeX509TrustManager(
    private val managers: List<X509TrustManager>
) : X509TrustManager {

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
        firstAccepting { manager -> manager.checkServerTrusted(chain, authType) }
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) {
        firstAccepting { manager -> manager.checkClientTrusted(chain, authType) }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> {
        return managers.flatMap { manager -> manager.acceptedIssuers.asList() }.toTypedArray()
    }

    private fun firstAccepting(check: (X509TrustManager) -> Unit) {
        var firstFailure: CertificateException? = null
        for (manager in managers) {
            try {
                check(manager)
                return
            } catch (failure: CertificateException) {
                if (firstFailure == null) {
                    firstFailure = failure
                }
            }
        }
        throw firstFailure ?: CertificateException("Nenhum repositório de confiança disponível.")
    }
}
