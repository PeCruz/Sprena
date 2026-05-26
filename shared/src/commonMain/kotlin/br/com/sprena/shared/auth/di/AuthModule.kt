package br.com.sprena.shared.auth.di

import br.com.sprena.shared.auth.domain.usecase.LoginUseCase
import br.com.sprena.shared.auth.domain.usecase.LogoutUseCase
import br.com.sprena.shared.auth.domain.usecase.RequestPasswordResetUseCase
import br.com.sprena.shared.auth.domain.usecase.RestoreSessionUseCase
import org.koin.dsl.module

/**
 * Módulo Koin de autenticação (commonMain).
 *
 * NÃO declara `AuthRepository` — a impl é Android-only (`FirebaseAuthRepositoryImpl`),
 * declarada em `composeApp/PlatformModule.android.kt`. Mesma estratégia para `SessionStore`
 * (ver [sessionModule]).
 */
fun authModule() =
    module {
        factory { LoginUseCase(authRepository = get(), sessionStore = get(), clock = get(), logger = get()) }
        factory { LogoutUseCase(authRepository = get(), sessionStore = get(), logger = get()) }
        factory { RequestPasswordResetUseCase(authRepository = get(), logger = get()) }
        factory { RestoreSessionUseCase(authRepository = get(), sessionStore = get(), clock = get(), logger = get()) }
    }
