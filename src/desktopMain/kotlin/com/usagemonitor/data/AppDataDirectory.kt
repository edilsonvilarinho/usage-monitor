package com.usagemonitor.data

import java.io.File

/**
 * A raiz dos dados do app **neste processo**: `~/.usage-monitor`, ou a pasta de
 * `--user-data-dir` (issue #419).
 *
 * Um dono só. Antes, cada data source montava `user.home/.usage-monitor/…` por
 * conta própria: trocar a raiz exigiria lembrar de 22 lugares, e o esquecido
 * gravaria em silêncio na raiz errada. `ArchitectureRulesTest` proíbe o literal
 * fora deste arquivo.
 *
 * Dois escopos:
 * - [file]: dados e instância (credenciais do app, bancos, diagnóstico, trava,
 *   pedido de foco) — seguem a raiz configurada.
 * - [installationFile]: o que scripts de fora leem em caminho fixo (recibo do
 *   NSIS, ACK e log do updater do Linux, pacotes baixados) — sempre a raiz
 *   padrão, porque o instalador e o updater não recebem `--user-data-dir`.
 */
internal object AppDataDirectory {
    const val DEFAULT_DIRECTORY_NAME = ".usage-monitor"

    private val process = AppDataRoot(defaultRoot = ::defaultAppDataRoot)

    /** Chamado uma vez pelo `main()`, antes de qualquer leitura. */
    fun configure(root: File) = process.configure(root)

    val root: File
        get() = process.root

    /** `false` quando o processo roda com `--user-data-dir` numa pasta que não é a padrão. */
    val isDefault: Boolean
        get() = process.isDefault

    fun file(relative: String): File = process.file(relative)

    fun installationFile(relative: String): File = process.installationFile(relative)
}

/**
 * A regra de [AppDataDirectory], separada do estado global: o teste cria a sua e
 * nunca toca na do processo, que a suíte inteira compartilha na mesma JVM.
 */
internal class AppDataRoot(private val defaultRoot: () -> File) {
    private val lock = Any()
    private var configured: File? = null
    private var read = false

    /**
     * Configurar depois da primeira leitura **lança erro**. Algum arquivo já teria
     * sido aberto na raiz padrão, e o resto iria para a nova: dados de uma execução
     * espalhados em duas pastas, sem erro nenhum.
     */
    fun configure(root: File) {
        synchronized(lock) {
            check(!read) { "A raiz de dados já foi lida; configurá-la agora espalharia os arquivos em duas pastas." }
            configured = root
        }
    }

    val root: File
        get() = synchronized(lock) {
            read = true
            configured ?: defaultRoot()
        }

    val isDefault: Boolean
        get() = synchronized(lock) {
            read = true
            configured == null
        }

    fun file(relative: String): File = File(root, relative)

    fun installationFile(relative: String): File = File(defaultRoot(), relative)
}

internal fun defaultAppDataRoot(): File {
    val homeDir = System.getProperty("user.home")
        ?: throw IllegalStateException("Propriedade 'user.home' não disponível")
    return File(homeDir, AppDataDirectory.DEFAULT_DIRECTORY_NAME)
}
