package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.repository.KiloRepository
import com.usagemonitor.domain.repository.GeminiRepository
import com.usagemonitor.domain.repository.CursorRepository
import com.usagemonitor.domain.repository.AntigravityRepository
import com.usagemonitor.domain.repository.OpenCodeGoRepository
import com.usagemonitor.domain.repository.OpenCodeRepository
import com.usagemonitor.domain.repository.OpenRouterRepository
import com.usagemonitor.domain.usecase.GetKiloUsageUseCase
import com.usagemonitor.domain.usecase.GetGeminiUsageUseCase
import com.usagemonitor.domain.usecase.GetCursorUsageUseCase
import com.usagemonitor.domain.usecase.GetAntigravityUsageUseCase
import com.usagemonitor.domain.usecase.GetOpenCodeGoUsageUseCase
import com.usagemonitor.domain.usecase.GetOpenCodeUsageUseCase
import com.usagemonitor.domain.usecase.GetOpenRouterUsageUseCase

// Os casos de uso que o `DashboardViewModel` recebe por default quando a build
// não liga a fonte. Cada um falha dizendo o que falta, em vez de deixar o card
// em carga eterna. Fora da classe pelo limite de 800 linhas (#304).

internal fun unavailableOpenCodeUsage(): GetOpenCodeUsageUseCase = GetOpenCodeUsageUseCase(
    object : OpenCodeRepository {
        override suspend fun getUsage(): Result<ApiUsageStats> {
            return Result.failure(IllegalStateException("OpenCode local database is unavailable"))
        }
    }
)

internal fun unavailableOpenCodeGoUsage(): GetOpenCodeGoUsageUseCase = GetOpenCodeGoUsageUseCase(
    object : OpenCodeGoRepository {
        override suspend fun getUsage(): Result<ApiUsageStats> {
            return Result.failure(
                IllegalStateException(
                    "Chave da API OpenCode não configurada. Abra Configurações > APIs e informe a chave."
                )
            )
        }
    }
)

internal fun unavailableKiloUsage(): GetKiloUsageUseCase = GetKiloUsageUseCase(
    object : KiloRepository {
        override suspend fun getUsage(): Result<ApiUsageStats> {
            return Result.failure(IllegalStateException("Kilo local database is unavailable"))
        }
    }
)

internal fun unavailableOpenRouterUsage(): GetOpenRouterUsageUseCase = GetOpenRouterUsageUseCase(
    object : OpenRouterRepository {
        override suspend fun getUsage(): Result<ApiUsageStats> {
            return Result.failure(
                IllegalStateException(
                    "Chave da API OpenRouter não configurada. Abra Configurações > APIs e informe a chave."
                )
            )
        }
    }
)

internal fun unavailableGeminiUsage(): GetGeminiUsageUseCase = GetGeminiUsageUseCase(
    object : GeminiRepository {
        override suspend fun getUsage(): Result<ApiUsageStats> {
            return Result.failure(IllegalStateException("Gemini CLI local usage is unavailable"))
        }
    }
)

internal fun unavailableCursorUsage(): GetCursorUsageUseCase = GetCursorUsageUseCase(
    object : CursorRepository {
        override suspend fun getUsage(): Result<ApiUsageStats> {
            return Result.failure(IllegalStateException("Cursor local session usage is unavailable"))
        }
    }
)

internal fun unavailableAntigravityUsage(): GetAntigravityUsageUseCase = GetAntigravityUsageUseCase(
    object : AntigravityRepository {
        override suspend fun getUsage(): Result<ApiUsageStats> {
            return Result.failure(IllegalStateException("Antigravity CLI usage is unavailable"))
        }
    }
)
