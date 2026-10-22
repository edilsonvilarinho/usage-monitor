package com.usagemonitor.terminal

import com.usagemonitor.CommandLineRequest
import com.usagemonitor.ConfigChange
import com.usagemonitor.ConfigKeys
import com.usagemonitor.ConfigValueType
import com.usagemonitor.data.datasource.LocalProxySettingsDataSource
import com.usagemonitor.domain.entity.CollectorKind
import com.usagemonitor.parseCommandLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** `usage-monitor config …` (#440, 4.3 do protótipo). */
class TerminalSettingCommandTest {

    @Test
    fun `config parses list, get and set, and a secret never comes from an argument`() {
        assertEquals(CommandLineRequest.Terminal(SettingCommand.List("alerts")), parseCommandLine(arrayOf("config", "list", "alerts")))
        assertEquals(CommandLineRequest.Terminal(SettingCommand.Get("language")), parseCommandLine(arrayOf("config", "get", "language")))
        assertEquals(CommandLineRequest.Terminal(SettingCommand.Set("proxy.password", null)), parseCommandLine(arrayOf("config", "set", "proxy.password")))
        for (arguments in listOf(
            arrayOf("config", "set", "proxy.password", "hunter2"),
            arrayOf("config", "set", "alerts.quiet"),
            arrayOf("config", "get", "nope"),
            arrayOf("config", "fly")
        )) {
            assertTrue(parseCommandLine(arguments) is CommandLineRequest.Invalid, arguments.joinToString(" "))
        }
    }

    @Test
    fun `config list shows the defaults of a section`() = TerminalConfigHarness().use { harness ->
        val (code, output) = harness.run("config", "list", "alerts")

        assertEquals(0, code)
        val lines = output.trimEnd().lines()
        assertEquals(ConfigKeys.ALL.count { key -> key.name.startsWith("alerts.") }, lines.size)
        // A coluna do valor começa depois do nome mais longo da seção, mais dois espaços.
        assertEquals("alerts.quota.enabled".padEnd("alerts.quota.account_thresholds".length + 2) + "true", lines.first())
        assertTrue(lines.any { line -> line.startsWith("alerts.quota.thresholds") && line.endsWith("75,90,100") })
        assertTrue(lines.any { line -> line.startsWith("alerts.quiet") && line.endsWith("desligado") })
    }

    @Test
    fun `a value is normalized, saved and read back by get`() = TerminalConfigHarness().use { harness ->
        assertEquals(0 to "✓ alerts.quota.thresholds = 70,90 · vale quando o app ou o serviço abrir\n", harness.run("config", "set", "alerts.quota.thresholds", "90, 70"))
        assertEquals(0 to "70,90\n", harness.run("config", "get", "alerts.quota.thresholds"))
        assertEquals(0 to "✓ alerts.quiet = 22-07 · vale quando o app ou o serviço abrir\n", harness.run("config", "set", "alerts.quiet", "22:00-07:00"))
        assertEquals(0 to "22-07\n", harness.run("config", "get", "alerts.quiet"))
        harness.run("config", "set", "alerts.quiet", "off")
        assertEquals(0 to "off\n", harness.run("config", "get", "alerts.quiet"))
    }

    @Test
    fun `a value the setting does not take is a usage error that explains what it takes`() = TerminalConfigHarness().use { harness ->
        val (code, output) = harness.run("config", "set", "alerts.spike.factor", "1")

        assertEquals(2, code)
        assertEquals("▲ 'alerts.spike.factor' não aceita esse valor. Use um fator de 1.5 a 100, como 2.\n", output)
    }

    @Test
    fun `the proxy applies after a restart of whoever collects`() = TerminalConfigHarness(collector = CollectorKind.SERVICE).use { harness ->
        assertEquals(0 to "✓ proxy.port = 3128 · vale depois de usage-monitor service restart\n", harness.run("config", "set", "proxy.port", "3128"))
    }

    @Test
    fun `a secret is read from the input, saved, and never shown`() = TerminalConfigHarness().use { harness ->
        assertEquals(0 to "✓ proxy.password salva · vale quando o app ou o serviço abrir\n", harness.run("config", "set", "proxy.password", secret = "hunter2"))

        assertEquals("hunter2", LocalProxySettingsDataSource(harness.file("proxy.json")).load().password)
        assertEquals(0 to "••••\n", harness.run("config", "get", "proxy.password"))
        assertFalse(harness.run("config", "list", "proxy").second.contains("hunter2"))
        assertFalse(ConfigChange.SetValue("proxy.password", "hunter2").toString().contains("hunter2"))
        assertEquals(1, harness.run("config", "set", "proxy.password", secret = "").first)
    }

    /** Todo ajuste aceita um valor de exemplo e o devolve igual pelo `get` (menos o que mexe no sistema). */
    @Test
    fun `every setting round-trips a sample value`() = TerminalConfigHarness().use { harness ->
        val samples = mapOf(
            ConfigValueType.BOOLEAN to "false", ConfigValueType.PERCENTS to "60,80", ConfigValueType.MINUTES to "45",
            ConfigValueType.MINUTES_OR_OFF to "20", ConfigValueType.FACTOR to "2.5", ConfigValueType.QUIET_HOURS to "23-06",
            ConfigValueType.HOUR_OR_OFF to "9", ConfigValueType.USD_OR_OFF to "150", ConfigValueType.LANGUAGE to "en",
            ConfigValueType.TEXT to "proxy.local", ConfigValueType.PORT to "8080", ConfigValueType.CLEAR_ONLY to "off"
        )
        for (key in ConfigKeys.ALL.filter { key -> !key.secret && key.name != "system.autostart" }) {
            val sample = samples.getValue(key.type)
            val (code, output) = harness.run("config", "set", key.name, sample)
            assertEquals(0, code, "${key.name}: $output")
            assertEquals(sample, harness.run("config", "get", key.name).second.trim(), key.name)
        }
    }
}
