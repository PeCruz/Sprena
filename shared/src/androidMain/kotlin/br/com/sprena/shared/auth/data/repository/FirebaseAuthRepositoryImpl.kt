package br.com.sprena.shared.auth.data.repository

import br.com.sprena.shared.account.domain.repository.AccountBootstrapRepository
import br.com.sprena.shared.auth.domain.model.AuthResult
import br.com.sprena.shared.auth.domain.model.UserModel
import br.com.sprena.shared.auth.domain.model.UserRole
import br.com.sprena.shared.auth.domain.repository.AuthRepository
import br.com.sprena.shared.core.logger.Logger
import br.com.sprena.shared.core.logger.pii.PiiMasker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Impl Android do [AuthRepository] usando Firebase Authentication (email/senha)
 * + Firestore para resolver a role (`users/{uid}` doc).
 *
 * Erros do Firebase (Auth e Firestore) viram mensagens em PT-BR — ver [mapAuthError].
 */
class FirebaseAuthRepositoryImpl(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val logger: Logger,
    /**
     * Cria `users/{uid}` quando ele não existe (F1.7.3d).
     *
     * Até aqui, doc ausente significava "conta não autorizada" e derrubava o login — o app não
     * tinha cadastro, e toda conta era provisionada à mão no Console. Com o bootstrap, a conta
     * nasce sozinha com o papel mais restrito.
     */
    private val bootstrap: AccountBootstrapRepository,
) : AuthRepository {
    // ReturnCount: 4 distinct error paths (no uid / missing doc / invalid role / success) are
    // clearer as guards than via collapsed Result chains. TooGenericExceptionCaught: Firebase
    // wraps many failure modes in subclasses of Exception — we map all of them via mapAuthError.
    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    override suspend fun authenticate(
        email: String,
        password: String,
    ): AuthResult {
        return try {
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            val uid =
                authResult.user?.uid
                    ?: return AuthResult.Error("Falha inesperada na autenticação")

            val doc =
                firestore
                    .collection(USERS_COLLECTION)
                    .document(uid)
                    .get()
                    .await()
            // Doc ausente deixa de ser erro: a conta nasce aqui, com papel USER. O bootstrap
            // é idempotente e nunca faz update, então chamá-lo numa conta existente devolve o
            // papel real — nada é rebaixado se este caminho for percorrido por engano.
            val resolved =
                if (doc.exists()) {
                    doc
                } else {
                    logger.info(TAG, "user doc ausente, criando conta uid=$uid")
                    bootstrap.bootstrap().getOrElse { error ->
                        logger.warn(TAG, "bootstrap falhou uid=$uid", error)
                        return AuthResult.Error("Não foi possível preparar sua conta. Tente de novo.")
                    }
                    // Relê em vez de confiar no papel devolvido: `name` e os demais campos vêm
                    // do documento, e uma segunda fonte de verdade aqui divergiria na primeira
                    // vez que o bootstrap mudasse.
                    firestore
                        .collection(USERS_COLLECTION)
                        .document(uid)
                        .get()
                        .await()
                }

            if (!resolved.exists()) {
                logger.warn(TAG, "user doc ausente apos bootstrap uid=$uid")
                return AuthResult.Error("Conta não autorizada. Contate o administrador.")
            }

            val roleStr = resolved.getString("role")
            val role =
                roleStr?.let { runCatching { UserRole.valueOf(it.uppercase()) }.getOrNull() }
                    ?: run {
                        logger.warn(TAG, "user doc has invalid role uid=$uid raw=$roleStr")
                        return AuthResult.Error("Conta sem perfil válido")
                    }
            val name = resolved.getString("name") ?: email.substringBefore('@')

            logger.info(TAG, "login ok uid=$uid email=${PiiMasker.email(email)}")
            AuthResult.Success(UserModel(id = uid, email = email, name = name, role = role))
        } catch (e: Exception) {
            logger.warn(
                TAG,
                "login failed email=${PiiMasker.email(email)} " +
                    "cause=${e::class.simpleName}${errorDiagnostics(e)}",
                e,
            )
            AuthResult.Error(mapAuthError(e))
        }
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> =
        runCatching {
            auth.sendPasswordResetEmail(email).await()
            Unit
        }.onFailure { e ->
            logger.warn(TAG, "sendPasswordReset failed email=${PiiMasker.email(email)}", e)
        }

    override suspend fun signOut() {
        auth.signOut()
        logger.info(TAG, "firebase auth signOut")
    }

    override fun currentUid(): String? = auth.currentUser?.uid

    /**
     * Renova o ID token para separar "conta não existe mais" de "sem rede".
     *
     * `FirebaseAuthInvalidUserException` é o único sinal confiável de que o usuário foi
     * apagado ou desabilitado no servidor. **Qualquer outra exceção devolve sucesso**:
     * sem rede, o SDK falha aqui, e tratar isso como conta inexistente deslogaria todo
     * mundo que abrisse o app offline.
     *
     * Sem `currentUser`, não há o que renovar — quem decide é [currentUid].
     */
    override suspend fun refreshToken(): Result<Unit> {
        val user = auth.currentUser ?: return Result.success(Unit)

        return runCatching { user.getIdToken(true).await() }
            .fold(
                onSuccess = { Result.success(Unit) },
                onFailure = { error ->
                    if (error is FirebaseAuthInvalidUserException) {
                        logger.warn(TAG, "conta nao existe mais no servidor${errorDiagnostics(error)}")
                        Result.failure(error)
                    } else {
                        // Rede, App Check, timeout: a conta pode estar perfeitamente viva.
                        logger.info(TAG, "refresh do token indisponivel${errorDiagnostics(error)}")
                        Result.success(Unit)
                    }
                },
            )
    }

    private companion object {
        const val TAG = "FirebaseAuthRepo"
        const val USERS_COLLECTION = "users"
    }
}
