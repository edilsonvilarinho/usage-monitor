package com.usagemonitor.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val RELEASE_URL = "https://github.com/edilsonvilarinho/usage-monitor/releases/tag/v1.0.0"

class DesktopAppUpdateReleaseOpenerTest {

    @Test
    fun `falls back to open on macOS when native browse is unavailable`() {
        val commands = mutableListOf<List<String>>()
        val opener = DesktopAppUpdateReleaseOpener(
            processLauncher = { command, _ ->
                commands += command
                throw UnsupportedOperationException("process not started in tests")
            },
            desktopBrowser = { false },
            osNameProvider = { "Mac OS X" }
        )

        opener.open(RELEASE_URL)

        assertEquals(listOf("open", RELEASE_URL), commands.single())
    }

    @Test
    fun `falls back to xdg-open on Linux when native browse is unavailable`() {
        val commands = mutableListOf<List<String>>()
        val opener = DesktopAppUpdateReleaseOpener(
            processLauncher = { command, _ ->
                commands += command
                throw UnsupportedOperationException("process not started in tests")
            },
            desktopBrowser = { false },
            osNameProvider = { "Linux" }
        )

        opener.open(RELEASE_URL)

        assertEquals(listOf("xdg-open", RELEASE_URL), commands.single())
    }

    @Test
    fun `does not launch any process when native browse handles the uri`() {
        val commands = mutableListOf<List<String>>()
        val opener = DesktopAppUpdateReleaseOpener(
            processLauncher = { command, _ ->
                commands += command
                throw UnsupportedOperationException("process not started in tests")
            },
            desktopBrowser = { true },
            osNameProvider = { "Mac OS X" }
        )

        val result = opener.open(RELEASE_URL)

        assertTrue(result.isSuccess)
        assertTrue(commands.isEmpty())
    }

    @Test
    fun `rejects urls outside the expected release path`() {
        val opener = DesktopAppUpdateReleaseOpener(
            processLauncher = { _, _ -> throw UnsupportedOperationException("must not launch") },
            desktopBrowser = { true },
            osNameProvider = { "Mac OS X" }
        )

        val result = opener.open("https://github.com/other/repo/releases/tag/v1.0.0")

        assertTrue(result.isFailure)
    }
}
