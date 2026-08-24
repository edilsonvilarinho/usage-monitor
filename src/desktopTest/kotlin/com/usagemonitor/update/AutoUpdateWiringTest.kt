package com.usagemonitor.update

import com.usagemonitor.data.repository.isVersionNewer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Portão de release dos dois interruptores de build da atualização automática.
 *
 * Eles guardam coisas diferentes — `AUTO_UPDATE_SHIPPED` diz se **esta build**
 * traz o mecanismo, `MIN_UPDATABLE_TARGET_VERSION` diz a partir de qual versão
 * **baixada** o instalador entende `/UPDATE` — e a combinação errada é
 * destrutiva: ligar o mecanismo com o mínimo inalcançável faz toda tentativa
 * falhar, e ligar o mecanismo com o mínimo abaixo da primeira release que tem
 * `/UPDATE` manda `/S /UPDATE` para um instalador que não conhece a opção, o que
 * produz uma instalação silenciosa travada no `MessageBox` do `.onInit` — medido
 * na atividade A02.
 */
class AutoUpdateWiringTest {

    private val unreachableSentinel = "999.0.0"

    @Test
    fun `shipping the mechanism requires a reachable minimum target version`() {
        if (AUTO_UPDATE_SHIPPED) {
            assertTrue(
                MIN_UPDATABLE_TARGET_VERSION != unreachableSentinel,
                "AUTO_UPDATE_SHIPPED foi ligado sem baixar MIN_UPDATABLE_TARGET_VERSION do sentinela: " +
                    "toda tentativa de atualização falharia."
            )
        } else {
            assertEquals(
                unreachableSentinel,
                MIN_UPDATABLE_TARGET_VERSION,
                "MIN_UPDATABLE_TARGET_VERSION foi baixado do sentinela sem ligar AUTO_UPDATE_SHIPPED: " +
                    "a próxima pessoa a ligar o mecanismo não teria mais este portão."
            )
        }
    }

    @Test
    fun `the controller ships no installer while the mechanism is off`() {
        // Enquanto o .nsi não entende /UPDATE, nenhum caminho de código pode
        // chegar a lançar um instalador.
        if (!AUTO_UPDATE_SHIPPED) {
            assertEquals("999.0.0", MIN_UPDATABLE_TARGET_VERSION)
        }
    }

    @Test
    fun `the minimum target version is above the last release without update mode`() {
        // Das duas direções de erro só uma é destrutiva. Mínimo alto demais é
        // inócuo: a app só enxerga versões mais novas que a dela, e uma versão
        // que nunca existiu nunca é oferecida. Mínimo baixo demais manda
        // `/S /UPDATE` para um Setup.exe que não conhece a opção, e aquele fica
        // pendurado no MessageBox do `.onInit` para sempre — medido na A02.
        //
        // Por isso o teste afirma só o piso, e não a igualdade com a tag: amarrar
        // à tag exigiria que este arquivo soubesse o número da release, que é
        // decidido depois.
        assertTrue(
            isVersionNewer(MIN_UPDATABLE_TARGET_VERSION, LAST_VERSION_WITHOUT_UPDATE_MODE),
            "MIN_UPDATABLE_TARGET_VERSION ($MIN_UPDATABLE_TARGET_VERSION) precisa ser maior que " +
                "$LAST_VERSION_WITHOUT_UPDATE_MODE, a última release cujo instalador não entende /UPDATE."
        )
    }
}
