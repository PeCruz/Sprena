package br.com.sprena.shared.establishment.domain.model

/**
 * O vínculo entre uma pessoa e um estabelecimento — a aresta de autorização do sistema.
 *
 * O documento correspondente (`establishments/{estId}/members/{uid}`) é `write: if false`
 * nas rules: só o Admin SDK, dentro das callables de F1.7.3, escreve aqui. Por isso este
 * modelo é somente leitura no app — não existe um `save` correspondente, e não deve
 * passar a existir.
 *
 * [active] falso significa desligado, não removido: as rules tratam `active != true` como
 * "não é membro", e manter o documento preserva a trilha de quem já teve acesso.
 */
data class Membership(
    val establishmentId: String,
    val uid: String,
    val role: MemberRole,
    val active: Boolean,
)
