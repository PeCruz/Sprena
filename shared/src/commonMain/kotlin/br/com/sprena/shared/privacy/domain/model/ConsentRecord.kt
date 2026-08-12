package br.com.sprena.shared.privacy.domain.model

/**
 * Aceite de política registrado para um usuário.
 *
 * @property policyVersion versão do texto aceito — comparada com [PrivacyPolicy.VERSION]
 */
data class ConsentRecord(
    val uid: String,
    val policyVersion: String,
    val acceptedAtEpochMillis: Long,
)
