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
    /**
     * Nome para exibição, copiado para cá no momento da vinculação.
     *
     * É denormalização deliberada. As rules de `users/{uid}` e `user_profiles/{uid}` liberam
     * leitura **apenas ao próprio dono** — inclusive contra o ADM —, então sem este campo a
     * tela de membros listaria identificadores opacos. Abrir aquelas rules para resolver isso
     * ampliaria a superfície de PII e custaria um `get()` por linha da lista.
     *
     * O dado já existe no fluxo que cria o vínculo: o pré-cadastro por CPF carrega CPF e
     * apelido. Quem escreve é o Admin SDK, junto do resto do documento.
     *
     * `null` para os vínculos semeados à mão pelo Console antes de F1.7.3b. A UI cai no uid
     * abreviado nesse caso, em vez de mostrar um espaço vazio.
     */
    val displayName: String? = null,
)
