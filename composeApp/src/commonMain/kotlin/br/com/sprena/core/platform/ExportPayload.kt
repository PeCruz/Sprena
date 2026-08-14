package br.com.sprena.core.platform

/** Arquivo gerado pelo app e pronto para ser entregue ao titular (F1.6a). */
data class ExportPayload(
    val fileName: String,
    val content: String,
    val mimeType: String = "application/json",
)
