package br.com.sprena.shared.establishment.data.dto

import br.com.sprena.shared.establishment.domain.model.Establishment
import br.com.sprena.shared.establishment.domain.model.EstablishmentAddress
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue

/**
 * Mapeamento de `establishments/{id}`.
 *
 * A leitura é manual, campo a campo, em vez de `toObject()`: um documento sem `name` ou
 * sem `cnpj` não é um estabelecimento incompleto, é um documento que não deveria existir.
 * `toObject()` o entregaria com strings vazias e o app o mostraria como um item sem nome
 * na lista; aqui ele vira `null` e some.
 *
 * A escrita monta o mapa à mão porque `updatedAt` precisa ser [FieldValue.serverTimestamp],
 * que não tem representação num data class — e é justamente esse campo que a rule compara
 * com `request.time` para impedir backdating.
 */
internal object EstablishmentDto {
    const val FIELD_NAME = "name"
    const val FIELD_ACTIVE = "active"
    const val FIELD_CNPJ = "cnpj"
    const val FIELD_RAZAO_SOCIAL = "razaoSocial"
    const val FIELD_ADDRESS = "address"
    const val FIELD_PHONE = "phone"
    const val FIELD_EMAIL = "email"
    const val FIELD_CREATED_AT = "createdAt"
    const val FIELD_UPDATED_AT = "updatedAt"
    const val FIELD_CREATED_BY = "createdBy"

    fun fromSnapshot(doc: DocumentSnapshot): Establishment? {
        // Documento inexistente cai aqui naturalmente: `getString` devolve null e o
        // resultado é o mesmo de um documento sem nome — nos dois casos não há
        // estabelecimento a mostrar.
        val name = doc.getString(FIELD_NAME)?.takeIf { it.isNotBlank() }
        val cnpj = doc.getString(FIELD_CNPJ)?.takeIf { it.isNotBlank() }
        return if (name == null || cnpj == null) {
            null
        } else {
            Establishment(
                id = doc.id,
                name = name,
                cnpj = cnpj,
                phone = doc.getString(FIELD_PHONE).orEmpty(),
                email = doc.getString(FIELD_EMAIL).orEmpty(),
                // Ausente é tratado como ativo: o campo só passou a existir com F1.7.1, e
                // um documento semeado antes dele não deve sumir da lista por omissão.
                active = doc.getBoolean(FIELD_ACTIVE) ?: true,
                razaoSocial = doc.getString(FIELD_RAZAO_SOCIAL)?.takeIf { it.isNotBlank() },
                address = readAddress(doc),
            )
        }
    }

    /** Estado final completo do documento — é essa forma que `keys().hasOnly()` valida. */
    fun toMap(
        establishment: Establishment,
        createdBy: String? = null,
    ): Map<String, Any?> =
        buildMap {
            put(FIELD_NAME, establishment.name)
            put(FIELD_ACTIVE, establishment.active)
            put(FIELD_CNPJ, establishment.cnpj)
            put(FIELD_PHONE, establishment.phone)
            put(FIELD_EMAIL, establishment.email)
            establishment.razaoSocial?.let { put(FIELD_RAZAO_SOCIAL, it) }
            establishment.address?.let { put(FIELD_ADDRESS, addressToMap(it)) }
            put(FIELD_UPDATED_AT, FieldValue.serverTimestamp())
            // Só na criação: um update que reenviasse createdAt/createdBy sobrescreveria a
            // origem do registro, e a rule não tem como distinguir os dois momentos.
            createdBy?.let {
                put(FIELD_CREATED_BY, it)
                put(FIELD_CREATED_AT, FieldValue.serverTimestamp())
            }
        }

    @Suppress("UNCHECKED_CAST")
    private fun readAddress(doc: DocumentSnapshot): EstablishmentAddress? {
        val raw = doc.get(FIELD_ADDRESS) as? Map<String, Any?> ?: return null
        val address =
            EstablishmentAddress(
                street = raw["street"] as? String,
                number = raw["number"] as? String,
                complement = raw["complement"] as? String,
                district = raw["district"] as? String,
                city = raw["city"] as? String,
                state = raw["state"] as? String,
                zipCode = raw["zipCode"] as? String,
            )
        return address.takeIf { !it.isEmpty }
    }

    private fun addressToMap(address: EstablishmentAddress): Map<String, Any> =
        buildMap {
            address.street?.let { put("street", it) }
            address.number?.let { put("number", it) }
            address.complement?.let { put("complement", it) }
            address.district?.let { put("district", it) }
            address.city?.let { put("city", it) }
            address.state?.let { put("state", it) }
            address.zipCode?.let { put("zipCode", it) }
        }
}
