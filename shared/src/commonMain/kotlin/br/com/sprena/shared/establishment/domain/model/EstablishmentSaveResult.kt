package br.com.sprena.shared.establishment.domain.model

import br.com.sprena.shared.core.validation.ValidationResult

/**
 * Desfecho da gravação de um estabelecimento.
 *
 * [Invalid] carrega o erro por campo, como [br.com.sprena.shared.account.domain.model.ProfileSaveResult]:
 * o formulário marca o campo problemático, e uma mensagem única obrigaria a tela a adivinhar qual.
 *
 * [DuplicateCnpj] é caso próprio, e não um [Invalid] de CNPJ, porque a causa é outra — o
 * número está correto, já existe outro estabelecimento com ele. A tela precisa dessa
 * diferença para poder oferecer "abrir o estabelecimento existente" em vez de só apontar
 * erro de digitação.
 */
sealed interface EstablishmentSaveResult {
    data class Saved(
        val id: String,
    ) : EstablishmentSaveResult

    data class Invalid(
        val name: ValidationResult = ValidationResult.Valid,
        val cnpj: ValidationResult = ValidationResult.Valid,
        val razaoSocial: ValidationResult = ValidationResult.Valid,
        val phone: ValidationResult = ValidationResult.Valid,
        val email: ValidationResult = ValidationResult.Valid,
    ) : EstablishmentSaveResult {
        val hasError: Boolean
            get() = !name.isValid || !cnpj.isValid || !razaoSocial.isValid || !phone.isValid || !email.isValid
    }

    data object DuplicateCnpj : EstablishmentSaveResult

    data class Failed(
        val message: String,
    ) : EstablishmentSaveResult
}
