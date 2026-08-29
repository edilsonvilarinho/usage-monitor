package com.usagemonitor.data

import com.usagemonitor.data.datasource.RemoteApiDataSource
import com.usagemonitor.data.dto.GitHubReleaseDto
import com.usagemonitor.data.repository.AppUpdateRepositoryImpl
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppUpdateReleaseNotesTest {

    private val releasePageUrl = "https://github.com/edilsonvilarinho/usage-monitor/releases/tag/v39.0.0"

    @Test
    fun `asks for the tag of the version that is running`() = runTest {
        var requestedTag: String? = null
        val repo = AppUpdateRepositoryImpl(
            fakeRemote(release(body = "- feat: algo (`abcdef1`)")) { tag -> requestedTag = tag }
        )

        repo.getReleaseNotes(version = "39.0.0", previousVersion = "37.0.0")

        // A tag leva o "v" que getLatestAvailableUpdate remove ao ler.
        assertEquals("v39.0.0", requestedTag)
    }

    @Test
    fun `maps the body, the publish date and both versions`() = runTest {
        val repo = AppUpdateRepositoryImpl(
            fakeRemote(
                release(
                    body = "## Changes\n\n- feat(update): mostra as novidades (`abcdef1`)",
                    publishedAt = "2026-08-24T21:15:00Z"
                )
            )
        )

        val notes = repo.getReleaseNotes(version = "39.0.0", previousVersion = "37.0.0").getOrNull()

        assertEquals("39.0.0", notes?.version)
        assertEquals("37.0.0", notes?.previousVersion)
        assertEquals(releasePageUrl, notes?.releasePageUrl)
        assertEquals(listOf("mostra as novidades"), notes?.items)
        assertEquals("2026-08-24T21:15:00Z", notes?.publishedAt?.toString())
    }

    @Test
    fun `a release with nothing user-facing yields no notes at all`() = runTest {
        // Sucesso com lista vazia não existe: quem chama trata um caso só, e uma
        // tela de novidades vazia afirmaria que a versão não trouxe nada.
        val repo = AppUpdateRepositoryImpl(fakeRemote(release(body = "- chore: bump version (`abcdef1`)")))

        val result = repo.getReleaseNotes(version = "39.0.0", previousVersion = null)

        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }

    @Test
    fun `a release without a body yields no notes`() = runTest {
        val repo = AppUpdateRepositoryImpl(fakeRemote(release(body = null)))

        assertNull(repo.getReleaseNotes(version = "39.0.0", previousVersion = null).getOrNull())
    }

    @Test
    fun `an unparseable publish date does not lose the notes`() = runTest {
        // A data é acessória: perder a lista inteira por causa dela seria trocar
        // o conteúdo pelo enfeite.
        val repo = AppUpdateRepositoryImpl(
            fakeRemote(release(body = "- fix: corrige algo (`abcdef1`)", publishedAt = "ontem"))
        )

        val notes = repo.getReleaseNotes(version = "39.0.0", previousVersion = null).getOrNull()

        assertEquals(listOf("corrige algo"), notes?.items)
        assertNull(notes?.publishedAt)
    }

    @Test
    fun `a network failure is a failure, not an empty release`() = runTest {
        // A distinção decide se a versão é marcada como já vista: falha tenta de
        // novo na abertura seguinte.
        val repo = AppUpdateRepositoryImpl(failingRemote())

        val result = repo.getReleaseNotes(version = "39.0.0", previousVersion = null)

        assertTrue(result.isFailure)
    }

    @Test
    fun `a tag that does not exist is an answer, not a failure`() = runTest {
        // O intervalo entre subir a versão no build.gradle.kts e criar a tag.
        // Como falha não marca a versão como vista, tratar isto como erro daria
        // uma requisição por abertura, para sempre, por uma resposta que não vai
        // mudar.
        val repo = AppUpdateRepositoryImpl(missingTagRemote())

        val result = repo.getReleaseNotes(version = "99.0.0", previousVersion = null)

        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }

    @Test
    fun `the feed override reaches the data source`() = runTest {
        // O smoke test da atualização serve uma release só, e o override
        // substitui a URL inteira em vez de virar um caminho de tag.
        var seenOverride: String? = null
        val remote = object : RemoteApiDataSource(noopHttpClient()) {
            override suspend fun fetchGitHubReleaseByTag(
                owner: String,
                repository: String,
                tag: String,
                feedUrlOverride: String?
            ): GitHubReleaseDto? {
                seenOverride = feedUrlOverride
                return release(body = "- feat: algo (`abcdef1`)")
            }
        }

        AppUpdateRepositoryImpl(remote, envVarReader = { "https://example.invalid/release.json" })
            .getReleaseNotes(version = "39.0.0", previousVersion = null)

        assertEquals("https://example.invalid/release.json", seenOverride)
    }

    /**
     * O data source de verdade, sem fake por cima.
     *
     * Os demais casos deste arquivo sobrescrevem `fetchGitHubReleaseByTag` e por
     * isso não executam uma linha de HTTP — é a armadilha registrada na issue
     * #94. A tradução de 404 acontece exatamente ali, então ela precisa de um
     * teste que passe pelo `HttpClient`.
     */
    @Test
    fun `the data source turns a 404 into null and keeps other statuses failing`() = runTest {
        val notFound = RemoteApiDataSource(
            jsonHttpClient { respond("Not Found", HttpStatusCode.NotFound) }
        )

        assertNull(notFound.fetchGitHubReleaseByTag("owner", "repo", "v99.0.0"))

        val unavailable = RemoteApiDataSource(
            jsonHttpClient { respond("upstream down", HttpStatusCode.ServiceUnavailable) }
        )

        // 5xx continua erro: um problema de disponibilidade passando por
        // "release sem novidades" marcaria a versão como vista sem ninguém ver
        // nada.
        assertFailsWith<IllegalStateException> {
            unavailable.fetchGitHubReleaseByTag("owner", "repo", "v39.0.0")
        }
    }

    private fun jsonHttpClient(handler: MockRequestHandler): HttpClient {
        return HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    private fun release(
        body: String?,
        publishedAt: String? = null
    ) = GitHubReleaseDto(
        tagName = "v39.0.0",
        htmlUrl = releasePageUrl,
        body = body,
        publishedAt = publishedAt
    )

    private fun fakeRemote(
        release: GitHubReleaseDto,
        onTag: (String) -> Unit = {}
    ): RemoteApiDataSource {
        return object : RemoteApiDataSource(noopHttpClient()) {
            override suspend fun fetchGitHubReleaseByTag(
                owner: String,
                repository: String,
                tag: String,
                feedUrlOverride: String?
            ): GitHubReleaseDto? {
                onTag(tag)
                return release
            }
        }
    }

    private fun failingRemote(): RemoteApiDataSource {
        return object : RemoteApiDataSource(noopHttpClient()) {
            override suspend fun fetchGitHubReleaseByTag(
                owner: String,
                repository: String,
                tag: String,
                feedUrlOverride: String?
            ): GitHubReleaseDto? {
                throw IllegalStateException("GitHub release HTTP 503: service unavailable")
            }
        }
    }

    /** Tag inexistente: o data source devolve `null` em vez de lançar. */
    private fun missingTagRemote(): RemoteApiDataSource {
        return object : RemoteApiDataSource(noopHttpClient()) {
            override suspend fun fetchGitHubReleaseByTag(
                owner: String,
                repository: String,
                tag: String,
                feedUrlOverride: String?
            ): GitHubReleaseDto? = null
        }
    }

    // HttpClient ocioso: o fake sobrescreve o método usado, mas a classe pai exige um cliente.
    private fun noopHttpClient(): HttpClient {
        return HttpClient(MockEngine { respond("") })
    }
}
