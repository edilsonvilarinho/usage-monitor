package com.usagemonitor.domain

import com.usagemonitor.domain.entity.compareAppVersions
import com.usagemonitor.domain.entity.isVersionNewer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppVersionComparisonTest {

    @Test
    fun `pre-release suffix is stripped via substringBefore dash`() {
        // Comportamento atual: "8.0.1-beta" -> [8,0,1] que é > "8.0.0".
        assertTrue(isVersionNewer(candidateVersion = "8.0.1-beta", currentVersion = "8.0.0"))
        // E "8.0.0-beta" tratado igual a "8.0.0".
        assertEquals(false, isVersionNewer(candidateVersion = "8.0.0-beta", currentVersion = "8.0.0"))
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
