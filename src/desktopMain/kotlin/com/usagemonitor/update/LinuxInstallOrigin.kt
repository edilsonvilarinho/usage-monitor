package com.usagemonitor.update

/**
 * De onde veio esta instalação no Linux.
 *
 * A atualização automática promove uma árvore extraída para
 * `versions/<versão>` e reescreve o ponteiro `current`. Fazer isso por cima de
 * uma instalação que **não** foi criada por esse caminho ou não faz nada — a
 * árvore promovida nunca é executada — ou pior: mexe em arquivos que pertencem
 * ao `dpkg`/`rpm`, que o gerenciador de pacotes vai reinstalar ou remover por
 * conta própria na próxima operação. Por isso o resolvedor existe, e por isso o
 * default é não atualizar.
 *
 * Duas origens e não quatro, pelo mesmo motivo de [WindowsInstallOrigin]: `.deb`,
 * `.rpm`, cópia manual da pasta e `gradlew run` recebem a mesma resposta, e
 * afirmar qual deles é sem ter como provar seria pior que dizer "não foi por
 * este instalador".
 */
enum class LinuxInstallOrigin {
    /** Árvore XDG gerenciada: pode se atualizar sozinha. */
    MANAGED_XDG,

    /** Qualquer outra coisa — `.deb`, `.rpm`, cópia manual, `gradlew run`. */
    UNMANAGED
}

object LinuxInstallOriginResolver {

    /**
     * Resolve a origem a partir do estado real da máquina.
     *
     * Duas fontes para o executável em execução pela mesma razão do resolvedor
     * do Windows: `jpackage.app-path` é a propriedade que o launcher do jpackage
     * injeta, e `ProcessHandle` devolve a imagem do processo. Aceitar qualquer
     * uma das duas torna a detecção independente de qual delas o runtime
     * preenche.
     */
    fun current(): LinuxInstallOrigin {
        val layout = resolveLinuxInstallLayout()
        return resolve(
            isLinux = System.getProperty("os.name").orEmpty().lowercase().contains("linux"),
            rootPath = layout?.rootPath,
            executableCandidates = listOfNotNull(
                System.getProperty("jpackage.app-path"),
                ProcessHandle.current().info().command().orElse(null)
            ),
            hasMarker = layout?.hasMarker() ?: false
        )
    }

    /**
     * Função pura, para o teste não depender do disco nem do processo real.
     *
     * São **três** condições, e nenhuma delas basta sozinha:
     *
     * 1. o marcador `.usage-monitor-managed` existe na raiz — ele é o que
     *    autoriza qualquer escrita nessa árvore;
     * 2. o executável em execução está **dentro de `<raiz>/versions/`** — o
     *    marcador sobrevive a uma instalação apagada à mão e passaria a
     *    autorizar a atualização de uma cópia qualquer da pasta;
     * 3. o executável não está num caminho de gerenciador de pacotes.
     *
     * A terceira parece redundante diante da segunda, e é explícita de
     * propósito: um `/opt/usage-monitor` instalado por `.rpm` pode conviver com
     * um marcador deixado por uma instalação XDG anterior, e nesse estado a
     * decisão de não tocar em `/opt` passaria a depender do formato exato do
     * caminho de `versions/`. Aqui ela é uma condição com nome, e não um efeito
     * colateral de outra.
     */
    internal fun resolve(
        isLinux: Boolean,
        rootPath: String?,
        executableCandidates: List<String>,
        hasMarker: Boolean
    ): LinuxInstallOrigin {
        if (!isLinux) {
            return LinuxInstallOrigin.UNMANAGED
        }

        if (!hasMarker) {
            return LinuxInstallOrigin.UNMANAGED
        }

        val root = rootPath?.trim()?.takeIf { it.isNotBlank() }
            ?: return LinuxInstallOrigin.UNMANAGED
        val versionsPrefix = "${normalizePosixPath(root).trimEnd('/')}/$LINUX_VERSIONS_DIRECTORY_NAME/"

        val normalizedCandidates = executableCandidates
            .mapNotNull { candidate -> candidate.trim().trim('"').takeIf { it.isNotBlank() } }
            .map { candidate -> normalizePosixPath(candidate) }

        if (normalizedCandidates.any { isLinuxPackageManagerPath(it) }) {
            return LinuxInstallOrigin.UNMANAGED
        }

        val insideVersions = normalizedCandidates.any { candidate ->
            candidate.startsWith(versionsPrefix)
        }

        return if (insideVersions) LinuxInstallOrigin.MANAGED_XDG else LinuxInstallOrigin.UNMANAGED
    }
}

/**
 * Prefixos que pertencem ao gerenciador de pacotes.
 *
 * O `.deb` e o `.rpm` deste projeto instalam em `/opt/usage-monitor`; `/usr` e
 * `/usr/local` entram porque são os destinos convencionais e porque o custo de
 * um falso negativo aqui é escrever por cima de arquivo que o `dpkg` reinstala.
 */
private val LINUX_PACKAGE_MANAGER_PREFIXES = listOf("/usr/", "/opt/")

internal fun isLinuxPackageManagerPath(path: String): Boolean {
    val normalized = normalizePosixPath(path.trim().trim('"'))
    return LINUX_PACKAGE_MANAGER_PREFIXES.any { prefix -> normalized.startsWith(prefix) }
}

/**
 * Normaliza um caminho POSIX **como texto**: colapsa `//`, resolve `.` e `..` e
 * tira a barra final.
 *
 * Não usa `File.normalize()` porque aquele trabalha com o separador da máquina
 * que executa o código, e a suíte roda no Windows: `/home/u/../x` voltaria
 * intacto e o teste que existe para provar que `..` não escapa passaria sem
 * provar nada. Não toca o disco — `..` sobre symlink não é resolvido, e não
 * precisa ser: quem autoriza a árvore é o marcador, não este caminho.
 */
internal fun normalizePosixPath(path: String): String {
    val isAbsolute = path.startsWith("/")
    val resolved = ArrayDeque<String>()

    path.split('/').forEach { segment ->
        when {
            segment.isEmpty() || segment == "." -> Unit
            segment == ".." -> {
                if (resolved.isNotEmpty() && resolved.last() != "..") {
                    resolved.removeLast()
                } else if (!isAbsolute) {
                    // Fora de um caminho absoluto, `..` que sobe além da origem
                    // continua significando alguma coisa e é preservado.
                    resolved.addLast("..")
                }
            }

            else -> resolved.addLast(segment)
        }
    }

    val body = resolved.joinToString("/")
    return if (isAbsolute) "/$body" else body
}
