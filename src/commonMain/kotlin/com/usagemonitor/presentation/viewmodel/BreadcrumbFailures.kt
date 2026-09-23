package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.BreadcrumbCategory
import com.usagemonitor.domain.entity.breadcrumbFailureReasonOf
import com.usagemonitor.domain.entity.sanitizeBreadcrumbErrorMessage
import com.usagemonitor.domain.repository.BreadcrumbRecorder
import kotlinx.coroutines.CancellationException

/**
 * Registra uma falha no limite que a consome. Cancelamento é fluxo de controle:
 * ele sobe sem ser convertido em erro nem em breadcrumb.
 */
fun BreadcrumbRecorder.recordFailure(operation: String, error: Throwable) {
    if (error is CancellationException) {
        throw error
    }
    record(
        category = BreadcrumbCategory.ERROR,
        message = "$operation falhou: ${breadcrumbFailureReasonOf(error)}"
    )
}

/** Registra uma falha estruturada que não veio de uma exceção. */
fun BreadcrumbRecorder.recordFailure(operation: String, detail: String) {
    record(
        category = BreadcrumbCategory.ERROR,
        message = "$operation falhou: ${sanitizeBreadcrumbErrorMessage(detail)}"
    )
}
