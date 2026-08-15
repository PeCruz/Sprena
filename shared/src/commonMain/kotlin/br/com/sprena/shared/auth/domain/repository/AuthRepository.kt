package br.com.sprena.shared.auth.domain.repository

import br.com.sprena.shared.auth.domain.model.AuthResult

/**
 * Contrato do repositório de autenticação.
 *
 * Implementação concreta em `shared/androidMain`: `FirebaseAuthRepositoryImpl`.
 */
interface AuthRepository {
    /**
     * Autentica com [email] e [password].
     * Em sucesso, retorna [AuthResult.Success] com `UserModel` populado a partir
     * do Firebase Auth + doc `users/{uid}` no Firestore.
     */
    suspend fun authenticate(
        email: String,
        password: String,
    ): AuthResult

    /**
     * Envia email de reset de senha. `Result.failure` se rede/Firebase falhar.
     */
    suspend fun sendPasswordReset(email: String): Result<Unit>

    /**
     * Encerra a sessão Firebase Auth local (não invalida no servidor).
     */
    suspend fun signOut()

    /**
     * Retorna o uid do usuário atualmente autenticado no Firebase Auth, ou null.
     * Não depende de cache local — consulta `FirebaseAuth.currentUser`.
     */
    fun currentUid(): String?

    /**
     * Força o refresh do ID token para descobrir se a conta ainda existe no servidor.
     *
     * [currentUid] responde a partir do cache local do SDK: depois de uma exclusão de
     * conta (F1.6a) ou de um delete pelo Console, ele continua devolvendo o uid até o
     * token ser renovado. Sem esta checagem o app segue "logado" numa conta que não
     * existe mais — e é exatamente esse o roteiro que um revisor da Play executa:
     * excluir a conta e reabrir o app.
     *
     * Contrato deliberado: **falha de rede devolve sucesso.** Tratar rede como "conta
     * inexistente" deslogaria todo mundo que abrisse o app offline, que é a mesma classe
     * de erro descrita na Parte F.5 do runbook. Só usuário inexistente ou desabilitado
     * vira `Result.failure`.
     */
    suspend fun refreshToken(): Result<Unit>
}
