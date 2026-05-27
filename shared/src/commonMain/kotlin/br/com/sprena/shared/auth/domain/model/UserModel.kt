package br.com.sprena.shared.auth.domain.model

/**
 * Representa o usuário autenticado no Sprena.
 *
 * @property id Identificador único (uid do Firebase Auth)
 * @property email Email do usuário (login)
 * @property name Nome para exibição na UI
 * @property role Perfil de acesso
 */
data class UserModel(
    val id: String,
    val email: String,
    val name: String,
    val role: UserRole,
)
