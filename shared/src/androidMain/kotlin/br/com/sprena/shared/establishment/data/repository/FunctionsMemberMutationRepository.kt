package br.com.sprena.shared.establishment.data.repository

import br.com.sprena.shared.core.logger.Logger
import br.com.sprena.shared.establishment.domain.model.MemberLinkResult
import br.com.sprena.shared.establishment.domain.model.MemberRole
import br.com.sprena.shared.establishment.domain.repository.MemberMutationRepository
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

/**
 * Mutação do grafo de vínculos pelas callables (F1.7.3d).
 *
 * Nenhuma `FirebaseFunctionsException` cruza para `commonMain` — a tradução acontece aqui, em
 * [mapMemberLinkError] (restrição 13 do CLAUDE.md).
 *
 * O CPF vai como o usuário digitou, com ou sem pontuação: normalizar aqui não adiantaria nada,
 * porque o servidor normaliza de novo. Ele não confia no cliente, e é isso que faz a callable
 * ser chamável direto pela API sem virar brecha.
 */
class FunctionsMemberMutationRepository(
    private val functions: FirebaseFunctions,
    private val logger: Logger,
) : MemberMutationRepository {
    override suspend fun linkByCpf(
        establishmentId: String,
        cpf: String,
        name: String,
        role: MemberRole,
    ): MemberLinkResult =
        runCatching {
            val response =
                functions
                    .getHttpsCallable(LINK)
                    .call(
                        mapOf(
                            "establishmentId" to establishmentId,
                            "cpf" to cpf,
                            "name" to name,
                            "role" to role.name,
                        ),
                    ).await()

            @Suppress("UNCHECKED_CAST")
            val data = response.data as? Map<String, Any?>
            when (data?.get("status")) {
                "linked" -> MemberLinkResult.Linked
                "pending" -> MemberLinkResult.Pending
                "already" -> MemberLinkResult.AlreadyLinked
                // Resposta que o app não entende: pode ser função mais nova que o APK. Tratar
                // como sucesso silencioso esconderia uma incompatibilidade real.
                else -> MemberLinkResult.Failed("Resposta inesperada do servidor.")
            }
        }.getOrElse { error ->
            // O CPF nunca entra no log — nem mascarado, porque a operação já é rastreada pela
            // trilha de auditoria do estabelecimento, com o prefixo do HMAC.
            logger.warn(TAG, "linkMemberByCpf falhou${callableDiagnostics(error)}", error)
            mapMemberLinkError(error)
        }

    override suspend fun setRole(
        establishmentId: String,
        targetUid: String,
        role: MemberRole,
    ): Result<Unit> =
        call(
            name = SET_ROLE,
            payload =
                mapOf(
                    "establishmentId" to establishmentId,
                    "targetUid" to targetUid,
                    "role" to role.name,
                ),
        )

    override suspend fun remove(
        establishmentId: String,
        targetUid: String,
    ): Result<Unit> =
        call(
            name = REMOVE,
            payload = mapOf("establishmentId" to establishmentId, "targetUid" to targetUid),
        )

    override suspend fun leave(establishmentId: String): Result<Unit> =
        call(name = LEAVE, payload = mapOf("establishmentId" to establishmentId))

    private suspend fun call(
        name: String,
        payload: Map<String, Any?>,
    ): Result<Unit> =
        runCatching {
            functions.getHttpsCallable(name).call(payload).await()
            Unit
        }.onFailure { error ->
            logger.warn(TAG, "$name falhou${callableDiagnostics(error)}", error)
        }

    private companion object {
        const val LINK = "linkMemberByCpf"
        const val SET_ROLE = "setMemberRole"
        const val REMOVE = "removeMember"
        const val LEAVE = "leaveEstablishment"
        const val TAG = "MemberMutationRepo"
    }
}
