package br.com.sprena.shared.establishment.data.repository

import br.com.sprena.shared.establishment.domain.model.MemberLinkResult
import com.google.firebase.functions.FirebaseFunctionsException

/**
 * Traduz falhas das callables de vínculo em desfecho de domínio (F1.7.3d).
 *
 * Espelha [br.com.sprena.shared.account.data.repository.mapAccountDeletionError] e vale as
 * mesmas duas regras: mensagem sem PII e sem eco do texto cru da exceção.
 *
 * A distinção que importa aqui é entre [MemberLinkResult.Invalid] e [MemberLinkResult.Denied]:
 * a primeira é erro de preenchimento e a tela marca o campo; a segunda é falta de permissão e
 * a tela precisa dizer isso, porque insistir não vai resolver. Colapsar as duas num "erro ao
 * salvar" faria o moderador tentar de novo o que nunca vai passar.
 */
internal fun mapMemberLinkError(error: Throwable): MemberLinkResult =
    when ((error as? FirebaseFunctionsException)?.code) {
        FirebaseFunctionsException.Code.INVALID_ARGUMENT ->
            MemberLinkResult.Invalid("Verifique o CPF e o nome informados.")

        FirebaseFunctionsException.Code.PERMISSION_DENIED ->
            MemberLinkResult.Denied("Você não pode vincular alguém com este papel aqui.")

        FirebaseFunctionsException.Code.UNAUTHENTICATED ->
            MemberLinkResult.Failed("Sua sessão expirou. Entre novamente.")

        FirebaseFunctionsException.Code.NOT_FOUND ->
            MemberLinkResult.Failed("Serviço indisponível. Tente mais tarde.")

        FirebaseFunctionsException.Code.UNAVAILABLE,
        FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
        ->
            MemberLinkResult.Failed("Sem conexão. Verifique a internet e tente de novo.")

        else -> MemberLinkResult.Failed("Não foi possível concluir. Tente novamente.")
    }

/** Só o código, para o log. Não é PII e é o que distingue os cenários no logcat. */
internal fun callableDiagnostics(error: Throwable): String =
    (error as? FirebaseFunctionsException)?.let { " code=${it.code.name}" }.orEmpty()
