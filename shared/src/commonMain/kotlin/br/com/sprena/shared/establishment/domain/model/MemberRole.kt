package br.com.sprena.shared.establishment.domain.model

/**
 * Papel de uma pessoa **dentro de um estabelecimento**.
 *
 * É um enum separado de [br.com.sprena.shared.auth.domain.model.UserRole] de propósito, e não
 * duplicação por descuido. A partir de F1.7 a autorização tem dois níveis:
 *
 * - `UserRole` (em `users/{uid}.role`) é global e responde a uma única pergunta: é ADM?
 * - `MemberRole` (em `establishments/{estId}/members/{uid}.role`) é por tenant.
 *
 * Só o papel por tenant permite expressar "MOD gere os **seus** estabelecimentos", e deixa
 * a mesma pessoa ser MOD num lugar e CLIENT noutro — o que um enum global não consegue.
 *
 * Há um segundo motivo, de sequenciamento: `SECURITY.md` registra que criar a constante
 * `UserRole.USER` antes das rules que a restringem é ativamente perigoso, porque
 * `sport_clients` ainda tem `read: if isSignedIn()`. Aquela constante nasce em F1.7.3,
 * junto das rules; esta aqui não toca naquele caminho.
 */
enum class MemberRole(
    val displayName: String,
) {
    /** Gere o estabelecimento: cardápio, categorias, financeiro e vínculo de Clients. */
    MOD("Moderador"),

    /** Opera o dia a dia: comandas e cadastro de usuários do estabelecimento. */
    CLIENT("Funcionário"),

    /** Frequentador: consulta a própria comanda e os eventos. */
    USER("Usuário"),
    ;

    companion object {
        /**
         * `null` para valor desconhecido, em vez de exceção ou de um padrão silencioso.
         *
         * Mesma escolha do `UserProfileDto`: um papel que o app não entende precisa
         * derrubar o vínculo, não virar o papel menos privilegiado por acidente — um
         * fallback aqui transformaria erro de digitação no Console em acesso concedido.
         */
        fun fromRaw(raw: String?): MemberRole? = entries.firstOrNull { it.name == raw?.uppercase() }
    }
}
