package br.com.sprena.shared.establishment.domain.model

/**
 * Desfecho de uma vinculação por CPF.
 *
 * [Linked] e [Pending] são desfechos de sucesso diferentes, e a tela precisa dos dois: no
 * primeiro a pessoa já pode ser avisada de que tem acesso; no segundo ela precisa entrar no
 * app e informar o CPF para o vínculo se concretizar. Colapsar os dois num "salvo" faria quem
 * vinculou não saber se deve ou não cobrar uma ação da pessoa.
 *
 * É a única informação que a operação devolve sobre o CPF digitado — não há consulta, não há
 * listagem, e o servidor nunca confirma se um CPF qualquer tem conta na plataforma.
 */
sealed interface MemberLinkResult {
    /** A pessoa já tinha conta: o vínculo existe agora. */
    data object Linked : MemberLinkResult

    /** Ninguém reivindicou este CPF ainda: fica pendente até o primeiro login. */
    data object Pending : MemberLinkResult

    /** Nada mudou porque o vínculo (ou a pendência) já era exatamente esse. */
    data object AlreadyLinked : MemberLinkResult

    data class Invalid(
        val message: String,
    ) : MemberLinkResult

    data class Denied(
        val message: String,
    ) : MemberLinkResult

    data class Failed(
        val message: String,
    ) : MemberLinkResult
}
