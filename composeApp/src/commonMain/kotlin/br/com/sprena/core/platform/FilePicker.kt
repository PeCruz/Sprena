package br.com.sprena.core.platform

import androidx.compose.runtime.Composable

/**
 * Dados de um arquivo selecionado pelo usuário.
 */
data class PickedFile(
    val name: String,
    val sizeBytes: Long,
)

/**
 * Composable que registra um file picker do sistema.
 * Retorna uma função `launch` que, quando chamada, abre o seletor de arquivos.
 *
 * [onFilePicked] é chamado com os dados do arquivo selecionado.
 */
@Composable
expect fun rememberFilePicker(onFilePicked: (PickedFile) -> Unit): () -> Unit
