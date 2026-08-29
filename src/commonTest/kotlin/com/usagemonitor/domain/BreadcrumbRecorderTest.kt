package com.usagemonitor.domain

import com.usagemonitor.domain.entity.BreadcrumbCategory
import com.usagemonitor.domain.repository.NoOpBreadcrumbRecorder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BreadcrumbRecorderTest {

    /**
     * O recorder nulo existe para composições sem disco. Ele tem de aceitar a
     * chamada em silêncio — quem o injeta não pode precisar saber que é ele.
     */
    @Test
    fun `the no-op recorder accepts every write and reads back nothing`() {
        BreadcrumbCategory.entries.forEach { category ->
            NoOpBreadcrumbRecorder.record(category, "passo qualquer")
        }

        assertTrue(NoOpBreadcrumbRecorder.read(limit = 30).isEmpty())
        assertEquals(emptyList(), NoOpBreadcrumbRecorder.read(limit = 0))
    }
}
