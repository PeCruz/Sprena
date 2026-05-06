package br.com.sprena.shared.auth.domain.model

/**
 * Perfis de acesso do sistema Sprena.
 *
 * @property displayName Nome legível para exibição na UI.
 */
enum class UserRole(val displayName: String) {
    /** Acesso total ao sistema. */
    ADM("Administrador"),

    /** Acesso intermediário — gestão de eventos e visualizações. */
    MOD("Moderador"),

    /** Acesso básico — visualização e ações limitadas. */
    CLIENT("Funcionário"),
}
