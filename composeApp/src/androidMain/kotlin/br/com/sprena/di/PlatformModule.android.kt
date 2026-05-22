package br.com.sprena.di

import br.com.sprena.shared.sportclient.data.repository.SportClientRepositoryImpl
import br.com.sprena.shared.sportclient.domain.repository.SportClientRepository
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import org.koin.dsl.module

/**
 * Módulo Koin com dependências específicas da plataforma Android.
 *
 * Responsabilidades:
 *  - Fornecer [FirebaseFirestore] como singleton
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

        // Repository bindings (interface → Firestore implementation)
        single<SportClientRepository> { SportClientRepositoryImpl(firestore = get()) }
    }
