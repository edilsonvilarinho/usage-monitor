import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

version = "8.0.0"

val appVersion = version.toString()
val generatedAppVersionDir = layout.buildDirectory.dir("generated/app-version/desktopMain/kotlin")

kotlin {
    // ÃƒÅ¡nico alvo: Desktop JVM.
    // O nome "desktop" define o source set desktopMain/desktopTest.
    jvm("desktop")

    // Java 17 ÃƒÂ© o mÃƒÂ­nimo recomendado para Compose Multiplatform Desktop
    jvmToolchain(17)

    sourceSets {

        // Ã¢â€â‚¬Ã¢â€â‚¬ commonMain Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
        // CÃƒÂ³digo compartilhado: domain, data e presentation.
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

                // Ktor Ã¢â‚¬â€ cliente HTTP (engine vem no desktopMain)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.json)
                implementation(libs.ktor.client.logging)

                // SerializaÃƒÂ§ÃƒÂ£o e utilitÃƒÂ¡rios KMP
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.multiplatform.settings)
            }
        }

        // Ã¢â€â‚¬Ã¢â€â‚¬ desktopMain Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
        // CÃƒÂ³digo especÃƒÂ­fico da plataforma Desktop (JVM):
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
            }
        }

        // Ã¢â€â‚¬Ã¢â€â‚¬ commonTest Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
        // Testes unitÃƒÂ¡rios: domain, mappers, ViewModel
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.client.mock)
            }
        }

        // Ã¢â€â‚¬Ã¢â€â‚¬ desktopTest Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
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

// ConfiguraÃƒÂ§ÃƒÂ£o da aplicaÃƒÂ§ÃƒÂ£o Desktop
compose.desktop {
    application {
        mainClass = "com.usagemonitor.MainKt"

        nativeDistributions {
            targetFormats(
                TargetFormat.Exe,
                TargetFormat.Msi,
                TargetFormat.Deb,
                TargetFormat.Rpm
            )
            packageName = "Usage Monitor"
            packageVersion = appVersion
            modules("java.sql")

            windows {
                iconFile.set(project.file("src/desktopMain/resources/icons/app_icon.ico"))
                menu = true
                shortcut = true
                perUserInstall = true
                dirChooser = true
                upgradeUuid = "D26C4B79-9F2B-4CE5-B94E-E2E6A2A9E4A4"
            }
            linux {
                iconFile.set(project.file("src/desktopMain/resources/icons/app_icon.png"))
            }
            macOS {
                iconFile.set(project.file("src/desktopMain/resources/icons/app_icon.icns"))
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

// Adicionar manifest ao desktopJar para tornÃƒÂ¡-lo executÃƒÂ¡vel
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


