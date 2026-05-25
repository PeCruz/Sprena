package br.com.sprena.shared.auth.di

import br.com.sprena.shared.auth.data.repository.MockAuthRepository
import br.com.sprena.shared.auth.domain.repository.AuthRepository
import br.com.sprena.shared.auth.domain.usecase.LoginUseCase
import org.koin.dsl.module

/**
 * Módulo Koin de autenticação.
 *
 * Registra:
 * - [AuthRepository] → [MockAuthRepository] (substituir por Firebase Auth futuramente)
 * - [LoginUseCase]
 */
fun authModule() =
    module {
        single<AuthRepository> { MockAuthRepository() }
        factory { LoginUseCase(authRepository = get(), logger = get()) }
    }
