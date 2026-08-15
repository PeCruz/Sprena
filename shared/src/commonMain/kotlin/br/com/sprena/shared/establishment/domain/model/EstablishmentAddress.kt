package br.com.sprena.shared.establishment.domain.model

/**
 * Endereço do estabelecimento. Opcional por inteiro, e cada campo também — o cadastro
 * mínimo exigido é nome, CNPJ, telefone e e-mail.
 *
 * Vai para o Firestore como um mapa aninhado, que a rule valida apenas como `is map`:
 * endereço não participa de nenhuma decisão de autorização, então validar campo a campo
 * lá dentro só criaria uma allowlist a mais para esquecer de atualizar.
 */
data class EstablishmentAddress(
    val street: String? = null,
    val number: String? = null,
    val complement: String? = null,
    val district: String? = null,
    val city: String? = null,
    val state: String? = null,
    val zipCode: String? = null,
) {
    val isEmpty: Boolean
        get() =
            listOf(street, number, complement, district, city, state, zipCode)
                .all { it.isNullOrBlank() }
}
