package br.com.sprena.core.security

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Provider de debug: gera um token local que só é aceito depois de o UUID
 * impresso no logcat (tag `DebugAppCheckProvider`) ser registrado no Firebase
 * Console em App Check → Apps → Gerenciar tokens de depuração.
 *
 * Esta implementação existe apenas na variante debug — o artefato
 * `firebase-appcheck-debug` é `debugImplementation`, então o release não
 * compila contra ela nem a empacota.
 */
internal fun appCheckProviderFactory(): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()
