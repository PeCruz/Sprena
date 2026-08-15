package br.com.sprena.shared.establishment.data.dto

import br.com.sprena.shared.establishment.domain.model.MemberRole
import br.com.sprena.shared.establishment.domain.model.Membership
import com.google.firebase.firestore.DocumentSnapshot

/**
 * Mapeamento de `establishments/{estId}/members/{uid}`.
 *
 * Só leitura: a coleção é `write: if false` nas rules, e toda mutação passa pelas
 * callables de F1.7.3. Não existe `toMap` aqui de propósito.
 *
 * Papel desconhecido devolve `null` em vez de um padrão — mesma escolha do `UserProfileDto`
 * para a role global. Um vínculo com papel que o app não entende precisa sumir, não virar
 * o papel menos privilegiado: o fallback transformaria erro de digitação em acesso
 * concedido, e o de cima em acesso negado sem explicação.
 */
internal object MembershipDto {
    const val FIELD_UID = "uid"
    const val FIELD_ROLE = "role"
    const val FIELD_ACTIVE = "active"

    /**
     * [establishmentId] vem do path, não do documento.
     *
     * Numa query de collection group o caminho é o único lugar onde o tenant existe de
     * forma confiável — e é também o que as rules usam. Ler de um campo abriria a porta
     * para um documento que diz pertencer a um estabelecimento e mora em outro.
     */
    fun fromSnapshot(
        doc: DocumentSnapshot,
        establishmentId: String,
    ): Membership? =
        // Documento inexistente cai junto com papel desconhecido: `getString` devolve null,
        // `fromRaw` devolve null, e o vínculo simplesmente não entra na lista.
        MemberRole.fromRaw(doc.getString(FIELD_ROLE))?.let { role ->
            Membership(
                establishmentId = establishmentId,
                uid = doc.getString(FIELD_UID) ?: doc.id,
                role = role,
                // Ausente é tratado como desligado — o oposto de `active`, onde a omissão
                // é benigna. Aqui a omissão é a diferença entre ter e não ter acesso.
                active = doc.getBoolean(FIELD_ACTIVE) ?: false,
            )
        }

    /** O id do estabelecimento é o avô do documento: `establishments/{estId}/members/{uid}`. */
    fun establishmentIdOf(doc: DocumentSnapshot): String? =
        doc.reference.parent.parent
            ?.id
}
