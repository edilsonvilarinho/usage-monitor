package com.usagemonitor.presentation.ui.theme

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AppMotionPolicyTest {

    @Test
    fun `a politica estatica anima transicoes e nao libera animacao continua`() {
        // É o default de `AppTheme`, e por isso o dos testes e dos geradores de
        // captura: continuar false aqui é o que mantém o `waitForIdle` seguro.
        assertFalse(AppMotionPolicy.Static.reduced)
        assertFalse(AppMotionPolicy.Static.continuous)
    }

    @Test
    fun `a preferencia de reduzir desliga transicao e animacao continua`() {
        assertEquals(AppMotionPolicy.Reduced, AppMotionPolicy.forPreference(reduceMotion = true))
        assertEquals(AppMotionPolicy.Live, AppMotionPolicy.forPreference(reduceMotion = false))
        assertTrue(AppMotionPolicy.Live.continuous)
        assertFalse(AppMotionPolicy.Reduced.continuous)
    }

    @Test
    fun `mola e tween viram snap com movimento reduzido`() {
        assertIs<SnapSpec<Float>>(appSpringSpec<Float>(AppMotion.Springs.GENTLE, AppMotionPolicy.Reduced))
        assertIs<SnapSpec<Float>>(appTweenSpec<Float>(AppMotion.normal, AppMotionPolicy.Reduced))
    }

    @Test
    fun `mola e tween seguem os tokens fora do movimento reduzido`() {
        val spring = assertIs<SpringSpec<Float>>(
            appSpringSpec<Float>(AppMotion.Springs.SNAPPY, AppMotionPolicy.Static)
        )
        assertEquals(AppMotion.Springs.SNAPPY.stiffness, spring.stiffness)
        assertEquals(AppMotion.Springs.SNAPPY.dampingRatio, spring.dampingRatio)

        val tween = assertIs<TweenSpec<Float>>(appTweenSpec<Float>(AppMotion.slow, AppMotionPolicy.Static))
        assertEquals(AppMotion.slow, tween.durationMillis)
    }

    @Test
    fun `as molas de dado nao tem rebote`() {
        // Barra, anel e número usam GENTLE; uma barra que passa do valor e volta
        // mostra por alguns quadros um percentual que não é verdade.
        assertEquals(1f, AppMotion.Springs.GENTLE.dampingRatio)
        assertEquals(1f, AppMotion.Springs.SNAPPY.dampingRatio)
        assertTrue(AppMotion.Springs.EXPRESSIVE.dampingRatio < 1f)
    }

    @Test
    fun `a saida e mais curta que a entrada mais curta`() {
        assertTrue(AppMotion.exit < AppMotion.fast)
    }
}
