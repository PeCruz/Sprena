package br.com.sprena.shared.establishment.domain.usecase

import br.com.sprena.shared.core.logger.Logger
import br.com.sprena.shared.establishment.domain.model.Establishment
import br.com.sprena.shared.establishment.domain.model.EstablishmentSaveResult
import br.com.sprena.shared.establishment.domain.repository.EstablishmentRepository
import br.com.sprena.shared.establishment.domain.validation.CnpjValidator
import br.com.sprena.shared.establishment.domain.validation.EstablishmentValidator

/**
 * Cadastra ou edita um estabelecimento (ADM).
 *
 * Normaliza CNPJ e telefone para só dígitos **antes** de persistir. A formatação é decisão
 * de exibição; guardar o valor pontuado faria dois cadastros do mesmo CNPJ com pontuação
 * diferente escaparem da unicidade de `cnpj_index`, que usa os dígitos como id do documento.
 *
 * Criar e editar ficam no mesmo use case porque a responsabilidade é uma só — persistir um
 * estabelecimento válido — e a diferença entre os dois é apenas o id já existir. Separá-los
 * duplicaria a validação inteira, que é a maior parte do que acontece aqui.
 */
class SaveEstablishmentUseCase(
    private val repository: EstablishmentRepository,
    private val logger: Logger,
) {
    suspend operator fun invoke(establishment: Establishment): EstablishmentSaveResult {
        val invalid =
            EstablishmentSaveResult.Invalid(
                name = EstablishmentValidator.validateName(establishment.name),
                cnpj = EstablishmentValidator.validateCnpj(establishment.cnpj),
                razaoSocial = EstablishmentValidator.validateRazaoSocial(establishment.razaoSocial),
                phone = EstablishmentValidator.validatePhone(establishment.phone),
                email = EstablishmentValidator.validateEmail(establishment.email),
            )
        if (invalid.hasError) return invalid

        val normalized = normalize(establishment)
        return if (normalized.id.isBlank()) create(normalized) else update(normalized)
    }

    private suspend fun create(establishment: Establishment): EstablishmentSaveResult =
        repository.isCnpjTaken(establishment.cnpj).fold(
            onSuccess = { taken ->
                if (taken) EstablishmentSaveResult.DuplicateCnpj else persist(establishment)
            },
            // Fail-closed: sem saber se o CNPJ já existe, não grava. Seguir em frente
            // criaria a duplicata justamente quando a rede está instável — e o
            // `cnpj_index` nega delete, então desfazer exigiria o Console.
            onFailure = { failed(it, "cnpj lookup failed") },
        )

    private suspend fun persist(establishment: Establishment): EstablishmentSaveResult =
        repository.create(establishment).fold(
            onSuccess = { EstablishmentSaveResult.Saved(it) },
            onFailure = { failed(it, "create failed") },
        )

    private suspend fun update(establishment: Establishment): EstablishmentSaveResult =
        repository.update(establishment).fold(
            onSuccess = { EstablishmentSaveResult.Saved(establishment.id) },
            onFailure = { failed(it, "update failed") },
        )

    private fun failed(
        error: Throwable,
        message: String,
    ): EstablishmentSaveResult {
        logger.warn(TAG, message, error)
        return EstablishmentSaveResult.Failed(SAVE_FAILED_MESSAGE)
    }

    private fun normalize(establishment: Establishment): Establishment =
        establishment.copy(
            name = establishment.name.trim(),
            cnpj = CnpjValidator.digits(establishment.cnpj),
            phone = establishment.phone.filter { it.isDigit() },
            email = establishment.email.trim(),
            razaoSocial = establishment.razaoSocial?.trim()?.takeIf { it.isNotEmpty() },
            address = establishment.address?.takeIf { !it.isEmpty },
        )

    private companion object {
        const val TAG = "SaveEstablishment"
        const val SAVE_FAILED_MESSAGE = "Não foi possível salvar. Verifique a conexão."
    }
}
