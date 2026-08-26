package com.usagemonitor.update

import com.usagemonitor.domain.entity.AppUpdatePlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A plataforma escolhe **qual instalador o texto da tela nomeia**. Errar aqui
 * não deixa rastro nenhum: o interruptor continua desabilitado, só que com o
 * motivo de outra plataforma.
 */
class CurrentUpdatePlatformTest {

    @Test
    fun `windows names are recognized`() {
        assertEquals(AppUpdatePlatform.WINDOWS, currentUpdatePlatform("Windows 11"))
        assertEquals(AppUpdatePlatform.WINDOWS, currentUpdatePlatform("Windows Server 2022"))
    }

    @Test
    fun `linux names are recognized`() {
        assertEquals(AppUpdatePlatform.LINUX, currentUpdatePlatform("Linux"))
    }

    /**
     * O JDK devolve `Mac OS X` — a string não contém "darwin", e é por isso que
     * as duas grafias são aceitas.
     */
    @Test
    fun `mac names are recognized in both spellings`() {
        assertEquals(AppUpdatePlatform.MACOS, currentUpdatePlatform("Mac OS X"))
        assertEquals(AppUpdatePlatform.MACOS, currentUpdatePlatform("Darwin"))
    }

    /**
     * `null` e não um chute: nomear o instalador errado é pior que não nomear
     * nenhum, e a tela tem texto genérico exatamente para este caso.
     */
    @Test
    fun `anything else is unknown rather than a guess`() {
        assertNull(currentUpdatePlatform("SunOS"))
        assertNull(currentUpdatePlatform(""))
    }
}
