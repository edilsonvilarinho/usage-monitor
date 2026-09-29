import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kover)
}

// A versao vem da tag (#344): o release roda com `-PappVersion=X.Y.Z`, lido do `vX.Y.Z`,
// e nenhum commit de bump passa pela `main`. Sem a propriedade, a ultima tag alcancavel,
// para `run` e `packageInstaller` locais seguirem com versao numerica valida para o
// jpackage. Sem git ou sem tag (checkout raso do CI) cai em 1.0.0 -- nao 0.0.0, que o
// plugin do Compose recusa na configuracao (`MAJOR` do Dmg tem de ser > 0). So os testes
// rodam ali, e eles nao leem `CURRENT_APP_VERSION`. O `git` so e chamado sem a propriedade --
// o container do `build-linux` nem tem git.
fun lastReleaseTagVersion(): String = runCatching {
    providers.exec {
        // `--exclude`: tag beta (`vX.Y.Z-beta.N`, issue #355) nao e a versao estavel de base.
        commandLine("git", "describe", "--tags", "--abbrev=0", "--match", "v[0-9]*", "--exclude", "*-beta*")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().removePrefix("v")
}.getOrDefault("")

version = providers.gradleProperty("appVersion").orNull?.trim()?.removePrefix("v")
    ?: lastReleaseTagVersion().ifBlank { "1.0.0" }

val appVersion = version.toString()

// Beta (`X.Y.Z-beta.N`, issue #355): o plugin do Compose recusa o sufixo em Exe
// (`MAJOR.MINOR.BUILD`), Dmg (versao e build version so numericas) e Rpm (sem `-`)
// -- medido com `createDistributable -PappVersion=99.0.0-beta.1`, que falha na
// configuracao. Esses formatos levam so o numero; Deb e Rpm levam `~beta.N`, que
// os dois gerenciadores ordenam ANTES da estavel do mesmo numero. `CURRENT_APP_VERSION`,
// o instalador NSIS, o tarball e o recibo continuam com a string completa.
val packageBaseVersion = appVersion.substringBefore("-")
val linuxPackageVersion = appVersion.replace("-", "~")
val generatedAppVersionDir = layout.buildDirectory.dir("generated/app-version/desktopMain/kotlin")

kotlin {
    // ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¡nico alvo: Desktop JVM.
    // O nome "desktop" define o source set desktopMain/desktopTest.
    jvm("desktop")

    // Java 17 ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â© o mÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â­nimo recomendado para Compose Multiplatform Desktop
    jvmToolchain(17)

    sourceSets {

        // --- commonMain ---
        // CÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â³digo compartilhado: domain, data e presentation.
        // Depende apenas de bibliotecas multiplataforma.
        getByName("commonMain") {
            dependencies {
                // Compose runtime e componentes visuais
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.material3)
                implementation(libs.compose.material.icons)
                implementation(libs.compose.components.resources)

                // Ktor ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â cliente HTTP (engine vem no desktopMain)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.json)
                implementation(libs.ktor.client.logging)

                // SerializaÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â§ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â£o e utilitÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¡rios KMP
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.multiplatform.settings)
            }
        }

        // --- desktopMain ---
        // CÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â³digo especÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â­fico da plataforma Desktop (JVM):
        // - engine OkHttp do Ktor
        // - leitura de ficheiros com java.io.File
        // - entry point da janela Compose
        getByName("desktopMain") {
            kotlin.srcDir(generatedAppVersionDir)
            dependencies {
                // Compose Desktop: inclui janela nativa para o SO atual
                implementation(compose.desktop.currentOs)

                // OkHttp: engine HTTP para JVM
                implementation(libs.ktor.client.okhttp)

                // Coroutines com suporte ao dispatcher Swing (UI thread do Desktop)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.sqlite.jdbc)

                // PDFBox: relatorio PDF das telas de sessoes. E JVM-only, entao o
                // modelo do documento fica em commonMain e so a renderizacao aqui.
                implementation(libs.pdfbox)

                // Commons Compress: leitura do .tar.gz da atualizacao Linux. O JDK
                // traz zip e gzip, mas nao traz leitor de tar; e depender do binario
                // `tar` deixaria o update na mao do que estiver no PATH da maquina.
                implementation(libs.commons.compress)
            }
        }

        // --- commonTest ---
        // Testes unitÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¡rios: domain, mappers, ViewModel
        getByName("commonTest") {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.client.mock)
            }
        }

        // --- desktopTest ---
        // Testes de componente Compose para Desktop
        getByName("desktopTest") {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.compose.ui.test)
            }
        }
    }
}

// ConfiguraÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â§ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â£o da aplicaÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â§ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â£o Desktop
compose.desktop {
    application {
        mainClass = "com.usagemonitor.MainKt"

        nativeDistributions {
            targetFormats(
                TargetFormat.Exe,
                TargetFormat.Deb,
                TargetFormat.Rpm,
                TargetFormat.Dmg
            )
            packageName = "Usage Monitor"
            packageVersion = packageBaseVersion
            // `java.logging` cobre o commons-logging que o PDFBox traz: hoje ele
            // acha o SLF4J que o Ktor ja poe no classpath, mas o fallback dele e o
            // `Jdk14Logger`, de `java.util.logging`. Modulo faltando no runtime
            // image so aparece no app empacotado, nunca no `gradlew run` Ã¢â‚¬â€ por isso
            // vai declarado, e nao descoberto no primeiro relatorio que falhar.
            modules("java.sql", "java.logging")
            // `Windows-ROOT` (repositório de certificados do Windows, issue #325) mora
            // em `jdk.crypto.mscapi`, que so existe no JDK do Windows: declarado sem
            // condicao, o jlink do build Linux/macOS falharia com modulo inexistente.
            if (System.getProperty("os.name").startsWith("Windows")) {
                modules("jdk.crypto.mscapi")
            }

            windows {
                iconFile.set(project.file("src/desktopMain/resources/icons/app_icon.ico"))
                menu = true
                shortcut = true
                perUserInstall = true
                dirChooser = true
                // Nao geramos mais MSI, entao este UpgradeCode nao produz nada aqui.
                // Ele fica porque e o UpgradeCode das instalacoes MSI que ja existem
                // por ai -- ate a v37 o release publicava as duas coisas -- e e por
                // ele que o UsageMonitor.nsi as encontra e remove antes de instalar
                // (!ifndef MSI_UPGRADE_CODE). Apagar daqui deixaria o GUID orfao no
                // .nsi, sem nada que diga de onde ele veio.
                upgradeUuid = "D26C4B79-9F2B-4CE5-B94E-E2E6A2A9E4A4"
            }
            linux {
                iconFile.set(project.file("src/desktopMain/resources/icons/app_icon.png"))
                debPackageVersion = linuxPackageVersion
                rpmPackageVersion = linuxPackageVersion
            }
            macOS {
                iconFile.set(project.file("src/desktopMain/resources/icons/app_icon.icns"))
                bundleID = "com.usagemonitor.app"
                dockName = "Usage Monitor"
                // Distribuicao sem Developer ID: o jpackage aplica assinatura ad-hoc,
                // exigida pelo Apple Silicon. O Gatekeeper ainda pede liberacao manual.
            }
        }
    }
}

