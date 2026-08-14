package br.com.sprena.shared.account.data.repository

import br.com.sprena.shared.account.domain.model.AccountDeletionResult
import com.google.firebase.functions.FirebaseFunctionsException

/**
 * Traduz falhas do callable `deleteMyAccount` em desfecho de domínio (F1.6a).
 *
 * Espelha [br.com.sprena.shared.auth.data.repository.mapAuthError] e vale as mesmas
 * duas regras: mensagem sem PII e sem eco do texto cru da exceção.
 *
 * `NOT_FOUND` merece atenção: ele significa "função não deployada" **ou** "região do
 * cliente diferente da região do callable", e os dois são indistinguíveis do lado de
 * cá. A mensagem é deliberadamente operacional em vez de culpar o usuário.
 */
internal fun mapAccountDeletionError(error: Throwable): AccountDeletionResult =
    when ((error as? FirebaseFunctionsException)?.code) {
        FirebaseFunctionsException.Code.UNAUTHENTICATED ->
            AccountDeletionResult.SessionExpired

        FirebaseFunctionsException.Code.PERMISSION_DENIED ->
            AccountDeletionResult.Failed(
                "Não foi possível validar o aplicativo neste dispositivo. Atualize pela Play Store.",
            )

        FirebaseFunctionsException.Code.NOT_FOUND ->
            AccountDeletionResult.Failed("Serviço de exclusão indisponível. Tente mais tarde.")

        FirebaseFunctionsException.Code.UNAVAILABLE,
        FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
        ->
            AccountDeletionResult.Failed("Sem conexão. Verifique a internet e tente de novo.")

        FirebaseFunctionsException.Code.INVALID_ARGUMENT ->
            AccountDeletionResult.Failed("Pedido de exclusão inválido. Atualize o aplicativo.")

        else ->
            AccountDeletionResult.Failed("Não foi possível excluir sua conta. Tente novamente.")
    }

/** Só o código, para o log. Não é PII e é o que distingue os cenários no logcat. */
internal fun deletionDiagnostics(error: Throwable): String =
    (error as? FirebaseFunctionsException)?.let { " code=${it.code.name}" }.orEmpty()
