package br.com.sprena.shared.account.domain.model

import br.com.sprena.shared.auth.domain.model.UserRole
import br.com.sprena.shared.sportclient.domain.validation.SportModality

/**
 * Perfil do titular, montado a partir de duas fontes com donos diferentes (F1.6a):
 *
 * - `users/{uid}` — identidade **operacional**: quem autorizou o acesso e em que nível.
 *   Provisionado pelo Console/Admin SDK, `allow write: if false` nas rules.
 * - `user_profiles/{uid}` — dados **autodeclarados** pelo próprio titular. Documento
 *   inteiro opcional: quem nunca editou não tem o doc, e os campos vêm nulos.
 *
 * A separação é deliberada e está justificada em SECURITY.md: a role vive num doc que o
 * cliente não escreve, então nenhuma allowlist de campos pode ser esquecida no futuro.
 *
 * [SportModality] é reusado de `shared.sportclient` — mesmo módulo Gradle, pacote diferente.
 * Duplicar o enum criaria duas listas de modalidades para manter em sincronia.
 */
data class UserProfile(
    val uid: String,
    val email: String,
    val role: UserRole,
    val name: String?,
    val apelido: String?,
    val cpf: String?,
    val phone: String?,
    val modalities: List<SportModality>,
)
