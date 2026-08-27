package br.com.sprena.di

import br.com.sprena.BuildConfig
import br.com.sprena.shared.account.data.repository.FirestoreUserProfileRepository
import br.com.sprena.shared.account.data.repository.FunctionsAccountBootstrapRepository
import br.com.sprena.shared.account.data.repository.FunctionsAccountDeletionRepository
import br.com.sprena.shared.account.domain.repository.AccountBootstrapRepository
import br.com.sprena.shared.account.domain.repository.AccountDeletionRepository
import br.com.sprena.shared.account.domain.repository.UserProfileRepository
import br.com.sprena.shared.auth.data.repository.FirebaseAuthRepositoryImpl
import br.com.sprena.shared.auth.domain.repository.AuthRepository
import br.com.sprena.shared.establishment.data.repository.FirestoreActiveEstablishmentRepository
import br.com.sprena.shared.establishment.data.repository.FirestoreEstablishmentRepository
import br.com.sprena.shared.establishment.data.repository.FirestoreMembershipRepository
import br.com.sprena.shared.establishment.data.repository.FunctionsMemberMutationRepository
import br.com.sprena.shared.establishment.domain.repository.ActiveEstablishmentRepository
import br.com.sprena.shared.establishment.domain.repository.EstablishmentRepository
import br.com.sprena.shared.establishment.domain.repository.MemberMutationRepository
import br.com.sprena.shared.establishment.domain.repository.MembershipRepository
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
                bootstrap = get(),
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

        // F1.7.1: multi-tenancy. Os três leem `auth` para resolver o uid corrente em vez
        // de recebê-lo por parâmetro — a alternativa faria cada chamador carregar o uid
        // até aqui, e um chamador que passasse o uid errado viraria leitura de dado alheio
        // tentada contra as rules, que é ruído de PERMISSION_DENIED sem causa aparente.
        single<EstablishmentRepository> {
            FirestoreEstablishmentRepository(firestore = get(), auth = get(), logger = get())
        }
        single<MembershipRepository> {
            FirestoreMembershipRepository(firestore = get(), auth = get(), logger = get())
        }
        single<ActiveEstablishmentRepository> {
            FirestoreActiveEstablishmentRepository(firestore = get(), auth = get(), logger = get())
        }

        // F1.7.3d: as callables. Leitura do grafo vai direto ao Firestore; escrita nao tem
        // como ir, porque `members` e write: if false — dai repositorios separados.
        single<AccountBootstrapRepository> {
            FunctionsAccountBootstrapRepository(functions = get(), logger = get())
        }
        single<MemberMutationRepository> {
            FunctionsMemberMutationRepository(functions = get(), logger = get())
        }
    }
