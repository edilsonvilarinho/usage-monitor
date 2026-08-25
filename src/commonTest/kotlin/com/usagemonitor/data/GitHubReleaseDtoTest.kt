package com.usagemonitor.data

import com.usagemonitor.data.dto.GitHubReleaseDto
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Desserialização do corpo de `GET /repos/{owner}/{repo}/releases/latest`.
 *
 * O recorte abaixo é o do v37.0.0 real, com os campos que o app não declara
 * mantidos de propósito: é o `ignoreUnknownKeys` do cliente de produção que os
 * ignora, e um teste com o JSON já podado não provaria isso.
 */
class GitHubReleaseDtoTest {

    // Mesma configuração do cliente montado em Main.kt.
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun `reads tag, size and sha-256 digest from a real release payload`() {
        val release = json.decodeFromString<GitHubReleaseDto>(REAL_PAYLOAD)

        assertEquals("v37.0.0", release.tagName)
        assertEquals(2, release.assets.size)

        val setup = release.assets.first { it.name == "UsageMonitor-Setup-37.0.0.exe" }
        assertEquals(120054859L, setup.size)
        assertEquals(
            "sha256:23222796bb56197309fb3fec5a6705dd4c016ec65996f4ddf0470af7d3cc40e3",
            setup.digest
        )
        assertEquals(
            "https://github.com/edilsonvilarinho/usage-monitor/releases/download/v37.0.0/UsageMonitor-Setup-37.0.0.exe",
            setup.browserDownloadUrl
        )
    }

    @Test
    fun `asset without digest deserializes to null instead of failing the whole check`() {
        val release = json.decodeFromString<GitHubReleaseDto>(REAL_PAYLOAD)

        val legacy = release.assets.first { it.name == "checksums.txt" }

        assertNull(legacy.digest)
        assertEquals(1024L, legacy.size)
    }

    @Test
    fun `asset without size deserializes to null, never to zero`() {
        val payload = """
            {
              "tag_name": "v40.0.0",
              "html_url": "https://example.test/tag/v40.0.0",
              "assets": [
                {
                  "name": "UsageMonitor-Setup-40.0.0.exe",
                  "browser_download_url": "https://example.test/setup.exe"
                }
              ]
            }
        """.trimIndent()

        val asset = json.decodeFromString<GitHubReleaseDto>(payload).assets.single()

        // Zero afirmaria um artefato vazio e reprovaria o download; nulo diz
        // "não informado" e deixa o SHA-256 ser o portão.
        assertNull(asset.size)
        assertNull(asset.digest)
    }

    @Test
    fun `release without assets deserializes to an empty list`() {
        val payload = """
            {"tag_name":"v41.0.0","html_url":"https://example.test/tag/v41.0.0"}
        """.trimIndent()

        val release = json.decodeFromString<GitHubReleaseDto>(payload)

        assertEquals(emptyList(), release.assets)
    }

    private companion object {
        val REAL_PAYLOAD = """
            {
              "url": "https://api.github.com/repos/edilsonvilarinho/usage-monitor/releases/1",
              "tag_name": "v37.0.0",
              "name": "v37.0.0",
              "draft": false,
              "prerelease": false,
              "html_url": "https://github.com/edilsonvilarinho/usage-monitor/releases/tag/v37.0.0",
              "body": "notas da versao",
              "published_at": "2026-08-22T10:00:00Z",
              "assets": [
                {
                  "url": "https://api.github.com/repos/edilsonvilarinho/usage-monitor/releases/assets/1",
                  "id": 1,
                  "name": "UsageMonitor-Setup-37.0.0.exe",
                  "label": null,
                  "content_type": "application/x-msdownload",
                  "state": "uploaded",
                  "size": 120054859,
                  "digest": "sha256:23222796bb56197309fb3fec5a6705dd4c016ec65996f4ddf0470af7d3cc40e3",
                  "download_count": 12,
                  "browser_download_url": "https://github.com/edilsonvilarinho/usage-monitor/releases/download/v37.0.0/UsageMonitor-Setup-37.0.0.exe"
                },
                {
                  "url": "https://api.github.com/repos/edilsonvilarinho/usage-monitor/releases/assets/2",
                  "id": 2,
                  "name": "checksums.txt",
                  "content_type": "text/plain",
                  "state": "uploaded",
                  "size": 1024,
                  "browser_download_url": "https://github.com/edilsonvilarinho/usage-monitor/releases/download/v37.0.0/checksums.txt"
                }
              ]
            }
        """.trimIndent()
    }
}
