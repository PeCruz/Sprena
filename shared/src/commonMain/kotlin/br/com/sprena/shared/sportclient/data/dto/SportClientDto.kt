package br.com.sprena.shared.sportclient.data.dto

import br.com.sprena.shared.sportclient.domain.model.SportClientModel
import br.com.sprena.shared.sportclient.domain.validation.PaymentMethod
import br.com.sprena.shared.sportclient.domain.validation.SportModality

/**
 * DTO para serialização de/para Firebase Firestore.
 *
 * Firestore armazena campos como Map<String, Any>.
 * Este DTO faz a conversão bidirecional com [SportClientModel].
 */
data class SportClientDto(
    val name: String = "",
    val apelido: String = "",
    val cpf: String = "",
    val phone: String = "",
    val modalities: List<String> = emptyList(),
    val attendance: Int = 0,
    val paymentMethod: String = "",
    val cashAmountCents: Long = 0L,
    val paymentHistory: List<String> = emptyList(),
) {

    /**
     * Converte o DTO para o modelo de domínio.
     *
     * @param id O ID do documento Firestore.
     */
    fun toDomain(id: String): SportClientModel = SportClientModel(
        id = id,
        name = name,
        apelido = apelido,
        cpf = cpf,
        phone = phone,
        modalities = modalities.mapNotNull { name ->
            runCatching { SportModality.valueOf(name) }.getOrNull()
        }.ifEmpty { listOf(SportModality.FUTEVOLEI) },
        attendance = attendance,
        paymentMethod = PaymentMethod.valueOf(paymentMethod.ifBlank { PaymentMethod.CASH.name }),
        cashAmountCents = cashAmountCents,
        paymentHistory = paymentHistory,
    )

    companion object {
        /**
         * Converte o modelo de domínio para DTO.
         */
        fun fromDomain(model: SportClientModel): SportClientDto = SportClientDto(
            name = model.name,
            apelido = model.apelido,
            cpf = model.cpf,
            phone = model.phone,
            modalities = model.modalities.map { it.name },
            attendance = model.attendance,
            paymentMethod = model.paymentMethod.name,
            cashAmountCents = model.cashAmountCents,
            paymentHistory = model.paymentHistory,
        )
    }
}
