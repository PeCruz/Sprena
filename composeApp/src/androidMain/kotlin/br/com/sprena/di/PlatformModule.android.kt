package br.com.sprena.di

import br.com.sprena.BuildConfig
import br.com.sprena.shared.account.data.repository.FirestoreUserProfileRepository
import br.com.sprena.shared.account.data.repository.FunctionsAccountDeletionRepository
import br.com.sprena.shared.account.domain.repository.AccountDeletionRepository
import br.com.sprena.shared.account.domain.repository.UserProfileRepository
import br.com.sprena.shared.auth.data.repository.FirebaseAuthRepositoryImpl
import br.com.sprena.shared.auth.domain.repository.AuthRepository
import br.com.sprena.shared.privacy.data.repository.FirestoreConsentRepository
import br.com.sprena.shared.privacy.domain.repository.ConsentRepository
import br.com.sprena.shared.sportclient.data.repository.SportClientRepositoryImpl
import br.com.sprena.shared.sportclient.domain.repository.SportClientRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.functions
import org.koin.dsl.module

/**
 * Região da Cloud Function `deleteMyAccount` (F1.6a).
 *
 * **Precisa bater com `FUNCTIONS_REGION` em `functions/src/index.ts`.** Divergência
 * devolve `NOT_FOUND` no cliente, que é indistinguível de "função não deployada" — o
 * sintoma não aponta para a causa, por isso as duas constantes estão documentadas
 * juntas em SECURITY.md.
 */
private const val FUNCTIONS_REGION = "southamerica-east1"

/**
 * Módulo Koin com dependências específicas da plataforma Android.
 *
 * Responsabilidades:
 *  - Fornecer [FirebaseFirestore] como singleton
 *  - Fornecer [FirebaseAuth] como singleton
 *  - Binding das implementações Android dos repositórios (Firebase)
 *
 * ⚠️ Configuração do Firebase:
 *  1. Coloque o arquivo `google-services.json` em `composeApp/`
 *  2. O plugin `google-services` lê as credenciais automaticamente
 *  3. O applicationId deve ser `br.com.sprena` (registrado no Firebase Console)
 */
fun platformModule() =
    module {
        // Firebase Firestore instance
        single<FirebaseFirestore> { Firebase.firestore }

        // Firebase Auth instance
        single<FirebaseAuth> { Firebase.auth }

        // Repository bindings (interface → Firestore/Firebase implementation)
        single<SportClientRepository> { SportClientRepositoryImpl(firestore = get(), logger = get()) }
        single<AuthRepository> {
            FirebaseAuthRepositoryImpl(
                auth = get(),
                firestore = get(),
                logger = get(),
            )
        }

        // F1.5: aceite da política de privacidade. `appVersion` vem do BuildConfig do
        // composeApp — o módulo shared não tem BuildConfig próprio.
        single<ConsentRepository> {
            FirestoreConsentRepository(
                firestore = get(),
                appVersion = BuildConfig.VERSION_NAME,
                logger = get(),
            )
        }

        // F1.6a: direitos do titular. O perfil junta `users` (só leitura) com o sidecar
        // `user_profiles`; a exclusão passa por Cloud Function porque cascade delete e
        // remoção do usuário do Auth exigem Admin SDK.
        single<FirebaseFunctions> { Firebase.functions(FUNCTIONS_REGION) }
        single<UserProfileRepository> {
            FirestoreUserProfileRepository(
                firestore = get(),
                auth = get(),
                logger = get(),
            )
        }
        single<AccountDeletionRepository> {
            FunctionsAccountDeletionRepository(functions = get(), logger = get())
        }
    }
