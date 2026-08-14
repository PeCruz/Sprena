package br.com.sprena.shared.privacy.di

import br.com.sprena.shared.privacy.domain.usecase.AcceptConsentUseCase
import br.com.sprena.shared.privacy.domain.usecase.CheckConsentUseCase
import org.koin.dsl.module

/**
 * Módulo Koin de privacidade (commonMain).
 *
 * NÃO declara `ConsentRepository` — a impl é Android-only
 * (`FirestoreConsentRepository`), declarada em `composeApp/PlatformModule.android.kt`.
 * Mesma estratégia do [br.com.sprena.shared.auth.di.authModule].
 */
fun privacyModule() =
    module {
        factory { CheckConsentUseCase(repository = get(), logger = get()) }
        factory { AcceptConsentUseCase(repository = get(), logger = get()) }
    }
