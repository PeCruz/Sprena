package br.com.sprena.presentation.establishment.edit

import br.com.sprena.shared.core.mvi.UiState
import br.com.sprena.shared.establishment.domain.model.Establishment
import br.com.sprena.shared.establishment.domain.model.EstablishmentAddress

/**
 * Rascunho do formulário.
 *
 * [cnpj] e [phone] guardam **só dígitos** — a pontuação é máscara visual. É essa forma que a
 * rule valida e que vira o id em `cnpj_index`; guardar o valor pontuado faria dois cadastros
 * do mesmo CNPJ escaparem da unicidade.
 */
data class EstablishmentDraft(
    val id: String = "",
    val name: String = "",
    val cnpj: String = "",
    val razaoSocial: String = "",
    val phone: String = "",
    val email: String = "",
    val active: Boolean = true,
    val street: String = "",
    val number: String = "",
    val complement: String = "",
    val district: String = "",
    val city: String = "",
    val state: String = "",
    val zipCode: String = "",
) {
    fun toEstablishment(): Establishment =
        Establishment(
            id = id,
            name = name,
            cnpj = cnpj,
            phone = phone,
            email = email,
            active = active,
            razaoSocial = razaoSocial,
            address =
                EstablishmentAddress(
                    street = street,
                    number = number,
                    complement = complement,
                    district = district,
                    city = city,
                    state = state,
                    zipCode = zipCode,
                ),
        )

    companion object {
        fun from(establishment: Establishment): EstablishmentDraft =
            EstablishmentDraft(
                id = establishment.id,
                name = establishment.name,
                cnpj = establishment.cnpj,
                razaoSocial = establishment.razaoSocial.orEmpty(),
                phone = establishment.phone,
                email = establishment.email,
                active = establishment.active,
                street = establishment.address?.street.orEmpty(),
                number = establishment.address?.number.orEmpty(),
                complement = establishment.address?.complement.orEmpty(),
                district = establishment.address?.district.orEmpty(),
                city = establishment.address?.city.orEmpty(),
                state = establishment.address?.state.orEmpty(),
                zipCode = establishment.address?.zipCode.orEmpty(),
            )
    }
}

/**
 * Os erros por campo saem de `EstablishmentSaveResult.Invalid` e viram `supportingText` do
 * campo correspondente — o padrão de `ProfileState`. Uma mensagem única obrigaria a tela a
 * adivinhar qual campo apontar.
 *
 * [error] é outra coisa: falha ao **carregar** o cadastro, que substitui o formulário inteiro
 * por uma mensagem com "tentar de novo".
 */
data class EstablishmentEditState(
    val draft: EstablishmentDraft = EstablishmentDraft(),
    val isCreating: Boolean = true,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val nameError: String? = null,
    val cnpjError: String? = null,
    val razaoSocialError: String? = null,
    val phoneError: String? = null,
    val emailError: String? = null,
) : UiState {
    val canSave: Boolean
        get() = !isSaving && !isLoading && error == null
}
