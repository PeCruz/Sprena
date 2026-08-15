package br.com.sprena.core.platform

import androidx.compose.runtime.Composable

/**
 * Registra o compartilhamento de um arquivo gerado pelo app. Devolve a função de
 * disparo, no mesmo molde de [rememberFilePicker].
 *
 * Esta é a fronteira de plataforma da exportação: o use case monta o JSON (lógica pura,
 * testável em `commonTest`), o ViewModel emite `ProfileEffect.ShareExport`, e só aqui
 * alguém conhece `Intent`. Nenhum Composable decide nada e nenhum ViewModel vê Android.
 */
@Composable
expect fun rememberDataExportSharer(): (ExportPayload) -> Unit
