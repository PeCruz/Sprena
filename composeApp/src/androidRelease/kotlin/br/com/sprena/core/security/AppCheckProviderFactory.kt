package br.com.sprena.core.security

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Provider de release: Play Integrity.
 *
 * Exige que o app esteja registrado no Firebase Console com o SHA-256 da chave
 * de assinatura de release e com a Play Integrity API habilitada no projeto
 * Google Cloud vinculado. Builds assinados com outra chave (ou sideloaded fora
 * da Play) falham a atestação — é exatamente o comportamento desejado.
 */
internal fun appCheckProviderFactory(): AppCheckProviderFactory = PlayIntegrityAppCheckProviderFactory.getInstance()
