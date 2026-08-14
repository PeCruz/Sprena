package br.com.sprena.shared.account.domain.model

/**
 * Arquivo de exportação pronto para entrega ao titular (LGPD art. 18, V).
 *
 * O domínio produz **conteúdo e nome**; entregar é problema da plataforma (share sheet
 * no Android). Assim o use case continua testável em `commonTest` e nenhum ViewModel
 * precisa conhecer `Intent`.
 */
data class DataExport(
    val fileName: String,
    val json: String,
)
