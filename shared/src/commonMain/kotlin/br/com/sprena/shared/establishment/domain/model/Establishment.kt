package br.com.sprena.shared.establishment.domain.model

/**
 * Um estabelecimento — o tenant do sistema a partir de F1.7.
 *
 * [cnpj] e [phone] guardam **apenas dígitos**. A máscara é assunto de apresentação; o
 * domínio e o Firestore trabalham com a forma normalizada, e é ela que a rule valida com
 * `^[0-9]{14}$`. Guardar o valor formatado faria dois cadastros do mesmo CNPJ com
 * pontuação diferente escaparem da unicidade de `cnpj_index`.
 */
data class Establishment(
    val id: String,
    val name: String,
    val cnpj: String,
    val phone: String,
    val email: String,
    val active: Boolean = true,
    val razaoSocial: String? = null,
    val address: EstablishmentAddress? = null,
)
