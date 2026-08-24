import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kover)
}

version = "37.0.0"

val appVersion = version.toString()
val generatedAppVersionDir = layout.buildDirectory.dir("generated/app-version/desktopMain/kotlin")

kotlin {
    // ÃƒÆ’Ã…Â¡nico alvo: Desktop JVM.
    // O nome "desktop" define o source set desktopMain/desktopTest.
    jvm("desktop")

    // Java 17 ÃƒÆ’Ã‚Â© o mÃƒÆ’Ã‚Â­nimo recomendado para Compose Multiplatform Desktop
    jvmToolchain(17)

    sourceSets {

        // --- commonMain ---
        // CÃƒÆ’Ã‚Â³digo compartilhado: domain, data e presentation.
        // Depende apenas de bibliotecas multiplataforma.
        val commonMain by getting {
            dependencies {
                // Compose runtime e componentes visuais
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.components.resources)

                // Ktor ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â cliente HTTP (engine vem no desktopMain)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.json)
                implementation(libs.ktor.client.logging)

                // SerializaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o e utilitÃƒÆ’Ã‚Â¡rios KMP
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.multiplatform.settings)
            }
        }

        // --- desktopMain ---
        // CÃƒÆ’Ã‚Â³digo especÃƒÆ’Ã‚Â­fico da plataforma Desktop (JVM):
        // - engine OkHttp do Ktor
        // - leitura de ficheiros com java.io.File
        // - entry point da janela Compose
        val desktopMain by getting {
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
            }
        }

        // --- commonTest ---
        // Testes unitÃƒÆ’Ã‚Â¡rios: domain, mappers, ViewModel
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.client.mock)
            }
        }

        // --- desktopTest ---
        // Testes de componente Compose para Desktop
        val desktopTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.uiTest)
            }
        }
    }
}

// ConfiguraÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o da aplicaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o Desktop
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
            packageVersion = appVersion
            // `java.logging` cobre o commons-logging que o PDFBox traz: hoje ele
            // acha o SLF4J que o Ktor ja poe no classpath, mas o fallback dele e o
            // `Jdk14Logger`, de `java.util.logging`. Modulo faltando no runtime
            // image so aparece no app empacotado, nunca no `gradlew run` — por isso
            // vai declarado, e nao descoberto no primeiro relatorio que falhar.
            modules("java.sql", "java.logging")

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

val generateAppVersionSource by tasks.registering {
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

tasks.register<JavaExec>("generateTourGif") {
    group = "documentation"
    description = "Renderiza offscreen o GIF de tour do README com dados sinteticos."

    mainClass.set("com.usagemonitor.screenshots.TourGifGeneratorKt")
    classpath = files(desktopTestCompilation.output.allOutputs, desktopTestCompilation.runtimeDependencyFiles)
    args(layout.projectDirectory.dir("img").asFile.absolutePath)
}

// Adicionar manifest ao desktopJar para tornÃƒÆ’Ã‚Â¡-lo executÃƒÆ’Ã‚Â¡vel
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
