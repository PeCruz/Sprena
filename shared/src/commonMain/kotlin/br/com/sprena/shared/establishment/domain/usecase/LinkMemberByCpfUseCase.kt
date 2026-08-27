package br.com.sprena.shared.establishment.domain.usecase

import br.com.sprena.shared.core.validation.CpfValidator
import br.com.sprena.shared.establishment.domain.model.MemberLinkResult
import br.com.sprena.shared.establishment.domain.model.MemberRole
import br.com.sprena.shared.establishment.domain.repository.MemberMutationRepository

private const val NAME_MAX_LENGTH = 60

/**
 * Vincula alguém a um estabelecimento pelo CPF.
 *
 * A validação daqui é **conveniência de UI, não segurança**: o servidor valida de novo, e é
 * ele quem decide. O ganho é o usuário saber que digitou errado sem esperar a ida e volta da
 * rede, e a callable não gastar leitura nem rate limit com um número que nunca ia passar.
 *
 * Nome vazio é recusado antes de chegar ao servidor por um motivo próprio: sem ele, o vínculo
 * nasceria sem `displayName`, e a lista de membros mostraria um identificador opaco — o
 * problema que a denormalização daquele campo existe para resolver.
 */
class LinkMemberByCpfUseCase(
    private val repository: MemberMutationRepository,
) {
    suspend operator fun invoke(
        establishmentId: String,
        cpf: String,
        name: String,
        role: MemberRole,
    ): MemberLinkResult {
        val trimmedName = name.trim()
        val cpfCheck = CpfValidator.validate(cpf)

        return when {
            !cpfCheck.isValid -> MemberLinkResult.Invalid(cpfCheck.errorMessage ?: "CPF inválido")
            trimmedName.isEmpty() ->
                MemberLinkResult.Invalid("Informe um nome para identificar a pessoa.")
            else ->
                repository.linkByCpf(
                    establishmentId = establishmentId,
                    cpf = cpf,
                    name = trimmedName.take(NAME_MAX_LENGTH),
                    role = role,
                )
        }
    }
}
