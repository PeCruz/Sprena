package br.com.sprena.shared.auth.domain.model

import br.com.sprena.shared.auth.session.SessionUser

sealed interface RestoreResult {
    data class Authenticated(
        val user: SessionUser,
    ) : RestoreResult

    data object NotAuthenticated : RestoreResult
}
