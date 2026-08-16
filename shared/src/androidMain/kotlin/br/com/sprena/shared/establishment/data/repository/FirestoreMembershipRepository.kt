package br.com.sprena.shared.establishment.data.repository

import br.com.sprena.shared.core.logger.Logger
import br.com.sprena.shared.establishment.data.dto.MembershipDto
import br.com.sprena.shared.establishment.domain.model.Membership
import br.com.sprena.shared.establishment.domain.repository.MembershipRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Leitura do grafo de vínculos em `establishments/{estId}/members/{uid}`.
 *
 * Só leitura: a coleção é `write: if false`, e as callables de F1.7.3 são o único caminho
 * de mutação.
 */
class FirestoreMembershipRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val logger: Logger,
) : MembershipRepository {
    override fun observeMine(): Flow<Result<List<Membership>>> {
        val uid =
            auth.currentUser?.uid
                ?: return flowOf(Result.failure(IllegalStateException(NOT_SIGNED_IN)))

        // O filtro por `uid` não é conveniência: a rule do collection group é
        // `resource.data.uid == request.auth.uid`, e é justamente este `whereEqualTo` que
        // o motor consegue casar com ela. Sem o filtro a query é negada em bloco.
        return firestore
            .collectionGroup(MEMBERS)
            .whereEqualTo(MembershipDto.FIELD_UID, uid)
            .snapshots()
            .map { snapshot ->
                Result.success(
                    snapshot.documents.mapNotNull { doc ->
                        MembershipDto.establishmentIdOf(doc)?.let { MembershipDto.fromSnapshot(doc, it) }
                    },
                )
            }.catch { error ->
                // FAILED_PRECONDITION aqui costuma ser o índice de escopo collection group
                // faltando em produção — o emulador cria índice sozinho e esconde o problema.
                logger.warn(TAG, "observeMine failed", error)
                emit(Result.failure(error))
            }
    }

    override fun observeMembers(establishmentId: String): Flow<Result<List<Membership>>> =
        firestore
            .collection(ESTABLISHMENTS)
            .document(establishmentId)
            .collection(MEMBERS)
            .snapshots()
            .map { snapshot ->
                Result.success(
                    snapshot.documents.mapNotNull { MembershipDto.fromSnapshot(it, establishmentId) },
                )
            }.catch { error ->
                logger.warn(TAG, "observeMembers failed", error)
                emit(Result.failure(error))
            }

    private companion object {
        const val ESTABLISHMENTS = "establishments"
        const val MEMBERS = "members"
        const val TAG = "MembershipRepo"
        const val NOT_SIGNED_IN = "Sessão expirada. Entre novamente."
    }
}
