package br.com.sprena.shared.account.di

import br.com.sprena.shared.account.domain.usecase.DeleteMyAccountUseCase
import br.com.sprena.shared.account.domain.usecase.ExportMyDataUseCase
import br.com.sprena.shared.account.domain.usecase.GetMyProfileUseCase
import br.com.sprena.shared.account.domain.usecase.SaveMyProfileUseCase
import org.koin.dsl.module

/**
 * Módulo Koin da conta do titular (commonMain).
 *
 * NÃO declara `UserProfileRepository` nem `AccountDeletionRepository` — as impls são
 * Android-only (Firestore e Cloud Functions), declaradas em
 * `composeApp/PlatformModule.android.kt`. Mesma estratégia de
 * [br.com.sprena.shared.privacy.di.privacyModule].
 */
fun accountModule() =
    module {
        factory { GetMyProfileUseCase(repository = get(), sessionStore = get(), logger = get()) }
        factory { SaveMyProfileUseCase(repository = get(), sessionStore = get(), logger = get()) }
        factory {
            ExportMyDataUseCase(
                profileRepository = get(),
                consentRepository = get(),
                sessionStore = get(),
                clock = get(),
                logger = get(),
            )
        }
        factory {
            DeleteMyAccountUseCase(
                deletionRepository = get(),
                authRepository = get(),
                sessionStore = get(),
                logger = get(),
            )
        }
    }
