package br.com.sprena.shared.auth.domain.model

/**
 * Modelo de domínio do usuário autenticado.
 *
 * @property id Identificador único.
 * @property username Login do usuário (max 8 caracteres).
 * @property name Nome completo para exibição.
 * @property role Perfil de acesso.
 */
data class UserModel(
    val id: String,
    val username: String,
    val name: String,
    val role: UserRole,
)
