package com.usagemonitor.domain

import com.usagemonitor.domain.entity.compareAppVersions
import com.usagemonitor.domain.entity.isPrereleaseVersion
import com.usagemonitor.domain.entity.isVersionNewer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppVersionComparisonTest {

    /**
     * Precedência do SemVer (issue #355): antes o sufixo era descartado e
     * `beta.1`, `beta.2` e a estável comparavam iguais — o canal beta nunca
     * oferecia a próxima beta nem a estável a quem já estava numa beta.
     */
    @Test
    fun `prerelease versions follow semver precedence`() {
        assertTrue(compareAppVersions("42.0.0-beta.1", "42.0.0-beta.2") < 0)
        assertTrue(compareAppVersions("42.0.0-beta.2", "42.0.0") < 0)
        assertTrue(compareAppVersions("42.0.0-beta.9", "42.0.0-beta.10") < 0)
        assertTrue(compareAppVersions("42.0.0-beta.1", "41.9.9") > 0)
        assertEquals(0, compareAppVersions("v42.0.0-beta.1", "42.0.0-beta.1"))
    }

    @Test
    fun `a prerelease of the next version is newer than the current stable`() {
        assertTrue(isVersionNewer(candidateVersion = "8.0.1-beta", currentVersion = "8.0.0"))
        assertEquals(false, isVersionNewer(candidateVersion = "8.0.0-beta", currentVersion = "8.0.0"))
        assertTrue(isVersionNewer(candidateVersion = "8.0.0", currentVersion = "8.0.0-beta"))
    }

    @Test
    fun `prerelease identifiers compare the semver way`() {
        // Número antes de texto; lista mais curta antes quando é prefixo.
        assertTrue(compareAppVersions("1.0.0-1", "1.0.0-beta") < 0)
        assertTrue(compareAppVersions("1.0.0-alpha", "1.0.0-beta") < 0)
        assertTrue(compareAppVersions("1.0.0-beta", "1.0.0-beta.1") < 0)
        assertTrue(compareAppVersions("1.0.0-beta.99999999999", "1.0.0-beta.100000000000") < 0)
    }

    @Test
    fun `build metadata does not affect precedence`() {
        assertEquals(0, compareAppVersions("42.0.0+abc", "42.0.0"))
    }

    @Test
    fun `detects prerelease versions`() {
        assertTrue(isPrereleaseVersion("42.0.0-beta.1"))
        assertTrue(isPrereleaseVersion("v42.0.0-beta.1"))
        assertFalse(isPrereleaseVersion("42.0.0"))
        assertFalse(isPrereleaseVersion("sem-numero"))
    }

    /**
     * O sinal é o que separa atualização de retrocesso. O booleano de
     * [isVersionNewer] colapsa "mais antiga" e "igual" no mesmo `false`, e é
     * justamente essa distinção que a decisão das novidades precisa.
     */
    @Test
    fun `the sign tells an upgrade from a downgrade`() {
        assertTrue(compareAppVersions("38.0.2", "38.0.1") > 0)
        assertTrue(compareAppVersions("38.0.1", "38.0.2") < 0)
        assertEquals(0, compareAppVersions("38.0.2", "38.0.2"))
    }

    @Test
    fun `the tag prefix is not part of the number`() {
        assertEquals(0, compareAppVersions("v38.0.2", "38.0.2"))
    }

    @Test
    fun `the same version written differently compares equal`() {
        // Strings diferentes, versão igual: quem decide pela igualdade textual
        // vê duas coisas, quem decide pelo número vê uma.
        assertEquals(0, compareAppVersions("38.0.2", "38.0.02"))
    }

    @Test
    fun `missing components count as zero`() {
        assertEquals(0, compareAppVersions("38.1", "38.1.0"))
        assertTrue(compareAppVersions("39", "38.9.9") > 0)
    }

    /**
     * Falha fechado: versão ilegível não lança e não vira "mais nova". As duas
     * decisões que dependem daqui leem "iguais" como não fazer nada.
     */
    @Test
    fun `an unreadable version compares equal instead of throwing`() {
        assertEquals(0, compareAppVersions("sem-numero", ""))
        assertEquals(false, isVersionNewer(candidateVersion = "sem-numero", currentVersion = "38.0.2"))
    }
}