val generateAppVersionSource = tasks.register("generateAppVersionSource") {
    // Sem este input a troca de `-PappVersion` nao invalida a tarefa: o script nao muda
    // mais a cada release, e o `AppVersion.kt` antigo sairia do cache.
    inputs.property("appVersion", appVersion)
    outputs.dir(generatedAppVersionDir)

    doLast {
        val packageDir = generatedAppVersionDir.get().dir("com/usagemonitor").asFile
        val outputFile = packageDir.resolve("AppVersion.kt")

        packageDir.mkdirs()
        outputFile.writeText(
            """
            package com.usagemonitor

            const val CURRENT_APP_VERSION = "$appVersion"
            """.trimIndent()
        )
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn(generateAppVersionSource)
}

// Forks paralelos so por opt-in (`-PtestForks=N`), e o default e UM.
//
// O ganho e real e foi medido numa maquina de 16 processadores: 1m24s serial,
// 1m12s com 2 forks e 52s com 4, com resultado identico nos tres casos. O que
// impede de ligar por default e o Skiko: `Library.unpackIfNeeded` extrai
// `skiko-windows-x64.dll` para `~/.skiko/<hash>/` com um `Files.move`, e no
// Windows esse move falha com `AccessDeniedException` quando outro processo ja
// abriu o destino. Com o cache quente -- o caso de toda maquina de
// desenvolvimento -- nao ha extracao e nao ha corrida; num runner limpo, todo
// fork tenta extrair ao mesmo tempo.
//
// Foi exatamente o que aconteceu: passou local, passou no primeiro run do CI e
// derrubou o segundo com `ExceptionInInitializerError` em `AppThemeScaleTest` e
// em 40 testes de `ComponentTest`.
//
// Por isso, com mais de um fork, `extractSkikoNative` roda ANTES da suite num
// processo so (issue #295): a DLL chega a `~/.skiko` sem concorrencia e cada
// fork encontra o cache quente. O CI passa `-PtestForks=3`; localmente o
// default continua serial.
val testForks = providers.gradleProperty("testForks").orNull?.toIntOrNull() ?: 1

tasks.withType<Test>().configureEach {
    maxParallelForks = testForks
    maxHeapSize = "1g"
}

// O plugin do Kover estava aplicado desde sempre e NENHUMA tarefa de relatorio
// era executada em lugar nenhum: o agente instrumentava toda passada de
// `:desktopTest` -- 6 a 7 s medidos -- para produzir um numero que ninguem lia.
// Agora a instrumentacao e opt-in por `-Pcoverage`, que o CI liga em todo run
// que executa a suite (issue #299). Sem a propriedade, a suite roda limpa.
kover {
    currentProject {
        instrumentation {
            disabledForAll.set(!providers.gradleProperty("coverage").isPresent)
        }
    }

    reports {
        filters {
            excludes {
                // `Main.kt` e o grafo de DI mais a janela: nao existe teste de
                // unidade que o exercite, e conta-lo como descoberto afunda o
                // numero sem apontar lacuna nenhuma que se possa fechar.
                classes("com.usagemonitor.MainKt", "com.usagemonitor.MainKt$*")
            }
        }

        total {
            // Nada pendurado no `check`: o relatorio e um passo proprio do CI, e
            // amarra-lo ao `check` faria toda build local pagar por ele.
            html { onCheck.set(false) }
            xml { onCheck.set(false) }
        }
    }
}

// Capturas do README: renderizadas offscreen a partir dos composables reais com
// dados sinteticos. O gerador vive em desktopTest para nao entrar no JAR
// distribuido e ainda enxergar os composables `internal`.
val desktopTestCompilation = kotlin.jvm("desktop").compilations.getByName("test")

tasks.register<JavaExec>("generateScreenshots") {
    group = "documentation"
    description = "Renderiza offscreen os prints do README com dados sinteticos."

    mainClass.set("com.usagemonitor.screenshots.ScreenshotGeneratorKt")
    classpath = files(desktopTestCompilation.output.allOutputs, desktopTestCompilation.runtimeDependencyFiles)
    args(layout.projectDirectory.dir("img").asFile.absolutePath)
}

tasks.register<JavaExec>("generateGargantuaPreview") {
    group = "documentation"
    description = "Renderiza a HUD Gargantua e suas animacoes com dados sinteticos."
    mainClass.set("com.usagemonitor.screenshots.GargantuaPreviewGeneratorKt")
    classpath = files(desktopTestCompilation.output.allOutputs, desktopTestCompilation.runtimeDependencyFiles)
    args(layout.buildDirectory.dir("gargantua-preview").get().asFile.absolutePath)
}

tasks.register<JavaExec>("generateTourGif") {
    group = "documentation"
    description = "Renderiza offscreen o GIF de tour do README com dados sinteticos."

    mainClass.set("com.usagemonitor.screenshots.TourGifGeneratorKt")
    classpath = files(desktopTestCompilation.output.allOutputs, desktopTestCompilation.runtimeDependencyFiles)
    args(layout.projectDirectory.dir("img").asFile.absolutePath)
}

// Demos da janela de ajuda (issue #184). Saem em src/desktopMain/resources/help
// para entrar no jar distribuido: sao lidas do classpath em tempo de execucao.
tasks.register<JavaExec>("generateHelpMedia") {
    group = "documentation"
    description = "Renderiza offscreen as demos da janela de ajuda com dados sinteticos."

    mainClass.set("com.usagemonitor.screenshots.HelpMediaGeneratorKt")
    classpath = files(desktopTestCompilation.output.allOutputs, desktopTestCompilation.runtimeDependencyFiles)
    args(layout.projectDirectory.dir("src/desktopMain/resources/help").asFile.absolutePath)
}

// Pre-extracao da nativa do Skiko antes dos forks paralelos -- ver o comentario
// de `testForks` acima. Sem forks nao ha corrida, e a tarefa nao entra no grafo.
val extractSkikoNative = tasks.register<JavaExec>("extractSkikoNative") {
    group = "verification"
    description = "Extrai a biblioteca nativa do Skiko antes dos forks paralelos da suite."

    mainClass.set("com.usagemonitor.SkikoWarmupKt")
    classpath = files(desktopTestCompilation.output.allOutputs, desktopTestCompilation.runtimeDependencyFiles)
}

if (testForks > 1) {
    tasks.named<Test>("desktopTest") {
        dependsOn(extractSkikoNative)
    }
}

// Adicionar manifest ao desktopJar para tornÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¡-lo executÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¡vel
tasks.named<Jar>("desktopJar") {
    manifest {
        attributes(
            "Main-Class" to "com.usagemonitor.MainKt",
            "Manifest-Version" to "1.0",
            "Created-By" to "Kotlin Multiplatform"
        )
    }
}

// Tarefa para gerar o instalador NSIS
val installerDir = file("build/installer")
val installerFilesDir = file("build/installer/files")

tasks.register<Copy>("prepareInstallerFiles") {
    dependsOn("createDistributable")

    // O destino e limpo antes da copia porque `Copy` do Gradle e aditivo: ele
    // nunca remove do destino um arquivo que sumiu da origem. O jpackage nomeia
    // os jars com versao + hash, entao qualquer mudanca de dependencia gera nome
    // novo -- e o jar antigo ficava aqui e ENTRAVA no instalador. Medido em
    // 2026-08-24: 2 jars orfaos, 4.464.723 bytes a mais no payload e um
    // Setup.exe de 126.532.759 bytes contra os ~122,3 MB do build limpo.
    //
    // Nao aparece no CI, onde o workspace nasce vazio; e defeito de build local,
    // e e o mesmo problema que o instalador trata do lado do usuario.
    doFirst { delete(installerFilesDir) }

    from(file("build/compose/binaries/main/app/Usage Monitor"))
    into(installerFilesDir)
}

tasks.register<Exec>("buildNsisInstaller") {
    dependsOn("prepareInstallerFiles")

    onlyIf { file("src/installer/UsageMonitor.nsi").exists() }

    val nsisPath = listOf(
        "C:/Program Files/NSIS/makensis.exe",
        "C:/Program Files (x86)/NSIS/makensis.exe",
        "makensis"
    ).firstOrNull { file(it).exists() }

    if (nsisPath != null) {
        workingDir(file("src/installer"))
        commandLine(
            nsisPath,
            "/DPRODUCT_VERSION=$appVersion",
            "UsageMonitor.nsi"
        )
    } else {
        logger.warn("NSIS not found. Skipping installer generation.")
        logger.warn("Install NSIS from https://nsis.sourceforge.io/ to enable installer build.")
    }
}

tasks.register("packageInstaller") {
    dependsOn("buildNsisInstaller")

    doLast {
        val installer = file("build/installer/UsageMonitor-Setup-$appVersion.exe")
        if (installer.exists()) {
            logger.lifecycle("Installer created: ${installer.absolutePath}")
        }
    }
}
