package br.com.sprena.core.security

import com.google.firebase.appcheck.FirebaseAppCheck

/**
 * Instalação do Firebase App Check. Chamar uma vez na Application.onCreate,
 * antes de qualquer acesso a Firestore/Auth.
 *
 * App Check ataca um buraco que as Security Rules (F1.4) não cobrem: as rules
 * dizem *quem* pode ler e gravar, mas não dizem de *onde*. Sem App Check, quem
 * extrair a apiKey do `google-services.json` fala com o backend via REST usando
 * qualquer cliente. Com App Check, cada request carrega um token de atestação e
 * o backend rejeita o que não vier do app genuíno.
 *
 * O provider vem de [appCheckProviderFactory], que tem uma implementação por
 * build type — Play Integrity em release, provider de debug em debug. A escolha
 * é resolvida na compilação, não em runtime: ver `src/androidRelease` e
 * `src/androidDebug`.
 *
 * ⚠️ Instalar o provider não ativa a proteção sozinho. A *enforcement* é ligada
 * por produto no Firebase Console (Firestore e Auth) — enquanto estiver em modo
 * monitoramento, requests sem token continuam passando. Ver
 * `docs/ops/firebase-users-runbook.md`.
 */
object AppCheckBootstrap {
    fun init() {
        FirebaseAppCheck
            .getInstance()
            .installAppCheckProviderFactory(appCheckProviderFactory())
    }
}
