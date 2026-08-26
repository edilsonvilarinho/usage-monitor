package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.CliSessionsLabels
import com.usagemonitor.presentation.ui.resumeSessionCommand
import com.usagemonitor.presentation.ui.shortSessionId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private const val SESSION_ID = "f66da412-9c3a-4e51-b7d2-1a8e0c5d7734"

class ResumeSessionCommandTest {

    @Test
    fun `the command carries the whole session id`() {
        assertEquals("claude --resume $SESSION_ID", resumeSessionCommand(SESSION_ID))
    }

    /**
     * O prefixo de oito caracteres que a tela mostra não retoma a sessão — com
     * ele o `--resume` cai no seletor interativo. É a razão de o botão existir.
     */
    @Test
    fun `the command is not the shortened id shown on screen`() {
        val command = resumeSessionCommand(SESSION_ID)

        assertTrue(command.endsWith(SESSION_ID))
        assertNotEquals("claude --resume ${shortSessionId(SESSION_ID)}", command)
    }

    @Test
    fun `copy labels exist in both languages`() {
        assertEquals("Copiar comando de retomada", CliSessionsLabels.copyResumeCommand(AppLanguage.PT))
        assertEquals("Copy resume command", CliSessionsLabels.copyResumeCommand(AppLanguage.EN))
        assertEquals("Copiado", CliSessionsLabels.copied(AppLanguage.PT))
        assertEquals("Copied", CliSessionsLabels.copied(AppLanguage.EN))
    }
}
