package br.com.sprena.shared.auth.domain.model

sealed interface PasswordResetResult {
    data object Sent : PasswordResetResult

    data class InvalidEmail(
        val message: String,
    ) : PasswordResetResult

    data object NetworkError : PasswordResetResult

    data class UnknownError(
        val message: String,
    ) : PasswordResetResult
}
